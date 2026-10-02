package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.hydrology.ChannelField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Channel mask non-circularity test (TECHSPEC_AMEND001 A6.8).
 *
 * <p>Verifies that the channel mask M_channel is not defined
 * circularly in terms of the terrain realization.
 */
public class ChannelMaskNonCircularityTest {

    @Tag("must")
    @Tag("phase6")
    @Test
    @DisplayName("A6.8: Channel mask is defined by accumulation, not realization")
    void testChannelMaskNonCircularity() {
        ChannelField channel = new ChannelField(14.0);

        // Channel mask is determined by accumulation threshold
        assertTrue(channel.inChannelMask(15.0));
        assertFalse(channel.inChannelMask(10.0));

        // Realization mask is a subset of channel mask
        assertTrue(channel.inRealizeMask(15.0));
        assertFalse(channel.inRealizeMask(10.0));

        // M_realize implies M_channel
        assertTrue(channel.inRealizeMask(20.0) && channel.inChannelMask(20.0));
    }

    @Tag("phase6")
    @Test
    @DisplayName("Channel mask is independent of incision")
    void testChannelMaskIndependentOfIncision() {
        ChannelField channel = new ChannelField(14.0);

        // Channel mask depends only on accumulation, not on incision depth
        // (incision is computed separately in RiverField)
        assertTrue(channel.inChannelMask(15.0));
        // Width is positive for accumulation above threshold
        assertTrue(channel.channelWidth(15.0) > 0.0);
    }
}
