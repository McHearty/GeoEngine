package com.omms.geoenginecore.test;

import com.omms.geoenginecore.hydrology.DrainageRouter;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InterRegionHydrologyContinuityTest {
    private static final long TEST_SEED = 0x9876543210FEDCBAL;
    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private DrainageRouter router;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(TEST_SEED, config);
        router = new DrainageRouter();
    }

    @Test
    @DisplayName("Halo Drainage Graph: Seamless flow accumulation across 256-block region boundary")
    void testInterRegionBoundaryContinuity() {
        long seed = TEST_SEED;
        long configHash = config.configHash();

        double seamWestX = 255.0;
        double seamEastX = 256.0;

        for (double wz = 32.0; wz < 224.0; wz += 16.0) {
            double flowWest = router.computeAccumulationProxy(kernel, seed, configHash, seamWestX, wz);
            double flowEast = router.computeAccumulationProxy(kernel, seed, configHash, seamEastX, wz);

            assertFalse(Double.isNaN(flowWest));
            assertFalse(Double.isNaN(flowEast));

            double delta = Math.abs(flowEast - flowWest);
            assertTrue(delta < 0.85);
        }
    }

    @Test
    @DisplayName("Slope-Modulated Warp: Flat surfaces receive minimal vertical deformation")
    void testWarpFlatTerrainDamping() {
        double flatWarp = kernel.getWarpField().evaluateWarp(100.0, 70.0, 100.0, 0.0);
        double steepWarp = kernel.getWarpField().evaluateWarp(100.0, 70.0, 100.0, 1.2);

        assertTrue(Math.abs(flatWarp) <= Math.abs(steepWarp) + 1e-6);
        assertTrue(Math.abs(flatWarp) < 2.0);
    }

    @Test
    @DisplayName("Cave Surface Integrity: No voids generated within minimum overburden threshold")
    void testCaveSurfaceBreachProtection() {
        double surfaceH = 100.0;
        double voidCarve = kernel.getCaveField().evaluateCave(200.0, 96.0, 200.0, surfaceH);
        assertEquals(0.0, voidCarve, 0.0);
    }
}
