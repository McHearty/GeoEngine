package com.omms.geoenginecore.test.phase3;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.hydrology.ChannelField;
import com.omms.geoenginecore.hydrology.RiverField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configurable channel parameters tests (TECHSPEC_AMEND001 A3.1).
 *
 * <p>Verifies that the channel initiation threshold and related
 * parameters are configurable as required by the amendment.
 */
public class ConfigurableChannelTest {

    @Tag("must")
    @Tag("phase3")
    @Test
    @DisplayName("A3.1: Channel width uses configurable minRiverAccumulation")
    void testChannelWidthUsesConfigurableThreshold() {
        // Default threshold (14.0)
        ChannelField defaultField = new ChannelField();
        assertTrue(defaultField.channelWidth(13.0) == 0.0);
        assertTrue(defaultField.channelWidth(15.0) > 0.0);

        // Custom threshold (20.0)
        ChannelField customField = new ChannelField(20.0);
        assertTrue(customField.channelWidth(15.0) == 0.0);
        assertTrue(customField.channelWidth(25.0) > 0.0);

        // Static method with threshold parameter
        assertTrue(ChannelField.getWidth(13.0, 14.0) == 0.0);
        assertTrue(ChannelField.getWidth(15.0, 14.0) > 0.0);
        assertTrue(ChannelField.getWidth(15.0, 20.0) == 0.0);
        assertTrue(ChannelField.getWidth(25.0, 20.0) > 0.0);
    }

    @Tag("must")
    @Tag("phase3")
    @Test
    @DisplayName("A3.1: River incision uses configurable minRiverAccumulation")
    void testRiverIncisionUsesConfigurableThreshold() {
        GeoConfig config = GeoConfig.defaultOverworld(1).withMinRiverAccumulation(20.0);
        RiverField riverField = new RiverField(config);

        // Below threshold: no incision
        double incisionBelow = riverField.computeIncision(15.0, 0.1, 1.0, 1.0);
        assertTrue(incisionBelow == 0.0, "Incision should be 0 below threshold");

        // Above threshold: some incision
        double incisionAbove = riverField.computeIncision(25.0, 0.1, 1.0, 1.0);
        assertTrue(incisionAbove > 0.0, "Incision should be > 0 above threshold");
    }

    @Tag("must")
    @Tag("phase3")
    @Test
    @DisplayName("A3.1: Channel profile factor uses configurable threshold")
    void testChannelProfileFactorUsesConfigurableThreshold() {
        ChannelField field = new ChannelField(20.0);

        // Below threshold: no channel
        double factorBelow = field.channelProfileFactor(15.0, 0.0);
        assertTrue(factorBelow == 0.0, "Profile factor should be 0 below threshold");

        // Above threshold at centerline: 1.0
        double factorCenter = field.channelProfileFactor(25.0, 0.0);
        assertEquals(1.0, factorCenter, 1e-6, "Profile factor should be 1.0 at centerline");
    }

    @Tag("phase3")
    @Test
    @DisplayName("Channel width increases with accumulation (configurable)")
    void testChannelWidthIncreasesWithAccumulation() {
        ChannelField field = new ChannelField(14.0);

        double w1 = field.channelWidth(20.0);
        double w2 = field.channelWidth(50.0);
        double w3 = field.channelWidth(100.0);

        assertTrue(w1 < w2, "Width should increase with accumulation");
        assertTrue(w2 < w3, "Width should increase with accumulation");

        // Width should saturate around 28 blocks
        assertTrue(w3 < 30.0, "Width should saturate below 30 blocks");
    }

    @Tag("phase3")
    @Test
    @DisplayName("Channel width saturates at trunk river scale")
    void testChannelWidthSaturates() {
        ChannelField field = new ChannelField(14.0);

        double w100 = field.channelWidth(100.0);
        double w1000 = field.channelWidth(1000.0);
        double w10000 = field.channelWidth(10000.0);

        // Saturation: width should not grow unbounded
        assertTrue(w100 < 30.0);
        assertTrue(w1000 < 30.0);
        assertTrue(w10000 < 30.0);

        // But should be close to saturation
        assertTrue(w1000 > 25.0);
        assertTrue(w10000 > 25.0);
    }

    @Tag("phase3")
    @Test
    @DisplayName("Channel profile is cosine U-trough")
    void testChannelProfileIsCosineUTrough() {
        ChannelField field = new ChannelField(14.0);
        double halfWidth = field.channelWidth(50.0) * 0.5;

        // Centerline: 1.0
        assertEquals(1.0, field.channelProfileFactor(50.0, 0.0), 1e-6);

        // Midway: should be positive
        double factorMid = field.channelProfileFactor(50.0, halfWidth * 0.5);
        assertTrue(factorMid > 0.0 && factorMid < 1.0);

        // At bank: 0.0
        double factorBank = field.channelProfileFactor(50.0, halfWidth);
        assertEquals(0.0, factorBank, 1e-6);

        // Beyond bank: 0.0
        double factorBeyond = field.channelProfileFactor(50.0, halfWidth * 1.5);
        assertEquals(0.0, factorBeyond, 1e-6);
    }

    @Tag("phase3")
    @Test
    @DisplayName("Channel field is deterministic")
    void testChannelFieldDeterministic() {
        ChannelField field1 = new ChannelField(14.0);
        ChannelField field2 = new ChannelField(14.0);

        for (double acc = 10.0; acc <= 100.0; acc += 5.0) {
            assertEquals(field1.channelWidth(acc), field2.channelWidth(acc), 1e-10);
            assertEquals(field1.channelProfileFactor(acc, 1.0), field2.channelProfileFactor(acc, 1.0), 1e-10);
        }
    }
}
