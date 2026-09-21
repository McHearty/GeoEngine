package com.omms.geoenginecore.derivative;

/**
 * Finite-difference derivative stencils (TECHSPEC §37).
 *
 * <p>Finite differences are used because they are deterministic,
 * simple, testable, portable, and SIMD-friendly (TECHSPEC §37). All
 * methods are pure functions of their inputs, so they are safe to
 * call on chunk-boundary halo samples (TECHSPEC §76).
 *
 * <p>Gradient stencils are central differences: the neighbor pair
 * straddles the evaluation column by {@code 2 * step} blocks, so the
 * estimated directional derivative is (hFar - hNear) / (2 * step),
 * which recovers the analytic slope of any linear surface exactly.
 * The Laplacian is the 5-point stencil, which is exact for quadratic
 * surfaces. The hot pipeline path inlines the same stencils; this
 * class is the standalone reference pinned by
 * {@code Phase1DerivativeStencilTest}.
 */
public final class DerivativeSampler {
    /** Hides the implicit constructor. This is a static utility class. */
    private DerivativeSampler() {}

    /**
     * Central-difference gradient along the X axis.
     *
     * @param hNear neighbor sample on the negative-X side
     * @param hFar neighbor sample on the positive-X side
     * @param step one-sided stencil spacing in blocks (must be &gt; 0)
     * @return ∂H/∂x
     * @throws IllegalArgumentException if step is not positive
     */
    public static double gradientX(double hNear, double hFar, double step) {
        if (step <= 0.0) {
            throw new IllegalArgumentException("step must be positive, got " + step);
        }
        return (hFar - hNear) / (2.0 * step);
    }

    /**
     * Central-difference gradient along the Z axis.
     *
     * @param hNear neighbor sample on the negative-Z side
     * @param hFar neighbor sample on the positive-Z side
     * @param step one-sided stencil spacing in blocks (must be &gt; 0)
     * @return ∂H/∂z
     * @throws IllegalArgumentException if step is not positive
     */
    public static double gradientZ(double hNear, double hFar, double step) {
        if (step <= 0.0) {
            throw new IllegalArgumentException("step must be positive, got " + step);
        }
        return (hFar - hNear) / (2.0 * step);
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
     * @param step stencil spacing in blocks (must be &gt; 0)
     * @return ∇²H
     * @throws IllegalArgumentException if step is not positive
     */
    public static double laplacian(double hC, double hN, double hS,
                                   double hW, double hE, double step) {
        if (step <= 0.0) {
            throw new IllegalArgumentException("step must be positive, got " + step);
        }
        return (hN + hS + hW + hE - 4.0 * hC) / (step * step);
    }
}
