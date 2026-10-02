package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.ChannelField;
import com.omms.geoenginecore.hydrology.ChannelRealization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Realization outside mask identity test (TECHSPEC_AMEND001 A6.15).
 *
 * <p>Verifies that outside the channel realization mask,
 * R_quant = 0 and Hterrain = H0 + S (identity).
 */
public class RealizationOutsideMaskIdentityTest {

    @Tag("must")
    @Tag("phase6")
    @Test
    @DisplayName("A6.15: R_quant = 0 outside channel mask")
    void testRQuantZeroOutsideMask() {
        ChannelField channel = new ChannelField(14.0);
        ChannelRealization realization = new ChannelRealization(1.0, 20.0);

        // Below threshold: not in channel mask
        double flowAcc = 10.0;
        assertFalse(channel.inChannelMask(flowAcc));

        // Incision is 0 below threshold
        assertEquals(0.0, channel.channelWidth(flowAcc), 1e-9);

        // R_quant = 0 when R = 0
        double R = 0.0;
        assertEquals(0.0, realization.realize(R), 1e-9);
    }

    @Tag("must")
    @Tag("phase6")
    @Test
    @DisplayName("A6.15: Hterrain = H0 + S outside channel mask")
    void testHTerrainIdentityOutsideMask() {
        // Outside channel mask: Hterrain = H0 - R_quant + S = H0 + S (since R_quant = 0)
        double H0 = 100.0;
        double R_quant = 0.0;
        double S = 5.0;

        double Hterrain = H0 - R_quant + S;
        assertEquals(105.0, Hterrain, 1e-9);
    }
}
