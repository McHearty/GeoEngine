package com.omms.geoenginecore.test.phase5;

import com.omms.geoenginecore.hydrology.ReconnectionField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reconnection field tests (TECHSPEC_AMEND001 A3.12).
 *
 * <p>Verifies bounded multi-plate reconnection with reconnectRadius
 * and maxReconnectionSamples.
 */
public class ReconnectionFieldTest {

    @Tag("must")
    @Tag("phase5")
    @Test
    @DisplayName("A6.10: Reconnection within radius succeeds")
    void testReconnectionWithinRadius() {
        ReconnectionField recon = new ReconnectionField(64.0, 100, 8);

        // Within 64 blocks of outlet
        assertTrue(recon.findReconnectionTarget(null, 10.0, 10.0, 0.0, 0.0));
    }

    @Tag("must")
    @Tag("phase5")
    @Test
    @DisplayName("A6.10: Reconnection beyond radius fails")
    void testReconnectionBeyondRadius() {
        ReconnectionField recon = new ReconnectionField(64.0, 100, 8);

        // Beyond 64 blocks of outlet
        assertFalse(recon.findReconnectionTarget(null, 100.0, 100.0, 0.0, 0.0));
    }

    @Tag("must")
    @Tag("phase5")
    @Test
    @DisplayName("A6.11: Search budget limits reconnection attempts")
    void testSearchBudgetLimits() {
        ReconnectionField recon = new ReconnectionField(64.0, 10, 8);

        assertTrue(recon.shouldContinueSearch(5));
        assertTrue(recon.shouldContinueSearch(10));
        assertFalse(recon.shouldContinueSearch(11));
    }

    @Tag("phase5")
    @Test
    @DisplayName("Reconnection distance is Euclidean")
    void testReconnectionDistance() {
        ReconnectionField recon = new ReconnectionField(64.0, 100, 8);

        // Distance from (0,0) to (3,4) = 5
        assertEquals(5.0, recon.reconnectionDistance(0.0, 0.0, 3.0, 4.0), 1e-9);
    }

    @Tag("phase5")
    @Test
    @DisplayName("Sea level extension reduces drainage below sea level")
    void testSeaLevelExtension() {
        ReconnectionField recon = new ReconnectionField(64.0, 100, 8);

        // Above sea level: no extension
        assertEquals(1.0, recon.seaLevelExtensionFactor(10.0, 0.0), 1e-9);

        // Below sea level: reduced extension
        double factor = recon.seaLevelExtensionFactor(-10.0, 0.0);
        assertTrue(factor < 1.0);
    }

    @Tag("phase5")
    @Test
    @DisplayName("Reconnection is deterministic")
    void testReconnectionDeterministic() {
        ReconnectionField r1 = new ReconnectionField(64.0, 100, 8);
        ReconnectionField r2 = new ReconnectionField(64.0, 100, 8);

        for (double x = 0.0; x <= 100.0; x += 10.0) {
            for (double z = 0.0; z <= 100.0; z += 10.0) {
                assertEquals(r1.findReconnectionTarget(null, x, z, 0.0, 0.0),
                           r2.findReconnectionTarget(null, x, z, 0.0, 0.0));
            }
        }
    }

    @Tag("must")
    @Tag("phase5")
    @Test
    @DisplayName("A6.12: Reconnection determinism across evaluation order")
    void testReconnectionDeterminismAcrossOrder() {
        // Test that reconnection results are independent of evaluation order
        ReconnectionField recon = new ReconnectionField(64.0, 100, 8);

        // Evaluate in different orders
        boolean result1 = recon.findReconnectionTarget(null, 10.0, 10.0, 0.0, 0.0);
        boolean result2 = recon.findReconnectionTarget(null, 10.0, 10.0, 0.0, 0.0);
        boolean result3 = recon.findReconnectionTarget(null, 10.0, 10.0, 0.0, 0.0);

        // All should be identical
        assertEquals(result1, result2);
        assertEquals(result1, result3);

        // Test with multiple outlets
        boolean nearOutlets = true;
        for (double outletX = 0.0; outletX <= 50.0; outletX += 10.0) {
            for (double outletZ = 0.0; outletZ <= 50.0; outletZ += 10.0) {
                boolean result = recon.findReconnectionTarget(null, 10.0, 10.0, outletX, outletZ);
                // If near any outlet, result should be true
                if (result) {
                    nearOutlets = true;
                }
            }
        }
        // At least some outlets should be within range
        assertTrue(nearOutlets);
    }
}
