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
 * C2: Seam continuity invariant.
 *
 * <p>Adjacent chunks must share continuous heightmaps at their boundaries.
 * The maximum allowed discontinuity is 8 blocks (TECHSPEC requirement).
 */
public class InvariantSeamContinuityTest {

    @Test
    @DisplayName("C2: Seam continuity - adjacent chunks share continuous heightmaps")
    void testSeamContinuity() {
        GeoConfig config = defaultConfig();
        long seed = 0xCAFEBABEDEADBEEFL;

        // Generate two adjacent chunks
        ScalarFieldKernel kernel1 = new ScalarFieldKernel(seed, config);
        WorkerScratchpad sp1 = ScratchpadProvider.get();
        kernel1.rasterizeSurfaceChunk(sp1, 64, 64);

        ScalarFieldKernel kernel2 = new ScalarFieldKernel(seed, config);
        WorkerScratchpad sp2 = ScratchpadProvider.get();
        kernel2.rasterizeSurfaceChunk(sp2, 80, 64);

        // Compare the shared edge (right edge of chunk 1 vs left edge of chunk 2)
        double maxDiscontinuity = 0.0;
        for (int z = 0; z < 16; z++) {
            // Chunk 1's right edge is at column 15
            int idx1 = (15 * 16) + z;
            // Chunk 2's left edge is at column 0
            int idx2 = (0 * 16) + z;

            double diff = Math.abs(sp1.surfaceGrid[idx1] - sp2.surfaceGrid[idx2]);
            maxDiscontinuity = Math.max(maxDiscontinuity, diff);
        }

        assertTrue(maxDiscontinuity <= 8.0,
                "C2 seam continuity violated: max discontinuity " + maxDiscontinuity + " blocks");
    }
}
