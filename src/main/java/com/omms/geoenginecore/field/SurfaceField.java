package com.omms.geoenginecore.field;

import com.omms.geoenginecore.hydrology.DepositionField;
import com.omms.geoenginecore.hydrology.RiverField;
import com.omms.geoenginecore.math.GeoConfig;

public final class SurfaceField {
    private final RiverField riverField;
    private final double seaLevel;

    public SurfaceField(GeoConfig config) {
        this.riverField = new RiverField(config);
        this.seaLevel = config.seaLevel();
    }

    public double evaluateH0(double tectonic, double erosion) {
        return tectonic - erosion;
    }

    public double evaluateHStar(double h0, double flowAcc, double slope, double climateMult) {
        double incision = riverField.computeIncision(flowAcc, slope, climateMult);
        return h0 - incision;
    }

    public double evaluateHf(double hStar, double erosion, double incision, double slope, double laplacian, double age) {
        double deposition = DepositionField.computeDeposition(
            erosion, incision, slope, laplacian, hStar - seaLevel, age
        );
        return hStar + deposition;
    }
}
