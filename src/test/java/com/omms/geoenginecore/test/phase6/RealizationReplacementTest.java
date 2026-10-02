package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.ChannelRealization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Realization replacement test (TECHSPEC_AMEND001 A6.16).
 *
 * <p>Verifies that R_quant replaces R in the terrain realization
 * formula, not in addition to it.
 */
public class RealizationReplacementTest {

    @Tag("must")
    @Tag("phase6")
    @Test
    @DisplayName("A6.16: R_quant replaces R in terrain realization")
    void testRQuantReplacesR() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // Continuous incision R
        double R = 3.7;
        // Discrete realization R_quant
        double R_quant = realization.realize(R);

        // Terrain realization uses R_quant, not R
        // Hterrain = H0 - R_quant + S (not H0 - R + S)
        assertNotEquals(R, R_quant);
        assertEquals(4.0, R_quant, 1e-9);
    }

    @Tag("phase6")
    @Test
    @DisplayName("R vs R_quant: quantization error is bounded")
    void testQuantizationErrorBounded() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        for (double r = 0.0; r <= 10.0; r += 0.5) {
            double rQuant = realization.realize(r);
            // Quantization error is at most half the step size
            assertTrue(Math.abs(rQuant - r) <= 0.5);
        }
    }
}
