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
 * P-M-05: Surface height range property.
 *
 * <p>All surface heights must be within the valid Minecraft world range
 * [0, 384] for any seed. This is a fundamental property of the terrain
 * generation pipeline.
 */
public class PropertySurfaceRangeTest {

    @Tag("property")

    @Test
    @DisplayName("P-M-05: All surface heights are within [0, 384] for multiple seeds")
    void testSurfaceHeightRange() {
        for (long seed : SEED_MATRIX) {
            ScalarFieldKernel kernel = kernelWithSeed(seed);
            WorkerScratchpad sp = ScratchpadProvider.get();
            kernel.rasterizeSurfaceChunk(sp, 64, 64);

            for (double h : sp.surfaceGrid) {
                assertTrue(h >= 0.0 && h <= 384.0,
                        "Surface height " + h + " out of range [0, 384] for seed " + seed);
            }
        }
    }
}
