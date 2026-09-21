package com.omms.geoenginecore.geomorphology;

/**
 * Geomorphic process flag evaluation (TECHSPEC §97, §98).
 *
 * <p>Derives the process flags from the pipeline's own field
 * results — incision, shape, climate, altitude, tectonic uplift,
 * slope, and lowering — so process labels describe what actually
 * shaped the terrain, not which noise stream produced it.
 */
public final class ProcessClassifier {
    /** Hides the implicit constructor. This is a static utility class. */
    private ProcessClassifier() {}

    /**
     * @param riverIncision channel incision R
     * @param shape resolved landform type
     * @param humidity normalized humidity
     * @param altitude final surface H_f
     * @param seaLevel dimension sea level
     * @param temp normalized temperature
     * @param tectonic raw tectonic relief T
     * @param slope |∇H|
     * @param erosion long-term lowering E
     * @return packed process flags
     */
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
