package com.omms.geoenginecore.test.property;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P-M-04: Seed chaos / adversarial seed robustness.
 *
 * <p>Test with a stream of random and adversarial seeds to ensure
 * the kernel never produces NaN, Infinity, or crashes.
 */
public class PropertySeedRobustnessTest {

    @Test
    @DisplayName("P-M-04: Kernel produces finite values for random seeds")
    void testRandomSeedRobustness() {
        Random rng = new Random(0xBEEF);

        for (int i = 0; i < 20; i++) {
            long seed = rng.nextLong();
            ScalarFieldKernel kernel = kernelWithSeed(seed);
            WorkerScratchpad sp = ScratchpadProvider.get();
            kernel.rasterizeSurfaceChunk(sp, 64, 64);

            // Verify all surface heights are finite and within range
            for (double h : sp.surfaceGrid) {
                assertTrue(Double.isFinite(h),
                        "Non-finite surface height with seed " + seed);
                assertTrue(h >= 0.0 && h <= 512.0,
                        "Surface height out of range with seed " + seed + ": " + h);
            }
        }
    }

    @Test
    @DisplayName("P-M-04: Kernel handles adversarial seeds (all 1s, all 0s, -1)")
    void testAdversarialSeeds() {
        long[] adversarialSeeds = {0, -1, 0xFFFFFFFFFFFFFFFFL, 0xAAAAAAAAAAAAAAAAL, 0x5555555555555555L};

        for (long seed : adversarialSeeds) {
            ScalarFieldKernel kernel = kernelWithSeed(seed);
            WorkerScratchpad sp = ScratchpadProvider.get();
            kernel.rasterizeSurfaceChunk(sp, 64, 64);

            // Verify finite values
            for (double h : sp.surfaceGrid) {
                assertTrue(Double.isFinite(h),
                        "Non-finite surface height with adversarial seed " + seed);
            }
        }
    }
}
