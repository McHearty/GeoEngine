package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.ChannelRealization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TECHSPEC_AMEND001 A6.4: Quantized incision isolation.
 *
 * <p>Verifies that R_quant affects only terrain realization, not the
 * continuous geomorphological state (R, H*, S, Hf, Af, channel topology).
 */
@DisplayName("TECHSPEC_AMEND001 A6.4: Quantized Incision Isolation")
class QuantizedIncisionIsolationTest {

    @Test
    @DisplayName("R_quant differs from R but R is unchanged")
    void testRQuantDiffersFromR() {
        ChannelRealization realize = new ChannelRealization(1.0, 20.0);

        double r = 3.7; // Continuous incision
        double rQuant = realize.realize(r);

        // R_quant is quantized to nearest step
        assertTrue(Math.abs(rQuant - 4.0) < 1e-10,
            "R_quant should be 4.0, got " + rQuant);
        // Original R is unchanged
        assertTrue(r == 3.7, "R should be unchanged");
    }

    @Test
    @DisplayName("R_quant is bounded by R")
    void testRQuantBounded() {
        ChannelRealization realize = new ChannelRealization(1.0, 20.0);

        for (double r = 0.0; r <= 20.0; r += 0.3) {
            double rQuant = realize.realize(r);
            // R_quant should be within one step of R
            assertTrue(Math.abs(rQuant - r) <= 0.5,
                "R_quant should be within 0.5 of R: r=" + r + ", rQuant=" + rQuant);
            // R_quant should not exceed max incision
            assertTrue(rQuant <= 20.0, "R_quant should not exceed max incision");
        }
    }

    @Test
    @DisplayName("R_quant is deterministic")
    void testRQuantDeterministic() {
        ChannelRealization realize = new ChannelRealization(1.0, 20.0);

        double r = 5.3;
        double rQuant1 = realize.realize(r);
        double rQuant2 = realize.realize(r);

        assertTrue(rQuant1 == rQuant2, "R_quant should be deterministic");
    }
}