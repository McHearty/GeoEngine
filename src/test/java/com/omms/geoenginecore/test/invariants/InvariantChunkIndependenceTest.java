package com.omms.geoenginecore.test.invariants;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * C5: Chunk independence invariant.
 *
 * <p>No generated chunk may be a correctness dependency for another
 * chunk. Generating chunk A must not affect the output of chunk B.
 */
public class InvariantChunkIndependenceTest {

    @Tag("invariant")
    @Tag("must")
    @Test
    @DisplayName("C5: Chunk independence - generation order does not affect output")
    void testChunkIndependence() {
        long seed = 0xCAFEBABEDEADBEEFL;
        GeoConfig config = defaultConfig();

        // Generate chunk A first, then chunk B
        ScalarFieldKernel kernel1 = new ScalarFieldKernel(seed, config);
        WorkerScratchpad sp1 = ScratchpadProvider.get();
        kernel1.rasterizeSurfaceChunk(sp1, 0, 0);
        double[] surfaceAB_first = sp1.surfaceGrid.clone();

        // Now generate chunk B first, then chunk A
        ScalarFieldKernel kernel2 = new ScalarFieldKernel(seed, config);
        WorkerScratchpad sp2 = ScratchpadProvider.get();
        kernel2.rasterizeSurfaceChunk(sp2, 16, 0);
        kernel2.rasterizeSurfaceChunk(sp2, 0, 0);
        double[] surfaceBA_first = sp2.surfaceGrid.clone();

        // Chunk A should be identical regardless of generation order
        assertArrayEquals(surfaceAB_first, surfaceBA_first,
                "C5 chunk independence violated: generation order affects output");
    }
}
