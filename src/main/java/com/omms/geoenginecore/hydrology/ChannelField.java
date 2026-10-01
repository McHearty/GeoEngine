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
     * 
     * Raw cell count (not log1p-transformed). Set to 8.0 to reduce
     * network density and avoid "veiny" terrain.
     */
    public static final double CHANNEL_INITIATION_FLOW = 8.0;

    /** Hides the implicit constructor. This is a static utility class. */
    private ChannelField() {}

    /**
     * Saturated channel corridor width W(A_f) in blocks
     * (TECHSPEC §29). Recalibrated for raw cell-count values
     * (Phase 9 Sprint R2) with target envelope:
     * <p>A_f=8 (initiation): 4 blocks
     * <p>A_f=20 (stream): 10 blocks
     * <p>A_f=100+ (trunk): 25-28 blocks
     *
     * @param flowAcc flow accumulation proxy A_f
     * @return corridor width in blocks, 0 below the initiation threshold
     */
    public static double getWidth(double flowAcc) {
        if (flowAcc < CHANNEL_INITIATION_FLOW) return 0.0;
        double strength = flowAcc - CHANNEL_INITIATION_FLOW;
        return 4.0 + 24.0 * (1.0 - Math.exp(-strength * 0.024));
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
        return corridorFactor(getWidth(flowAcc) * 0.5, distanceToCenterline);
    }

    /**
     * Cross-sectional profile factor F_channel in [0.0, 1.0] given the
     * half-width directly (TECHSPEC §29). The thalweg is 1.0; the bed
     * follows a cosine U-trough that decays smoothly to 0.0 at the
     * banks and stays 0.0 beyond them. A non-positive half-width means
     * no channel (unsaturated overland flow), where the factor is 0.0.
     *
     * @param halfWidth channel half-width in blocks
     * @param distance perpendicular distance from the centerline in blocks
     * @return profile factor in [0.0, 1.0]
     */
    public static double corridorFactor(double halfWidth, double distance) {
        if (halfWidth <= 0.0) return 0.0;
        if (distance >= halfWidth) {
            return 0.0; // Outside channel banks: untouched terrain
        }

        // Cosine U-trough channel bed
        double norm = distance / halfWidth;
        double profile = Math.cos(norm * (Math.PI * 0.5));
        return profile * profile;
    }
}
