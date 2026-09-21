package com.omms.geoenginecore.test;

import com.omms.geoenginecore.hydrology.DrainageRouter;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Region seam continuity for the authoritative 24×24 D8
 * DrainageGraph (TECHSPEC §135, §136): flow accumulation must
 * transition smoothly across 256-block region boundaries, with the
 * overlapping halo providing C0 continuity.
 */
public class InterRegionHydrologyContinuityTest {
    /** Fixed test world seed. */
    private static final long TEST_SEED = 0x9876543210FEDCBAL;
    /** Default Overworld configuration. */
    private GeoConfig config;
    /** Scalar field kernel. */
    private ScalarFieldKernel kernel;
    /** Drainage router. */
    private DrainageRouter router;

    /**
     * Installs the shared test fixtures.
     */
    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(TEST_SEED, config);
        router = new DrainageRouter();
    }

    /**
     * Flow accumulation just west (X=255) and east (X=256) of the
     * region seam must stay within a smooth transition tolerance.
     */
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
