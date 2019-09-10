package com.example.usbtest.mcu.aprom;

import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SensorCache {

    private final LinkedHashMap<Long, float[]> gyroMap;

    private final LinkedHashMap<Long, float[]> acceMap;

    private final LinkedHashMap<Long, float[]> mangMap;

    public SensorCache(final int max, boolean lru) {
        gyroMap = new LinkedHashMap<Long, float[]>(max, 0.75f, lru) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
                return size() > max;
            }
        };
        acceMap = new LinkedHashMap<Long, float[]>(max, 0.75f, lru) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
                return size() > max;
            }
        };
        mangMap = new LinkedHashMap<Long, float[]>(max, 0.75f, lru) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
                return size() > max;
            }
        };
    }

    public static final SensorCache getMcuCache() {
        return LazyHolder.INSTANCE;
    }

    public void put(int sensorType, Long key, float[] value) {
        switch (sensorType) {
            case 1:
                synchronized (acceMap) {
                    acceMap.put(key, value);
                    break;
                }
            case 2:
                synchronized (mangMap) {
                    mangMap.put(key, value);
                    break;
                }
            case 4:
                synchronized (gyroMap) {
                    gyroMap.put(key, value);
                    break;
                }
        }
    }

    public float[] getTail(int sensorType) throws ConcurrentModificationException {
        switch (sensorType) {
            case 1:
                return getEntryValue(acceMap);
            case 2:
                return getEntryValue(mangMap);
            case 4:
                return getEntryValue(gyroMap);
            default:
                return null;
        }
    }

    public void clearAllData() {
        synchronized (this) {
            acceMap.clear();
            mangMap.clear();
            gyroMap.clear();
        }
    }

    private float[] getEntryValue(LinkedHashMap<Long, float[]> map) {
        synchronized (map) {
            Map.Entry<Long, float[]> tail = null;
            for (Map.Entry<Long, float[]> kvEntry : map.entrySet()) {
                tail = kvEntry;
            }
            return tail.getValue();
        }
    }

    private static class LazyHolder {
        private static final SensorCache INSTANCE = new SensorCache(5, false);
    }
}
