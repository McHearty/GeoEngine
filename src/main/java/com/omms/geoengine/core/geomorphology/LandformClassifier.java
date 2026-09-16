package com.geoengine.core.geomorphology;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;

/**
 * Classifies terrain samples into landform shapes, geomorphological processes, and environmental
 * regions.
 *
 * <p>Shape classification is derived from the local surface Hessian and gradient. Process and
 * environment flags are then assigned from the resulting shape together with the sample's
 * terrain, climate, and erosion properties.
 */
public final class LandformClassifier {
    /** Curvature magnitude below which a Hessian eigenvalue is treated as approximately zero. */
    private static final double EPSILON_CURVATURE = 0.008;

    /** Maximum gradient magnitude considered sufficiently flat for flat-surface classification. */
    private static final double FLAT_SLOPE_THRESHOLD = 0.05;

    /** Gradient magnitude above which a valley may be classified as a canyon. */
    private static final double CANYON_SLOPE_THRESHOLD = 0.65;

    private final GeoConfig config;

    /**
     * Reusable curvature output for classification.
     *
     * <p>The classifier is stateful because this object is reused rather than allocating a new
     * curvature result for every sample.
     */
    private final HessianSolver.CurvatureResult curvatureScratch =
        new HessianSolver.CurvatureResult();

    /**
     * Creates a landform classifier using the supplied terrain configuration.
     *
     * @param config terrain-generation configuration
     */
    public LandformClassifier(GeoConfig config) {
        this.config = config;
    }

    /**
     * Classifies a terrain sample using its local surface geometry and physical attributes.
     *
     * <p>The supplied neighborhood heights are used to compute the local Hessian. Its eigenvalues
     * determine the primary landform shape, after which process and environmental flags are
     * derived from the shape and the sample's terrain and climate values. The resulting bitfield
     * is stored in {@code sample.classificationBits}.
     *
     * @param sample terrain sample to classify
     * @param hC center height
     * @param hN north-neighbor height
     * @param hS south-neighbor height
     * @param hW west-neighbor height
     * @param hE east-neighbor height
     * @param hNW northwest-neighbor height
     * @param hNE northeast-neighbor height
     * @param hSW southwest-neighbor height
     * @param hSE southeast-neighbor height
     * @param delta horizontal sample spacing
     * @return the complete landform classification bitfield
     */
    public int classify(
        GeoSample sample,
        double hC, double hN, double hS, double hW, double hE,
        double hNW, double hNE, double hSW, double hSE,
        double delta
    ) {
        int bits = 0;

        HessianSolver.solve(
            hC, hN, hS, hW, hE, hNW, hNE, hSW, hSE, delta, curvatureScratch
        );
        double l1 = curvatureScratch.lambda1;
        double l2 = curvatureScratch.lambda2;
        double slope = sample.gradMagnitude;
        double altitude = sample.finalSurface;
        double seaLevel = config.seaLevel();

        int shape;
        if (Math.abs(l1) < EPSILON_CURVATURE
            && Math.abs(l2) < EPSILON_CURVATURE
            && slope < FLAT_SLOPE_THRESHOLD) {
            if (altitude > seaLevel + 180.0) {
                shape = LandformBits.SHAPE_PLATEAU;
            } else {
                shape = LandformBits.SHAPE_FLAT;
            }
        } else if (l1 < -EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
            shape = LandformBits.SHAPE_PEAK;
        } else if (l1 > EPSILON_CURVATURE && l2 > EPSILON_CURVATURE) {
            shape = LandformBits.SHAPE_BASIN;
        } else if (l1 >= -EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
            shape = LandformBits.SHAPE_RIDGE;
        } else if (l1 > EPSILON_CURVATURE && l2 <= EPSILON_CURVATURE) {
            if (slope > CANYON_SLOPE_THRESHOLD && sample.riverIncision > 12.0) {
                shape = LandformBits.SHAPE_CANYON;
            } else {
                shape = LandformBits.SHAPE_VALLEY;
            }
        } else if (l1 > EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
            shape = LandformBits.SHAPE_SADDLE;
        } else {
            shape =
                (slope < FLAT_SLOPE_THRESHOLD)
                    ? LandformBits.SHAPE_FLAT
                    : LandformBits.SHAPE_UNKNOWN;
        }
        bits = LandformBits.setShape(bits, shape);

        if (sample.riverIncision > 1.5
            || (shape == LandformBits.SHAPE_VALLEY && sample.humidity > 0.4)) {
            bits |= LandformBits.PROCESS_FLUVIAL;
        }
        if (altitude > seaLevel + 280.0 && sample.temperature < 0.25) {
            bits |= LandformBits.PROCESS_GLACIAL;
        }
        if (sample.rawTectonic > 220.0) {
            bits |= LandformBits.PROCESS_TECTONIC;
        }
        if (sample.temperature > 0.65 && sample.humidity < 0.25 && slope < 0.2) {
            bits |= LandformBits.PROCESS_AEOLIAN;
        }
        if (sample.erosionLowering > 20.0) {
            bits |= LandformBits.PROCESS_EROSIONAL;
        }

        if (altitude < seaLevel) {
            bits |= LandformBits.ENV_SUBMARINE;
        } else if (altitude <= seaLevel + 6.0) {
            bits |= LandformBits.ENV_COASTAL;
        } else if (altitude < seaLevel + 120.0) {
            bits |= LandformBits.ENV_LOWLAND;
        } else if (altitude < seaLevel + 260.0) {
            bits |= LandformBits.ENV_HIGHLAND;
        } else {
            bits |= LandformBits.ENV_ALPINE;
        }

        sample.classificationBits = bits;
        return bits;
    }
}
