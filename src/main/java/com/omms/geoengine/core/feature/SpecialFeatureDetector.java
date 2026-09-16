package com.geoengine.core.feature;

import com.geoengine.core.geomorphology.LandformBits;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;

/**
 * Detects deterministic special terrain features from an evaluated terrain sample.
 *
 * <p>Feature detection uses the sample's terrain, geomorphological, and environmental properties
 * together with the supplied downstream slope change. This class identifies feature eligibility;
 * it does not generate or place the corresponding world features.
 */
public final class SpecialFeatureDetector {

    /**
     * Special feature classifications produced by {@link #detectFeature}.
     */
    public enum FeatureType {
        /** No special feature satisfies the configured detection rules. */
        NONE,

        /** A steep river section with a sufficient downstream elevation drop. */
        WATERFALL_CREST,

        /** A humid, steep valley source at elevated terrain. */
        MOUNTAIN_SPRING,

        /** A flat, warm, dry location associated with strong tectonic and erosional activity. */
        GEOTHERMAL_VENT
    }

    /** Sea-level reference used by the elevation thresholds in the detection rules. */
    private final int seaLevel;

    /**
     * Creates a detector using the sea level from the terrain configuration.
     *
     * @param config terrain-generation configuration providing the sea-level reference
     */
    public SpecialFeatureDetector(GeoConfig config) {
        this.seaLevel = config.seaLevel();
    }

    /**
     * Detects the first special feature whose eligibility conditions are satisfied.
     *
     * <p>Checks are evaluated in declaration order: waterfall crest, mountain spring, then
     * geothermal vent. If more than one rule matches, the first matching feature is returned.
     *
     * @param sample evaluated terrain sample containing the classification and environmental data
     * @param downstreamSlopeDrop elevation drop used by the waterfall-crest test
     * @return the first matching {@link FeatureType}, or {@link FeatureType#NONE} if no rule matches
     */
    public FeatureType detectFeature(GeoSample sample, double downstreamSlopeDrop) {
        if (sample.riverIncision > 3.5 && sample.finalSurface > seaLevel + 6.0) {
            if (downstreamSlopeDrop > 8.0 && sample.gradMagnitude > 0.65) {
                return FeatureType.WATERFALL_CREST;
            }
        }

        if (sample.finalSurface > seaLevel + 120.0 && sample.riverIncision < 1.0) {
            boolean isValley =
                (sample.classificationBits & LandformBits.SHAPE_MASK) == LandformBits.SHAPE_VALLEY;
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
