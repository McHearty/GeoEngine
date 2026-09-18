package com.omms.geoengineforge.integration;

import com.omms.geoengineforge.GeoEngineMod;
import com.omms.geoengineforge.generator.GeoBiomeSource;
import com.omms.geoengineforge.generator.GeoChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GeoRegistry {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, GeoEngineMod.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, GeoEngineMod.MOD_ID);

    public static void init(IEventBus modBus) {
        GENERATORS.register("geo_chunk_generator", () -> GeoChunkGenerator.CODEC);
        BIOME_SOURCES.register("geo_biome_source", () -> GeoBiomeSource.CODEC);
        GENERATORS.register(modBus);
        BIOME_SOURCES.register(modBus);
    }
}
