package com.geoengine.core.math;

import com.geoengine.core.derivative.DerivativeSampler;
import com.geoengine.core.field.*;
import com.geoengine.core.hydrology.*;
import com.geoengine.core.memory.WorkerScratchpad;

public final class ScalarFieldKernel implements FieldKernel {
    private final GeoConfig config;
    private final StressWarp stressWarp;
    private final TectonicField tectonicField;
    private final EpochField epochField;
    private final ClimateField climateField;
    private final ErosionField erosionField;
    private final RiverField riverField;
    private final WarpField warpField;
    private final CaveField caveField;

    /**
     * Creates a scalar field kernel for the specified world seed and generation configuration.
     *
     * @param worldSeed seed used by deterministic field generators
     * @param config world-generation configuration
     */
    public ScalarFieldKernel(long worldSeed, GeoConfig config) {
        this.config = config;
        this.stressWarp = new StressWarp(worldSeed, config);
        this.tectonicField = new TectonicField(worldSeed, config);
        this.epochField = new EpochField(worldSeed, config);
        this.climateField = new ClimateField(worldSeed, config);
        this.erosionField = new ErosionField(worldSeed, config);
        this.riverField = new RiverField(config);
        this.warpField = new WarpField(worldSeed, config);
        this.caveField = new CaveField(worldSeed, config);
    }

    /**
     * Evaluates the pre-hydrology surface height at a world-space position.
     *
     * <p>The evaluation applies stress warping, then derives geological age and climate,
     * evaluates the tectonic height, and subtracts climate-dependent erosion lowering.
     * The supplied sample is populated with the intermediate values produced by the
     * evaluation.
     *
     * @param wx world X coordinate
     * @param wz world Z coordinate
     * @param sample reusable storage for intermediate field values
     * @return pre-hydrology surface height
     */
    public double evaluateH0(double wx, double wz, GeoSample sample) {
        sample.worldX = wx;
        sample.worldZ = wz;
        sample.stressWarpX = stressWarp.getWarpX(wx, wz);
        sample.stressWarpZ = stressWarp.getWarpZ(wx, wz);
        sample.warpedX = wx + sample.stressWarpX;
        sample.warpedZ = wz + sample.stressWarpZ;

        sample.age = epochField.evaluateAge(sample.warpedX, sample.warpedZ);
        sample.temperature = climateField.evaluateTemperature(sample.warpedX, sample.warpedZ);
        sample.humidity = climateField.evaluateHumidity(sample.warpedX, sample.warpedZ);
        sample.climateMultiplier =
            climateField.computeMultiplier(sample.temperature, sample.humidity);

        sample.rawTectonic =
            tectonicField.evaluate(sample.warpedX, sample.warpedZ);
        sample.erosionLowering = erosionField.evaluateErosion(
            sample.warpedX,
            sample.warpedZ,
            sample.age,
            sample.climateMultiplier,
            sample.rawTectonic);

        sample.surfaceH0 = sample.rawTectonic - sample.erosionLowering;
        return sample.surfaceH0;
    }

    /**
     * Evaluates the pre-hydrology surface on the macro-grid surrounding a chunk.
     *
     * <p>The macro-grid extends four world units beyond the chunk on each side and
     * samples the field at four-unit intervals.
     *
     * @param scratchpad reusable worker-local storage
     * @param chunkWorldX world X coordinate of the chunk origin
     * @param chunkWorldZ world Z coordinate of the chunk origin
     */
    @Override
    public void evaluateMacroGrid(
            WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        final int originX = chunkWorldX - 4;
        final int originZ = chunkWorldZ - 4;
        final int delta = 4;
        int idx = 0;

        for (int gz = 0; gz < WorkerScratchpad.MACRO_GRID_DIM; gz++) {
            double wz = originZ + gz * delta;
            for (int gx = 0; gx < WorkerScratchpad.MACRO_GRID_DIM; gx++) {
                double wx = originX + gx * delta;
                scratchpad.macroH0[idx++] =
                    evaluateH0(wx, wz, scratchpad.sample);
            }
        }
    }

