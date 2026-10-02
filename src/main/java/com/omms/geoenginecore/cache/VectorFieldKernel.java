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

/**
 * Hardware SIMD field kernel (TECHSPEC §63-§64).
 *
 * <p>Uses the Java 21 incubator Vector API (preferred species) to
 * interpolate the 6×6 macro grid into the 16×16 chunk surface
 * arrays in 8-lane batches, with the Scalar kernel as the
 * authoritative fallback for everything the SIMD path does not
 * vectorize. Results must be bit-identical to the Scalar
 * reference (TECHSPEC §64).
 */
public final class VectorFieldKernel implements FieldKernel {
    /** Preferred SIMD double-lane species. */
    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    /** Lanes per vector. */
    private static final int V_LENGTH = SPECIES.length();

    /** Active validated configuration. */
    private final GeoConfig config;
    /** Scalar reference authority for non-vectorized stages. */
    private final ScalarFieldKernel fallbackKernel;
    /** LRU cache of evaluated macro grids. */
    private final MacroGridCache macroCache;

    /**
     * Overworld convenience constructor.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated Overworld configuration
     */
    public VectorFieldKernel(long worldSeed, GeoConfig config) {
        this(worldSeed, new OverworldProfile(config));
    }

    /**
     * @param worldSeed world seed that roots every seed domain
     * @param profile validated dimension profile
     */
    public VectorFieldKernel(long worldSeed, DimensionProfile profile) {
        this.config = profile.getConfig();
        this.fallbackKernel = new ScalarFieldKernel(worldSeed, profile);
        this.macroCache = new MacroGridCache(2048);
    }

    /**
     * Loads the chunk's 6×6 macro grid, computing it on cache miss.
     *
     * @param scratchpad worker scratchpad
     * @param chunkWorldX world-coordinate X of the chunk
     * @param chunkWorldZ world-coordinate Z of the chunk
     */
    @Override
    public void evaluatePlateGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        long key = MacroGridCache.packKey(chunkWorldX, chunkWorldZ);
        if (!macroCache.tryGet(key, scratchpad.macroH0)) {
            fallbackKernel.evaluatePlateGrid(scratchpad, chunkWorldX, chunkWorldZ);
            macroCache.put(key, scratchpad.macroH0);
        }
    }

    /**
     * Rasterizes the 16×16 chunk surface stage in SIMD (TECHSPEC
     * §4, §64): bilinear interpolation of every macro field into the
     * chunk grids and the climate multiplier, then the Scalar
     * kernel's deterministic finish stage.
     *
     * @param scratchpad worker scratchpad
     * @param chunkWorldX world-coordinate X of the chunk
     * @param chunkWorldZ world-coordinate Z of the chunk
     */
    @Override
    public void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        evaluatePlateGrid(scratchpad, chunkWorldX, chunkWorldZ);

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

    /**
     * Bilinearly interpolates one macro field into a row of chunk
     * surface slots.
     *
     * @param scratchpad worker scratchpad (lane work arrays)
     * @param macroArr 6×6 macro source grid
     * @param targetArr chunk surface destination grid
     * @param targetOffset destination row start
     * @param z0 lower macro row index
     * @param macroDim macro grid dimension (6)
     * @param w00 bilinear weight for the (x0, z0) corner
     * @param w10 bilinear weight for the (x0+1, z0) corner
     * @param w01 bilinear weight for the (x0, z0+1) corner
     * @param w11 bilinear weight for the (x0+1, z0+1) corner
     */
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

    /**
     * Vectorizes the canonical density column D = H_f − (y + W) − C
     * (TECHSPEC §49, §64).
     *
     * @param scratchpad worker scratchpad (lane work arrays)
     * @param worldX world X of the column
     * @param sectionMinY minimum Y of the 16-block section
     * @param worldZ world Z of the column
     * @param outDensities destination density array for the section
     */
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

    /**
     * @return the shared landform classifier (Scalar authority)
     */
    @Override
    public com.omms.geoenginecore.geomorphology.LandformClassifier getLandformClassifier() {
        return fallbackKernel.getLandformClassifier();
    }

    /**
     * Single-voxel density; delegates to the Scalar authority.
     *
     * @param scratchpad worker scratchpad
     * @param worldX world X of the voxel
     * @param worldY world Y of the voxel
     * @param worldZ world Z of the voxel
     * @return canonical voxel density
     */
    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        return fallbackKernel.evaluateDensity(scratchpad, worldX, worldY, worldZ);
    }

    /**
     * Full pipeline sample for a column; delegates to the Scalar
     * authority.
     *
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @param sample sample to fill
     */
    @Override
    public void evaluateFullColumn(double wx, double wz, GeoSample sample) {
        fallbackKernel.evaluateFullColumn(wx, wz, sample);
    }

    /**
     * @return the shared cave field (Scalar authority)
     */
    @Override public CaveField getCaveField() { return fallbackKernel.getCaveField(); }

    /**
     * @return the shared warp field (Scalar authority)
     */
    @Override public WarpField getWarpField() { return fallbackKernel.getWarpField(); }
}
