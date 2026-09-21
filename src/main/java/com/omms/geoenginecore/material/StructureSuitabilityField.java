package com.omms.geoenginecore.structure;

import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.geomorphology.LandformType;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;

/**
 * Player structure siting analysis (TECHSPEC §213-§214).
 *
 * <p>Computes a settlement score from flatness, curvature, cave
 * breach risk, and flood risk, then gates village and outpost
 * suitability on the score, the landform type, and the risk flags.
 * Every input is a deterministic pipeline field.
 */
public final class StructureSuitabilityField {
    /** Coastline reference: the dimension's sea level. */
    private final int seaLevel;

    /**
     * Reusable siting report (TECHSPEC §66); do not share between
     * threads.
     */
    public static final class FoundationReport {
        /** Composite settlement score in [0, 1]. */
        public double settlementScore;
        /** Column sits on a cliff edge. */
        public boolean isCliffEdge;
        /** Cave void threatens the foundation. */
        public boolean isCaveBreachRisk;
        /** Column is exposed to flooding. */
        public boolean isFloodRisk;
        /** Column meets the village rules. */
        public boolean isSuitableForVillage;
        /** Column meets the outpost rules. */
        public boolean isSuitableForOutpost;
    }

    /**
     * @param config validated configuration supplying the sea level
     */
    public StructureSuitabilityField(GeoConfig config) {
        this.seaLevel = config.seaLevel();
    }

    /**
     * Fills {@code out} for one column (TECHSPEC §213).
     *
     * @param sample pipeline sample of the column
     * @param measuredCaveVoidAtFoundation cave void measured at foundation depth
     * @param out scratch struct to fill
     */
    public void evaluateSuitability(GeoSample sample, double measuredCaveVoidAtFoundation, FoundationReport out) {
        double slope = sample.gradMagnitude;
        double laplacian = Math.abs(sample.laplacian);
        double altitude = sample.finalSurface;

        double flatness = 1.0 / (1.0 + (slope * 8.0));
        out.isCliffEdge = slope > 0.28;

        double curvaturePenalty = Math.clamp(laplacian * 4.0, 0.0, 1.0);
        double curvatureFactor = 1.0 - curvaturePenalty;

        out.isCaveBreachRisk = measuredCaveVoidAtFoundation > 1.2;
        double caveSafety = out.isCaveBreachRisk ? 0.05 : 1.0;

        double clearanceAboveSea = altitude - seaLevel;
        out.isFloodRisk = clearanceAboveSea < 2.0 || sample.riverIncision > 6.0;
        double floodSafety = out.isFloodRisk ? 0.1 : 1.0;

        out.settlementScore = flatness * curvatureFactor * caveSafety * floodSafety;

        LandformType type = LandformBits.getType(sample.classificationBits);
        boolean flatTableland = (type == LandformType.PLAINS || type == LandformType.PLATEAU || type == LandformType.MESA);

        out.isSuitableForVillage = out.settlementScore > 0.65 
            && flatTableland 
            && !out.isCliffEdge 
            && !out.isCaveBreachRisk 
            && !out.isFloodRisk;

        out.isSuitableForOutpost = (type == LandformType.HILL || type == LandformType.RIDGE || type == LandformType.PLATEAU)
            && slope < 0.40 
            && !out.isCaveBreachRisk;
    }
}
