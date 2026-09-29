package com.omms.geoenginecore.test.phase2;

import com.omms.geoenginecore.hydrology.DrainageGraph;
import com.omms.geoenginecore.hydrology.DrainageRouter;
import com.omms.geoenginecore.hydrology.HydrologyField;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-2 acceptance criteria "deterministic basin identification"
 * and "deterministic confluences" (TECHSPEC §222): basins are the
 * exact topological upstream sets of regional sinks (disjoint,
 * complete, mass-conserving), confluences are first-class detected
 * relationships with stable scoped IDs (§80), and both are
 * bit-identical across independent kernel instances, worker threads,
 * and never collide across worlds or generator-version variants.
 */
public class Phase2BasinConfluenceTest {
    /** World seed shared by every kernel in this suite. */
    private static final long TEST_SEED = 0xCAFEBABEDEADBEEFL;
    /** Region under test (the 256-block region at the world origin). */
    private static final int REGION_X = 0;
    private static final int REGION_Z = 0;
    /** Halo cells per boundary (must match DrainageGraph). */
    private static final int HALO_CELLS = 4;

    /** Deterministic probe columns spanning the region, in world space. */
    private static final long[][] PROBE_COLUMNS = {
        {0, 0}, {10, 10}, {80, 120}, {150, 40}, {200, 250},
        {255, 255}, {30, 220}, {128, 128}, {64, 32}, {240, 200}
    };

