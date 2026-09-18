package com.omms.geoenginecore.geomorphology;

public final class HessianSolver {
    private HessianSolver() {}

    public static final class CurvatureResult {
        public double lambda1;
        public double lambda2;
        public double meanCurvature;
        public double gaussianCurv;
    }

    public static void solve(
        double hC, double hN, double hS, double hW, double hE,
        double hNW, double hNE, double hSW, double hSE,
        double delta, CurvatureResult out
    ) {
        double d2 = delta * delta;
        double invD2 = 1.0 / d2;
        double inv4D2 = 1.0 / (4.0 * d2);

        double hxx = (hE - 2.0 * hC + hW) * invD2;
        double hzz = (hS - 2.0 * hC + hN) * invD2;
        double hxz = ((hSE - hSW) - (hNE - hNW)) * inv4D2;

        double trace = hxx + hzz;
        double det = (hxx * hzz) - (hxz * hxz);

        double disc = Math.max(0.0, trace * trace - 4.0 * det);
        double sqrtDisc = Math.sqrt(disc);

        out.lambda1 = 0.5 * (trace + sqrtDisc);
        out.lambda2 = 0.5 * (trace - sqrtDisc);
        out.meanCurvature = 0.5 * trace;
        out.gaussianCurv = det;
    }
}
