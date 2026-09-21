package com.omms.geoenginecore.geomorphology;

import com.omms.geoenginecore.math.ScalarFieldKernel;

/**
 * Multi-scale relief (prominence) metrics (TECHSPEC §105, §106).
 *
 * <p>Four concentric four-point means at fixed radii (8, 32, 128,
 * 512 blocks) give the micro/meso/regional prominence and the
 * continental baseline. These are the only scale metrics the
 * landform grammar may use — no 3-D line-of-sight visibility, no
 * global precomputation.
 */
public final class MultiScaleRelief {
    /** Micro-prominence radius in blocks. */
    public static final double RADIUS_MICRO = 8.0;
    /** Meso-prominence radius in blocks. */
    public static final double RADIUS_MESO = 32.0;
    /** Regional-prominence radius in blocks. */
    public static final double RADIUS_REGIONAL = 128.0;
    /** Continental baseline radius in blocks. */
    public static final double RADIUS_CONTINENTAL = 512.0;

    /**
     * Reusable relief report (TECHSPEC §66); do not share between
     * threads.
     */
    public static final class ReliefReport {
        /** H₀ − mean of the micro-scale neighborhood. */
        public double microProminence;
        /** H₀ − mean of the meso-scale neighborhood. */
        public double mesoProminence;
        /** H₀ − mean of the regional-scale neighborhood. */
        public double regionalProminence;
        /** H₀ − mean of the continental-scale neighborhood. */
        public double continentalRelief;
        /** Max − min over the meso-scale cardinal neighbors. */
        public double localReliefSpan;
    }

    /** Hides the implicit constructor. This is a static utility class. */
    private MultiScaleRelief() {}

    /**
     * Fills {@code out} for one column (TECHSPEC §105, §106).
     *
     * @param kernel H₀ kernel of the current configuration
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @param currentH final surface H_f of the column
     * @param out scratch struct to fill
     */
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

    /**
     * Mean H₀ over the four cardinal points at radius r.
     *
     * @param kernel H₀ kernel of the current configuration
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @param r neighborhood radius in blocks
     * @return mean H₀ over the four cardinal neighbors
     */
    private static double sampleConcentricMean(ScalarFieldKernel kernel, double wx, double wz, double r) {
        double hN = kernel.evaluatePureH0(wx, wz - r);
        double hS = kernel.evaluatePureH0(wx, wz + r);
        double hW = kernel.evaluatePureH0(wx - r, wz);
        double hE = kernel.evaluatePureH0(wx + r, wz);
        return (hN + hS + hW + hE) * 0.25;
    }
}