    /**
     * Builds the chunk surface and applies hydrological incision and deposition.
     *
     * <p>The surface is first reconstructed from the macro-grid using bilinear interpolation.
     * Gradients and curvature are then calculated from the interpolated surface before river
     * incision and sediment deposition are applied.
     *
     * @param scratchpad reusable worker-local storage
     * @param chunkWorldX world X coordinate of the chunk origin
     * @param chunkWorldZ world Z coordinate of the chunk origin
     */
    @Override
    public void rasterizeSurfaceChunk(
            WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        evaluateMacroGrid(scratchpad, chunkWorldX, chunkWorldZ);

        final int macroDim = WorkerScratchpad.MACRO_GRID_DIM;
        final double invDelta = 0.25;

        // Reconstruct the 16x16 chunk surface from the four-unit macro-grid.
        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            double continuousZ = (lz + 4.0) * invDelta;
            int z0 = (int) continuousZ;
            double fz = continuousZ - z0;

            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                double continuousX = (lx + 4.0) * invDelta;
                int x0 = (int) continuousX;
                double fx = continuousX - x0;

                int idx00 = z0 * macroDim + x0;
                int idx10 = idx00 + 1;
                int idx01 = (z0 + 1) * macroDim + x0;
                int idx11 = idx01 + 1;

                double h0 = (1.0 - fx) * (1.0 - fz) * scratchpad.macroH0[idx00]
                          + fx * (1.0 - fz) * scratchpad.macroH0[idx10]
                          + (1.0 - fx) * fz * scratchpad.macroH0[idx01]
                          + fx * fz * scratchpad.macroH0[idx11];

                scratchpad.surfaceGrid[(lz << 4) | lx] = h0;
            }
        }

        // Calculate surface derivatives before modifying the surface with hydrology.
        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                int cIdx = (lz << 4) | lx;
                double hC = scratchpad.surfaceGrid[cIdx];
                double hN = (lz > 0) ? scratchpad.surfaceGrid[((lz - 1) << 4) | lx] : hC;
                double hS = (lz < 15) ? scratchpad.surfaceGrid[((lz + 1) << 4) | lx] : hC;
                double hW = (lx > 0) ? scratchpad.surfaceGrid[(lz << 4) | (lx - 1)] : hC;
                double hE = (lx < 15) ? scratchpad.surfaceGrid[(lz << 4) | (lx + 1)] : hC;

                scratchpad.gradXGrid[cIdx] =
                    DerivativeSampler.gradientX(hW, hE, 2.0);
                scratchpad.gradZGrid[cIdx] =
                    DerivativeSampler.gradientZ(hN, hS, 2.0);
                scratchpad.laplacianGrid[cIdx] =
                    DerivativeSampler.laplacian(hC, hN, hS, hW, hE, 1.0);
            }
        }

        // Apply hydrological incision and deposition to the reconstructed surface.
        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            int wz = chunkWorldZ + lz;
            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                int wx = chunkWorldX + lx;
                int cIdx = (lz << 4) | lx;

                double h0 = scratchpad.surfaceGrid[cIdx];
                double gx = scratchpad.gradXGrid[cIdx];
                double gz = scratchpad.gradZGrid[cIdx];
                double slope = DerivativeSampler.magnitude(gx, gz);
                double lap = scratchpad.laplacianGrid[cIdx];

                double flowAcc =
                    DrainageRouter.computeAccumulationProxy(
                        this, wx, wz, scratchpad.sample);
                double incision =
                    riverField.computeIncision(
                        flowAcc, slope, scratchpad.sample.climateMultiplier);

                double hStar = h0 - incision;

                double deposition = DepositionField.computeDeposition(
                    scratchpad.sample.erosionLowering,
                    incision,
                    slope,
                    lap,
                    hStar - config.seaLevel(),
                    scratchpad.sample.age);

                scratchpad.surfaceGrid[cIdx] = hStar + deposition;
            }
        }
    }

    /**
     * Evaluates the signed terrain density at a world-space block position.
     *
     * <p>Positive values represent positions below the generated surface and negative values
     * represent positions above it. The density combines the chunk surface, vertical warp,
     * and cave field.
     *
     * <p>If the calculated density is non-finite, the result falls back to solid below sea
     * level and empty above sea level.
     *
     * @param scratchpad worker-local surface data for the current chunk
     * @param worldX world X coordinate
     * @param worldY world Y coordinate
     * @param worldZ world Z coordinate
     * @return signed terrain density
     */
    @Override
    public float evaluateDensity(
            WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        int lx = worldX & 15;
        int lz = worldZ & 15;
        double hf = scratchpad.surfaceGrid[(lz << 4) | lx];

        double w = warpField.evaluateWarp(worldX, worldY, worldZ);
        double c = caveField.evaluateCave(worldX, worldY, worldZ, hf);

        double d = hf - ((double) worldY + w) - c;

        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return (worldY < config.seaLevel()) ? 1.0f : -1.0f;
        }
        return (float) d;
    }
}
