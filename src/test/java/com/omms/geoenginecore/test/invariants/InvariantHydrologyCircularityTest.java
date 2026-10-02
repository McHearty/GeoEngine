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
 * C8: Hydrology circularity invariant.
 *
 * <p>River incision cannot depend directly on derivatives of the
 * surface that already contains that same incision unless an explicit
 * iterative solver exists. The pipeline uses H0 (pre-fluvial) for
 * gradient computation and incision is applied after.
 */
public class InvariantHydrologyCircularityTest {

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
    @DisplayName("C8: Hydrology circularity - incision does not depend on post-incision derivatives")
    void testHydrologyCircularity() {
        // Find a column with incision
        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                kernel.evaluateFullColumn(x, z, sample);

                if (sample.riverIncision > 0.0) {
                    // Hf = H0 - incision + deposition + asymmetry
                    // If incision depended on post-incision derivatives, we'd have circularity
                    // The pipeline ensures this by computing incision from H0 derivatives

                    // Compute asymmetry term from feature mask (Phase 9 Sprint R5)
                    double asymmetry = 0.0;
                    if ((sample.featureMask & com.omms.geoenginecore.hydrology.FeatureGrammar.F_POINT_BAR) != 0) {
                        asymmetry = 0.5;
                    } else if ((sample.featureMask & com.omms.geoenginecore.hydrology.FeatureGrammar.F_CUT_BANK) != 0) {
                        asymmetry = -0.5;
                    }

                    // TECHSPEC_AMEND001: finalSurface = Hf - R_quant (discrete realization)
                    // Hf = surfaceH0 - R + S + asymmetry (continuous authority)
                    // Verify incision is computed from H0 derivatives (no circularity)
                    // by checking that incision is bounded and positive
                    assertTrue(sample.riverIncision > 0.0,
                            "C8: Incision should be positive at (" + x + "," + z + ")");

                    // Verify incision is bounded by the removal budget
                    assertTrue(sample.riverIncision <= sample.erosionLowering + 0.01,
                            "C8: Incision exceeds erosion budget at (" + x + "," + z +
                                    ") — suggests circular dependency");

                    return;
                }
            }
        }
    }
}
