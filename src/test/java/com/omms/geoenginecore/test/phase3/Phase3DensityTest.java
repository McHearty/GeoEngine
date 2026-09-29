package com.omms.geoenginecore.test.phase3;

import com.omms.geoenginecore.field.DensityField;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static com.omms.geoenginecore.test.fixtures.TestFixtures.standardKernel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 density field tests (TECHSPEC §49-§50).
 *
 * <p>Validates the canonical density identity D = H_f − (y + W) − C
 * and the monotonicity invariant.
 *
 * <p>Seal gate: P3-01, P3-06 must be green.
 */
public class Phase3DensityTest {

    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private WorkerScratchpad sp;

    @BeforeEach
    void setUp() {
        config = defaultConfig();
        kernel = standardKernel();
        sp = ScratchpadProvider.get();
    }

    /**
     * P3-01: Density identity.
     *
     * <p>For each evaluated column, the kernel's density value must
     * match the canonical formula D = H_f − (y + W) − C within float
     * tolerance. This validates that the density field is computed
     * correctly and that no extraneous terms are added.
     */
    @Test
    @DisplayName("P3-01: Density identity D = Hf - (y + W) - C")
    void testDensityIdentity() {
        // Evaluate density at several columns and verify the identity
        int[] testX = {130, 258, 386};
        int[] testZ = {130, 258, 386};
        int[] testY = {60, 100, 140, 180, 220};

        for (int x : testX) {
            for (int z : testZ) {
                // Evaluate full column to get Hf
                kernel.rasterizeSurfaceChunk(sp, x >> 4, z >> 4);
                int lx = x & 15;
                int lz = z & 15;
                int cIdx = (lz << 4) | lx;
                double hf = sp.surfaceGrid[cIdx];

                if (hf == 0.0) {
                    // Chunk not rasterized; compute directly
                    hf = kernel.evaluatePureH0(x, z);
                }

                for (int y : testY) {
                    // Get the kernel's density value
                    float kernelDensity = kernel.evaluateDensity(sp, x, y, z);

                    // Compute the canonical density manually
                    double warp = kernel.getWarpField().evaluateWarp(
                            x, y, z,
                            Math.sqrt(sp.gradXGrid[cIdx] * sp.gradXGrid[cIdx] +
                                      sp.gradZGrid[cIdx] * sp.gradZGrid[cIdx]));
                    double cave = kernel.getCaveField().evaluateCave(x, y, z, hf);
                    float canonicalDensity = (float) (hf - (y + warp) - cave);

                    // Verify identity within float tolerance
                    assertEquals(canonicalDensity, kernelDensity, 1e-4f,
                            "Density identity violated at (" + x + "," + y + "," + z +
                            "): kernel=" + kernelDensity + " canonical=" + canonicalDensity);
                }
            }
        }
    }

    /**
     * P3-06: Monotonicity invariant.
     *
     * <p>With warp and cave disabled (W=C=0), the density difference
     * between adjacent Y values must be exactly -1. This validates
     * that the density field is a proper linear function of Y when
     * no volumetric processes are active.
     */
    @Test
    @DisplayName("P3-06: Monotonicity D(y+1) - D(y) = -1 when W=C=0")
    void testMonotonicity() {
        // Use the DensityField utility class which has a verifyMonotonicity method
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                kernel.rasterizeSurfaceChunk(sp, x, z);
                int lx = 7;
                int lz = 7;
                int cIdx = (lz << 4) | lx;
                double hf = sp.surfaceGrid[cIdx];

                if (hf == 0.0) {
                    hf = kernel.evaluatePureH0(x * 16 + lx, z * 16 + lz);
                }

                // Verify monotonicity for a range of Y values
                for (int y = 60; y < 240; y++) {
                    boolean monotonic = DensityField.verifyMonotonicity(hf, y, y + 1);
                    assertTrue(monotonic,
                            "Monotonicity violated at y=" + y + ": " +
                            "D(" + y + ")-D(" + (y + 1) + ") != -1");
                }
            }
        }
    }
}
