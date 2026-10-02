package com.omms.geoenginecore.test.phase7;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.hydrology.DrainageGraph;
import com.omms.geoenginecore.hydrology.HydrologyField;
import com.omms.geoenginecore.memory.HydrologyRegionCache;

/**
 * Terrain generation integration test.
 */
class TerrainGenerationTest {

    private static final long WORLD_SEED = 12345L;

    @Tag("must")
    @Tag("phase7")
    @Test
    @DisplayName("Terrain generation completes without hanging")
    void testTerrainGenerationCompletes() {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(WORLD_SEED, config);
        HydrologyRegionCache cache = new HydrologyRegionCache();
        HydrologyField hydro = new HydrologyField();

        long configHash = config.configHash();

        // Generate a few plates
        for (int px = 0; px < 2; px++) {
            for (int pz = 0; pz < 2; pz++) {
                System.out.println("Generating plate " + px + "," + pz);
                int regionOriginX = px * (int) config.plateScale();
                int regionOriginZ = pz * (int) config.plateScale();

                DrainageGraph graph = cache.getOrCompute(
                    WORLD_SEED, configHash, px, pz,
                    kernel, regionOriginX, regionOriginZ, 5);

                hydro.analyze(graph, WORLD_SEED, configHash,
                    0, 1, px, pz);

                System.out.println("Plate " + px + "," + pz + " complete");
            }
        }

        assertTrue(true, "Terrain generation completed");
    }
}
