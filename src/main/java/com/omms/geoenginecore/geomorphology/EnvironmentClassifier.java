package com.omms.geoenginecore.geomorphology;

/**
 * Vertical environment bands (TECHSPEC §98).
 *
 * <p>Classifies a column into one of five environments relative to
 * sea level: SUBMARINE, COASTAL (0..+6), LOWLAND (+6..+120),
 * HIGHLAND (+120..+260), ALPINE (above +260).
 */
public final class EnvironmentClassifier {
    /** Hides the implicit constructor. This is a static utility class. */
    private EnvironmentClassifier() {}

    /**
     * @param altitude final surface H_f of the column
     * @param seaLevel dimension sea level
     * @return the matching {@link LandformBits} environment flag
     */
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
