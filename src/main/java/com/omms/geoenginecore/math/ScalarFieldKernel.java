package com.omms.geoenginecore.math;

import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.field.*;
import com.omms.geoenginecore.field.advanced.*;
import com.omms.geoenginecore.geomorphology.LandformClassifier;
import com.omms.geoenginecore.hydrology.*;
import com.omms.geoenginecore.memory.WorkerScratchpad;

/**
 * Scalar reference terrain kernel and the numerical authority of the
 * engine (TECHSPEC §68).
 *
 * <p>Implements the full deterministic pipeline: crustal baseline
 * (tectonics, stress warp, epoch, climate, erosion), volumetric and
 * hydrology fields, advanced geomorphic process modifiers, and
 * landform classification. All fields are constructed eagerly in the
 * constructor so the evaluation hot path performs no construction and
 * no allocation (TECHSPEC §62). Every result is a pure function of the
 * deterministic key (TECHSPEC §8).
 *
 * <p>Optimized kernels such as {@code VectorFieldKernel} must
 * reproduce this kernel's values within the established tolerance
 * (TECHSPEC §71).
 */
public final class ScalarFieldKernel implements FieldKernel {
    /** World seed that roots every deterministic seed domain (TECHSPEC §9). */
    private final long worldSeed;
    /** Validated configuration captured from the dimension profile. */
    private final GeoConfig config;
    /** Dimension profile controlling vertical layout and active process families. */
    private final DimensionProfile profile;

    // Crustal Baseline
    /** Anisotropic coordinate warp that deforms the tectonic domain (TECHSPEC §16). */
    private final StressWarp stressWarp;
    /** Macro-tectonic relief field T built from three frequency bands (TECHSPEC §13-§15). */
    private final TectonicField tectonicField;
    /** Geological age field scaling process strength (TECHSPEC §17). */
    private final EpochField epochField;
    /** Horizontal temperature/humidity field and bounded climate multiplier (TECHSPEC §19-§20). */
    private final ClimateField climateField;
    /** Long-term surface lowering E (TECHSPEC §22). */
    private final ErosionField erosionField;

    // Volumetric & Hydrology
    /** Bounded fluvial incision R (TECHSPEC §28). */
    private final RiverField riverField;
    /** Channel width and profile with configurable initiation threshold (TECHSPEC_AMEND001 A3.1). */
    private final ChannelField channelField;
    /** Discrete realization of channel incision (TECHSPEC_AMEND001 A3.3). */
    private final ChannelRealization channelRealization;
    /** Bank geometry from continuous channel fields (TECHSPEC_AMEND001 A3.7). */
    private final BankField bankField;
    /** Lake topology detection from continuous fields (TECHSPEC_AMEND001 A3.11). */
    private final LakeTopology lakeTopology;
    /** Bounded multi-plate reconnection (TECHSPEC_AMEND001 A3.12). */
    private final ReconnectionField reconnectionField;
    /** Volumetric 3-D rock warp W (TECHSPEC §41-§44). */
    private final WarpField warpField;
    /** Volumetric cave void field C (TECHSPEC §45-§48). */
    private final CaveField caveField;
    /** Coarse deterministic drainage router behind the flow accumulation proxy (TECHSPEC §26-§27). */
    private final DrainageRouter drainageRouter;
    /** First-class hydrology layer: basin identification and confluence detection (TECHSPEC §222, §26). */
    private final HydrologyField hydrologyField;

