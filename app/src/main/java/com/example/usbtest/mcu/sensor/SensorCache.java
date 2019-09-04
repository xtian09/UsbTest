package com.example.usbtest.mcu.sensor;


import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SensorCache<K, V> {

    private LinkedHashMap<K, V> gyroMap = null;

    private LinkedHashMap<K, V> acceMap = null;

    private LinkedHashMap<K, V> mangMap = null;

    private int max;

    public SensorCache(final int max, boolean lru) {
        this.max = max;
        gyroMap = new LinkedHashMap<K, V>(max, 0.75f, lru) {
            @Override
            protected boolean removeEldestEntry(Entry<K, V> eldest) {
                return size() > max;
            }
        };
        acceMap = new LinkedHashMap<K, V>(max, 0.75f, lru) {
            @Override
            protected boolean removeEldestEntry(Entry<K, V> eldest) {
                return size() > max;
            }
        };
        mangMap = new LinkedHashMap<K, V>(max, 0.75f, lru) {
            @Override
            protected boolean removeEldestEntry(Entry<K, V> eldest) {
                return size() > max;
            }
        };
    }

    public static final SensorCache getMcuCache() {
        return LazyHolder.INSTANCE;
    }

    public void put(int sensorType, K key, V value) {
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

    public void clearAllData() {
        synchronized (this) {
            acceMap.clear();
            mangMap.clear();
            gyroMap.clear();
        }
    }

    public <K, V> Map.Entry<K, V> getTail(int sensorType) throws ConcurrentModificationException {
        switch (sensorType) {
            case 1:
                synchronized (acceMap) {
                    Map.Entry<K, V> tail = null;
                    Iterator<Map.Entry<K, V>> iterator1 = ((LinkedHashMap<K, V>) acceMap).entrySet().iterator();
                    while (iterator1.hasNext()) {
                        tail = iterator1.next();
                    }
                    return tail;
                }
            case 2:
                synchronized (mangMap) {
                    Map.Entry<K, V> tail = null;
                    Iterator<Map.Entry<K, V>> iterator2 = ((LinkedHashMap<K, V>) mangMap).entrySet().iterator();
                    while (iterator2.hasNext()) {
                        tail = iterator2.next();
                    }
                    return tail;
                }
            case 4:
                synchronized (gyroMap) {
                    Map.Entry<K, V> tail = null;
                    Iterator<Map.Entry<K, V>> iterator4 = ((LinkedHashMap<K, V>) gyroMap).entrySet().iterator();
                    while (iterator4.hasNext()) {
                        tail = iterator4.next();
                    }
                    return tail;
                }
            default:
                return null;
        }
    }

    private static class LazyHolder {
        private static final SensorCache INSTANCE = new SensorCache(5, false);
    }
}
