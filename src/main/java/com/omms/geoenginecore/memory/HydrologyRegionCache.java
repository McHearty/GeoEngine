package com.omms.geoenginecore.memory;

import com.omms.geoenginecore.hydrology.DrainageGraph;
import com.omms.geoenginecore.math.ScalarFieldKernel;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, scoped cache of coarse {@link DrainageGraph} results
 * (TECHSPEC §78-§80).
 *
 * <p>Cache keys encode the world seed, configuration hash, and region
 * coordinates, so entries can never collide across worlds or
 * configuration variants. The cache is a pure optimization: evicting
 * or clearing it must never change generated terrain (TECHSPEC §77,
 * §81).
 *
 * <p>Storage is a striped, primitive-{@code long}-keyed
 * open-addressing table rather than a boxed-key {@code Map}: a hit
 * performs no allocation and no lock acquisition (TECHSPEC §62), and
 * a miss takes only the lock of its own stripe. Insertion is
 * first-publisher-wins: if two workers miss the same region
 * concurrently, both build the (value-identical, deterministic) graph
 * and the first to publish is kept, so every caller observes the same
 * deterministic graph (TECHSPEC §80).
 */
public final class HydrologyRegionCache {
    /** Stripes of the open-addressing table. */
    private static final int STRIPE_COUNT = 8;
    /** Slots per stripe (power of two). */
    private static final int SLOTS_PER_STRIPE = 16;

    /** Total cached region capacity (128). */
    public static final int CAPACITY = STRIPE_COUNT * SLOTS_PER_STRIPE;

    /** Empty-slot key marker. */
    private static final long EMPTY_KEY = 0L;

    /** Region-keyed graph storage, safe for concurrent worker access. */
    private final long[][] keys = new long[STRIPE_COUNT][SLOTS_PER_STRIPE];
    /** Graphs paired with {@link #keys}; null while a slot is empty. */
    private final DrainageGraph[][] graphs = new DrainageGraph[STRIPE_COUNT][SLOTS_PER_STRIPE];
    /** One short-held lock per stripe; a miss locks only its stripe. */
    private final ReentrantLock[] stripeLocks = new ReentrantLock[STRIPE_COUNT];

    /**
     * Allocates the stripe locks; slots start empty.
     */
    public HydrologyRegionCache() {
        for (int s = 0; s < STRIPE_COUNT; s++) {
            stripeLocks[s] = new ReentrantLock();
        }
    }

    /**
     * Packs the scoped region identity into a single cache key.
     *
     * <p>The key includes the world seed, configuration hash, and
     * region coordinates, so cached graphs can never be reused across
     * worlds, configurations, or regions (TECHSPEC §80). The final
     * avalanche mixing follows the SplitMix64 finalizer. The result is
     * remapped away from zero, which is the table's empty-slot marker.
     *
     * @param worldSeed seed of the generating world
     * @param configHash hash of the active configuration
     * @param regionX region grid coordinate X
     * @param regionZ region grid coordinate Z
     * @return collision-resistant, non-zero scoped cache key
     */
    public static long packScopedKey(long worldSeed, long configHash, int regionX, int regionZ) {
        long h = worldSeed ^ (configHash * 0x9E3779B97F4A7C15L);
        h ^= ((long) regionX) * 0x517CC1B727220A95L;
        h ^= ((long) regionZ) * 0x3C6EF372FE94F82BL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        long mixed = h ^ (h >>> 31);
        // 0 is the table's empty-slot marker; remap the degenerate key.
        return mixed == 0L ? 0x9E3779B97F4A7C15L : mixed;
    }

    /**
     * Returns the drainage graph for a coarse region, computing it on a
     * cache miss.
     *
     * <p>The hit path is a lock-free, allocation-free probe over the
     * stripe's slots (TECHSPEC §62): slots are examined graph-first so
     * the probe is correct under any store interleaving, and the probe
     * ends at the first empty slot, as open addressing requires.
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
        int stripe = (int) (key & (STRIPE_COUNT - 1));

        // Hit path: read-only probe, no lock, no allocation.
        for (int i = 0; i < SLOTS_PER_STRIPE; i++) {
            DrainageGraph g = graphs[stripe][i];
            if (g == null) {
                break;
            }
            if (keys[stripe][i] == key) {
                return g;
            }
        }

        // Miss path: build the graph, then publish it under the stripe
        // lock so a concurrent reader never observes a key without its
        // graph.
        DrainageGraph created = new DrainageGraph();
        created.buildRegion(kernel, regionOriginX, regionOriginZ);

        stripeLocks[stripe].lock();
        try {
            for (int i = 0; i < SLOTS_PER_STRIPE; i++) {
                if (keys[stripe][i] == EMPTY_KEY) {
                    keys[stripe][i] = key;
                    graphs[stripe][i] = created;
                    return created;
                }
                if (keys[stripe][i] == key) {
                    // A concurrent builder published this region first
                    // (TECHSPEC §80: first-publisher-wins, so every
                    // caller observes the same deterministic graph).
                    return graphs[stripe][i];
                }
            }
            // Stripe full: evict the tail slot. Eviction is a pure
            // optimization decision (TECHSPEC §77, §81).
            int victim = SLOTS_PER_STRIPE - 1;
            keys[stripe][victim] = key;
            graphs[stripe][victim] = created;
            return created;
        } finally {
            stripeLocks[stripe].unlock();
        }
    }

    /**
     * Drops all cached region graphs, for example when the world or
     * configuration identity changes (TECHSPEC §78).
     */
    public void clear() {
        for (int s = 0; s < STRIPE_COUNT; s++) {
            stripeLocks[s].lock();
            try {
                for (int i = 0; i < SLOTS_PER_STRIPE; i++) {
                    keys[s][i] = EMPTY_KEY;
                    graphs[s][i] = null;
                }
            } finally {
                stripeLocks[s].unlock();
            }
        }
    }
}
