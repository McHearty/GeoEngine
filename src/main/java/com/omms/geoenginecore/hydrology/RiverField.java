package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoConfig;

public final class RiverField {
    private final double maxIncisionDepth;
    private final double channelSteepness;

    public RiverField(GeoConfig config) {
        this.maxIncisionDepth = config.riverMaxIncision();
        this.channelSteepness = config.riverChannelSteepness();
    }

    /**
     * Evaluates channel incision depth R for a river thalweg (§28).
     * Lowlands maintain an incision baseline so trunk rivers carve defined beds down to the water table.
     */
    public double computeIncision(double flowAccumulation, double slopeMagnitude, double climateMultiplier) {
        if (flowAccumulation <= 1.8) {
            return 0.0;
        }

        double flowStrength = 1.0 - Math.exp(-(flowAccumulation - 1.8) * channelSteepness);
        // Lowlands (slope ≈ 0) maintain a baseline of 0.65 for defined channels
        double slopeFactor = 0.65 + Math.min(1.85, slopeMagnitude * 1.5);
        double rBase = 16.0 * flowStrength * slopeFactor * climateMultiplier;
        double rMax = maxIncisionDepth * Math.min(1.0, slopeFactor * 0.65);

        return Math.clamp(rBase, 0.0, rMax);
    }
}
