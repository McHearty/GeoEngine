package com.omms.geoenginecore.climate;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class TemperatureField {
    private final GeoNoise noise;
    private final double frequency;
    private final double lapseRate;
    private final int seaLevel;

    public TemperatureField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CLIMATE_TEMP.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.frequency = config.climateTempFrequency();
        this.lapseRate = config.lapseRatePerBlock();
        this.seaLevel = config.seaLevel();
    }

    public double evaluate(double x, double z, double worldY) {
        double raw = (noise.sample2D(x * frequency, z * frequency) + 1.0) * 0.5;
        double altitudeAboveSea = Math.max(0.0, worldY - seaLevel);
        return Math.clamp(raw - (altitudeAboveSea * lapseRate), 0.0, 1.0);
    }
}
