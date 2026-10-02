package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.ReconnectionField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TECHSPEC_AMEND001 A6.11: Reconnection bounds.
 *
 * <p>Verifies that reconnection search respects radius and sample budget.
 */
@DisplayName("TECHSPEC_AMEND001 A6.11: Reconnection Bounds")
class ReconnectionBoundTest {

    @Test
    @DisplayName("Search respects radius bound")
    void testRadiusBound() {
        ReconnectionField recon = new ReconnectionField(100.0, 1000, 8);

        // Within radius: should find target (simplified check)
        boolean found = recon.findReconnectionTarget(null, 10.0, 10.0, 0.0, 0.0);
        assertTrue(found, "Should find target within radius");

        // Beyond radius: should not find target
        recon = new ReconnectionField(10.0, 1000, 8);
        found = recon.findReconnectionTarget(null, 100.0, 100.0, 0.0, 0.0);
        assertTrue(!found, "Should not find target beyond radius");
    }

    @Test
    @DisplayName("Search respects sample budget")
    void testSampleBudget() {
        ReconnectionField recon = new ReconnectionField(1000.0, 5, 8);

        // shouldContinueSearch returns false after budget exceeded
        assertTrue(recon.shouldContinueSearch(0));
        assertTrue(recon.shouldContinueSearch(5));
        assertTrue(!recon.shouldContinueSearch(6));
    }
}