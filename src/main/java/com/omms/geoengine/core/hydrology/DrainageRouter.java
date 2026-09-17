package com.geoengine.core.hydrology;

import com.geoengine.core.math.GeoSample;
import com.geoengine.core.math.ScalarFieldKernel;

/**
 * Computes a coarse proxy for local drainage accumulation from the terrain height field.
 *
 * <p>The calculation samples a square neighborhood on a coarse grid and uses higher surrounding
 * elevations and their relative relief to construct a logarithmically compressed accumulation
 * proxy. It does not perform explicit flow routing between cells.
 */
public final class DrainageRouter {

    /** Horizontal spacing between coarse-grid samples. */
    public static final int COARSE_CELL_SIZE = 16;

    /** Radius, in coarse cells, of the neighborhood examined around the sample. */
    private static final int SEARCH_RADIUS = 5;

    private DrainageRouter() {}

    /**
     * Computes a local drainage-accumulation proxy around a world-space position.
     *
     * <p>The sample position is mapped to the nearest coarse-grid coordinate. Neighboring coarse
     * cells with higher terrain contribute according to their relative elevation and horizontal
     * distance. The resulting proxy is compressed with {@link Math#log1p(double)}.
     *
     * @param kernel terrain field kernel used to evaluate coarse-grid surface heights
     * @param wx world-space X coordinate
     * @param wz world-space Z coordinate
     * @param sample reusable sample storage passed to the terrain evaluator
     * @return logarithmically compressed local accumulation proxy
     */
    public static double computeAccumulationProxy(
        ScalarFieldKernel kernel,
        double wx,
        double wz,
        GeoSample sample
    ) {
        long baseCoordX = Math.round(wx / COARSE_CELL_SIZE);
        long baseCoordZ = Math.round(wz / COARSE_CELL_SIZE);

        double centerElevation =
            kernel.evaluateH0(baseCoordX * COARSE_CELL_SIZE, baseCoordZ * COARSE_CELL_SIZE, sample);
        double higherNeighborCount = 0.0;
        double accumulatedRelief = 0.0;

        for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                if (dx == 0 && dz == 0) continue;

                double nx = (baseCoordX + dx) * COARSE_CELL_SIZE;
                double nz = (baseCoordZ + dz) * COARSE_CELL_SIZE;
                double neighborElevation = kernel.evaluateH0(nx, nz, sample);

                if (neighborElevation > centerElevation) {
                    double distSq = dx * dx + dz * dz;
                    double slope = (neighborElevation - centerElevation) / Math.sqrt(distSq);
                    higherNeighborCount += 1.0;
                    accumulatedRelief += slope;
                }
            }
        }

        double rawProxy = higherNeighborCount * (accumulatedRelief * 0.1);
        return Math.log1p(Math.max(0.0, rawProxy));
    }
}
