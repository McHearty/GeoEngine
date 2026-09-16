package com.geoengine.core.raster;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.memory.WorkerScratchpad;

/**
 * Classifies a vertical section using the previously rasterized surface grid and configured
 * vertical warp bound.
 *
 * <p>Sections entirely outside the configured world-height range are classified as {@link
 * SectionClassification#SOLID} or {@link SectionClassification#AIR}. Sections whose vertical
 * range may intersect the terrain surface are classified as {@link SectionClassification#BAND}.
 */
public final class SectionClassifier {
    /** Maximum vertical displacement considered when bounding the terrain surface. */
    private final double maxWarpAmplitude;

    /** Inclusive lower bound of the configured world-height range. */
    private final int worldMinY;

    /** Exclusive upper bound of the configured world-height range. */
    private final int worldMaxY;

    /**
     * Creates a classifier using the vertical bounds and warp amplitude from the generator
     * configuration.
     *
     * @param config terrain-generation configuration
     */
    public SectionClassifier(GeoConfig config) {
        this.maxWarpAmplitude = config.maxWarpAmplitude();
        this.worldMinY = config.worldMinY();
        this.worldMaxY = config.worldMaxY();
    }

    /**
     * Classifies a 16-block-high vertical section against the rasterized terrain surface.
     *
     * <p>The surface grid is reduced to its minimum and maximum heights, then expanded by the
     * configured maximum warp amplitude to obtain conservative vertical bounds. Sections outside
     * those bounds can be classified without evaluating individual blocks.
     *
     * @param scratchpad scratch state containing the rasterized surface grid
     * @param sectionMinY minimum Y coordinate of the section
     * @return the classification applicable to the section
     */
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
