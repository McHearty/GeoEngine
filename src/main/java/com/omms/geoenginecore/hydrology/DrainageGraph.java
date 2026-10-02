package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.math.GeoMath;

import java.util.Arrays;

/**
 * Continuous drainage-field model for flow accumulation (TECHSPEC_AMEND001).
 *
 * <p>Computes the flow accumulation field Af by integrating the
 * conservation equation along characteristics of the continuous
 * drainage vector field. The vector field is derived from the
 * outlet-aware drainage potential Φ = H₀ + λ·D_outlet.
 *
 * <p>Grid dimensions are configurable via constructor parameters
 * (TECHSPEC_AMEND001 A2.2). The model uses a coarse lattice for
 * sampling but computes the drainage field continuously.
 */
public final class DrainageGraph {
    /** Edge length of one routing cell in blocks (configurable). */
    public final int CELL_SIZE;
    /** Core cells per axis (configurable from plateScale/gridSpacing). */
    public final int CORE_CELLS;
    /** Halo cells per boundary (configurable). */
    public final int HALO_CELLS;
    /** Total cells per axis (core + halo on both sides). */
    public final int GRID_DIM;
    /** Total cells in the expanded grid. */
    public final int TOTAL_CELLS;
    /** Outlet-aware drainage potential. */
    private final DrainagePotential potential;
    /** Continuous drainage accumulator. */
    private final DrainageAccumulator accumulator;

    /**
     * Constructs a drainage graph with the specified grid configuration.
     *
     * @param gridSpacing routing cell size in blocks (A2.2 gridSpacing)
     * @param plateScale overall plate/region size in blocks (A2.2 plateScale)
     */
    public DrainageGraph(double gridSpacing, double plateScale) {
        this(gridSpacing, plateScale, 0.05, 1e-6, 1.0);
    }

    /**
     * Constructs a drainage graph with the specified grid configuration
     * and continuous drainage-field parameters.
     *
     * @param gridSpacing routing cell size in blocks (A2.2 gridSpacing)
     * @param plateScale overall plate/region size in blocks (A2.2 plateScale)
     * @param outletLambda outlet attraction strength λ
     * @param epsilon regularization constant ε
     * @param stepSize finite difference step size in blocks
     */
    public DrainageGraph(double gridSpacing, double plateScale, double outletLambda,
                         double epsilon, double stepSize) {
        this.CELL_SIZE = (int) Math.max(4.0, gridSpacing);
        this.CORE_CELLS = (int) Math.max(2, plateScale / this.CELL_SIZE);
        this.HALO_CELLS = 4;
        this.GRID_DIM = this.CORE_CELLS + (this.HALO_CELLS * 2);
        this.TOTAL_CELLS = this.GRID_DIM * this.GRID_DIM;

        this.potential = new DrainagePotential(outletLambda, epsilon, stepSize);
        this.accumulator = new DrainageAccumulator(this.potential, 64, 2.0);

        // Reallocate arrays with computed dimensions
        this.elevation = new double[TOTAL_CELLS];
        this.receiverIndex = new int[TOTAL_CELLS];
        this.flowAccumulation = new double[TOTAL_CELLS];
        this.upstreamCount = new int[TOTAL_CELLS];
        this.basinCell = new int[TOTAL_CELLS];
        this.confluenceCell = new int[TOTAL_CELLS];
        this.basinStableId = new long[TOTAL_CELLS];
        this.confluenceStableId = new long[TOTAL_CELLS];
        this.basinSinkCell = new int[TOTAL_CELLS];
        this.inDegree = new int[TOTAL_CELLS];
        this.topoQueue = new int[TOTAL_CELLS];
    }

    /** H₀ elevation at each cell center. */
    public final double[] elevation;
    /** Downstream receiver of each cell (−1 for sinks). */
    public final int[] receiverIndex;
    /** Catchment discharge A_f of each cell, including upstream contributions. */
    public final double[] flowAccumulation;
    /**
     * Upstream sender count per cell, snapshotted when routing is
     * resolved (TECHSPEC §26, §31). A cell with upstreamCount ≥ 2 is a
     * confluence: the first-class confluence detection signal that
     * Kahn's algorithm would otherwise consume.
     */
    public final int[] upstreamCount;
    /**
     * Basin ordinal of each cell (index into {@link #basinStableId});
     * -1 until {@link HydrologyField#analyze} completes. The basin
     * partition is topological: every cell drains to exactly one
     * regional sink (elevations strictly decrease along the receiver
     * chain, so every walk terminates).
     */
    public final int[] basinCell;
    /** Confluence ordinal of each cell; -1 when the cell is not a confluence. */
    public final int[] confluenceCell;
    /** Stable scoped basin IDs indexed by basin ordinal (§80). */
    public final long[] basinStableId;
    /** Stable scoped confluence IDs indexed by confluence ordinal (§80). */
    public final long[] confluenceStableId;
    /** Sink cell of each basin, indexed by basin ordinal. */
    public final int[] basinSinkCell;
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
    private final int[] inDegree;
    /** FIFO work queue for topological accumulation. */
    private final int[] topoQueue;

    /** World X of the expanded grid origin. */
    private int gridOriginX;
    /** World Z of the expanded grid origin. */
    private int gridOriginZ;

