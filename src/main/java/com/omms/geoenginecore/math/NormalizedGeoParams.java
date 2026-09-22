package com.omms.geoenginecore.math;

/**
 * User-facing 0..1 slider parameters of the GeoEngine configuration.
 *
 * <p>Each component is a dimensionless control in [0.0, 1.0] mapped by
 * {@link GeoConfigNormalizer} onto concrete engine values. The compact
 * constructor clamps every component, so a live instance is always
 * valid.
 */
public record NormalizedGeoParams(
    double continentalScale,       // [0.0, 1.0]: Continental plate & ocean size
    double mountainScale,          // [0.0, 1.0]: Wavelength of mountain belts
    double mountainRelief,         // [0.0, 1.0]: Peak elevation above base
    double ridgeRoughness,         // [0.0, 1.0]: Foothill & secondary ridge amplitude
    double valleyFlatness,         // [0.0, 1.0]: Uplift exponent (plains flatness)
    double stressShear,            // [0.0, 1.0]: Tectonic coordinate deformation
    double erosionStrength,        // [0.0, 1.0]: Base surface lowering budget
    double riverIncisionDepth,     // [0.0, 1.0]: Maximum fluvial canyon depth
    double riverIncisionRate,      // [0.0, 1.0]: Drainage accumulation carve rate
    double cliffOverhangIntensity  // [0.0, 1.0]: 3D volumetric rock warp
) {
    /**
     * Clamps every component into [0.0, 1.0].
     *
     * @throws NullPointerException if any component is a boxed null
     */
    public NormalizedGeoParams {
        continentalScale = GeoMath.clamp(continentalScale, 0.0, 1.0);
        mountainScale = GeoMath.clamp(mountainScale, 0.0, 1.0);
        mountainRelief = GeoMath.clamp(mountainRelief, 0.0, 1.0);
        ridgeRoughness = GeoMath.clamp(ridgeRoughness, 0.0, 1.0);
        valleyFlatness = GeoMath.clamp(valleyFlatness, 0.0, 1.0);
        stressShear = GeoMath.clamp(stressShear, 0.0, 1.0);
        erosionStrength = GeoMath.clamp(erosionStrength, 0.0, 1.0);
        riverIncisionDepth = GeoMath.clamp(riverIncisionDepth, 0.0, 1.0);
        riverIncisionRate = GeoMath.clamp(riverIncisionRate, 0.0, 1.0);
        cliffOverhangIntensity = GeoMath.clamp(cliffOverhangIntensity, 0.0, 1.0);
    }

    /**
     * Balanced defaults matching the calibrated Overworld baseline.
     *
     * @return parameter set tuned for the reference Overworld terrain
     */
    public static NormalizedGeoParams defaultOverworld() {
        return new NormalizedGeoParams(
            0.50, // Continental wavelength ~3300m
            0.45, // Mountain belt wavelength ~1250m
            0.35, // Primary relief ~160m
            0.25, // Foothills ~50m
            0.60, // Exponent p=2.2 (flattens valleys, isolates peaks)
            0.35, // Stress warp ~24 blocks
            0.40, // Base erosion ~35m
            0.35, // River incision max 18m
            0.30, // River carve steepness 0.15
            0.35  // Cliff warp ~5 blocks
        );
    }
}
