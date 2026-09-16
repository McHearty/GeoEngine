package com.geoengine.core.derivative;

public final class DerivativeSampler {
    private DerivativeSampler() {}

    public static double gradientX(double hNorth, double hSouth, double step) {
        return (hSouth - hNorth) / step;
    }

    public static double gradientZ(double hWest, double hEast, double step) {
        return (hEast - hWest) / step;
    }

    public static double magnitude(double gx, double gz) {
        return Math.sqrt(gx * gx + gz * gz);
    }

    public static double laplacian(double hC, double hN, double hS, double hW, double hE, double delta) {
        double d2 = delta * delta;
        return (hN + hS + hW + hE - 4.0 * hC) / d2;
    }
}
