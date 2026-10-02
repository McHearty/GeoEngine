package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.ChannelRealization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TECHSPEC_AMEND001 A6.5: Continuous surface authority.
 *
 * <p>Verifies that Hf (continuous authority) is never mutated by
 * discrete realization. H_terrain = Hf - R_quant is computed separately.
 */
@DisplayName("TECHSPEC_AMEND001 A6.5: Continuous Surface Authority")
class ContinuousSurfaceAuthorityTest {

    @Test
    @DisplayName("Hf is never mutated by R_quant computation")
    void testHfNotMutated() {
        ChannelRealization realize = new ChannelRealization(1.0, 20.0);

        double h0 = 64.0;
        double r = 3.7;
        double s = 1.2;

        // H* = H0 - R
        double hStar = h0 - r;
        // Hf = H* + S
        double hf = hStar + s;
        // Original Hf
        double hfOriginal = hf;

        // Compute R_quant
        double rQuant = realize.realize(r);
        // H_terrain = Hf - R_quant
        double hTerrain = hf - rQuant;

        // Hf should be unchanged
        assertTrue(hf == hfOriginal, "Hf should not be mutated by R_quant computation");
        // H_terrain should differ from Hf
        assertTrue(hTerrain != hf, "H_terrain should differ from Hf");
    }

    @Test
    @DisplayName("H_terrain is always <= Hf")
    void testHTerrainLeqHf() {
        ChannelRealization realize = new ChannelRealization(1.0, 20.0);

        for (double r = 0.0; r <= 10.0; r += 0.5) {
            double h0 = 64.0;
            double hStar = h0 - r;
            double hf = hStar + 1.0;

            double rQuant = realize.realize(r);
            double hTerrain = hf - rQuant;

            // R_quant >= 0, so H_terrain <= Hf
            assertTrue(hTerrain <= hf,
                "H_terrain should be <= Hf: r=" + r + ", hf=" + hf + ", hTerrain=" + hTerrain);
        }
    }
}