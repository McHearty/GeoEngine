package com.omms.geoenginecore.test.invariants;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C4: Surface height identity invariant.
 *
 * <p>At every column, the final surface height must satisfy:
 * Hf = H0 + deposition
 * (Erosion and incision are already factored into H0 by the pipeline.)
 */
public class InvariantDensityIdentityTest {

    @Tag("invariant")

    @Test
    @DisplayName("C4: Surface height identity - Hf = H0 + deposition")
    void testSurfaceHeightIdentity() {
        GeoConfig config = defaultConfig();
        long seed = 0xCAFEBABEDEADBEEFL;

        ScalarFieldKernel kernel = new ScalarFieldKernel(seed, config);
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 64, 64);

        // Verify surface height identity across all columns
        for (int i = 0; i < sp.surfaceGrid.length; i++) {
            double h0 = sp.h0Grid[i];
            double deposition = sp.depositionGrid[i];
            double expectedHf = h0 + deposition;
            double actualHf = sp.surfaceGrid[i];

            // Allow small tolerance for floating-point arithmetic
            double tolerance = 0.001;
            double diff = Math.abs(actualHf - expectedHf);

            assertTrue(diff <= tolerance,
                    "C4 surface height identity violated at column " + i +
                            ": actual=" + actualHf + " expected=" + expectedHf +
                            " diff=" + diff);
        }
    }
}
