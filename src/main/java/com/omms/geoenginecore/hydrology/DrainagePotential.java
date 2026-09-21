package com.omms.geoenginecore.hydrology;

/**
 * Flow-potential heuristic used for basin detection (TECHSPEC §34).
 *
 * <p>Φ = (0.2 · drop + 1.5 · slope) · K, where drop is the elevation
 * difference to the local minimum. A column far above its local
 * minimum — or on any slope at all — cannot be a valid depositional
 * basin, which keeps the detector conservative.
 */
public final class DrainagePotential {
    /** Hides the implicit constructor. This is a static utility class. */
    private DrainagePotential() {}

    /**
     * @param h0 pre-carve surface elevation
     * @param localMinH lowest H₀ in the detection neighborhood
     * @param slope |∇H| at the column
     * @param climateRunoff bounded climate multiplier K
     * @return flow potential Φ ≥ 0
     */
    public static double compute(double h0, double localMinH, double slope, double climateRunoff) {
        double drop = Math.max(0.0, h0 - localMinH);
        return (drop * 0.2 + slope * 1.5) * climateRunoff;
    }
}
