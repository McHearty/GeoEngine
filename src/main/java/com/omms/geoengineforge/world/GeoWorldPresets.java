package com.omms.geoengineforge.integration;

import com.omms.geoengineforge.GeoEngineMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * Resource keys for GeoEngine world presets.
 */
public final class GeoWorldPresets {
    /** GeoEngine world preset. */
    public static final ResourceKey<WorldPreset> GEOENGINE =
        ResourceKey.create(
            Registries.WORLD_PRESET,
            ResourceLocation.parse(GeoEngineMod.MOD_ID + ":geoengine")
        );

    private GeoWorldPresets() {
    }
}