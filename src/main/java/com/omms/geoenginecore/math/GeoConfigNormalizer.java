package com.omms.geoenginecore.math;

/**
 * Maps unit-range {@link NormalizedGeoParams} sliders onto concrete
 * {@link GeoConfig} values (TECHSPEC §63).
 *
 * <p>Every parameter is derived from a documented, bounded linear
 * sweep so that user-facing 0..1 sliders can never produce an invalid
 * configuration. The stress amplitude is additionally clamped so the
 * Jacobian bound of 0.45 is never exceeded (TECHSPEC §16).
 */
public final class GeoConfigNormalizer {
    /** Hides the implicit constructor. This is a static utility class. */
    private GeoConfigNormalizer() {}

    /**
     * Converts normalized 0..1 parameters into a validated
     * {@link GeoConfig}.
     *
     * <p>Wavelength sweeps: continental 2000-8000 m, mountain belts
     * 800-2500 m, foothills 400-1200 m. Amplitude sweeps: continental
     * base ±35-85 m, primary relief 180-1400 m, secondary ridges
     * 30-250 m. The returned instance is guaranteed to pass
     * {@link GeoConfig} validation.
     *
     * @param version generator version stored in the configuration
     * @param dimensionId dimension identity for seed-domain separation
     * @param worldMinY inclusive lowest buildable Y of the dimension
     * @param worldMaxY exclusive upper Y bound of the dimension
     * @param seaLevel sea/fluid level of the dimension
     * @param p normalized 0..1 parameter set
     * @return validated GeoConfig built from the normalized parameters
     * @throws IllegalArgumentException if the vertical bounds are inconsistent
     */
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
