package com.omms.geoenginecore.field;

import com.omms.geoenginecore.hydrology.DepositionField;
import com.omms.geoenginecore.hydrology.RiverField;
import com.omms.geoenginecore.math.GeoConfig;

/**
 * Compact 2-D surface pipeline helper: H₀ → H* → H_f
 * (TECHSPEC §23-§24, §36).
 *
 * <p>Provides the three equilibrium stages — pre-carve surface,
 * incised surface, and deposited final surface — without the full
 * scratchpad machinery, for use by lightweight consumers such as
 * test probes and debug exporters.
 */
public final class SurfaceField {
    /** Fluvial incision model used by the H* stage. */
    private final RiverField riverField;
    /** Sea level used by the deposition budget. */
    private final double seaLevel;

    /**
     * @param config validated configuration
     */
    public SurfaceField(GeoConfig config) {
        this.riverField = new RiverField(config);
        this.seaLevel = config.seaLevel();
    }

    /**
     * Pre-carve surface (TECHSPEC §23).
     *
     * @param tectonic tectonic relief T
     * @param erosion erosion lowering E
     * @return H₀ = T − E
     */
    public double evaluateH0(double tectonic, double erosion) {
        return tectonic - erosion;
    }

    /**
     * Incised surface (TECHSPEC §24).
     *
     * @param h0 pre-carve surface
     * @param flowAcc flow accumulation proxy A_f
     * @param slope |∇H₀|
     * @param climateMult bounded climate multiplier
     * @return H* = H₀ − R
     */
    public double evaluateHStar(double h0, double flowAcc, double slope, double climateMult) {
        double incision = riverField.computeIncision(flowAcc, slope, climateMult);
        return h0 - incision;
    }

    /**
     * Final surface with equilibrium deposition (TECHSPEC §36).
     *
     * @param hStar incised surface
     * @param erosion total erosion lowering feeding the deposition budget
     * @param incision river incision R
     * @param slope |∇H|
     * @param laplacian ∇²H
     * @param age geological age factor in [0, 1]
     * @return H_f = H* + S
     */
    public double evaluateHf(double hStar, double erosion, double incision, double slope, double laplacian, double age) {
        double deposition = DepositionField.computeDeposition(
            erosion, incision, slope, laplacian, hStar - seaLevel, age
        );
        return hStar + deposition;
    }
}
