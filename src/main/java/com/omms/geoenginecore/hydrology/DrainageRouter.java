package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.HydrologyRegionCache;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;

public final class DrainageRouter {
    public static final int REGION_SPAN = DrainageGraph.CORE_CELLS * DrainageGraph.CELL_SIZE; // 256 blocks
    public static final double BLEND_MARGIN = 16.0; // 1-cell (16-block) transition margin

    private final HydrologyRegionCache regionCache = new HydrologyRegionCache();

    /**
     * Authoritative D8 flow accumulation query (§26, §27).
     * Smoothly blends across 256-block region seams to maintain strict C0 continuity.
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

    public HydrologyRegionCache getRegionCache() {
        return regionCache;
    }
}
