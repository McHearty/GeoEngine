package com.omms.geoenginecore.test;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class MultiSeedMultiThreadMatrixTest {
    private static final long[] SEED_MATRIX = {
        0x123456789ABCDEFL,
        0xDEADBEEFCAFEBABEL,
        0x0000000000000001L,
        0x7FFFFFFFFFFFFFFFL,
        0xFEDCBA9876543210L
    };

    @Test
    @DisplayName("Multi-Thread / Multi-Seed Determinism Matrix")
    void testMatrixDeterminism() throws Exception {
        int threadPoolSize = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        GeoConfig config = GeoConfig.defaultOverworld(1);

        try {
            for (long seed : SEED_MATRIX) {
                ScalarFieldKernel kernel = new ScalarFieldKernel(seed, config);

                WorkerScratchpad baselineSp = new WorkerScratchpad();
                kernel.rasterizeSurfaceChunk(baselineSp, 128, -256);
                double[] baseline = new double[WorkerScratchpad.CHUNK_SURFACE_SIZE];
                System.arraycopy(baselineSp.surfaceGrid, 0, baseline, 0, baseline.length);

                List<Future<double[]>> futures = new ArrayList<>();
                for (int t = 0; t < 16; t++) {
                    futures.add(executor.submit(() -> {
                        WorkerScratchpad workerSp = ScratchpadProvider.get();
                        kernel.rasterizeSurfaceChunk(workerSp, 128, -256);
                        double[] copy = new double[WorkerScratchpad.CHUNK_SURFACE_SIZE];
                        System.arraycopy(workerSp.surfaceGrid, 0, copy, 0, copy.length);
                        return copy;
                    }));
                }

                for (Future<double[]> future : futures) {
                    double[] threadResult = future.get(5, TimeUnit.SECONDS);
                    assertArrayEquals(baseline, threadResult, 0.0);
                }
            }
        } finally {
            executor.shutdown();
        }
    }
}
