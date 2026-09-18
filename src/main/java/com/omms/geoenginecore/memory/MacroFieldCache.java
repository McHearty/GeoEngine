package com.omms.geoenginecore.memory;

import com.omms.geoenginecore.cache.MacroGridCache;

public final class MacroFieldCache {
    private final MacroGridCache internalCache;

    public MacroFieldCache(int capacity) {
        this.internalCache = new MacroGridCache(capacity);
    }

    public MacroGridCache get() {
        return internalCache;
    }
}
