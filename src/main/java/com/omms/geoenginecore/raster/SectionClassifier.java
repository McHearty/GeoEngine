package com.omms.geoenginecore.raster;

import com.omms.geoenginecore.field.CaveField;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.memory.WorkerScratchpad;

public final class SectionClassifier {
    private final double maxWarpAmplitude;
    private final int worldMinY;
    private final int worldMaxY;
    private final int caveMinY;
    private final int caveMaxY;
    private final CaveField caveField;

    public SectionClassifier(GeoConfig config, CaveField caveField) {
        this.maxWarpAmplitude = config.maxWarpAmplitude();
        this.worldMinY = config.worldMinY();
        this.worldMaxY = config.worldMaxY();
        this.caveMinY = config.caveMinY();
        this.caveMaxY = config.caveMaxY();
        this.caveField = caveField;
    }

    public SectionClassification classifySection(WorkerScratchpad scratchpad, int chunkWorldX, int sectionMinY, int chunkWorldZ) {
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
            // Proven solid: entire section lies below the cave floor or above cave ceiling (§55)
            if (sectionMaxY <= caveMinY || sectionMinY >= caveMaxY) {
                return SectionClassification.SOLID;
            }
            // Proven solid: regional 3D occupancy certifies section is void-free
            if (!caveField.hasCavePotential(chunkWorldX, sectionMinY, chunkWorldZ)) {
                return SectionClassification.SOLID;
            }
        }

        return SectionClassification.BAND;
    }
}
