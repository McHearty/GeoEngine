package com.geoengine.core.memory;

import com.geoengine.core.math.GeoSample;

/**
 * Reusable temporary storage for terrain evaluation performed by a single worker thread.
 *
 * <p>The scratchpad groups the mutable sample state and fixed-size arrays used by the terrain
 * pipeline. Instances are intended to be thread-confined; {@link ScratchpadProvider} supplies one
 * instance per thread.
 *
 * <p>The arrays are organized by evaluation stage: a 6x6 macro grid, a 16x16 chunk surface grid,
 * and a 16x16x16 section density buffer.
 */
public final class WorkerScratchpad {

    /** Reusable mutable sample state for field and terrain calculations. */
    public final GeoSample sample = new GeoSample();

    /** Number of samples along each horizontal dimension of the macro grid. */
    public static final int MACRO_GRID_DIM = 6;

    /** Total number of samples in the macro grid. */
    public static final int MACRO_GRID_SIZE = MACRO_GRID_DIM * MACRO_GRID_DIM;

    /** Macro-grid surface-height samples. */
    public final double[] macroH0 = new double[MACRO_GRID_SIZE];

    /** Macro-grid tectonic-field samples. */
    public final double[] macroTectonic = new double[MACRO_GRID_SIZE];

    /** Number of samples along each horizontal dimension of a chunk. */
    public static final int CHUNK_DIM = 16;

    /** Total number of horizontal samples in a chunk surface grid. */
    public static final int CHUNK_SURFACE_SIZE = CHUNK_DIM * CHUNK_DIM;

    /** Rasterized surface-height samples for a chunk. */
    public final double[] surfaceGrid = new double[CHUNK_SURFACE_SIZE];

    /** X-gradient samples for a chunk surface. */
    public final double[] gradXGrid = new double[CHUNK_SURFACE_SIZE];

    /** Z-gradient samples for a chunk surface. */
    public final double[] gradZGrid = new double[CHUNK_SURFACE_SIZE];

    /** Discrete Laplacian samples for a chunk surface. */
    public final double[] laplacianGrid = new double[CHUNK_SURFACE_SIZE];

    /** Number of voxels in a 16x16x16 section. */
    public static final int SECTION_VOXELS = 4096;

    /** Density values for one 16x16x16 terrain section. */
    public final float[] sectionDensity = new float[SECTION_VOXELS];

    WorkerScratchpad() {}
}
