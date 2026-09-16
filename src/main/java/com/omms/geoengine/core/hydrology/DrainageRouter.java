package com.geoengine.core.hydrology;

import com.geoengine.core.math.ScalarFieldKernel;
import com.geoengine.core.math.GeoSample;

public final class DrainageRouter {
    public static final int COARSE_CELL_SIZE = 16;
    private static final int SEARCH_RADIUS = 5;

    private DrainageRouter() {}

    public static double computeAccumulationProxy(ScalarFieldKernel kernel, double wx, double wz, GeoSample sample) {
        long baseCoordX = Math.round(wx / COARSE_CELL_SIZE);
        long baseCoordZ = Math.round(wz / COARSE_CELL_SIZE);
        
        double centerElevation = kernel.evaluateH0(baseCoordX * COARSE_CELL_SIZE, baseCoordZ * COARSE_CELL_SIZE, sample);
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
