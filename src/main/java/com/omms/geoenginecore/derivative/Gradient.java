package com.omms.geoenginecore.derivative;

/**
 * Reusable gradient result ∇H = (gx, gz) (TECHSPEC §37).
 *
 * <p>Mutable and allocation-free: instances are reused across
 * evaluations under the complete-overwrite contract (TECHSPEC §66)
 * and must not be shared between threads.
 */
public final class Gradient {
    /** X component of ∇H. */
    public double gx;
    /** Z component of ∇H. */
    public double gz;
    /** |∇H| magnitude. */
    public double magnitude;

    /**
     * Recomputes all components from central differences.
     *
     * @param hNorth elevation of the northern neighbor
     * @param hSouth elevation of the southern neighbor
     * @param hWest elevation of the western neighbor
     * @param hEast elevation of the eastern neighbor
     * @param step one-sided stencil spacing in blocks
     */
    public void calculate(double hNorth, double hSouth, double hWest, double hEast, double step) {
        this.gx = (hSouth - hNorth) / step;
        this.gz = (hEast - hWest) / step;
        this.magnitude = Math.sqrt(gx * gx + gz * gz);
    }
}
