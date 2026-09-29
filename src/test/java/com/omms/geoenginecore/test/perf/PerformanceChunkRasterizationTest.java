package com.omms.geoenginecore.test.perf;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.*;

/**
 * P-J-01, P-J-02: Performance measurement for chunk rasterization.
 *
 * <p>These tests measure and report timing but do NOT fail on timing alone.
 * They serve as regression indicators, not hard gates.
 */
public class PerformanceChunkRasterizationTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("P-J-01: Chunk rasterization timing (report-only)")
    void testChunkRasterizationTiming() {
        ScalarFieldKernel kernel = standardKernel();
        WorkerScratchpad sp = ScratchpadProvider.get();

        // Warmup
        for (int i = 0; i < 3; i++) {
            kernel.rasterizeSurfaceChunk(sp, 64, 64);
        }

        // Timed runs
        long totalNs = 0;
        int iterations = 10;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            kernel.rasterizeSurfaceChunk(sp, 64, 64);
            totalNs += System.nanoTime() - start;
        }

        double avgMs = (totalNs / iterations) / 1_000_000.0;
        System.out.println("[PERF] Chunk rasterization: avg " + String.format("%.2f", avgMs) + " ms over " + iterations + " iterations");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("P-J-01: Region rasterization timing (report-only)")
    void testRegionRasterizationTiming() {
        ScalarFieldKernel kernel = standardKernel();

        // Warmup
        rasterizeRegion(kernel, 0, 0);

        // Timed run
        long start = System.nanoTime();
        rasterizeRegion(kernel, 0, 0);
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        System.out.println("[PERF] Region rasterization: " + String.format("%.2f", elapsedMs) + " ms");
    }
}
