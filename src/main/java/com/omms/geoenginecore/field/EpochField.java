package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Geological epoch (age) field (TECHSPEC §17).
 *
 * <p>A very-low-frequency noise stream remapped to [0, 1]: young
 * (0) → old (1). Age modulates tectonic influence (young) versus
 * erosion, weathering, and deposition (old). The transition is
 * spatially smooth, and the age mask is evaluated before
 * derivatives so process parameters blend before differencing
 * (TECHSPEC §18).
 */
public final class EpochField {
    /** Age noise stream. */
    private final GeoNoise noise;
    /** Very-low spatial frequency of the age field. */
    private final double frequency;

    /**
     * Derives the EPOCH seed domain and captures the frequency.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration supplying the epoch frequency
     */
    public EpochField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EPOCH.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.frequency = config.epochFrequency();
    }

    /**
     * @param x world-space X
     * @param z world-space Z
     * @return age factor in [0, 1]
     */
    public double evaluateAge(double x, double z) {
        double raw = noise.sample2D(x * frequency, z * frequency);
        return Math.clamp((raw + 1.0) * 0.5, 0.0, 1.0);
    }
}
