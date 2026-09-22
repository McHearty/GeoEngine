package com.omms.geoenginecore.math;

/**
 * Pure-JDK clamp helper for the GeoEngine core (TECHSPEC §7 dependency
 * rule: the core must compile on a plain JDK without any
 * Minecraft/NeoForge toolchain). Some mod-development toolchains patch
 * {@link Math} with a {@code clamp} method, but a standard JDK does not
 * have one, so the core defines its own clamping contract instead of
 * relying on a toolchain extension.
 *
 * <p>Contract, matching the toolchain {@code Math.clamp} the code was
 * originally written against:
 * <ul>
 *   <li>{@code v &lt; min} returns {@code min},</li>
 *   <li>{@code v &gt; max} returns {@code max},</li>
 *   <li>otherwise returns {@code v}; a NaN {@code v} propagates as NaN,</li>
 *   <li>a NaN bound or {@code min &gt; max} throws
 *       {@link IllegalArgumentException} (fail-fast, mirroring
 *       {@link Math#clamp} on toolchains that provide it).</li>
 * </ul>
 */
public final class GeoMath {

    private GeoMath() {
    }

    /**
     * Clamps an int into [min, max].
     *
     * @param v value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return v clamped into [min, max]
     */
    public static int clamp(int v, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " > max " + max);
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    /**
     * Clamps a long into [min, max].
     *
     * @param v value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return v clamped into [min, max]
     */
    public static long clamp(long v, long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " > max " + max);
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    /**
     * Clamps a float into [min, max].
     *
     * @param v value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return v clamped into [min, max], NaN when v is NaN
     */
    public static float clamp(float v, float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || min > max) {
            throw new IllegalArgumentException(
                "NaN bound or min " + min + " > max " + max);
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    /**
     * Clamps a double into [min, max].
     *
     * @param v value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return v clamped into [min, max], NaN when v is NaN
     */
    public static double clamp(double v, double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
            throw new IllegalArgumentException(
                "NaN bound or min " + min + " > max " + max);
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}
