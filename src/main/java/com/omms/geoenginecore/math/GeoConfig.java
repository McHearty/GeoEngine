package com.omms.geoenginecore.math;

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
    int surfaceBandRadius,
    double riverMaxIncision,
    double riverChannelSteepness,
    int caveMinY,
    int caveMaxY
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
            throw new IllegalArgumentException("Stress warp exceeds Jacobian bound: " + maxJac + " > " + stressMaxJacobian);
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
        if (riverMaxIncision < 0.0 || riverChannelSteepness <= 0.0) {
            throw new IllegalArgumentException("Invalid river parameters");
        }
    }

    public long configHash() {
        long h = Double.doubleToLongBits(tectonicFreqLow);
        h = 31 * h + Double.doubleToLongBits(tectonicAmpA);
        h = 31 * h + Double.doubleToLongBits(stressFrequency);
        h = 31 * h + Double.doubleToLongBits(stressAmplitude);
        h = 31 * h + Double.doubleToLongBits(baseErosionRate);
        h = 31 * h + Double.doubleToLongBits(riverMaxIncision);
        h = 31 * h + worldMinY;
        h = 31 * h + worldMaxY;
        h = 31 * h + seaLevel;
        h = 31 * h + generatorVersion;
        return h ^ (h >>> 32);
    }

    public static GeoConfig defaultOverworld(int version) {
        return new GeoConfig(
            version, 
            0,                  // dimensionId: 0 (Overworld)
            -64,                // worldMinY
            1984,               // worldMaxY
            64,                 // seaLevel
            0.0003,             // tectonicFreqLow: Continental wavelength ~3300m
            0.0008,             // tectonicFreqA: Primary mountain belt wavelength ~1250m
            0.0016,             // tectonicFreqB: Secondary ridge wavelength ~625m
            160.0,              // tectonicAmpLow: Continental base amplitude
            160.0,              // tectonicAmpA: Primary belt relief amplitude
            50.0,               // tectonicAmpB: Foothill ridge amplitude
            2.2,                // upliftExponent: p=2.2 isolates peaks and flattens valleys
            0.0005,             // stressFrequency: Broad horizontal stress warp
            24.0,               // stressAmplitude: Maximum horizontal coordinate displacement
            0.45,               // stressMaxJacobian
            0.0003,             // epochFrequency: Geological age variation
            0.0004,             // climateTempFrequency
            0.0004,             // climateHumidFrequency
            0.6,                // climateMin
            1.4,                // climateMax
            0.0012,             // lapseRatePerBlock
            35.0,               // baseErosionRate
            5.0,                // maxWarpAmplitude: Natural 3-5 block rock overhangs
            16,                 // surfaceBandRadius
            18.0,               // riverMaxIncision: 3-8m stream valleys, max 18m in canyons
            0.15,               // riverChannelSteepness: Progressive incision along drainage paths
            -40,                // caveMinY
            128                 // caveMaxY
        );
    }
}
