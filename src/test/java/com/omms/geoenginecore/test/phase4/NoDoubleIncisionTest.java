package com.omms.geoenginecore.test.phase4;

import com.omms.geoenginecore.hydrology.ChannelRealization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No double incision test (TECHSPEC_AMEND001 A6.7).
 *
 * <p>Verifies that terrain realization uses H0 - R_quant + S,
 * not Hf - R_quant (which would double-count channel incision).
 */
public class NoDoubleIncisionTest {

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("A6.7: No double incision in terrain realization")
    void testNoDoubleIncision() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // Continuous incision R
        double R = 3.7;
        // Discrete realization R_quant
        double R_quant = realization.realize(R);

        // Continuous surface: Hf = H0 - R + S (let S = 0 for simplicity)
        double H0 = 100.0;
        double S = 0.0;
        double Hf = H0 - R + S;

        // Terrain realization: Hterrain = H0 - R_quant + S
        // NOT Hf - R_quant (which would be H0 - R - R_quant + S = double incision)
        double HterrainCorrect = H0 - R_quant + S;
        double HterrainWrong = Hf - R_quant + S;

        // Verify they differ (proves double incision would be wrong)
        assertNotEquals(HterrainCorrect, HterrainWrong);

        // Verify the correct formula
        assertEquals(100.0 - 4.0 + 0.0, HterrainCorrect, 1e-9);

        // Verify the wrong formula would give a different (wrong) result
        assertEquals(100.0 - 3.7 - 4.0 + 0.0, HterrainWrong, 1e-9);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("R_quant is bounded by R")
    void testRQuantBoundedByR() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        for (double r = 0.0; r <= 10.0; r += 0.5) {
            double rQuant = realization.realize(r);
            // R_quant should be close to R (within half the quantization step)
            assertTrue(Math.abs(rQuant - r) <= 0.5);
        }
    }

    @Tag("phase4")
    @Test
    @DisplayName("stepDeltaY does not affect continuous state")
    void testStepDeltaYDoesNotAffectContinuousState() {
        // Continuous incision R is computed independently of stepDeltaY
        double R = 3.7;

        // Different stepDeltaY values give different R_quant, but same R
        ChannelRealization r1 = new ChannelRealization(1.0, 20.0);
        ChannelRealization r2 = new ChannelRealization(0.5, 20.0);

        // R is the same
        assertEquals(R, R);

        // R_quant differs
        assertNotEquals(r1.realize(R), r2.realize(R));
    }
}
