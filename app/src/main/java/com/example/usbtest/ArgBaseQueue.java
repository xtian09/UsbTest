//package com.example.usbtest;
//
//import android.hardware.Sensor;
//import android.os.Handler;
//import android.os.Looper;
//import android.os.MessageQueue;
//import android.util.SparseBooleanArray;
//import android.util.SparseIntArray;
//
//import java.lang.ref.WeakReference;
//
//import static java.lang.System.currentTimeMillis;
//
//class ArgBaseQueue {
//    private static abstract class ArgBaseEventQueue {
//
//        private final SparseBooleanArray mActiveSensors = new SparseBooleanArray();
//        protected final SparseIntArray mSensorAccuracies = new SparseIntArray();
//        protected final SystemSensorManager mManager;
//        protected static final int OPERATING_MODE_NORMAL = 0;
//        protected static final int OPERATING_MODE_DATA_INJECTION = 1;
//        private Handler argHandler;
//        private Runnable gyroTask;
//        private Runnable acceTask;
//        private Runnable mangTask;
//
//        ArgBaseEventQueue(Looper looper, SystemSensorManager manager, int mode, String packageName) {
//            if (packageName == null) packageName = "";
//            argHandler = new Handler(looper);
//            mManager = manager;
//        }
//
//        public void dispose() {
//            dispose(false);
//        }
//
//        public boolean addSensor(
//                Sensor sensor, int delayUs, int maxBatchReportLatencyUs) {
//            // Check if already present.
//            int handle = sensor.getHandle();
//            if (mActiveSensors.get(handle)) return false;
//
//            // Get ready to receive events before calling enable.
//            mActiveSensors.put(handle, true);
//            addSensorEvent(sensor);
//            if (enableSensor(sensor, delayUs, maxBatchReportLatencyUs) != 0) {
//                // Try continuous mode if batching fails.
//                if (maxBatchReportLatencyUs == 0 ||
//                        maxBatchReportLatencyUs > 0 && enableSensor(sensor, delayUs, 0) != 0) {
//                    removeSensor(sensor, false);
//                    return false;
//                }
//            }
//            return true;
//        }
//
//        public boolean removeAllSensors() {
//            for (int i = 0; i < mActiveSensors.size(); i++) {
//                if (mActiveSensors.valueAt(i) == true) {
//                    int handle = mActiveSensors.keyAt(i);
//                    Sensor sensor = mManager.mHandleToSensor.get(handle);
//                    if (sensor != null) {
//                        disableSensor(sensor);
//                        mActiveSensors.put(handle, false);
//                        removeSensorEvent(sensor);
//                    } else {
//                        // sensor just disconnected -- just ignore.
//                    }
//                }
//            }
//            return true;
//        }
//
//        public boolean removeSensor(Sensor sensor, boolean disable) {
//            final int handle = sensor.getHandle();
//            if (mActiveSensors.get(handle)) {
//                if (disable) disableSensor(sensor);
//                mActiveSensors.put(sensor.getHandle(), false);
//                removeSensorEvent(sensor);
//                return true;
//            }
//            return false;
//        }
//
//        public int flush() {
//            if (argHandler == null) throw new NullPointerException();
//            return 0;
//        }
//
//        public boolean hasSensors() {
//            // no more sensors are set
//            return mActiveSensors.indexOfValue(true) >= 0;
//        }
//
//        @Override
//        protected void finalize() throws Throwable {
//            try {
//                dispose(true);
//            } finally {
//                super.finalize();
//            }
//        }
//
//        private void dispose(boolean finalized) {
//            if (argHandler != null) {
//                if (acceTask != null) {
//                    argHandler.removeCallbacks(acceTask);
//                    acceTask = null;
//                }
//                if (mangTask != null) {
//                    argHandler.removeCallbacks(mangTask);
//                    mangTask = null;
//                }
//                if (gyroTask != null) {
//                    argHandler.removeCallbacks(gyroTask);
//                    gyroTask = null;
//                }
//                argHandler = null;
//            }
//        }
//
//        private int enableSensor(
//                final Sensor sensor, int rateUs, int maxBatchReportLatencyUs) {
//            if (argHandler == null) throw new NullPointerException();
//            if (sensor == null) throw new NullPointerException();
//            final long millisecondsRate = rateUs / 1000;
//            switch (sensor.getType()) {
//                case 1:
//                    if (acceTask != null) {
//                        argHandler.removeCallbacks(acceTask);
//                    }
//                    acceTask = new Runnable() {
//                        @Override
//                        public void run() {
//                            dispatchSensorEvent(sensor.getHandle(), new float[]{0, 1, 2}, 0, System.currentTimeMillis());
//                            argHandler.postDelayed(acceTask, millisecondsRate);
//                        }
//                    };
//                    argHandler.postDelayed(acceTask, millisecondsRate);
//                    return 0;
//                case 2:
//                    if (mangTask != null) {
//                        argHandler.removeCallbacks(mangTask);
//                    }
//                    mangTask = new Runnable() {
//                        @Override
//                        public void run() {
//                            dispatchSensorEvent(sensor.getHandle(), new float[]{0, 1, 2}, 0, System.currentTimeMillis());
//                            argHandler.postDelayed(mangTask, millisecondsRate);
//                        }
//                    };
//                    argHandler.postDelayed(mangTask, millisecondsRate);
//                    return 0;
//                case 4:
//                    if (gyroTask != null) {
//                        argHandler.removeCallbacks(gyroTask);
//                    }
//                    gyroTask = new Runnable() {
//                        @Override
//                        public void run() {
//                            dispatchSensorEvent(sensor.getHandle(), new float[]{0, 1, 2}, 0, System.currentTimeMillis());
//                            argHandler.postDelayed(gyroTask, millisecondsRate);
//                        }
//                    };
//                    argHandler.postDelayed(gyroTask, millisecondsRate);
//                    return 0;
//            }
//            return 1;
//        }
//
//        protected int injectSensorDataBase(int handle, float[] values, int accuracy,
//                                           long timestamp) {
//            return 0;
//        }
//
//        private int disableSensor(Sensor sensor) {
//            if (argHandler == null) throw new NullPointerException();
//            if (sensor == null) throw new NullPointerException();
//            switch (sensor.getType()) {
//                case 1:
//                    if (acceTask != null) {
//                        argHandler.removeCallbacks(acceTask);
//                        acceTask = null;
//                    }
//                    break;
//                case 2:
//                    if (mangTask != null) {
//                        argHandler.removeCallbacks(mangTask);
//                        mangTask = null;
//                    }
//                    break;
//                case 4:
//                    if (gyroTask != null) {
//                        argHandler.removeCallbacks(gyroTask);
//                        gyroTask = null;
//                    }
//                    break;
//            }
//            return 0;
//        }
//
//        protected abstract void dispatchSensorEvent(int handle, float[] values, int accuracy,
//                                                    long timestamp);
//
//        protected abstract void dispatchFlushCompleteEvent(int handle);
//
//        protected void dispatchAdditionalInfoEvent(
//                int handle, int type, int serial, float[] floatValues, int[] intValues) {
//            // default implementation is do nothing
//        }
//
//        protected abstract void addSensorEvent(Sensor sensor);
//
//        protected abstract void removeSensorEvent(Sensor sensor);
//    }
//}