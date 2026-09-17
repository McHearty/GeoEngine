package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates deterministic temperature and humidity fields for terrain generation.
 *
 * <p>Temperature and humidity use independently derived noise seeds and configurable sampling
 * frequencies. The resulting noise values are remapped from the expected noise domain to the
 * climate field representation used by downstream terrain logic.
 *
 * <p>The climate multiplier linearly interpolates between the configured minimum and maximum
 * factors using the average of the supplied temperature and humidity values.
 */
public final class ClimateField {
    /** Deterministic noise field used to evaluate temperature variation. */
    private final GeoNoise tempNoise;

    /** Deterministic noise field used to evaluate humidity variation. */
    private final GeoNoise humidNoise;

    /** Sampling frequency for the temperature noise field. */
    private final double fTemp;

    /** Sampling frequency for the humidity noise field. */
    private final double fHumid;

    /** Minimum climate multiplier returned by {@link #computeMultiplier(double, double)}. */
    private final double kMin;

    /** Maximum climate multiplier returned by {@link #computeMultiplier(double, double)}. */
    private final double kMax;

    /**
     * Creates a climate field using deterministic noise derived from the world configuration.
     *
     * <p>Temperature and humidity use separate {@link NoiseDomain} values so their noise fields
     * receive independent derived seeds while remaining deterministic for the same world seed,
     * dimension, and generator version.
     *
     * @param worldSeed world seed used for deterministic field generation
     * @param config terrain-generation configuration
     */
    public ClimateField(long worldSeed, GeoConfig config) {
        long sTemp = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.CLIMATE_TEMP.getSalt(),
            config.generatorVersion());

        long sHumid = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.CLIMATE_HUMID.getSalt(),
            config.generatorVersion());

        this.tempNoise = new GeoNoise(sTemp);
        this.humidNoise = new GeoNoise(sHumid);
        this.fTemp = config.climateTempFrequency();
        this.fHumid = config.climateHumidFrequency();
        this.kMin = config.climateMin();
        this.kMax = config.climateMax();
    }

    /**
     * Evaluates the temperature field at a horizontal world-space position.
     *
     * <p>The noise result is remapped by {@code (value + 1) / 2} before being returned. The
     * resulting interpretation therefore depends on the output-domain contract of {@link GeoNoise}.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @return remapped temperature field value
     */
    public double evaluateTemperature(double x, double z) {
        return (tempNoise.sample2D(x * fTemp, z * fTemp) + 1.0) * 0.5;
    }

    /**
     * Evaluates the humidity field at a horizontal world-space position.
     *
     * <p>The noise result is remapped by {@code (value + 1) / 2} before being returned. The
     * resulting interpretation therefore depends on the output-domain contract of {@link GeoNoise}.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @return remapped humidity field value
     */
    public double evaluateHumidity(double x, double z) {
        return (humidNoise.sample2D(x * fHumid, z * fHumid) + 1.0) * 0.5;
    }

    /**
     * Computes the climate multiplier from temperature and humidity values.
     *
     * <p>The two inputs are averaged and used as the interpolation factor between the configured
     * minimum and maximum climate multipliers. This method does not clamp the supplied inputs;
     * callers are therefore responsible for providing values in the intended interpolation domain.
     *
     * @param temp temperature field value
     * @param humid humidity field value
     * @return linearly interpolated climate multiplier
     */
    public double computeMultiplier(double temp, double humid) {
        double rawFactor = 0.5 * (temp + humid);
        return kMin + (kMax - kMin) * rawFactor;
    }
}
