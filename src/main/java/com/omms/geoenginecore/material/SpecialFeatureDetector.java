package com.omms.geoenginecore.feature;

import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.geomorphology.LandformType;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;

public final class SpecialFeatureDetector {
    public enum FeatureType {
        NONE,
        WATERFALL_CREST,
        MOUNTAIN_SPRING,
        GEOTHERMAL_VENT
    }

    private final int seaLevel;

    public SpecialFeatureDetector(GeoConfig config) {
        this.seaLevel = config.seaLevel();
    }

    /**
     * Pure geomorphic detection of non-scalar hydrological and geothermal features (§201–§203).
     */
    public FeatureType detectFeature(GeoSample sample, double downstreamSlopeDrop) {
        // 1. Waterfall Crest: River incision plunging over steep cliff scarp (§201)
        if (sample.riverIncision > 3.5 && sample.finalSurface > seaLevel + 6.0) {
            if (downstreamSlopeDrop > 8.0 && sample.gradMagnitude > 0.65) {
                return FeatureType.WATERFALL_CREST;
            }
        }

        // 2. Mountain Spring: Headwater aquifer discharge at high alpine slope breaks (§202)
        if (sample.finalSurface > seaLevel + 110.0 && sample.riverIncision < 1.0) {
            boolean isValley = LandformBits.getType(sample.classificationBits) == LandformType.VALLEY;
            if (isValley && sample.humidity > 0.65 && sample.gradMagnitude > 0.30) {
                return FeatureType.MOUNTAIN_SPRING;
            }
        }

        // 3. Geothermal Vent: High tectonic thermal plate (§203)
        if (sample.rawTectonic > 260.0 && sample.temperature > 0.70 && sample.humidity < 0.25) {
            if (sample.gradMagnitude < 0.22 && sample.erosionLowering > 12.0) {
                return FeatureType.GEOTHERMAL_VENT;
            }
        }

        return FeatureType.NONE;
    }
}
