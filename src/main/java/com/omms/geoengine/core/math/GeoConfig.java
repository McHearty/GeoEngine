package com.geoengine.core.math;

public record GeoConfig(
    int generatorVersion,
    int dimensionId,
    int worldMinY,
    int worldMaxY,
    int seaLevel,
    double tectonicFreqLow,
    double tectonicFreqA,
    double tectonicFreqB,
    double tectonicAmpLow,
    double tectonicAmpA,
    double tectonicAmpB,
    double upliftExponent,
    double stressFrequency,
    double stressAmplitude,
    double stressMaxJacobian,
    double epochFrequency,
    double climateTempFrequency,
    double climateHumidFrequency,
    double climateMin,
    double climateMax,
    double lapseRatePerBlock,
    double baseErosionRate,
    double maxWarpAmplitude,
    int surfaceBandRadius
) {
    public GeoConfig {
        if (worldMinY >= worldMaxY) {
            throw new IllegalArgumentException("worldMinY must be strictly less than worldMaxY");
        }
        if (seaLevel < worldMinY || seaLevel > worldMaxY) {
            throw new IllegalArgumentException("seaLevel out of world bounds");
        }
        if (tectonicFreqLow <= 0.0 || tectonicFreqA <= 0.0 || tectonicFreqB <= 0.0) {
            throw new IllegalArgumentException("Tectonic frequencies must be positive");
        }
        if (tectonicAmpLow < 0.0 || tectonicAmpA < 0.0 || tectonicAmpB < 0.0) {
            throw new IllegalArgumentException("Tectonic amplitudes cannot be negative");
        }
        if (upliftExponent < 1.0) {
            throw new IllegalArgumentException("upliftExponent must be >= 1.0 to concentrate relief");
        }
        if (stressFrequency <= 0.0 || stressAmplitude < 0.0) {
            throw new IllegalArgumentException("Invalid stress configuration");
        }
        double maxJac = stressAmplitude * stressFrequency;
        if (maxJac > stressMaxJacobian) {
            throw new IllegalArgumentException("Stress warp parameter exceeds Jacobian bound: " 
                + maxJac + " > " + stressMaxJacobian);
        }
        if (climateMin < 0.0 || climateMax < climateMin) {
            throw new IllegalArgumentException("Invalid climate bounds: 0 <= climateMin <= climateMax");
        }
        if (baseErosionRate < 0.0) {
            throw new IllegalArgumentException("Erosion rate cannot be negative");
        }
        if (surfaceBandRadius <= 0) {
            throw new IllegalArgumentException("surfaceBandRadius must be positive");
        }
        validateFinite(tectonicFreqLow, "tectonicFreqLow");
        validateFinite(tectonicFreqA, "tectonicFreqA");
        validateFinite(tectonicFreqB, "tectonicFreqB");
        validateFinite(stressFrequency, "stressFrequency");
        validateFinite(stressAmplitude, "stressAmplitude");
    }

    private static void validateFinite(double val, String name) {
        if (!Double.isFinite(val)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public static GeoConfig defaultOverworld(int version) {
        return new GeoConfig(
            version, 0, -64, 1984, 64,
            0.0005, 0.0017, 0.0023,
            200.0, 350.0, 150.0,
            1.8,
            0.0007, 32.0, 0.45,
            0.0003,
            0.0004, 0.0004,
            0.6, 1.4,
            0.0012,
            45.0,
            16.0,
            16
        );
    }
}
