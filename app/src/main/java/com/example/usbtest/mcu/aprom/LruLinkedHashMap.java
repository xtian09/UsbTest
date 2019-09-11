package com.example.usbtest.mcu.aprom;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LruLinkedHashMap<K, V> {

    final ReentrantLock lock;

    final Condition notEmpty;

    private final LinkedHashMap<K, V> map;

    public LruLinkedHashMap(final int capacity, boolean lru) {
        lock = new ReentrantLock(true);
        notEmpty = lock.newCondition();
        map = new LinkedHashMap<K, V>((int) Math.ceil(capacity / 0.75f) + 1, 0.75f, lru) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized void put(K k, V v) {
        map.put(k, v);
    }

    public synchronized void clear() {
        map.clear();
    }

    public V get() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (map.size() == 0)
                notEmpty.await();
            Map.Entry<K, V> tail = null;
            for (Map.Entry<K, V> kvEntry : map.entrySet()) {
                tail = kvEntry;
            }
            return tail.getValue();
        } finally {
            lock.unlock();
        }
    }
}
