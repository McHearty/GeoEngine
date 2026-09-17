package com.geoengine.core.structure;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;

/**
 * Evaluates terrain suitability for structure foundations.
 *
 * <p>Suitability combines local flatness and curvature with penalties for detected cave-breach and
 * flood conditions. The evaluation also records threshold-based terrain indicators in a
 * {@link FoundationReport}.
 */
public final class StructureSuitabilityField {

    /** Sea-level reference used by the flood-risk threshold. */
    private final int seaLevel;

    /**
     * Mutable output containing the foundation suitability score and detected terrain conditions.
     *
     * <p>The boolean fields represent conditions detected by fixed thresholds; they are not
     * probability estimates. The settlement score is the product of the flatness, curvature,
     * cave-safety, and flood-safety factors.
     */
    public static final class FoundationReport {

        /** Combined foundation suitability score produced by the evaluation. */
        public double settlementScore;

        /** Whether the foundation location exceeds the configured cave-void threshold. */
        public boolean isCaveBreachRisk;

        /** Whether the location is below the configured elevation or river-incision threshold. */
        public boolean isFloodRisk;

        /** Whether the local slope exceeds the configured cliff-edge threshold. */
        public boolean isCliffEdge;
    }

    /**
     * Creates a structure-suitability evaluator using the configured sea level.
     *
     * @param config terrain configuration providing the sea-level reference
     */
    public StructureSuitabilityField(GeoConfig config) {
        this.seaLevel = config.seaLevel();
    }

    /**
     * Evaluates foundation suitability for a terrain sample.
     *
     * <p>The evaluation derives a flatness factor from slope magnitude, penalizes strong local
     * curvature, and applies fixed safety penalties when a cave void or flood condition is
     * detected. The resulting score and condition indicators are written to {@code out}.
     *
     * @param sample evaluated terrain sample containing slope, curvature, elevation, and river data
     * @param caveVoidAtFoundation cave-void value at the proposed foundation location
     * @param out mutable report receiving the evaluation results
     */
    public void evaluateSuitability(
        GeoSample sample,
        double caveVoidAtFoundation,
        FoundationReport out
    ) {
        double slope = sample.gradMagnitude;
        double laplacian = Math.abs(sample.laplacian);
        double altitude = sample.finalSurface;

        double flatness = 1.0 / (1.0 + (slope * 8.0));
        out.isCliffEdge = slope > 0.28;

        double curvaturePenalty = Math.clamp(laplacian * 4.0, 0.0, 1.0);
        double curvatureFactor = 1.0 - curvaturePenalty;

        out.isCaveBreachRisk = caveVoidAtFoundation > 1.5;
        double caveSafetyFactor = out.isCaveBreachRisk ? 0.05 : 1.0;

        double clearanceAboveSea = altitude - seaLevel;
        out.isFloodRisk = clearanceAboveSea < 2.0 || sample.riverIncision > 6.0;
        double floodFactor = out.isFloodRisk ? 0.1 : 1.0;

        out.settlementScore = flatness * curvatureFactor * caveSafetyFactor * floodFactor;
    }
}
