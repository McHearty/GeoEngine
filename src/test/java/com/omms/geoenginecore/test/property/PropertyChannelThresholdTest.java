package com.omms.geoenginecore.test.property;

import com.omms.geoenginecore.hydrology.ChannelField;
import com.omms.geoenginecore.hydrology.RiverField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P-M-03: Channel initiation threshold boundary conditions.
 *
 * <p>Test the exact boundary conditions at the incision and channel width
 * thresholds (A_f = 1.8 for incision, A_f < 2.5 for width).
 */
public class PropertyChannelThresholdTest {

    private static final double EPSILON = 1e-9;

    @Tag("property")

    @Test
    @DisplayName("P-M-03: Incision threshold boundary - no incision at A_f = 1.8")
    void testIncisionThresholdBoundary() {
        RiverField rf = new RiverField(defaultConfig());

        // At exactly A_f = 1.8, incision should be zero (threshold not exceeded)
        double incisionAtThreshold = rf.computeIncision(1.8, 0.1, 1.0);
        assertEquals(0.0, incisionAtThreshold, EPSILON,
                "Incision should be zero at exactly the threshold A_f=1.8");

        // Just above threshold, incision should be non-zero
        double incisionAboveThreshold = rf.computeIncision(1.81, 0.1, 1.0);
        assertTrue(incisionAboveThreshold > 0.0,
                "Incision should be non-zero just above the threshold");
    }

    @Tag("property")

    @Test
    @DisplayName("P-M-03: Channel width threshold boundary - no width at A_f < 2.5")
    void testWidthThresholdBoundary() {
        // Below A_f = 2.5, width should be zero
        double widthBelowThreshold = ChannelField.getWidth(2.49);
        assertEquals(0.0, widthBelowThreshold, EPSILON,
                "Width should be zero below the threshold A_f=2.5");

        // At A_f = 2.5, width should be non-zero
        double widthAtThreshold = ChannelField.getWidth(2.5);
        assertTrue(widthAtThreshold > 0.0,
                "Width should be non-zero at the threshold A_f=2.5");
    }
}