    /**
     * Builds the continuous drainage-field model across the expanded
     * catchment (TECHSPEC_AMEND001).
     *
     * <p>Step 1 samples H₀ at every cell center. Step 2 computes the
     * continuous drainage vector field V = -∇Φ/‖∇Φ‖ from the
     * outlet-aware potential Φ. Step 3 computes flow accumulation Af
     * by integrating along characteristics of V (not using D8 routing).
     *
     * @param kernel H₀ kernel of the current configuration
     * @param regionOriginX world X of the region's core origin
     * @param regionOriginZ world Z of the region's core origin
     * @param iterations fixed-iteration count K (≥ 1); used for convergence
     */
    public void buildRegion(ScalarFieldKernel kernel, int regionOriginX, int regionOriginZ, int iterations) {
        this.gridOriginX = regionOriginX - (HALO_CELLS * CELL_SIZE);
        this.gridOriginZ = regionOriginZ - (HALO_CELLS * CELL_SIZE);

        // Step 1: Sample 24x24 coarse elevation lattice H0(gx, gz) once
        // Use cheap proxy for drainage topology: tectonic uplift only,
        // no climate/erosion fine-scale variation. Large-scale drainage
        // patterns are driven by uplift, not erosion.
        int idx = 0;
        for (int gz = 0; gz < GRID_DIM; gz++) {
            double wz = gridOriginZ + (gz * CELL_SIZE);
            for (int gx = 0; gx < GRID_DIM; gx++) {
                double wx = gridOriginX + (gx * CELL_SIZE);
                elevation[idx] = kernel.evaluatePureH0Proxy(wx, wz);
                idx++;
            }
        }

        // Step 2: Compute flow accumulation using continuous characteristic integration
        // (TECHSPEC_AMEND001: ∇·(Af V) = q, solved by integrating along characteristics)
        // No D8 receivers - each cell traces upstream independently.
        for (int gz = 0; gz < GRID_DIM; gz++) {
            for (int gx = 0; gx < GRID_DIM; gx++) {
                int currentIdx = (gz * GRID_DIM) + gx;
                double wx = gridOriginX + (gx * CELL_SIZE);
                double wz = gridOriginZ + (gz * CELL_SIZE);

                // Find outlet reference (lowest neighbor) for potential computation
                double lowestNeighborElev = elevation[currentIdx];
                int lowestNeighborIdx = -1;
                for (int ddz = -1; ddz <= 1; ddz++) {
                    int nz = gz + ddz;
                    if (nz < 0 || nz >= GRID_DIM) continue;
                    for (int ddx = -1; ddx <= 1; ddx++) {
                        if (ddx == 0 && ddz == 0) continue;
                        int nx = gx + ddx;
                        if (nx < 0 || nx >= GRID_DIM) continue;
                        int neighborIdx = (nz * GRID_DIM) + nx;
                        if (elevation[neighborIdx] < lowestNeighborElev) {
                            lowestNeighborElev = elevation[neighborIdx];
                            lowestNeighborIdx = neighborIdx;
                        }
                    }
                }

                // Compute outlet reference point
                double outletX = wx;
                double outletZ = wz;
                if (lowestNeighborIdx >= 0) {
                    outletX = gridOriginX + ((lowestNeighborIdx % GRID_DIM) * CELL_SIZE);
                    outletZ = gridOriginZ + ((lowestNeighborIdx / GRID_DIM) * CELL_SIZE);
                }

                // Compute flow accumulation by integrating along characteristics
                // This solves ∇·(Af V) = q by tracing upstream and integrating source density
                flowAccumulation[currentIdx] = accumulator.computeAccumulation(
                    kernel, wx, wz, outletX, outletZ);
            }
        }

        // Step 3: Build receiver index using strict elevation-ordered total order
        // (TECHSPEC_AMEND001: guaranteed acyclic by construction)
        // Every edge goes to a strictly lower key (elevation, then index), so no cycles can exist.
        Arrays.fill(inDegree, 0);
        Arrays.fill(upstreamCount, 0);
        Arrays.fill(receiverIndex, -1);

        // Build total order: cells sorted by (elevation desc, index asc)
        Integer[] order = new Integer[TOTAL_CELLS];
        for (int i = 0; i < TOTAL_CELLS; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int cmp = Double.compare(elevation[b], elevation[a]); // descending elevation
            return (cmp != 0) ? cmp : Integer.compare(a, b);      // ascending index (tie-break)
        });

        // D8 neighbor offsets
        final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
        final int[] DZ = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (int cell : order) {
            int gx = cell % GRID_DIM;
            int gz = cell / GRID_DIM;

            double bestElev = elevation[cell];
            int bestIdx = -1;

            for (int k = 0; k < 8; k++) {
                int nx = gx + DX[k];
                int nz = gz + DZ[k];
                if (nx < 0 || nx >= GRID_DIM || nz < 0 || nz >= GRID_DIM) continue;
                int nIdx = (nz * GRID_DIM) + nx;
                double nElev = elevation[nIdx];
                // Strict downhill, or equal elevation with lower index (total order)
                if (nElev < bestElev || (nElev == bestElev && nIdx < cell)) {
                    bestElev = nElev;
                    bestIdx = nIdx;
                }
            }

            if (bestIdx != -1 && bestElev < elevation[cell]) {
                receiverIndex[cell] = bestIdx;
                inDegree[bestIdx]++;
                upstreamCount[bestIdx]++;
            }
            // else: sink (no downhill neighbor) — receiverIndex remains -1
        }

        // Step 4: Reset topology labels
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