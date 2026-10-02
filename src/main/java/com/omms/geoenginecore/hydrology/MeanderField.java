package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Channel meander modeling (TECHSPEC §30, TECHSPEC_AMEND001 A3.6).
 *
 * <p>Computes a deterministic lateral offset applied to the routing-lattice
 * centerline to produce a meandered thalweg. The offset is bounded by local
 * channel width and slope, and is identical for the same (seed, config, x, z).
 *
 * <p>Meander parameters are configurable via {@link GeoConfig}:
 * <ul>
 *   <li>meanderStrength: amplitude strength (default 0.5)</li>
 *   <li>smoothingPasses: deterministic smoothing passes (default 3)</li>
 * </ul>
 */
public final class MeanderField {
    /** Default wavelength multiplier. */
    public static final double MEANDER_WAVELENGTH_MULTIPLIER = 8.0;
    /** Default maximum meander amplitude fraction. */
    public static final double MEANDER_AMPLITUDE_FRACTION = 1.0;
    /** Default slope threshold for straight channels. */
    public static final double MEANDER_STRAIGHT_SLOPE = 0.1;

    /** Configured meander strength (amplitude). */
    private final double strength;
    /** Configured number of smoothing passes. */
    private final int smoothingPasses;

    /**
     * Constructs a meander field with default parameters.
     */
    public MeanderField() {
        this(0.5, 3);
    }

    /**
     * Constructs a meander field with configurable parameters.
     *
     * @param strength meander amplitude strength (0.0-1.0)
     * @param smoothingPasses number of smoothing passes (0-10)
     */
    public MeanderField(double strength, int smoothingPasses) {
        this.strength = Math.max(0.0, Math.min(1.0, strength));
        this.smoothingPasses = Math.max(0, Math.min(10, smoothingPasses));
    }

    /**
     * Constructs a meander field from configuration.
     *
     * @param config validated configuration
     */
    public MeanderField(GeoConfig config) {
        this(config.meanderStrength(), config.smoothingPasses());
    }

    /** @return the configured meander strength */
    public double getStrength() { return strength; }

    /** @return the configured smoothing passes */
    public int getSmoothingPasses() { return smoothingPasses; }

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
    /**
     * Compute the meander amplitude for a given channel order and slope.
     * Amplitude decreases with increasing slope (steeper = straighter).
     * Uses the configured strength parameter.
     */
    /**
     * Instance method: compute the meander amplitude with configured strength.
     */
    public double meanderAmplitude(int order, double slope, double halfWidth) {
        if (order == 0) {
            return 0.0;
        }
        // Slope factor: 1.0 at flat, 0.0 at steep
        double slopeFactor = GeoMath.clamp(1.0 - slope / MEANDER_STRAIGHT_SLOPE, 0.0, 1.0);
        // Amplitude bounded by half-width, scaled by strength
        return strength * MEANDER_AMPLITUDE_FRACTION * halfWidth * slopeFactor;
    }

    /**
     * Static method with default strength for backward compatibility.
     */
    public static double amplitudeForOrder(int order, double slope, double halfWidth) {
        if (order == 0) {
            return 0.0;
        }
        double slopeFactor = GeoMath.clamp(1.0 - slope / MEANDER_STRAIGHT_SLOPE, 0.0, 1.0);
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
    /**
     * Instance method: compute the lateral meander offset with configured parameters.
     */
    public double meanderOffset(int order, double slope, double halfWidth,
                                long seed, long basinId, double arcLength) {
        if (order == 0 || halfWidth <= 0.0) {
            return 0.0;
        }
        double fullWidth = halfWidth * 2.0;
        double wavelength = wavelengthForWidth(fullWidth);
        double amplitude = this.meanderAmplitude(order, slope, halfWidth);
        double phaseVal = phase(seed, basinId);
        // Sinusoidal meander
        return amplitude * Math.sin(2.0 * Math.PI * arcLength / wavelength + phaseVal);
    }

    /**
     * Static method with default strength for backward compatibility.
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
        return amplitude * Math.sin(2.0 * Math.PI * arcLength / wavelength + phaseVal);
    }
}