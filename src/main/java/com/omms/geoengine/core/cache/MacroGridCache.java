package com.geoengine.core.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe bounded cache for macro-grid height samples keyed by chunk coordinates.
 *
 * <p>Entries are copied into and out of the cache so callers can reuse their input and output
 * arrays without sharing mutable cache storage.
 *
 * <p>The cache uses approximate FIFO eviction and maintains a pool of reusable cache entries to
 * reduce allocation pressure.
 */
public final class MacroGridCache {
    /** Number of macro-grid height samples stored in each cache entry. */
    public static final int GRID_SIZE = 36;

    /**
     * Mutable storage for one cached macro-grid sample set.
     */
    public static final class CacheEntry {
        /** Macro-grid height samples associated with {@link #key}. */
        public final double[] macroH0 = new double[GRID_SIZE];
        
        /** Packed chunk-coordinate key associated with {@link #macroH0}. */
        public volatile long key;
    }

    private final int capacity;
    private final ConcurrentHashMap<Long, CacheEntry> map;
    private final ConcurrentLinkedQueue<Long> evictionQueue;
    private final ConcurrentLinkedQueue<CacheEntry> pool;
    private final AtomicInteger currentSize;

    /**
     * Creates a cache with the requested capacity.
     *
     * <p>The effective capacity is at least 128 entries.
     *
     * @param capacity requested maximum number of cached entries
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
     * Packs two chunk coordinates into a single cache key.
     *
     * @param chunkWorldX chunk X coordinate
     * @param chunkWorldZ chunk Z coordinate
     * @return packed key containing both coordinates
     */
    public static long packKey(int chunkWorldX, int chunkWorldZ) {
        return (((long) chunkWorldX) << 32) | (chunkWorldZ & 0xFFFFFFFFL);
    }

    /**
     * Copies the cached macro-grid samples into the destination array.
     *
     * @param key cache key to look up
     * @param destination array receiving the cached samples
     * @return {@code true} if the key was present; {@code false} otherwise
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
     * Adds macro-grid samples to the cache if the key is not already present.
     *
     * <p>If the cache is at capacity, older entries are evicted before the new entry is added.
     *
     * @param key cache key
     * @param source array containing the macro-grid samples to cache
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
     * Removes all cached entries and makes their storage available for reuse.
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
     * Returns the number of entries currently accounted for by the cache.
     *
     * @return current cache size
     */
    public int size() {
        return currentSize.get();
    }
}
