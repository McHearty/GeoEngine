package com.omms.geoenginecore.memory;

import com.omms.geoenginecore.hydrology.DrainageGraph;
import com.omms.geoenginecore.math.ScalarFieldKernel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, scoped cache of coarse {@link DrainageGraph} results
 * (TECHSPEC §78-§80).
 *
 * <p>Cache keys encode the world seed, configuration hash, and region
 * coordinates, so entries can never collide across worlds or
 * configuration variants. The cache is a pure optimization: evicting
 * or clearing it must never change generated terrain (TECHSPEC §77, §81).
 */
public final class HydrologyRegionCache {
    /** Region-keyed graph storage safe for concurrent worker access. */
    private final ConcurrentHashMap<Long, DrainageGraph> regionFlowCache = new ConcurrentHashMap<>();

    /**
     * Packs the scoped region identity into a single cache key.
     *
     * <p>The key includes the world seed, configuration hash, and
     * region coordinates, so cached graphs can never be reused across
     * worlds, configurations, or regions (TECHSPEC §80). The final
     * avalanche mixing follows the SplitMix64 finalizer.
     *
     * @param worldSeed seed of the generating world
     * @param configHash hash of the active configuration
     * @param regionX region grid coordinate X
     * @param regionZ region grid coordinate Z
     * @return collision-resistant scoped cache key
     */
    public static long packScopedKey(long worldSeed, long configHash, int regionX, int regionZ) {
        long h = worldSeed ^ (configHash * 0x9E3779B97F4A7C15L);
        h ^= ((long) regionX) * 0x517CC1B727220A95L;
        h ^= ((long) regionZ) * 0x3C6EF372FE94F82BL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 31);
    }

    /**
     * Returns the drainage graph for a coarse region, computing it on a
     * cache miss.
     *
     * <p>Direct retrieval avoids lambda and Long wrapper allocations
     * (TECHSPEC §62). The lookup is race-safe: if two workers miss the
     * same region concurrently, both build the graph and
     * {@code putIfAbsent} keeps the first one, so every caller observes
     * the same deterministic graph.
     *
     * @param worldSeed seed of the generating world
     * @param configHash hash of the active configuration
     * @param regionX region grid coordinate X
     * @param regionZ region grid coordinate Z
     * @param kernel terrain kernel used to build the graph on a miss
     * @param regionOriginX world-coordinate X of the region origin
     * @param regionOriginZ world-coordinate Z of the region origin
     * @return cached or freshly built drainage graph for the region
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

    /** Drops all cached region graphs, for example when the world or configuration identity changes (TECHSPEC §78). */
    public void clear() {
        regionFlowCache.clear();
    }
}
