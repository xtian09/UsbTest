package com.example.usbtest.mcu.file;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.example.usbtest.mcu.Callback;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.example.usbtest.mcu.file.FileCmd.CMD_GET_FLASHMODE;
import static com.example.usbtest.mcu.file.FileCmd.CMD_RUN_APROM;
import static com.example.usbtest.mcu.file.FileCmd.CMD_SYNC_PACKNO;
import static com.example.usbtest.mcu.file.FileCmd.CMD_UPDATE_APROM;
import static com.example.usbtest.mcu.file.FileCmd.IntToByte;
import static com.example.usbtest.mcu.file.FileCmd.concatByte;
import static com.example.usbtest.mcu.file.FileCmd.getReturnCode;

public class FileTransfer {

    private static final String TAG = "MCU_FileTransfer";
    private UsbEndpoint mEpIn;
    private UsbEndpoint mEpOut;
    private UsbDeviceConnection mUsbDeviceConnection;
    private static final int timeOut = 100;
    private UsbManager mUsbManager;
    private FileSplicer mFileSplicer;

    public FileTransfer(UsbManager mUsbManager, String path) throws FileNotFoundException {
        this.mUsbManager = mUsbManager;
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
                    if (usbDevice.getVendorId() == 1046 && usbDevice.getProductId() == 20512) {
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
                                preStage();
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
        stageList.add(new ModeStage());
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
            fileList.add(new FileStage(startPos, hasRead));
            while (hasRead < length) {
                if (hasRead + stepLength < length) {
                    fileList.add(new FileStage(hasRead, stepLength));
                } else {
                    fileList.add(new FileStage(hasRead, length - hasRead));
                }
                hasRead += stepLength;
            }
        } else {
            fileList.add(new FileStage(startPos, length));
        }
        return fileList;
    }

    private void request(byte[] request, Callback mCallback) {
        if (mUsbDeviceConnection == null || mEpIn == null || mEpOut == null) {
            mCallback.onFailure("device detached !!");
            return;
        }
        int requestCode = mUsbDeviceConnection.bulkTransfer(mEpOut, request, request.length, timeOut);
        if (requestCode >= 0) {
            byte[] response = new byte[64];
            int responseCode = mUsbDeviceConnection.bulkTransfer(mEpIn, response, response.length, timeOut);
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
                    Log.d(TAG, this.getClass().getSimpleName() + " error = " + error);
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

    private class FileStage extends BaseStage {

        private int startPos;
        private int length;

        FileStage(int startPos, int length) {
            this.startPos = startPos;
            this.length = length;
        }

        @Override
        int cmd() {
            return CMD_UPDATE_APROM;
        }

        @Override
        byte[] data() {
            if (startPos == 0) {
                return concatByte(IntToByte(startPos), IntToByte(mFileSplicer.getFileLength()), mFileSplicer.getData(startPos, length));
            } else {
                return mFileSplicer.getData(startPos, length);
            }
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

}
