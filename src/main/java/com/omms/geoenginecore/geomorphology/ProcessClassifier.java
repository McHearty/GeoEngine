package com.omms.geoenginecore.geomorphology;

public final class ProcessClassifier {
    private ProcessClassifier() {}

    public static int classify(double riverIncision, LandformType shape, double humidity, double altitude, 
                                double seaLevel, double temp, double tectonic, double slope, double erosion) {
        int bits = 0;
        if (riverIncision > 1.5 || (shape == LandformType.VALLEY && humidity > 0.4)) {
            bits |= LandformBits.PROCESS_FLUVIAL;
        }
        if (altitude > seaLevel + 280.0 && temp < 0.25) {
            bits |= LandformBits.PROCESS_GLACIAL;
        }
        if (tectonic > 220.0) {
            bits |= LandformBits.PROCESS_TECTONIC;
        }
        if (temp > 0.65 && humidity < 0.25 && slope < 0.2) {
            bits |= LandformBits.PROCESS_AEOLIAN;
        }
        if (erosion > 20.0) {
            bits |= LandformBits.PROCESS_EROSIONAL;
        }
        if (altitude < seaLevel + 6.0 && altitude > seaLevel - 8.0) {
            bits |= LandformBits.PROCESS_COASTAL;
        }
        return bits;
    }
}
