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

    public FeatureType detectFeature(GeoSample sample, double downstreamSlopeDrop) {
        if (sample.riverIncision > 3.0 && sample.finalSurface > seaLevel + 6.0) {
            if (downstreamSlopeDrop > 6.0 && sample.gradMagnitude > 0.60) {
                return FeatureType.WATERFALL_CREST;
            }
        }

        if (sample.finalSurface > seaLevel + 110.0 && sample.riverIncision < 1.0) {
            boolean isValley = LandformBits.getType(sample.classificationBits) == LandformType.VALLEY;
            if (isValley && sample.humidity > 0.65 && sample.gradMagnitude > 0.30) {
                return FeatureType.MOUNTAIN_SPRING;
            }
        }

        if (sample.rawTectonic > 260.0 && sample.temperature > 0.70 && sample.humidity < 0.25) {
            if (sample.gradMagnitude < 0.22 && sample.erosionLowering > 12.0) {
                return FeatureType.GEOTHERMAL_VENT;
            }
        }

        return FeatureType.NONE;
    }
}
