package com.geoengine.core.math;

import com.geoengine.core.memory.WorkerScratchpad;

public interface FieldKernel {
    void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);
    void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);
    float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ);
}
