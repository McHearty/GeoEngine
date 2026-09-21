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

/**
 * Deferred worldgen registry setup (TECHSPEC §204).
 *
 * <p>Registers the GeoEngine chunk generator and biome source codecs
 * under the mod namespace so dimension data packs can reference them.
 */
public final class GeoRegistry {
    /** Deferred register for chunk generator codecs. */
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, GeoEngineMod.MOD_ID);

    /** Deferred register for biome source codecs. */
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, GeoEngineMod.MOD_ID);

    /**
     * Registers both codecs on the mod event bus.
     *
     * @param modBus mod event bus
     */
    public static void init(IEventBus modBus) {
        GENERATORS.register("geo_chunk_generator", () -> GeoChunkGenerator.CODEC);
        BIOME_SOURCES.register("geo_biome_source", () -> GeoBiomeSource.CODEC);
        GENERATORS.register(modBus);
        BIOME_SOURCES.register(modBus);
    }
}
