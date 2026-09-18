package com.omms.geoenginecore.climate;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class HumidityField {
    private final GeoNoise noise;
    private final double frequency;

    public HumidityField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CLIMATE_HUMID.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.frequency = config.climateHumidFrequency();
    }

    public double evaluate(double x, double z) {
        return (noise.sample2D(x * frequency, z * frequency) + 1.0) * 0.5;
    }
}
