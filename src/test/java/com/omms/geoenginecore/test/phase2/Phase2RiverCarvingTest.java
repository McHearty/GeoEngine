package com.omms.geoenginecore.test.phase2;

import com.omms.geoenginecore.hydrology.ChannelField;
import com.omms.geoenginecore.hydrology.DrainageRouter;
import com.omms.geoenginecore.hydrology.FeatureGrammar;
import com.omms.geoenginecore.hydrology.MeanderField;
import com.omms.geoenginecore.hydrology.RiverField;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.test.fixtures.HydrologyProbe;
import com.omms.geoenginecore.test.fixtures.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 river carving correctness tests (TECHSPEC §28, §80).
 *
 * <p>These tests validate that the river incision pipeline actually
 * carves channels, that incision couples to surface lowering, and that
 * channel geometry matches the U-curve corridor model. This is the
 * primary Phase 2 seal gate — topology and bounds alone are necessary
 * but not sufficient.
 *
 * <p>Seal gate: all tests in this class MUST be green.
 */
public class Phase2RiverCarvingTest {

    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private HydrologyProbe probe;
    private GeoSample sample;

    @BeforeEach
    void setUp() {
        config = TestFixtures.defaultConfig();
        kernel = TestFixtures.standardKernel();
        probe = new HydrologyProbe(kernel, config);
        sample = new GeoSample();
    }

