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

/**
 * GeoEngine biome source: assigns biomes from the deterministic
 * pipeline instead of vanilla multi-noise alone (TECHSPEC §205-§207).
 *
 * <p>Overworld biomes come from vanilla multi-noise parameters whose
 * six climate axes are driven by pipeline fields (temperature,
 * humidity, continentalness, erosion, depth, weirdness), so biome
 * borders follow real geomorphic structure; incised river channels
 * receive River/Frozen River directly. Nether and End dimensions
 * use their standard parameters.
 */
public final class GeoBiomeSource extends BiomeSource {
    /** Serialization codec: (seed, dimension id, biome registry). */
    public static final MapCodec<GeoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter((GeoBiomeSource s) -> s.worldSeed),
            Codec.INT.optionalFieldOf("dimension_id", 0).forGetter((GeoBiomeSource s) -> s.dimensionId),
            RegistryOps.<Biome, GeoBiomeSource>retrieveGetter(Registries.BIOME)
        ).apply(instance, (Long seed, Integer dimId, HolderGetter<Biome> biomes) -> new GeoBiomeSource(seed, dimId, biomes))
    );

    /** World seed that roots every seed domain (reseedable). */
    private long worldSeed;
    /** Dimension id: 0 overworld, 1 nether, 2 end. */
    private final int dimensionId;
    /** Biome registry access. */
    private final HolderGetter<Biome> biomeGetter;
    /** Validated dimension profile. */
    private final DimensionProfile profile;
    /** Validated configuration. */
    private final GeoConfig config;
    /** Scalar field kernel (re-seeded with the world). */
    private ScalarFieldKernel kernel;
    /** Effective-temperature and zone classifier. */
    private final ClimateClassifier climateClassifier;

    // Multi-noise parameter trees
    /** Vanilla overworld multi-noise biome parameters. */
    private final Climate.ParameterList<Holder<Biome>> overworldParameters;
    /** Vanilla nether multi-noise biome parameters. */
    private final Climate.ParameterList<Holder<Biome>> netherParameters;

    // Overworld Biome Holders
    /** River biome holder. */
    private final Holder<Biome> river;
    /** Frozen river biome holder. */
    private final Holder<Biome> frozenRiver;

    // The End Biome Holders
    /** End biome holder. */
    private final Holder<Biome> theEnd;
    /** End highlands biome holder. */
    private final Holder<Biome> endHighlands;
    /** End midlands biome holder. */
    private final Holder<Biome> endMidlands;
    /** Small End islands biome holder. */
    private final Holder<Biome> smallEndIslands;
    /** End barrens biome holder. */
    private final Holder<Biome> endBarrens;

    /**
     * Overworld convenience constructor.
     *
     * @param worldSeed world seed
     * @param biomes biome registry access
     */
    public GeoBiomeSource(long worldSeed, HolderGetter<Biome> biomes) {
        this(worldSeed, 0, biomes);
    }

    /**
     * @param worldSeed world seed
     * @param dimensionId dimension id: 0 overworld, 1 nether, 2 end
     * @param biomes biome registry access
     */
    public GeoBiomeSource(long worldSeed, int dimensionId, HolderGetter<Biome> biomes) {
        this.worldSeed = worldSeed;
        this.dimensionId = dimensionId;
        this.biomeGetter = biomes;
        this.profile = GeoDimensionProfile.getProfileFor(dimensionId, 1);
        this.config = this.profile.getConfig();
        this.climateClassifier = new ClimateClassifier(config);
        reseed(worldSeed);

        // Vanilla Overworld multi-noise parameter list (includes all ~55 biomes, oceans, caves)
        MultiNoiseBiomeSourceParameterList overworldList = new MultiNoiseBiomeSourceParameterList(
            MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, biomes
        );
        this.overworldParameters = overworldList.parameters();

        // Vanilla Nether multi-noise parameter list
        MultiNoiseBiomeSourceParameterList netherList = new MultiNoiseBiomeSourceParameterList(
            MultiNoiseBiomeSourceParameterList.Preset.NETHER, biomes
        );
        this.netherParameters = netherList.parameters();

        // River Biomes
        this.river = biomes.getOrThrow(Biomes.RIVER);
        this.frozenRiver = biomes.getOrThrow(Biomes.FROZEN_RIVER);

        // The End Biomes
        this.theEnd = biomes.getOrThrow(Biomes.THE_END);
        this.endHighlands = biomes.getOrThrow(Biomes.END_HIGHLANDS);
        this.endMidlands = biomes.getOrThrow(Biomes.END_MIDLANDS);
        this.smallEndIslands = biomes.getOrThrow(Biomes.SMALL_END_ISLANDS);
        this.endBarrens = biomes.getOrThrow(Biomes.END_BARRENS);
    }

    /**
     * Re-roots the field kernel after a world seed change
     * (TECHSPEC §67).
     *
     * @param seed new world seed
     */
    public synchronized void reseed(long seed) {
        this.worldSeed = seed;
        this.kernel = new ScalarFieldKernel(seed, this.profile);
    }

    /**
     * @return this source's codec
     */
    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    /**
     * @return biomes this source may produce for its dimension
     */
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        if (dimensionId == 1) {
            return netherParameters.values().stream().map(Pair::getSecond);
        }
        if (dimensionId == 2) {
            return Stream.of(theEnd, endHighlands, endMidlands, smallEndIslands, endBarrens);
        }
        return Stream.concat(
            overworldParameters.values().stream().map(Pair::getSecond),
            Stream.of(river, frozenRiver)
        );
    }

    /**
     * Resolves the biome for one climate query (TECHSPEC §205-§207).
     *
     * @param quartX query X in 4-block climate quarts
     * @param quartY query Y in 4-block climate quarts
     * @param quartZ query Z in 4-block climate quarts
     * @param sampler vanilla climate sampler (unused by the pipeline)
     * @return biome holder for the queried position
     */
    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int worldX = quartX << 2;
        int worldY = quartY << 2;
        int worldZ = quartZ << 2;

        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(worldX, worldZ, sp.sample);

        // 1. The End
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

        // 2. The Nether
        if (dimensionId == 1) {
            float temperature = (float) Math.clamp((sp.sample.temperature * 2.0) - 1.0, -1.0, 1.0);
            float humidity = (float) Math.clamp((sp.sample.humidity * 2.0) - 1.0, -1.0, 1.0);

            Climate.TargetPoint netherTarget = new Climate.TargetPoint(
                Climate.quantizeCoord(temperature),
                Climate.quantizeCoord(humidity),
                0L, 0L, 0L, 0L
            );
            return netherParameters.findValue(netherTarget);
        }

        // 3. The Overworld:
        // When on an incised river channel, directly assign the River biome
        if (sp.sample.riverIncision > 2.5 && sp.sample.finalSurface >= config.seaLevel() - 4.0) {
            double effTemp = climateClassifier.getEffectiveTemperature(sp.sample.temperature, worldY);
            return (effTemp < 0.15) ? frozenRiver : river;
        }

        // Standard multi-noise evaluation (natural oceans, coasts, plains, mountains, caves)
        Climate.TargetPoint overworldTarget = convertToOverworldClimate(sp.sample, worldY);
        return overworldParameters.findValue(overworldTarget);
    }

    /**
     * Maps End surface elevation to the erosion axis used by the End
     * biome parameter list (TECHSPEC §207).
     *
     * @param sample pipeline sample of the column
     * @return End erosion axis value
     */
    private float computeEndErosion(GeoSample sample) {
        double surfaceH = sample.finalSurface;
        if (surfaceH < 0.0) return -0.60f;
        if (surfaceH <= 52.0) return -0.15f;
        if (surfaceH <= 65.0) return 0.25f;
        return 0.75f;
    }

    /**
     * Builds the six-axis climate target point from pipeline fields
     * (TECHSPEC §205-§206).
     *
     * @param sample pipeline sample of the column
     * @param worldY absolute world Y of the query
     * @return quantized climate target point
     */
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

    /**
     * Maps surface height relative to sea level onto the vanilla
     * continentalness axis (TECHSPEC §206).
     *
     * @param deltaH surface height minus sea level, in blocks
     * @return continentalness axis value in [-1, 1]
     */
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
     * Maps elevation, slope, and incision onto the vanilla erosion
     * axis (TECHSPEC §206).
     *
     * @param hf final surface H_f
     * @param slope |∇H|
     * @param incision channel incision R
     * @return erosion axis value in [-1, 1]
     */
    private float computeErosion(double hf, double slope, double incision) {
        double seaLevel = config.seaLevel();
        double heightAboveSea = Math.max(0.0, hf - seaLevel);

        double elevationFactor = Math.clamp(heightAboveSea / 280.0, 0.0, 1.0);
        double slopeFactor = Math.clamp(slope / 0.85, 0.0, 1.0);

        float baseErosion = (float) (0.45 - (1.25 * Math.pow(Math.max(elevationFactor, slopeFactor), 0.75)));

        if (incision > 4.0) {
            baseErosion = Math.clamp(baseErosion - 0.20f, -1.0f, 1.0f);
        }
        return Math.clamp(baseErosion, -1.0f, 1.0f);
    }

    /**
     * Maps stress warp and special conditions onto the vanilla
     * weirdness axis (TECHSPEC §206).
     *
     * @param sample pipeline sample of the column
     * @return weirdness axis value in [-1, 1]
     */
    private float computeWeirdness(GeoSample sample) {
        if (sample.riverIncision > 6.0) return -0.67f;
        if (sample.finalSurface > (double) (config.seaLevel() + 240)) return 0.0f;
        return (float) Math.clamp(sample.stressWarpX / 32.0, -1.0, 1.0);
    }
}
