package com.omms.geoenginecore.test.phase5;

import com.omms.geoenginecore.hydrology.LakeTopology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lake topology tests (TECHSPEC_AMEND001 A3.11).
 *
 * <p>Verifies lake detection and area filtering.
 */
public class LakeTopologyTest {

    @Tag("must")
    @Tag("phase5")
    @Test
    @DisplayName("A6.9: Lake area filter passes large lakes")
    void testLakeAreaFilterPassesLarge() {
        LakeTopology lakes = new LakeTopology(100.0, 10000.0);

        assertTrue(lakes.passesAreaFilter(500.0));
        assertTrue(lakes.passesAreaFilter(5000.0));
    }

    @Tag("must")
    @Tag("phase5")
    @Test
    @DisplayName("A6.9: Lake area filter rejects small lakes")
    void testLakeAreaFilterRejectsSmall() {
        LakeTopology lakes = new LakeTopology(100.0, 10000.0);

        assertFalse(lakes.passesAreaFilter(10.0));
        assertFalse(lakes.passesAreaFilter(50.0));
    }

    @Tag("must")
    @Tag("phase5")
    @Test
    @DisplayName("A6.9: Lake area filter rejects very large lakes")
    void testLakeAreaFilterRejectsLarge() {
        LakeTopology lakes = new LakeTopology(100.0, 10000.0);

        assertFalse(lakes.passesAreaFilter(50000.0));
    }

    @Tag("phase5")
    @Test
    @DisplayName("Water surface level equals lake depth")
    void testWaterSurfaceLevel() {
        LakeTopology lakes = new LakeTopology(100.0, 10000.0);

        assertEquals(2.0, lakes.waterSurfaceLevel(2.0), 1e-9);
        assertEquals(5.0, lakes.waterSurfaceLevel(5.0), 1e-9);
    }

    @Tag("phase5")
    @Test
    @DisplayName("Lake topology is deterministic")
    void testLakeTopologyDeterministic() {
        LakeTopology l1 = new LakeTopology(100.0, 10000.0);
        LakeTopology l2 = new LakeTopology(100.0, 10000.0);

        for (double area = 0.0; area <= 20000.0; area += 100.0) {
            assertEquals(l1.passesAreaFilter(area), l2.passesAreaFilter(area));
        }
    }
}
