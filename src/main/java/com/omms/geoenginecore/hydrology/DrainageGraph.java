package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;

import java.util.Arrays;

/**
 * Authoritative topological D8 drainage graph (TECHSPEC §26, §27).
 *
 * <p>Routes flow on a 24×24 coarse lattice (16×16 core plus a
 * 4-cell halo on every side, covering 384×384 blocks). The halo
 * eliminates region-boundary truncation, and flow is accumulated
 * strictly downstream along steepest-descent D8 links.
 */
public final class DrainageGraph {
    /** Edge length of one routing cell in blocks. */
    public static final int CELL_SIZE = 16;
    /** Core cells per axis: the 256×256 block active region. */
    public static final int CORE_CELLS = 16;
    /** Halo cells per boundary: 64 blocks of context. */
    public static final int HALO_CELLS = 4;
    /** Total cells per axis (core + halo on both sides). */
    public static final int GRID_DIM = CORE_CELLS + (HALO_CELLS * 2);
    /** Total cells in the expanded grid. */
    public static final int TOTAL_CELLS = GRID_DIM * GRID_DIM;

    /** H₀ elevation at each cell center. */
    public final double[] elevation = new double[TOTAL_CELLS];
    /** Downstream receiver of each cell (−1 for sinks). */
    public final int[] receiverIndex = new int[TOTAL_CELLS];
    /** Catchment discharge A_f of each cell, including upstream contributions. */
    public final double[] flowAccumulation = new double[TOTAL_CELLS];

    /** Unprocessed upstream children per cell, used by Kahn's algorithm. */
    private final int[] inDegree = new int[TOTAL_CELLS];
    /** FIFO work queue for topological accumulation. */
    private final int[] topoQueue = new int[TOTAL_CELLS];

    /** World X of the expanded grid origin. */
    private int gridOriginX;
    /** World Z of the expanded grid origin. */
    private int gridOriginZ;

    /**
     * Builds the authoritative D8 drainage network across the 24×24
     * expanded catchment (TECHSPEC §26, §27).
     *
     * <p>Step 1 samples H₀ at every cell center (base discharge 1.0);
     * Step 2 assigns each cell to its steepest downhill D8 neighbor;
     * Step 3 runs Kahn's topological sort from headwaters and
     * propagates full upstream catchment discharge downstream, so
     * every cell's A_f equals its exact catchment size.
     *
     * @param kernel H₀ kernel of the current configuration
     * @param regionOriginX world X of the region's core origin
     * @param regionOriginZ world Z of the region's core origin
     */
    public void buildRegion(ScalarFieldKernel kernel, int regionOriginX, int regionOriginZ) {
        this.gridOriginX = regionOriginX - (HALO_CELLS * CELL_SIZE);
        this.gridOriginZ = regionOriginZ - (HALO_CELLS * CELL_SIZE);

        Arrays.fill(inDegree, 0);

        // Step 1: Sample 24x24 coarse elevation lattice H0(gx, gz)
        int idx = 0;
        for (int gz = 0; gz < GRID_DIM; gz++) {
            double wz = gridOriginZ + (gz * CELL_SIZE);
            for (int gx = 0; gx < GRID_DIM; gx++) {
                double wx = gridOriginX + (gx * CELL_SIZE);
                elevation[idx] = kernel.evaluatePureH0(wx, wz);
                flowAccumulation[idx] = 1.0; // Base precipitation contribution
                receiverIndex[idx] = -1;
                idx++;
            }
        }

        // Step 2: Determine steepest D8 downhill neighbor for all cells
        for (int gz = 0; gz < GRID_DIM; gz++) {
            for (int gx = 0; gx < GRID_DIM; gx++) {
                int currentIdx = (gz * GRID_DIM) + gx;
                double currentH = elevation[currentIdx];

                double maxSlope = 0.0;
                int steepestNeighbor = -1;

                for (int ddz = -1; ddz <= 1; ddz++) {
                    int nz = gz + ddz;
                    if (nz < 0 || nz >= GRID_DIM) continue;

                    for (int ddx = -1; ddx <= 1; ddx++) {
                        if (ddx == 0 && ddz == 0) continue;
                        int nx = gx + ddx;
                        if (nx < 0 || nx >= GRID_DIM) continue;

                        int neighborIdx = (nz * GRID_DIM) + nx;
                        double neighborH = elevation[neighborIdx];

                        if (neighborH < currentH) {
                            double dist = (ddx != 0 && ddz != 0) ? (CELL_SIZE * 1.41421356) : CELL_SIZE;
                            double slope = (currentH - neighborH) / dist;

                            if (slope > maxSlope) {
                                maxSlope = slope;
                                steepestNeighbor = neighborIdx;
                            }
                        }
                    }
                }

                receiverIndex[currentIdx] = steepestNeighbor;
                if (steepestNeighbor != -1) {
                    inDegree[steepestNeighbor]++;
                }
            }
        }

        // Step 3: Kahn's Algorithm for topological flow accumulation (§27)
        // Headwaters (cells with inDegree == 0) initiate downstream propagation
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
                // Downstream cell accumulates full upstream catchment discharge
                flowAccumulation[v] += flowAccumulation[u];
                inDegree[v]--;
                if (inDegree[v] == 0) {
                    topoQueue[tail++] = v;
                }
            }
        }
    }

    /**
     * Bilinearly samples the continuous flow accumulation A_f at a
     * world coordinate, with a saturating logarithmic transform for
     * incision scaling (TECHSPEC §27).
     *
     * @param wx world-space X
     * @param wz world-space Z
     * @return flow accumulation proxy A_f, ≥ 0
     */
    public double sampleAccumulation(double wx, double wz) {
        double cellX = (wx - gridOriginX) / (double) CELL_SIZE;
        double cellZ = (wz - gridOriginZ) / (double) CELL_SIZE;

        int x0 = (int) Math.floor(cellX);
        int z0 = (int) Math.floor(cellZ);

        x0 = Math.clamp(x0, 0, GRID_DIM - 2);
        z0 = Math.clamp(z0, 0, GRID_DIM - 2);

        double fx = Math.clamp(cellX - x0, 0.0, 1.0);
        double fz = Math.clamp(cellZ - z0, 0.0, 1.0);

        int idx00 = (z0 * GRID_DIM) + x0;
        int idx10 = idx00 + 1;
        int idx01 = ((z0 + 1) * GRID_DIM) + x0;
        int idx11 = idx01 + 1;

        double rawAcc = (1.0 - fx) * (1.0 - fz) * flowAccumulation[idx00]
                      + fx * (1.0 - fz) * flowAccumulation[idx10]
                      + (1.0 - fx) * fz * flowAccumulation[idx01]
                      + fx * fz * flowAccumulation[idx11];

        // Saturating logarithmic transform for incision scaling
        return Math.log1p(Math.max(0.0, rawAcc));
    }
}
