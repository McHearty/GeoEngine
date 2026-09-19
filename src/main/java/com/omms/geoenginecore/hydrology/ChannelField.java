package com.omms.geoenginecore.hydrology;

public final class ChannelField {
    public static final double CHANNEL_INITIATION_FLOW = 2.5; // Flow accumulation required to form a channel

    private ChannelField() {}

    /**
     * Saturated channel corridor width W(Af) in blocks (§29).
     * Brooks: ~3-5 blocks wide; Trunk rivers: ~16-28 blocks wide.
     */
    public static double getWidth(double flowAcc) {
        if (flowAcc < CHANNEL_INITIATION_FLOW) return 0.0;
        double strength = flowAcc - CHANNEL_INITIATION_FLOW;
        return 3.0 + 25.0 * (1.0 - Math.exp(-strength * 0.18));
    }

    /**
     * Evaluates the cross-sectional channel profile factor F_channel in [0.0, 1.0] (§28).
     * Centerline (thalweg) = 1.0; Channel banks = 0.0; Outside corridor = 0.0.
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
