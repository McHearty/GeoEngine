package com.omms.geoenginecore.climate;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Normalized horizontal temperature field with lapse-rate altitude
 * correction (TECHSPEC §19, §21).
 *
 * <p>Remaps the temperature noise stream to [0, 1] at the
 * configured very-low frequency, then subtracts the validated lapse
 * rate per block above sea level and clamps to [0, 1]. The vertical
 * component is an approximation, not a physical atmospheric model
 * across 2048 blocks (TECHSPEC §19).
 */
public final class TemperatureField {
    /** Temperature noise stream. */
    private final GeoNoise noise;
    /** Spatial frequency of the temperature field. */
    private final double frequency;
    /** Validated vertical lapse rate in normalized units per block. */
    private final double lapseRate;
    /** Elevation reference: the dimension's sea level. */
    private final int seaLevel;

    /**
     * Derives the CLIMATE_TEMP seed domain and captures the
     * frequency, lapse rate, and sea level.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
    public TemperatureField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CLIMATE_TEMP.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.frequency = config.climateTempFrequency();
        this.lapseRate = config.lapseRatePerBlock();
        this.seaLevel = config.seaLevel();
    }

    /**
     * @param x world-space X
     * @param z world-space Z
     * @param worldY absolute world Y of the sample
     * @return effective temperature clamped to [0, 1]
     */
    public double evaluate(double x, double z, double worldY) {
        double raw = (noise.sample2D(x * frequency, z * frequency) + 1.0) * 0.5;
        double altitudeAboveSea = Math.max(0.0, worldY - seaLevel);
        return Math.clamp(raw - (altitudeAboveSea * lapseRate), 0.0, 1.0);
    }
}
