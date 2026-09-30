package com.omms.geoenginecore.test.phase9;

import com.omms.geoenginecore.math.GeoConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 9 Sprint 0 — Relief profile and bounded fixed-iteration
 * drainage configuration (TECHSPEC §24).
 */
@Tag("phase9")
public class ReliefProfileAndDrainageIterationTest {

    @Test
    @DisplayName("Target overworld preset has higher mountain relief than default")
    void testTargetPresetHigherRelief() {
        GeoConfig defaultConfig = GeoConfig.defaultOverworld(1);
        GeoConfig targetConfig = GeoConfig.targetOverworld(1);

        // Mountain belt relief is amplified in TARGET
        assertEquals(160.0, defaultConfig.tectonicAmpA());
        assertEquals(240.0, targetConfig.tectonicAmpA());
        assertEquals(50.0, defaultConfig.tectonicAmpB());
        assertEquals(80.0, targetConfig.tectonicAmpB());
        // River incision is deeper in TARGET
        assertEquals(18.0, defaultConfig.riverMaxIncision());
        assertEquals(28.0, targetConfig.riverMaxIncision());
        // TARGET uses two-pass drainage for stabler corridors
        assertEquals(1, defaultConfig.drainageIterations());
        assertEquals(2, targetConfig.drainageIterations());
    }

    @Test
    @DisplayName("Both presets pass validation and produce different hashes")
    void testPresetValidationAndHashDivergence() {
        GeoConfig defaultConfig = GeoConfig.defaultOverworld(1);
        GeoConfig targetConfig = GeoConfig.targetOverworld(1);

        // Both must be valid (constructor throws on invalid)
        assertNotEquals(defaultConfig.configHash(), targetConfig.configHash());
    }

    @Test
    @DisplayName("Drainage iteration count is validated to [1, 8]")
    void testDrainageIterationBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            new GeoConfig(1, 0, -64, 1984, 64,
                0.0003, 0.0008, 0.0016, 160.0, 160.0, 50.0, 2.2,
                0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
                0.6, 1.4, 0.0012, 35.0, 5.0, 16,
                18.0, 0.15, 0, -40, 128));
        assertThrows(IllegalArgumentException.class, () ->
            new GeoConfig(1, 0, -64, 1984, 64,
                0.0003, 0.0008, 0.0016, 160.0, 160.0, 50.0, 2.2,
                0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
                0.6, 1.4, 0.0012, 35.0, 5.0, 16,
                18.0, 0.15, 9, -40, 128));
        // Boundary values accepted
        new GeoConfig(1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016, 160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, 1, -40, 128);
        new GeoConfig(1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016, 160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, 8, -40, 128);
    }

    @Test
    @DisplayName("Changing drainage iteration count changes config hash")
    void testDrainageIterationAffectsHash() {
        GeoConfig k1 = GeoConfig.defaultOverworld(1);
        // Construct manually with K=2 to test hash divergence
        GeoConfig k2 = new GeoConfig(1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016, 160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, 2, -40, 128);
        assertNotEquals(k1.configHash(), k2.configHash());
    }
}
