package com.omms.geoenginecore.test.phase9;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static com.omms.geoenginecore.test.fixtures.TestFixtures.standardKernel;
import static com.omms.geoenginecore.test.fixtures.TestFixtures.EPS_R;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9 wet river tests (TECHSPEC §149).
 *
 * <p>P9-01: waterSurfaceLevel computed when R≥EPS_R inland.
 * <p>P9-02: WATER placed only for y < waterSurfaceLevel inside corridor (adapter).
 * <p>P9-03: Outside corridor: no channel WATER (adapter).
 *
 * <p>P9-02 and P9-03 are adapter tests requiring @GameTest; only P9-01
 * is implemented here as a headless core test.
 */
public class Phase9WetRiversTest {

    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private GeoSample sample;

    @BeforeEach
    void setUp() {
        config = defaultConfig();
        kernel = standardKernel();
        sample = new GeoSample();
    }

    /**
     * P9-01: waterSurfaceLevel computed when R≥EPS_R inland.
     *
     * <p>For columns with river incision ≥ EPS_R (1.0) and above
     * sea level, the water surface level must be computed as a
     * valid height (0 < waterSurfaceLevel < 256).
     *
     * <p>Note: This test currently fails because waterSurfaceLevel
     * is never computed in the core. It documents the expected
     * behavior for Sprint D implementation.
     */
    @Test
    @DisplayName("P9-01: Water surface level computed when R >= EPS_R inland")
    void testWaterSurfaceLevelComputed() {
        // Find a column with significant incision
        // (requires the channel factor bug to be fixed first)
        boolean foundIncision = false;
        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                kernel.evaluateFullColumn(x, z, sample);
                if (sample.riverIncision >= EPS_R) {
                    foundIncision = true;

                    // Water surface level must be computed
                    assertTrue(sample.waterSurfaceLevel > 0,
                            "waterSurfaceLevel not computed (0) for column with R="
                            + sample.riverIncision + " at (" + x + "," + z + ")");

                    // Water surface level must be reasonable
                    assertTrue(sample.waterSurfaceLevel < 256,
                            "waterSurfaceLevel too high (" + sample.waterSurfaceLevel
                            + ") for column at (" + x + "," + z + ")");

                    // Water surface level should be near the channel bottom
                    // (within a few blocks of the final surface)
                    double expectedLevel = sample.finalSurface;
                    double delta = Math.abs(sample.waterSurfaceLevel - expectedLevel);
                    assertTrue(delta < 5.0,
                            "waterSurfaceLevel (" + sample.waterSurfaceLevel
                            + ") too far from channel bottom (" + expectedLevel
                            + ") at (" + x + "," + z + ")");

                    return;
                }
            }
        }

        // If no incision found, the channel factor bug is still present
        // This is a product bug (P2-10), not a test bug
        assertTrue(foundIncision,
                "No columns with R >= EPS_R found — channel factor bug (P2-10) still present");
    }

    // P9-02 and P9-03 are adapter tests requiring @GameTest
    // They validate that the Forge adapter places water blocks
    // below the waterSurfaceLevel inside the channel corridor
    // and no water outside the corridor.
}
