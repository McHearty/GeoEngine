package com.geoengine.core.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class MacroGridCache {
    public static final int GRID_SIZE = 36;

    public static final class CacheEntry {
        public final double[] macroH0 = new double[GRID_SIZE];
        public volatile long key;
    }

    private final int capacity;
    private final ConcurrentHashMap<Long, CacheEntry> map;
    private final ConcurrentLinkedQueue<Long> evictionQueue;
    private final ConcurrentLinkedQueue<CacheEntry> pool;
    private final AtomicInteger currentSize;

    public MacroGridCache(int capacity) {
        this.capacity = Math.max(128, capacity);
        this.map = new ConcurrentHashMap<>(this.capacity);
        this.evictionQueue = new ConcurrentLinkedQueue<>();
        this.pool = new ConcurrentLinkedQueue<>();
        this.currentSize = new AtomicInteger(0);

        for (int i = 0; i < this.capacity + 32; i++) {
            pool.add(new CacheEntry());
        }
    }

    public static long packKey(int chunkWorldX, int chunkWorldZ) {
        return (((long) chunkWorldX) << 32) | (chunkWorldZ & 0xFFFFFFFFL);
    }

    public boolean tryGet(long key, double[] destination) {
        CacheEntry entry = map.get(key);
        if (entry != null) {
            System.arraycopy(entry.macroH0, 0, destination, 0, GRID_SIZE);
            return true;
        }
        return false;
    }

    public void put(long key, double[] source) {
        if (map.containsKey(key)) {
            return;
        }

        while (currentSize.get() >= capacity) {
            Long oldestKey = evictionQueue.poll();
            if (oldestKey != null) {
                CacheEntry removed = map.remove(oldestKey);
                if (removed != null) {
                    currentSize.decrementAndGet();
                    pool.offer(removed);
                }
            } else {
                break;
            }
        }

        CacheEntry entry = pool.poll();
        if (entry == null) {
            entry = new CacheEntry();
        }

        entry.key = key;
        System.arraycopy(source, 0, entry.macroH0, 0, GRID_SIZE);

        if (map.putIfAbsent(key, entry) == null) {
            evictionQueue.offer(key);
            currentSize.incrementAndGet();
        } else {
            pool.offer(entry);
        }
    }

    public void clear() {
        for (CacheEntry entry : map.values()) {
            pool.offer(entry);
        }
        map.clear();
        evictionQueue.clear();
        currentSize.set(0);
    }

    public int size() {
        return currentSize.get();
    }
}
