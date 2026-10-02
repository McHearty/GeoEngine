package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoMath;

/**
 * Discrete realization of channel incision (TECHSPEC_AMEND001 A3.3, A4).
 *
 * <p>Computes the discrete realization R_quant of the continuous incision
 * field R using vertical quantization with stepDeltaY. The realization
 * is deterministic and bounded by the continuous incision magnitude.
 */
public final class ChannelRealization {
    /** Vertical quantization step in blocks. */
    private final double stepDeltaY;
    /** Maximum incision depth in blocks. */
    private final double maxIncisionDepth;

    /**
     * Constructs the channel realization.
     *
     * @param stepDeltaY vertical quantization step in blocks
     * @param maxIncisionDepth maximum incision depth in blocks
     */
    public ChannelRealization(double stepDeltaY, double maxIncisionDepth) {
        this.stepDeltaY = Math.max(0.1, stepDeltaY);
        this.maxIncisionDepth = Math.max(0.0, maxIncisionDepth);
    }

    /**
     * Computes the discrete realization of continuous incision.
     *
     * @param continuousIncision the continuous incision R
     * @return the discrete realization R_quant
     */
    public double realize(double continuousIncision) {
        // Clamp to [0, maxIncisionDepth]
        double clamped = GeoMath.clamp(continuousIncision, 0.0, maxIncisionDepth);

        // Quantize to nearest stepDeltaY
        return Math.round(clamped / stepDeltaY) * stepDeltaY;
    }

    /**
     * Computes the discrete realization of continuous incision using
     * the floor function (conservative realization).
     *
     * @param continuousIncision the continuous incision R
     * @return the discrete realization R_quant (floor)
     */
    public double realizeFloor(double continuousIncision) {
        // Clamp to [0, maxIncisionDepth]
        double clamped = GeoMath.clamp(continuousIncision, 0.0, maxIncisionDepth);

        // Floor to stepDeltaY
        return Math.floor(clamped / stepDeltaY) * stepDeltaY;
    }

    /**
     * Computes the discrete realization of continuous incision using
     * the ceiling function (aggressive realization).
     *
     * @param continuousIncision the continuous incision R
     * @return the discrete realization R_quant (ceiling)
     */
    public double realizeCeiling(double continuousIncision) {
        // Clamp to [0, maxIncisionDepth]
        double clamped = GeoMath.clamp(continuousIncision, 0.0, maxIncisionDepth);

        // Ceiling to stepDeltaY
        return Math.ceil(clamped / stepDeltaY) * stepDeltaY;
    }

    /**
     * Determines if a location should be realized as a channel.
     *
     * @param continuousIncision the continuous incision R
     * @return true if the location should be realized
     */
    public boolean shouldRealize(double continuousIncision) {
        return realize(continuousIncision) > 0.0;
    }
}
