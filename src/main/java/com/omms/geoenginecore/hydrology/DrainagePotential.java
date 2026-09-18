package com.omms.geoenginecore.hydrology;

public final class DrainagePotential {
    private DrainagePotential() {}

    public static double compute(double h0, double localMinH, double slope, double climateRunoff) {
        double drop = Math.max(0.0, h0 - localMinH);
        return (drop * 0.2 + slope * 1.5) * climateRunoff;
    }
}
