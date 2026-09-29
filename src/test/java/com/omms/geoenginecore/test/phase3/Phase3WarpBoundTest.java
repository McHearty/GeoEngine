package com.omms.geoenginecore.test.phase3;

import com.omms.geoenginecore.field.WarpField;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P3-03: Controlled overhangs — warp amplitude is bounded.
 *
 * <p>|W| ≤ maxWarpAmplitude for all locations.
 */
public class Phase3WarpBoundTest {

    @Tag("must")
    @Tag("phase3")
    @Test
    @DisplayName("P3-03: Controlled overhangs — |W| ≤ maxWarpAmplitude")
    void testWarpBound() {
        long seed = 0xCAFEBABEDEADBEEFL;
        GeoConfig config = defaultConfig();
        ScalarFieldKernel kernel = new ScalarFieldKernel(seed, config);
        WarpField warpField = kernel.getWarpField();
        GeoSample sample = new GeoSample();

        double maxWarp = config.maxWarpAmplitude();
        double maxObserved = 0.0;

        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                // Rasterize to get gradient data
                WorkerScratchpad sp = ScratchpadProvider.get();
                kernel.rasterizeSurfaceChunk(sp, x, z);

                // Cell index within chunk (center of 16x16 grid)
                int cIdx = 8 + 8 * 16;
                double gx = sp.gradXGrid[cIdx];
                double gz = sp.gradZGrid[cIdx];
                double slope = Math.sqrt(gx * gx + gz * gz);

                kernel.evaluateFullColumn(x, z, sample);
                double surfaceY = sample.finalSurface;

                // Check warp at multiple heights
                for (int y = (int) (surfaceY - 100); y <= (int) (surfaceY + 100); y += 32) {
                    double warp = warpField.evaluateWarp(x, y, z, slope);

                    maxObserved = Math.max(maxObserved, Math.abs(warp));

                    assertTrue(Math.abs(warp) <= maxWarp + 1e-9,
                            "P3-03: Warp exceeds bound at (" + x + "," + y + "," + z +
                                    "): " + warp + " > " + maxWarp);
                }
            }
        }

        System.out.println("P3-03: Max observed warp amplitude: " + maxObserved +
                " (bound: " + maxWarp + ")");
    }
}
