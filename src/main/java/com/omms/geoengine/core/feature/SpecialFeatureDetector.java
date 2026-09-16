package com.geoengine.core.feature;

import com.geoengine.core.geomorphology.LandformBits;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;

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
        if (sample.riverIncision > 3.5 && sample.finalSurface > seaLevel + 6.0) {
            if (downstreamSlopeDrop > 8.0 && sample.gradMagnitude > 0.65) {
                return FeatureType.WATERFALL_CREST;
            }
        }

        if (sample.finalSurface > seaLevel + 120.0 && sample.riverIncision < 1.0) {
            boolean isValley = (sample.classificationBits & LandformBits.SHAPE_MASK) == LandformBits.SHAPE_VALLEY;
            if (isValley && sample.humidity > 0.65 && sample.gradMagnitude > 0.35) {
                return FeatureType.MOUNTAIN_SPRING;
            }
        }

        if (sample.rawTectonic > 280.0 && sample.temperature > 0.75 && sample.humidity < 0.20) {
            if (sample.gradMagnitude < 0.20 && sample.erosionLowering > 15.0) {
                return FeatureType.GEOTHERMAL_VENT;
            }
        }

        return FeatureType.NONE;
    }
}
