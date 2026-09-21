package com.omms.geoenginecore.hydrology;

/**
 * Saturated channel corridor width and cross-section profile
 * (TECHSPEC §28-§29).
 *
 * <p>Channels are corridors of saturated flow, not fixed-width
 * trenches. Width grows with discharge and saturates in
 * 25-28 blocks for trunk rivers; below the initiation threshold the
 * column is unsaturated overland flow and has no corridor.
 */
public final class ChannelField {
    /**
     * Flow accumulation required to initiate a channel. Below this
     * threshold the column is unsaturated overland flow.
     */
    public static final double CHANNEL_INITIATION_FLOW = 2.5;

    /** Hides the implicit constructor. This is a static utility class. */
    private ChannelField() {}

    /**
     * Saturated channel corridor width W(A_f) in blocks
     * (TECHSPEC §29). Brooks: ~3-5 blocks wide; trunk rivers:
     * ~16-28 blocks wide.
     *
     * @param flowAcc flow accumulation proxy A_f
     * @return corridor width in blocks, 0 below the initiation threshold
     */
    public static double getWidth(double flowAcc) {
        if (flowAcc < CHANNEL_INITIATION_FLOW) return 0.0;
        double strength = flowAcc - CHANNEL_INITIATION_FLOW;
        return 3.0 + 25.0 * (1.0 - Math.exp(-strength * 0.18));
    }

    /**
     * Cross-sectional channel profile factor F_channel in [0.0, 1.0]
     * (TECHSPEC §28). Centerline (thalweg) = 1.0; channel banks =
     * 0.0; outside the corridor = 0.0. The bed follows a cosine
     * U-trough that is smooth and never negative.
     *
     * @param flowAcc flow accumulation proxy A_f
     * @param distanceToCenterline horizontal distance to the thalweg in blocks
     * @return profile factor in [0.0, 1.0]
     */
    public static double getChannelProfileFactor(double flowAcc, double distanceToCenterline) {
        double width = getWidth(flowAcc);
        if (width <= 0.0) return 0.0;

        double halfWidth = width * 0.5;
        if (distanceToCenterline >= halfWidth) {
            return 0.0; // Outside channel banks: untouched terrain
        }

        // Parabolic / cosine U-trough channel bed
        double norm = distanceToCenterline / halfWidth;
        double profile = Math.cos(norm * (Math.PI * 0.5));
        return profile * profile;
    }
}
