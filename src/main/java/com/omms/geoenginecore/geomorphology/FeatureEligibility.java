package com.omms.geoenginecore.geomorphology;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;

/**
 * Special-feature eligibility gates (TECHSPEC §99-§104).
 *
 * <p>Each gate combines the required process/environment
 * classification bits with geometry and climate conditions, so a
 * feature is only proposed where its parent process actually
 * applies.
 */
public final class FeatureEligibility {
    /** Active validated configuration. */
    private final GeoConfig config;

    /**
     * @param config validated configuration supplying the sea level
     */
    public FeatureEligibility(GeoConfig config) {
        this.config = config;
    }

    /**
     * River eligibility (TECHSPEC §99): fluvial process and at or
     * above the water table.
     *
     * @param sample pipeline sample of the column
     * @return true when the column can host a river channel
     */
    public boolean isRiverEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_FLUVIAL) != 0
            && sample.finalSurface >= config.seaLevel() - 4.0;
    }

    /**
     * Waterfall eligibility (TECHSPEC §103): river-eligible column on
     * a steep canyon with a large gradient drop.
     *
     * @param sample pipeline sample of the column
     * @return true when the column can host a waterfall
     */
    public boolean isWaterfallEligible(GeoSample sample) {
        return isRiverEligible(sample)
            && sample.gradMagnitude > 0.85
            && LandformBits.getType(sample.classificationBits) == LandformType.CANYON;
    }

    /**
     * Dune field eligibility (TECHSPEC §104): aeolian process in
     * lowland environment above sea level.
     *
     * @param sample pipeline sample of the column
     * @return true when the column can host a dune field
     */
    public boolean isDuneFieldEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_AEOLIAN) != 0
            && (sample.classificationBits & LandformBits.ENV_LOWLAND) != 0
            && sample.finalSurface > config.seaLevel();
    }

    /**
     * Glacier eligibility (TECHSPEC §104): glacial process in
     * alpine environment.
     *
     * @param sample pipeline sample of the column
     * @return true when the column can host a glacier
     */
    public boolean isGlacierEligible(GeoSample sample) {
        return (sample.classificationBits & LandformBits.PROCESS_GLACIAL) != 0
            && (sample.classificationBits & LandformBits.ENV_ALPINE) != 0;
    }

    /**
     * Karst sinkhole eligibility (TECHSPEC §102): basin landform in
     * warm, wet conditions sufficiently inland.
     *
     * @param sample pipeline sample of the column
     * @return true when the column can host a sinkhole
     */
    public boolean isKarstSinkholeEligible(GeoSample sample) {
        LandformType type = LandformBits.getType(sample.classificationBits);
        return type == LandformType.BASIN
            && sample.temperature > 0.45
            && sample.humidity > 0.60
            && sample.finalSurface > config.seaLevel() + 16.0;
    }

    /**
     * Structure foundability (TECHSPEC §99): flat or plateau
     * landform, gentle slope, above water.
     *
     * @param sample pipeline sample of the column
     * @return true when the column can host a player structure
     */
    public boolean isStructureFoundable(GeoSample sample) {
        LandformType type = LandformBits.getType(sample.classificationBits);
        boolean flatOrPlateau = (type == LandformType.PLAINS || type == LandformType.PLATEAU || type == LandformType.MESA);
        return flatOrPlateau && sample.gradMagnitude < 0.18 && sample.finalSurface > config.seaLevel() + 2.0;
    }
}
