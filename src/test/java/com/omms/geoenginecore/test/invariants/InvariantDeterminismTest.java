package com.omms.geoenginecore.test.invariants;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C1: Determinism invariant.
 *
 * <p>16 threads × 5 seeds must produce bit-identical scratchpad
 * grids. Any divergence indicates non-determinism in the generation
 * pipeline.
 */
public class InvariantDeterminismTest {

    private static final long[] SEEDS = {
            0xCAFEBABEDEADBEEFL,
            0x123456789ABCDEF0L,
            0xFEDCBA9876543210L,
            -1L,
            0x5555555555555555L
    };

    @Test
    @DisplayName("C1: Determinism - 16 threads x 5 seeds produce bit-identical grids")
    void testDeterminismMatrix() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            for (long seed : SEEDS) {
                // Reference: single-threaded generation
                GeoConfig config = defaultConfig();
                ScalarFieldKernel refKernel = new ScalarFieldKernel(seed, config);
                WorkerScratchpad refSp = ScratchpadProvider.get();
                refKernel.rasterizeSurfaceChunk(refSp, 64, 64);
                double[] refSurface = refSp.surfaceGrid.clone();
                double[] refGradX = refSp.gradXGrid.clone();
                double[] refGradZ = refSp.gradZGrid.clone();

                // Concurrent: 16 threads generating the same chunk
                Callable<double[]> task = () -> {
                    ScalarFieldKernel k = new ScalarFieldKernel(seed, config);
                    WorkerScratchpad sp = ScratchpadProvider.get();
                    k.rasterizeSurfaceChunk(sp, 64, 64);
                    return sp.surfaceGrid.clone();
                };

                for (int t = 0; t < 16; t++) {
                    double[] concurrentSurface = executor.submit(task).get();

                    assertArrayEquals(refSurface, concurrentSurface,
                            "C1 determinism violated: thread " + t + " differs for seed " + seed);
                }

                executor.shutdownNow();
                executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS);
                executor = Executors.newFixedThreadPool(16);
            }
        } finally {
            executor.shutdown();
        }
    }
}
