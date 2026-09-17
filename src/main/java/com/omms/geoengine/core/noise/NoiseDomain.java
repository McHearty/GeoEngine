package com.geoengine.core.noise;

/**
 * Independent noise domains used by the terrain-generation pipeline.
 *
 * <p>Each domain supplies a distinct deterministic salt for seed derivation, allowing separate
 * terrain fields to use independent derived seeds while remaining reproducible for the same world
 * seed, dimension, and generator version.
 */
public enum NoiseDomain {

    /** Base tectonic field. */
    TECTONIC_BASE(0x9E3779B97F4A7C15L),

    /** First detailed tectonic field. */
    TECTONIC_DETAIL_A(0xBF58476D1CE4E5B9L),

    /** Second detailed tectonic field. */
    TECTONIC_DETAIL_B(0x94D049BB133111EBL),

    /** X-axis stress field. */
    STRESS_X(0xA0761D6478BD642FL),

    /** Z-axis stress field. */
    STRESS_Z(0xE7037ED1A0B428DBL),

    /** Geological epoch field. */
    EPOCH(0x8CBCEB690A87A5E3L),

    /** Climate temperature field. */
    CLIMATE_TEMP(0x1B8735932C4A78F5L),

    /** Climate humidity field. */
    CLIMATE_HUMID(0x27D4EB2F165667C5L),

    /** Erosion field. */
    EROSION(0x517CC1B727220A95L),

    /** Hydrology field. */
    HYDROLOGY(0x3C6EF372FE94F82BL),

    /** Deposition field. */
    DEPOSITION(0x62E07BB30125EFD3L),

    /** Terrain-warp field. */
    WARP(0x73C5AE89130B62E9L),

    /** Cave-generation field. */
    CAVE(0x854378A52140A7C1L);

    private final long salt;

    NoiseDomain(long salt) {
        this.salt = salt;
    }

    /**
     * Returns the deterministic salt assigned to this noise domain.
     *
     * @return domain-specific seed-derivation salt
     */
    public long getSalt() {
        return salt;
    }
}
