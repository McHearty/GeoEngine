package com.omms.geoenginecore.memory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small FIFO cache of recently rasterized chunk scratchpads.
 *
 * <p>Reduces double-rasterization when buildSurface is called on
 * a different worker thread than fillFromNoise. The cache is small
 * (64 chunks) to bound memory usage; evicted scratchpads are
 * reclaimed by GC.
 */
public final class ChunkScratchpadCache {
    private static final int CAPACITY = 64;

    /** Chunk position key: pack x,z into long (x << 32 | z). */
    private final Map<Long, WorkerScratchpad> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, WorkerScratchpad> eldest) {
            return size() > CAPACITY;
        }
    };

    /**
     * Stores a rasterized scratchpad for the given chunk position.
     */
    public void put(int chunkX, int chunkZ, WorkerScratchpad scratchpad) {
        long key = packKey(chunkX, chunkZ);
        cache.put(key, scratchpad);
    }

    /**
     * Retrieves the cached scratchpad for the given chunk position,
     * or null if not cached.
     */
    public WorkerScratchpad get(int chunkX, int chunkZ) {
        long key = packKey(chunkX, chunkZ);
        return cache.get(key);
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Packs chunk coordinates into a long key.
     */
    private static long packKey(int x, int z) {
        return ((long) x << 32) | ((long) z & 0xFFFFFFFFL);
    }
}