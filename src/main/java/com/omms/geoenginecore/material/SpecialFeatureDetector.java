package com.omms.geoenginecore.feature;

import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.geomorphology.LandformType;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;

/**
 * Pure geomorphic detector for non-scalar special features
 * (TECHSPEC §201-§203).
 *
 * <p>Detection uses only the pipeline sample and the downstream
 * slope drop: waterfall crests (deep incision plunging over a steep
 * scarp), mountain springs (headwater aquifer discharge at alpine
 * slope breaks), and geothermal vents (hot, dry, high-tectonic
 * plates on gentle, strongly eroded ground). The result never
 * depends on block identity, chunk layout, or threading.
 */
public final class SpecialFeatureDetector {
    /** Special feature taxonomy. */
    public enum FeatureType {
        /** No feature detected. */
        NONE,
        /** Waterfall crest: river plunging over a steep scarp (TECHSPEC §201). */
        WATERFALL_CREST,
        /** Mountain spring: headwater aquifer discharge (TECHSPEC §202). */
        MOUNTAIN_SPRING,
        /** Geothermal vent: high tectonic thermal plate (TECHSPEC §203). */
        GEOTHERMAL_VENT
    }

    /** Coastline reference: the dimension's sea level. */
    private final int seaLevel;

    /**
     * @param config validated configuration supplying the sea level
     */
    public SpecialFeatureDetector(GeoConfig config) {
        this.seaLevel = config.seaLevel();
    }

    /**
     * Pure geomorphic detection of non-scalar hydrological and
     * geothermal features (TECHSPEC §201-§203).
     *
     * @param sample pipeline sample of the column
     * @param downstreamSlopeDrop elevation drop along the flow direction over the feature reach
     * @return the detected feature, or {@link FeatureType#NONE}
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
