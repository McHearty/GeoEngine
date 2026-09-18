package com.omms.geoenginecore.geomorphology;

public final class ShapeClassifier {
    private static final double EPSILON_CURVATURE = 0.008;
    private static final double FLAT_SLOPE = 0.05;
    private static final double CANYON_SLOPE = 0.65;

    private ShapeClassifier() {}

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