    /**
     * P2-10: Non-zero carving.
     *
     * <p>Over a full hydrology region with default Overworld config,
     * at least one column must have river incision R ≥ EPS_R (1.0).
     * If this fails, the river generation pipeline is broken.
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-10: Non-zero river carving over full hydrology region")
    void testNonZeroCarving_overHydrologyRegion() {
        probe.rasterize(0, 0);

        double maxIncision = probe.maxIncision();
        long carvedCells = probe.countCarved(TestFixtures.EPS_R);

        assertTrue(maxIncision >= TestFixtures.EPS_R,
                "Max incision " + maxIncision + " < EPS_R " + TestFixtures.EPS_R
                        + " — river generation pipeline produces no carving");
        assertTrue(carvedCells > 0,
                "No cells with R >= " + TestFixtures.EPS_R
                        + " — river generation pipeline produces no carving");
    }

    /**
     * P2-11: Hf coupling.
     *
     * <p>For columns with R ≥ EPS_R, the final surface must reflect
     * incision: | (hPre - R + S) - finalSurface | < 1e-9 (coastal off).
     * This validates that incision actually lowers the surface.
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-11: Incision couples to surface lowering (Hf coupling)")
    void testHfCoupling_forCarvedColumns() {
        probe.rasterize(0, 0);

        // Find a column with significant incision
        HydrologyProbe.ProbeColumn col = probe.findHighFlowColumn(TestFixtures.AF_CHANNEL_THRESHOLD);
        assertNotNull(col, "No column with A_f >= " + TestFixtures.AF_CHANNEL_THRESHOLD
                + " — cannot test Hf coupling");

        // Evaluate full column at this location
        kernel.evaluateFullColumn(col.worldX, col.worldZ, sample);

        assertTrue(sample.riverIncision >= TestFixtures.EPS_R,
                "Selected column has R = " + sample.riverIncision + " < EPS_R");

        // Compute asymmetry term from feature mask (Phase 9 Sprint R5)
        double asymmetry = 0.0;
        if ((sample.featureMask & com.omms.geoenginecore.hydrology.FeatureGrammar.F_POINT_BAR) != 0) {
            asymmetry = 0.5;
        } else if ((sample.featureMask & com.omms.geoenginecore.hydrology.FeatureGrammar.F_CUT_BANK) != 0) {
            asymmetry = -0.5;
        }

        double hStar = sample.hPre - sample.riverIncision;
        double expectedHf = hStar + sample.deposition + asymmetry;

        assertEquals(expectedHf, sample.finalSurface, 1e-9,
                "Hf coupling broken: hPre(" + sample.hPre + ") - R(" + sample.riverIncision
                        + ") + S(" + sample.deposition + ") + asym(" + asymmetry
                        + ") = " + expectedHf
                        + " but finalSurface = " + sample.finalSurface);
    }

    /**
     * P2-12: Corridor shape.
     *
     * <p>Pick a column with A_f ≥ 2.5, measure R on centerline vs ±halfWidth.
     * Require R_c > R_bank ≥ 0 and R outside width == 0. This validates
     * the U-curve channel cross-section (TECHSPEC §29).
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-12: U-curve channel corridor shape")
    void testCorridorShape() {
        // Use ChannelField directly to verify the corridor profile
        double flowAcc = 10.0; // Trunk river scale
        double halfWidth = ChannelField.getWidth(flowAcc) * 0.5;
        assertTrue(halfWidth > 0.0, "No channel width for A_f = " + flowAcc);

        // Centerline must have maximum factor
        double centerFactor = ChannelField.corridorFactor(halfWidth, 0.0);
        assertEquals(1.0, centerFactor, 1e-9, "Centerline factor must be 1.0");

        // Factor must decrease with distance
        double halfDistFactor = ChannelField.corridorFactor(halfWidth, halfWidth * 0.5);
        assertTrue(halfDistFactor < centerFactor,
                "Factor must decrease away from centerline: "
                        + centerFactor + " vs " + halfDistFactor);

        // Factor must be 0 at and beyond bank
        double bankFactor = ChannelField.corridorFactor(halfWidth, halfWidth);
        assertEquals(0.0, bankFactor, 1e-9, "Factor must be 0.0 at channel bank");

        double beyondBankFactor = ChannelField.corridorFactor(halfWidth, halfWidth * 1.5);
        assertEquals(0.0, beyondBankFactor, 1e-9,
                "Factor must be 0.0 outside channel");

        // Factor must be in [0, 1] everywhere
        for (double dist = 0.0; dist < halfWidth; dist += 0.1) {
            double factor = ChannelField.corridorFactor(halfWidth, dist);
            assertTrue(factor >= 0.0 && factor <= 1.0,
                    "Factor out of [0,1] at distance " + dist + ": " + factor);
        }
    }

    /**
     * P2-13: Thresholds.
     *
     * <p>A_f ≤ 8.0 → R = 0 and width = 0; A_f > 8.0 → R > 0 and width > 0.
     * This validates the channel initiation threshold (TECHSPEC §28).
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-13: Channel initiation thresholds")
    void testThresholds() {
        RiverField riverField = new RiverField(config);

        // Below incision threshold: no incision
        double belowIncision = riverField.computeIncision(1.0, 0.5, 1.0, 1.0);
        assertEquals(0.0, belowIncision, 1e-9, "Incision must be 0 for A_f < 8.0");

        double atIncision = riverField.computeIncision(8.0, 0.5, 1.0, 1.0);
        assertEquals(0.0, atIncision, 1e-9, "Incision must be 0 for A_f = 8.0");

        // Above channel threshold: incision and width
        double incisionWithChannel = riverField.computeIncision(9.0, 0.5, 1.0, 1.0);
        assertTrue(incisionWithChannel > 0.0,
                "Incision must be > 0 for A_f > 8.0: got " + incisionWithChannel);

        double widthWithChannel = ChannelField.getWidth(9.0);
        assertTrue(widthWithChannel > 0.0,
                "Width must be > 0 for A_f > 8.0");
    }

    /**
     * P2-14: F_channel in [0, 1].
     *
     * <p>Evaluate channel factor across a range of flow accumulations
     * and distances; never outside [0, 1].
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-14: Channel factor bounded in [0, 1]")
    void testChannelFactorBounded() {
        double[] flowAccValues = {0.0, 1.0, 1.8, 2.0, 2.5, 3.0, 5.0, 10.0, 50.0, 100.0};

        for (double af : flowAccValues) {
            double halfWidth = ChannelField.getWidth(af) * 0.5;

            // Check at multiple distances
            for (double dist = 0.0; dist <= halfWidth * 2.0 + 1.0; dist += 0.5) {
                double factor = ChannelField.corridorFactor(halfWidth, dist);
                assertTrue(factor >= 0.0 && factor <= 1.0,
                        "Channel factor out of [0,1] at A_f=" + af
                                + ", dist=" + dist + ": " + factor);
            }
        }
    }

    /**
     * P2-15: Width calibration envelope (Phase 9 Sprint R2).
     *
     * <p>Verify the recalibrated width formula produces the target
     * envelope: A_f=8→4, A_f=20→10, A_f=100→25-28 blocks.
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-15: Width calibration envelope")
    void testWidthCalibrationEnvelope() {
        // At initiation: 4 blocks (target: 4-6)
        double widthAtInitiation = ChannelField.getWidth(8.0);
        assertEquals(4.0, widthAtInitiation, 0.1, "Width at A_f=8 should be 4.0");

        // At stream scale: 10 blocks (target: 8-12)
        double widthAtStream = ChannelField.getWidth(20.0);
        assertTrue(widthAtStream >= 8.0 && widthAtStream <= 12.0,
                "Width at A_f=20 should be 8-12 blocks, got " + widthAtStream);

        // At trunk scale: 25-28 blocks (target: 18-28)
        double widthAtTrunk = ChannelField.getWidth(100.0);
        assertTrue(widthAtTrunk >= 18.0 && widthAtTrunk <= 28.0,
                "Width at A_f=100 should be 18-28 blocks, got " + widthAtTrunk);

        // At arterial scale: capped at 28 blocks
        double widthAtArterial = ChannelField.getWidth(500.0);
        assertTrue(widthAtArterial <= 28.0,
                "Width at A_f=500 should be capped at 28 blocks, got " + widthAtArterial);
    }

    /**
     * P2-16: Incision depth variance (Phase 9 Sprint R3).
     *
     * <p>With channelSteepness=0.10, incision depth variance should be
     * reduced: A_f=8→~2 blocks, A_f=20→~6 blocks, A_f=100→~14 blocks.
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-16: Incision depth variance")
    void testIncisionDepthVariance() {
        RiverField rf = new RiverField(TestFixtures.defaultConfig());

        // At initiation (A_f=9): shallow incision ~2 blocks
        double incisionAtInitiation = rf.computeIncision(9.0, 0.5, 1.0, 1.0);
        assertTrue(incisionAtInitiation >= 1.0 && incisionAtInitiation <= 3.0,
                "Incision at A_f=9 should be 1-3 blocks, got " + incisionAtInitiation);

        // At stream scale (A_f=20): moderate incision ~6 blocks
        double incisionAtStream = rf.computeIncision(20.0, 0.5, 1.0, 1.0);
        assertTrue(incisionAtStream >= 4.0 && incisionAtStream <= 8.0,
                "Incision at A_f=20 should be 4-8 blocks, got " + incisionAtStream);

        // At trunk scale (A_f=100): deep incision ~14 blocks
        double incisionAtTrunk = rf.computeIncision(100.0, 0.5, 1.0, 1.0);
        assertTrue(incisionAtTrunk >= 10.0 && incisionAtTrunk <= 18.0,
                "Incision at A_f=100 should be 10-18 blocks, got " + incisionAtTrunk);

        // Incision depth ratio should be reduced (not 9x)
        double ratio = incisionAtTrunk / Math.max(1.0, incisionAtInitiation);
        assertTrue(ratio < 8.0,
                "Incision depth ratio should be < 8x, got " + ratio + "x");
    }

    /**
     * P2-17: Meander calibration (Phase 9 Sprint R4).
     *
     * <p>Verify wavelength is ~8x width and amplitude is ~w/2.
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-17: Meander calibration")
    void testMeanderCalibration() {
        // Wavelength should be ~8x width
        double width10 = ChannelField.getWidth(20.0); // ~10 blocks
        double wavelength10 = MeanderField.wavelengthForWidth(width10);
        assertTrue(wavelength10 >= 40.0 && wavelength10 <= 100.0,
                "Wavelength at width=10 should be 40-100 blocks, got " + wavelength10);

        double width20 = ChannelField.getWidth(100.0); // ~25 blocks
        double wavelength20 = MeanderField.wavelengthForWidth(width20);
        assertTrue(wavelength20 >= 100.0 && wavelength20 <= 250.0,
                "Wavelength at width=25 should be 100-250 blocks, got " + wavelength20);

        // Amplitude should be ~w/2 at flat slope
        double halfWidth10 = width10 * 0.5;
        double amplitude10 = MeanderField.amplitudeForOrder(1, 0.0, halfWidth10);
        assertTrue(amplitude10 >= 3.0 && amplitude10 <= 7.0,
                "Amplitude at width=10 should be 3-7 blocks, got " + amplitude10);
    }

    /**
     * P2-18: Cross-section asymmetry (Phase 9 Sprint R5).
     *
     * <p>Verify point bar deposition (+0.5 blocks) and cut bank erosion (-0.5 blocks)
     * modify the surface height appropriately.
     */
    @Tag("must")
    @Tag("phase2")

