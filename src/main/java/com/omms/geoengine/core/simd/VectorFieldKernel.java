package com.geoengine.core.simd;

import com.geoengine.core.cache.MacroGridCache;
import com.geoengine.core.math.FieldKernel;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.ScalarFieldKernel;
import com.geoengine.core.memory.WorkerScratchpad;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;

public final class VectorFieldKernel implements FieldKernel {
    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    private final ScalarFieldKernel fallbackKernel;
    private final MacroGridCache macroCache;
    private final SoABuffers soa = new SoABuffers();

    public VectorFieldKernel(long worldSeed, GeoConfig config) {
        this.fallbackKernel = new ScalarFieldKernel(worldSeed, config);
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
        fallbackKernel.rasterizeSurfaceChunk(scratchpad, chunkWorldX, chunkWorldZ);
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        return fallbackKernel.evaluateDensity(scratchpad, worldX, worldY, worldZ);
    }

    public void evaluateSectionDensityBatch(
        float[] outDensity, int outOffset, double surfaceH, int startY, double warpVal, double caveVal
    ) {
        int bound = SPECIES.loopBound(16);
        double yCorrection = warpVal + caveVal;

        for (int ly = 0; ly < bound; ly += SPECIES.length()) {
            double[] yVals = new double[SPECIES.length()];
            for (int lane = 0; lane < SPECIES.length(); lane++) {
                yVals[lane] = (startY + ly + lane) + yCorrection;
            }

            DoubleVector vy = DoubleVector.fromArray(SPECIES, yVals, 0);
            DoubleVector vSurf = DoubleVector.broadcast(SPECIES, surfaceH);
            DoubleVector vDensity = vSurf.sub(vy);

            for (int lane = 0; lane < SPECIES.length(); lane++) {
                outDensity[outOffset + ly + lane] = (float) vDensity.lane(lane);
            }
        }
    }
}
