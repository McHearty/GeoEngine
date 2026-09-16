package com.geoengine.core.simd;

import com.geoengine.core.cache.MacroGridCache;
import com.geoengine.core.math.FieldKernel;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.ScalarFieldKernel;
import com.geoengine.core.memory.WorkerScratchpad;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * Field-kernel implementation that combines cached scalar field evaluation with SIMD density
 * evaluation.
 *
 * <p>Surface-field generation currently delegates to {@link ScalarFieldKernel}; the vectorized
 * path is used for batched section-density evaluation.
 */
public final class VectorFieldKernel implements FieldKernel {
    /** Preferred vector width used by the SIMD density path. */
    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;

    /** Scalar implementation used for operations without a vectorized implementation. */
    private final ScalarFieldKernel fallbackKernel;

    /** Cache of previously evaluated macro-grid height fields. */
    private final MacroGridCache macroCache;

    /** Storage associated with the vectorized field-evaluation path. */
    private final SoABuffers soa = new SoABuffers();

    public VectorFieldKernel(long worldSeed, GeoConfig config) {
        this.fallbackKernel = new ScalarFieldKernel(worldSeed, config);
        this.macroCache = new MacroGridCache(2048);
    }

    @Override
    public void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        long key = MacroGridCache.packKey(chunkWorldX, chunkWorldZ);
        if (!macroCache.tryGet(key, scratchpad.macroH0)) {
            // Preserve the scalar implementation as the source of truth for macro-grid generation.
            fallbackKernel.evaluateMacroGrid(scratchpad, chunkWorldX, chunkWorldZ);
            macroCache.put(key, scratchpad.macroH0);
        }
    }

    @Override
    public void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        evaluateMacroGrid(scratchpad, chunkWorldX, chunkWorldZ);

        // Surface rasterization remains delegated to the scalar implementation until a vectorized
        // rasterization path is implemented.
        fallbackKernel.rasterizeSurfaceChunk(scratchpad, chunkWorldX, chunkWorldZ);
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        return fallbackKernel.evaluateDensity(scratchpad, worldX, worldY, worldZ);
    }

    /**
     * Evaluates the density of one vertical section in SIMD-width batches.
     *
     * <p>The calculation matches the scalar form {@code surfaceH - (y + warp + cave)} for the
     * sixteen vertical samples in the section.
     */
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
