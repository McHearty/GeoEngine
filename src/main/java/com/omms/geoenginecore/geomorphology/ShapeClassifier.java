package com.omms.geoenginecore.geomorphology;

/**
 * Curvature-based shape grammar (TECHSPEC §95, §101).
 *
 * <p>Maps the 2-D Hessian eigenstructure onto shape families: both
 * eigenvalues near zero and flat → PLATEAU (when elevated) or
 * PLAINS; both negative (convex) → MOUNTAIN; both positive
 * (concave) → BASIN; mixed with the larger eigenvalue non-negative
 * → RIDGE, or CANYON/VALLEY when steep and deeply incised; mixed
 * with the larger negative → SADDLE. The grammar is deterministic.
 */
public final class ShapeClassifier {
    /** Curvature threshold below which an eigenvalue reads as zero. */
    private static final double EPSILON_CURVATURE = 0.008;
    /** |∇H| at or below which a column reads as flat. */
    private static final double FLAT_SLOPE = 0.05;
    /** |∇H| above which a concave channel reads as a canyon. */
    private static final double CANYON_SLOPE = 0.65;

    /** Hides the implicit constructor. This is a static utility class. */
    private ShapeClassifier() {}

    /**
     * Resolves the shape family from the eigenstructure and local
     * context (TECHSPEC §101).
     *
     * @param l1 largest principal curvature
     * @param l2 smallest principal curvature
     * @param slope |∇H|
     * @param altitude final surface H_f
     * @param seaLevel dimension sea level
     * @param incision channel incision R
     * @return resolved shape landform type
     */
    public static LandformType classify(double l1, double l2, double slope, double altitude, double seaLevel, double incision) {
        if (Math.abs(l1) < EPSILON_CURVATURE && Math.abs(l2) < EPSILON_CURVATURE && slope < FLAT_SLOPE) {
            return (altitude > seaLevel + 180.0) ? LandformType.PLATEAU : LandformType.PLAINS;
        }
        if (l1 < -EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
            return LandformType.MOUNTAIN;
        }
        if (l1 > EPSILON_CURVATURE && l2 > EPSILON_CURVATURE) {
            return LandformType.BASIN;
        }
        if (l1 >= -EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
            return LandformType.RIDGE;
        }
        if (l1 > EPSILON_CURVATURE && l2 <= EPSILON_CURVATURE) {
            return (slope > CANYON_SLOPE && incision > 12.0) ? LandformType.CANYON : LandformType.VALLEY;
        }
        if (l1 > EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
            return LandformType.SADDLE;
        }
        return (slope < FLAT_SLOPE) ? LandformType.PLAINS : LandformType.UNKNOWN;
    }
}
