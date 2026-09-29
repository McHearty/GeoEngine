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
 * P3-02: Surface protection — caves cannot reach the surface.
 *
 * <p>For columns near the surface (overburden ≤ Cover_min = 8),
 * cave contribution C must be 0.
 */
public class Phase3SurfaceProtectionTest {

    @Tag("must")
    @Tag("phase3")
    @Test
    @DisplayName("P3-02: Surface protection — C == 0 when overburden ≤ Cover_min")
    void testSurfaceProtection() {
        long seed = 0xCAFEBABEDEADBEEFL;
        GeoConfig config = defaultConfig();
        ScalarFieldKernel kernel = new ScalarFieldKernel(seed, config);
        CaveField caveField = kernel.getCaveField();
        GeoSample sample = new GeoSample();

        int violations = 0;
        int checks = 0;

        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                kernel.evaluateFullColumn(x, z, sample);
                double surfaceY = sample.finalSurface;

                // Check overburden ≤ 8 (surface protection zone)
                // Use double y to avoid truncation issues
                for (double y = surfaceY - 8.0; y <= surfaceY; y += 0.5) {
                    checks++;
                    double overburden = surfaceY - y;
                    if (overburden > 8.0) {
                        continue; // Skip if overburden > 8 (shouldn't happen with this loop)
                    }
                    double cave = caveField.evaluateCave(x, y, z, surfaceY);
                    if (cave > 1e-9) {
                        violations++;
                    }
                }
            }
        }

        assertTrue(checks > 0, "No surface protection checks performed");
        assertEquals(0, violations,
                "P3-02: Caves breached surface protection zone at " + violations +
                        " of " + checks + " checks (overburden ≤ 8)");
    }
}
