package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Anisotropic coordinate warp that deforms the tectonic domain
 * (TECHSPEC §16).
 *
 * <p>x' = x + W_x(x, z) and z' = z + W_z(x, z), with W_x and W_z
 * drawn from independent seed domains at the configured low
 * frequency. The configuration validates the Jacobian bound so the
 * warp never degenerates into a high-frequency coordinate
 * distortion (TECHSPEC §16, §64).
 */
public final class StressWarp {
    /** Noise stream for the X displacement. */
    private final GeoNoise noiseX;
    /** Noise stream for the Z displacement. */
    private final GeoNoise noiseZ;
    /** Spatial frequency of the warp. */
    private final double frequency;
    /** Peak displacement in blocks; bounded by the Jacobian check. */
    private final double amplitude;

    /**
     * Derives the two independent seed domains and captures the warp
     * parameters.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration supplying frequency and amplitude
     */
    public StressWarp(long worldSeed, GeoConfig config) {
        long seedX = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.STRESS_X.getSalt(), config.generatorVersion());
        long seedZ = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.STRESS_Z.getSalt(), config.generatorVersion());
        this.noiseX = new GeoNoise(seedX);
        this.noiseZ = new GeoNoise(seedZ);
        this.frequency = config.stressFrequency();
        this.amplitude = config.stressAmplitude();
    }

    /**
     * @param x world-space X
     * @param z world-space Z
     * @return X displacement W_x, bounded by the configured amplitude
     */
    public double getWarpX(double x, double z) {
        return noiseX.sample2D(x * frequency, z * frequency) * amplitude;
    }

    /**
     * @param x world-space X
     * @param z world-space Z
     * @return Z displacement W_z, bounded by the configured amplitude
     */
    public double getWarpZ(double x, double z) {
        return noiseZ.sample2D(x * frequency, z * frequency) * amplitude;
    }
}
