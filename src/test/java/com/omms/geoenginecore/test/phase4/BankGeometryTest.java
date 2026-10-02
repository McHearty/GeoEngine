package com.omms.geoenginecore.test.phase4;

import com.omms.geoenginecore.hydrology.BankField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bank geometry tests (TECHSPEC_AMEND001 A3.7).
 *
 * <p>Verifies bank displacement from continuous channel fields,
 * operating on the final post-meander channel centerline.
 */
public class BankGeometryTest {

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("Channel floor has zero displacement")
    void testChannelFloorZeroDisplacement() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);

        // At centerline
        assertEquals(0.0, bank.displacement(0.0), 1e-9);

        // Within channel floor
        assertEquals(0.0, bank.displacement(1.0), 1e-9);
        assertEquals(0.0, bank.displacement(2.0), 1e-9);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("Bank displacement increases with distance")
    void testBankDisplacementIncreases() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);

        // On bank: linear increase
        double d1 = bank.displacement(2.5);
        double d2 = bank.displacement(3.0);
        double d3 = bank.displacement(3.5);

        assertTrue(d1 < d2);
        assertTrue(d2 < d3);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("Beyond bank: containment berm")
    void testContainmentBerm() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);

        // Bank top: 2.0 * 0.5 = 1.0
        assertEquals(1.0, bank.displacement(4.0), 1e-9);

        // Beyond bank: 1.0 + 0.5 = 1.5
        assertEquals(1.5, bank.displacement(5.0), 1e-9);
        assertEquals(1.5, bank.displacement(10.0), 1e-9);
    }

    @Tag("must")
    @Tag("phase4")
    @Test
    @DisplayName("isOnFloor and isWithinBank are correct")
    void testFloorAndBankDetection() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);

        // On floor
        assertTrue(bank.isOnFloor(0.0));
        assertTrue(bank.isOnFloor(1.0));
        assertTrue(bank.isOnFloor(2.0));

        // Within bank
        assertFalse(bank.isWithinBank(0.0));
        assertFalse(bank.isWithinBank(1.0));
        assertTrue(bank.isWithinBank(2.5));
        assertTrue(bank.isWithinBank(3.0));
        assertFalse(bank.isWithinBank(5.0));
    }

    @Tag("phase4")
    @Test
    @DisplayName("Bank displacement is continuous")
    void testBankDisplacementContinuous() {
        BankField bank = new BankField(4.0, 0.5, 0.0, 1.0, 0.5);

        // Check continuity at floor/bank boundary (distance = 2.0)
        double d1 = bank.displacement(1.999);
        double d2 = bank.displacement(2.000);
        double d3 = bank.displacement(2.001);

        assertEquals(0.0, d1, 1e-9);
        assertEquals(0.0, d2, 1e-9);
        assertTrue(d3 > 0.0);

        // Check continuity at bank/berm boundary (distance = 4.0)
        // Continuous transition: ramps from 1.0 to 1.5 over 1 block
        double d4 = bank.displacement(3.999);
        double d5 = bank.displacement(4.000);
        double d6 = bank.displacement(4.001);

        assertEquals(1.0, d5, 1e-9);
        assertTrue(d6 > 1.0 && d6 < 1.5); // On the ramp
        // d4 should be close to 1.0 (linear ramp)
        assertTrue(d4 > 0.99 && d4 < 1.0);
    }

    @Tag("phase4")
    @Test
    @DisplayName("Bank noise is bounded")
    void testBankNoiseBounded() {
        BankField bank = new BankField(4.0, 0.5, 0.1, 1.0, 0.5);

        // Noise is bounded by ±bankNoise/2
        double dNoisy = bank.displacementNoisy(1.0, 12345);
        double dBase = bank.displacement(1.0);

        assertTrue(Math.abs(dNoisy - dBase) <= 0.05);
    }

    @Tag("phase4")
    @Test
    @DisplayName("Bank geometry is deterministic")
    void testBankGeometryDeterministic() {
        BankField b1 = new BankField(4.0, 0.5, 0.1, 1.0, 0.5);
        BankField b2 = new BankField(4.0, 0.5, 0.1, 1.0, 0.5);

        for (double d = 0.0; d <= 5.0; d += 0.5) {
            assertEquals(b1.displacement(d), b2.displacement(d), 1e-9);
        }

        // Noisy displacement is deterministic for same seed
        assertEquals(b1.displacementNoisy(1.0, 12345), b2.displacementNoisy(1.0, 12345), 1e-9);
    }
}
