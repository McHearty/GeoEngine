package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Fluvial incision model R (TECHSPEC §25-§28).
 *
 * <p>Incision requires a flow accumulation threshold; growth is
 * saturating in discharge, scaled by a slope factor that keeps a
 * lowland baseline so trunk rivers carve defined beds down to the
 * water table, and bounded by the configured maximum scaled with
 * local slope. Channels are saturated flow corridors, not fixed
 * trenches.
 */
public final class RiverField {
    /** Configured maximum incision depth in blocks. */
    private final double maxIncisionDepth;
    /** Steepness of the saturating flow-strength response. */
    private final double channelSteepness;

    /**
     * @param config validated configuration supplying the incision parameters
     */
    public RiverField(GeoConfig config) {
        this.maxIncisionDepth = config.riverMaxIncision();
        this.channelSteepness = config.riverChannelSteepness();
    }

    /**
     * Evaluates channel incision depth R for a river thalweg
     * (TECHSPEC §28): R = min(F(A_f)·F_slope·F_climate·F_channel, R_max).
     *
     * <p>Lowlands (slope ≈ 0) keep a 0.65 baseline so defined channel
     * beds survive; the result is clamped to the slope-scaled
     * maximum. F_channel is the thalweg-to-bank cosine-squared U
     * profile (TECHSPEC §29) measured from the routing-lattice
     * centerline, turning incision into a bounded corridor.
     *
     * @param flowAccumulation flow accumulation proxy A_f
     * @param slopeMagnitude |∇H₀|
     * @param climateMultiplier bounded climate multiplier K
     * @param channelFactor bounded channel form factor F_channel ∈ [0, 1]
     * @return incision depth R in blocks, 0 when flow is below threshold
     */
    public double computeIncision(double flowAccumulation, double slopeMagnitude,
                                  double climateMultiplier, double channelFactor) {
        if (flowAccumulation <= 1.8) {
            return 0.0;
        }

        double flowStrength = 1.0 - Math.exp(-(flowAccumulation - 1.8) * channelSteepness);
        // Lowlands (slope ≈ 0) maintain a baseline of 0.65 for defined channels
        double slopeFactor = 0.65 + Math.min(1.85, slopeMagnitude * 1.5);
        double rBase = 16.0 * flowStrength * slopeFactor * climateMultiplier * channelFactor;
        double rMax = maxIncisionDepth * Math.min(1.0, slopeFactor * 0.65);

        return GeoMath.clamp(rBase, 0.0, rMax);
    }

    /**
     * Flat-channel convenience overload (F_channel = 1.0) for lightweight
     * consumers without access to the routing lattice. Production
     * pipelines always use the four-argument form.
     *
     * @param flowAccumulation flow accumulation proxy A_f
     * @param slopeMagnitude |∇H₀|
     * @param climateMultiplier bounded climate multiplier K
     * @return incision depth R in blocks, 0 when flow is below threshold
     */
    public double computeIncision(double flowAccumulation, double slopeMagnitude, double climateMultiplier) {
        return computeIncision(flowAccumulation, slopeMagnitude, climateMultiplier, 1.0);
    }
}
