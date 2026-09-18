package com.omms.geoenginecore.test;

import com.omms.geoenginecore.feature.SpecialFeatureDetector;
import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.geomorphology.LandformType;
import com.omms.geoenginecore.material.LithologyField;
import com.omms.geoenginecore.material.RockFamily;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.structure.StructureSuitabilityField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase9FinalContentTest {
    private static final long TEST_SEED = 0x8888444422221111L;
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
    @DisplayName("Structure Placement Gating: Village rejected on sheer cliffs or shallow voids")
    void testStructureFoundationGating() {
        StructureSuitabilityField.FoundationReport report = new StructureSuitabilityField.FoundationReport();
        GeoSample sample = new GeoSample();

        sample.finalSurface = 78.0;
        sample.gradMagnitude = 0.03;
        sample.laplacian = 0.01;
        sample.riverIncision = 0.0;
        sample.classificationBits = LandformBits.setType(0, LandformType.PLAINS);

        suitabilityField.evaluateSuitability(sample, 0.0, report);
        assertTrue(report.isSuitableForVillage);
        assertFalse(report.isCliffEdge);

        suitabilityField.evaluateSuitability(sample, 14.0, report);
        assertFalse(report.isSuitableForVillage);
        assertTrue(report.isCaveBreachRisk);

        sample.gradMagnitude = 0.65;
        suitabilityField.evaluateSuitability(sample, 0.0, report);
        assertFalse(report.isSuitableForVillage);
        assertTrue(report.isCliffEdge);
    }

    @Test
    @DisplayName("Dipping Strata: Continuous horizons across chunk boundary without shearing")
    void testDippingStrataSeamContinuity() {
        int boundaryX1 = 15;
        int boundaryX2 = 16;
        int z = 64;
        int testY = 40;
        double surfaceH = 85.0;

        RockFamily rockA = lithologyField.evaluateLithology(boundaryX1, testY, z, surfaceH);
        RockFamily rockB = lithologyField.evaluateLithology(boundaryX2, testY, z, surfaceH);

        assertNotNull(rockA);
        assertNotNull(rockB);
        assertFalse(rockA == RockFamily.DEEP_DEEPSLATE && rockB == RockFamily.SEDIMENTARY_LIMESTONE);
    }

    @Test
    @DisplayName("Deterministic Feature Trigger: Waterfall Lip Detection")
    void testWaterfallDetection() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = 95.0;
        sample.riverIncision = 4.2;
        sample.gradMagnitude = 0.72;

        SpecialFeatureDetector.FeatureType type = featureDetector.detectFeature(sample, 14.0);
        assertEquals(SpecialFeatureDetector.FeatureType.WATERFALL_CREST, type);
    }

    @Test
    @DisplayName("Deterministic Feature Trigger: Geothermal Magma Vent Detection")
    void testGeothermalVentDetection() {
        GeoSample sample = new GeoSample();
        sample.rawTectonic = 290.0;
        sample.temperature = 0.85;
        sample.humidity = 0.10;
        sample.gradMagnitude = 0.12;
        sample.erosionLowering = 16.0;

        SpecialFeatureDetector.FeatureType type = featureDetector.detectFeature(sample, 0.0);
        assertEquals(SpecialFeatureDetector.FeatureType.GEOTHERMAL_VENT, type);
    }
}
