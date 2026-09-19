package com.omms.geoenginecore.memory;

import com.omms.geoenginecore.hydrology.DrainageGraph;
import com.omms.geoenginecore.math.ScalarFieldKernel;

import java.util.concurrent.ConcurrentHashMap;

public final class HydrologyRegionCache {
    private final ConcurrentHashMap<Long, DrainageGraph> regionFlowCache = new ConcurrentHashMap<>();

    public static long packScopedKey(long worldSeed, long configHash, int regionX, int regionZ) {
        long h = worldSeed ^ (configHash * 0x9E3779B97F4A7C15L);
        h ^= ((long) regionX) * 0x517CC1B727220A95L;
        h ^= ((long) regionZ) * 0x3C6EF372FE94F82BL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 31);
    }

    /**
     * Direct retrieval without lambda or Long wrapper allocations (§62).
     */
    public DrainageGraph getOrCompute(
        long worldSeed, long configHash, int regionX, int regionZ,
        ScalarFieldKernel kernel, int regionOriginX, int regionOriginZ
    ) {
        long key = packScopedKey(worldSeed, configHash, regionX, regionZ);
        DrainageGraph existing = regionFlowCache.get(key);
        if (existing != null) {
            return existing;
        }

        DrainageGraph created = new DrainageGraph();
        created.buildRegion(kernel, regionOriginX, regionOriginZ);

        DrainageGraph prev = regionFlowCache.putIfAbsent(key, created);
        return prev != null ? prev : created;
    }

    public void clear() {
        regionFlowCache.clear();
    }
}
