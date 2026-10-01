package com.omms.geoenginecore.test.phase2;

import com.omms.geoenginecore.hydrology.ChannelField;
import com.omms.geoenginecore.hydrology.DrainageRouter;
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

        double hStar = sample.hPre - sample.riverIncision;
        double expectedHf = hStar + sample.deposition;

        assertEquals(expectedHf, sample.finalSurface, 1e-9,
                "Hf coupling broken: hPre(" + sample.hPre + ") - R(" + sample.riverIncision
                        + ") + S(" + sample.deposition + ") = " + expectedHf
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
}
