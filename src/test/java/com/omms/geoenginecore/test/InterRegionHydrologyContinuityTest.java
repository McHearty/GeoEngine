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
    @DisplayName("Authoritative D8 Drainage Graph: Seamless flow accumulation across 256-block region boundary")
    void testInterRegionBoundaryContinuity() {
        long seed = TEST_SEED;
        long configHash = config.configHash();

        // Boundary occurs between X=255 (Region 0) and X=256 (Region 1)
        double seamWestX = 255.0;
        double seamEastX = 256.0;

        for (double wz = 32.0; wz < 224.0; wz += 16.0) {
            double flowWest = router.computeAccumulationProxy(kernel, seed, configHash, seamWestX, wz);
            double flowEast = router.computeAccumulationProxy(kernel, seed, configHash, seamEastX, wz);

            assertFalse(Double.isNaN(flowWest));
            assertFalse(Double.isNaN(flowEast));

            // Overlapping 4-cell halo guarantees tight C0 continuity across region seams
            double delta = Math.abs(flowEast - flowWest);
            assertTrue(delta < 0.15, 
                "Flow accumulation must transition smoothly across 256-block boundary at Z=" + wz + " (observed delta=" + delta + ")");
        }
    }
}