    // Advanced Geomorphic Process Modifiers
    /** Cryogenic modifier: U-valley floors and cirque bowls (TECHSPEC §118-§119). */
    private final GlacialField glacialField;
    /** Aeolian modifier: dune relief (TECHSPEC §110-§112). */
    private final AeolianField aeolianField;
    /** Karst modifier: sinkholes and tower karst (TECHSPEC §125). */
    private final KarstField karstField;
    /** Coastal modifier: wave-cut platforms and sea arches (TECHSPEC §122-§124). */
    private final CoastalField coastalField;
    /** Fluvial deposition modifier: alluvial fans and delta lobes (TECHSPEC §116-§117). */
    private final AlluvialDeltaField deltaField;
    /** Volcanic modifier: cones, calderas, and lava fields (TECHSPEC §109). */
    private final VolcanicCalderaField volcanicField;
    /** Voronoi fracture field providing regional offsets and cell relief (TECHSPEC §90-§91). */
    private final VoronoiFractureField fractureField;

    // Morphological Classifier
    /** Grammar-based landform classification (TECHSPEC §92-§107). */
    private final LandformClassifier landformClassifier;

    /**
     * Constructs a kernel with the given configuration, assuming the
     * Overworld dimension profile.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
    public ScalarFieldKernel(long worldSeed, GeoConfig config) {
        this(worldSeed, new OverworldProfile(config));
    }

    /**
     * Constructs the kernel for an arbitrary dimension.
     *
     * <p>The profile supplies both the {@link GeoConfig} and the set of
     * active process families. All fields are built eagerly so that
     * the evaluation hot path performs no construction or allocation
     * (TECHSPEC §62).
     *
     * @param worldSeed world seed that roots every seed domain
     * @param profile dimension profile defining vertical layout and process availability
     */
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
        this.channelField = new ChannelField(config);
        this.channelRealization = new ChannelRealization(
            config.stepDeltaY(), config.riverMaxIncision());
        this.bankField = new BankField(
            config.bankWidth(), config.bankSlope(), config.bankNoise(),
            config.bankSteepFactor(), config.containmentBerm());
        this.lakeTopology = new LakeTopology(
            config.lakeMinArea(), config.lakeMaxArea());
        this.reconnectionField = new ReconnectionField(
            config.reconnectRadius(), config.maxReconnectionSamples(),
            config.seaLevelExtension());
        this.warpField = new WarpField(worldSeed, config);
        this.caveField = new CaveField(worldSeed, config);
        this.drainageRouter = new DrainageRouter();
        this.drainageRouter.setDrainageIterations(config.drainageIterations());
        this.drainageRouter.configure(config.gridSpacing(), config.plateScale(),
            config.minRiverAccumulation(), config.meanderStrength(), config.smoothingPasses());
        this.hydrologyField = new HydrologyField();

        this.glacialField = new GlacialField(worldSeed, config);
        this.aeolianField = new AeolianField(worldSeed, config);
        this.karstField = new KarstField(worldSeed, config);
        this.coastalField = new CoastalField(worldSeed, config);
        this.deltaField = new AlluvialDeltaField(worldSeed, config);
        this.volcanicField = new VolcanicCalderaField(worldSeed, config.generatorVersion());
        this.fractureField = new VoronoiFractureField(worldSeed, 144.0);

