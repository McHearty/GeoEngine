package com.omms.geoenginecore.math;

import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.field.*;
import com.omms.geoenginecore.field.advanced.*;
import com.omms.geoenginecore.geomorphology.LandformClassifier;
import com.omms.geoenginecore.hydrology.*;
import com.omms.geoenginecore.memory.WorkerScratchpad;

public final class ScalarFieldKernel implements FieldKernel {
    private final long worldSeed;
    private final GeoConfig config;
    private final DimensionProfile profile;

    // Crustal Baseline
    private final StressWarp stressWarp;
    private final TectonicField tectonicField;
    private final EpochField epochField;
    private final ClimateField climateField;
    private final ErosionField erosionField;

    // Volumetric & Hydrology
    private final RiverField riverField;
    private final WarpField warpField;
    private final CaveField caveField;
    private final DrainageRouter drainageRouter;

    // Advanced Geomorphic Process Modifiers
    private final GlacialField glacialField;
    private final AeolianField aeolianField;
    private final KarstField karstField;
    private final CoastalField coastalField;
    private final AlluvialDeltaField deltaField;
    private final VolcanicCalderaField volcanicField;
    private final VoronoiFractureField fractureField;

    // Morphological Classifier
    private final LandformClassifier landformClassifier;

    public ScalarFieldKernel(long worldSeed, GeoConfig config) {
        this(worldSeed, new OverworldProfile(config));
    }

    public ScalarFieldKernel(long worldSeed, DimensionProfile profile) {
        this.worldSeed = worldSeed;
        this.profile = profile;
        this.config = profile.getConfig();

        this.stressWarp = new StressWarp(worldSeed, config);
        this.tectonicField = new TectonicField(worldSeed, config);
        this.epochField = new EpochField(worldSeed, config);
        this.climateField = new ClimateField(worldSeed, config);
        this.erosionField = new ErosionField(worldSeed, config);
        this.riverField = new RiverField(config);
        this.warpField = new WarpField(worldSeed, config);
        this.caveField = new CaveField(worldSeed, config);
        this.drainageRouter = new DrainageRouter();

        this.glacialField = new GlacialField(worldSeed, config);
        this.aeolianField = new AeolianField(worldSeed, config);
        this.karstField = new KarstField(worldSeed, config);
        this.coastalField = new CoastalField(worldSeed, config);
        this.deltaField = new AlluvialDeltaField(worldSeed, config);
        this.volcanicField = new VolcanicCalderaField(worldSeed, config.generatorVersion());
        this.fractureField = new VoronoiFractureField(worldSeed, 144.0);

        this.landformClassifier = new LandformClassifier(config);
    }

    public double evaluatePureH0(double wx, double wz) {
        double sx = stressWarp.getWarpX(wx, wz);
        double sz = stressWarp.getWarpZ(wx, wz);
        double warpx = wx + sx;
        double warpz = wz + sz;

        double age = epochField.evaluateAge(warpx, warpz);
        double temp = climateField.evaluateTemperature(warpx, warpz);
        double humid = climateField.evaluateHumidity(warpx, warpz);
        double climateMult = climateField.computeMultiplier(temp, humid);

        double tectonic = tectonicField.evaluate(warpx, warpz);
        double erosion = erosionField.evaluateErosion(warpx, warpz, age, climateMult, tectonic);

        return tectonic - erosion;
    }

    public double evaluatePreFluvialSurface(double wx, double wz, double h0, double temp, double humid, double slope) {
        double hPre = h0;

        if (profile.hasVoronoiFracture()) {
            VoronoiFractureField.FractureSample sample = new VoronoiFractureField.FractureSample();
            fractureField.sample(wx, wz, sample);
            double drop = (sample.distanceToEdge < 14.0) ? (38.0 * (1.0 - Math.pow(sample.distanceToEdge / 14.0, 2))) : 0.0;
            return sample.cellElevation - drop;
        }

        if (profile.hasGlacialProcesses()) {
            double gInt = Math.clamp((h0 - (config.seaLevel() + 200.0)) / 150.0, 0.0, 1.0);
            hPre += glacialField.evaluateUValleyModification(wx, wz, h0, slope, temp, gInt);
            hPre += glacialField.evaluateCirqueBowl(wx, wz, h0, temp);
        }

        if (profile.hasKarstProcesses()) {
            hPre += karstField.evaluateSinkholeRelief(wx, wz, temp, humid);
            hPre += karstField.evaluateTowerKarstRelief(wx, wz, temp, humid);
        }

        if (profile.hasAeolianProcesses()) {
            hPre += aeolianField.evaluateDuneRelief(wx, wz, temp, humid, slope);
        }

        if (profile.hasVolcanicProcesses() && h0 > 180.0) {
            hPre += volcanicField.evaluateVolcanicRelief(wx, wz);
        }

        return hPre;
    }

