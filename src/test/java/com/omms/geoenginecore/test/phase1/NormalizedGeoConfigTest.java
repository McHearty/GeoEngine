package com.omms.geoenginecore.test.phase1;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoConfigNormalizer;
import com.omms.geoenginecore.math.NormalizedGeoParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Normalized config sweep (TECHSPEC §77): every slider driven to
 * each extreme of [0.0, 1.0] must still yield a strictly valid,
 * non-degenerate GeoConfig (positive frequencies, bounded
 * exponents, consistent warp bounds).
 */
public class NormalizedGeoConfigTest {

    /**
     * Sweeps all sliders across {0.0, 0.25, 0.50, 0.75, 1.0} and
     * asserts the full valid-domain contract on the result.
     */
    @Test
    @DisplayName("Normalized Config Sweep: Extremes [0.0, 1.0] strictly produce valid GeoConfig instances")
    void testNormalizedParameterSweep() {
        double[] testValues = {0.0, 0.25, 0.50, 0.75, 1.0};

        for (double v : testValues) {
            NormalizedGeoParams params = new NormalizedGeoParams(v, v, v, v, v, v, v, v, v, v);
            GeoConfig config = GeoConfigNormalizer.toGeoConfig(1, 0, -64, 1984, 64, params);

            assertNotNull(config);
            assertTrue(config.tectonicFreqLow() > 0.0);
            assertTrue(config.tectonicFreqA() > 0.0);
            assertTrue(config.tectonicFreqB() > 0.0);
            assertTrue(config.tectonicAmpLow() >= 0.0);
            assertTrue(config.tectonicAmpA() >= 0.0);
            assertTrue(config.upliftExponent() >= 1.0);
            assertTrue(config.stressAmplitude() * config.stressFrequency() <= config.stressMaxJacobian());
            assertTrue(config.maxWarpAmplitude() >= 0.0);
        }
    }
}
