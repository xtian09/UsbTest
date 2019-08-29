package com.example.usbtest;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ArgService extends Service {

    private static final String ArgTAG = "ArgService";
    public static int Arg23D = 0;
    public static int ArgBN = 0;
    public static int[] ArgCalibration = new int[4];
    public static String ArgLtVersion = null;
    public static String ArgMcuVersion = null;
    private static UsbEndpoint argEpIn;
    private static UsbEndpoint argEpOut;
    private static UsbDevice argUsbDevice;
    private static UsbDeviceConnection argUsbDeviceConnection;
    private static UsbManager argUsbManager;
    private static LinkedBlockingQueue argCmdQueue = new LinkedBlockingQueue();
    private ScheduledExecutorService argMcuService;
    private ArgBinder argBinder = new ArgBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        argUsbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
        if (argUsbManager == null) {
            Log.d(ArgTAG, "phone disable OTG");
        } else {
            Log.d(ArgTAG, "argUsbManager not null");
            HashMap<String, UsbDevice> deviceList = argUsbManager.getDeviceList();
            if (deviceList == null || deviceList.isEmpty()) {
                Log.d(ArgTAG, "usb deviceList not exist");
            } else {
                for (UsbDevice usbDevice : deviceList.values()) {
                    Log.d(ArgTAG, "usb device vendorId = " + usbDevice.getVendorId() + " , ProductId = " + usbDevice.getProductId());
                    if (usbDevice.getVendorId() == 1046 && usbDevice.getProductId() == 20512) {
                        Log.d(ArgTAG, "argDevice attached");
                        argUsbDevice = usbDevice;
                        argVersionTask();
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return argBinder;
    }

    private void argVersionTask() {
        new Thread() {
            @Override
            public void run() {
                if (argUsbDevice != null) {
                    argUsbDeviceConnection = argUsbManager.openDevice(argUsbDevice);
                    if (argUsbDeviceConnection != null) {
                        UsbInterface inf = argUsbDevice.getInterface(0);
                        if (inf.getEndpointCount() < 2) {
                            Log.d(ArgTAG, "arg end point count error");
                            return;
                        }
                        argEpOut = inf.getEndpoint(1);//it is out
                        argEpIn = inf.getEndpoint(0);//it is in
                        if (argUsbDeviceConnection.claimInterface(inf, true)) {
                            try {
                                int i = 3;
                                while (i > 0) {
                                    byte[] in = new byte[64];
                                    in[0] = 2;
                                    int retIn = argUsbDeviceConnection.bulkTransfer(argEpOut, in, in.length, 100);
                                    if (retIn >= 0) {
                                        byte[] out = new byte[64];
                                        int retOut = argUsbDeviceConnection.bulkTransfer(argEpIn, out, 64, 100);
                                        Log.d(ArgTAG, "cmd version result = " + retIn + " , out info =" + retOut);
                                        if (retOut >= 0) {
                                            argVersion(out);
                                            break;
                                        }
                                    }
                                    i--;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                periodMcuTask();
                            }
                        } else {
                            argUsbDeviceConnection.close();
                        }
                    }
                }
            }
        }.run();
    }

    private void periodMcuTask() {
        argMcuService = Executors.newSingleThreadScheduledExecutor();
        argMcuService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] in;
                    if (!argCmdQueue.isEmpty()) {
                        in = (byte[]) argCmdQueue.remove();
                    } else {
                        in = new byte[64];
                        in[0] = 1;
                        in[1] = 0;
                    }
                    int retIn = argUsbDeviceConnection.bulkTransfer(argEpOut, in, in.length, 100);
                    if (retIn >= 0) {
                        byte[] out = new byte[64];
                        int retOut = argUsbDeviceConnection.bulkTransfer(argEpIn, out, 64, 100);
                        if (retOut >= 0 && 1 == out[0]) {
                            if (in[1] != 0) {
                                Log.d(ArgTAG, "cmd value=" + out[1] + "  ,3d value=" + out[26] + "  ,light=" + out[27] + "   ,lh=" + out[28] + "   ,lv=" + out[29] + "   ,rh=" + out[30] + "   ,rv=" + out[31]);
                            }
                            ArgBN = out[27];
                            ArgCalibration[0] = out[28];
                            ArgCalibration[1] = out[29];
                            ArgCalibration[2] = out[30];
                            ArgCalibration[3] = out[31];
                            if (Arg23D != out[26]) {
                                Arg23D = out[26];
                                Log.d(ArgTAG, "usb mode has change to " + (0 == Arg23D));
                                Intent intent = new Intent();
                                intent.setAction("android.intent.action.3D_MODE_CHANGED");
                                intent.putExtra("3D_MODE", 0 == Arg23D);
//                                mContext.sendBroadcast(intent);
                                sendBroadcast(intent);
                            }
//                            saveGyroStr(out);
//                            saveAcceStr(out);
//                            saveMangStr(out);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
    }

    public void argVersion(byte[] byteArray) {
        try {
            if (byteArray == null || byteArray.length < 1)
                throw new IllegalArgumentException("this byteArray must not be null or empty");
            char[] chars = new char[63];
            for (int i = 1; i < byteArray.length; i++) {
                if (byteArray[i] == 0) {
                    break;
                }
                chars[i - 1] = (char) byteArray[i];
                Log.d(ArgTAG, "chars[" + (i - 1) + "]=" + chars[i - 1]);
            }
            String rowData = String.valueOf(chars);
            if (!TextUtils.isEmpty(rowData)) {
                String[] versions = rowData.split("V");
                if (versions.length > 1) {
                    ArgMcuVersion = "V" + versions[0];
                    ArgLtVersion = "V" + versions[1];
                    Log.d(ArgTAG, "ArgMcuVersion = " + ArgMcuVersion + " , ArgLtVersion = " + ArgLtVersion);
                }
            }
        } catch (Exception e) {
            Log.d(ArgTAG, "argVersion Exception : " + e.toString());
        }
    }

    /**
     * getThreeD
     *
     * @return //2D: false; 3D: true
     */
    public boolean getThreeD() {
        Log.d(ArgTAG, "getThreeD" + (Arg23D == 0));
        return Arg23D == 0;
    }

    /**
     * setThreeD
     *
     * @param b3D 2D: false; 3D: true
     */
    public void setThreeD(boolean b3D) {
        byte[] cmd = new byte[64];
        cmd[0] = 1;
        cmd[1] = 1;
        cmd[2] = (byte) (b3D ? 0 : 1);
        Log.d(ArgTAG, "setThreeD" + cmd[2]);
        try {
            argCmdQueue.put(cmd);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * getCalibration
     *
     * @return
     */
    public int[] getCalibration() {
        Log.d(ArgTAG, "getCalibration" + ArgCalibration.toString());
        return ArgCalibration;
    }

    /**
     * setCalibration
     *
     * @param calibration (-15,15)
     */
    public void setCalibration(int[] calibration) {
        byte[] cmd = new byte[64];
        cmd[0] = 1;
        cmd[1] = 3;
        cmd[2] = (byte) calibration[0];
        cmd[3] = (byte) calibration[1];
        cmd[4] = (byte) calibration[2];
        cmd[5] = (byte) calibration[3];
        Log.d(ArgTAG, "setCalibration" + cmd[2]);
        try {
            argCmdQueue.put(cmd);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * getBrightness
     *
     * @return
     */
    public int getBrightness() {
        Log.d(ArgTAG, "getBrightness" + ArgBN);
        return ArgBN;
    }

    /**
     * setBrightness
     *
     * @param brightness (1,7)
     */
    public void setBrightness(int brightness) {
        byte[] cmd = new byte[64];
        cmd[0] = 1;
        cmd[1] = 2;
        cmd[2] = (byte) brightness;
        Log.d(ArgTAG, "setBrightness" + cmd[2]);
        try {
            argCmdQueue.put(cmd);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * getArgMcuVersion
     *
     * @return
     */
    public String getArgMcuVersion() {
        Log.d(ArgTAG, "getArgMcuVersion" + ArgMcuVersion);
        return ArgMcuVersion;
    }

    /**
     * getArgLtVersion
     *
     * @return
     */
    public String getArgLtVersion() {
        Log.d(ArgTAG, "getArgLtVersion" + ArgLtVersion);
        return ArgLtVersion;
    }

    private static class InterHandler extends Handler {

        private WeakReference<Looper> mLooper;

        public InterHandler(Looper looper) {
            mLooper = new WeakReference<>(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mLooper.get() != null) {
                switch (msg.what) {
//                    case REFRESH_COMPLETE:
//                        activity.mListView.setOnRefreshComplete();
//                        activity.mAdapter.notifyDataSetChanged();
//                        activity.mListView.setSelection(0);
//                        break;
                }
            }
        }
    }

    class ArgBinder extends Binder {
        public ArgService getService() {
            return ArgService.this;
        }
    }
}
