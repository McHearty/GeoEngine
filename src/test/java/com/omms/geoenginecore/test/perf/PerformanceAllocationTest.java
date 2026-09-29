package com.omms.geoenginecore.test.perf;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.TimeUnit;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.*;

/**
 * P-J-01: Allocation measurement for chunk rasterization.
 *
 * <p>These tests measure heap allocation but do NOT fail on allocation alone.
 * They serve as regression indicators for memory behavior.
 */
public class PerformanceAllocationTest {

    private static final MemoryMXBean MEMORY_MX = ManagementFactory.getMemoryMXBean();

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("P-J-01: Chunk rasterization allocation (report-only)")
    void testChunkRasterizationAllocation() {
        ScalarFieldKernel kernel = standardKernel();
        WorkerScratchpad sp = ScratchpadProvider.get();

        // Warmup
        for (int i = 0; i < 3; i++) {
            kernel.rasterizeSurfaceChunk(sp, 64, 64);
        }

        // Measure allocation
        System.gc();
        long beforeHeap = MEMORY_MX.getHeapMemoryUsage().getUsed();

        int iterations = 10;
        for (int i = 0; i < iterations; i++) {
            kernel.rasterizeSurfaceChunk(sp, 64, 64);
        }

        long afterHeap = MEMORY_MX.getHeapMemoryUsage().getUsed();
        double avgAllocBytes = (afterHeap - beforeHeap) / iterations;

        System.out.println("[PERF] Chunk rasterization allocation: avg " +
                String.format("%.2f", avgAllocBytes / 1024.0) + " KB per chunk over " + iterations + " iterations");
    }
}
