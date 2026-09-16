package com.geoengine.core.test;

import com.geoengine.core.field.advanced.*;
import com.geoengine.core.math.GeoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase7AdvancedGeomorphologyTest {
    private static final long TEST_SEED = 0x777A55C1A1D0E1L;
    private GeoConfig config;
    private GlacialField glacial;
    private AeolianField aeolian;
    private KarstField karst;
    private CoastalField coastal;
    private AlluvialDeltaField delta;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        glacial = new GlacialField(TEST_SEED, config);
        aeolian = new AeolianField(TEST_SEED, config);
        karst = new KarstField(TEST_SEED, config);
        coastal = new CoastalField(TEST_SEED, config);
        delta = new AlluvialDeltaField(TEST_SEED, config);
    }

    @Test
    @DisplayName("Glacial Trough: Parabolic Profile Invariant")
    void testGlacialParabolicProfile() {
        double deltaH = glacial.evaluateUValleyModification(100.0, 100.0, 150.0, 0.2, 0.10, 1.0);
        assertTrue(deltaH <= 0.0);

        double warmDelta = glacial.evaluateUValleyModification(100.0, 100.0, 150.0, 0.2, 0.80, 1.0);
        assertEquals(0.0, warmDelta, 0.0);
    }

    @Test
    @DisplayName("Aeolian Dunes: Climate Aridity Precondition Gating")
    void testAeolianAridityGating() {
        double humidDune = aeolian.evaluateDuneRelief(200.0, 200.0, 0.8, 0.60, 0.05);
        assertEquals(0.0, humidDune, 0.0);

        double aridDune = aeolian.evaluateDuneRelief(200.0, 200.0, 0.85, 0.05, 0.02);
        assertFalse(Double.isNaN(aridDune));
        assertTrue(aridDune >= 0.0);
    }

    @Test
    @DisplayName("Karst Dissolution: Sinkholes Bounded Cones")
    void testKarstDissolutionBounds() {
        double coldKarst = karst.evaluateSinkholeRelief(0, 0, 0.1, 0.8);
        assertEquals(0.0, coldKarst, 0.0);

        double activeKarst = karst.evaluateSinkholeRelief(0, 0, 0.8, 0.9);
        assertTrue(activeKarst <= 0.0);
        assertTrue(activeKarst >= -25.0);
    }

    @Test
    @DisplayName("Coastal Sea Arch: Volumetric Void Sign Invariant (C >= 0)")
    void testCoastalArchVoidSign() {
        double inlandVoid = coastal.evaluateSeaArchVoid(500, config.seaLevel() + 2, 500, 150.0, 0.8);
        assertEquals(0.0, inlandVoid, 0.0);

        double coastalVoid = coastal.evaluateSeaArchVoid(0, config.seaLevel() + 2, 0, config.seaLevel() + 12, 0.8);
        assertTrue(coastalVoid >= 0.0);
    }

    @Test
    @DisplayName("Alluvial Delta: Shallow Water Sediment Deposition Bounds")
    void testDeltaDepositionBounds() {
        double deepOceanDelta = delta.evaluateDeltaLobe(100, 100, config.seaLevel() - 50.0, 3.0, 4.0);
        assertEquals(0.0, deepOceanDelta, 0.0);

        double shallowDelta = delta.evaluateDeltaLobe(100, 100, config.seaLevel() - 4.0, 3.5, 4.0);
        assertTrue(shallowDelta >= 0.0);
    }
}
