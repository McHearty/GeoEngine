package com.omms.geoenginecore.test.fixtures;

import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.hydrology.DrainageRouter;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;

/**
 * Shared test fixtures for GeoEngine unit tests (TECHSPEC §8, §65, §80).
 *
 * <p>Provides deterministic kernel construction, region rasterization,
 * probe column tables, and the fixed seed matrix used across the test
 * suite.
 */
public final class TestFixtures {

    /** Default test seed (TECHSPEC §8). */
    public static final long SEED = 0xCAFEBABEDEADBEEFL;

    /** Tolerance for chunk-seam flow-accumulation continuity (TECHSPEC §136). */
    public static final double TOL_AF_CHUNK = 1.5;

    /** Tolerance for chunk-seam river-incision continuity (TECHSPEC §136). */
    public static final double TOL_R_CHUNK = 4.0;

    /** Tolerance for region-seam flow-accumulation continuity (TECHSPEC §136). */
    public static final double TOL_AF_REGION = 0.15;

    /** Minimum "river fired" threshold (TECHSPEC §28). */
    public static final double EPS_R = 1.0;

    /** River incision threshold for channel formation (TECHSPEC §28). */
    public static final double AF_RIVER_THRESHOLD = 1.8;

    /** River channel width threshold (TECHSPEC §28). */
    public static final double AF_CHANNEL_THRESHOLD = 2.5;

    /** Half the standard chunk dimension. */
    public static final int HALF_CHUNK = 8;

    /** Standard hydrology region dimension (blocks). */
    public static final int REGION_DIM = 256;

    /** Standard hydrology region dimension (chunks). */
    public static final int REGION_CHUNKS = 16;

    /** Seed matrix for determinism testing (≥5 seeds, includes negative and extreme values). */
    public static final long[] SEED_MATRIX = {
            SEED,
            0x123456789ABCDEF0L,
            0xFFFF_FFFF_FFFF_FFFFL,
            -42L,
            Long.MIN_VALUE,
            0xAAAAAAAA_AAAAAAAAL,
            1L
    };

    private TestFixtures() {
        // utility
    }

    /**
     * Creates the standard default Overworld GeoConfig.
     */
    public static GeoConfig defaultConfig() {
        return GeoConfig.defaultOverworld(1);
    }

    /**
     * Creates a scalar field kernel with the standard test seed and default Overworld config.
     */
    public static ScalarFieldKernel standardKernel() {
        return new ScalarFieldKernel(SEED, defaultConfig());
    }

    /**
     * Creates a scalar field kernel with the given seed and default Overworld config.
     */
    public static ScalarFieldKernel kernelWithSeed(long seed) {
        return new ScalarFieldKernel(seed, defaultConfig());
    }

    /**
     * Creates a worker scratchpad via the pool.
     */
    public static WorkerScratchpad scratchpad() {
        return ScratchpadProvider.get();
    }

    /**
     * Rasterizes a single chunk surface.
     */
    public static void rasterizeChunk(ScalarFieldKernel kernel, WorkerScratchpad sp, int chunkX, int chunkZ) {
        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
    }

    /**
     * Rasterizes a full 16×16 hydrology region starting at the given region origin.
     * Returns the scratchpad containing the last chunk's data (caller should process
     * per-chunk results inside the loop for grid analysis).
     */
    public static WorkerScratchpad rasterizeRegion(ScalarFieldKernel kernel, int regionX, int regionZ) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        for (int cz = 0; cz < REGION_CHUNKS; cz++) {
            for (int cx = 0; cx < REGION_CHUNKS; cx++) {
                kernel.rasterizeSurfaceChunk(sp, regionX * REGION_CHUNKS + cx, regionZ * REGION_CHUNKS + cz);
            }
        }
        return sp;
    }

    /**
     * Returns the max value in a grid.
     */
    public static double gridMax(double[] grid) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : grid) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /**
     * Returns the min value in a grid.
     */
    public static double gridMin(double[] grid) {
        double min = Double.POSITIVE_INFINITY;
        for (double v : grid) {
            if (v < min) {
                min = v;
            }
        }
        return min;
    }

    /**
     * Counts grid cells exceeding the given threshold.
     */
    public static int gridCountAbove(double[] grid, double threshold) {
        int count = 0;
        for (double v : grid) {
            if (v > threshold) {
                count++;
            }
        }
        return count;
    }
}
