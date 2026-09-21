package com.omms.geoenginecore.climate;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Normalized horizontal humidity field (TECHSPEC §19, §21).
 *
 * <p>Remaps the humidity noise stream to [0, 1] (dry = 0, humid = 1)
 * at the configured very-low frequency. Humidity drives
 * evapotranspiration, chemical weathering, and the deposition ratio.
 */
public final class HumidityField {
    /** Humidity noise stream. */
    private final GeoNoise noise;
    /** Spatial frequency of the humidity field. */
    private final double frequency;

    /**
     * Derives the CLIMATE_HUMID seed domain and captures the
     * frequency.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration supplying the humidity frequency
     */
    public HumidityField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CLIMATE_HUMID.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.frequency = config.climateHumidFrequency();
    }

    /**
     * @param x world-space X
     * @param z world-space Z
     * @return normalized humidity, nominally in [0, 1]
     */
    public double evaluate(double x, double z) {
        return (noise.sample2D(x * frequency, z * frequency) + 1.0) * 0.5;
    }
}
