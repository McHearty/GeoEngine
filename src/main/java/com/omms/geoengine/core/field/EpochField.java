package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic terrain-age field.
 *
 * <p>The field is generated from a seed derived from the world configuration and sampled at the
 * configured epoch frequency. The sampled noise value is remapped and clamped to the normalized
 * range {@code [0, 1]} for use by downstream terrain processes.
 */
public final class EpochField {
    /** Deterministic noise source used to generate spatial age variation. */
    private final GeoNoise noise;

    /** Sampling frequency for the epoch noise field. */
    private final double frequency;

    /**
     * Creates an epoch field using a deterministic noise seed derived from the world configuration.
     *
     * @param worldSeed world seed used for deterministic field generation
     * @param config terrain-generation configuration
     */
    public EpochField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.EPOCH.getSalt(),
            config.generatorVersion());

        this.noise = new GeoNoise(seed);
        this.frequency = config.epochFrequency();
    }

    /**
     * Evaluates the normalized terrain-age field at a horizontal world-space position.
     *
     * <p>The noise value is remapped by {@code (value + 1) / 2} and then clamped to
     * {@code [0, 1]}. The clamping makes the returned range explicit regardless of minor deviations
     * outside the expected noise domain.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @return normalized terrain-age value in the range {@code [0, 1]}
     */
    public double evaluateAge(double x, double z) {
        double raw = noise.sample2D(x * frequency, z * frequency);
        return Math.clamp((raw + 1.0) * 0.5, 0.0, 1.0);
    }
}
