package com.omms.geoenginecore.test;

import com.omms.geoenginecore.math.GeoConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Configuration validation conformance (TECHSPEC §64; Phase 1 acceptance
 * criterion "configuration validation tests pass").
 *
 * <p>Each rejected variant uses the calibrated Overworld baseline
 * ({@code -64, 1984, 64, 0.0003, 0.0008, 0.0016, 160, 160, 50, 2.2,
 * 0.0005, 24, 0.45, 0.0003, 0.0004, 0.0004, 0.6, 1.4, 0.0012, 35, 5,
 * 16, 18, 0.15, -40, 128}) and mutates exactly one component,
 * isolating the rule under test.
 */
public class Phase1ConfigValidationTest {

    @Test
    @DisplayName("Inverted Vertical Bounds Rejected (worldMinY < worldMaxY)")
    void testInvertedWorldBounds() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, 100, 50, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Sea Level Below World Floor Rejected")
    void testSeaLevelBelowWorld() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, -65,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Sea Level Above World Ceiling Rejected")
    void testSeaLevelAboveWorld() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 1985,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Zero Tectonic Frequencies Rejected (positive wavelengths, §64)")
    void testZeroTectonicFrequencies() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Negative Tectonic Amplitudes Rejected")
    void testNegativeTectonicAmplitudes() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            -160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, -160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, -50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Uplift Exponent Below 1 Rejected")
    void testUpliftExponentBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 0.5,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Non-positive Stress Frequency Rejected")
    void testNonPositiveStressFrequency() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Negative Stress Amplitude Rejected")
    void testNegativeStressAmplitude() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, -24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Stress Warp Exceeding Jacobian Bound Rejected (§44)")
    void testStressJacobianExceeded() {
        // 1000.0 * 0.0005 = 0.5 > 0.45
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 1000.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Non-positive Epoch and Climate Frequencies Rejected (§64 wavelengths)")
    void testNonPositiveTimeFrequencies() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Negative Lapse Rate Rejected")
    void testNegativeLapseRate() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, -0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Invalid Climate Bounds Rejected (0 <= climateMin <= climateMax)")
    void testInvalidClimateBounds() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            -0.1, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 0.5, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Negative Erosion Rate Rejected")
    void testNegativeBaseErosionRate() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, -35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Negative Warp Amplitude Rejected (§44 volumetric bound)")
    void testNegativeMaxWarpAmplitude() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, -5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Non-positive Surface Band Radius Rejected (§51)")
    void testNonPositiveSurfaceBandRadius() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 0,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Invalid River Parameters Rejected (§28)")
    void testInvalidRiverParameters() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            -18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.0, -40, 128));
    }

    @Test
    @DisplayName("NaN and Infinity Values Rejected (§64 finite floating point)")
    void testNonFiniteValuesRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, Double.NaN, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, Double.POSITIVE_INFINITY, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, Double.NEGATIVE_INFINITY,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128));
    }

    @Test
    @DisplayName("Cave Envelope Inverted Rejected (§64 valid cave cover depths)")
    void testInvertedCaveEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, 128, -40));
    }

    @Test
    @DisplayName("Cave Envelope Outside World Bounds Rejected")
    void testCaveEnvelopeOutsideWorld() {
        assertThrows(IllegalArgumentException.class, () -> new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 3000));
    }

    @Test
    @DisplayName("Degenerate Zero-Height Cave Envelope Accepted (means no caves)")
    void testDegenerateCaveEnvelopeAccepted() {
        new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, 0, 0);
    }

    @Test
    @DisplayName("Calibrated Baseline Constructs and Hashes Deterministically (§8)")
    void testValidBaselineAndHashStability() {
        GeoConfig a = GeoConfig.defaultOverworld(1);
        GeoConfig b = new GeoConfig(
            1, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128);
        assertEquals(a.configHash(), b.configHash(),
            "identical parameters must fold to the same deterministic hash");
        GeoConfig c = new GeoConfig(
            2, 0, -64, 1984, 64,
            0.0003, 0.0008, 0.0016,
            160.0, 160.0, 50.0, 2.2,
            0.0005, 24.0, 0.45, 0.0003, 0.0004, 0.0004,
            0.6, 1.4, 0.0012, 35.0, 5.0, 16,
            18.0, 0.15, -40, 128);
        assertNotEquals(a.configHash(), c.configHash(),
            "a changed generatorVersion must change the hash");
    }
}
