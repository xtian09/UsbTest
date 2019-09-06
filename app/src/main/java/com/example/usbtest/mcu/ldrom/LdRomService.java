package com.example.usbtest.mcu.ldrom;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.example.usbtest.mcu.Callback;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class LdRomService {

    private static final String TAG = "MCU_FileTransfer";
    private static final int CMD_UPDATE_APROM = 0x000000A0;//160
    private static final int CMD_UPDATE_APROM_REMAIN = 0x00000000;//0
    private static final int CMD_SYNC_PACKNO = 0x000000A4;//164
    private static final int CMD_RUN_APROM = 0x000000AB;//171
    private static final int CMD_GET_FLASHMODE = 0x000000CA;//202
    private UsbEndpoint mEpIn;
    private UsbEndpoint mEpOut;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbManager mUsbManager;
    private FileSplicer mFileSplicer;

    public LdRomService(Context context, String path) throws FileNotFoundException {
        this.mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.mFileSplicer = new FileSplicer(path);
    }

    public void start() {
        if (mUsbManager == null) {
            Log.d(TAG, "OTG disable !!");
        } else {
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            if (deviceList == null || deviceList.isEmpty()) {
                Log.d(TAG, "usb deviceList doesn't exist !!");
            } else {
                for (UsbDevice usbDevice : deviceList.values()) {
                    Log.d(TAG, "usb device vendorId = " + usbDevice.getVendorId() + " , ProductId = " + usbDevice.getProductId());
                    if (usbDevice.getVendorId() == 1046 && usbDevice.getProductId() == 16128) {
                        Log.d(TAG, "argDevice attached !!");
                        mUsbDeviceConnection = mUsbManager.openDevice(usbDevice);
                        if (mUsbDeviceConnection != null) {
                            UsbInterface inf = usbDevice.getInterface(0);
                            if (mUsbDeviceConnection.claimInterface(inf, true)) {
                                if (inf.getEndpointCount() < 2) {
                                    Log.d(TAG, "argDevice endPoint count error !!");
                                    return;
                                }
                                mEpIn = inf.getEndpoint(0);
                                mEpOut = inf.getEndpoint(1);
                                startStage();
                            } else {
                                mUsbDeviceConnection.close();
                            }
                        }
                    }
                }
            }
        }
    }

    public void dispose() {
        this.mUsbManager = null;
        this.mUsbDeviceConnection = null;
        this.mEpIn = null;
        this.mEpOut = null;
        if (this.mFileSplicer != null) {
            this.mFileSplicer.close();
            this.mFileSplicer = null;
        }
    }

    private void preStage() {
        List<McuStage> stageList = new ArrayList<>();
        stageList.add(new ModeStage());
        RealStage realStage = new RealStage(stageList, 0, 0);
        realStage.deliver(0);
    }

    private void startStage() {
        List<McuStage> stageList = new ArrayList<>();
        stageList.add(new SyncStage());
        stageList.addAll(getFileSlice());
        stageList.add(new RunApStage());
        RealStage realStage = new RealStage(stageList, 0, 0);
        realStage.deliver(0);
    }

    private List<McuStage> getFileSlice() {
        ArrayList<McuStage> fileList = new ArrayList<>();
        int length = mFileSplicer.getFileLength();
        int startPos = 0;
        int firstLength = 48;
        int stepLength = 56;
        if (length > firstLength) {
            int hasRead = firstLength;
            fileList.add(new FirstFileStage(startPos, hasRead));
            while (hasRead < length) {
                if (hasRead + stepLength < length) {
                    fileList.add(new OtherFileStage(hasRead, stepLength));
                } else {
                    fileList.add(new OtherFileStage(hasRead, length - hasRead));
                }
                hasRead += stepLength;
            }
        } else {
            fileList.add(new FirstFileStage(startPos, length));
        }
        return fileList;
    }

    private void request(byte[] request, Callback mCallback) {
        if (mUsbDeviceConnection == null || mEpIn == null || mEpOut == null) {
            mCallback.onFailure("device detached !!");
            return;
        }
        int requestCode = mUsbDeviceConnection.bulkTransfer(mEpOut, request, request.length, 1000);
        if (requestCode >= 0) {
            byte[] response = new byte[64];
            int responseCode = mUsbDeviceConnection.bulkTransfer(mEpIn, response, response.length, 5000);
            if (responseCode >= 0) {
                mCallback.onResponse(response);
            } else {
                mCallback.onFailure("response fail !!");
            }
        } else {
            mCallback.onFailure("request fail !!");
        }
    }

    private interface McuStage {

        void process(Chain chain);

        interface Chain {

            void deliver(int pkgNum);

            int getPkgNum();
        }
    }

    private class RealStage implements McuStage.Chain {

        private List<McuStage> mStages;
        private int mIndex;
        private int mPkgNum;

        RealStage(List<McuStage> mStages, int mIndex, int mPkgNum) {
            this.mStages = mStages;
            this.mIndex = mIndex;
            this.mPkgNum = mPkgNum;
        }

        @Override
        public void deliver(int pkgNum) {
            if (mIndex >= mStages.size()) {
                Log.d(TAG, "end of the chain !!");
                return;
            }
            RealStage next = new RealStage(mStages, mIndex + 1, pkgNum);
            McuStage mcuStage = mStages.get(mIndex);
            mcuStage.process(next);
            Log.d(TAG, mStages.size() + " has done num = " + mIndex);
        }

        @Override
        public int getPkgNum() {
            return mPkgNum;
        }
    }

    private abstract class BaseStage implements McuStage {

        abstract int cmd();

        abstract byte[] data();

        abstract void handleResponse(List<Integer> response);

        byte[] initRequest(int pkgNum) {
            return concatByte(IntToByte(cmd()), IntToByte(pkgNum), data());
        }

        @Override
        public void process(final Chain chain) {
            request(initRequest(chain.getPkgNum()), new Callback() {
                @Override
                public void onFailure(String error) {
                    Log.d(TAG, cmd() + " cmd error = " + error + " pkgNum =" + chain.getPkgNum());
                }

                @Override
                public void onResponse(byte[] response) {
                    List<Integer> codeList = getReturnCode(response, 4);
                    if (codeList.size() > 1) {
                        if (codeList.size() > 2) {
                            handleResponse(codeList);
                        }
                        chain.deliver(codeList.get(1) + 1);
                    }
                }
            });
        }
    }

    private class FirstFileStage extends BaseStage {

        private int startPos;
        private int length;

        FirstFileStage(int startPos, int length) {
            this.startPos = startPos;
            this.length = length;
        }

        @Override
        int cmd() {
            return CMD_UPDATE_APROM;
        }

        @Override
        byte[] data() {
            return concatByte(IntToByte(startPos), IntToByte(mFileSplicer.getFileLength()), mFileSplicer.getData(startPos, length));

        }

        @Override
        void handleResponse(List<Integer> response) {

        }
    }

    private class OtherFileStage extends BaseStage {

        private int startPos;
        private int length;

        OtherFileStage(int startPos, int length) {
            this.startPos = startPos;
            this.length = length;
        }

        @Override
        int cmd() {
            return CMD_UPDATE_APROM_REMAIN;
        }

        @Override
        byte[] data() {
            return mFileSplicer.getData(startPos, length);
        }

        @Override
        void handleResponse(List<Integer> response) {

        }
    }

    private class RunApStage extends BaseStage {

        @Override
        int cmd() {
            return CMD_RUN_APROM;
        }

        @Override
        byte[] data() {
            return new byte[0];
        }

        @Override
        void handleResponse(List<Integer> response) {
            dispose();
        }
    }

    private class SyncStage extends BaseStage {

        @Override
        int cmd() {
            return CMD_SYNC_PACKNO;
        }

        @Override
        byte[] data() {
            return IntToByte(0);
        }

        @Override
        void handleResponse(List<Integer> response) {

        }
    }

    private class ModeStage extends BaseStage {

        @Override
        int cmd() {
            return CMD_GET_FLASHMODE;
        }

        @Override
        byte[] data() {
            return new byte[0];
        }

        @Override
        void handleResponse(List<Integer> response) {
            Log.d(TAG, "mode = " + response.get(2));
            if (response.get(2) == 2) {
                Log.d(TAG, "start transfer !!");
                startStage();
            }
        }
    }

    private List<Integer> getReturnCode(byte[] data, int count) {
        List<Integer> result = new ArrayList<>();
        int size = data.length;
        if (size > 0 && count > 0) {
            int segments = size / count;
            segments = size % count == 0 ? segments : segments + 1;
            int reCode;
            for (int i = 0; i < segments; i++) {
                if (i == segments - 1) {
                    reCode = ByteToInt(Arrays.copyOfRange(data, count * i, size));
                } else {
                    reCode = ByteToInt(Arrays.copyOfRange(data, count * i, count * (i + 1)));
                }
                result.add(reCode);
            }
        }
        return result;
    }

    private int ByteToInt(byte[] bytes) {
        return (bytes[3] & 0xff) << 24
                | (bytes[2] & 0xff) << 16
                | (bytes[1] & 0xff) << 8
                | (bytes[0] & 0xff);
    }

    private byte[] IntToByte(int num) {
        byte[] bytes = new byte[4];
        bytes[3] = (byte) ((num >> 24) & 0xff);
        bytes[2] = (byte) ((num >> 16) & 0xff);
        bytes[1] = (byte) ((num >> 8) & 0xff);
        bytes[0] = (byte) (num & 0xff);
        return bytes;
    }

    private byte[] concatByte(byte[] first, byte[]... rest) {
        int totalLength = first.length;
        for (byte[] array : rest) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (byte[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

}
