package com.geoengine.core.test;

import com.geoengine.core.cache.MacroGridCache;
import com.geoengine.core.math.FieldKernel;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.ScalarFieldKernel;
import com.geoengine.core.memory.ScratchpadProvider;
import com.geoengine.core.memory.WorkerScratchpad;
import com.geoengine.core.simd.KernelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase8PerformanceTest {
    private static final long TEST_SEED = 0xFEEDBEEF9999AAAAL;
    private GeoConfig config;
    private ScalarFieldKernel scalarKernel;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        scalarKernel = new ScalarFieldKernel(TEST_SEED, config);
    }

    @Test
    @DisplayName("Cache Independence: Terrain output identical with Cold vs Warm cache (§77, §160)")
    void testCacheIndependence() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        MacroGridCache cache = new MacroGridCache(64);
        int chunkX = 32;
        int chunkZ = 64;

        scalarKernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
        double[] coldResult = new double[WorkerScratchpad.CHUNK_SURFACE_SIZE];
        System.arraycopy(sp.surfaceGrid, 0, coldResult, 0, coldResult.length);

        long key = MacroGridCache.packKey(chunkX, chunkZ);
        cache.put(key, sp.macroH0);

        double[] warmMacro = new double[MacroGridCache.GRID_SIZE];
        assertTrue(cache.tryGet(key, warmMacro));
        assertArrayEquals(sp.macroH0, warmMacro, 0.0);

        System.arraycopy(warmMacro, 0, sp.macroH0, 0, MacroGridCache.GRID_SIZE);
        scalarKernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);

        assertArrayEquals(coldResult, sp.surfaceGrid, 0.0);
    }

    @Test
    @DisplayName("Bounded Cache Eviction: Preserves size limits under load (§79)")
    void testCacheCapacityEviction() {
        int maxCap = 128;
        MacroGridCache cache = new MacroGridCache(maxCap);
        double[] dummyData = new double[MacroGridCache.GRID_SIZE];

        for (int i = 0; i < 500; i++) {
            long key = MacroGridCache.packKey(i * 16, i * 16);
            cache.put(key, dummyData);
        }

        assertTrue(cache.size() <= maxCap);
    }

    @Test
    @DisplayName("KernelProvider Factory Isolation: Never throws missing class errors (§70)")
    void testKernelProviderSafety() {
        FieldKernel kernel = KernelProvider.createKernel(TEST_SEED, config);
        assertNotNull(kernel);

        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 0, 0);

        for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
            assertFalse(Double.isNaN(sp.surfaceGrid[i]));
        }
    }
}
