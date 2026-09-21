package com.omms.geoenginecore.memory;

import com.omms.geoenginecore.cache.MacroGridCache;

/**
 * Bounded cache of chunk macro grids (TECHSPEC §77-§79).
 *
 * <p>Entries are scoped to the generator instance, so evictions can
 * never leak stale data between worlds. A cache hit only saves
 * re-evaluation; the returned grid is always identical to a fresh
 * evaluation, so the cache can never become a source of truth
 * (TECHSPEC §77).
 */
public final class MacroFieldCache {
    /** Backing bounded LRU macro grid cache. */
    private final MacroGridCache internalCache;

    /**
     * @param capacity maximum number of macro grids held before eviction
     */
    public MacroFieldCache(int capacity) {
        this.internalCache = new MacroGridCache(capacity);
    }

    /**
     * @return the underlying bounded macro grid cache
     */
    public MacroGridCache get() {
        return internalCache;
    }
}
