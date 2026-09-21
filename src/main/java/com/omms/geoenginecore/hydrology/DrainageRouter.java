package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.HydrologyRegionCache;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;

/**
 * Region-based A_f provider with seamless boundary blending.
 *
 * <p>Flow accumulation is routed on 256-block regions (16×16-cell
 * cores). A_f is sampled from the owning region's authoritative
 * graph, and a 16-block (one-cell) margin at each seam blends the
 * two neighboring region graphs so the field remains strictly
 * C₀-continuous across region boundaries (TECHSPEC §26). Graphs are
 * cached per (worldSeed, configHash, region) key, and the last-used
 * graph is kept in the worker scratchpad (TECHSPEC §65-§66).
 */
public final class DrainageRouter {
    /** Region span in blocks (256). */
    public static final int REGION_SPAN = DrainageGraph.CORE_CELLS * DrainageGraph.CELL_SIZE;
    /** One-cell (16-block) seam transition margin. */
    public static final double BLEND_MARGIN = 16.0;

    /** LRU cache of built region graphs. */
    private final HydrologyRegionCache regionCache = new HydrologyRegionCache();

    /**
     * Computes the authoritative D8 flow accumulation A_f with
     * cross-region blending (TECHSPEC §26, §27).
     *
     * <p>Interior columns use the owning region's graph only;
     * columns inside the seam margin linearly ramp between the two
     * adjacent region graphs.
     *
     * @param kernel H₀ kernel of the current configuration
     * @param worldSeed world seed that roots every seed domain
     * @param configHash fingerprint of the active configuration
     * @param wx world-space X
     * @param wz world-space Z
     * @return flow accumulation proxy A_f, ≥ 0
     */
    public double computeAccumulationProxy(ScalarFieldKernel kernel, long worldSeed, long configHash, double wx, double wz) {
        int rx = (int) Math.floor(wx / (double) REGION_SPAN);
        int rz = (int) Math.floor(wz / (double) REGION_SPAN);

        double localX = wx - (rx * REGION_SPAN);
        double localZ = wz - (rz * REGION_SPAN);

        // Primary region graph
        DrainageGraph primaryGraph = getGraph(kernel, worldSeed, configHash, rx, rz);
        double primaryAcc = primaryGraph.sampleAccumulation(wx, wz);

        // --- Seamless Boundary Blending (X-Axis) ---
        if (localX < BLEND_MARGIN) {
            double u = (localX + BLEND_MARGIN) / (2.0 * BLEND_MARGIN); // 0.0 at -16 -> 0.5 at 0 -> 1.0 at +16
            DrainageGraph westGraph = getGraph(kernel, worldSeed, configHash, rx - 1, rz);
            double westAcc = westGraph.sampleAccumulation(wx, wz);
            return (1.0 - u) * westAcc + u * primaryAcc;
        } else if (localX > REGION_SPAN - BLEND_MARGIN) {
            double u = (localX - (REGION_SPAN - BLEND_MARGIN)) / (2.0 * BLEND_MARGIN); // 0.0 at 240 -> 0.5 at 256 -> 1.0 at 272
            DrainageGraph eastGraph = getGraph(kernel, worldSeed, configHash, rx + 1, rz);
            double eastAcc = eastGraph.sampleAccumulation(wx, wz);
            return (1.0 - u) * primaryAcc + u * eastAcc;
        }

        // --- Seamless Boundary Blending (Z-Axis) ---
        if (localZ < BLEND_MARGIN) {
            double v = (localZ + BLEND_MARGIN) / (2.0 * BLEND_MARGIN);
            DrainageGraph northGraph = getGraph(kernel, worldSeed, configHash, rx, rz - 1);
            double northAcc = northGraph.sampleAccumulation(wx, wz);
            return (1.0 - v) * northAcc + v * primaryAcc;
        } else if (localZ > REGION_SPAN - BLEND_MARGIN) {
            double v = (localZ - (REGION_SPAN - BLEND_MARGIN)) / (2.0 * BLEND_MARGIN);
            DrainageGraph southGraph = getGraph(kernel, worldSeed, configHash, rx, rz + 1);
            double southAcc = southGraph.sampleAccumulation(wx, wz);
            return (1.0 - v) * primaryAcc + v * southAcc;
        }

        return primaryAcc;
    }

    /**
     * Resolves the authoritative graph for one region, preferring the
     * thread-local scratchpad register (zero allocations, zero map
     * lookups on interior columns) and falling back to the shared LRU
     * cache (TECHSPEC §65-§66).
     *
     * @param kernel H₀ kernel of the current configuration
     * @param worldSeed world seed
     * @param configHash configuration fingerprint
     * @param rx region index in X
     * @param rz region index in Z
     * @return the region's drainage graph
     */
    private DrainageGraph getGraph(ScalarFieldKernel kernel, long worldSeed, long configHash, int rx, int rz) {
        long key = HydrologyRegionCache.packScopedKey(worldSeed, configHash, rx, rz);
        WorkerScratchpad sp = ScratchpadProvider.get();

        // Fast-path: thread-local scratchpad register (0 allocations, 0 map lookups on interior columns)
        if (sp.cachedHydrologyRegionKey == key && sp.cachedHydrologyGraph != null) {
            return sp.cachedHydrologyGraph;
        }

        int originX = rx * REGION_SPAN;
        int originZ = rz * REGION_SPAN;
        DrainageGraph graph = regionCache.getOrCompute(worldSeed, configHash, rx, rz, kernel, originX, originZ);

        sp.cachedHydrologyRegionKey = key;
        sp.cachedHydrologyGraph = graph;
        return graph;
    }

    /**
     * @return the LRU cache of built region graphs, for invalidation
     *         when the configuration changes
     */
    public HydrologyRegionCache getRegionCache() {
        return regionCache;
    }
}
