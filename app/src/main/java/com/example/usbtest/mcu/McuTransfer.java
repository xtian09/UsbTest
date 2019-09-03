package com.example.usbtest.mcu;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class McuTransfer {

    private static final String TAG = "McuTransfer";
    private UsbEndpoint mEpIn;
    private UsbEndpoint mEpOut;
    private UsbDeviceConnection mUsbDeviceConnection;
    private int timeOut = 100;

    public McuTransfer(UsbDeviceConnection mUsbDeviceConnection, UsbEndpoint mEpIn, UsbEndpoint mEpOut) {
        this.mUsbDeviceConnection = mUsbDeviceConnection;
        this.mEpIn = mEpIn;
        this.mEpOut = mEpOut;
    }

    public void start() {
        List<McuStage> stageList = new ArrayList<>();
        stageList.add(new SyncStage());
        stageList.add(new ModeStage());
        RealStage realStage = new RealStage(stageList, 0);
        realStage.deliver();
    }


    public void dispose() {
        this.mUsbDeviceConnection = null;
        this.mEpIn = null;
        this.mEpOut = null;
    }

    private void request(byte[] request, Callback mCallback) {
        if (mUsbDeviceConnection == null || mEpIn == null || mEpOut == null) {
            mCallback.onFailure(request, new IOException("device detached !!"));
            return;
        }
        int requestCode = mUsbDeviceConnection.bulkTransfer(mEpOut, request, request.length, timeOut);
        if (requestCode >= 0) {
            byte[] response = new byte[64];
            int responseCode = mUsbDeviceConnection.bulkTransfer(mEpIn, response, response.length, timeOut);
            if (responseCode >= 0) {
                mCallback.onResponse(request, response);
            } else {
                mCallback.onFailure(request, new IOException("response fail !!"));
            }
        } else {
            mCallback.onFailure(request, new IOException("request fail !!"));
        }
    }

    private interface McuStage {

        public void process(Chain chain);

        interface Chain {

            void deliver();
        }
    }

    private class RealStage implements McuStage.Chain {

        private List<McuStage> mStages;
        private int mIndex;

        public RealStage(List<McuStage> mStages, int mIndex) {
            this.mStages = mStages;
            this.mIndex = mIndex;
        }

        @Override
        public void deliver() {
            if (mIndex >= mStages.size()) {
                Log.d(TAG, "no stage can handle !!");
                return;
            }
            RealStage next = new RealStage(mStages, mIndex + 1);
            McuStage mcuStage = mStages.get(mIndex);
            mcuStage.process(next);
        }
    }

    private class SyncStage implements McuStage {

        @Override
        public void process(final Chain chain) {
            byte[] request = McuCmd.getSyncByte();
            request(request, new Callback() {
                @Override
                public void onFailure(byte[] request, IOException e) {
                    Log.d(TAG, "sync error = " + e.getMessage());
                }

                @Override
                public void onResponse(byte[] request, byte[] response) {
                    int rCode = McuCmd.getReturnCode(response, 4).get(2);
                    Log.d(TAG, "sync code 2 = " + rCode);
                    chain.deliver();
                }
            });
        }
    }

    private class ModeStage implements McuStage {

        @Override
        public void process(final Chain chain) {
            byte[] request = McuCmd.getModeByte();
            request(request, new Callback() {
                @Override
                public void onFailure(byte[] request, IOException e) {
                    Log.d(TAG, "mode error = " + e.getMessage());
                }

                @Override
                public void onResponse(byte[] request, byte[] response) {
                    int rCode = McuCmd.getReturnCode(response, 4).get(2);
                    Log.d(TAG, "mode code 2 = " + rCode);
                    chain.deliver();
                }
            });
        }
    }

}
