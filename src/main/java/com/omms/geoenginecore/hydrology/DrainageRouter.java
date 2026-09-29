package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.HydrologyRegionCache;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.math.GeoMath;

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

        // Fast path: the worker's direct-mapped register - a pure
        // array read: zero allocations, zero map lookups, no locks
        // (TECHSPEC §62, §65). The key is already avalanche-mixed, so
        // its low bits select the home slot; the register holds the
        // worker's few most-recent regions, so a blended seam column
        // (primary + one neighbor) is fully resident. Slots are
        // examined graph-first, which stays correct under any store
        // interleaving; the probe ends at an empty slot.
        final int slots = WorkerScratchpad.HYDROLOGY_REGISTER_SLOTS;
        int home = (int) (key & (slots - 1));
        for (int probe = 0; probe < slots; probe++) {
            int s = (home + probe) & (slots - 1);
            if (sp.hydrologyGraphs[s] != null && sp.hydrologyRegionKeys[s] == key) {
                return sp.hydrologyGraphs[s];
            }
        }

        // Slow path (first use on this worker): resolve through the
        // shared bounded cache (TECHSPEC §65-§66), then pin the result
        // into the register, displacing whatever occupied its home
        // slot. A displaced region re-resolves through the shared
        // cache without a rebuild.
        int originX = rx * REGION_SPAN;
        int originZ = rz * REGION_SPAN;
        DrainageGraph graph = regionCache.getOrCompute(worldSeed, configHash, rx, rz, kernel, originX, originZ);

        sp.hydrologyRegionKeys[home] = key;
        sp.hydrologyGraphs[home] = graph;
        return graph;
    }

    /**
     * @return the LRU cache of built region graphs, for invalidation
     *         when the configuration changes
     */
    public HydrologyRegionCache getRegionCache() {
        return regionCache;
    }

    /**
     * Resolves the authoritative graph for one region through the
     * public API: worker register fast path first, then the shared
     * cache (TECHSPEC §65-§66). Production callers that need the graph
     * object itself (centerline walks, conformance probes) use this.
     *
     * @param kernel H₀ kernel of the current configuration
     * @param worldSeed world seed that roots every seed domain
     * @param configHash fingerprint of the active configuration
     * @param rx region index in X
     * @param rz region index in Z
     * @return the region's drainage graph
     */
    public DrainageGraph resolveGraph(ScalarFieldKernel kernel, long worldSeed, long configHash, int rx, int rz) {
        return getGraph(kernel, worldSeed, configHash, rx, rz);
    }

    /**
     * Lattice cell index of the column's deterministic routing cell:
     * the cell whose 16×16-block footprint contains (wx, wz), z-major.
     * Columns in the seam margin (up to one cell outside the 16×16
     * core) clamp to the nearest core-edge halo cell, keeping the
     * index in bounds without changing any interior column's routing.
     *
     * @param rx region index in X
     * @param rz region index in Z
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @return routing cell index in [0, TOTAL_CELLS)
     */
    public int cellIndexFor(int rx, int rz, double wx, double wz) {
        int gx = (int) Math.floor((wx - (rx * REGION_SPAN)) / (double) DrainageGraph.CELL_SIZE)
            + DrainageGraph.HALO_CELLS;
        int gz = (int) Math.floor((wz - (rz * REGION_SPAN)) / (double) DrainageGraph.CELL_SIZE)
            + DrainageGraph.HALO_CELLS;
        gx = GeoMath.clamp(gx, 0, DrainageGraph.GRID_DIM - 1);
        gz = GeoMath.clamp(gz, 0, DrainageGraph.GRID_DIM - 1);
        return gz * DrainageGraph.GRID_DIM + gx;
    }

    /**
     * Bounded channel form factor F_channel for one column
     * (TECHSPEC §28, §29). The centerline is the downstream D8 path of
     * the column's routed lattice cell; F_channel is the
     * cosine-squared U profile of that distance: 1.0 on the centerline,
     * decaying smoothly to 0.0 at the banks.
     *
     * <p>Below the channel-formation threshold (W ≤ 0) the column is
     * unsaturated overland flow and the factor is 0.0, matching the
     * public {@code ChannelField.getChannelProfileFactor} contract.
     *
     * <p>The walk starts at the routed cell and follows the receiver
     * chain. It stops at the first vertex farther from the column than
     * halfWidth + WALK_MARGIN; since every path segment is at most one
     * cell hop (16√2 &lt; WALK_MARGIN blocks) long, the perpendicular
     * approach of any unchecked later segment is bounded below by
     * sqrt((halfWidth + C)² − C²) &gt; halfWidth, so no unexamined
     * segment can raise the factor back above zero. The walk is
     * allocation-free and terminates at the regional sink or the
     * distance cutoff, whichever comes first.
     *
     * @param graph the column's primary-region routing lattice
     * @param routedCell the column's routing cell (z-major index)
     * @param flowAcc flow accumulation proxy A_f
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @return F_channel ∈ [0, 1]
     */
    public double evaluateChannelFactor(DrainageGraph graph, int routedCell, double flowAcc, double wx, double wz) {
        double width = ChannelField.getWidth(flowAcc);
        if (width <= 0.0) {
            return 0.0;
        }
        double halfWidth = width * 0.5;

        double d0 = pointDistance(wx, wz, graph.latticeX(routedCell), graph.latticeZ(routedCell));
        if (d0 > halfWidth + WALK_MARGIN) {
            return 0.0;
        }

        double best = d0;
        int cur = routedCell;
        while (cur >= 0) {
            int next = graph.receiverIndex[cur];
            if (next < 0) {
                break; // Regional sink: the path ends
            }
            best = Math.min(best, segmentDistance(
                wx, wz, graph.latticeX(cur), graph.latticeZ(cur), graph.latticeX(next), graph.latticeZ(next)));
            if (best < halfWidth
                && pointDistance(wx, wz, graph.latticeX(next), graph.latticeZ(next)) > halfWidth + WALK_MARGIN) {
                break;
            }
            cur = next;
        }
        return ChannelField.corridorFactor(halfWidth, best);
    }

    /**
     * Evaluate the channel factor with meander applied to the centerline.
     * The meander offset is computed per-lattice-vertex and applied to the
     * distance calculation, producing a meandered thalweg.
     *
     * @param graph routing lattice
     * @param routedCell column's routing cell
     * @param flowAcc flow accumulation A_f
     * @param channelOrder channel order (0-4)
     * @param slope local channel slope
     * @param wx world-space X
     * @param wz world-space Z
     * @param seed world seed
     * @param basinId drainage basin ID
     * @return F_channel ∈ [0, 1] with meander applied
     */
    public double evaluateChannelFactorMeandered(DrainageGraph graph, int routedCell,
                                                  double flowAcc, int channelOrder, double slope,
                                                  double wx, double wz, long seed, long basinId) {
        double width = ChannelField.getWidth(flowAcc);
        if (width <= 0.0) {
            return 0.0;
        }
        double halfWidth = width * 0.5;

        // Compute distance to the meandered centerline
        double best = pointDistance(wx, wz,
            graph.latticeX(routedCell) + MeanderField.offset(channelOrder, slope, halfWidth,
                seed, basinId, 0.0), graph.latticeZ(routedCell));

        if (best > halfWidth + WALK_MARGIN) {
            return 0.0;
        }

        int cur = routedCell;
        double arcLength = 0.0;
        while (cur >= 0) {
            int next = graph.receiverIndex[cur];
            if (next < 0) {
                break;
            }
            // Compute meandered segment endpoints
            double x1 = graph.latticeX(cur);
            double z1 = graph.latticeZ(cur);
            double x2 = graph.latticeX(next);
            double z2 = graph.latticeZ(next);

            // Apply meander offset at each vertex
            double offset1 = MeanderField.offset(channelOrder, slope, halfWidth,
                seed, basinId, arcLength);
            double offset2 = MeanderField.offset(channelOrder, slope, halfWidth,
                seed, basinId, arcLength + DrainageGraph.CELL_SIZE);

            // Compute distance to meandered segment
            best = Math.min(best, segmentDistance(
                wx, wz, x1 + offset1, z1, x2 + offset2, z2));

            if (best < halfWidth
                && pointDistance(wx, wz, x2 + offset2, z2) > halfWidth + WALK_MARGIN) {
                break;
            }

            arcLength += DrainageGraph.CELL_SIZE;
            cur = next;
        }
        return ChannelField.corridorFactor(halfWidth, best);
    }

    /** Strict bound on one D8 hop (16√2 ≈ 22.63 blocks), in blocks. */
    private static final double WALK_MARGIN = 23.0;

    /**
     * Euclidean distance between two world-space points.
     *
     * @param wx X of the first point
     * @param wz Z of the first point
     * @param cx X of the second point
     * @param cz Z of the second point
     * @return distance in blocks
     */
    private static double pointDistance(double wx, double wz, double cx, double cz) {
        double dx = wx - cx;
        double dy = wz - cz;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Distance from (wx, wz) to the line segment (ax, az)–(bx, by);
     * the closest point is the clamped projection or an endpoint.
     *
     * @return perpendicular distance in blocks
     */
    private static double segmentDistance(double wx, double wz, double ax, double az, double bx, double by) {
        double abx = bx - ax;
        double aby = by - az;
        double len2 = abx * abx + aby * aby;
        if (len2 == 0.0) {
            // Degenerate zero-length segment (defensive; the graph's
            // strictly-downhill invariant makes this unreachable):
            // distance to the segment is the distance to either endpoint.
            return pointDistance(wx, wz, ax, az);
        }
        double t = ((wx - ax) * abx + (wz - az) * aby) / len2;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        double dx = wx - (ax + t * abx);
        double dy = wz - (az + t * aby);
        return Math.sqrt(dx * dx + dy * dy);
    }
}
