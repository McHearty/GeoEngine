package com.omms.geoenginecore.hydrology;

public final class ChannelField {
    private ChannelField() {}

    public static double getWidth(double flowAcc) {
        return 2.0 + 40.0 * (1.0 - Math.exp(-flowAcc * 0.15));
    }

    public static double getMeanderOffset(double flowAcc, double slope, double noiseSample) {
        if (slope > 0.35 || flowAcc < 1.0) {
            return 0.0;
        }
        double freedom = Math.clamp(1.0 - (slope / 0.35), 0.0, 1.0);
        return noiseSample * freedom * 16.0;
    }
}
