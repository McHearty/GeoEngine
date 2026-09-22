package com.omms.geoenginecore.climate;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Effective-temperature adjustment and climate zone
 * classification (TECHSPEC §21).
 *
 * <p>Base temperature is adjusted by the validated lapse rate over
 * elevation above sea level, then classified into one of five
 * zones. The classifier is a pure function of (effectiveTemp,
 * humidity) and the configuration.
 */
public final class ClimateClassifier {
    /** Validated vertical lapse rate in normalized units per block. */
    private final double lapseRate;
    /** Elevation reference: the dimension's sea level. */
    private final int seaLevel;

    /**
     * @param config validated configuration supplying the lapse rate
     *         and sea level
     */
    public ClimateClassifier(GeoConfig config) {
        this.lapseRate = config.lapseRatePerBlock();
        this.seaLevel = config.seaLevel();
    }

    /**
     * Applies the lapse-rate altitude adjustment (TECHSPEC §21).
     *
     * @param baseTemp horizontal base temperature in [0, 1]
     * @param worldY absolute world Y of the sample
     * @return effective temperature clamped to [0, 1]
     */
    public double getEffectiveTemperature(double baseTemp, double worldY) {
        double altitudeAboveSea = Math.max(0.0, worldY - seaLevel);
        return GeoMath.clamp(baseTemp - (altitudeAboveSea * lapseRate), 0.0, 1.0);
    }

    /**
     * Classifies the climate zone from effective temperature and
     * humidity (TECHSPEC §21).
     *
     * @param effectiveTemp lapse-adjusted temperature in [0, 1]
     * @param humidity normalized humidity in [0, 1]
     * @return the resulting climate zone
     */
    public ClimateZone classifyZone(double effectiveTemp, double humidity) {
        if (effectiveTemp < 0.20) {
            return ClimateZone.POLAR;
        }
        if (effectiveTemp < 0.40) {
            return ClimateZone.BOREAL_TUNDRA;
        }
        if (humidity < 0.22 && effectiveTemp > 0.55) {
            return ClimateZone.ARID_DESERT;
        }
        if (effectiveTemp > 0.65 && humidity > 0.60) {
            return ClimateZone.WARM_HUMID;
        }
        return ClimateZone.TEMPERATE;
    }
}
