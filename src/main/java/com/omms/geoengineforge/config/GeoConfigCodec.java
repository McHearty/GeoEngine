package com.omms.geoengineforge.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.omms.geoenginecore.math.GeoConfig;

/**
 * Minecraft serialization codec for {@link GeoConfig}.
 *
 * <p>The core GeoEngine configuration remains independent of
 * Minecraft serialization. This adapter exposes the immutable
 * configuration to world-generation codecs.
 *
 * <p>Uses RecordCodecBuilder with 20 fields, omitting the less
 * frequently modified parameters which use defaults when not
 * serialized.
 */
public final class GeoConfigCodec {

    private GeoConfigCodec() {
    }

    /** Codec for the complete persisted GeoEngine configuration. */
    public static final Codec<GeoConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("generator_version")
                .forGetter(GeoConfig::generatorVersion),
            Codec.INT.fieldOf("dimension_id")
                .forGetter(GeoConfig::dimensionId),
            Codec.INT.fieldOf("world_min_y")
                .forGetter(GeoConfig::worldMinY),
            Codec.INT.fieldOf("world_max_y")
                .forGetter(GeoConfig::worldMaxY),
            Codec.INT.fieldOf("sea_level")
                .forGetter(GeoConfig::seaLevel),

            Codec.DOUBLE.fieldOf("tectonic_freq_low")
                .forGetter(GeoConfig::tectonicFreqLow),
            Codec.DOUBLE.fieldOf("tectonic_freq_a")
                .forGetter(GeoConfig::tectonicFreqA),
            Codec.DOUBLE.fieldOf("tectonic_freq_b")
                .forGetter(GeoConfig::tectonicFreqB),

            Codec.DOUBLE.fieldOf("tectonic_amp_low")
                .forGetter(GeoConfig::tectonicAmpLow),
            Codec.DOUBLE.fieldOf("tectonic_amp_a")
                .forGetter(GeoConfig::tectonicAmpA),
            Codec.DOUBLE.fieldOf("tectonic_amp_b")
                .forGetter(GeoConfig::tectonicAmpB),

            Codec.DOUBLE.fieldOf("uplift_exponent")
                .forGetter(GeoConfig::upliftExponent),

            Codec.DOUBLE.fieldOf("stress_frequency")
                .forGetter(GeoConfig::stressFrequency),
            Codec.DOUBLE.fieldOf("stress_amplitude")
                .forGetter(GeoConfig::stressAmplitude),
            Codec.DOUBLE.fieldOf("stress_max_jacobian")
                .forGetter(GeoConfig::stressMaxJacobian)
        ).apply(instance, (generatorVersion, dimensionId, worldMinY, worldMaxY, seaLevel,
                           tectonicFreqLow, tectonicFreqA, tectonicFreqB,
                           tectonicAmpLow, tectonicAmpA, tectonicAmpB,
                           upliftExponent,
                           stressFrequency, stressAmplitude, stressMaxJacobian) -> {
            // Default values for remaining parameters
            return new GeoConfig(
                generatorVersion, dimensionId, worldMinY, worldMaxY, seaLevel,
                tectonicFreqLow, tectonicFreqA, tectonicFreqB,
                tectonicAmpLow, tectonicAmpA, tectonicAmpB,
                upliftExponent,
                stressFrequency, stressAmplitude, stressMaxJacobian,
                0.00001,  // epoch_frequency: default
                0.00005,  // climate_temp_frequency: default
                0.00005,  // climate_humid_frequency: default
                0.0,  // climate_min: default
                1.0,  // climate_max: default
                0.01,  // lapse_rate_per_block: default
                35.0,  // base_erosion_rate: default
                1.0,  // max_warp_amplitude: default
                3,  // surface_band_radius: default
                18.0,  // river_max_incision: default
                1.0,  // river_channel_steepness: default
                1,  // drainage_iterations: default
                -64,  // cave_min_y: default
                0   // cave_max_y: default
            );
        })
    );
}