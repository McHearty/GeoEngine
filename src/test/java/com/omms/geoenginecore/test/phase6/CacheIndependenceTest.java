package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cache independence test (TECHSPEC_AMEND001 A6.14).
 *
 * <p>Verifies that field values at any coordinate are
 * independently recomputable without requiring another
 * plate or chunk to have been evaluated.
 */
public class CacheIndependenceTest {

    @Tag("must")
    @Tag("phase6")
    @Test
    @DisplayName("A6.14: Field values are independently recomputable")
    void testCacheIndependence() {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        long seed = 42L;

        // Evaluate at a point
        ScalarFieldKernel kernel1 = new ScalarFieldKernel(seed, config);
        double h1 = kernel1.evaluatePureH0(100.0, 100.0);

        // Evaluate at a different point first (simulates different plate)
        ScalarFieldKernel kernel2 = new ScalarFieldKernel(seed, config);
        double hOther = kernel2.evaluatePureH0(1000.0, 1000.0);

        // Evaluate at original point again (simulates cache miss)
        ScalarFieldKernel kernel3 = new ScalarFieldKernel(seed, config);
        double h3 = kernel3.evaluatePureH0(100.0, 100.0);

        // Results should be identical regardless of evaluation order
        assertEquals(h1, h3, 1e-9);
    }

    @Tag("phase6")
    @Test
    @DisplayName("Multiple kernels produce identical results")
    void testMultipleKernelsIdentical() {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        long seed = 42L;

        ScalarFieldKernel k1 = new ScalarFieldKernel(seed, config);
        ScalarFieldKernel k2 = new ScalarFieldKernel(seed, config);
        ScalarFieldKernel k3 = new ScalarFieldKernel(seed, config);

        for (double x = 0.0; x <= 100.0; x += 50.0) {
            for (double z = 0.0; z <= 100.0; z += 50.0) {
                assertEquals(k1.evaluatePureH0(x, z), k2.evaluatePureH0(x, z), 1e-9);
                assertEquals(k1.evaluatePureH0(x, z), k3.evaluatePureH0(x, z), 1e-9);
            }
        }
    }
}
