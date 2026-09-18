package com.omms.geoenginecore.math;

public final class GeoConfigNormalizer {
    private GeoConfigNormalizer() {}

    public static GeoConfig toGeoConfig(
        int version, int dimensionId, int worldMinY, int worldMaxY, int seaLevel,
        NormalizedGeoParams p
    ) {
        // Continental scale: wavelength 2000m to 8000m
        double continentalWavelength = 2000.0 + (p.continentalScale() * 6000.0);
        double fLow = 1.0 / continentalWavelength;

        // Mountain belts: wavelength 800m to 2500m
        double mountainWavelength = 800.0 + (p.mountainScale() * 1700.0);
        double fA = 1.0 / mountainWavelength;

        // Foothills: wavelength 400m to 1200m
        double ridgeWavelength = 400.0 + (p.mountainScale() * 800.0);
        double fB = 1.0 / ridgeWavelength;

        // Continental base: ±35m to ±85m around sea level
        double ampLow = 35.0 + (p.continentalScale() * 50.0);

        // Primary Mountain Relief: Scales from 180m up to 1400m for 2048-block world height
        double ampA = 180.0 + (p.mountainRelief() * 1220.0);
        double ampB = 30.0 + (p.ridgeRoughness() * 220.0);

        // Valley flatness exponent
        double upliftExponent = 1.4 + (p.valleyFlatness() * 1.2); // 1.4 to 2.6

        // Stress Warp
        double stressWavelength = 1400.0 + (p.continentalScale() * 2000.0);
        double stressFreq = 1.0 / stressWavelength;
        double maxAllowedAmp = 0.45 / stressFreq;
        double stressAmp = Math.min(maxAllowedAmp * 0.90, p.stressShear() * 32.0);

        // Erosion & Incision
        double baseErosion = 15.0 + (p.erosionStrength() * 45.0);
        double maxIncision = 8.0 + (p.riverIncisionDepth() * 24.0); // 8m to 32m
        double channelSteepness = 0.08 + (p.riverIncisionRate() * 0.20);

        // Volumetric 3D Cliff Warp: max 6 blocks
        double maxWarp = p.cliffOverhangIntensity() * 6.0;

        return new GeoConfig(
            version,
            dimensionId,
            worldMinY,
            worldMaxY,
            seaLevel,
            fLow,
            fA,
            fB,
            ampLow,
            ampA,
            ampB,
            upliftExponent,
            stressFreq,
            stressAmp,
            0.45,
            0.0003,
            0.0004,
            0.0004,
            0.6,
            1.4,
            0.0012,
            baseErosion,
            maxWarp,
            16,
            maxIncision,
            channelSteepness,
            -40,
            128
        );
    }
}