    private GeoConfig config;
    private ScalarFieldKernel kernel;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(TEST_SEED, config);
    }

    /** Resolves and analyzes the region's graph for this kernel. */
    private DrainageGraph analyzedGraph(ScalarFieldKernel k, GeoConfig cfg) {
        DrainageGraph g = k.getDrainageRouter().resolveGraph(k, TEST_SEED, cfg.configHash(), REGION_X, REGION_Z);
        k.getHydrologyField().analyze(g, TEST_SEED, cfg.configHash(),
            cfg.dimensionId(), cfg.generatorVersion(), REGION_X, REGION_Z);
        return g;
    }

    /** World X of a z-major lattice cell, re-derived from the documented scheme. */
    private static double expectedLatticeX(int cell) {
        double origin = REGION_X * (double) DrainageRouter.REGION_SPAN
            - (double) (HALO_CELLS * DrainageGraph.CELL_SIZE);
        return origin + (cell % DrainageGraph.GRID_DIM) * (double) DrainageGraph.CELL_SIZE;
    }

    /** World Z of a z-major lattice cell, re-derived from the documented scheme. */
    private static double expectedLatticeZ(int cell) {
        double origin = REGION_Z * (double) DrainageRouter.REGION_SPAN
            - (double) (HALO_CELLS * DrainageGraph.CELL_SIZE);
        return origin + (cell / DrainageGraph.GRID_DIM) * (double) DrainageGraph.CELL_SIZE;
    }

    /**
     * The basin partition is topological: disjoint, complete, and
     * mass-conserving — every member drains to its own basin's
     * outlet, and the sink's A_f equals the member count (each cell
     * contributes the base unit). Stable IDs are distinct and
     * non-zero (TECHSPEC §222, §26, §80).
     */

    @Test
    @DisplayName("Phase-2 Acceptance: deterministic basin identification (disjoint, complete, mass-conserving)")
    void testBasinsPartitionCells() {
        DrainageGraph g = analyzedGraph(kernel, config);
        final int n = DrainageGraph.TOTAL_CELLS;
        assertTrue(g.topologyAnalyzed, "analysis must complete on the resolved graph");
        assertTrue(g.basinCount > 0, "a region always contains at least one sink");

        // 1) Every cell is labeled with an in-range basin ordinal.
        for (int i = 0; i < n; i++) {
            assertTrue(g.basinCell[i] >= 0 && g.basinCell[i] < g.basinCount,
                "cell " + i + " must carry a valid basin label");
        }

        // 2) Disjoint + complete: the basin member sets partition all
        //    n cells, and every basin is non-empty.
        boolean[] memberOfBasin = new boolean[n];
        int[] memberCount = new int[g.basinCount];
        for (int i = 0; i < n; i++) {
            int b = g.basinCell[i];
            assertTrue(!memberOfBasin[i], "cell " + i + " claimed by more than one basin");
            memberOfBasin[i] = true;
            memberCount[b]++;
        }
        int totalMembers = 0;
        for (int b = 0; b < g.basinCount; b++) {
            assertTrue(memberCount[b] > 0, "basin " + b + " must be non-empty");
            totalMembers += memberCount[b];
        }
        assertEquals(n, totalMembers, "basins must cover every routing cell exactly once");

        // 3) Membership: every member's downstream walk terminates at
        //    its own basin's sink, and the sink's A_f equals the
        //    member count (base discharge 1.0 per cell).
        for (int b = 0; b < g.basinCount; b++) {
            int sink = g.basinSinkCell[b];
            assertTrue(g.receiverIndex[sink] == -1, "basin " + b + " sink must be a regional sink");
            for (int i = 0; i < n; i++) {
                if (g.basinCell[i] != b) {
                    continue;
                }
                int cur = i;
                int hops = 0;
                while (g.receiverIndex[cur] >= 0 && hops++ < n) {
                    cur = g.receiverIndex[cur];
                }
                assertEquals(sink, cur, "cell " + i + " must drain to its basin's sink");
            }
            assertEquals((double) memberCount[b], g.flowAccumulation[sink],
                "basin " + b + " catchment must equal its member count");

            // 4) Stable IDs: distinct and never 0 (the reserved none).
            assertTrue(g.basinStableId[b] != 0L, "basin stable IDs must be non-zero");
            for (int other = 0; other < g.basinCount; other++) {
                if (other != b) {
                    assertNotEquals(g.basinStableId[b], g.basinStableId[other],
                        "basin stable IDs must be distinct");
                }
            }
        }
    }

    /**
     * Confluences are first-class detected relationships (TECHSPEC
     * §26, §31): every enumerated confluence has ≥ 2 upstream
     * senders matching the graph, carries a stable non-zero ID, and
     * the enumeration is deterministic (ascending lattice order)
     * with world coordinates matching the z-major lattice.
     */

    @Test
    @DisplayName("Phase-2 Acceptance: deterministic confluences (first-class detection, stable IDs)")
    void testConfluencesFirstClass() {
        DrainageGraph g = analyzedGraph(kernel, config);
        final int n = DrainageGraph.TOTAL_CELLS;
        assertTrue(g.topologyAnalyzed);

        // The enumeration must match the upstream-sender census.
        int census = 0;
        for (int i = 0; i < n; i++) {
            if (g.upstreamCount[i] >= 2) {
                census++;
            } else {
                assertEquals(-1, g.confluenceCell[i],
                    "non-confluence cell " + i + " must stay unlabeled");
            }
        }
        assertEquals(census, g.confluenceCount, "confluence count must match the sender census");

        // Each confluence: upstream count ≥ 2, stable non-zero
        // distinct ID, ascending-cell enumeration order, and world
        // coordinates matching the documented z-major lattice
        // (a genuine re-derivation, guarding the axis convention).
        int lastCell = -1;
        Set<Long> ids = new HashSet<>();
        for (int c = 0; c < g.confluenceCount; c++) {
            int cell = -1;
            for (int i = 0; i < n; i++) {
                if (g.confluenceCell[i] == c) {
                    cell = i;
                    break;
                }
            }
            assertTrue(cell >= 0, "confluence " + c + " must map to a lattice cell");
            assertTrue(g.upstreamCount[cell] >= 2, "confluence must have at least two senders");
            assertTrue(g.confluenceStableId[c] != 0L, "confluence stable IDs must be non-zero");
            assertTrue(ids.add(g.confluenceStableId[c]), "confluence stable IDs must be distinct");
            assertTrue(cell > lastCell, "confluences must enumerate in ascending cell order");
            lastCell = cell;
            assertEquals(expectedLatticeX(cell), g.latticeX(cell),
                "world X must match the z-major lattice of the region origin");
            assertEquals(expectedLatticeZ(cell), g.latticeZ(cell),
                "world Z must match the z-major lattice of the region origin");
        }
    }

    /**
     * Basin and confluence IDs are bit-identical across independent
     * kernel instances and across 16 worker threads rasterizing the
     * same chunk (no chunk-generation ordering dependence, TECHSPEC
     * §222), and the full-column sample carries the same stable IDs.
     */

    @Test
    @DisplayName("Phase-2 Acceptance: basin/confluence IDs bit-exact across instances and 16 threads")
    void testDeterministicBasinConfluenceIds() throws Exception {
        // Baseline: the full-column sample carries stable scoped IDs,
        // and every column of a fluvial region belongs to a basin.
        long[] baselineIds = new long[PROBE_COLUMNS.length * 2];
        GeoSample baseSample = new GeoSample();
        for (int p = 0; p < PROBE_COLUMNS.length; p++) {
            kernel.evaluateFullColumn(PROBE_COLUMNS[p][0], PROBE_COLUMNS[p][1], baseSample);
            assertTrue(baseSample.basinId != 0L,
                "every column of a fluvial region belongs to exactly one basin");
            baselineIds[p * 2] = baseSample.basinId;
            baselineIds[p * 2 + 1] = baseSample.confluenceId;
        }

        // Independent kernel instance, same world: identical IDs.
        ScalarFieldKernel twin = new ScalarFieldKernel(TEST_SEED, config);
        for (int p = 0; p < PROBE_COLUMNS.length; p++) {
            twin.evaluateFullColumn(PROBE_COLUMNS[p][0], PROBE_COLUMNS[p][1], baseSample);
            assertEquals(baselineIds[p * 2], baseSample.basinId,
                "basin ID must be stable across independent instances, column "
                    + PROBE_COLUMNS[p][0] + "," + PROBE_COLUMNS[p][1]);
            assertEquals(baselineIds[p * 2 + 1], baseSample.confluenceId,
                "confluence ID must be stable across independent instances");
        }

        // 16 worker threads rasterize the same chunk: the ID grids
        // must be bit-exact for every column.
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            WorkerScratchpad baselineSp = new WorkerScratchpad();
            kernel.rasterizeSurfaceChunk(baselineSp, 0, 0);
            long[] baselineBasins = baselineSp.basinIdGrid.clone();
            long[] baselineConfluences = baselineSp.confluenceIdGrid.clone();

            List<Future<long[][]>> futures = new ArrayList<>();
            for (int t = 0; t < 16; t++) {
                futures.add(executor.submit(() -> {
                    WorkerScratchpad workerSp = ScratchpadProvider.get();
                    kernel.rasterizeSurfaceChunk(workerSp, 0, 0);
                    return new long[][] {workerSp.basinIdGrid, workerSp.confluenceIdGrid};
                }));
            }
            for (int t = 0; t < futures.size(); t++) {
                long[][] result = futures.get(t).get(30, TimeUnit.SECONDS);
                assertArrayEquals(baselineBasins, result[0],
                    "thread " + t + ": basinIdGrid must be bit-exact");
                assertArrayEquals(baselineConfluences, result[1],
                    "thread " + t + ": confluenceIdGrid must be bit-exact");
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Stable IDs never collide across worlds or generator-version
     * (configuration) variants (TECHSPEC §80).
     */

    @Test
    @DisplayName("Phase-2 Acceptance: stable IDs scoped — no collision across seeds or generator versions")
    void testStableIdsScoped() {
        long seedA = 0x1111111122223333L;
        long seedB = 0x4444444455556666L;
        ScalarFieldKernel kernelA = new ScalarFieldKernel(seedA, config);
        ScalarFieldKernel kernelB = new ScalarFieldKernel(seedB, config);

        DrainageGraph graphA = analyzedGraphWith(kernelA, seedA, config);
        DrainageGraph graphB = analyzedGraphWith(kernelB, seedB, config);

        Set<Long> idsA = new HashSet<>();
        for (int b = 0; b < graphA.basinCount; b++) {
            idsA.add(graphA.basinStableId[b]);
        }
        for (int c = 0; c < graphA.confluenceCount; c++) {
            idsA.add(graphA.confluenceStableId[c]);
        }
        for (int b = 0; b < graphB.basinCount; b++) {
            assertTrue(!idsA.contains(graphB.basinStableId[b]),
                "basin IDs must not collide across worlds");
        }
        for (int c = 0; c < graphB.confluenceCount; c++) {
            assertTrue(!idsA.contains(graphB.confluenceStableId[c]),
                "confluence IDs must not collide across worlds");
        }

        // Generator-version variants (configHash differs) are disjoint
        // even for the same world seed.
        GeoConfig configV2 = GeoConfig.defaultOverworld(2);
        assertNotEquals(config.configHash(), configV2.configHash(),
            "the generator version must change the configuration hash");
        DrainageGraph graphV2 = analyzedGraphWith(new ScalarFieldKernel(seedA, configV2), seedA, configV2);
        Set<Long> idsV2 = new HashSet<>();
        for (int b = 0; b < graphV2.basinCount; b++) {
            idsV2.add(graphV2.basinStableId[b]);
        }
        for (long id : idsA) {
            assertTrue(!idsV2.contains(id), "basin IDs must not collide across generator versions");
        }
    }

    /** Resolves and analyzes a region graph with an explicit seed. */
    private DrainageGraph analyzedGraphWith(ScalarFieldKernel k, long seed, GeoConfig cfg) {
        DrainageGraph g = k.getDrainageRouter().resolveGraph(k, seed, cfg.configHash(), REGION_X, REGION_Z);
        k.getHydrologyField().analyze(g, seed, cfg.configHash(),
            cfg.dimensionId(), cfg.generatorVersion(), REGION_X, REGION_Z);
        return g;
    }
}
