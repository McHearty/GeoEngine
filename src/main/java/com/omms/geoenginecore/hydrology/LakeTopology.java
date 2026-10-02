package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;

import java.util.ArrayList;
import java.util.List;

/**
 * Lake topology detection from continuous fields (TECHSPEC_AMEND001 A3.11).
 *
 * <p>Detects lakes by identifying closed depressions in the continuous
 * terrain field that are below sea level or have accumulated water.
 * Lakes are filtered by area to remove small depressions.
 */
public final class LakeTopology {
    /** Minimum lake area in square blocks. */
    private final double minArea;
    /** Maximum lake area in square blocks. */
    private final double maxArea;

    /**
     * Constructs the lake topology detector.
     *
     * @param minArea minimum lake area in square blocks
     * @param maxArea maximum lake area in square blocks
     */
    public LakeTopology(double minArea, double maxArea) {
        this.minArea = Math.max(0.0, minArea);
        this.maxArea = Math.max(minArea, maxArea);
    }

    /**
     * Represents a detected lake.
     */
    public record Lake(
        /** Center X coordinate. */
        double centerX,
        /** Center Z coordinate. */
        double centerZ,
        /** Area in square blocks. */
        double area,
        /** Maximum depth in blocks. */
        double maxDepth) {
    }

    /**
     * Detects lakes in a region by identifying closed depressions.
     *
     * @param kernel the H₀ kernel
     * @param regionMinX minimum X of region
     * @param regionMinZ minimum Z of region
     * @param regionSize size of region in blocks
     * @param cellSize grid cell size in blocks
     * @return list of detected lakes
     */
    public List<Lake> detectLakes(ScalarFieldKernel kernel,
                                   double regionMinX, double regionMinZ,
                                   double regionSize, double cellSize) {
        List<Lake> lakes = new ArrayList<>();

        // Simplified: detect depressions by checking local minima
        // In a full implementation, this would use contour analysis
        // and flood-fill to identify closed depressions

        int gridDim = (int) Math.ceil(regionSize / cellSize);

        for (int ix = 0; ix < gridDim - 1; ix++) {
            for (int iz = 0; iz < gridDim - 1; iz++) {
                double x = regionMinX + ix * cellSize;
                double z = regionMinZ + iz * cellSize;

                // Check if this is a local minimum
                double hCenter = kernel.evaluatePureH0(x, z);
                double hEast = kernel.evaluatePureH0(x + cellSize, z);
                double hWest = kernel.evaluatePureH0(x - cellSize, z);
                double hNorth = kernel.evaluatePureH0(x, z + cellSize);
                double hSouth = kernel.evaluatePureH0(x, z - cellSize);

                if (hCenter < hEast && hCenter < hWest && hCenter < hNorth && hCenter < hSouth) {
                    // Local minimum detected - estimate lake area and depth
                    // Simplified: use cell size as proxy for area
                    double area = cellSize * cellSize;
                    double maxDepth = Math.max(0.0, (hEast + hWest + hNorth + hSouth) / 4.0 - hCenter);

                    // Filter by area
                    if (area >= minArea && area <= maxArea) {
                        lakes.add(new Lake(x, z, area, maxDepth));
                    }
                }
            }
        }

        return lakes;
    }

    /**
     * Checks if a lake passes the area filter.
     *
     * @param area lake area in square blocks
     * @return true if the lake passes the filter
     */
    public boolean passesAreaFilter(double area) {
        return area >= minArea && area <= maxArea;
    }

    /**
     * Computes the water surface level for a lake.
     *
     * @param lakeDepth lake depth in blocks
     * @return water surface level in blocks above lake bottom
     */
    public double waterSurfaceLevel(double lakeDepth) {
        return lakeDepth;
    }
}
