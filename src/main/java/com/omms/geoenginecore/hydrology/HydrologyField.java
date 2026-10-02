package com.omms.geoenginecore.hydrology;

import java.util.ArrayList;
import java.util.List;

/**
 * First-class hydrology layer (TECHSPEC §222): basin identification
 * and confluence detection on top of the continuous drainage-field
 * model, establishing basin connectivity, confluences, and outlets.
 *
 * <p>The drainage vector field is derived from the outlet-aware
 * potential Φ = H₀ + λ·D_outlet, and flow accumulation Af is computed
 * by propagating discharge downstream along the continuous vector
 * field. Basin identification remains topological: a basin is the
 * exact set of routing cells whose downstream walk terminates at
 * one regional sink.
 *
 * <p>Stable basin and confluence IDs (§80) mix the generator
 * version, dimension, world seed, configuration hash, region, and
 * cell, so IDs can never collide across worlds, dimensions,
 * generator versions, or configuration variants. IDs are full 64-bit
 * hashes, so they may be negative — uniqueness (and scoping), not
 * sign, is the guarantee. ID 0 is reserved to mean "none".
 *
 * <p>Topology is analyzed once per graph (synchronized on the graph,
 * first publisher wins, matching the region cache), so per-column
 * basin/confluence lookups are plain array reads: zero
 * allocations, zero locks (TECHSPEC §62).
 */
public final class HydrologyField {
    /** Generator version mixed into every stable ID (TECHSPEC §80). */
    public static final long SPEC_VERSION = 0x47454F45_4E470002L;
    /** Reserved stable ID meaning "no basin / not a confluence". */
    public static final long NO_ID = 0L;

    /**
     * One identified drainage basin: the exact upstream cell set of a
     * regional sink, with a stable scoped identity (§80).
     */
    public record BasinInfo(
        /** Stable scoped basin ID (never 0). */
        long stableId,
        /** Routing lattice index of the basin's outlet (regional sink). */
        int sinkCell,
        /** World X of the outlet lattice point. */
        double sinkWorldX,
        /** World Z of the outlet lattice point. */
        double sinkWorldZ,
        /** Number of routing cells in the basin. */
        int cellCount,
        /** Catchment discharge A_f at the sink (equals cellCount: every cell contributes the base unit). */
        double totalDischarge) {
    }

    /**
     * One detected confluence: a routing lattice cell with two or
     * more upstream senders, with a stable scoped identity (§80).
     */
    public record Confluence(
        /** Stable scoped confluence ID (never 0). */
        long stableId,
        /** Routing lattice index of the confluence. */
        int cell,
        /** Number of upstream senders (≥ 2). */
        int upstreamCount,
        /** Catchment discharge A_f at the confluence. */
        double flowAccumulation,
        /** World X of the confluence lattice point. */
        double worldX,
        /** World Z of the confluence lattice point. */
        double worldZ) {
    }

    /**
     * One-shot topological analysis of a region's drainage graph
     * (TECHSPEC §26, §80): labels every routing cell with its basin,
     * and enumerates every confluence with a stable scoped ID.
     *
     * <p>Basins are enumerated in ascending sink-cell order and
     * confluences in ascending cell order, so the enumeration is
     * deterministic. The work runs under the graph's own monitor and
     * is skipped when a first publisher already completed it
     * (first-publisher-wins, matching the region cache), so every
     * worker observes the same topology.
     *
     * @param g the region's drainage graph
     * @param worldSeed world seed that roots every seed domain
     * @param configHash fingerprint of the active configuration
     * @param dimensionId dimension the graph belongs to
     * @param generatorVersion engine generator version
     * @param regionX region grid coordinate X
     * @param regionZ region grid coordinate Z
     */
    public void analyze(DrainageGraph g, long worldSeed, long configHash,
                       int dimensionId, long generatorVersion, int regionX, int regionZ) {
        synchronized (g) {
            if (g.topologyAnalyzed) {
                return;
            }
            final int n = g.TOTAL_CELLS;

            // Pass 1: resolve each cell's terminal regional sink.
            // The walk is bounded: elevation strictly decreases along
            // the receiver chain, and a region has at most n cells.
            int[] sinkOf = new int[n];
            for (int i = 0; i < n; i++) {
                int cur = i;
                int hops = 0;
                while (g.receiverIndex[cur] >= 0 && hops++ < n) {
                    cur = g.receiverIndex[cur];
                }
                sinkOf[i] = cur;
            }

            // Pass 2: deterministic basin enumeration (ascending sink
            // order). Every cell maps to exactly one sink, so basins
            // are disjoint and their union is the full cell set.
            boolean[] assigned = new boolean[n];
            int basinCount = 0;
            for (int s = 0; s < n; s++) {
                if (g.receiverIndex[s] != -1 || assigned[s]) {
                    continue; // s is a sink, first time it is seen
                }
                for (int i = 0; i < n; i++) {
                    if (sinkOf[i] == s) {
                        g.basinCell[i] = basinCount;
                        assigned[i] = true;
                    }
                }
                g.basinSinkCell[basinCount] = s;
                g.basinStableId[basinCount] =
                    stableId(dimensionId, worldSeed, configHash, generatorVersion, regionX, regionZ, s);
                basinCount++;
            }

            // Pass 3: confluence enumeration (ascending cell order).
            int confluenceCount = 0;
            for (int c = 0; c < n; c++) {
                if (g.upstreamCount[c] < 2) {
                    continue;
                }
                g.confluenceCell[c] = confluenceCount;
                g.confluenceStableId[confluenceCount] =
                    stableId(dimensionId, worldSeed, configHash, generatorVersion, regionX, regionZ, c);
                confluenceCount++;
            }

            // Publish: counts, then the volatile analysis flag.
            g.basinCount = basinCount;
            g.confluenceCount = confluenceCount;
            g.topologyAnalyzed = true;
        }
    }

