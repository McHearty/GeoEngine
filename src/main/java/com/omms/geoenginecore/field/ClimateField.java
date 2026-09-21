package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Horizontal climate field (TECHSPEC §19-§21).
 *
 * <p>Temperature and humidity are horizontal fields in [0, 1]
 * derived from independent seed domains. The vertical lapse
 * component is applied separately where needed and is an
 * approximation, not a physically valid atmospheric model across
 * 2048 blocks (TECHSPEC §19). The climate multiplier modifies
 * process strength without erasing tectonic identity
 * (TECHSPEC §20).
 */
public final class ClimateField {
    /** Temperature noise stream. */
    private final GeoNoise tempNoise;
    /** Humidity noise stream. */
    private final GeoNoise humidNoise;
    /** Spatial frequency of the temperature field. */
    private final double fTemp;
    /** Spatial frequency of the humidity field. */
    private final double fHumid;
    /** Lower bound of the climate multiplier K (TECHSPEC §20). */
    private final double kMin;
    /** Upper bound of the climate multiplier K (TECHSPEC §20). */
    private final double kMax;

    /**
     * Derives the two independent climate seed domains and captures
     * the frequencies and multiplier bounds.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
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

    /**
     * @param x world-space X
     * @param z world-space Z
     * @return normalized temperature at (x, z), nominally in [0, 1]
     */
    public double evaluateTemperature(double x, double z) {
        return (tempNoise.sample2D(x * fTemp, z * fTemp) + 1.0) * 0.5;
    }

    /**
     * @param x world-space X
     * @param z world-space Z
     * @return normalized humidity at (x, z), nominally in [0, 1]
     */
    public double evaluateHumidity(double x, double z) {
        return (humidNoise.sample2D(x * fHumid, z * fHumid) + 1.0) * 0.5;
    }

    /**
     * Maps the (temperature + humidity)/2 signal onto
     * [kMin, kMax] (TECHSPEC §20).
     *
     * <p>The multiplier scales process strength; it must not replace
     * tectonic structure.
     *
     * @param temp normalized temperature at the column
     * @param humid normalized humidity at the column
     * @return climate multiplier K ∈ [kMin, kMax] when the inputs lie in [0, 1]
     */
    public double computeMultiplier(double temp, double humid) {
        double rawFactor = 0.5 * (temp + humid);
        return kMin + (kMax - kMin) * rawFactor;
    }
}
