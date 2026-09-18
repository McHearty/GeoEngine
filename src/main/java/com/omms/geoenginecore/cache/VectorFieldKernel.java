package com.omms.geoenginecore.simd;

import com.omms.geoenginecore.cache.MacroGridCache;
import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.field.CaveField;
import com.omms.geoenginecore.field.WarpField;
import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;

public final class VectorFieldKernel implements FieldKernel {
    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final int V_LENGTH = SPECIES.length();

    private final GeoConfig config;
    private final ScalarFieldKernel fallbackKernel;
    private final MacroGridCache macroCache;

    public VectorFieldKernel(long worldSeed, GeoConfig config) {
        this(worldSeed, new OverworldProfile(config));
    }

    public VectorFieldKernel(long worldSeed, DimensionProfile profile) {
        this.config = profile.getConfig();
        this.fallbackKernel = new ScalarFieldKernel(worldSeed, profile);
        this.macroCache = new MacroGridCache(2048);
    }

    @Override
    public void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        long key = MacroGridCache.packKey(chunkWorldX, chunkWorldZ);
        if (!macroCache.tryGet(key, scratchpad.macroH0)) {
            fallbackKernel.evaluateMacroGrid(scratchpad, chunkWorldX, chunkWorldZ);
            macroCache.put(key, scratchpad.macroH0);
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
            DoubleVector vFz = DoubleVector.broadcast(SPECIES, fz);
            DoubleVector vOneMinusFz = DoubleVector.broadcast(SPECIES, 1.0 - fz);

            int rowOffset = lz << 4;

            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx += V_LENGTH) {
                for (int lane = 0; lane < V_LENGTH; lane++) {
                    int curX = lx + lane;
                    if (curX < 16) {
                        double continuousX = (curX + 4.0) * invDelta;
                        int x0 = (int) continuousX;
                        scratchpad.simdX0[lane] = x0;
                        scratchpad.simdFx[lane] = continuousX - x0;
                        scratchpad.simdOneMinusFx[lane] = 1.0 - scratchpad.simdFx[lane];
                    }
                }

                DoubleVector vFx = DoubleVector.fromArray(SPECIES, scratchpad.simdFx, 0);
                DoubleVector vOneMinusFx = DoubleVector.fromArray(SPECIES, scratchpad.simdOneMinusFx, 0);

                DoubleVector w00 = vOneMinusFx.mul(vOneMinusFz);
                DoubleVector w10 = vFx.mul(vOneMinusFz);
                DoubleVector w01 = vOneMinusFx.mul(vFz);
                DoubleVector w11 = vFx.mul(vFz);

                interpolateFieldVector(scratchpad, scratchpad.macroH0, scratchpad.surfaceGrid, rowOffset + lx, z0, macroDim, w00, w10, w01, w11);
                interpolateFieldVector(scratchpad, scratchpad.macroAge, scratchpad.ageGrid, rowOffset + lx, z0, macroDim, w00, w10, w01, w11);
                interpolateFieldVector(scratchpad, scratchpad.macroTemp, scratchpad.tempGrid, rowOffset + lx, z0, macroDim, w00, w10, w01, w11);
                interpolateFieldVector(scratchpad, scratchpad.macroHumid, scratchpad.humidGrid, rowOffset + lx, z0, macroDim, w00, w10, w01, w11);
                interpolateFieldVector(scratchpad, scratchpad.macroErosion, scratchpad.erosionGrid, rowOffset + lx, z0, macroDim, w00, w10, w01, w11);

                DoubleVector vTemp = DoubleVector.fromArray(SPECIES, scratchpad.tempGrid, rowOffset + lx);
                DoubleVector vHumid = DoubleVector.fromArray(SPECIES, scratchpad.humidGrid, rowOffset + lx);
                DoubleVector vKMin = DoubleVector.broadcast(SPECIES, config.climateMin());
                DoubleVector vKRange = DoubleVector.broadcast(SPECIES, config.climateMax() - config.climateMin());
                DoubleVector vHalf = DoubleVector.broadcast(SPECIES, 0.5);

                DoubleVector vClimateMult = vKMin.add(vKRange.mul(vHalf.mul(vTemp.add(vHumid))));
                vClimateMult.intoArray(scratchpad.climateMultGrid, rowOffset + lx);
            }
        }

        fallbackKernel.finishSurfaceProcessing(scratchpad, chunkWorldX, chunkWorldZ);
    }

    private void interpolateFieldVector(
        WorkerScratchpad scratchpad, double[] macroArr, double[] targetArr, int targetOffset,
        int z0, int macroDim, DoubleVector w00, DoubleVector w10, DoubleVector w01, DoubleVector w11
    ) {
        for (int lane = 0; lane < V_LENGTH; lane++) {
            int x0 = scratchpad.simdX0[lane];
            int idx00 = (z0 * macroDim) + x0;
            int idx10 = idx00 + 1;
            int idx01 = ((z0 + 1) * macroDim) + x0;
            int idx11 = idx01 + 1;

            scratchpad.simdN00[lane] = macroArr[idx00];
            scratchpad.simdN10[lane] = macroArr[idx10];
            scratchpad.simdN01[lane] = macroArr[idx01];
            scratchpad.simdN11[lane] = macroArr[idx11];
        }

        DoubleVector v00 = DoubleVector.fromArray(SPECIES, scratchpad.simdN00, 0);
        DoubleVector v10 = DoubleVector.fromArray(SPECIES, scratchpad.simdN10, 0);
        DoubleVector v01 = DoubleVector.fromArray(SPECIES, scratchpad.simdN01, 0);
        DoubleVector v11 = DoubleVector.fromArray(SPECIES, scratchpad.simdN11, 0);

        DoubleVector result = w00.mul(v00)
            .add(w10.mul(v10))
            .add(w01.mul(v01))
            .add(w11.mul(v11));

        result.intoArray(targetArr, targetOffset);
    }

    public void evaluateDensityColumnVector(
        WorkerScratchpad scratchpad, int worldX, int sectionMinY, int worldZ, float[] outDensities
    ) {
        int lx = worldX & 15;
        int lz = worldZ & 15;
        int cIdx = (lz << 4) | lx;

        double hf = scratchpad.surfaceGrid[cIdx];
        double gx = scratchpad.gradXGrid[cIdx];
        double gz = scratchpad.gradZGrid[cIdx];
        double slope = Math.sqrt(gx * gx + gz * gz);

        DoubleVector vHf = DoubleVector.broadcast(SPECIES, hf);

        for (int ly = 0; ly < 16; ly += V_LENGTH) {
            for (int lane = 0; lane < V_LENGTH; lane++) {
                int wy = sectionMinY + ly + lane;
                scratchpad.simdYVals[lane] = wy;
                scratchpad.simdWVals[lane] = fallbackKernel.getWarpField().evaluateWarp(worldX, wy, worldZ, slope);
                scratchpad.simdCVals[lane] = fallbackKernel.getCaveField().evaluateCave(worldX, wy, worldZ, hf);
            }

            DoubleVector vY = DoubleVector.fromArray(SPECIES, scratchpad.simdYVals, 0);
            DoubleVector vW = DoubleVector.fromArray(SPECIES, scratchpad.simdWVals, 0);
            DoubleVector vC = DoubleVector.fromArray(SPECIES, scratchpad.simdCVals, 0);

            DoubleVector vDensity = vHf.sub(vY.add(vW)).sub(vC);
            vDensity.intoArray(scratchpad.simdResults, 0);

            for (int lane = 0; lane < V_LENGTH; lane++) {
                outDensities[ly + lane] = (float) scratchpad.simdResults[lane];
            }
        }
    }

    @Override
    public com.omms.geoenginecore.geomorphology.LandformClassifier getLandformClassifier() {
        return fallbackKernel.getLandformClassifier();
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        return fallbackKernel.evaluateDensity(scratchpad, worldX, worldY, worldZ);
    }

    @Override
    public void evaluateFullColumn(double wx, double wz, GeoSample sample) {
        fallbackKernel.evaluateFullColumn(wx, wz, sample);
    }

    @Override public CaveField getCaveField() { return fallbackKernel.getCaveField(); }
    @Override public WarpField getWarpField() { return fallbackKernel.getWarpField(); }
}
