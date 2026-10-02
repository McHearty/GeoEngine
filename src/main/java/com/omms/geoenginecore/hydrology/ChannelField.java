package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoConfig;

/**
 * Saturated channel corridor width and cross-section profile
 * (TECHSPEC §28-§29, TECHSPEC_AMEND001 A3.1).
 *
 * <p>Channels are corridors of saturated flow, not fixed-width
 * trenches. Width grows with discharge and saturates in
 * 25-28 blocks for trunk rivers; below the initiation threshold the
 * column is unsaturated overland flow and has no corridor.
 *
 * <p>The channel initiation threshold is configurable via
 * {@link GeoConfig#minRiverAccumulation()} (TECHSPEC_AMEND001 A3.1).
 */
public final class ChannelField {
    /**
     * Default flow accumulation required to initiate a channel.
     * Raw cell count (not log1p-transformed). Set to 14.0 to reduce
     * network density and eliminate spurious 2-3 block fragments.
     */
    public static final double CHANNEL_INITIATION_FLOW = 14.0;

    /** Configured minimum river accumulation threshold. */
    private final double minAccumulation;

    /**
     * Constructs a channel field with the default initiation threshold.
     */
    public ChannelField() {
        this(CHANNEL_INITIATION_FLOW);
    }

    /**
     * Constructs a channel field with a configurable initiation threshold.
     *
     * @param config validated configuration supplying the threshold
     */
    public ChannelField(GeoConfig config) {
        this(config.minRiverAccumulation());
    }

    /**
     * Constructs a channel field with a specific initiation threshold.
     *
     * @param minAccumulation flow accumulation required to initiate a channel
     */
    public ChannelField(double minAccumulation) {
        this.minAccumulation = Math.max(0.0, minAccumulation);
    }

    /**
     * @return the configured minimum accumulation threshold
     */
    public double getMinAccumulation() {
        return minAccumulation;
    }

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
        return getWidth(flowAcc, CHANNEL_INITIATION_FLOW);
    }

    /**
     * Saturated channel corridor width W(A_f) in blocks with
     * configurable initiation threshold (TECHSPEC_AMEND001 A3.1).
     *
     * @param flowAcc flow accumulation proxy A_f
     * @param minAccumulation minimum accumulation threshold
     * @return corridor width in blocks, 0 below the threshold
     */
    public static double getWidth(double flowAcc, double minAccumulation) {
        if (flowAcc < minAccumulation) return 0.0;
        double strength = flowAcc - minAccumulation;
        return 4.0 + 24.0 * (1.0 - Math.exp(-strength * 0.024));
    }

    /**
     * Instance method: channel width with configured threshold.
     *
     * @param flowAcc flow accumulation proxy A_f
     * @return corridor width in blocks, 0 below the threshold
     */
    public double channelWidth(double flowAcc) {
        return getWidth(flowAcc, this.minAccumulation);
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
        return getChannelProfileFactor(flowAcc, distanceToCenterline, CHANNEL_INITIATION_FLOW);
    }

    /**
     * Cross-sectional channel profile factor with configurable threshold.
     *
     * @param flowAcc flow accumulation proxy A_f
     * @param distanceToCenterline horizontal distance to the thalweg in blocks
     * @param minAccumulation minimum accumulation threshold
     * @return profile factor in [0.0, 1.0]
     */
    public static double getChannelProfileFactor(double flowAcc, double distanceToCenterline,
                                                  double minAccumulation) {
        return corridorFactor(getWidth(flowAcc, minAccumulation) * 0.5, distanceToCenterline);
    }

    /**
     * Instance method: channel profile factor with configured threshold.
     *
     * @param flowAcc flow accumulation proxy A_f
     * @param distanceToCenterline horizontal distance to the thalweg in blocks
     * @return profile factor in [0.0, 1.0]
     */
    public double channelProfileFactor(double flowAcc, double distanceToCenterline) {
        return getChannelProfileFactor(flowAcc, distanceToCenterline, this.minAccumulation);
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

    /**
     * Returns true if the location is within the channel (M_channel mask).
     *
     * @param flowAcc flow accumulation at location
     * @return true if within channel mask
     */
    public boolean inChannelMask(double flowAcc) {
        return flowAcc >= minAccumulation;
    }

    /**
     * Returns true if the location should be realized as a channel
     * (M_realize mask) based on the final channel geometry.
     *
     * @param flowAcc flow accumulation at location
     * @return true if within realization mask
     */
    public boolean inRealizeMask(double flowAcc) {
        // M_realize is a subset of M_channel: only realize channels that
        // are wide enough and have sufficient accumulation
        return inChannelMask(flowAcc) && channelWidth(flowAcc) > 0.0;
    }
}
