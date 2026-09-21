package com.omms.geoenginecore.derivative;

/**
 * Reusable curvature result (TECHSPEC §37, §100).
 *
 * <p>The Laplacian uses the 5-point stencil and the mean curvature is
 * half of it; the Gaussian curvature is reserved for the full 9-point
 * Hessian solver ({@code HessianSolver}) and stays zero here.
 * Instances are reused across evaluations under the
 * complete-overwrite contract (TECHSPEC §66) and must not be shared
 * between threads.
 */
public final class Curvature {
    /** 5-point Laplacian ∇²H. */
    public double laplacian;
    /** Half of the Laplacian, the 2-D mean curvature. */
    public double meanCurvature;
    /** Reserved for the Hessian-based Gaussian curvature; zero in this class. */
    public double gaussianCurvature;

    /**
     * Recomputes all components from the 5-point stencil.
     *
     * @param hC elevation at the column center
     * @param hN northern neighbor elevation
     * @param hS southern neighbor elevation
     * @param hW western neighbor elevation
     * @param hE eastern neighbor elevation
     * @param delta stencil spacing in blocks
     */
    public void evaluate(double hC, double hN, double hS, double hW, double hE, double delta) {
        this.laplacian = (hN + hS + hW + hE - 4.0 * hC) / (delta * delta);
        this.meanCurvature = this.laplacian * 0.5;
        this.gaussianCurvature = 0.0;
    }
}
