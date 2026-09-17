package com.geoengine.core.hydrology;

import com.geoengine.core.math.GeoConfig;

/**
 * Computes river incision and channel width from a terrain sample's flow accumulation and slope.
 *
 * <p>Incision increases with flow accumulation, slope, and the supplied climate multiplier, while
 * the result is bounded by a configured maximum depth. Channel width follows a separate
 * saturating relationship with flow accumulation.
 */
public final class RiverField {

    /** Maximum incision depth permitted by the model. */
    private final double maxIncisionDepth;

    /** Controls the saturation rate of flow accumulation in the incision model. */
    private final double channelSteepness;

    /**
     * Creates a river field using the current fixed incision-model parameters.
     *
     * @param config terrain configuration; currently retained for API consistency but its values
     *     are not read by this implementation
     */
    public RiverField(GeoConfig config) {
        this.maxIncisionDepth = 48.0;
        this.channelSteepness = 0.35;
    }

    /**
     * Computes river incision depth from flow accumulation, slope, and climate.
     *
     * <p>Flow accumulation at or below {@code 0.2} produces no incision. Otherwise, the
     * accumulation contribution saturates exponentially, while slope and climate scale the
     * resulting incision. The final value is bounded between zero and the flow-dependent maximum.
     *
     * @param flowAccumulation local flow-accumulation value
     * @param slopeMagnitude local slope magnitude
     * @param climateMultiplier climate-dependent scaling factor
     * @return modeled river-incision depth
     */
    public double computeIncision(
        double flowAccumulation,
        double slopeMagnitude,
        double climateMultiplier
    ) {
        if (flowAccumulation <= 0.2) {
            return 0.0;
        }

        double flowStrength = 1.0 - Math.exp(-flowAccumulation * channelSteepness);
        double slopeFactor = Math.min(2.5, slopeMagnitude * 1.5);
        double rBase = 24.0 * flowStrength * slopeFactor * climateMultiplier;

        double rMax = maxIncisionDepth * Math.min(1.0, slopeFactor + 0.2);
        return Math.clamp(rBase, 0.0, rMax);
    }

    /**
     * Computes channel width from flow accumulation.
     *
     * <p>The width increases with accumulation and approaches an upper bound as accumulation
     * becomes large.
     *
     * @param flowAccumulation local flow-accumulation value
     * @return modeled channel width
     */
    public double computeChannelWidth(double flowAccumulation) {
        return 2.0 + 40.0 * (1.0 - Math.exp(-flowAccumulation * 0.15));
    }
}
