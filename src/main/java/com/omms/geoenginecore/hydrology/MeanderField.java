package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoMath;

/**
 * Channel meander modeling (TECHSPEC §30).
 *
 * <p>Computes a deterministic lateral offset applied to the routing-lattice
 * centerline to produce a meandered thalweg. The offset is bounded by local
 * channel width and slope, and is identical for the same (seed, config, x, z).
 */
public final class MeanderField {

    /** Wavelength multiplier (wavelength = MEANDER_WAVELENGTH_MULTIPLIER * width). */
    public static final double MEANDER_WAVELENGTH_MULTIPLIER = 8.0;
    /** Maximum meander amplitude as fraction of channel half-width. */
    public static final double MEANDER_AMPLITUDE_FRACTION = 1.0;
    /** Slope above which meanders become straight (dimensionless). */
    public static final double MEANDER_STRAIGHT_SLOPE = 0.1;

    /**
     * Compute the wavelength for a given channel width.
     * Wavelength is proportional to width (~8x the full width).
     */
    public static double wavelengthForWidth(double fullWidth) {
        return MEANDER_WAVELENGTH_MULTIPLIER * fullWidth;
    }

    /**
     * Compute the meander amplitude for a given channel order and slope.
     * Amplitude decreases with increasing slope (steeper = straighter).
     */
    public static double amplitudeForOrder(int order, double slope, double halfWidth) {
        if (order == 0) {
            return 0.0;
        }
        // Slope factor: 1.0 at flat, 0.0 at steep
        double slopeFactor = GeoMath.clamp(1.0 - slope / MEANDER_STRAIGHT_SLOPE, 0.0, 1.0);
        // Amplitude bounded by half-width
        return MEANDER_AMPLITUDE_FRACTION * halfWidth * slopeFactor;
    }

    /**
     * Compute the meander phase from seed and basin ID.
     * Uses SplitMix64-style mixing for deterministic pseudo-random phase.
     * The phase is constant for the entire channel.
     */
    public static double phase(long seed, long basinId) {
        // Mix seed and basin ID
        long mixed = seed ^ (basinId * 0x9E3779B97F4A7C15L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed = mixed ^ (mixed >>> 31);
        // Convert to [0, 1) and scale to [0, 2π)
        double u = ((mixed >>> 11) & 0x7FFFFFFFL) / (double) 0x80000000L;
        return u * 2.0 * Math.PI;
    }

    /**
     * Compute the lateral meander offset at a point along the channel.
     *
     * @param order channel order (0-4)
     * @param slope local channel slope
     * @param halfWidth channel half-width
     * @param seed world seed
     * @param basinId drainage basin ID
     * @param arcLength distance along the channel centerline
     * @return lateral offset in blocks
     */
    public static double offset(int order, double slope, double halfWidth,
                                long seed, long basinId, double arcLength) {
        if (order == 0 || halfWidth <= 0.0) {
            return 0.0;
        }
        double fullWidth = halfWidth * 2.0;
        double wavelength = wavelengthForWidth(fullWidth);
        double amplitude = amplitudeForOrder(order, slope, halfWidth);
        double phaseVal = phase(seed, basinId);
        // Sinusoidal meander
        return amplitude * Math.sin(2.0 * Math.PI * arcLength / wavelength + phaseVal);
    }
}