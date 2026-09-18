package com.omms.geoengineforge.config;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoConfigNormalizer;
import com.omms.geoenginecore.math.NormalizedGeoParams;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class GeoEngineConfig {
    public static final ModConfigSpec SPEC;

    // Macro Scale & Geometry
    public static final ModConfigSpec.DoubleValue CONTINENTAL_SCALE;
    public static final ModConfigSpec.DoubleValue MOUNTAIN_SCALE;
    public static final ModConfigSpec.DoubleValue MOUNTAIN_RELIEF;
    public static final ModConfigSpec.DoubleValue RIDGE_ROUGHNESS;
    public static final ModConfigSpec.DoubleValue VALLEY_FLATNESS;
    public static final ModConfigSpec.DoubleValue STRESS_SHEAR;

    // Geomorphic Weathering & Hydrology
    public static final ModConfigSpec.DoubleValue EROSION_STRENGTH;
    public static final ModConfigSpec.DoubleValue RIVER_INCISION_DEPTH;
    public static final ModConfigSpec.DoubleValue RIVER_INCISION_RATE;

    // Volumetric 3D Detail
    public static final ModConfigSpec.DoubleValue CLIFF_OVERHANG_INTENSITY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("=====================================================",
                  " GeoEngine Terrain Configuration (Old Man Modding Studio)",
                  " All parameters are normalized [0.0 - 1.0] for easy tuning.",
                  " Changes to this file take effect on next world creation",
                  " or game restart without needing to recompile the mod.",
                  "=====================================================");

        b.push("terrain_scale");
        CONTINENTAL_SCALE = b.comment("Scale of continental landmasses and oceans (0.0 = small islands, 1.0 = vast continents)")
            .defineInRange("continental_scale", 0.50, 0.0, 1.0);

        MOUNTAIN_SCALE = b.comment("Wavelength of mountain belts (0.0 = tight crumpled ridges, 1.0 = broad sweeping ranges)")
            .defineInRange("mountain_scale", 0.45, 0.0, 1.0);

        STRESS_SHEAR = b.comment("Directional tectonic crustal deformation (0.0 = isotropic/round hills, 1.0 = strong linear ridges)")
            .defineInRange("stress_shear", 0.35, 0.0, 1.0);
        b.pop();

        b.push("relief_and_elevation");
        MOUNTAIN_RELIEF = b.comment("Height of primary mountain ranges above base plains (0.0 = rolling hills, 1.0 = towering peaks)")
            .defineInRange("mountain_relief", 0.35, 0.0, 1.0);

        RIDGE_ROUGHNESS = b.comment("Roughness and height of secondary foothill ridges (0.0 = smooth hills, 1.0 = rugged foothills)")
            .defineInRange("ridge_roughness", 0.25, 0.0, 1.0);

        VALLEY_FLATNESS = b.comment("Flatness of lowlands and valleys (0.0 = narrow V-valleys, 1.0 = broad flat plains)")
            .defineInRange("valley_flatness", 0.60, 0.0, 1.0);
        b.pop();

        b.push("hydrology_and_erosion");
        EROSION_STRENGTH = b.comment("Depth of long-term geomorphic surface lowering (0.0 = sharp unweathered rock, 1.0 = heavily eroded)")
            .defineInRange("erosion_strength", 0.40, 0.0, 1.0);

        RIVER_INCISION_DEPTH = b.comment("Depth of river valleys and canyons (0.0 = shallow streams, 1.0 = deep gorges)")
            .defineInRange("river_incision_depth", 0.35, 0.0, 1.0);

        RIVER_INCISION_RATE = b.comment("How quickly rivers carve down as flow accumulates (0.0 = slow progressive, 1.0 = rapid carving)")
            .defineInRange("river_incision_rate", 0.30, 0.0, 1.0);
        b.pop();

        b.push("volumetric_warp");
        CLIFF_OVERHANG_INTENSITY = b.comment("3D volumetric cliff overhangs and rock grain (0.0 = smooth planar cliffs, 1.0 = dramatic ledges)")
            .defineInRange("cliff_overhang_intensity", 0.35, 0.0, 1.0);
        b.pop();

        SPEC = b.build();
    }

    /**
     * Constructs a validated GeoConfig instance dynamically from the active TOML config.
     */
    public static GeoConfig getActiveOverworldConfig(int version) {
        NormalizedGeoParams params = new NormalizedGeoParams(
            CONTINENTAL_SCALE.get(),
            MOUNTAIN_SCALE.get(),
            MOUNTAIN_RELIEF.get(),
            RIDGE_ROUGHNESS.get(),
            VALLEY_FLATNESS.get(),
            STRESS_SHEAR.get(),
            EROSION_STRENGTH.get(),
            RIVER_INCISION_DEPTH.get(),
            RIVER_INCISION_RATE.get(),
            CLIFF_OVERHANG_INTENSITY.get()
        );

        return GeoConfigNormalizer.toGeoConfig(version, 0, -64, 1984, 64, params);
    }
}
