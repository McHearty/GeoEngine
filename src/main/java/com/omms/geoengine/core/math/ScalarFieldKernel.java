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
        sample.climateMultiplier = climateField.computeMultiplier(sample.temperature, sample.humidity);

        sample.rawTectonic = tectonicField.evaluate(sample.warpedX, sample.warpedZ);
        sample.erosionLowering = erosionField.evaluateErosion(
            sample.warpedX, sample.warpedZ, sample.age, sample.climateMultiplier, sample.rawTectonic
        );

        sample.surfaceH0 = sample.rawTectonic - sample.erosionLowering;
        return sample.surfaceH0;
    }

    @Override
    public void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        final int originX = chunkWorldX - 4;
        final int originZ = chunkWorldZ - 4;
        final int delta = 4;
        int idx = 0;

        for (int gz = 0; gz < WorkerScratchpad.MACRO_GRID_DIM; gz++) {
            double wz = originZ + gz * delta;
            for (int gx = 0; gx < WorkerScratchpad.MACRO_GRID_DIM; gx++) {
                double wx = originX + gx * delta;
                scratchpad.macroH0[idx++] = evaluateH0(wx, wz, scratchpad.sample);
            }
        }
    }

    @Override
    public void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        evaluateMacroGrid(scratchpad, chunkWorldX, chunkWorldZ);

        final int macroDim = WorkerScratchpad.MACRO_GRID_DIM;
        final double invDelta = 0.25;

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

        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                int cIdx = (lz << 4) | lx;
                double hC = scratchpad.surfaceGrid[cIdx];
                double hN = (lz > 0) ? scratchpad.surfaceGrid[((lz - 1) << 4) | lx] : hC;
                double hS = (lz < 15) ? scratchpad.surfaceGrid[((lz + 1) << 4) | lx] : hC;
                double hW = (lx > 0) ? scratchpad.surfaceGrid[(lz << 4) | (lx - 1)] : hC;
                double hE = (lx < 15) ? scratchpad.surfaceGrid[(lz << 4) | (lx + 1)] : hC;

                scratchpad.gradXGrid[cIdx] = DerivativeSampler.gradientX(hW, hE, 2.0);
                scratchpad.gradZGrid[cIdx] = DerivativeSampler.gradientZ(hN, hS, 2.0);
                scratchpad.laplacianGrid[cIdx] = DerivativeSampler.laplacian(hC, hN, hS, hW, hE, 1.0);
            }
        }

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

                double flowAcc = DrainageRouter.computeAccumulationProxy(this, wx, wz, scratchpad.sample);
                double incision = riverField.computeIncision(flowAcc, slope, scratchpad.sample.climateMultiplier);
                
                double hStar = h0 - incision;
                
                double deposition = DepositionField.computeDeposition(
                    scratchpad.sample.erosionLowering, incision, slope, lap, hStar - config.seaLevel(), scratchpad.sample.age
                );

                scratchpad.surfaceGrid[cIdx] = hStar + deposition;
            }
        }
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
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
