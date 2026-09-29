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
 * P-J-03: Explicit timeouts for slow hydrology operations.
 *
 * <p>These tests verify that generation operations complete within
 * reasonable time bounds, preventing hangs or pathological slowdowns.
 */
public class ReliabilityTimeoutTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("P-J-03: Chunk rasterization completes within 10s")
    void testChunkRasterizationTimeout() {
        ScalarFieldKernel kernel = standardKernel();
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 64, 64);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("P-J-03: Region rasterization completes within 60s")
    void testRegionRasterizationTimeout() {
        ScalarFieldKernel kernel = standardKernel();
        rasterizeRegion(kernel, 0, 0);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("P-J-03: Multiple sequential chunks complete within 30s")
    void testSequentialChunksTimeout() {
        ScalarFieldKernel kernel = standardKernel();
        WorkerScratchpad sp = ScratchpadProvider.get();

        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                kernel.rasterizeSurfaceChunk(sp, x * 16, z * 16);
            }
        }
    }
}
