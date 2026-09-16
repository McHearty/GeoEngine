package com.geoengine.core.geomorphology;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;

public final class FeatureEligibility {
    private final GeoConfig config;

    public FeatureEligibility(GeoConfig config) {
        this.config = config;
    }

    public boolean isRiverEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_FLUVIAL) != 0
            && sample.finalSurface >= config.seaLevel() - 4.0;
    }

    public boolean isWaterfallEligible(GeoSample sample) {
        return isRiverEligible(sample)
            && sample.gradMagnitude > 0.85
            && (sample.classificationBits & LandformBits.SHAPE_MASK) == LandformBits.SHAPE_CANYON;
    }

    public boolean isDuneFieldEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_AEOLIAN) != 0
            && (sample.classificationBits & LandformBits.ENV_LOWLAND) != 0
            && sample.finalSurface > config.seaLevel();
    }

    public boolean isGlacierEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_GLACIAL) != 0
            && (sample.classificationBits & LandformBits.ENV_ALPINE) != 0;
    }

    public boolean isKarstSinkholeEligible(GeoSample sample) {
        int shape = LandformBits.getShape(sample.classificationBits);
        return shape == LandformBits.SHAPE_BASIN
            && sample.temperature > 0.45
            && sample.humidity > 0.60
            && sample.finalSurface > config.seaLevel() + 16.0;
    }

    public boolean isStructureFoundable(GeoSample sample) {
        int shape = LandformBits.getShape(sample.classificationBits);
        boolean flatOrPlateau = (shape == LandformBits.SHAPE_FLAT || shape == LandformBits.SHAPE_PLATEAU);
        boolean gentleSlope = sample.gradMagnitude < 0.18;
        boolean dryLand = sample.finalSurface > config.seaLevel() + 2.0;
        return flatOrPlateau && gentleSlope && dryLand;
    }
}
