package com.omms.geoenginecore.structure;

import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.geomorphology.LandformType;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;

public final class StructureSuitabilityField {
    private final int seaLevel;

    public static final class FoundationReport {
        public double settlementScore;
        public boolean isCliffEdge;
        public boolean isCaveBreachRisk;
        public boolean isFloodRisk;
        public boolean isSuitableForVillage;
        public boolean isSuitableForOutpost;
    }

    public StructureSuitabilityField(GeoConfig config) {
        this.seaLevel = config.seaLevel();
    }

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
