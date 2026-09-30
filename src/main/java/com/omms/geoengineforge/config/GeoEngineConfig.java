package com.omms.geoengineforge.config;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoConfigNormalizer;
import com.omms.geoenginecore.math.NormalizedGeoParams;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge ModConfigSpec for GeoEngine terrain sliders
 * (TECHSPEC §77).
 *
 * <p>All sliders are normalized [0.0, 1.0]. Active spec values are
 * converted into a validated {@link GeoConfig} at world creation;
 * changes to the TOML file apply on the next world or restart.
 */
public final class GeoEngineConfig {
    /** Built spec: the single source of the TOML file. */
    public static final ModConfigSpec SPEC;

    // Macro Scale & Geometry
    /** Continental landmass/ocean scale (0.0 = small islands, 1.0 = vast continents). */
    public static final ModConfigSpec.DoubleValue CONTINENTAL_SCALE;
    /** Wavelength of the primary mountain band (0.0 = tight crumpled ridges, 1.0 = broad ranges). */
    public static final ModConfigSpec.DoubleValue MOUNTAIN_SCALE;
    /** Height of the primary mountain band (0.0 = rolling hills, 1.0 = towering peaks). */
    public static final ModConfigSpec.DoubleValue MOUNTAIN_RELIEF;
    /** Height and roughness of the secondary ridge band. */
    public static final ModConfigSpec.DoubleValue RIDGE_ROUGHNESS;
    /** Flatness of lowlands and valleys (0.0 = narrow V-valleys, 1.0 = broad flat plains). */
    public static final ModConfigSpec.DoubleValue VALLEY_FLATNESS;
    /** Directional tectonic shear / anisotropy (0.0 = isotropic, 1.0 = strong linear ridges). */
    public static final ModConfigSpec.DoubleValue STRESS_SHEAR;

    // Geomorphic Weathering & Hydrology
    /** Long-term erosion intensity (0.0 = sharp unweathered rock, 1.0 = heavily eroded). */
    public static final ModConfigSpec.DoubleValue EROSION_STRENGTH;
    /** River/canyon incision depth (0.0 = shallow streams, 1.0 = deep gorges). */
    public static final ModConfigSpec.DoubleValue RIVER_INCISION_DEPTH;
    /** Speed of the incision response to flow accumulation. */
    public static final ModConfigSpec.DoubleValue RIVER_INCISION_RATE;

    // Volumetric 3D Detail
    /** 3-D volumetric cliff overhang / rock-grain intensity. */
    public static final ModConfigSpec.DoubleValue CLIFF_OVERHANG_INTENSITY;

    /** Relief profile (default, continental, alpine, canyonlands). */
    public static final ModConfigSpec.ConfigValue<String> RELIEF_PROFILE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("=====================================================",
                  " GeoEngine Terrain Configuration (Old Man Modding Studio)",
                  " All parameters are normalized [0.0 - 1.0] for easy tuning.",
                  " Changes to this file take effect on next world creation",
                  " or game restart without needing to recompile the mod.",
                  "=====================================================");

        RELIEF_PROFILE = b.comment("Relief profile preset (default, continental, alpine, canyonlands)")
            .define("relief_profile", "default");

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
     * Constructs a validated {@link GeoConfig} dynamically from the
     * active TOML config (TECHSPEC §77).
     *
     * @param version generator version bound into the config identity
     * @return validated Overworld configuration
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
