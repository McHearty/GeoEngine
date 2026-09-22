package com.omms.geoenginecore.memory;

import com.omms.geoenginecore.geomorphology.MultiScaleRelief;
import com.omms.geoenginecore.math.GeoSample;

import java.util.Arrays;

/**
 * Worker-owned scratchpad of reusable primitive buffers (TECHSPEC §73).
 *
 * <p>Holds every per-chunk working grid the pipeline needs: the
 * world-space macro node arrays, the 16×16 chunk column grids, the
 * per-section voxel density buffer, and the SIMD scratch vectors. All
 * buffers are allocated once in the constructor and reused for the
 * scratchpad's lifetime, so steady-state chunk generation performs no
 * heap allocation in its inner loops (TECHSPEC §62).
 *
 * <p>An instance is owned by a single worker thread and must never be
 * shared concurrently (TECHSPEC §74).
 */
public final class WorkerScratchpad {
    /** Reusable per-worker column sample (TECHSPEC §65, §73). */
    public final GeoSample sample = new GeoSample();

    /** Side length of the world-space macro node grid (TECHSPEC §38, §75). */
    public static final int MACRO_GRID_DIM = 6;
    /** Total macro node count, including the derivative halo. */
    public static final int MACRO_GRID_SIZE = MACRO_GRID_DIM * MACRO_GRID_DIM; // 36
    /** Interpolated H₀ baseline per macro node. */
    public final double[] macroH0 = new double[MACRO_GRID_SIZE];
    /** Raw tectonic relief per macro node. */
    public final double[] macroTectonic = new double[MACRO_GRID_SIZE];
    /** Geological age factor per macro node. */
    public final double[] macroAge = new double[MACRO_GRID_SIZE];
    /** Temperature per macro node. */
    public final double[] macroTemp = new double[MACRO_GRID_SIZE];
    /** Humidity per macro node. */
    public final double[] macroHumid = new double[MACRO_GRID_SIZE];
    /** Long-term erosion lowering per macro node. */
    public final double[] macroErosion = new double[MACRO_GRID_SIZE];

    /** Side length of the Minecraft chunk (16 columns per edge). */
    public static final int CHUNK_DIM = 16;
    /** Column count of one chunk surface (256). */
    public static final int CHUNK_SURFACE_SIZE = CHUNK_DIM * CHUNK_DIM; // 256
    /** Final 16×16 H_f surface; feeds the heightmap, section, and biome stages (TECHSPEC §57). */
    public final double[] surfaceGrid = new double[CHUNK_SURFACE_SIZE];
    /** Pre-carve H₀ baseline per column. */
    public final double[] h0Grid = new double[CHUNK_SURFACE_SIZE];
    /** Pre-fluvial surface H_pre per column (TECHSPEC §24). */
    public final double[] hPreGrid = new double[CHUNK_SURFACE_SIZE];
    /** X component of ∇H per column (TECHSPEC §37). */
    public final double[] gradXGrid = new double[CHUNK_SURFACE_SIZE];
    /** Z component of ∇H per column (TECHSPEC §37). */
    public final double[] gradZGrid = new double[CHUNK_SURFACE_SIZE];
    /** Laplacian ∇²H per column (TECHSPEC §37). */
    public final double[] laplacianGrid = new double[CHUNK_SURFACE_SIZE];

    /** Age factor per column. */
    public final double[] ageGrid = new double[CHUNK_SURFACE_SIZE];
    /** Temperature per column. */
    public final double[] tempGrid = new double[CHUNK_SURFACE_SIZE];
    /** Humidity per column. */
    public final double[] humidGrid = new double[CHUNK_SURFACE_SIZE];
    /** Bounded climate multiplier K per column (TECHSPEC §20). */
    public final double[] climateMultGrid = new double[CHUNK_SURFACE_SIZE];
    /** Long-term erosion lowering per column. */
    public final double[] erosionGrid = new double[CHUNK_SURFACE_SIZE];
    /** Flow accumulation proxy A_f per column (TECHSPEC §27). */
    public final double[] flowAccGrid = new double[CHUNK_SURFACE_SIZE];
    /** Fluvial incision R per column (TECHSPEC §28). */
    public final double[] riverIncisionGrid = new double[CHUNK_SURFACE_SIZE];
    /** Equilibrium deposition S per column (TECHSPEC §32-§34). */
    public final double[] depositionGrid = new double[CHUNK_SURFACE_SIZE];
    /** Bit-packed landform classification per column. */
    public final int[] classificationBitsGrid = new int[CHUNK_SURFACE_SIZE];

