package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class ClimateField {
    private final GeoNoise tempNoise;
    private final GeoNoise humidNoise;
    private final double fTemp;
    private final double fHumid;
    private final double kMin;
    private final double kMax;

    public ClimateField(long worldSeed, GeoConfig config) {
        long sTemp = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CLIMATE_TEMP.getSalt(), config.generatorVersion());
        long sHumid = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CLIMATE_HUMID.getSalt(), config.generatorVersion());
        this.tempNoise = new GeoNoise(sTemp);
        this.humidNoise = new GeoNoise(sHumid);
        this.fTemp = config.climateTempFrequency();
        this.fHumid = config.climateHumidFrequency();
        this.kMin = config.climateMin();
        this.kMax = config.climateMax();
    }

    public double evaluateTemperature(double x, double z) {
        return (tempNoise.sample2D(x * fTemp, z * fTemp) + 1.0) * 0.5;
    }

    public double evaluateHumidity(double x, double z) {
        return (humidNoise.sample2D(x * fHumid, z * fHumid) + 1.0) * 0.5;
    }

    public double computeMultiplier(double temp, double humid) {
        double rawFactor = 0.5 * (temp + humid);
        return kMin + (kMax - kMin) * rawFactor;
    }
}
