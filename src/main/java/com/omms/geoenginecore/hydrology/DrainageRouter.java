package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;

public final class DrainageRouter {
    private static final double CELL_SCALE = 0.0625; // 1 / 16.0

    public double computeAccumulationProxy(ScalarFieldKernel kernel, long worldSeed, long configHash, double wx, double wz) {
        double cellX = wx * CELL_SCALE;
        double cellZ = wz * CELL_SCALE;

        int x0 = (int) Math.floor(cellX);
        int z0 = (int) Math.floor(cellZ);
        double fx = cellX - x0;
        double fz = cellZ - z0;

        // Sample 4 continuous coarse lattice nodes (0 heap allocations)
        double a00 = evaluateCellAccumulation(kernel, x0, z0);
        double a10 = evaluateCellAccumulation(kernel, x0 + 1, z0);
        double a01 = evaluateCellAccumulation(kernel, x0, z0 + 1);
        double a11 = evaluateCellAccumulation(kernel, x0 + 1, z0 + 1);

        double rawAcc = (1.0 - fx) * (1.0 - fz) * a00
                      + fx * (1.0 - fz) * a10
                      + (1.0 - fx) * fz * a01
                      + fx * fz * a11;

        return Math.log1p(Math.max(0.0, rawAcc));
    }

    private double evaluateCellAccumulation(ScalarFieldKernel kernel, int cx, int cz) {
        double centerH = kernel.evaluatePureH0(cx * 16.0, cz * 16.0);
        double acc = 1.0;

        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 && dz == 0) continue;
                double nx = (cx + dx) * 16.0;
                double nz = (cz + dz) * 16.0;
                double nH = kernel.evaluatePureH0(nx, nz);
                if (nH > centerH) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double slope = (nH - centerH) / (dist * 16.0);
                    acc += slope * 10.0;
                }
            }
        }
        return acc;
    }
}
