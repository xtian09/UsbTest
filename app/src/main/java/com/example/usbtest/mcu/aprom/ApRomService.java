package com.example.usbtest.mcu.aprom;

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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.usbtest.mcu.Callback;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ApRomService extends Service {
    private static final String TAG = "MCU_SensorService";
    private static final int timeOut = 100;
    private static int Arg23D = 0;
    private static int ArgBN = 0;
    private static int[] ArgCalibration = new int[4];
    private static String ArgLtVersion = null;
    private static String ArgMcuVersion = null;
    private UsbEndpoint mEpOut;
    private UsbEndpoint mEpIn;
    private UsbDeviceConnection mUsbDeviceConnection;
    private Handler mHandler;
    private VersionTask mVersionTask;
    private SensorTask mSensorTask;
    private ApRomService.ArgBinder argBinder = new ApRomService.ArgBinder();

    public void startSensor() {
        if (mHandler != null) {
            UsbManager mUsbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
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
                                    mEpOut = inf.getEndpoint(0);
                                    mEpIn = inf.getEndpoint(1);
                                    mVersionTask = new VersionTask();
                                    mHandler.post(mVersionTask);
                                } else {
                                    mUsbDeviceConnection.close();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void changeToLdRom(final Callback callback) {
        if (mHandler != null) {
            if (mVersionTask != null) {
                mHandler.removeCallbacks(mVersionTask);
                mVersionTask = null;
            }
            if (mSensorTask != null) {
                mHandler.removeCallbacks(mSensorTask);
                mSensorTask = null;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    request(Cmd.ldRomCmd, callback);
                }
            });
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return argBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
    }

    public class ArgBinder extends Binder {
        public ApRomService getService() {
            return ApRomService.this;
        }
    }

    /**
     * getThreeD
     *
     * @return //2D: false; 3D: true
     */
    public boolean get3DMode() {
        Log.d(TAG, "get23D" + (Arg23D == 0));
        return Arg23D == 0;
    }

    /**
     * setThreeD
     *
     * @param b3D 2D: false; 3D: true
     */
    public void set3DMode(boolean b3D) {
        Cmd.set3DMode(b3D);
    }

    /**
     * getCalibration
     *
     * @return Calibration
     */
    public int[] getCalibration() {
        Log.d(TAG, "getCalibration" + ArgCalibration[2]);
        return ArgCalibration;
    }

    /**
     * setCalibration
     *
     * @param calibration (-15,15)
     */
    public void setCalibration(int[] calibration) {
        Cmd.setCalibration(calibration);
    }

    /**
     * getBrightness
     *
     * @return Brightness
     */
    public int getBrightness() {
        Log.d(TAG, "getBrightness" + ArgBN);
        return ArgBN;
    }

    /**
     * setBrightness
     *
     * @param brightness (1,7)
     */
    public void setBrightness(int brightness) {
        Cmd.setBrightness(brightness);
    }

    /**
     * getArgMcuVersion
     *
     * @return ArgMcuVersion
     */
    public String getArgMcuVersion() {
        Log.d(TAG, "getArgMcuVersion" + ArgMcuVersion);
        return ArgMcuVersion;
    }

    /**
     * getArgLtVersion
     *
     * @return ArgLtVersion
     */
    public String getArgLtVersion() {
        Log.d(TAG, "getArgLtVersion" + ArgLtVersion);
        return ArgLtVersion;
    }

    private final static class Cmd {

        private static final byte[] versionCmd = {2};
        private static final byte[] normalCmd = {1, 0};
        private static final byte[] ldRomCmd = {4, 1};
        private static LinkedBlockingQueue<byte[]> argCmdQueue = new LinkedBlockingQueue<>();

        private static void set3DMode(boolean b3D) {
            byte[] cmd = new byte[3];
            cmd[0] = 1;
            cmd[1] = 1;
            cmd[2] = (byte) (b3D ? 0 : 1);
            Log.d(TAG, "set3DMode" + cmd[2]);
            try {
                argCmdQueue.put(cmd);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private static void setCalibration(int[] calibration) {
            byte[] cmd = new byte[6];
            cmd[0] = 1;
            cmd[1] = 3;
            cmd[2] = (byte) calibration[0];
            cmd[3] = (byte) calibration[1];
            cmd[4] = (byte) calibration[2];
            cmd[5] = (byte) calibration[3];
            Log.d(TAG, "setCalibration" + cmd[2]);
            try {
                argCmdQueue.put(cmd);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private static void setBrightness(int brightness) {
            byte[] cmd = new byte[3];
            cmd[0] = 1;
            cmd[1] = 2;
            cmd[2] = (byte) brightness;
            Log.d(TAG, "setBrightness" + cmd[2]);
            try {
                argCmdQueue.put(cmd);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private static byte[] getRequest() {
            if (!argCmdQueue.isEmpty()) {
                return argCmdQueue.remove();
            } else {
                return normalCmd;
            }
        }
    }

    private class VersionTask implements Runnable {

        int retryTime = 3;

        @Override
        public void run() {
            request(Cmd.versionCmd, new Callback() {
                @Override
                public void onFailure(String error) {
                    Log.d(TAG, "versionCmd error = " + error);
                    retryTime--;
                    if (retryTime > 0) {
                        mHandler.post(VersionTask.this);
                    }
                }

                @Override
                public void onResponse(byte[] response) {
                    parseVersion(response);
                    mSensorTask = new SensorTask();
                    mHandler.post(mSensorTask);
                }
            });
        }
    }

    private void request(byte[] request, Callback mCallback) {
        if (mUsbDeviceConnection == null || mEpOut == null || mEpIn == null) {
            mCallback.onFailure("device detached !!");
            return;
        }
        int requestCode = mUsbDeviceConnection.bulkTransfer(mEpIn, request, request.length, timeOut);
        if (requestCode >= 0) {
            byte[] response = new byte[64];
            int responseCode = mUsbDeviceConnection.bulkTransfer(mEpOut, response, response.length, timeOut);
            if (responseCode >= 0) {
                mCallback.onResponse(response);
            } else {
                mCallback.onFailure("response fail !!");
            }
        } else {
            mCallback.onFailure("request fail !!");
        }
    }

    private void parseVersion(byte[] response) {
        try {
            if (response == null || response.length < 1)
                throw new IllegalArgumentException("this data must not be null or empty");
            char[] chars = new char[63];
            for (int i = 1; i < response.length; i++) {
                if (response[i] == 0) {
                    chars = Arrays.copyOf(chars, i - 1);
                    break;
                }
                chars[i - 1] = (char) response[i];
                //Log.d(TAG, "chars[" + (i - 1) + "]=" + chars[i - 1]);
            }
            String rowData = String.valueOf(chars);
            if (!TextUtils.isEmpty(rowData)) {
                String[] versions = rowData.split("V");
                if (versions.length > 1) {
                    ArgMcuVersion = "V" + versions[0];
                    ArgLtVersion = "V" + versions[1];
                    Log.d(TAG, "ArgMcuVersion = " + ArgMcuVersion + " , ArgLtVersion = " + ArgLtVersion);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "argVersion Exception : " + e.toString());
        }
    }

    private void parseSensor(byte[] response) {
        if (response == null || response.length < 1)
            throw new IllegalArgumentException("this data must not be null or empty");
        if (1 == response[0]) {
            if (response[1] != 0) {
                Log.d(TAG, "cmd value=" + response[1] + "  ,3d value=" + response[26] + "  ,light=" + response[27] + "   ,lh=" + response[28] + "   ,lv=" + response[29] + "   ,rh=" + response[30] + "   ,rv=" + response[31]);
            }
            ArgBN = response[27];
            ArgCalibration[0] = response[28];
            ArgCalibration[1] = response[29];
            ArgCalibration[2] = response[30];
            ArgCalibration[3] = response[31];
            if (Arg23D != response[26]) {
                Arg23D = response[26];
                Log.d(TAG, "usb mode has change to " + (0 == Arg23D));
//                Intent intent = new Intent();
//                intent.setAction("android.intent.action.3D_MODE_CHANGED");
//                intent.putExtra("3D_MODE", 0 == Arg23D);
//                mContext.sendBroadcast(intent);
            }
            try {
                float[] gyros = new float[3];
                for (int i = 2; i < 7; i += 2) {
                    gyros[i / 2 - 1] = mathRawValue(response[i], response[i + 1]);
                    Log.d(TAG, "gyros[" + (i / 2 - 1) + "]=" + gyros[i / 2 - 1]);
                }
                SensorCache.getMcuCache().put(4, System.currentTimeMillis(), gyros);
                float[] acces = new float[3];
                for (int i = 8; i < 13; i += 2) {
                    acces[i / 2 - 4] = mathRawValue(response[i], response[i + 1]);
                    Log.d(TAG, "acces[" + (i / 2 - 4) + "]=" + acces[i / 2 - 4]);
                }
                SensorCache.getMcuCache().put(1, System.currentTimeMillis(), acces);
                float[] mangs = new float[3];
                for (int i = 14; i < 25; i += 4) {
                    int mang = 0;
                    mang = mang | (response[i] & 0xff);
                    mang = mang | (response[i + 1] & 0xff) << 8;
                    mang = mang | (response[i + 2] & 0xff) << 16;
                    mang = mang | (response[i + 3] & 0xff) << 24;
                    mangs[i / 4 - 3] = Float.intBitsToFloat(mang);
                    Log.d(TAG, "mangs[" + (i / 4 - 3) + "]=" + mangs[i / 4 - 3]);
                }
                SensorCache.getMcuCache().put(2, System.currentTimeMillis(), mangs);
            } catch (Exception e) {
                Log.d(TAG, "saveSensor exception = " + e.toString());
            }
        }
    }

    private float mathRawValue(byte first, byte second) {
        String firstStr = Integer.toHexString(first & 0xFF);
        if (firstStr.length() == 1) {
            firstStr = '0' + firstStr;
        }
        String secondStr = Integer.toHexString(second & 0xFF);
        if (secondStr.length() == 1) {
            secondStr = '0' + secondStr;
        }
        int intRaw = Integer.valueOf(secondStr + firstStr, 16) - ((second < 0) ? 0x10000 : 0);
        return (float) (intRaw / 16.4 / 180 * 3.14);
    }

    private class SensorTask implements Runnable {

        @Override
        public void run() {
            request(Cmd.getRequest(), new Callback() {
                @Override
                public void onFailure(String error) {
                    Log.d(TAG, "sensorCmd error = " + error);
                    mHandler.postDelayed(SensorTask.this, 20);
                }

                @Override
                public void onResponse(byte[] response) {
                    parseSensor(response);
                    mHandler.postDelayed(SensorTask.this, 20);
                }
            });
        }
    }
}
