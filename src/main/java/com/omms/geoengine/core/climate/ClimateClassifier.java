package com.geoengine.core.climate;

import com.geoengine.core.math.GeoConfig;

public final class ClimateClassifier {
    private final double lapseRate;
    private final int seaLevel;

    /**
     * Creates a climate classifier using the supplied world-generation configuration.
     *
     * @param config configuration containing the lapse rate and sea level
     */
    public ClimateClassifier(GeoConfig config) {
        this.lapseRate = config.lapseRatePerBlock();
        this.seaLevel = config.seaLevel();
    }

    /**
     * Applies the configured lapse rate to a base temperature above sea level.
     *
     * <p>Altitude below sea level does not further increase the temperature. The resulting
     * temperature is clamped to the range {@code [0.0, 1.0]}.
     *
     * @param baseTemp base temperature at or below sea level
     * @param worldY world-space Y coordinate at which to evaluate the temperature
     * @return effective temperature normalized to the range {@code [0.0, 1.0]}
     */
    public double getEffectiveTemperature(double baseTemp, double worldY) {
        double altitudeAboveSea = Math.max(0.0, worldY - seaLevel);
        return Math.clamp(baseTemp - (altitudeAboveSea * lapseRate), 0.0, 1.0);
    }

    /**
     * Classifies a location into a climate zone using effective temperature and humidity.
     *
     * @param effectiveTemp effective temperature normalized to the range {@code [0.0, 1.0]}
     * @param humidity humidity normalized to the range {@code [0.0, 1.0]}
     * @return climate zone corresponding to the supplied temperature and humidity
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
