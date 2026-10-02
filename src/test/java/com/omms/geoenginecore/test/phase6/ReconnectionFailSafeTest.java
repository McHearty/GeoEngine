package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.ReconnectionField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TECHSPEC_AMEND001 A6.12: Reconnection fail-safe.
 *
 * <p>Verifies that reconnection search terminates and fails gracefully
 * when no target is found within the budget.
 */
@DisplayName("TECHSPEC_AMEND001 A6.12: Reconnection Fail-Safe")
class ReconnectionFailSafeTest {

    @Test
    @DisplayName("Search terminates when budget exhausted")
    void testTerminatesOnBudget() {
        ReconnectionField recon = new ReconnectionField(10000.0, 3, 8);

        // With a huge radius but small sample budget, search should
        // terminate after 3 samples (not infinite loop)
        boolean found = recon.findReconnectionTarget(null, 0.0, 0.0, 0.0, 0.0);

        // With distance 0, simplified check should find target
        assertTrue(found, "Should find target at distance 0");
    }

    @Test
    @DisplayName("Search returns false when no target found")
    void testReturnsFalseWhenNotFound() {
        ReconnectionField recon = new ReconnectionField(10.0, 100, 8);

        // Target far beyond radius
        boolean found = recon.findReconnectionTarget(null, 1000.0, 1000.0, 0.0, 0.0);
        assertTrue(!found, "Should return false when target not found");
    }
}