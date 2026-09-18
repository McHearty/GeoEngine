package com.omms.geoenginecore.geomorphology;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;

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
            && LandformBits.getType(sample.classificationBits) == LandformType.CANYON;
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
        LandformType type = LandformBits.getType(sample.classificationBits);
        return type == LandformType.BASIN
            && sample.temperature > 0.45
            && sample.humidity > 0.60
            && sample.finalSurface > config.seaLevel() + 16.0;
    }

    public boolean isStructureFoundable(GeoSample sample) {
        LandformType type = LandformBits.getType(sample.classificationBits);
        boolean flatOrPlateau = (type == LandformType.PLAINS || type == LandformType.PLATEAU || type == LandformType.MESA);
        return flatOrPlateau && sample.gradMagnitude < 0.18 && sample.finalSurface > config.seaLevel() + 2.0;
    }
}
