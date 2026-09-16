package com.geoengine.core.geomorphology;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;

/**
 * Evaluates deterministic terrain conditions for optional landform features and structures.
 *
 * <p>Eligibility is derived from the classification flags and continuous properties already
 * computed for a {@link GeoSample}. This class does not generate or place the corresponding
 * features.
 */
public final class FeatureEligibility {
    private final GeoConfig config;

    /**
     * Creates an eligibility evaluator using the supplied terrain configuration.
     *
     * @param config terrain-generation configuration
     */
    public FeatureEligibility(GeoConfig config) {
        this.config = config;
    }

    /**
     * Tests whether the sample is eligible for a river channel.
     *
     * <p>The sample must have the fluvial process flag and its surface must be no more than four
     * blocks below the configured sea level.
     *
     * @param sample terrain sample to evaluate
     * @return {@code true} when the river eligibility conditions are satisfied
     */
    public boolean isRiverEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_FLUVIAL) != 0
            && sample.finalSurface >= config.seaLevel() - 4.0;
    }

    /**
     * Tests whether the sample is eligible for a waterfall.
     *
     * <p>Waterfall eligibility requires river eligibility, a sufficiently steep gradient, and a
     * canyon shape classification.
     *
     * @param sample terrain sample to evaluate
     * @return {@code true} when the waterfall eligibility conditions are satisfied
     */
    public boolean isWaterfallEligible(GeoSample sample) {
        return isRiverEligible(sample)
            && sample.gradMagnitude > 0.85
            && (sample.classificationBits & LandformBits.SHAPE_MASK) == LandformBits.SHAPE_CANYON;
    }

    /**
     * Tests whether the sample is eligible for a dune field.
     *
     * <p>The sample must be classified as aeolian and lowland, and its surface must be above sea
     * level.
     *
     * @param sample terrain sample to evaluate
     * @return {@code true} when the dune-field eligibility conditions are satisfied
     */
    public boolean isDuneFieldEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_AEOLIAN) != 0
            && (sample.classificationBits & LandformBits.ENV_LOWLAND) != 0
            && sample.finalSurface > config.seaLevel();
    }

    /**
     * Tests whether the sample is eligible for a glacier.
     *
     * <p>The sample must have both glacial process and alpine environment classifications.
     *
     * @param sample terrain sample to evaluate
     * @return {@code true} when the glacier eligibility conditions are satisfied
     */
    public boolean isGlacierEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_GLACIAL) != 0
            && (sample.classificationBits & LandformBits.ENV_ALPINE) != 0;
    }

    /**
     * Tests whether the sample is eligible for a karst sinkhole.
     *
     * <p>The sample must have a basin shape, sufficiently warm and humid conditions, and a surface
     * at least sixteen blocks above sea level.
     *
     * @param sample terrain sample to evaluate
     * @return {@code true} when the karst sinkhole eligibility conditions are satisfied
     */
    public boolean isKarstSinkholeEligible(GeoSample sample) {
        int shape = LandformBits.getShape(sample.classificationBits);
        return shape == LandformBits.SHAPE_BASIN
            && sample.temperature > 0.45
            && sample.humidity > 0.60
            && sample.finalSurface > config.seaLevel() + 16.0;
    }

    /**
     * Tests whether the sample is eligible for structure placement.
     *
     * <p>The sample must have a flat or plateau shape, a gradient below {@code 0.18}, and a
     * surface at least two blocks above sea level.
     *
     * @param sample terrain sample to evaluate
     * @return {@code true} when the structure eligibility conditions are satisfied
     */
    public boolean isStructureFoundable(GeoSample sample) {
        int shape = LandformBits.getShape(sample.classificationBits);
        boolean flatOrPlateau =
            shape == LandformBits.SHAPE_FLAT || shape == LandformBits.SHAPE_PLATEAU;
        boolean gentleSlope = sample.gradMagnitude < 0.18;
        boolean dryLand = sample.finalSurface > config.seaLevel() + 2.0;
        return flatOrPlateau && gentleSlope && dryLand;
    }
}