    public double evaluateFullFlowAccumulation(double wx, double wz) {
        return drainageRouter.computeAccumulationProxy(this, worldSeed, config.configHash(), wx, wz);
    }

    @Override
    public void evaluateFullColumn(double wx, double wz, GeoSample sample) {
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

        final double delta = 1.0;
        double hE0 = evaluatePureH0(wx + delta, wz);
        double hW0 = evaluatePureH0(wx - delta, wz);
        double hS0 = evaluatePureH0(wx, wz + delta);
        double hN0 = evaluatePureH0(wx, wz - delta);
        double baseSlope = Math.sqrt(Math.pow((hE0 - hW0) / 2.0, 2) + Math.pow((hS0 - hN0) / 2.0, 2));

        double hPre = evaluatePreFluvialSurface(wx, wz, sample.surfaceH0, sample.temperature, sample.humidity, baseSlope);

        double hPreE = evaluatePreFluvialSurface(wx + delta, wz, hE0, sample.temperature, sample.humidity, baseSlope);
        double hPreW = evaluatePreFluvialSurface(wx - delta, wz, hW0, sample.temperature, sample.humidity, baseSlope);
        double hPreS = evaluatePreFluvialSurface(wx, wz + delta, hS0, sample.temperature, sample.humidity, baseSlope);
        double hPreN = evaluatePreFluvialSurface(wx, wz - delta, hN0, sample.temperature, sample.humidity, baseSlope);

        sample.gradX = (hPreE - hPreW) / (2.0 * delta);
        sample.gradZ = (hPreS - hPreN) / (2.0 * delta);
        sample.gradMagnitude = Math.sqrt(sample.gradX * sample.gradX + sample.gradZ * sample.gradZ);
        sample.laplacian = (hPreN + hPreS + hPreW + hPreE - 4.0 * hPre) / (delta * delta);

        double flowAcc = 0.0;
        double incision = 0.0;
        if (profile.hasFluvialHydrology()) {
            flowAcc = evaluateFullFlowAccumulation(wx, wz);
            incision = riverField.computeIncision(flowAcc, sample.gradMagnitude, sample.climateMultiplier);
        }
        sample.flowAccumulation = flowAcc;
        sample.riverIncision = incision;
        double hStar = hPre - incision;

        double deposition = 0.0;
        if (profile.hasFluvialHydrology()) {
            deposition = DepositionField.computeDeposition(
                sample.erosionLowering, incision, sample.gradMagnitude,
                sample.laplacian, hStar - config.seaLevel(), sample.age
            );
            deposition += deltaField.evaluateAlluvialFan(flowAcc, sample.gradMagnitude, sample.laplacian, sample.erosionLowering);
            deposition += deltaField.evaluateDeltaLobe(wx, wz, hStar, flowAcc, incision);
        }
        sample.deposition = deposition;

        double hFinal = hStar + deposition;
        if (profile.hasCoastalProcesses()) {
            hFinal += coastalField.evaluateWaveCutPlatform(hFinal);
        }
        sample.finalSurface = hFinal;

        // Diagonal samples for 9-point Hessian curvature stencil
        double hPreNW = evaluatePreFluvialSurface(wx - delta, wz - delta, evaluatePureH0(wx - delta, wz - delta), sample.temperature, sample.humidity, baseSlope);
        double hPreNE = evaluatePreFluvialSurface(wx + delta, wz - delta, evaluatePureH0(wx + delta, wz - delta), sample.temperature, sample.humidity, baseSlope);
        double hPreSW = evaluatePreFluvialSurface(wx - delta, wz + delta, evaluatePureH0(wx - delta, wz + delta), sample.temperature, sample.humidity, baseSlope);
        double hPreSE = evaluatePreFluvialSurface(wx + delta, wz + delta, evaluatePureH0(wx + delta, wz + delta), sample.temperature, sample.humidity, baseSlope);

        landformClassifier.classify(
            this, sample,
            hFinal, hPreN, hPreS, hPreW, hPreE,
            hPreNW, hPreNE, hPreSW, hPreSE, delta
        );
    }

