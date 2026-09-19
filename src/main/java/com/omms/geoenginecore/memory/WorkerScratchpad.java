package com.omms.geoenginecore.memory;

import com.omms.geoenginecore.geomorphology.MultiScaleRelief;
import com.omms.geoenginecore.math.GeoSample;

public final class WorkerScratchpad {
    public final GeoSample sample = new GeoSample();

    // 6x6 coarse grid (macro baseline)
    public static final int MACRO_GRID_DIM = 6;
    public static final int MACRO_GRID_SIZE = MACRO_GRID_DIM * MACRO_GRID_DIM; // 36
    public final double[] macroH0 = new double[MACRO_GRID_SIZE];
    public final double[] macroTectonic = new double[MACRO_GRID_SIZE];
    public final double[] macroAge = new double[MACRO_GRID_SIZE];
    public final double[] macroTemp = new double[MACRO_GRID_SIZE];
    public final double[] macroHumid = new double[MACRO_GRID_SIZE];
    public final double[] macroErosion = new double[MACRO_GRID_SIZE];

    // 16x16 per-column surface and intermediate grids
    public static final int CHUNK_DIM = 16;
    public static final int CHUNK_SURFACE_SIZE = CHUNK_DIM * CHUNK_DIM; // 256
    public final double[] surfaceGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] h0Grid = new double[CHUNK_SURFACE_SIZE]; // Pre-carve preliminary surface (§8.9)
    public final double[] gradXGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] gradZGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] laplacianGrid = new double[CHUNK_SURFACE_SIZE];

    public final double[] ageGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] tempGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] humidGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] climateMultGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] erosionGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] flowAccGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] riverIncisionGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] depositionGrid = new double[CHUNK_SURFACE_SIZE];
    public final int[] classificationBitsGrid = new int[CHUNK_SURFACE_SIZE];

    // Voxel density buffer
    public static final int SECTION_VOXELS = 4096;
    public final float[] sectionDensity = new float[SECTION_VOXELS];

    // Multi-Scale Relief container
    public final MultiScaleRelief.ReliefReport reliefReport = new MultiScaleRelief.ReliefReport();

    // Preallocated SIMD vector lane buffers
    public static final int MAX_SIMD_LANES = 8;
    public final double[] simdFx = new double[MAX_SIMD_LANES];
    public final double[] simdOneMinusFx = new double[MAX_SIMD_LANES];
    public final int[] simdX0 = new int[MAX_SIMD_LANES];
    public final double[] simdN00 = new double[MAX_SIMD_LANES];
    public final double[] simdN10 = new double[MAX_SIMD_LANES];
    public final double[] simdN01 = new double[MAX_SIMD_LANES];
    public final double[] simdN11 = new double[MAX_SIMD_LANES];

    public final double[] simdYVals = new double[MAX_SIMD_LANES];
    public final double[] simdWVals = new double[MAX_SIMD_LANES];
    public final double[] simdCVals = new double[MAX_SIMD_LANES];
    public final double[] simdResults = new double[MAX_SIMD_LANES];

    // Cache registers
    public long cachedHydrologyRegionKey = Long.MIN_VALUE;
    public com.omms.geoenginecore.hydrology.DrainageGraph cachedHydrologyGraph = null;

    public WorkerScratchpad() {}

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
