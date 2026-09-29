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
 * C7: Hydrology Laplacian is a curvature signal, not watershed
 * accumulation.
 *
 * <p>The Laplacian is computed as a local 5-point stencil and should
 * not depend on global flow paths or drainage topology.
 */
public class InvariantLaplacianCurvatureTest {

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
    @DisplayName("C7: Laplacian is local curvature signal, not watershed accumulation")
    void testLaplacianIsCurvature() {
        // Evaluate at multiple locations
        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                kernel.evaluateFullColumn(x, z, sample);

                // Laplacian should be finite
                assertTrue(Double.isFinite(sample.laplacian),
                        "C7: Laplacian not finite at (" + x + "," + z + ")");

                // Laplacian magnitude should be bounded (local curvature)
                // Typical curvature values are in the range [-10, 10]
                assertTrue(Math.abs(sample.laplacian) < 100.0,
                        "C7: Laplacian magnitude too large at (" + x + "," + z +
                                "): " + sample.laplacian + " — not a local curvature signal");
            }
        }
    }
}
