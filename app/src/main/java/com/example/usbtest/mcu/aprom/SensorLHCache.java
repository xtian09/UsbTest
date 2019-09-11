package com.example.usbtest.mcu.aprom;

public class SensorLHCache {

    private final LruLinkedHashMap<Long, float[]> gyroMap;

    private final LruLinkedHashMap<Long, float[]> acceMap;

    private final LruLinkedHashMap<Long, float[]> mangMap;

    private SensorLHCache(final int capacity, boolean lru) {
        gyroMap = new LruLinkedHashMap<>(capacity, lru);
        acceMap = new LruLinkedHashMap<>(capacity, lru);
        mangMap = new LruLinkedHashMap<>(capacity, lru);
    }

    public static SensorLHCache getInstance() {
        return Internal.INSTANCE;
    }

    public void put(int sensorType, float[] value) {
        switch (sensorType) {
            case 1:
                acceMap.put(System.currentTimeMillis(), value);
            case 2:
                mangMap.put(System.currentTimeMillis(), value);
            case 4:
                gyroMap.put(System.currentTimeMillis(), value);
        }
    }

    public float[] getTail(int sensorType) throws InterruptedException {
        switch (sensorType) {
            case 1:
                return acceMap.get();
            case 2:
                return mangMap.get();
            case 4:
                return gyroMap.get();
            default:
                return null;
        }
    }

    private static class Internal {
        private static final SensorLHCache INSTANCE = new SensorLHCache(5, false);
    }

    public void clearAllData() {
        synchronized (this) {
            acceMap.clear();
            mangMap.clear();
            gyroMap.clear();
        }
    }
}
