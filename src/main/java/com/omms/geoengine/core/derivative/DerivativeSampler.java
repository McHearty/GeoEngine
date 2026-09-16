package com.geoengine.core.derivative;

/**
 * Provides finite-difference calculations for terrain height samples.
 */
public final class DerivativeSampler {
    private DerivativeSampler() {}

    /**
     * Calculates the X-axis gradient using north and south height samples.
     *
     * @param hNorth height at the north sample
     * @param hSouth height at the south sample
     * @param step distance between the north and south samples
     * @return X-axis gradient
     */
    public static double gradientX(double hNorth, double hSouth, double step) {
        return (hSouth - hNorth) / step;
    }

    /**
     * Calculates the Z-axis gradient using west and east height samples.
     *
     * @param hWest height at the west sample
     * @param hEast height at the east sample
     * @param step distance between the west and east samples
     * @return Z-axis gradient
     */
    public static double gradientZ(double hWest, double hEast, double step) {
        return (hEast - hWest) / step;
    }

    /**
     * Calculates the magnitude of a two-dimensional gradient vector.
     *
     * @param gx X-axis gradient
     * @param gz Z-axis gradient
     * @return gradient magnitude
     */
    public static double magnitude(double gx, double gz) {
        return Math.sqrt(gx * gx + gz * gz);
    }

    /**
     * Calculates the discrete five-point Laplacian of a height field.
     *
     * @param hC height at the center sample
     * @param hN height at the north sample
     * @param hS height at the south sample
     * @param hW height at the west sample
     * @param hE height at the east sample
     * @param delta distance between adjacent samples
     * @return discrete Laplacian at the center sample
     */
    public static double laplacian(
            double hC,
            double hN,
            double hS,
            double hW,
            double hE,
            double delta) {
        double d2 = delta * delta;
        return (hN + hS + hW + hE - 4.0 * hC) / d2;
    }
}
