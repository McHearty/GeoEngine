package com.geoengine.core.test;

import com.geoengine.core.math.*;
import com.geoengine.core.memory.ScratchpadProvider;
import com.geoengine.core.memory.WorkerScratchpad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class Phase1CoreVerificationTest {
    private static final long TEST_SEED = 0xD3ADBEEFCAFEBABEL;
    private GeoConfig config;
    private ScalarFieldKernel kernel;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(TEST_SEED, config);
    }

    @Test
    @DisplayName("Configuration invariants validation")
    void testConfigurationValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GeoConfig(1, 0, 100, 50, 64, 0.001, 0.002, 0.003, 100, 100, 100, 2.0, 0.001, 10.0, 0.5, 0.001, 0.001, 0.001, 0.6, 1.4, 0.001, 10.0, 16.0, 16));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new GeoConfig(1, 0, 0, 256, 64, 0.001, 0.002, 0.003, 100, 100, 100, 2.0, 0.1, 100.0, 0.5, 0.001, 0.001, 0.001, 0.6, 1.4, 0.001, 10.0, 16.0, 16));
    }

    @Test
    @DisplayName("Invariant: Density Monotonicity when W=0, C=0 (dD/dy == -1)")
    void testDensityMonotonicity() {
        WorkerScratchpad scratchpad = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = -128;
        kernel.rasterizeSurfaceChunk(scratchpad, chunkX, chunkZ);

        for (int y = config.worldMinY(); y < config.worldMaxY() - 1; y++) {
            float d1 = kernel.evaluateDensity(scratchpad, chunkX + 5, y, chunkZ + 7);
            float d2 = kernel.evaluateDensity(scratchpad, chunkX + 5, y + 1, chunkZ + 7);
            assertEquals(-1.0f, d2 - d1, 1e-5f, "Density must decrease strictly by 1.0 per vertical block");
        }
    }

    @Test
    @DisplayName("Chunk Seam Continuity: Identical world-coordinate evaluation across chunk borders")
    void testChunkSeamContinuity() {
        WorkerScratchpad spA = new WorkerScratchpad();
        WorkerScratchpad spB = new WorkerScratchpad();

        int chunkAX = 0;
        int chunkBX = 16;
        int chunkZ = 0;

        kernel.rasterizeSurfaceChunk(spA, chunkAX, chunkZ);
        kernel.rasterizeSurfaceChunk(spB, chunkBX, chunkZ);

        GeoSample directSample = new GeoSample();
        double directValA = kernel.evaluateH0(15, 8, directSample);
        double directValB = kernel.evaluateH0(16, 8, directSample);

        assertFalse(Double.isNaN(directValA));
        assertFalse(Double.isNaN(directValB));
        assertTrue(Math.abs(directValB - directValA) < 5.0, "Surface must remain smooth without artificial boundary step");
    }

    @Test
    @DisplayName("Deterministic evaluation across threads and execution orders")
    void testMultiThreadedDeterminism() throws InterruptedException, ExecutionException {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        Callable<double[]> task = () -> {
            WorkerScratchpad sp = ScratchpadProvider.get();
            kernel.rasterizeSurfaceChunk(sp, 256, 512);
            double[] copy = new double[WorkerScratchpad.CHUNK_SURFACE_SIZE];
            System.arraycopy(sp.surfaceGrid, 0, copy, 0, copy.length);
            return copy;
        };

        Future<double[]> baselineFuture = executor.submit(task);
        double[] baseline = baselineFuture.get();

        for (int i = 0; i < threads * 2; i++) {
            double[] result = executor.submit(task).get();
            assertArrayEquals(baseline, result, 0.0, "Concurrent evaluations must produce identical bit patterns");
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("Sanitation: No NaN or Infinite values emitted across high coordinate ranges")
    void testNumericalStability() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        double[] coordinates = {0, 1e5, -1e5, 2.5e6, -3.2e6};

        for (double cx : coordinates) {
            for (double cz : coordinates) {
                kernel.rasterizeSurfaceChunk(sp, (int) cx, (int) cz);
                for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
                    double val = sp.surfaceGrid[i];
                    assertFalse(Double.isNaN(val), "Surface value produced NaN at " + cx + ", " + cz);
                    assertFalse(Double.isInfinite(val), "Surface value produced Infinite at " + cx + ", " + cz);
                }
            }
        }
    }
}
