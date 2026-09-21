package com.omms.geoenginecore.hydrology;

/**
 * Equilibrium fluvial deposition S (TECHSPEC §33, §36).
 *
 * <p>S = E_total · f_slope · f_basin · f_alt · f_age, where the slope
 * term favors flat ground, the basin term averages concavity with a
 * low-altitude factor, and the age term grows with geomorphological
 * maturity. S is clamped to 0 ≤ S ≤ E_total so deposition can never
 * exceed the available sediment budget, and the age floor keeps
 * young terrain from instantaneously filling its basins.
 */
public final class DepositionField {
    /** Hides the implicit constructor. This is a static utility class. */
    private DepositionField() {}

    /**
     * Evaluates the deposition budget for one column (TECHSPEC §33).
     *
     * @param weatheringErosion long-term lowering E
     * @param riverIncision channel incision R
     * @param slopeMagnitude |∇H|
     * @param laplacian ∇²H
     * @param altitudeAboveSea H* − sea level
     * @param age geological age factor in [0, 1]
     * @return deposited thickness S ∈ [0, E_total]
     */
    public static double computeDeposition(
        double weatheringErosion,
        double riverIncision,
        double slopeMagnitude,
        double laplacian,
        double altitudeAboveSea,
        double age
    ) {
        double eTotal = weatheringErosion + riverIncision;
        if (eTotal <= 0.0) return 0.0;

        double flatness = 1.0 / (1.0 + slopeMagnitude * 4.0);
        double basinConcavity = Math.clamp(laplacian * 2.0, 0.0, 1.0);
        double lowAltitudeFactor = Math.clamp(1.0 - (altitudeAboveSea / 150.0), 0.1, 1.0);
        double ageFactor = 0.4 + 0.6 * age;

        double depositionRatio = flatness * (0.5 * basinConcavity + 0.5 * lowAltitudeFactor) * ageFactor;
        return Math.clamp(eTotal * depositionRatio, 0.0, eTotal);
    }
}
