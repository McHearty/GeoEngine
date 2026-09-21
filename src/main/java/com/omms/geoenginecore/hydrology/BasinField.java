package com.omms.geoenginecore.hydrology;

/**
 * Basin detection and accommodation-space helpers (TECHSPEC §34, §40).
 *
 * <p>A column reads as a depositional basin when it is concave and
 * sits in a region of limited relief. Accommodation space grows with
 * basin depth and saturates as the distance to the rim shrinks, so
 * the deposition budget is always finite. Both tests are
 * deterministic functions of pipeline fields.
 */
public final class BasinField {
    /** Hides the implicit constructor. This is a static utility class. */
    private BasinField() {}

    /**
     * Conservative basin test (TECHSPEC §34).
     *
     * @param laplacian ∇²H of the column
     * @param regionalRelief normalized regional relief magnitude
     * @return true when the column is a plausible depositional basin
     */
    public static boolean isBasin(double laplacian, double regionalRelief) {
        return laplacian > 0.02 && regionalRelief < 0.10;
    }

    /**
     * Available accommodation space (TECHSPEC §40).
     *
     * @param basinDepth basin depth in blocks
     * @param distanceToRim blocks to the nearest basin rim
     * @return accommodation space, ≥ 0
     */
    public static double getAccommodationSpace(double basinDepth, double distanceToRim) {
        return Math.max(0.0, basinDepth * (1.0 - Math.exp(-distanceToRim * 0.02)));
    }
}
