package com.geoengine.core.structure;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;

public final class StructureSuitabilityField {
    private final int seaLevel;

    public static final class FoundationReport {
        public double settlementScore;
        public boolean isCaveBreachRisk;
        public boolean isFloodRisk;
        public boolean isCliffEdge;
    }

    public StructureSuitabilityField(GeoConfig config) {
        this.seaLevel = config.seaLevel();
    }

    public void evaluateSuitability(GeoSample sample, double caveVoidAtFoundation, FoundationReport out) {
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
