package com.omms.geoenginecore.geomorphology;

/**
 * 9-point Hessian solver and curvature eigenanalysis
 * (TECHSPEC §100, §101).
 *
 * <p>The Hessian is assembled from central second differences; the
 * mixed derivative uses a difference of opposite diagonal pairs,
 * divided by 4Δ² so it is scale-consistent with the pure second
 * derivatives. The eigenvalues (λ₁, λ₂) expose the curvature sign
 * structure: both positive → concave (basin), both negative →
 * convex (summit), mixed → saddle.
 */
public final class HessianSolver {
    /** Hides the implicit constructor. This is a static utility class. */
    private HessianSolver() {}

    /**
     * Reusable curvature result (TECHSPEC §66); do not share between
     * threads.
     */
    public static final class CurvatureResult {
        /** Principal curvature with the larger (less negative) eigenvalue. */
        public double lambda1;
        /** Principal curvature with the smaller eigenvalue. */
        public double lambda2;
        /** 0.5 · trace(H). */
        public double meanCurvature;
        /** det(H). */
        public double gaussianCurv;
    }

    /**
     * Solves the 9-point Hessian and fills {@code out}
     * (TECHSPEC §100, §101).
     *
     * @param hC center cell elevation
     * @param hN northern neighbor elevation
     * @param hS southern neighbor elevation
     * @param hW western neighbor elevation
     * @param hE eastern neighbor elevation
     * @param hNW northwest diagonal elevation
     * @param hNE northeast diagonal elevation
     * @param hSW southwest diagonal elevation
     * @param hSE southeast diagonal elevation
     * @param delta stencil spacing in blocks
     * @param out scratch struct to fill
     */
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