    /** Local river water level for inland rivers above sea level (TECHSPEC §149). */
    public final int[] riverWaterLevelGrid = new int[CHUNK_SURFACE_SIZE];

    /** Voxel count of one 16³ section (4096). */
    public static final int SECTION_VOXELS = 4096;
    /** Density result for the current section being rasterized. */
    public final float[] sectionDensity = new float[SECTION_VOXELS];
    /** Reusable multi-scale relief report (TECHSPEC §99). */
    public final MultiScaleRelief.ReliefReport reliefReport = new MultiScaleRelief.ReliefReport();

    /** Lane width of the reusable SIMD scratch vectors. */
    public static final int MAX_SIMD_LANES = 8;
    /** SIMD scratch: per-lane fractional X offsets. */
    public final double[] simdFx = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane (1 - fx) factors. */
    public final double[] simdOneMinusFx = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane integer macro cell indices. */
    public final int[] simdX0 = new int[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane (0, 0) corner values. */
    public final double[] simdN00 = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane (1, 0) corner values. */
    public final double[] simdN10 = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane (0, 1) corner values. */
    public final double[] simdN01 = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane (1, 1) corner values. */
    public final double[] simdN11 = new double[MAX_SIMD_LANES];

    /** SIMD scratch: per-lane voxel Y values. */
    public final double[] simdYVals = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane warp values. */
    public final double[] simdWVals = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane cave void values. */
    public final double[] simdCVals = new double[MAX_SIMD_LANES];
    /** SIMD scratch: per-lane density results. */
    public final double[] simdResults = new double[MAX_SIMD_LANES];

    /**
     * Slots in the worker's direct-mapped hydrology register.
     *
     * <p>A seam-blended column needs the primary drainage region plus
     * one neighbor region, so the register holds the worker's few
     * most-recent regions (TECHSPEC §65) and a steady-state column
     * lookup is a pure array read - zero allocations and no locks
     * (TECHSPEC §62). A displaced region re-resolves through the
     * shared, bounded {@link com.omms.geoenginecore.memory.HydrologyRegionCache}.
     */
    public static final int HYDROLOGY_REGISTER_SLOTS = 4;

    /**
     * Direct-mapped drainage-region keys; {@code Long.MIN_VALUE} marks
     * an empty slot. Filled once in the constructor; owned by a single
     * worker thread (TECHSPEC §74).
     */
    public final long[] hydrologyRegionKeys = new long[HYDROLOGY_REGISTER_SLOTS];

    /** Drainage graphs backing {@link #hydrologyRegionKeys}; null in empty slots. */
    public final com.omms.geoenginecore.hydrology.DrainageGraph[] hydrologyGraphs =
        new com.omms.geoenginecore.hydrology.DrainageGraph[HYDROLOGY_REGISTER_SLOTS];

    /** Allocates every reusable buffer once; the instance is then owned by a single worker thread (TECHSPEC §74). */
    public WorkerScratchpad() {
        Arrays.fill(hydrologyRegionKeys, Long.MIN_VALUE);
    }

    /**
     * Rebuilds the worker's {@link GeoSample} from the per-column chunk
     * grids.
     *
     * <p>Implements the complete-overwrite contract of TECHSPEC §66:
     * every pipeline quantity the downstream stages read is written
     * before it is consumed.
     *
     * @param lx chunk-local X of the column, in [0, 15]
     * @param lz chunk-local Z of the column, in [0, 15]
     * @param worldX world-coordinate X of the column
     * @param worldZ world-coordinate Z of the column
     */
    public void populateSampleFromColumn(int lx, int lz, int worldX, int worldZ) {
        int idx = (lz << 4) | lx;
        sample.worldX = worldX;
        sample.worldZ = worldZ;
        sample.surfaceH0 = h0Grid[idx];
        sample.age = ageGrid[idx];
        sample.temperature = tempGrid[idx];
        sample.humidity = humidGrid[idx];
        sample.climateMultiplier = climateMultGrid[idx];
        sample.erosionLowering = erosionGrid[idx];
        sample.gradX = gradXGrid[idx];
        sample.gradZ = gradZGrid[idx];
        sample.gradMagnitude = Math.sqrt(sample.gradX * sample.gradX + sample.gradZ * sample.gradZ);
        sample.laplacian = laplacianGrid[idx];
        sample.flowAccumulation = flowAccGrid[idx];
        sample.riverIncision = riverIncisionGrid[idx];
        sample.deposition = depositionGrid[idx];
        sample.finalSurface = surfaceGrid[idx];
        sample.classificationBits = classificationBitsGrid[idx];
    }
}
