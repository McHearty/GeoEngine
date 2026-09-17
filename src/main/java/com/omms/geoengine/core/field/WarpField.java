package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic vertical terrain-warp field.
 *
 * <p>The warp is generated from a two-dimensional noise field in X/Z space and attenuated
 * according to normalized Y position. Its magnitude is bounded by the configured maximum warp
 * amplitude.
 */
public final class WarpField {

    /** Deterministic noise source used to generate horizontal warp variation. */
    private final GeoNoise noise;

    /** Maximum absolute vertical displacement produced by the warp. */
    private final double maxWarp;

    /** Lower bound of the configured world-height range used for Y normalization. */
    private final double minY;

    /** Upper bound of the configured world-height range used for Y normalization. */
    private final double maxY;

    /** Exponent controlling how strongly warp is attenuated with normalized altitude. */
    private final double altitudeExponent;

    /**
     * Creates a warp field with a seed derived from the world and warp noise domain.
     *
     * @param worldSeed world seed used for deterministic noise generation
     * @param config terrain configuration providing the dimension, generator version, world-height
     *     range, and maximum warp amplitude
     */
    public WarpField(long worldSeed, GeoConfig config) {
        long seed =
            SeedDerivation.derive(
                worldSeed,
                config.dimensionId(),
                NoiseDomain.WARP.getSalt(),
                config.generatorVersion());

        this.noise = new GeoNoise(seed);
        this.maxWarp = config.maxWarpAmplitude();
        this.minY = config.worldMinY();
        this.maxY = config.worldMaxY();
        this.altitudeExponent = 2.0;
    }

    /**
     * Evaluates the vertical warp at a world-space position.
     *
     * <p>The Y coordinate is normalized to the configured world-height range and clamped to
     * {@code [0, 1]}. The resulting normalized altitude controls a power-law damping factor,
     * while the X/Z coordinates determine the underlying noise value.
     *
     * @param x world-space X coordinate
     * @param y world-space Y coordinate
     * @param z world-space Z coordinate
     * @return vertical warp displacement bounded to {@code [-maxWarp, maxWarp]}
     */
    public double evaluateWarp(double x, double y, double z) {
        double normY = Math.clamp((y - minY) / (maxY - minY), 0.0, 1.0);
        double damping = 1.0 - Math.pow(normY, altitudeExponent);
        double raw = noise.sample2D(x * 0.02, z * 0.02);
        return Math.clamp(raw * maxWarp * damping, -maxWarp, maxWarp);
    }
}
