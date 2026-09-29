package com.omms.geoenginecore.test.invariants;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C14: No steady-state heap allocation in hot loops.
 *
 * <p>Multiple rasterization calls should not cause unbounded heap
 * growth. Allocations should be bounded and garbage collected.
 */
public class InvariantAllocationTest {

    private static final int RASTERIZATION_COUNT = 50;

    @Tag("invariant")
    @Tag("must")
    @Test
    @DisplayName("C14: No steady-state heap allocation in hot loops")
    void testNoUnboundedAllocation() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        GeoConfig config = defaultConfig();

        // Warmup: trigger class loading and JIT
        ScalarFieldKernel warmupKernel = new ScalarFieldKernel(0L, config);
        warmupKernel.rasterizeSurfaceChunk(ScratchpadProvider.get(), 64, 64);
        System.gc();

        long heapBefore = memoryBean.getHeapMemoryUsage().getUsed();

        // Perform multiple rasterizations
        for (int i = 0; i < RASTERIZATION_COUNT; i++) {
            ScalarFieldKernel kernel = new ScalarFieldKernel(i, config);
            kernel.rasterizeSurfaceChunk(ScratchpadProvider.get(), 64, 64);
        }

        System.gc();
        long heapAfter = memoryBean.getHeapMemoryUsage().getUsed();

        long heapDelta = heapAfter - heapBefore;

        // Allow for some growth (kernel objects, scratchpads), but not unbounded
        // Each kernel + scratchpad allocates ~2.5KB; 50 rasterizations = ~125KB
        // Allow up to 200KB to account for JVM overhead and GC timing
        long expectedMaxGrowth = 200_000;

        assertTrue(heapDelta < expectedMaxGrowth,
                "C14: Unbounded heap growth detected. Delta: " + heapDelta +
                        " bytes over " + RASTERIZATION_COUNT + " rasterizations. " +
                        "Expected < " + expectedMaxGrowth + " bytes.");
    }
}
