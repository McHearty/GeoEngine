package com.omms.geoenginecore.test.fixtures;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;

/**
 * Hydrology probing utilities for testing river incision, channel shape,
 * and flow-accumulation patterns (TECHSPEC §28, §80).
 *
 * <p>Rasters a full hydrology region and provides access to incision,
 * flow-accumulation, and surface data for analysis.
 */
public final class HydrologyProbe {

    private final ScalarFieldKernel kernel;
    private final GeoConfig config;
    private final WorkerScratchpad scratchpad;

    /** Per-chunk incision grids for the region (16×16 chunks). */
    private double[][][] incisionGrids;

    /** Per-chunk flow-accumulation grids for the region (16×16 chunks). */
    private double[][][] flowAccGrids;

    /** Per-chunk surface grids for the region (16×16 chunks). */
    private double[][][] surfaceGrids;

    /** Region origin in chunk coordinates. */
    private int regionChunkX;
    private int regionChunkZ;

    public HydrologyProbe(ScalarFieldKernel kernel, GeoConfig config) {
        this.kernel = kernel;
        this.config = config;
        this.scratchpad = ScratchpadProvider.get();
    }

    /**
     * Rasters the full hydrology region at the given chunk origin and
     * collects all incision, flow-accumulation, and surface grids.
     */
    public void rasterize(int regionChunkX, int regionChunkZ) {
        this.regionChunkX = regionChunkX;
        this.regionChunkZ = regionChunkZ;

        int regionSize = TestFixtures.REGION_CHUNKS;
        incisionGrids = new double[regionSize][regionSize][WorkerScratchpad.CHUNK_SURFACE_SIZE];
        flowAccGrids = new double[regionSize][regionSize][WorkerScratchpad.CHUNK_SURFACE_SIZE];
        surfaceGrids = new double[regionSize][regionSize][WorkerScratchpad.CHUNK_SURFACE_SIZE];

        for (int cz = 0; cz < regionSize; cz++) {
            for (int cx = 0; cx < regionSize; cx++) {
                int worldChunkX = regionChunkX * regionSize + cx;
                int worldChunkZ = regionChunkZ * regionSize + cz;

                kernel.rasterizeSurfaceChunk(scratchpad, worldChunkX, worldChunkZ);

                System.arraycopy(scratchpad.riverIncisionGrid, 0, incisionGrids[cz][cx], 0,
                        WorkerScratchpad.CHUNK_SURFACE_SIZE);
                System.arraycopy(scratchpad.flowAccGrid, 0, flowAccGrids[cz][cx], 0,
                        WorkerScratchpad.CHUNK_SURFACE_SIZE);
                System.arraycopy(scratchpad.surfaceGrid, 0, surfaceGrids[cz][cx], 0,
                        WorkerScratchpad.CHUNK_SURFACE_SIZE);
            }
        }
    }

    /**
     * Returns the maximum river incision across the entire rasterized region.
     */
    public double maxIncision() {
        double max = Double.NEGATIVE_INFINITY;
        for (double[][] chunkGrid : incisionGrids) {
            for (double[] grid : chunkGrid) {
                for (double v : grid) {
                    if (v > max) {
                        max = v;
                    }
                }
            }
        }
        return max;
    }

    /**
     * Returns the number of cells with incision ≥ eps across the region.
     */
    public long countCarved(double eps) {
        long count = 0;
        for (double[][] chunkGrid : incisionGrids) {
            for (double[] grid : chunkGrid) {
                for (double v : grid) {
                    if (v >= eps) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Samples the incision at a world-space XZ coordinate.
     */
    public double sampleIncision(int worldX, int worldZ) {
        int regionSize = TestFixtures.REGION_CHUNKS;
        int localChunkX = (worldX / 16) % regionSize;
        int localChunkZ = (worldZ / 16) % regionSize;
        int localX = worldX % 16;
        int localZ = worldZ % 16;

        return incisionGrids[localChunkZ][localChunkX][(localZ << 4) | localX];
    }

    /**
     * Samples the flow accumulation at a world-space XZ coordinate.
     */
    public double sampleFlowAccumulation(int worldX, int worldZ) {
        int regionSize = TestFixtures.REGION_CHUNKS;
        int localChunkX = (worldX / 16) % regionSize;
        int localChunkZ = (worldZ / 16) % regionSize;
        int localX = worldX % 16;
        int localZ = worldZ % 16;

        return flowAccGrids[localChunkZ][localChunkX][(localZ << 4) | localX];
    }

    /**
     * Finds a column with flow accumulation ≥ threshold.
     * Returns null if none found.
     */
    public ProbeColumn findHighFlowColumn(double threshold) {
        for (int cz = 0; cz < incisionGrids.length; cz++) {
            for (int cx = 0; cx < incisionGrids[cz].length; cx++) {
                for (int i = 0; i < incisionGrids[cz][cx].length; i++) {
                    double af = flowAccGrids[cz][cx][i];
                    if (af >= threshold) {
                        int localX = i & 0xF;
                        int localZ = (i >> 4) & 0xF;
                        int worldX = regionChunkX * 256 + cx * 16 + localX;
                        int worldZ = regionChunkZ * 256 + cz * 16 + localZ;
                        return new ProbeColumn(worldX, worldZ, af, incisionGrids[cz][cx][i]);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the channel half-width at a given flow-accumulation value
     * (TECHSPEC §28).
     */
    public static int channelHalfWidth(double af) {
        if (af < TestFixtures.AF_RIVER_THRESHOLD) {
            return 0;
        }
        if (af < TestFixtures.AF_CHANNEL_THRESHOLD) {
            return 1;
        }
        return (int) Math.min(4.0, Math.sqrt(af) * 0.8);
    }

    /**
     * Result of probing a column for hydrology analysis.
     */
    public static final class ProbeColumn {
        public final int worldX;
        public final int worldZ;
        public final double flowAccumulation;
        public final double incision;

        public ProbeColumn(int worldX, int worldZ, double flowAccumulation, double incision) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.flowAccumulation = flowAccumulation;
            this.incision = incision;
        }
    }
}
