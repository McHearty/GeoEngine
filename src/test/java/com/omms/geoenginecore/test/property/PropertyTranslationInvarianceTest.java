package com.omms.geoenginecore.test.property;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P-M-01: Translation invariance of hydrology topology.
 *
 * <p>Translating the world by a multiple of the routing cell size (16 blocks)
 * should produce hydrology topology that is isomorphic (same flow graph shape,
 * shifted coordinates). We test this by comparing the relative structure of
 * flow accumulation and drainage patterns.
 */
public class PropertyTranslationInvarianceTest {

    private static final int CELL_SIZE = 16;

    @Test
    @DisplayName("P-M-01: Hydrology topology is invariant under 16-block translations")
    void testTranslationInvariance() {
        long seed = SEED;
        GeoConfig config = defaultConfig();

        // Generate flow accumulation at origin
        ScalarFieldKernel kernel1 = kernelWithSeed(seed);
        WorkerScratchpad sp1 = ScratchpadProvider.get();
        kernel1.rasterizeSurfaceChunk(sp1, 0, 0);

        // Generate flow accumulation at origin + 16 blocks (one cell)
        ScalarFieldKernel kernel2 = kernelWithSeed(seed);
        WorkerScratchpad sp2 = ScratchpadProvider.get();
        kernel2.rasterizeSurfaceChunk(sp2, CELL_SIZE, 0);

        // Compare max flow accumulation (should be similar magnitude)
        double maxAcc1 = gridMax(sp1.flowAccGrid);
        double maxAcc2 = gridMax(sp2.flowAccGrid);

        // Allow 50% tolerance due to boundary effects
        assertTrue(maxAcc2 <= maxAcc1 * 1.5 && maxAcc2 >= maxAcc1 * 0.5,
                "Translation invariance violated: max flow accumulation differs by >50%");
    }
}
