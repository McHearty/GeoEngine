package com.geoengine.core.geomorphology;

/**
 * Computes local surface curvature from a sampled height field.
 *
 * <p>The surface is approximated locally by its 2D Hessian in the X/Z plane. The solver derives
 * the principal curvatures from the Hessian eigenvalues and also returns its mean and Gaussian
 * curvature.
 */
public final class HessianSolver {
    private HessianSolver() {}

    /**
     * Reusable output container for the curvature quantities computed by {@link #solve}.
     */
    public static final class CurvatureResult {
        /** First principal curvature, corresponding to the larger Hessian eigenvalue. */
        public double lambda1;

        /** Second principal curvature, corresponding to the smaller Hessian eigenvalue. */
        public double lambda2;

        /** Mean curvature derived from the Hessian trace. */
        public double meanCurvature;

        /** Gaussian curvature derived from the Hessian determinant. */
        public double gaussianCurv;
    }

    /**
     * Computes curvature from a center sample and its eight neighboring height samples.
     *
     * <p>The second derivatives are approximated with centered finite differences using the
     * supplied sample spacing. The mixed derivative uses the four diagonal neighbors.
     *
     * <p>The principal curvatures are the eigenvalues of the resulting 2x2 Hessian:
     *
     * <pre>{@code
     * [ hxx  hxz ]
     * [ hxz  hzz ]
     * }</pre>
     *
     * @param hC center height
     * @param hN north-neighbor height
     * @param hS south-neighbor height
     * @param hW west-neighbor height
     * @param hE east-neighbor height
     * @param hNW northwest-neighbor height
     * @param hNE northeast-neighbor height
     * @param hSW southwest-neighbor height
     * @param hSE southeast-neighbor height
     * @param delta horizontal sample spacing
     * @param out output object populated with the computed curvature values
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
