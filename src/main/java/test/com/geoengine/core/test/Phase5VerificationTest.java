package com.geoengine.core.test;

import com.geoengine.core.geomorphology.FeatureEligibility;
import com.geoengine.core.geomorphology.HessianSolver;
import com.geoengine.core.geomorphology.LandformBits;
import com.geoengine.core.geomorphology.LandformClassifier;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase5VerificationTest {
    private GeoConfig config;
    private LandformClassifier classifier;
    private FeatureEligibility eligibility;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        classifier = new LandformClassifier(config);
        eligibility = new FeatureEligibility(config);
    }

    @Test
    @DisplayName("Hessian Solver: Analytical Quadratic Surfaces")
    void testHessianEigenvalueExtraction() {
        HessianSolver.CurvatureResult res = new HessianSolver.CurvatureResult();

        double delta = 1.0;
        double hC = 0.0;
        double hN = 1.0, hS = 1.0, hW = 1.0, hE = 1.0;
        double hNW = 2.0, hNE = 2.0, hSW = 2.0, hSE = 2.0;

        HessianSolver.solve(hC, hN, hS, hW, hE, hNW, hNE, hSW, hSE, delta, res);
        assertEquals(2.0, res.lambda1, 1e-6);
        assertEquals(2.0, res.lambda2, 1e-6);
        assertTrue(res.gaussianCurv > 0.0);

        hC = 0.0;
        hW = -1.0; hE = -1.0; hN = 0.0; hS = 0.0;
        hNW = -1.0; hNE = -1.0; hSW = -1.0; hSE = -1.0;

        HessianSolver.solve(hC, hN, hS, hW, hE, hNW, hNE, hSW, hSE, delta, res);
        assertEquals(0.0, res.lambda1, 1e-6);
        assertEquals(-2.0, res.lambda2, 1e-6);
    }

    @Test
    @DisplayName("Landform Classification: Shape and Process Separation")
    void testLandformClassificationGrammar() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = 80.0;
        sample.gradMagnitude = 0.01;
        sample.riverIncision = 0.0;
        sample.erosionLowering = 2.0;
        sample.temperature = 0.5;
        sample.humidity = 0.5;

        int bits = classifier.classify(sample, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 2.0);
        int shape = LandformBits.getShape(bits);
        assertEquals(LandformBits.SHAPE_FLAT, shape);
        assertTrue(LandformBits.hasEnvironment(bits, LandformBits.ENV_LOWLAND));
    }

    @Test
    @DisplayName("Feature Eligibility: Structure Foundation Constraint Satisfaction")
    void testStructureEligibility() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = 70.0;
        sample.gradMagnitude = 0.05;
        sample.classificationBits = LandformBits.setShape(0, LandformBits.SHAPE_FLAT);

        assertTrue(eligibility.isStructureFoundable(sample));

        sample.gradMagnitude = 0.85;
        assertFalse(eligibility.isStructureFoundable(sample));
    }
}
