package com.omms.geoengineforge.generator;

import com.omms.geoenginecore.climate.ClimateClassifier;
import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoengineforge.integration.GeoDimensionProfile;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.*;

import java.util.stream.Stream;

public final class GeoBiomeSource extends BiomeSource {
    public static final MapCodec<GeoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter((GeoBiomeSource s) -> s.worldSeed),
            Codec.INT.optionalFieldOf("dimension_id", 0).forGetter((GeoBiomeSource s) -> s.dimensionId),
            RegistryOps.<Biome, GeoBiomeSource>retrieveGetter(Registries.BIOME)
        ).apply(instance, (Long seed, Integer dimId, HolderGetter<Biome> biomes) -> new GeoBiomeSource(seed, dimId, biomes))
    );

    private long worldSeed;
    private final int dimensionId;
    private final HolderGetter<Biome> biomeGetter;
    private final DimensionProfile profile;
    private final GeoConfig config;
    private ScalarFieldKernel kernel;
    private final ClimateClassifier climateClassifier;

    // Multi-noise parameter trees
    private final Climate.ParameterList<Holder<Biome>> overworldParameters;
    private final Climate.ParameterList<Holder<Biome>> netherParameters;

    // The End Biomes
    private final Holder<Biome> theEnd;
    private final Holder<Biome> endHighlands;
    private final Holder<Biome> endMidlands;
    private final Holder<Biome> smallEndIslands;
    private final Holder<Biome> endBarrens;

    public GeoBiomeSource(long worldSeed, HolderGetter<Biome> biomes) {
        this(worldSeed, 0, biomes);
    }

    public GeoBiomeSource(long worldSeed, int dimensionId, HolderGetter<Biome> biomes) {
        this.worldSeed = worldSeed;
        this.dimensionId = dimensionId;
        this.biomeGetter = biomes;
        this.profile = GeoDimensionProfile.getProfileFor(dimensionId, 1);
        this.config = this.profile.getConfig();
        this.climateClassifier = new ClimateClassifier(config);
        reseed(worldSeed);

        // Vanilla Overworld biomes (Plains, Forests, Oceans, Peaks, Caves)
        MultiNoiseBiomeSourceParameterList overworldList = new MultiNoiseBiomeSourceParameterList(
            MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, biomes
        );
        this.overworldParameters = overworldList.parameters();

        // Vanilla Nether biomes (Crimson, Warped, Soul Sand, Basalt Deltas, Wastes)
        MultiNoiseBiomeSourceParameterList netherList = new MultiNoiseBiomeSourceParameterList(
            MultiNoiseBiomeSourceParameterList.Preset.NETHER, biomes
        );
        this.netherParameters = netherList.parameters();

        // End Biomes
        this.theEnd = biomes.getOrThrow(Biomes.THE_END);
        this.endHighlands = biomes.getOrThrow(Biomes.END_HIGHLANDS);
        this.endMidlands = biomes.getOrThrow(Biomes.END_MIDLANDS);
        this.smallEndIslands = biomes.getOrThrow(Biomes.SMALL_END_ISLANDS);
        this.endBarrens = biomes.getOrThrow(Biomes.END_BARRENS);
    }

    public synchronized void reseed(long seed) {
        this.worldSeed = seed;
        this.kernel = new ScalarFieldKernel(seed, this.profile);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        if (dimensionId == 1) {
            return netherParameters.values().stream().map(Pair::getSecond);
        }
        if (dimensionId == 2) {
            return Stream.of(theEnd, endHighlands, endMidlands, smallEndIslands, endBarrens);
        }
        return overworldParameters.values().stream().map(Pair::getSecond);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int worldX = quartX << 2;
        int worldY = quartY << 2;
        int worldZ = quartZ << 2;

        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(worldX, worldZ, sp.sample);

        // --- The End (dimensionId == 2) ---
        if (dimensionId == 2) {
            int chunkX = quartX >> 2;
            int chunkZ = quartZ >> 2;
            long chunkOriginX = (long) chunkX << 4;
            long chunkOriginZ = (long) chunkZ << 4;
            long distSq = (chunkOriginX * chunkOriginX) + (chunkOriginZ * chunkOriginZ);

            if (distSq < 1024L * 1024L) return theEnd;

            float endErosion = computeEndErosion(sp.sample);
            if (endErosion > 0.45f) return endHighlands;
            if (endErosion > 0.10f) return endMidlands;
            if (endErosion > -0.30f) return endBarrens;
            return smallEndIslands;
        }

        // --- The Nether (dimensionId == 1) ---
        if (dimensionId == 1) {
            float temperature = (float) Math.clamp((sp.sample.temperature * 2.0) - 1.0, -1.0, 1.0);
            float humidity = (float) Math.clamp((sp.sample.humidity * 2.0) - 1.0, -1.0, 1.0);

            // Sits strictly on the T-H plane (C=0, E=0, D=0, W=0, offset=0)
            Climate.TargetPoint netherTarget = new Climate.TargetPoint(
                Climate.quantizeCoord(temperature),
                Climate.quantizeCoord(humidity),
                0L, 0L, 0L, 0L
            );
            return netherParameters.findValue(netherTarget);
        }

        // --- The Overworld (dimensionId == 0) ---
        Climate.TargetPoint overworldTarget = convertToOverworldClimate(sp.sample, worldY);
        return overworldParameters.findValue(overworldTarget);
    }

    private float computeEndErosion(GeoSample sample) {
        double surfaceH = sample.finalSurface;
        if (surfaceH < 0.0) return -0.60f;
        if (surfaceH <= 52.0) return -0.15f;
        if (surfaceH <= 65.0) return 0.25f;
        return 0.75f;
    }

    private Climate.TargetPoint convertToOverworldClimate(GeoSample sample, int worldY) {
        double effTemp = climateClassifier.getEffectiveTemperature(sample.temperature, worldY);
        float temperature = (float) Math.clamp((effTemp * 2.0) - 1.0, -1.0, 1.0);
        float humidity = (float) Math.clamp((sample.humidity * 2.0) - 1.0, -1.0, 1.0);
        float continentalness = computeContinentalness(sample.finalSurface - (double) config.seaLevel());
        float erosion = computeErosion(sample.finalSurface, sample.gradMagnitude, sample.riverIncision);

        double depthBlocks = sample.finalSurface - (double) worldY;
        float depth = (float) Math.max(0.0, depthBlocks * 0.0078125);
        float weirdness = computeWeirdness(sample);

        return new Climate.TargetPoint(
            Climate.quantizeCoord(temperature),
            Climate.quantizeCoord(humidity),
            Climate.quantizeCoord(continentalness),
            Climate.quantizeCoord(erosion),
            Climate.quantizeCoord(depth),
            Climate.quantizeCoord(weirdness)
        );
    }

    private float computeContinentalness(double deltaH) {
        if (deltaH < -32.0) {
            double t = Math.clamp((deltaH - (-64.0)) / 32.0, 0.0, 1.0);
            return (float) (-1.05 + t * (-0.455 - -1.05));
        } else if (deltaH < -2.0) {
            double t = (deltaH - (-32.0)) / 30.0;
            return (float) (-0.455 + t * (-0.19 - -0.455));
        } else if (deltaH <= 4.0) {
            double t = (deltaH - (-2.0)) / 6.0;
            return (float) (-0.19 + t * (-0.11 - -0.19));
        } else if (deltaH <= 25.0) {
            double t = (deltaH - 4.0) / 21.0;
            return (float) (-0.11 + t * (0.03 - -0.11));
        } else if (deltaH <= 90.0) {
            double t = (deltaH - 25.0) / 65.0;
            return (float) (0.03 + t * (0.30 - 0.03));
        } else {
            double t = Math.clamp((deltaH - 90.0) / 350.0, 0.0, 1.0);
            return (float) (0.30 + t * 0.70);
        }
    }

    /**
     * Calibrated Erosion mapping:
     * - Lowland plains (Hf <= 95m, slope < 0.25) -> High erosion (+0.25 ~ +0.55, Plains/Forests)
     * - Foothills & mid-relief (Hf 95-180m) -> Moderate erosion (-0.20 ~ +0.10, Hills/Meadows)
     * - High mountain peaks (Hf > 220m, steep slopes) -> Low erosion (-0.85 ~ -0.40, Jagged Peaks/Snowy Slopes)
     */
    private float computeErosion(double hf, double slope, double incision) {
        double seaLevel = config.seaLevel();
        double heightAboveSea = Math.max(0.0, hf - seaLevel);

        // Elevation factor: 0.0 at sea level -> 1.0 at Y=350+
        double elevationFactor = Math.clamp(heightAboveSea / 280.0, 0.0, 1.0);
        double slopeFactor = Math.clamp(slope / 0.85, 0.0, 1.0);

        // Lowlands are heavily eroded (+0.40); alpine peaks are jagged (-0.80)
        float baseErosion = (float) (0.45 - (1.25 * Math.pow(Math.max(elevationFactor, slopeFactor), 0.75)));

        if (incision > 4.0) {
            baseErosion = Math.clamp(baseErosion - 0.20f, -1.0f, 1.0f);
        }
        return Math.clamp(baseErosion, -1.0f, 1.0f);
    }

    private float computeWeirdness(GeoSample sample) {
        if (sample.riverIncision > 6.0) return -0.67f;
        if (sample.finalSurface > (double) (config.seaLevel() + 240)) return 0.0f;
        return (float) Math.clamp(sample.stressWarpX / 32.0, -1.0, 1.0);
    }
}