        this.landformClassifier = new LandformClassifier(config);
    }

    /**
     * Evaluates the pre-carve surface H₀ = T − E (TECHSPEC §23).
     *
     * <p>All factors are queried in the stress-warped domain so that
     * the tectonic structure, age, climate, and erosion stay mutually
     * consistent on the deformed coordinate lattice (TECHSPEC §16, §18).
     *
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @return H₀ elevation of the column after crustal erosion
     */
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

    /**
     * Computes the hydrology-local wetness field (TECHSPEC_AMEND001 A3.16).
     *
     * <p>Wetness is derived from the climate/humidity field and scaled
     * by the wetness configuration parameters. This is separate from
     * the global climate multiplier K and is used for the hydrological
     * source density q in the flow accumulation computation.
     *
     * @param wx world-space X
     * @param wz world-space Z
     * @return hydrology-local wetness value
     */
    public double computeHydrologyWetness(double wx, double wz) {
        double sx = stressWarp.getWarpX(wx, wz);
        double sz = stressWarp.getWarpZ(wx, wz);
        double warpx = wx + sx;
        double warpz = wz + sz;

        double temp = climateField.evaluateTemperature(warpx, warpz);
        double humid = climateField.evaluateHumidity(warpx, warpz);

        // Use humidity as the basis for wetness
        // Interpolate between wetnessDryCutoff (0.0) and wetnessWetReference (1.0)
        double wetness = (humid - config.wetnessDryCutoff()) / (config.wetnessWetReference() - config.wetnessDryCutoff());
        wetness = Math.max(0.0, Math.min(1.0, wetness));

        // Apply wetness multiplier
        return wetness * config.wetnessMultiplier();
    }

    /**
     * Applies the additive pre-fluvial process modifiers to an
     * elevation (the stage between H₀ and H* in TECHSPEC §24).
     *
     * <p>Modifiers are gated by the dimension profile and layered in a
     * fixed order: Voronoi fracture replacement, glacial valleys and
     * cirques, karst relief, aeolian dunes, and volcanic relief. Only
     * the fracture path replaces the input elevation; all other
     * modifiers offset it. Volcanic relief is applied only above
     * 180 blocks, where volcanic constructs are plausible.
     *
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @param h0 baseline elevation to modify
     * @param temp temperature driving process intensity
     * @param humid humidity driving process intensity
     * @param slope local gradient magnitude used by slope-sensitive modifiers
     * @return modified pre-fluvial surface elevation
     */
    public double evaluatePreFluvialSurface(double wx, double wz, double h0, double temp, double humid, double slope) {
        double hPre = h0;

        if (profile.hasVoronoiFracture()) {
            VoronoiFractureField.FractureSample sample = new VoronoiFractureField.FractureSample();
            fractureField.sample(wx, wz, sample);
            double drop = (sample.distanceToEdge < 14.0) ? (38.0 * (1.0 - Math.pow(sample.distanceToEdge / 14.0, 2))) : 0.0;
            return sample.cellElevation - drop;
        }

        if (profile.hasGlacialProcesses()) {
            double gInt = GeoMath.clamp((h0 - (config.seaLevel() + 200.0)) / 150.0, 0.0, 1.0);
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

    /**
     * Evaluates the deterministic flow accumulation proxy A_f at a
     * column (TECHSPEC §27).
     *
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @return accumulated upstream drainage contribution
     */
    public double evaluateFullFlowAccumulation(double wx, double wz) {
        return drainageRouter.computeAccumulationProxy(this, worldSeed, config.configHash(), wx, wz);
    }

    /**
     * Runs the complete 2-D column pipeline (TECHSPEC §24).
     *
     * <p>Stages: stress warp → age/climate → tectonic → erosion →
     * H₀ = T − E → central-difference derivatives on H₀ → pre-fluvial
     * modifiers H_pre → fluvial incision R → H* = H_pre − R →
     * deposition S → H_f = H* + S → coastal finishing → 9-point
     * Hessian curvature stencil → landform classification. The
     * construction is deliberately single-pass and acyclic; any
     * future H* feedback must use a bounded iterative solver
     * (TECHSPEC §24).
     *
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @param sample reusable sample struct overwritten in full by this pass
     */
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

        // Reduce erosion in channel areas (Phase 9 Sprint H8)
        // Channels protect underlying terrain from erosion
        // Note: channelFactor is not yet computed; use flowAcc as proxy
        if (profile.hasFluvialHydrology()) {
            double flowAcc = evaluateFullFlowAccumulation(wx, wz);
            if (flowAcc > channelField.getMinAccumulation()) {
                // Channel present: reduce erosion slightly
                // (not too much to avoid exceeding incision budget)
                sample.erosionLowering *= 0.95;
            }
        }

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

        // Fluvial Incision (TECHSPEC §28): R = F(A_f)·F_slope·F_climate·F_channel,
        // clamped to R_max.
        double flowAcc = 0.0;
        double incision = 0.0;
        long basinId = 0L;
        long confluenceId = 0L;
        double channelFactor = 1.0;
        if (profile.hasFluvialHydrology()) {
            int regionX = (int) Math.floor(wx / (double) DrainageRouter.REGION_SPAN);
            int regionZ = (int) Math.floor(wz / (double) DrainageRouter.REGION_SPAN);
            DrainageGraph graph = drainageRouter.resolveGraph(this, worldSeed, config.configHash(), regionX, regionZ);
            // One-shot topology analysis (basin labels + confluence IDs);
            // a no-op once this shared graph has been analyzed.
            hydrologyField.analyze(graph, worldSeed, config.configHash(),
                config.dimensionId(), config.generatorVersion(), regionX, regionZ);
            flowAcc = evaluateFullFlowAccumulation(wx, wz);
            int routedCell = drainageRouter.cellIndexFor(graph, regionX, regionZ, wx, wz);
            // Compute channel order from flow accumulation (needed for meandered channel factor)
            if (flowAcc < channelField.getMinAccumulation()) {
                sample.channelOrder = 0; // Overland
            } else if (flowAcc < 30.0) {
                sample.channelOrder = 1; // Creek / Stream
            } else if (flowAcc < 80.0) {
                sample.channelOrder = 2; // Feeder / Tributary
            } else if (flowAcc < 240.0) {
                sample.channelOrder = 3; // River (trunk)
            } else {
                sample.channelOrder = 4; // Arterial
            }
            // §80: stable scoped basin / confluence identities (needed for meandered channel factor)
            basinId = hydrologyField.basinIdForCell(graph, routedCell);
            confluenceId = hydrologyField.confluenceIdForCell(graph, routedCell);
            // Use meandered channel factor for consistency with chunk path (H5 fix)
            channelFactor = drainageRouter.evaluateChannelFactorMeandered(graph, routedCell,
                flowAcc, sample.channelOrder, sample.gradMagnitude, wx, wz, worldSeed, basinId);
            incision = riverField.computeIncision(flowAcc, sample.gradMagnitude, sample.climateMultiplier, channelFactor);
        }
        sample.flowAccumulation = flowAcc;
        sample.basinId = basinId;
        sample.confluenceId = confluenceId;
        sample.riverIncision = incision;
        sample.channelFactor = (float) channelFactor;

        // Channel half-width (used by adapter for corridor tests)
        sample.channelHalfWidth = (float) channelField.getWidth(flowAcc);

        // Feature grammar (Phase 9 spec §31)
        // Simplified: detect confluence from graph, compute other features from local properties
        boolean atConfluence = sample.confluenceId != 0L;
        // Note: parallelFlow, atDelta, atAlluvialFan require graph topology queries
        // that are not available in the per-column evaluation path. These are
        // computed in the region-scoped feature raster (future work).
        int featureMask = com.omms.geoenginecore.hydrology.FeatureGrammar.computeFeatureMask(
            sample.channelOrder, flowAcc, sample.gradMagnitude,
            sample.laplacian, 0.0, sample.channelHalfWidth,
            atConfluence, false, false, false, 0.0);
        sample.featureMask = (byte) featureMask;

        sample.hPre = hPre;
        double hStar = hPre - incision;

        double deposition = 0.0;
        if (profile.hasFluvialHydrology()) {
            // Mass-budget cascade (TECHSPEC §32, §33): every deposition
            // component draws from the remaining budget E_total = E + R,
            // so the total never exceeds the removal budget.
            double eTotal = sample.erosionLowering + incision;
            double residual = eTotal;

            // Scale channel factor by channel order: higher order = larger channel
            // = more transport capacity = less deposition. Matches chunk path.
            double orderScale = 1.0 - 0.05 * sample.channelOrder;
            double channelFactorForDeposition = channelFactor * orderScale;
            double sFluvial = Math.min(
                DepositionField.computeDeposition(
                    sample.erosionLowering, incision, sample.gradMagnitude,
                    sample.laplacian, hStar - config.seaLevel(), sample.age, channelFactorForDeposition
                ),
                residual
            );
            residual -= sFluvial;

            double sFan = Math.min(
                deltaField.evaluateAlluvialFan(flowAcc, sample.gradMagnitude, sample.laplacian, eTotal),
                residual
            );
            residual -= sFan;

            deposition = sFluvial + sFan
                + Math.min(deltaField.evaluateDeltaLobe(wx, wz, hStar, flowAcc, incision), residual);
        }
        sample.deposition = deposition;

        double hFinal = hStar + deposition;
        if (profile.hasCoastalProcesses()) {
            hFinal += coastalField.evaluateWaveCutPlatform(hFinal);
        }
        sample.finalSurface = hFinal;

        // Water surface level computation (Phase 9 spec §6.1)
        // Water depth scales with flow accumulation, clamped to bed elevation
        // Computed before asymmetry adjustment (consistent with chunk path)
        if (sample.channelOrder == 0 || hFinal <= config.seaLevel()) {
            sample.waterSurfaceLevel = 0; // No channel water (below sea level or not a channel)
        } else {
            // Water depth proportional to flow, capped at incision depth.
            // Exponential saturation matches RiverField flow scaling.
            double flowStrength = 1.0 - Math.exp(-(sample.flowAccumulation - 1.8) * 0.15);
            double waterDepth = flowStrength * Math.min(sample.riverIncision, 4.0);
            // Water surface at bed elevation (no freeboard above terrain)
            sample.waterSurfaceLevel = (int) Math.round(hFinal - waterDepth);
        }

        // Cross-section asymmetry (Phase 9 Sprint R5)
        // Point bar: sediment deposition on inner bend (+0.5 blocks)
        // Cut bank: erosion on outer bend (-0.5 blocks)
        if ((sample.featureMask & com.omms.geoenginecore.hydrology.FeatureGrammar.F_POINT_BAR) != 0) {
            sample.finalSurface = hFinal + 0.5;
        } else if ((sample.featureMask & com.omms.geoenginecore.hydrology.FeatureGrammar.F_CUT_BANK) != 0) {
            sample.finalSurface = hFinal - 0.5;
        } else {
            sample.finalSurface = hFinal;
        }

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

    /**
     * Convenience wrapper that evaluates a full column and returns only
     * the final surface elevation.
     *
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @param sample reusable sample struct overwritten by the evaluation
     * @return H_f elevation of the column
     */
    public double evaluateH0(double wx, double wz, GeoSample sample) {
        evaluateFullColumn(wx, wz, sample);
        return sample.finalSurface;
    }

    /**
     * Fills the 6×6 world-space macro node arrays (TECHSPEC §38-§40).
     *
     * <p>Nodes are spaced 4 blocks apart starting 4 blocks before the
     * chunk origin, so the grid covers the 16×16 chunk plus one halo
     * ring. Each node stores the interpolated H₀ baseline, the raw
     * tectonic field, and the age, temperature, humidity, and erosion
     * factors. The coarse grid cuts macro sample count by roughly 86%
     * relative to per-column evaluation (TECHSPEC §40).
     *
     * @param scratchpad worker-owned scratchpad that receives the macro node values
     * @param chunkWorldX world-coordinate X of the chunk origin
     * @param chunkWorldZ world-coordinate Z of the chunk origin
     */
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

    /**
     * Rasterizes the 16×16 chunk surface from the macro grid.
     *
     * <p>Evaluates the macro grid, bilinearly interpolates the macro
     * nodes onto the per-column chunk lattice (one chunk column per
     * 4 macro blocks), then hands off to
     * {@link #finishSurfaceProcessing} for the remaining passes.
     * Interpolation is deterministic and allocation-free (TECHSPEC §39).
     *
     * @param scratchpad worker-owned scratchpad that receives all chunk grids
     * @param chunkWorldX world-coordinate X of the chunk origin
     * @param chunkWorldZ world-coordinate Z of the chunk origin
     */
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

                double h0 = bilerp(scratchpad.macroH0, idx00, idx10, idx01, idx11, fx, fz);
                scratchpad.surfaceGrid[cIdx] = h0;
                scratchpad.h0Grid[cIdx] = h0;
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

    /**
     * Completes the chunk surface pipeline after macro-grid
     * interpolation.
     *
     * <p>Pass 1 applies the pre-fluvial modifiers per column. Pass 2
     * computes central-difference gradients and the Laplacian, falling
     * back to direct world-space evaluation for neighbors across chunk
     * boundaries (TECHSPEC §76). Pass 3 applies fluvial incision,
     * deposition, and coastal finishing to reach H_f. Pass 4
     * classifies every column using the 9-point Hessian curvature
     * stencil, again substituting the column itself when a neighbor is
     * outside the chunk (TECHSPEC §100).
     *
     * @param scratchpad worker-owned scratchpad holding the interpolated grids
     * @param chunkWorldX world-coordinate X of the chunk origin
     * @param chunkWorldZ world-coordinate Z of the chunk origin
     */
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

        // 2. Gradients and Laplacian on H_pre
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
                double channelFactor = 1.0;
                int order = 0;
                long basinId = 0L;
                long confluenceId = 0L;

                if (profile.hasFluvialHydrology()) {
                    int regionX = (int) Math.floor(wx / (double) DrainageRouter.REGION_SPAN);
                    int regionZ = (int) Math.floor(wz / (double) DrainageRouter.REGION_SPAN);
                    DrainageGraph graph = drainageRouter.resolveGraph(this, worldSeed, config.configHash(), regionX, regionZ);
                    // One-shot topology analysis (basin labels + confluence
                    // IDs); a no-op once this shared graph is analyzed.
                    hydrologyField.analyze(graph, worldSeed, config.configHash(),
                        config.dimensionId(), config.generatorVersion(), regionX, regionZ);
                    flowAcc = evaluateFullFlowAccumulation(wx, wz);
                    int routedCell = drainageRouter.cellIndexFor(graph, regionX, regionZ, wx, wz);
                    // §80: stable scoped basin / confluence identities.
                    basinId = hydrologyField.basinIdForCell(graph, routedCell);
                    confluenceId = hydrologyField.confluenceIdForCell(graph, routedCell);

                    // Channel order classification (Phase 9)
                    // Raw flow accumulation values — thresholds aligned with
                    // ChannelField.CHANNEL_INITIATION_FLOW (14.0) and RiverDebugSampler.
                    if (flowAcc < 14.0) {
                        order = 0;
                    } else if (flowAcc < 30.0) {
                        order = 1;
                    } else if (flowAcc < 80.0) {
                        order = 2;
                    } else if (flowAcc < 240.0) {
                        order = 3;
                    } else {
                        order = 4;
                    }

                    // §28: R = F(A_f)·F_slope·F_climate·F_channel.
                    // Use meandered channel factor for lateral displacement (§30).
                    channelFactor = drainageRouter.evaluateChannelFactorMeandered(graph, routedCell,
                        flowAcc, order, slope, wx, wz, worldSeed, basinId);
                    incision = riverField.computeIncision(flowAcc, slope, climateMult, channelFactor);
                }

                double hStar = hPre - incision;

                double deposition = 0.0;
                if (profile.hasFluvialHydrology()) {
                    // Mass-budget cascade (TECHSPEC §32, §33): every
                    // deposition component draws from the remaining
                    // budget E_total = E + R, so the total never exceeds
                    // the removal budget.
                    double eTotal = localErosion + incision;
                    double residual = eTotal;

                    // Scale channel factor by channel order: higher order = larger channel
                    // = more transport capacity = less deposition. Channel order is stable
                    // across chunk boundaries, so this doesn't introduce seam discontinuities.
                    double orderScale = 1.0 - 0.05 * order; // 1.0, 0.95, 0.9, 0.85, 0.8 for orders 0-4
                    double channelFactorForDeposition = channelFactor * orderScale;
                    double sFluvial = Math.min(
                        DepositionField.computeDeposition(localErosion, incision, slope, lap, hStar - config.seaLevel(), localAge, channelFactorForDeposition),
                        residual);
                    residual -= sFluvial;

                    double sFan = Math.min(deltaField.evaluateAlluvialFan(flowAcc, slope, lap, eTotal), residual);
                    residual -= sFan;

                    deposition = sFluvial + sFan
                        + Math.min(deltaField.evaluateDeltaLobe(wx, wz, hStar, flowAcc, incision), residual);
                }

                double hFinal = hStar + deposition;
                if (profile.hasCoastalProcesses()) {
                    hFinal += coastalField.evaluateWaveCutPlatform(hFinal);
                }

                scratchpad.flowAccGrid[cIdx] = flowAcc;
                scratchpad.riverIncisionGrid[cIdx] = incision;
                scratchpad.basinIdGrid[cIdx] = basinId;
                scratchpad.confluenceIdGrid[cIdx] = confluenceId;
                scratchpad.hPreGrid[cIdx] = hPre;
                scratchpad.depositionGrid[cIdx] = deposition;
                scratchpad.surfaceGrid[cIdx] = hFinal;

                scratchpad.channelOrderGrid[cIdx] = (byte) order;

                // Water surface level (Phase 9 spec §6.1)
                // Flow-based water depth, clamped to bed elevation
                if (order == 0 || hFinal <= config.seaLevel()) {
                    scratchpad.waterSurfaceGrid[cIdx] = 0;
                } else {
                    // Water depth proportional to flow, capped at incision depth.
                    // Exponential saturation matches RiverField flow scaling.
                    double flowStrength = 1.0 - Math.exp(-(flowAcc - 1.8) * 0.15);
                    // Use actual incision value, not max incision * channelFactor
                    double waterDepth = flowStrength * Math.min(incision, 4.0);
                    // Water surface at bed elevation (no freeboard above terrain)
                    scratchpad.waterSurfaceGrid[cIdx] = (int) Math.round(hFinal - waterDepth);
                }
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
                scratchpad.sample.hPre = scratchpad.hPreGrid[cIdx];
                scratchpad.sample.temperature = scratchpad.tempGrid[cIdx];
                scratchpad.sample.humidity = scratchpad.humidGrid[cIdx];
                scratchpad.sample.erosionLowering = scratchpad.erosionGrid[cIdx];
                scratchpad.sample.rawTectonic = scratchpad.macroTectonic[0];
                scratchpad.sample.distanceToThalweg = scratchpad.distanceToThalwegGrid[cIdx];
                scratchpad.sample.featureMask = scratchpad.featureMaskGrid[cIdx];

                // Compute signed distance to thalweg for bank asymmetry (Sprint C)
                int order = scratchpad.channelOrderGrid[cIdx];
                if (order > 0) {
                    int regionX = (int) Math.floor(wx / (double) DrainageRouter.REGION_SPAN);
                    int regionZ = (int) Math.floor(wz / (double) DrainageRouter.REGION_SPAN);
                    DrainageGraph graph = drainageRouter.resolveGraph(this, worldSeed, config.configHash(), regionX, regionZ);
                    int routedCell = drainageRouter.cellIndexFor(graph, regionX, regionZ, wx, wz);
                    scratchpad.distanceToThalwegGrid[cIdx] = (float) drainageRouter.signedDistanceToThalweg(
                        graph, routedCell, scratchpad.flowAccGrid[cIdx], order,
                        scratchpad.sample.gradMagnitude, wx, wz, worldSeed, scratchpad.basinIdGrid[cIdx]);

                    // Compute feature mask (TECHSPEC §31)
                    scratchpad.featureMaskGrid[cIdx] = (byte) FeatureGrammar.computeFeatureMask(
                        order, scratchpad.flowAccGrid[cIdx], scratchpad.sample.gradMagnitude,
                        scratchpad.laplacianGrid[cIdx], scratchpad.distanceToThalwegGrid[cIdx],
                        channelField.getWidth(scratchpad.flowAccGrid[cIdx]) * 0.5,
                        scratchpad.confluenceIdGrid[cIdx] != 0L, false, false, false, 0.0);

                    // Cross-section asymmetry (Phase 9 Sprint R5)
                    // Point bar: sediment deposition on inner bend (+0.5 blocks)
                    // Cut bank: erosion on outer bend (-0.5 blocks)
                    if ((scratchpad.featureMaskGrid[cIdx] & FeatureGrammar.F_POINT_BAR) != 0) {
                        scratchpad.sample.finalSurface = hC + 0.5;
                    } else if ((scratchpad.featureMaskGrid[cIdx] & FeatureGrammar.F_CUT_BANK) != 0) {
                        scratchpad.sample.finalSurface = hC - 0.5;
                    }
                }

                int bits = landformClassifier.classify(
                    this, scratchpad.sample,
                    hC, hN, hS, hW, hE, hNW, hNE, hSW, hSE, delta
                );
                scratchpad.classificationBitsGrid[cIdx] = bits;
            }
        }
    }

    /**
     * Bilinearly interpolates the four macro corners of a 4-block cell.
     *
     * @param arr macro node array
     * @param i00 index of the cell's (0, 0) corner
     * @param i10 index of the cell's (1, 0) corner
     * @param i01 index of the cell's (0, 1) corner
     * @param i11 index of the cell's (1, 1) corner
     * @param fx fractional X offset within the cell, in [0, 1]
     * @param fz fractional Z offset within the cell, in [0, 1]
     * @return interpolated value
     */
    private static double bilerp(double[] arr, int i00, int i10, int i01, int i11, double fx, double fz) {
        return (1.0 - fx) * (1.0 - fz) * arr[i00]
             + fx * (1.0 - fz) * arr[i10]
             + (1.0 - fx) * fz * arr[i01]
             + fx * fz * arr[i11];
    }

    /**
     * Evaluates the canonical terrain density D = H_f − (y + W) − C for
     * one voxel (TECHSPEC §41, §49).
     *
     * <p>Positive return means solid, non-positive means air. The warp
     * shifts the effective vertical coordinate; the cave field and sea
     * arch voids subtract additional material. Degenerate NaN/Infinity
     * results are resolved by the sea-level rule. With W = 0 and C = 0
     * the result reduces to H_f − y, preserving the density
     * monotonicity invariant ∂D/∂y = −1 (TECHSPEC §50).
     *
     * @param scratchpad worker-owned scratchpad holding the chunk surface grid
     * @param worldX absolute world X of the voxel
     * @param worldY absolute world Y of the voxel
     * @param worldZ absolute world Z of the voxel
     * @return terrain density for the voxel
     */
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

    /**
     * @return the volumetric cave field owned by this kernel
     */
    @Override public CaveField getCaveField() { return caveField; }

    /**
     * @return the volumetric rock warp owned by this kernel
     */
    @Override public WarpField getWarpField() { return warpField; }

    /**
     * @return the landform classifier owned by this kernel
     */
    @Override public LandformClassifier getLandformClassifier() { return landformClassifier; }

    /**
     * @return the dimension profile this kernel was constructed with
     */
    public DimensionProfile getProfile() { return profile; }

    /**
     * @return the region-based hydrology router, for conformance probes
     */
    public DrainageRouter getDrainageRouter() { return drainageRouter; }

    /**
     * @return the hydrology layer (basin identification, confluences)
     */
    public HydrologyField getHydrologyField() { return hydrologyField; }
}
