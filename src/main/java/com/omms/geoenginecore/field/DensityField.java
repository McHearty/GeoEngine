package com.omms.geoenginecore.field;

public final class DensityField {
    private DensityField() {}

    public static float evaluate(double hf, int worldY, double warp, double cave) {
        double density = hf - ((double) worldY + warp) - cave;
        if (Double.isNaN(density) || Double.isInfinite(density)) {
            return -1.0f;
        }
        return (float) density;
    }

    public static boolean verifyMonotonicity(double hf, int y1, int y2) {
        float d1 = evaluate(hf, y1, 0.0, 0.0);
        float d2 = evaluate(hf, y2, 0.0, 0.0);
        return Math.abs((d2 - d1) - (y1 - y2)) < 1e-5;
    }
}
