package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic horizontal stress-warp field.
 *
 * <p>The X and Z displacement components are generated from independently seeded noise fields,
 * allowing horizontal terrain coordinates to be displaced along each axis using the same spatial
 * frequency and configured amplitude.
 */
public final class StressWarp {
    /** Deterministic noise source for the X-axis displacement component. */
    private final GeoNoise noiseX;

    /** Deterministic noise source for the Z-axis displacement component. */
    private final GeoNoise noiseZ;

    /** Sampling frequency shared by both horizontal displacement fields. */
    private final double frequency;

    /** Displacement scale applied to both noise components. */
    private final double amplitude;

    /**
     * Creates a stress-warp field using independently derived X and Z noise seeds.
     *
     * @param worldSeed world seed used for deterministic field generation
     * @param config terrain-generation configuration
     */
    public StressWarp(long worldSeed, GeoConfig config) {
        long seedX = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.STRESS_X.getSalt(),
            config.generatorVersion());

        long seedZ = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.STRESS_Z.getSalt(),
            config.generatorVersion());

        this.noiseX = new GeoNoise(seedX);
        this.noiseZ = new GeoNoise(seedZ);
        this.frequency = config.stressFrequency();
        this.amplitude = config.stressAmplitude();
    }

    /**
     * Evaluates the X-axis horizontal warp displacement.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @return X-axis displacement produced by the stress field
     */
    public double getWarpX(double x, double z) {
        return noiseX.sample2D(x * frequency, z * frequency) * amplitude;
    }

    /**
     * Evaluates the Z-axis horizontal warp displacement.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @return Z-axis displacement produced by the stress field
     */
    public double getWarpZ(double x, double z) {
        return noiseZ.sample2D(x * frequency, z * frequency) * amplitude;
    }
}
