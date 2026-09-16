package com.geoengine.core.dimension;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;
import com.geoengine.core.memory.WorkerScratchpad;

public interface DimensionProfile {
    DimensionType getDimensionType();
    GeoConfig getConfig();
    int getFluidLevel();
    double evaluateSurface(double x, double z, GeoSample sample);
    float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ);
}
