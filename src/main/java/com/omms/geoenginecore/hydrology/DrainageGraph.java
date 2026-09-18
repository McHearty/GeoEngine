package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;

import java.util.Arrays;

public final class DrainageGraph {
    public static final int CELL_SIZE = 16;
    public static final int REGION_CELLS = 16; // 16x16 active cells = 256x256 block core
    public static final int TOTAL_CELLS = REGION_CELLS * REGION_CELLS; // 256

    // Halo grid sizing: 1-cell border on all sides = 18x18
    public static final int HALO_DIM = REGION_CELLS + 2; // 18
    public static final int TOTAL_HALO_CELLS = HALO_DIM * HALO_DIM; // 324

    private final double[] haloElevation = new double[TOTAL_HALO_CELLS];

    public final double[] elevation = new double[TOTAL_CELLS];
    public final int[] receiverIndex = new int[TOTAL_CELLS];
    public final double[] flowAccumulation = new double[TOTAL_CELLS];

    private final int[] inDegree = new int[TOTAL_CELLS];
    private final int[] topoQueue = new int[TOTAL_CELLS];

    public void buildRegion(ScalarFieldKernel kernel, int regionOriginX, int regionOriginZ) {
        Arrays.fill(inDegree, 0);

        int hIdx = 0;
        for (int hz = -1; hz <= REGION_CELLS; hz++) {
            double wz = regionOriginZ + (hz * CELL_SIZE);
            for (int hx = -1; hx <= REGION_CELLS; hx++) {
                double wx = regionOriginX + (hx * CELL_SIZE);
                haloElevation[hIdx++] = kernel.evaluatePureH0(wx, wz);
            }
        }

        for (int cz = 0; cz < REGION_CELLS; cz++) {
            for (int cx = 0; cx < REGION_CELLS; cx++) {
                int haloIdx = ((cz + 1) * HALO_DIM) + (cx + 1);
                int coreIdx = (cz * REGION_CELLS) + cx;
                elevation[coreIdx] = haloElevation[haloIdx];
                flowAccumulation[coreIdx] = 1.0;
                receiverIndex[coreIdx] = -1;
            }
        }

        for (int cz = 0; cz < REGION_CELLS; cz++) {
            for (int cx = 0; cx < REGION_CELLS; cx++) {
                int coreIdx = (cz * REGION_CELLS) + cx;
                int haloCenterIdx = ((cz + 1) * HALO_DIM) + (cx + 1);
                double currentH = haloElevation[haloCenterIdx];

                double maxSlope = 0.0;
                int steepestCoreReceiver = -1;
                boolean flowsOutOfRegion = false;

                for (int ddz = -1; ddz <= 1; ddz++) {
                    for (int ddx = -1; ddx <= 1; ddx++) {
                        if (ddx == 0 && ddz == 0) continue;

                        int nhx = (cx + 1) + ddx;
                        int nhz = (cz + 1) + ddz;
                        double neighborH = haloElevation[(nhz * HALO_DIM) + nhx];

                        if (neighborH < currentH) {
                            double dist = (ddx != 0 && ddz != 0) ? (CELL_SIZE * 1.41421356) : CELL_SIZE;
                            double slope = (currentH - neighborH) / dist;

                            if (slope > maxSlope) {
                                maxSlope = slope;
                                int targetCx = cx + ddx;
                                int targetCz = cz + ddz;

                                if (targetCx >= 0 && targetCx < REGION_CELLS && targetCz >= 0 && targetCz < REGION_CELLS) {
                                    steepestCoreReceiver = (targetCz * REGION_CELLS) + targetCx;
                                    flowsOutOfRegion = false;
                                } else {
                                    flowsOutOfRegion = true;
                                }
                            }
                        }
                    }
                }

                if (!flowsOutOfRegion && steepestCoreReceiver != -1) {
                    receiverIndex[coreIdx] = steepestCoreReceiver;
                    inDegree[steepestCoreReceiver]++;
                } else {
                    receiverIndex[coreIdx] = -1;
                }
            }
        }

        int head = 0;
        int tail = 0;
        for (int i = 0; i < TOTAL_CELLS; i++) {
            if (inDegree[i] == 0) {
                topoQueue[tail++] = i;
            }
        }

        while (head < tail) {
            int u = topoQueue[head++];
            int v = receiverIndex[u];

            if (v != -1) {
                flowAccumulation[v] += flowAccumulation[u];
                inDegree[v]--;
                if (inDegree[v] == 0) {
                    topoQueue[tail++] = v;
                }
            }
        }
    }

    public double sampleAccumulation(int regionOriginX, int regionOriginZ, double wx, double wz) {
        double cellX = (wx - regionOriginX) / CELL_SIZE;
        double cellZ = (wz - regionOriginZ) / CELL_SIZE;

        int x0 = Math.clamp((int) Math.floor(cellX), 0, REGION_CELLS - 2);
        int z0 = Math.clamp((int) Math.floor(cellZ), 0, REGION_CELLS - 2);

        double fx = Math.clamp(cellX - x0, 0.0, 1.0);
        double fz = Math.clamp(cellZ - z0, 0.0, 1.0);

        int idx00 = (z0 * REGION_CELLS) + x0;
        int idx10 = idx00 + 1;
        int idx01 = ((z0 + 1) * REGION_CELLS) + x0;
        int idx11 = idx01 + 1;

        double rawAcc = (1.0 - fx) * (1.0 - fz) * flowAccumulation[idx00]
                      + fx * (1.0 - fz) * flowAccumulation[idx10]
                      + (1.0 - fx) * fz * flowAccumulation[idx01]
                      + fx * fz * flowAccumulation[idx11];

        return Math.log1p(Math.max(0.0, rawAcc));
    }
}
