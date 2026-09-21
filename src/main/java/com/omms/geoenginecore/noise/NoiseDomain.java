package com.omms.geoenginecore.noise;

/**
 * Deterministic salt for each field family's seed domain
 * (TECHSPEC §9).
 *
 * <p>Every major field derives its noise seed from (world seed,
 * dimension, domain salt, generator version), so no two fields share
 * a raw noise stream merely because the same world seed is available.
 * The constants form a SplitMix64-derived salt table.
 */
public enum NoiseDomain {
    /** Very-low-frequency continental tectonic base band. */
    TECTONIC_BASE(0x9E3779B97F4A7C15L),
    /** Primary mountain-belt tectonic detail band. */
    TECTONIC_DETAIL_A(0xBF58476D1CE4E5B9L),
    /** Secondary ridge tectonic detail band. */
    TECTONIC_DETAIL_B(0x94D049BB133111EBL),
    /** Horizontal X component of the anisotropic stress warp. */
    STRESS_X(0xA0761D6478BD642FL),
    /** Horizontal Z component of the anisotropic stress warp. */
    STRESS_Z(0xE7037ED1A0B428DBL),
    /** Geological epoch (age) field. */
    EPOCH(0x8CBCEB690A87A5E3L),
    /** Horizontal temperature field. */
    CLIMATE_TEMP(0x1B8735932C4A78F5L),
    /** Horizontal humidity field. */
    CLIMATE_HUMID(0x27D4EB2F165667C5L),
    /** Long-term erosion field. */
    EROSION(0x517CC1B727220A95L),
    /** Hydrology fields: drainage, rivers, channels. */
    HYDROLOGY(0x3C6EF372FE94F82BL),
    /** Deposition fields: basin, fluvial, coastal, aeolian. */
    DEPOSITION(0x62E07BB30125EFD3L),
    /** Volumetric 3-D rock warp. */
    WARP(0x73C5AE89130B62E9L),
    /** Volumetric cave void field. */
    CAVE(0x854378A52140A7C1L);

    /** Domain salt mixed into the derived field seed. */
    private final long salt;

    /**
     * @param salt domain salt mixed into the derived field seed
     */
    NoiseDomain(long salt) {
        this.salt = salt;
    }

    /**
     * @return the domain salt for seed derivation
     */
    public long getSalt() {
        return salt;
    }
}
