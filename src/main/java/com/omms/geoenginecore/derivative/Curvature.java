package com.omms.geoenginecore.derivative;

public final class Curvature {
    public double laplacian;
    public double meanCurvature;
    public double gaussianCurvature;

    public void evaluate(double hC, double hN, double hS, double hW, double hE, double delta) {
        this.laplacian = (hN + hS + hW + hE - 4.0 * hC) / (delta * delta);
        this.meanCurvature = this.laplacian * 0.5;
        this.gaussianCurvature = 0.0;
    }
}