    /**
     * Stable scoped basin ID of a routing cell.
     *
     * @param g analyzed region graph
     * @param cell routing cell index
     * @return the cell's basin stable ID, or 0 when the graph is not
     *         yet analyzed or the cell is unlabeled
     */
    public long basinIdForCell(DrainageGraph g, int cell) {
        if (!g.topologyAnalyzed) {
            return NO_ID;
        }
        int b = g.basinCell[cell];
        return b < 0 ? NO_ID : g.basinStableId[b];
    }

    /**
     * Stable scoped confluence ID of a routing cell.
     *
     * @param g analyzed region graph
     * @param cell routing cell index
     * @return the cell's confluence stable ID, or 0 when the graph is
     *         not yet analyzed or the cell is not a confluence
     */
    public long confluenceIdForCell(DrainageGraph g, int cell) {
        if (!g.topologyAnalyzed) {
            return NO_ID;
        }
        int c = g.confluenceCell[cell];
        return c < 0 ? NO_ID : g.confluenceStableId[c];
    }

    /**
     * The region's basins in deterministic enumeration order (ascending
     * sink-cell order). Each member count is recomputed from the
     * labels, and the sink discharge is read from the graph, so the
     * view is consistent with the topology by construction.
     *
     * @param g analyzed region graph
     * @return basins in enumeration order (empty until analyzed)
     */
    public List<BasinInfo> basins(DrainageGraph g) {
        List<BasinInfo> out = new ArrayList<>();
        if (!g.topologyAnalyzed) {
            return out;
        }
        for (int b = 0; b < g.basinCount; b++) {
            int sink = g.basinSinkCell[b];
            int count = 0;
            for (int i = 0; i < g.TOTAL_CELLS; i++) {
                if (g.basinCell[i] == b) {
                    count++;
                }
            }
            out.add(new BasinInfo(g.basinStableId[b], sink,
                g.latticeX(sink), g.latticeZ(sink), count, g.flowAccumulation[sink]));
        }
        return out;
    }

    /**
     * The region's confluences in deterministic enumeration order
     * (ascending cell order).
     *
     * @param g analyzed region graph
     * @return confluences in enumeration order (empty until analyzed)
     */
    public List<Confluence> confluences(DrainageGraph g) {
        List<Confluence> out = new ArrayList<>();
        if (!g.topologyAnalyzed) {
            return out;
        }
        for (int c = 0; c < g.confluenceCount; c++) {
            int cell = firstConfluenceCell(g, c);
            if (cell < 0) {
                continue;
            }
            out.add(new Confluence(g.confluenceStableId[c], cell,
                g.upstreamCount[cell], g.flowAccumulation[cell],
                g.latticeX(cell), g.latticeZ(cell)));
        }
        return out;
    }

    /**
     * Finds the lattice cell that holds confluence ordinal
     * {@code ordinal}.
     */
    private static int firstConfluenceCell(DrainageGraph g, int ordinal) {
        for (int i = 0; i < g.TOTAL_CELLS; i++) {
            if (g.confluenceCell[i] == ordinal) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Deterministic scoped stable ID for one topology element
     * (TECHSPEC §80): the generator version, dimension, world seed,
     * configuration hash, region, and cell are folded through a
     * SplitMix64-style avalanche, and 0 is remapped because it is the
     * reserved "none" marker.
     */
    private static long stableId(int dimensionId, long worldSeed, long configHash,
                                long generatorVersion, int regionX, int regionZ, long cell) {
        long h = SPEC_VERSION;
        h = mix(h + ((long) dimensionId) * 0x9E3779B97F4A7C15L);
        h = mix(h + (worldSeed ^ 0xBF58476D1CE4E5B9L));
        h = mix(h + (configHash ^ 0x94D049BB133111EBL));
        h = mix(h + (generatorVersion ^ 0x65523F9841A4C7C9L));
        h = mix(h + ((long) regionX) * 0x517CC1B727220A95L);
        h = mix(h + ((long) regionZ) * 0x3C6EF372FE94F82BL);
        h = mix(h + (cell ^ 0x1F123456789ABCDEL));
        return h == 0L ? 0x9E3779B97F4A7C15L : h;
    }

    /** SplitMix64-style avalanche mixer. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
