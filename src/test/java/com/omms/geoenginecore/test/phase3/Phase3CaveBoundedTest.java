package com.omms.geoenginecore.test.phase3;

import com.omms.geoenginecore.field.CaveField;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P3-04: Caves remain bounded — C ≥ 0 and finite everywhere,
 * C == 0 outside the cave envelope.
 */
public class Phase3CaveBoundedTest {

    @Tag("must")
    @Tag("phase3")
    @Test
    @DisplayName("P3-04: Caves bounded — C ≥ 0, finite, zero outside envelope")
    void testCaveBounded() {
        long seed = 0xCAFEBABEDEADBEEFL;
        GeoConfig config = defaultConfig();
        ScalarFieldKernel kernel = new ScalarFieldKernel(seed, config);
        CaveField caveField = kernel.getCaveField();
        GeoSample sample = new GeoSample();

        double caveMinY = config.caveMinY();
        double caveMaxY = config.caveMaxY();

        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                kernel.evaluateFullColumn(x, z, sample);
                double surfaceY = sample.finalSurface;

                // Check below envelope
                double caveBelow = caveField.evaluateCave(x, caveMinY - 20, z, surfaceY);
                assertEquals(0.0, caveBelow, 1e-9,
                        "P3-04: C ≠ 0 below cave envelope at (" + x + "," + z + ")");

                // Check inside envelope
                for (int y = (int) caveMinY; y <= (int) caveMaxY; y += 16) {
                    double cave = caveField.evaluateCave(x, y, z, surfaceY);
                    assertTrue(cave >= 0.0,
                            "P3-04: C < 0 at (" + x + "," + y + "," + z + "): " + cave);
                    assertTrue(Double.isFinite(cave),
                            "P3-04: C not finite at (" + x + "," + y + "," + z + ")");
                }

                // Check above envelope
                double caveAbove = caveField.evaluateCave(x, caveMaxY + 20, z, surfaceY);
                assertEquals(0.0, caveAbove, 1e-9,
                        "P3-04: C ≠ 0 above cave envelope at (" + x + "," + z + ")");
            }
        }
    }
}
