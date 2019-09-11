package com.example.usbtest.mcu.aprom;


public class SensorADCache {

    private final LruArrayDeque<float[]> gyroList;
    private final LruArrayDeque<float[]> acceList;
    private final LruArrayDeque<float[]> mangList;

    private SensorADCache(int capacity) {
        this.gyroList = new LruArrayDeque<>(capacity);
        this.acceList = new LruArrayDeque<>(capacity);
        this.mangList = new LruArrayDeque<>(capacity);
    }

    public static SensorADCache getInstance() {
        return Internal.INSTANCE;
    }

    public void put(int sensorType, float[] value) {
        switch (sensorType) {
            case 1:
                acceList.put(value);
                break;
            case 2:
                mangList.put(value);
                break;
            case 4:
                gyroList.put(value);
                break;
        }
    }

    public float[] getTail(int sensorType) throws InterruptedException {
        switch (sensorType) {
            case 1:
                return acceList.get();
            case 2:
                return mangList.get();
            case 4:
                return gyroList.get();
            default:
                return null;
        }
    }

    private static class Internal {
        private static SensorADCache INSTANCE = new SensorADCache(5);
    }
}