package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoConfig;

public final class RiverField {
    private final double maxIncisionDepth;
    private final double channelSteepness;

    public RiverField(GeoConfig config) {
        this.maxIncisionDepth = config.riverMaxIncision();
        this.channelSteepness = config.riverChannelSteepness();
    }

    public double computeIncision(double flowAccumulation, double slopeMagnitude, double climateMultiplier) {
        if (flowAccumulation <= 0.2) {
            return 0.0;
        }

        double flowStrength = 1.0 - Math.exp(-flowAccumulation * channelSteepness);
        double slopeFactor = Math.min(2.5, slopeMagnitude * 1.5);
        double rBase = 24.0 * flowStrength * slopeFactor * climateMultiplier;
        double rMax = maxIncisionDepth * Math.min(1.0, slopeFactor + 0.2);

        return Math.clamp(rBase, 0.0, rMax);
    }

    public double computeChannelWidth(double flowAccumulation) {
        return 2.0 + 40.0 * (1.0 - Math.exp(-flowAccumulation * 0.15));
    }
}
