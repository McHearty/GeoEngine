package com.geoengine.core.hydrology;

/**
 * Computes a deposition amount from erosion, terrain geometry, elevation, and terrain age.
 *
 * <p>The calculation first combines weathering erosion and river incision into a total sediment
 * source, then scales that source using local flatness, basin concavity, altitude, and age.
 * Deposition is bounded to the available erosion amount.
 */
public final class DepositionField {

    private DepositionField() {}

    /**
     * Computes the amount of eroded material deposited at a terrain sample.
     *
     * <p>Non-positive total erosion produces no deposition. The resulting deposition is clamped
     * to the range {@code [0, weatheringErosion + riverIncision]}.
     *
     * @param weatheringErosion material contributed by weathering erosion
     * @param riverIncision material contributed by river incision
     * @param slopeMagnitude local slope magnitude
     * @param laplacian local surface Laplacian used as a concavity indicator
     * @param altitudeAboveSea terrain elevation relative to sea level
     * @param age normalized terrain-age factor used by the deposition model
     * @return deposited material, bounded by the total erosion input
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

        double depositionRatio =
            flatness
                * (0.5 * basinConcavity + 0.5 * lowAltitudeFactor)
                * ageFactor;
        return Math.clamp(eTotal * depositionRatio, 0.0, eTotal);
    }
}
