package com.omms.geoenginecore.test.phase4;

import com.omms.geoenginecore.hydrology.ChannelRealization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Discrete realization tests (TECHSPEC_AMEND001 A3.3).
 *
 * <p>Verifies the R vs R_quant distinction: continuous incision
 * magnitude R is separate from discrete realization R_quant with
 * vertical quantization.
 */
public class DiscreteRealizationTest {

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("R_quant is quantized to stepDeltaY")
    void testRQuantIsQuantized() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // 3.7 blocks -> 4.0 blocks (nearest)
        assertEquals(4.0, realization.realize(3.7), 1e-9);

        // 4.2 blocks -> 4.0 blocks (nearest)
        assertEquals(4.0, realization.realize(4.2), 1e-9);

        // 5.5 blocks -> 6.0 blocks (nearest)
        assertEquals(6.0, realization.realize(5.5), 1e-9);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("R_quant is bounded by maxIncisionDepth")
    void testRQuantIsBounded() {
        ChannelRealization realization = new ChannelRealization(1.0, 10.0);

        // Above max: clamped to max
        assertEquals(10.0, realization.realize(15.0), 1e-9);

        // Below max: quantized normally
        assertEquals(5.0, realization.realize(4.7), 1e-9);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("R vs R_quant: continuous vs discrete")
    void testRvsRQuantDistinction() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // R = 3.7 (continuous)
        double r = 3.7;
        // R_quant = 4.0 (discrete)
        double rQuant = realization.realize(r);

        // They differ
        assertNotEquals(r, rQuant);
        // R_quant is quantized
        assertEquals(4.0, rQuant, 1e-9);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("stepDeltaY=1.0: no vertical quantization effect")
    void testStepDeltaYOneHasNoEffect() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // Integer values are unchanged
        assertEquals(3.0, realization.realize(3.0), 1e-9);
        assertEquals(5.0, realization.realize(5.0), 1e-9);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("stepDeltaY=0.5: finer quantization")
    void testFinerQuantization() {
        ChannelRealization realization = new ChannelRealization(0.5, 20.0);

        // 3.2 -> 3.0
        assertEquals(3.0, realization.realize(3.2), 1e-9);

        // 3.3 -> 3.5
        assertEquals(3.5, realization.realize(3.3), 1e-9);

        // 3.7 -> 3.5
        assertEquals(3.5, realization.realize(3.7), 1e-9);
    }

    @Tag("phase4")
    @Test
    @DisplayName("Realization is deterministic")
    void testRealizationDeterministic() {
        ChannelRealization r1 = new ChannelRealization(1.0, 20.0);
        ChannelRealization r2 = new ChannelRealization(1.0, 20.0);

        for (double r = 0.0; r <= 10.0; r += 0.5) {
            assertEquals(r1.realize(r), r2.realize(r), 1e-9);
        }
    }

    @Tag("phase4")
    @Test
    @DisplayName("Floor realization is conservative")
    void testFloorRealization() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // Floor: always <= continuous value
        assertTrue(realization.realizeFloor(3.7) <= 3.7);
        assertTrue(realization.realizeFloor(4.2) <= 4.2);
        assertTrue(realization.realizeFloor(5.5) <= 5.5);

        // Floor: quantized
        assertEquals(3.0, realization.realizeFloor(3.7), 1e-9);
        assertEquals(4.0, realization.realizeFloor(4.2), 1e-9);
        assertEquals(5.0, realization.realizeFloor(5.5), 1e-9);
    }

    @Tag("phase4")
    @Test
    @DisplayName("Ceiling realization is aggressive")
    void testCeilingRealization() {
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // Ceiling: always >= continuous value
        assertTrue(realization.realizeCeiling(3.7) >= 3.7);
        assertTrue(realization.realizeCeiling(4.2) >= 4.2);
        assertTrue(realization.realizeCeiling(5.5) >= 5.5);

        // Ceiling: quantized
        assertEquals(4.0, realization.realizeCeiling(3.7), 1e-9);
        assertEquals(5.0, realization.realizeCeiling(4.2), 1e-9);
        assertEquals(6.0, realization.realizeCeiling(5.5), 1e-9);
    }
}
