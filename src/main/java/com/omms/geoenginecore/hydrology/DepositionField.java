package com.omms.geoenginecore.hydrology;

public final class DepositionField {
    private DepositionField() {}

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
