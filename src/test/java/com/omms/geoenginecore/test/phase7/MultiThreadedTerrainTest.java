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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Multi-threaded terrain generation test.
 */
class MultiThreadedTerrainTest {

    private static final long WORLD_SEED = 12345L;

    @Tag("must")
    @Tag("phase7")
    @Test
    @DisplayName("Multi-threaded terrain generation completes")
    void testMultiThreadedTerrainGeneration() throws Exception {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(WORLD_SEED, config);
        HydrologyRegionCache cache = new HydrologyRegionCache();
        HydrologyField hydro = new HydrologyField();

        long configHash = config.configHash();

        ExecutorService executor = Executors.newFixedThreadPool(4);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int px = 0; px < 4; px++) {
            for (int pz = 0; pz < 4; pz++) {
                final int fpx = px;
                final int fpz = pz;
                futures.add(CompletableFuture.runAsync(() -> {
                    int regionOriginX = fpx * (int) config.plateScale();
                    int regionOriginZ = fpz * (int) config.plateScale();

                    DrainageGraph graph = cache.getOrCompute(
                        WORLD_SEED, configHash, fpx, fpz,
                        kernel, regionOriginX, regionOriginZ, 5);

                    hydro.analyze(graph, WORLD_SEED, configHash,
                        0, 1, fpx, fpz);
                }, executor));
            }
        }

        // Wait for all futures with a timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("Terrain generation timed out");
        } finally {
            executor.shutdown();
        }

        assertTrue(true, "Multi-threaded terrain generation completed");
    }
}
