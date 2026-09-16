package com.geoengine.core.raster;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.memory.WorkerScratchpad;

public final class SectionClassifier {
    private final double maxWarpAmplitude;
    private final int worldMinY;
    private final int worldMaxY;

    public SectionClassifier(GeoConfig config) {
        this.maxWarpAmplitude = config.maxWarpAmplitude();
        this.worldMinY = config.worldMinY();
        this.worldMaxY = config.worldMaxY();
    }

    public SectionClassification classifySection(WorkerScratchpad scratchpad, int sectionMinY) {
        int sectionMaxY = sectionMinY + 16;
        if (sectionMaxY <= worldMinY) return SectionClassification.SOLID;
        if (sectionMinY >= worldMaxY) return SectionClassification.AIR;

        double chunkMinSurface = Double.POSITIVE_INFINITY;
        double chunkMaxSurface = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
            double h = scratchpad.surfaceGrid[i];
            if (h < chunkMinSurface) chunkMinSurface = h;
            if (h > chunkMaxSurface) chunkMaxSurface = h;
        }

        double solidCeilingBound = chunkMinSurface - maxWarpAmplitude;
        double airFloorBound = chunkMaxSurface + maxWarpAmplitude;

        if (sectionMinY > airFloorBound) {
            return SectionClassification.AIR;
        }

        if (sectionMaxY < solidCeilingBound) {
            return SectionClassification.BAND;
        }

        return SectionClassification.BAND;
    }
}
