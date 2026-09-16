package com.geoengine.forge.generator;

import com.geoengine.core.climate.ClimateClassifier;
import com.geoengine.core.climate.ClimateZone;
import com.geoengine.core.geomorphology.LandformBits;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.ScalarFieldKernel;
import com.geoengine.core.memory.ScratchpadProvider;
import com.geoengine.core.memory.WorkerScratchpad;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

public final class GeoBiomeSource extends BiomeSource {
    public static final MapCodec<GeoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            instance.fieldOf("seed").forGetter(s -> s.worldSeed)
        ).apply(instance, GeoBiomeSource::createDefault)
    );

    private final long worldSeed;
    private final GeoConfig config;
    private final ScalarFieldKernel kernel;
    private final ClimateClassifier climateClassifier;

    private final Holder<Biome> ocean;
    private final Holder<Biome> deepOcean;
    private final Holder<Biome> plains;
    private final Holder<Biome> desert;
    private final Holder<Biome> windsweptHills;
    private final Holder<Biome> jaggedPeaks;
    private final Holder<Biome> frozenPeaks;
    private final Holder<Biome> river;
    private final Holder<Biome> swamp;
    private final Holder<Biome> forest;
    private final Holder<Biome> snowySlopes;

    public static GeoBiomeSource createDefault(long seed) {
        throw new UnsupportedOperationException("Must be constructed via registry lookup in Forge context");
    }

    public GeoBiomeSource(long worldSeed, HolderGetter<Biome> biomes) {
        this.worldSeed = worldSeed;
        this.config = GeoConfig.defaultOverworld(1);
        this.kernel = new ScalarFieldKernel(worldSeed, config);
        this.climateClassifier = new ClimateClassifier(config);

        this.ocean = biomes.getOrThrow(Biomes.OCEAN);
        this.deepOcean = biomes.getOrThrow(Biomes.DEEP_OCEAN);
        this.plains = biomes.getOrThrow(Biomes.PLAINS);
        this.desert = biomes.getOrThrow(Biomes.DESERT);
        this.windsweptHills = biomes.getOrThrow(Biomes.WINDSWEPT_HILLS);
        this.jaggedPeaks = biomes.getOrThrow(Biomes.JAGGED_PEAKS);
        this.frozenPeaks = biomes.getOrThrow(Biomes.FROZEN_PEAKS);
        this.river = biomes.getOrThrow(Biomes.RIVER);
        this.swamp = biomes.getOrThrow(Biomes.SWAMP);
        this.forest = biomes.getOrThrow(Biomes.FOREST);
        this.snowySlopes = biomes.getOrThrow(Biomes.SNOWY_SLOPES);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(
            ocean, deepOcean, plains, desert, windsweptHills,
            jaggedPeaks, frozenPeaks, river, swamp, forest, snowySlopes
        );
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int worldX = quartX << 2;
        int worldY = quartY << 2;
        int worldZ = quartZ << 2;

        WorkerScratchpad sp = ScratchpadProvider.get();
        double surfaceH = kernel.evaluateH0(worldX, worldZ, sp.sample);

        if (surfaceH < config.seaLevel()) {
            return (surfaceH < config.seaLevel() - 30.0) ? deepOcean : ocean;
        }

        if (sp.sample.riverIncision > 4.0) {
            return river;
        }

        double effTemp = climateClassifier.getEffectiveTemperature(sp.sample.temperature, worldY);
        ClimateZone zone = climateClassifier.classifyZone(effTemp, sp.sample.humidity);

        if (surfaceH > config.seaLevel() + 240.0) {
            if (zone == ClimateZone.POLAR || effTemp < 0.25) {
                return frozenPeaks;
            }
            return jaggedPeaks;
        }

        if (surfaceH > config.seaLevel() + 140.0) {
            if (effTemp < 0.35) return snowySlopes;
            return windsweptHills;
        }

        return switch (zone) {
            case POLAR, BOREAL_TUNDRA -> snowySlopes;
            case ARID_DESERT -> desert;
            case WARM_HUMID -> (sp.sample.humidity > 0.75) ? swamp : forest;
            case TEMPERATE -> (sp.sample.humidity > 0.55) ? forest : plains;
        };
    }
}
