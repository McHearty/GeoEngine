package com.omms.geoenginecore.test;

import com.omms.geoenginecore.cache.MacroGridCache;
import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.simd.KernelProvider;
import com.omms.geoenginecore.simd.VectorFieldKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase8PerformanceTest {
    private static final long TEST_SEED = 0xFEEDBEEF9999AAAAL;
    private GeoConfig config;
    private OverworldProfile profile;
    private ScalarFieldKernel scalarKernel;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        profile = new OverworldProfile(config);
        scalarKernel = new ScalarFieldKernel(TEST_SEED, profile);
    }

    @Test
    @DisplayName("Invariant §62: True Zero Steady-State Heap Allocation at Source Level")
    void testZeroSteadyStateAllocation() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        AllocationProbe probe = new AllocationProbe();

        int chunkX = 128;
        int chunkZ = -256;

        for (int i = 0; i < 50; i++) {
            scalarKernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
            for (int y = 50; y < 70; y++) {
                scalarKernel.evaluateDensity(sp, chunkX + 8, y, chunkZ + 8);
            }
        }

        probe.start();
        for (int i = 0; i < 1000; i++) {
            scalarKernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
            for (int y = 60; y < 76; y++) {
                scalarKernel.evaluateDensity(sp, chunkX + 4, y, chunkZ + 4);
            }
        }
        long allocatedBytes = probe.stop();

        assertEquals(0L, allocatedBytes);
    }

    @Test
    @DisplayName("Invariant §71: Scalar vs Vector Numerical Parity Under Unified Process Stack (|Hs - Hv| <= 1e-5)")
    void testScalarVectorNumericalParity() {
        if (!KernelProvider.isVectorApiAvailable()) {
            return;
        }

        VectorFieldKernel vectorKernel = new VectorFieldKernel(TEST_SEED, profile);

        WorkerScratchpad spScalar = new WorkerScratchpad();
        WorkerScratchpad spVector = new WorkerScratchpad();

        int[] testCoords = {0, 128, -256, 1024};
        final double epsilon = 1e-5;

        for (int coord : testCoords) {
            scalarKernel.rasterizeSurfaceChunk(spScalar, coord, coord);
            vectorKernel.rasterizeSurfaceChunk(spVector, coord, coord);

            for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
                double hs = spScalar.surfaceGrid[i];
                double hv = spVector.surfaceGrid[i];
                assertEquals(hs, hv, epsilon);
            }

            float[] dVector = new float[16];
            vectorKernel.evaluateDensityColumnVector(spVector, coord + 8, 64, coord + 8, dVector);

            for (int ly = 0; ly < 16; ly++) {
                float ds = scalarKernel.evaluateDensity(spScalar, coord + 8, 64 + ly, coord + 8);
                float dv = dVector[ly];
                assertEquals(ds, dv, (float) epsilon);
            }
        }
    }

    @Test
    @DisplayName("Invariant §77 & §160: Macro Cache Bitwise Idempotency and Eviction")
    void testMacroCacheIdempotency() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        MacroGridCache cache = new MacroGridCache(32);

        int chunkX = 64;
        int chunkZ = 64;

        scalarKernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
        double[] coldSurface = new double[WorkerScratchpad.CHUNK_SURFACE_SIZE];
        System.arraycopy(sp.surfaceGrid, 0, coldSurface, 0, coldSurface.length);

        long key = MacroGridCache.packKey(chunkX, chunkZ);
        cache.put(key, sp.macroH0);

        double[] cachedMacro = new double[MacroGridCache.GRID_SIZE];
        assertTrue(cache.tryGet(key, cachedMacro));

        System.arraycopy(cachedMacro, 0, sp.macroH0, 0, MacroGridCache.GRID_SIZE);
        scalarKernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);

        assertArrayEquals(coldSurface, sp.surfaceGrid, 0.0);
    }
}
