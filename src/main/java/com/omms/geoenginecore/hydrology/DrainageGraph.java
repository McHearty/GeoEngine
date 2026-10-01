package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.math.GeoMath;

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
    /**
     * Upstream sender count per cell, snapshotted when routing is
     * resolved (TECHSPEC §26, §31). A cell with upstreamCount ≥ 2 is a
     * confluence: the first-class confluence detection signal that
     * Kahn's algorithm would otherwise consume.
     */
    public final int[] upstreamCount = new int[TOTAL_CELLS];
    /**
     * Basin ordinal of each cell (index into {@link #basinStableId});
     * -1 until {@link HydrologyField#analyze} completes. The basin
     * partition is topological: every cell drains to exactly one
     * regional sink (elevations strictly decrease along the receiver
     * chain, so every walk terminates).
     */
    public final int[] basinCell = new int[TOTAL_CELLS];
    /** Confluence ordinal of each cell; -1 when the cell is not a confluence. */
    public final int[] confluenceCell = new int[TOTAL_CELLS];
    /** Stable scoped basin IDs indexed by basin ordinal (§80). */
    public final long[] basinStableId = new long[TOTAL_CELLS];
    /** Stable scoped confluence IDs indexed by confluence ordinal (§80). */
    public final long[] confluenceStableId = new long[TOTAL_CELLS];
    /** Sink cell of each basin, indexed by basin ordinal. */
    public final int[] basinSinkCell = new int[TOTAL_CELLS];
    /** Number of basins in this region (valid once analyzed). */
    public int basinCount;
    /** Number of confluences in this region (valid once analyzed). */
    public int confluenceCount;
    /**
     * Topology analysis complete flag. Written under the graph's own
     * monitor (first publisher wins, matching the region cache), then
     * visible to every worker as a volatile read; per-column basin /
     * confluence lookups are then plain array reads (TECHSPEC §62).
     */
    public volatile boolean topologyAnalyzed;

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
     * <p>When {@code iterations} > 1, the receiver assignment and
     * flow accumulation are repeated K times to converge on a
     * stable flow network (TECHSPEC §24). Each pass re-evaluates
     * receivers based on accumulated discharge potential, resolving
     * flat areas and pits that single-pass routing cannot handle.
     *
     * @param kernel H₀ kernel of the current configuration
     * @param regionOriginX world X of the region's core origin
     * @param regionOriginZ world Z of the region's core origin
     * @param iterations fixed-iteration count K (≥ 1)
     */
    public void buildRegion(ScalarFieldKernel kernel, int regionOriginX, int regionOriginZ, int iterations) {
        this.gridOriginX = regionOriginX - (HALO_CELLS * CELL_SIZE);
        this.gridOriginZ = regionOriginZ - (HALO_CELLS * CELL_SIZE);

        // Step 1: Sample 24x24 coarse elevation lattice H0(gx, gz) once
        int idx = 0;
        for (int gz = 0; gz < GRID_DIM; gz++) {
            double wz = gridOriginZ + (gz * CELL_SIZE);
            for (int gx = 0; gx < GRID_DIM; gx++) {
                double wx = gridOriginX + (gx * CELL_SIZE);
                elevation[idx] = kernel.evaluatePureH0(wx, wz);
                idx++;
            }
        }

        // Fixed-iteration drainage loop (TECHSPEC §24)
        for (int k = 0; k < iterations; k++) {
            // Reset per-pass state
            Arrays.fill(inDegree, 0);
            Arrays.fill(upstreamCount, 0);
            Arrays.fill(receiverIndex, -1);
            Arrays.fill(flowAccumulation, 1.0); // Base precipitation contribution

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

                    // Flat/pit resolution: if no downhill neighbor, use lowest neighbor
                    // with index-ordered tie-breaking to guarantee acyclic graph (DAG)
                    if (steepestNeighbor == -1) {
                        for (int ddz = -1; ddz <= 1; ddz++) {
                            int nz = gz + ddz;
                            if (nz < 0 || nz >= GRID_DIM) continue;

                            for (int ddx = -1; ddx <= 1; ddx++) {
                                if (ddx == 0 && ddz == 0) continue;
                                int nx = gx + ddx;
                                if (nx < 0 || nx >= GRID_DIM) continue;

                                int neighborIdx = (nz * GRID_DIM) + nx;
                                if (neighborIdx > currentIdx) continue; // index-ordered tie-break

                                if (elevation[neighborIdx] < elevation[currentIdx]) {
                                    steepestNeighbor = neighborIdx;
                                    break; // found strictly lower neighbor
                                }
                                // On flats: allow flow to lower-index neighbor
                                if (Math.abs(elevation[neighborIdx] - elevation[currentIdx]) < 1e-6) {
                                    steepestNeighbor = neighborIdx;
                                }
                            }
                        }
                    }

                    receiverIndex[currentIdx] = steepestNeighbor;
                    if (steepestNeighbor != -1) {
                        inDegree[steepestNeighbor]++;
                        upstreamCount[steepestNeighbor]++;
                    }
                }
            }

            // Step 3: Kahn's Algorithm for topological flow accumulation (§27)
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

        // Step 4: Reset topology labels so the graph is clean before
        // HydrologyField.analyze runs (basin/confluence ordinals are
        // -1 sentinels until then).
        Arrays.fill(basinCell, -1);
        Arrays.fill(confluenceCell, -1);
        basinCount = 0;
        confluenceCount = 0;
        topologyAnalyzed = false;
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

        x0 = GeoMath.clamp(x0, 0, GRID_DIM - 2);
        z0 = GeoMath.clamp(z0, 0, GRID_DIM - 2);

        double fx = GeoMath.clamp(cellX - x0, 0.0, 1.0);
        double fz = GeoMath.clamp(cellZ - z0, 0.0, 1.0);

        int idx00 = (z0 * GRID_DIM) + x0;
        int idx10 = idx00 + 1;
        int idx01 = ((z0 + 1) * GRID_DIM) + x0;
        int idx11 = idx01 + 1;

        double rawAcc = (1.0 - fx) * (1.0 - fz) * flowAccumulation[idx00]
                      + fx * (1.0 - fz) * flowAccumulation[idx10]
                      + (1.0 - fx) * fz * flowAccumulation[idx01]
                      + fx * fz * flowAccumulation[idx11];

        // Return raw interpolated flow accumulation (cell counts).
        // No log1p transform: consumers (RiverField, ChannelField, order taxonomy)
        // expect raw values for consistent scaling across debug and production paths.
        return Math.max(0.0, rawAcc);
    }

    /**
     * World X of the lattice point of routing cell {@code cell}.
     *
     * @param cell routing cell index (z-major: gz * GRID_DIM + gx)
     * @return world X of that lattice point in blocks
     */
    public double latticeX(int cell) {
        // z-major index (gz * GRID_DIM + gx): x is the minor axis (mod),
        // z is the major axis (div). Consistent with build()'s indexing.
        return gridOriginX + ((cell % GRID_DIM) * (double) CELL_SIZE);
    }

    /**
     * World Z of the lattice point of routing cell {@code cell}.
     *
     * @param cell routing cell index (z-major: gz * GRID_DIM + gx)
     * @return world Z of that lattice point in blocks
     */
    public double latticeZ(int cell) {
        return gridOriginZ + ((cell / GRID_DIM) * (double) CELL_SIZE);
    }
}