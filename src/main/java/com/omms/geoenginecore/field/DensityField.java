package com.omms.geoenginecore.field;

/**
 * Canonical terrain density D = H_f − (y + W) − C and its
 * invariants (TECHSPEC §49-§50).
 *
 * <p>D &gt; 0 means solid, D ≤ 0 means air. With W = 0 and C = 0 the
 * expression reduces to H_f − y, so ∂D/∂y = −1; any scalar
 * implementation violating that property with W/C disabled is
 * incorrect.
 */
public final class DensityField {
    /** Hides the implicit constructor. This is a static utility class. */
    private DensityField() {}

    /**
     * Evaluates the canonical density (TECHSPEC §49). Degenerate
     * NaN/Infinity inputs resolve to air.
     *
     * @param hf final surface H_f of the column
     * @param worldY absolute world Y of the voxel
     * @param warp volumetric warp W at the voxel
     * @param cave cave void C at the voxel
     * @return terrain density for the voxel
     */
    public static float evaluate(double hf, int worldY, double warp, double cave) {
        double density = hf - ((double) worldY + warp) - cave;
        if (Double.isNaN(density) || Double.isInfinite(density)) {
            return -1.0f;
        }
        return (float) density;
    }

    /**
     * Checks the density monotonicity invariant: with warp and cave
     * disabled, the density difference between two Y values must
     * equal the Y difference exactly (TECHSPEC §50).
     *
     * @param hf final surface H_f of the column
     * @param y1 world Y of the first sample
     * @param y2 world Y of the second sample
     * @return true when the invariant holds within 1e-5
     */
    public static boolean verifyMonotonicity(double hf, int y1, int y2) {
        float d1 = evaluate(hf, y1, 0.0, 0.0);
        float d2 = evaluate(hf, y2, 0.0, 0.0);
        return Math.abs((d2 - d1) - (y1 - y2)) < 1e-5;
    }
}
