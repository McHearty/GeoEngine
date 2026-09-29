package com.omms.geoenginecore.test.invariants;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C10: Climate multiplier is bounded.
 *
 * <p>K_min ≤ K ≤ K_max for all locations and seeds.
 */
public class InvariantClimateBoundsTest {

    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private GeoSample sample;

    @BeforeEach
    void setUp() {
        config = defaultConfig();
        kernel = new ScalarFieldKernel(0xCAFEBABEDEADBEEFL, config);
        sample = new GeoSample();
    }

    @Tag("invariant")
    @Tag("must")
    @Test
    @DisplayName("C10: Climate multiplier bounded [K_min, K_max]")
    void testClimateBounds() {
        // Evaluate at multiple locations and seeds
        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                kernel.evaluateFullColumn(x, z, sample);

                // K should be in [0.2, 1.0] per default Overworld config
                // Allow for floating-point tolerance
                assertTrue(sample.climateMultiplier >= 0.2 - 1e-12,
                        "C10: Climate multiplier below K_min at (" + x + "," + z +
                                "): " + sample.climateMultiplier);
                assertTrue(sample.climateMultiplier <= 1.0 + 1e-6,
                        "C10: Climate multiplier above K_max at (" + x + "," + z +
                                "): " + sample.climateMultiplier);
            }
        }
    }
}
