package com.omms.geoenginecore.test.phase5;

import com.omms.geoenginecore.geomorphology.*;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-5 landform grammar (TECHSPEC §170-§181): scale
 * discrimination, priority override, compound classification, and
 * curvature/prominence gates.
 */
public class Phase5LandformGrammarTest {
    /** Default Overworld configuration. */
    private GeoConfig config;
    /** Scalar field kernel. */
    private ScalarFieldKernel kernel;
    /** Landform classifier. */
    private LandformClassifier classifier;

    /**
     * Installs the shared test fixtures.
     */
    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(0x5555AAAA5555AAAAL, config);
        classifier = new LandformClassifier(config);
    }

    /**
     * Flat 32-block neighborhood + low slope + low elevation →
     * PLATEAU/PLAINS, not mesa/butte (TECHSPEC §181).
     */
    @Test
    @DisplayName("Multi-Scale Discrimination: Butte vs Mesa vs Plateau")
    void testTablelandScaleDiscrimination() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = 220.0;
        sample.gradMagnitude = 0.02;

        int bits = classifier.classify(kernel, sample, 220.0, 220.0, 220.0, 220.0, 220.0, 220.0, 220.0, 220.0, 220.0, 1.0);
        LandformType type = LandformBits.getType(bits);

        assertTrue(type == LandformType.PLATEAU || type == LandformType.PLAINS);
    }

    /**
     * Deep incision on a steep slope classifies CANYON over generic
     * VALLEY (TECHSPEC §180).
     */
    @Test
    @DisplayName("Priority Resolution: Canyon overrides generic Valley")
    void testCanyonPriorityOverride() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = 90.0;
        sample.gradMagnitude = 0.68;
        sample.riverIncision = 14.0;

        double hC = 90.0;
        double hN = 90.0, hS = 90.0;
        double hW = 95.0, hE = 95.0;
        double hNW = 95.0, hNE = 95.0, hSW = 95.0, hSE = 95.0;

        int bits = classifier.classify(kernel, sample, hC, hN, hS, hW, hE, hNW, hNE, hSW, hSE, 1.0);
        LandformType type = LandformBits.getType(bits);

        assertEquals(LandformType.CANYON, type);
        assertTrue(LandformBits.hasProcess(bits, LandformBits.PROCESS_FLUVIAL));
    }

    /**
     * FJORD requires GLACIAL + COASTAL + trough shape jointly
     * (TECHSPEC §172).
     */
    @Test
    @DisplayName("Compound Landform: Fjord detection requires Glacial + Coastal + Trough")
    void testFjordCompoundClassification() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = config.seaLevel() + 2.0;
        sample.temperature = 0.15;
        sample.riverIncision = 12.0;
        sample.gradMagnitude = 0.65;

        double hC = sample.finalSurface;
        double hW = hC + 8.0, hE = hC + 8.0;

        int bits = classifier.classify(kernel, sample, hC, hC, hC, hW, hE, hW, hE, hW, hE, 1.0);
        LandformType type = LandformBits.getType(bits);

        assertEquals(LandformType.FJORD, type);
        assertTrue(LandformBits.hasProcess(bits, LandformBits.PROCESS_GLACIAL));
        assertTrue(LandformBits.hasProcess(bits, LandformBits.PROCESS_COASTAL));
    }

    /**
     * High-elevation, deeply incised, closed convex top →
     * MOUNTAIN/MASSIF/RIDGE with the ALPINE environment bit
     * (TECHSPEC §170, §176).
     */
    @Test
    @DisplayName("Curvature & Prominence: Mountain Massif Identification")
    void testMassifClassification() {
        GeoSample sample = new GeoSample();
        sample.finalSurface = 450.0;
        sample.gradMagnitude = 0.45;
        sample.rawTectonic = 300.0;

        double hC = 450.0;
        double hN = 448.0, hS = 448.0, hW = 448.0, hE = 448.0;
        double hDiag = 446.0;

        int bits = classifier.classify(kernel, sample, hC, hN, hS, hW, hE, hDiag, hDiag, hDiag, hDiag, 1.0);
        LandformType type = LandformBits.getType(bits);

        assertTrue(type == LandformType.MOUNTAIN || type == LandformType.MASSIF || type == LandformType.RIDGE);
        assertTrue(LandformBits.hasEnvironment(bits, LandformBits.ENV_ALPINE));
    }
}
