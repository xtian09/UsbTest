package com.example.usbtest.mcu.aprom;

import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LruArrayDeque<V> {

    final ReentrantLock lock;

    final Condition notEmpty;

    private final ArrayDeque<V> arrayDeque;

    private int capacity;

    public LruArrayDeque(final int capacity) {
        lock = new ReentrantLock(true);
        notEmpty = lock.newCondition();
        arrayDeque = new ArrayDeque<>();
        this.capacity = capacity;
    }

    public synchronized void put(V v) {
        if (arrayDeque.size() > capacity) {
            arrayDeque.addLast(v);
            arrayDeque.removeFirst();
        } else {
            arrayDeque.addLast(v);
        }
    }

    public synchronized void clear() {
        arrayDeque.clear();
    }

    public V get() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (arrayDeque.size() == 0)
                notEmpty.await();
            return arrayDeque.peekLast();
        } finally {
            lock.unlock();
        }
    }
}