    public double evaluateH0(double wx, double wz, GeoSample sample) {
        evaluateFullColumn(wx, wz, sample);
        return sample.finalSurface;
    }

    @Override
    public void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        final int originX = chunkWorldX - 4;
        final int originZ = chunkWorldZ - 4;
        final int delta = 4;
        int idx = 0;

        for (int gz = 0; gz < WorkerScratchpad.MACRO_GRID_DIM; gz++) {
            double wz = originZ + (gz * delta);
            for (int gx = 0; gx < WorkerScratchpad.MACRO_GRID_DIM; gx++) {
                double wx = originX + (gx * delta);

                double sx = stressWarp.getWarpX(wx, wz);
                double sz = stressWarp.getWarpZ(wx, wz);
                double warpx = wx + sx;
                double warpz = wz + sz;

                double age = epochField.evaluateAge(warpx, warpz);
                double temp = climateField.evaluateTemperature(warpx, warpz);
                double humid = climateField.evaluateHumidity(warpx, warpz);
                double tectonic = tectonicField.evaluate(warpx, warpz);
                double climateMult = climateField.computeMultiplier(temp, humid);
                double erosion = erosionField.evaluateErosion(warpx, warpz, age, climateMult, tectonic);

                scratchpad.macroH0[idx] = tectonic - erosion;
                scratchpad.macroTectonic[idx] = tectonic;
                scratchpad.macroAge[idx] = age;
                scratchpad.macroTemp[idx] = temp;
                scratchpad.macroHumid[idx] = humid;
                scratchpad.macroErosion[idx] = erosion;
                idx++;
            }
        }
    }

    @Override
    public void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        evaluateMacroGrid(scratchpad, chunkWorldX, chunkWorldZ);

        final int macroDim = WorkerScratchpad.MACRO_GRID_DIM;
        final double invDelta = 0.25;

        // Step 1: Interpolate H0 baseline, age, climate, erosion
        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            double continuousZ = (lz + 4.0) * invDelta;
            int z0 = (int) continuousZ;
            double fz = continuousZ - z0;

            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                double continuousX = (lx + 4.0) * invDelta;
                int x0 = (int) continuousX;
                double fx = continuousX - x0;

                int idx00 = (z0 * macroDim) + x0;
                int idx10 = idx00 + 1;
                int idx01 = ((z0 + 1) * macroDim) + x0;
                int idx11 = idx01 + 1;

                int cIdx = (lz << 4) | lx;

                // Position-correct continuous interpolation
                double h0 = bilerp(scratchpad.macroH0, idx00, idx10, idx01, idx11, fx, fz);
                scratchpad.surfaceGrid[cIdx] = h0;
                scratchpad.h0Grid[cIdx] = h0; // <--- Stores H0 for preliminary surface checks
                scratchpad.ageGrid[cIdx] = bilerp(scratchpad.macroAge, idx00, idx10, idx01, idx11, fx, fz);
                scratchpad.tempGrid[cIdx] = bilerp(scratchpad.macroTemp, idx00, idx10, idx01, idx11, fx, fz);
                scratchpad.humidGrid[cIdx] = bilerp(scratchpad.macroHumid, idx00, idx10, idx01, idx11, fx, fz);
                scratchpad.erosionGrid[cIdx] = bilerp(scratchpad.macroErosion, idx00, idx10, idx01, idx11, fx, fz);
                scratchpad.climateMultGrid[cIdx] = climateField.computeMultiplier(
                    scratchpad.tempGrid[cIdx], scratchpad.humidGrid[cIdx]
                );
            }
        }

        finishSurfaceProcessing(scratchpad, chunkWorldX, chunkWorldZ);
    }

    public void finishSurfaceProcessing(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ) {
        // 1. Additive Pre-Fluvial Modifiers
        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            int wz = chunkWorldZ + lz;
            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                int wx = chunkWorldX + lx;
                int cIdx = (lz << 4) | lx;

                double h0 = scratchpad.surfaceGrid[cIdx];
                double temp = scratchpad.tempGrid[cIdx];
                double humid = scratchpad.humidGrid[cIdx];

                scratchpad.surfaceGrid[cIdx] = evaluatePreFluvialSurface(wx, wz, h0, temp, humid, 0.2);
            }
        }

        // 2. Gradients and Laplacian on H_pre (Seamless boundary halo sampling, §76)
        final double delta = 1.0;
        final double d2 = delta * delta;

        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            int wz = chunkWorldZ + lz;
            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                int wx = chunkWorldX + lx;
                int cIdx = (lz << 4) | lx;
                double hC = scratchpad.surfaceGrid[cIdx];

                double hW = (lx > 0) ? scratchpad.surfaceGrid[(lz << 4) | (lx - 1)] 
                                     : evaluatePreFluvialSurface(wx - 1, wz, evaluatePureH0(wx - 1, wz), scratchpad.tempGrid[cIdx], scratchpad.humidGrid[cIdx], 0.2);
                double hE = (lx < 15) ? scratchpad.surfaceGrid[(lz << 4) | (lx + 1)] 
                                      : evaluatePreFluvialSurface(wx + 1, wz, evaluatePureH0(wx + 1, wz), scratchpad.tempGrid[cIdx], scratchpad.humidGrid[cIdx], 0.2);

                double hN = (lz > 0) ? scratchpad.surfaceGrid[((lz - 1) << 4) | lx] 
                                     : evaluatePreFluvialSurface(wx, wz - 1, evaluatePureH0(wx, wz - 1), scratchpad.tempGrid[cIdx], scratchpad.humidGrid[cIdx], 0.2);
                double hS = (lz < 15) ? scratchpad.surfaceGrid[((lz + 1) << 4) | lx] 
                                      : evaluatePreFluvialSurface(wx, wz + 1, evaluatePureH0(wx, wz + 1), scratchpad.tempGrid[cIdx], scratchpad.humidGrid[cIdx], 0.2);

                scratchpad.gradXGrid[cIdx] = (hE - hW) / (2.0 * delta);
                scratchpad.gradZGrid[cIdx] = (hS - hN) / (2.0 * delta);
                scratchpad.laplacianGrid[cIdx] = (hN + hS + hW + hE - 4.0 * hC) / d2;
            }
        }

        // 3. Fluvial Incision, Deposition, and Coastal Wave-Cut Finishing
        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            int wz = chunkWorldZ + lz;
            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                int wx = chunkWorldX + lx;
                int cIdx = (lz << 4) | lx;

                double hPre = scratchpad.surfaceGrid[cIdx];
                double gx = scratchpad.gradXGrid[cIdx];
                double gz = scratchpad.gradZGrid[cIdx];
                double slope = Math.sqrt(gx * gx + gz * gz);
                double lap = scratchpad.laplacianGrid[cIdx];

                double climateMult = scratchpad.climateMultGrid[cIdx];
                double localErosion = scratchpad.erosionGrid[cIdx];
                double localAge = scratchpad.ageGrid[cIdx];

                double flowAcc = 0.0;
                double incision = 0.0;
                if (profile.hasFluvialHydrology()) {
                    flowAcc = evaluateFullFlowAccumulation(wx, wz);
                    incision = riverField.computeIncision(flowAcc, slope, climateMult);
                }
                double hStar = hPre - incision;

                double deposition = 0.0;
                if (profile.hasFluvialHydrology()) {
                    deposition = DepositionField.computeDeposition(localErosion, incision, slope, lap, hStar - config.seaLevel(), localAge);
                    deposition += deltaField.evaluateAlluvialFan(flowAcc, slope, lap, localErosion);
                    deposition += deltaField.evaluateDeltaLobe(wx, wz, hStar, flowAcc, incision);
                }

                double hFinal = hStar + deposition;
                if (profile.hasCoastalProcesses()) {
                    hFinal += coastalField.evaluateWaveCutPlatform(hFinal);
                }

                scratchpad.flowAccGrid[cIdx] = flowAcc;
                scratchpad.riverIncisionGrid[cIdx] = incision;
                scratchpad.depositionGrid[cIdx] = deposition;
                scratchpad.surfaceGrid[cIdx] = hFinal;
            }
        }

        // 4. Per-Column Landform Classification Pass
        for (int lz = 0; lz < WorkerScratchpad.CHUNK_DIM; lz++) {
            int wz = chunkWorldZ + lz;
            for (int lx = 0; lx < WorkerScratchpad.CHUNK_DIM; lx++) {
                int wx = chunkWorldX + lx;
                int cIdx = (lz << 4) | lx;

                double hC = scratchpad.surfaceGrid[cIdx];
                double hN = (lz > 0) ? scratchpad.surfaceGrid[((lz - 1) << 4) | lx] : hC;
                double hS = (lz < 15) ? scratchpad.surfaceGrid[((lz + 1) << 4) | lx] : hC;
                double hW = (lx > 0) ? scratchpad.surfaceGrid[(lz << 4) | (lx - 1)] : hC;
                double hE = (lx < 15) ? scratchpad.surfaceGrid[(lz << 4) | (lx + 1)] : hC;

                double hNW = (lz > 0 && lx > 0) ? scratchpad.surfaceGrid[((lz - 1) << 4) | (lx - 1)] : hC;
                double hNE = (lz > 0 && lx < 15) ? scratchpad.surfaceGrid[((lz - 1) << 4) | (lx + 1)] : hC;
                double hSW = (lz < 15 && lx > 0) ? scratchpad.surfaceGrid[((lz + 1) << 4) | (lx - 1)] : hC;
                double hSE = (lz < 15 && lx < 15) ? scratchpad.surfaceGrid[((lz + 1) << 4) | (lx + 1)] : hC;

                scratchpad.sample.worldX = wx;
                scratchpad.sample.worldZ = wz;
                scratchpad.sample.finalSurface = hC;
                scratchpad.sample.gradMagnitude = Math.sqrt(scratchpad.gradXGrid[cIdx] * scratchpad.gradXGrid[cIdx] + scratchpad.gradZGrid[cIdx] * scratchpad.gradZGrid[cIdx]);
                scratchpad.sample.riverIncision = scratchpad.riverIncisionGrid[cIdx];
                scratchpad.sample.temperature = scratchpad.tempGrid[cIdx];
                scratchpad.sample.humidity = scratchpad.humidGrid[cIdx];
                scratchpad.sample.erosionLowering = scratchpad.erosionGrid[cIdx];
                scratchpad.sample.rawTectonic = scratchpad.macroTectonic[0];

                int bits = landformClassifier.classify(
                    this, scratchpad.sample,
                    hC, hN, hS, hW, hE, hNW, hNE, hSW, hSE, delta
                );
                scratchpad.classificationBitsGrid[cIdx] = bits;
            }
        }
    }

    private static double bilerp(double[] arr, int i00, int i10, int i01, int i11, double fx, double fz) {
        return (1.0 - fx) * (1.0 - fz) * arr[i00]
             + fx * (1.0 - fz) * arr[i10]
             + (1.0 - fx) * fz * arr[i01]
             + fx * fz * arr[i11];
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        int lx = worldX & 15;
        int lz = worldZ & 15;
        int cIdx = (lz << 4) | lx;

        double hf = scratchpad.surfaceGrid[cIdx];
        if (hf == 0.0) {
            hf = evaluatePureH0(worldX, worldZ);
        }

        double gx = scratchpad.gradXGrid[cIdx];
        double gz = scratchpad.gradZGrid[cIdx];
        double slope = Math.sqrt(gx * gx + gz * gz);

        double w = warpField.evaluateWarp(worldX, worldY, worldZ, slope);

        if (profile.hasVoronoiFracture()) {
            if (hf < 0.0) return -64.0f;
            double effectiveY = (double) worldY + w;
            if (effectiveY > hf) return (float) (hf - effectiveY);
            double islandBottom = hf - 36.0;
            if (effectiveY < islandBottom) return (float) (effectiveY - islandBottom);
            return (float) Math.min(hf - effectiveY, effectiveY - islandBottom);
        }

        double c = caveField.evaluateCave(worldX, worldY, worldZ, hf);

        if (profile.hasCoastalProcesses()) {
            c += coastalField.evaluateSeaArchVoid(worldX, worldY, worldZ, hf, slope);
        }

        double d = hf - ((double) worldY + w) - c;
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return (worldY < config.seaLevel()) ? 1.0f : -1.0f;
        }
        return (float) d;
    }

    @Override public CaveField getCaveField() { return caveField; }
    @Override public WarpField getWarpField() { return warpField; }
    @Override public LandformClassifier getLandformClassifier() { return landformClassifier; }
    public DimensionProfile getProfile() { return profile; }
}
