package com.omms.geoenginecore.test;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-thread / multi-seed determinism matrix (TECHSPEC §167):
 * 16 threads share one kernel across a 5-seed matrix, and every
 * thread-confined scratchpad must reproduce the baseline raster
 * bit-exactly (0.0 tolerance) on every output grid, not only the
 * surface. Comparing the full scratchpad (crustal, derivative,
 * hydrology, classification, and SIMD-lane grids) closes the gap
 * where a non-surface field could be non-deterministic without
 * affecting the visible surface.
 */
public class MultiSeedMultiThreadMatrixTest {

    /** Seed matrix covering zero, mid, and max-magnitude seeds. */
    private static final long[] SEED_MATRIX = {
        0x123456789ABCDEFL,
        0xDEADBEEFCAFEBABEL,
        0x0000000000000001L,
        0x7FFFFFFFFFFFFFFFL,
        0xFEDCBA9876543210L
    };

    /** Output grids compared across threads; names must line up with the snapshot copies. */
    private static final String[] DOUBLE_GRID_NAMES = {
        "macroH0", "macroTectonic", "macroAge", "macroTemp", "macroHumid", "macroErosion",
        "surfaceGrid", "h0Grid", "gradXGrid", "gradZGrid", "laplacianGrid",
        "ageGrid", "tempGrid", "humidGrid", "climateMultGrid", "erosionGrid",
        "flowAccGrid", "riverIncisionGrid", "depositionGrid",
        "simdFx", "simdOneMinusFx", "simdN00", "simdN10", "simdN01", "simdN11",
        "simdYVals", "simdWVals"
    };

    private static final String[] INT_GRID_NAMES = {
        "classificationBitsGrid", "simdX0", "riverWaterLevelGrid"
    };

    /** Deep copy of every scratchpad output grid, for cross-thread comparison. */
    private record ScratchpadSnapshot(double[][] doubleGrids, int[][] intGrids) {
    }

    /**
     * @throws Exception if the executor fails or a future times out
     */
    @Test
    @DisplayName("Multi-Thread / Multi-Seed Determinism Matrix: every output grid bit-exact")
    void testMatrixDeterminism() throws Exception {
        int threadPoolSize = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        GeoConfig config = GeoConfig.defaultOverworld(1);

        try {
            for (long seed : SEED_MATRIX) {
                ScalarFieldKernel kernel = new ScalarFieldKernel(seed, config);

                WorkerScratchpad baselineSp = new WorkerScratchpad();
                kernel.rasterizeSurfaceChunk(baselineSp, 128, -256);
                ScratchpadSnapshot baseline = snapshot(baselineSp);

                List<Future<ScratchpadSnapshot>> futures = new ArrayList<>();
                for (int t = 0; t < 16; t++) {
                    futures.add(executor.submit(() -> {
                        WorkerScratchpad workerSp = ScratchpadProvider.get();
                        kernel.rasterizeSurfaceChunk(workerSp, 128, -256);
                        return snapshot(workerSp);
                    }));
                }

                for (int t = 0; t < futures.size(); t++) {
                    ScratchpadSnapshot result = futures.get(t).get(30, TimeUnit.SECONDS);
                    compareSnapshots(baseline, result, "seed " + seed + " thread " + t);
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    private ScratchpadSnapshot snapshot(WorkerScratchpad sp) {
        double[][] doubleGrids = {
            sp.macroH0, sp.macroTectonic, sp.macroAge, sp.macroTemp, sp.macroHumid, sp.macroErosion,
            sp.surfaceGrid, sp.h0Grid, sp.gradXGrid, sp.gradZGrid, sp.laplacianGrid,
            sp.ageGrid, sp.tempGrid, sp.humidGrid, sp.climateMultGrid, sp.erosionGrid,
            sp.flowAccGrid, sp.riverIncisionGrid, sp.depositionGrid,
            sp.simdFx, sp.simdOneMinusFx, sp.simdN00, sp.simdN10, sp.simdN01, sp.simdN11,
            sp.simdYVals, sp.simdWVals
        };
        int[][] intGrids = {sp.classificationBitsGrid, sp.simdX0, sp.riverWaterLevelGrid};

        double[][] doubleCopies = new double[doubleGrids.length][];
        for (int i = 0; i < doubleGrids.length; i++) {
            doubleCopies[i] = Arrays.copyOf(doubleGrids[i], doubleGrids[i].length);
        }
        int[][] intCopies = new int[intGrids.length][];
        for (int i = 0; i < intGrids.length; i++) {
            intCopies[i] = Arrays.copyOf(intGrids[i], intGrids[i].length);
        }
        assertEquals(DOUBLE_GRID_NAMES.length, doubleCopies.length, "grid list must match the name table");
        assertEquals(INT_GRID_NAMES.length, intCopies.length, "grid list must match the name table");
        return new ScratchpadSnapshot(doubleCopies, intCopies);
    }

    private void compareSnapshots(ScratchpadSnapshot baseline, ScratchpadSnapshot result, String context) {
        for (int i = 0; i < DOUBLE_GRID_NAMES.length; i++) {
            assertArrayEquals(baseline.doubleGrids[i], result.doubleGrids[i], 0.0,
                context + ": double grid " + DOUBLE_GRID_NAMES[i] + " must be bit-exact");
        }
        for (int i = 0; i < INT_GRID_NAMES.length; i++) {
            assertArrayEquals(baseline.intGrids[i], result.intGrids[i],
                context + ": int grid " + INT_GRID_NAMES[i] + " must be bit-exact");
        }
    }
}
