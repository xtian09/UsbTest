package com.example.usbtest.mcu.file;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.util.Log;

import com.example.usbtest.mcu.Callback;

import java.util.ArrayList;
import java.util.List;

import static com.example.usbtest.mcu.file.FileCmd.CMD_GET_FLASHMODE;
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
    private int timeOut = 100;
    private FileSplicer mFileSplicer;

    public FileTransfer(UsbDeviceConnection mUsbDeviceConnection, UsbEndpoint mEpIn, UsbEndpoint mEpOut) {
        this.mUsbDeviceConnection = mUsbDeviceConnection;
        this.mEpIn = mEpIn;
        this.mEpOut = mEpOut;
    }

    public void start() {
        List<McuStage> stageList = new ArrayList<>();
        stageList.add(new SyncStage());
        stageList.add(new ModeStage());
        stageList.addAll(getFileSlice());
        RealStage realStage = new RealStage(stageList, 0, 0);
        realStage.deliver(0);
    }

    public void dispose() {
        this.mUsbDeviceConnection = null;
        this.mEpIn = null;
        this.mEpOut = null;
    }

    public List<McuStage> getFileSlice() {
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

        public RealStage(List<McuStage> mStages, int mIndex, int mPkgNum) {
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
        }
    }

}
