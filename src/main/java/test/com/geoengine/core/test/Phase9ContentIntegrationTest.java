package com.geoengine.core.test;

import com.geoengine.core.feature.SpecialFeatureDetector;
import com.geoengine.core.material.LithologyField;
import com.geoengine.core.material.RockFamily;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;
import com.geoengine.core.structure.StructureSuitabilityField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase9ContentIntegrationTest {
    private static final long TEST_SEED = 0x9876543210ABCDEFL;
    private GeoConfig config;
    private StructureSuitabilityField suitabilityField;
    private LithologyField lithologyField;
    private SpecialFeatureDetector featureDetector;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        suitabilityField = new StructureSuitabilityField(config);
        lithologyField = new LithologyField(TEST_SEED, config);
        featureDetector = new SpecialFeatureDetector(config);
    }

    @Test
    @DisplayName("Foundation Suitability: Cliff and Void Safety Gating (§135, §136)")
    void testStructureFoundationSafety() {
        StructureSuitabilityField.FoundationReport report = new StructureSuitabilityField.FoundationReport();
        GeoSample sample = new GeoSample();

        sample.finalSurface = 72.0;
        sample.gradMagnitude = 0.04;
        sample.laplacian = 0.01;
        sample.riverIncision = 0.0;

        suitabilityField.evaluateSuitability(sample, 0.0, report);
        assertTrue(report.settlementScore > 0.70);
        assertFalse(report.isCliffEdge);
        assertFalse(report.isCaveBreachRisk);
        assertFalse(report.isFloodRisk);

        sample.gradMagnitude = 0.85;
        suitabilityField.evaluateSuitability(sample, 12.0, report);
        assertTrue(report.isCliffEdge);
        assertTrue(report.isCaveBreachRisk);
        assertTrue(report.settlementScore < 0.10);
    }

    @Test
    @DisplayName("Lithology Stratification: Continuous Strata & Deepslate Transition (§189)")
    void testLithologyBanding() {
        int x = 64;
        int z = -128;
        double surfaceH = 90.0;

        RockFamily deepRock = lithologyField.evaluateLithology(x, -20, z, surfaceH);
        assertEquals(RockFamily.DEEP_DEEPSLATE, deepRock);

        RockFamily upperRock = lithologyField.evaluateLithology(x, 60, z, surfaceH);
        assertNotNull(upperRock);
        assertNotEquals(RockFamily.DEEP_DEEPSLATE, upperRock);
    }

    @Test
    @DisplayName("Feature Detection: Waterfall Plunge Verification (§201)")
    void testWaterfallDetection() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = 85.0;
        sample.riverIncision = 4.5;
        sample.gradMagnitude = 0.75;

        SpecialFeatureDetector.FeatureType type = featureDetector.detectFeature(sample, 12.0);
        assertEquals(SpecialFeatureDetector.FeatureType.WATERFALL_CREST, type);
    }
}
