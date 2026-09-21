package com.omms.geoenginecore.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, thread-safe LRU cache of evaluated 6×6 macro grids
 * (TECHSPEC §65).
 *
 * <p>Entries are pooled and reused, so steady-state cache pressure
 * allocates nothing; the pool is seeded above the capacity so
 * cold-start evictions never allocate either.
 */
public final class MacroGridCache {
    /** Number of macro cells per cached grid (6×6). */
    public static final int GRID_SIZE = 36;

    /**
     * Pooled cache entry: one 6×6 macro grid plus its owning key.
     */
    public static final class CacheEntry {
        /** Cached 6×6 H₀ macro grid. */
        public final double[] macroH0 = new double[GRID_SIZE];
        /** Chunk key this entry currently holds. */
        public volatile long key;
    }

    /** Eviction threshold in entries. */
    private final int capacity;
    /** Keyed entry index. */
    private final ConcurrentHashMap<Long, CacheEntry> map;
    /** Insertion-order LRU queue. */
    private final ConcurrentLinkedQueue<Long> evictionQueue;
    /** Pooled free entries. */
    private final ConcurrentLinkedQueue<CacheEntry> pool;
    /** Live entry count. */
    private final AtomicInteger currentSize;

    /**
     * @param capacity eviction threshold, clamped to a minimum of 128
     */
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

    /**
     * Packs chunk coordinates into a single map key.
     *
     * @param chunkWorldX world-coordinate X of the chunk
     * @param chunkWorldZ world-coordinate Z of the chunk
     * @return packed key
     */
    public static long packKey(int chunkWorldX, int chunkWorldZ) {
        return (((long) chunkWorldX) << 32) | (chunkWorldZ & 0xFFFFFFFFL);
    }

    /**
     * Copies the cached grid into {@code destination} when present.
     *
     * @param key packed chunk key
     * @param destination caller-owned 6×6 destination grid
     * @return true on hit
     */
    public boolean tryGet(long key, double[] destination) {
        CacheEntry entry = map.get(key);
        if (entry != null) {
            System.arraycopy(entry.macroH0, 0, destination, 0, GRID_SIZE);
            return true;
        }
        return false;
    }

    /**
     * Inserts a grid under a key, evicting LRU entries to stay
     * within capacity. Duplicate keys are no-ops.
     *
     * @param key packed chunk key
     * @param source grid to cache
     */
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

    /**
     * Returns all entries to the pool and empties the index.
     */
    public void clear() {
        for (CacheEntry entry : map.values()) {
            pool.offer(entry);
        }
        map.clear();
        evictionQueue.clear();
        currentSize.set(0);
    }

    /**
     * @return number of live entries
     */
    public int size() {
        return currentSize.get();
    }
}
