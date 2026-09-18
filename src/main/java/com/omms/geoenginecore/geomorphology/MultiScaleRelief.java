package com.omms.geoenginecore.geomorphology;

import com.omms.geoenginecore.math.ScalarFieldKernel;

public final class MultiScaleRelief {
    public static final double RADIUS_MICRO = 8.0;
    public static final double RADIUS_MESO = 32.0;
    public static final double RADIUS_REGIONAL = 128.0;
    public static final double RADIUS_CONTINENTAL = 512.0;

    public static final class ReliefReport {
        public double microProminence;
        public double mesoProminence;
        public double regionalProminence;
        public double continentalRelief;
        public double localReliefSpan;
    }

    private MultiScaleRelief() {}

    public static void evaluate(ScalarFieldKernel kernel, double wx, double wz, double currentH, ReliefReport out) {
        // Evaluate prominence relative to consistent crustal baseline
        double baseH = kernel.evaluatePureH0(wx, wz);

        out.microProminence = baseH - sampleConcentricMean(kernel, wx, wz, RADIUS_MICRO);
        out.mesoProminence = baseH - sampleConcentricMean(kernel, wx, wz, RADIUS_MESO);
        out.regionalProminence = baseH - sampleConcentricMean(kernel, wx, wz, RADIUS_REGIONAL);
        out.continentalRelief = baseH - sampleConcentricMean(kernel, wx, wz, RADIUS_CONTINENTAL);

        double hN = kernel.evaluatePureH0(wx, wz - RADIUS_MESO);
        double hS = kernel.evaluatePureH0(wx, wz + RADIUS_MESO);
        double hW = kernel.evaluatePureH0(wx - RADIUS_MESO, wz);
        double hE = kernel.evaluatePureH0(wx + RADIUS_MESO, wz);

        double maxH = Math.max(Math.max(hN, hS), Math.max(hW, hE));
        double minH = Math.min(Math.min(hN, hS), Math.min(hW, hE));
        out.localReliefSpan = maxH - minH;
    }

    private static double sampleConcentricMean(ScalarFieldKernel kernel, double wx, double wz, double r) {
        double hN = kernel.evaluatePureH0(wx, wz - r);
        double hS = kernel.evaluatePureH0(wx, wz + r);
        double hW = kernel.evaluatePureH0(wx - r, wz);
        double hE = kernel.evaluatePureH0(wx + r, wz);
        return (hN + hS + hW + hE) * 0.25;
    }
}
