package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.BankField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TECHSPEC_AMEND001 A6.2: Bank continuity across channel centerline.
 *
 * <p>Verifies that bank displacement is continuous (C⁰) across the
 * channel centerline and bank edges.
 */
@DisplayName("TECHSPEC_AMEND001 A6.2: Bank Continuity")
class BankContinuityTest {

    private static final double EPSILON = 1e-10;

    @Test
    @DisplayName("Bank displacement is continuous across centerline")
    void testCenterlineContinuity() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);

        // Distance approaching centerline from left
        double dispLeft = bank.displacement(-0.001);
        // Distance at centerline
        double dispAt = bank.displacement(0.0);
        // Distance approaching centerline from right
        double dispRight = bank.displacement(0.001);

        assertTrue(Math.abs(dispLeft - dispAt) < EPSILON,
            "Discontinuous at centerline (left): " + dispLeft + " vs " + dispAt);
        assertTrue(Math.abs(dispRight - dispAt) < EPSILON,
            "Discontinuous at centerline (right): " + dispRight + " vs " + dispAt);
    }

    @Test
    @DisplayName("Bank displacement is continuous across inner bank edge")
    void testInnerBankContinuity() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);
        double halfWidth = 2.0; // bankWidth / 2

        // Check limit from left (channel floor)
        double dispFloor = bank.displacement(halfWidth - 1e-12);
        // At inner bank edge
        double dispEdge = bank.displacement(halfWidth);
        // Check limit from right (bank slope)
        double dispBank = bank.displacement(halfWidth + 1e-12);

        assertTrue(Math.abs(dispFloor - dispEdge) < EPSILON,
            "Discontinuous at inner bank (floor side): " + dispFloor + " vs " + dispEdge);
        assertTrue(Math.abs(dispBank - dispEdge) < EPSILON,
            "Discontinuous at inner bank (bank side): " + dispBank + " vs " + dispEdge);
    }

    @Test
    @DisplayName("Bank displacement is continuous across outer bank edge")
    void testOuterBankContinuity() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);
        double bankWidth = 4.0;

        // Check limit from left (bank slope)
        double dispBank = bank.displacement(bankWidth - 1e-12);
        // At outer bank edge
        double dispEdge = bank.displacement(bankWidth);
        // Check limit from right (floodplain ramp)
        double dispPlain = bank.displacement(bankWidth + 1e-12);

        assertTrue(Math.abs(dispBank - dispEdge) < EPSILON,
            "Discontinuous at outer bank (bank side): " + dispBank + " vs " + dispEdge);
        assertTrue(Math.abs(dispPlain - dispEdge) < EPSILON,
            "Discontinuous at outer bank (plain side): " + dispPlain + " vs " + dispEdge);
    }
}