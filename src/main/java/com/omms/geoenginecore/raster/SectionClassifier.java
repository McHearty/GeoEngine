package com.omms.geoenginecore.raster;

import com.omms.geoenginecore.field.CaveField;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.memory.WorkerScratchpad;

/**
 * Coarse per-section raster classification (TECHSPEC §54-§55, §62).
 *
 * <p>Uses only chunk-scale statistics: the min/max of the cached
 * 16×16 surface grid, the warp amplitude, and the regional 3-D cave
 * occupancy test. Proven-solid and proven-air sections never
 * trigger a per-voxel pass, and the classifier itself never
 * performs voxel math (TECHSPEC §62).
 */
public final class SectionClassifier {
    /** Worst-case warp displacement; bounds the surface envelope. */
    private final double maxWarpAmplitude;
    /** World floor. */
    private final int worldMinY;
    /** World ceiling. */
    private final int worldMaxY;
    /** Lower Y of the cave placement envelope. */
    private final int caveMinY;
    /** Upper Y of the cave placement envelope. */
    private final int caveMaxY;
    /** Regional cave occupancy source. */
    private final CaveField caveField;

    /**
     * @param config validated configuration supplying the bounds
     * @param caveField regional cave occupancy source
     */
    public SectionClassifier(GeoConfig config, CaveField caveField) {
        this.maxWarpAmplitude = config.maxWarpAmplitude();
        this.worldMinY = config.worldMinY();
        this.worldMaxY = config.worldMaxY();
        this.caveMinY = config.caveMinY();
        this.caveMaxY = config.caveMaxY();
        this.caveField = caveField;
    }

    /**
     * Classifies one 16-block section from chunk-scale statistics
     * (TECHSPEC §54-§55).
     *
     * <p>World-bound sections are trivial. Otherwise the section is
     * AIR when it lies above the warp-bounded chunk surface ceiling;
     * SOLID when it lies below the warp-bounded chunk floor and is
     * provably void-free (outside the cave envelope, or certified by
     * the regional 3-D occupancy test); BAND otherwise.
     *
     * @param scratchpad worker scratchpad holding the chunk surface grid
     * @param chunkWorldX world-coordinate X of the chunk
     * @param sectionMinY minimum Y of the 16-block section
     * @param chunkWorldZ world-coordinate Z of the chunk
     * @return section classification
     */
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
