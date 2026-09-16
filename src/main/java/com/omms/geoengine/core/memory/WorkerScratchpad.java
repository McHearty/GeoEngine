package com.geoengine.core.memory;

import com.geoengine.core.math.GeoSample;

public final class WorkerScratchpad {
    public final GeoSample sample = new GeoSample();

    public static final int MACRO_GRID_DIM = 6;
    public static final int MACRO_GRID_SIZE = MACRO_GRID_DIM * MACRO_GRID_DIM;
    public final double[] macroH0 = new double[MACRO_GRID_SIZE];
    public final double[] macroTectonic = new double[MACRO_GRID_SIZE];

    public static final int CHUNK_DIM = 16;
    public static final int CHUNK_SURFACE_SIZE = CHUNK_DIM * CHUNK_DIM;
    public final double[] surfaceGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] gradXGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] gradZGrid = new double[CHUNK_SURFACE_SIZE];
    public final double[] laplacianGrid = new double[CHUNK_SURFACE_SIZE];

    public static final int SECTION_VOXELS = 4096;
    public final float[] sectionDensity = new float[SECTION_VOXELS];

    WorkerScratchpad() {}
}
