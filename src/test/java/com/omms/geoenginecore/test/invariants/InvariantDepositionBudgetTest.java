package com.omms.geoenginecore.test.invariants;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C3: Deposition budget invariant.
 *
 * <p>Total deposition across the chunk must be less than or equal to
 * total erosion. This ensures mass conservation in the geomorphic
 * simulation.
 */
public class InvariantDepositionBudgetTest {

    @Test
    @DisplayName("C3: Deposition budget - total deposition <= total erosion")
    void testDepositionBudget() {
        GeoConfig config = defaultConfig();
        long seed = 0xCAFEBABEDEADBEEFL;

        ScalarFieldKernel kernel = new ScalarFieldKernel(seed, config);
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 64, 64);

        // Compute total deposition and erosion from the grids
        double totalDeposition = 0.0;
        double totalErosion = 0.0;

        for (int i = 0; i < sp.surfaceGrid.length; i++) {
            totalDeposition += sp.depositionGrid[i];
            totalErosion += sp.erosionGrid[i];
        }

        // Allow small tolerance for floating-point arithmetic
        assertTrue(totalDeposition <= totalErosion + 0.1,
                "C3 deposition budget violated: deposition " + totalDeposition +
                        " > erosion " + totalErosion);
    }
}
