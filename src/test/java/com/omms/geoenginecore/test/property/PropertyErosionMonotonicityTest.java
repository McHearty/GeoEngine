package com.omms.geoenginecore.test.property;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P-M-02: Erosion non-negativity and finiteness.
 *
 * <p>Total erosion must be non-negative and finite across all seeds.
 * This is a property that must hold regardless of configuration.
 */
public class PropertyErosionMonotonicityTest {

    @Tag("property")

    @Test
    @DisplayName("P-M-02: Total erosion is non-negative and finite")
    void testErosionNonNegativeFinite() {
        for (long seed : SEED_MATRIX) {
            ScalarFieldKernel kernel = kernelWithSeed(seed);
            WorkerScratchpad sp = ScratchpadProvider.get();
            kernel.rasterizeSurfaceChunk(sp, 64, 64);

            // Compute total erosion
            double totalErosion = 0.0;
            for (double v : sp.erosionGrid) {
                totalErosion += v;
            }

            assertTrue(Double.isFinite(totalErosion),
                    "Non-finite total erosion for seed " + seed);
            assertTrue(totalErosion >= 0.0,
                    "Negative total erosion for seed " + seed + ": " + totalErosion);
        }
    }
}
