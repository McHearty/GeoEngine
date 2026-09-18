package com.omms.geoenginecore.math;

import com.omms.geoenginecore.field.CaveField;
import com.omms.geoenginecore.field.WarpField;
import com.omms.geoenginecore.geomorphology.LandformClassifier;
import com.omms.geoenginecore.memory.WorkerScratchpad;

public interface FieldKernel {
    void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);
    void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);
    float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ);
    void evaluateFullColumn(double wx, double wz, GeoSample sample);

    CaveField getCaveField();
    WarpField getWarpField();
    LandformClassifier getLandformClassifier();
}
