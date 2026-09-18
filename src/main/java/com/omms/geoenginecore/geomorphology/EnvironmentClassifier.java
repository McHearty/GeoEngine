package com.omms.geoenginecore.geomorphology;

public final class EnvironmentClassifier {
    private EnvironmentClassifier() {}

    public static int classify(double altitude, double seaLevel) {
        if (altitude < seaLevel) {
            return LandformBits.ENV_SUBMARINE;
        } else if (altitude <= seaLevel + 6.0) {
            return LandformBits.ENV_COASTAL;
        } else if (altitude < seaLevel + 120.0) {
            return LandformBits.ENV_LOWLAND;
        } else if (altitude < seaLevel + 260.0) {
            return LandformBits.ENV_HIGHLAND;
        } else {
            return LandformBits.ENV_ALPINE;
        }
    }
}
