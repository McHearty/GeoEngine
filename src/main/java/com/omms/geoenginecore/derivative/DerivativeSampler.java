package com.omms.geoenginecore.derivative;

/**
 * Stateless central-difference derivative stencils (TECHSPEC §37).
 *
 * <p>Finite differences are used because they are deterministic,
 * simple, testable, portable, and SIMD-friendly (TECHSPEC §37). All
 * methods are pure functions of their inputs, so they are safe to
 * call on chunk-boundary halo samples (TECHSPEC §76).
 */
public final class DerivativeSampler {
    /** Hides the implicit constructor. This is a static utility class. */
    private DerivativeSampler() {}

    /**
     * Central-difference X gradient.
     *
     * @param hNorth elevation of the northern neighbor
     * @param hSouth elevation of the southern neighbor
     * @param step one-sided stencil spacing in blocks
     * @return ∂H/∂x
     */
    public static double gradientX(double hNorth, double hSouth, double step) {
        return (hSouth - hNorth) / step;
    }

    /**
     * Central-difference Z gradient.
     *
     * @param hWest elevation of the western neighbor
     * @param hEast elevation of the eastern neighbor
     * @param step one-sided stencil spacing in blocks
     * @return ∂H/∂z
     */
    public static double gradientZ(double hWest, double hEast, double step) {
        return (hEast - hWest) / step;
    }

    /**
     * Gradient magnitude |∇H|.
     *
     * @param gx X gradient component
     * @param gz Z gradient component
     * @return |∇H|
     */
    public static double magnitude(double gx, double gz) {
        return Math.sqrt(gx * gx + gz * gz);
    }

    /**
     * 5-point Laplacian stencil (TECHSPEC §37).
     *
     * @param hC elevation at the column center
     * @param hN northern neighbor elevation
     * @param hS southern neighbor elevation
     * @param hW western neighbor elevation
     * @param hE eastern neighbor elevation
     * @param delta stencil spacing in blocks
     * @return ∇²H
     */
    public static double laplacian(double hC, double hN, double hS, double hW, double hE, double delta) {
        return (hN + hS + hW + hE - 4.0 * hC) / (delta * delta);
    }
}