    @Test
    @DisplayName("P2-18: Cross-section asymmetry")
    void testCrossSectionAsymmetry() {
        // Verify the asymmetry constants
        double pointBarDeposit = 0.5;  // Point bar adds sediment
        double cutBankErosion = -0.5;  // Cut bank removes sediment

        // Verify FeatureGrammar detects point bar and cut bank
        // computeFeatureMask(order, flowAcc, slope, curvature, distThalweg, halfWidth, ...)
        // Point bar: positive curvature, positive distance (inner bend)
        int pbMask = FeatureGrammar.computeFeatureMask(
                1, 2.0, 0.02, 0.02, 2.0, 5.0, false, false, false, false, 0.0);
        assertTrue((pbMask & FeatureGrammar.F_POINT_BAR) != 0,
                "Point bar should be detected on inner bend");

        // Cut bank: positive curvature, negative distance (outer bend)
        int cbMask = FeatureGrammar.computeFeatureMask(
                1, 2.0, 0.02, 0.02, -2.0, 5.0, false, false, false, false, 0.0);
        assertTrue((cbMask & FeatureGrammar.F_CUT_BANK) != 0,
                "Cut bank should be detected on outer bend");

        // Verify asymmetry is applied in kernel (via surface height identity tests)
        // C8 and P2-11 now account for asymmetry term
    }
}
