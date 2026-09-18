package com.omms.geoenginecore.climate;

import com.omms.geoenginecore.math.GeoConfig;

public final class ClimateClassifier {
    private final double lapseRate;
    private final int seaLevel;

    public ClimateClassifier(GeoConfig config) {
        this.lapseRate = config.lapseRatePerBlock();
        this.seaLevel = config.seaLevel();
    }

    public double getEffectiveTemperature(double baseTemp, double worldY) {
        double altitudeAboveSea = Math.max(0.0, worldY - seaLevel);
        return Math.clamp(baseTemp - (altitudeAboveSea * lapseRate), 0.0, 1.0);
    }

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
