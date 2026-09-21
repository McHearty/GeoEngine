package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

/**
 * Overworld dimension profile (TECHSPEC §87).
 *
 * <p>The Overworld targets the full terrestrial stack: sea level
 * around Y=64, continental relief, mountains, basins, rivers,
 * coastlines, glacial terrain, deserts, and forests. All process
 * families are enabled except Voronoi fracture.
 */
public final class OverworldProfile implements DimensionProfile {
    /** Validated Overworld configuration. */
    private final GeoConfig config;

    /**
     * Builds the calibrated Overworld baseline for a generator
     * version.
     *
     * @param version generator version stored in the configuration
     */
    public OverworldProfile(int version) {
        this.config = GeoConfig.defaultOverworld(version);
    }

    /**
     * Wraps an externally validated configuration.
     *
     * @param config validated Overworld configuration
     */
    public OverworldProfile(GeoConfig config) {
        this.config = config;
    }

    /**
     * @return {@link DimensionType#OVERWORLD}
     */
    @Override public DimensionType getDimensionType() { return DimensionType.OVERWORLD; }

    /**
     * @return the validated Overworld configuration
     */
    @Override public GeoConfig getConfig() { return config; }

    /**
     * @return the configuration's sea level, the Overworld's fluid interface
     */
    @Override public int getFluidLevel() { return config.seaLevel(); }

    /**
     * @return true: the Overworld runs fluvial hydrology
     */
    @Override public boolean hasFluvialHydrology() { return true; }

    /**
     * @return true: the Overworld runs glacial processes
     */
    @Override public boolean hasGlacialProcesses() { return true; }

    /**
     * @return true: the Overworld runs karst processes
     */
    @Override public boolean hasKarstProcesses() { return true; }

    /**
     * @return true: the Overworld runs aeolian processes
     */
    @Override public boolean hasAeolianProcesses() { return true; }

    /**
     * @return true: the Overworld runs coastal processes
     */
    @Override public boolean hasCoastalProcesses() { return true; }

    /**
     * @return true: the Overworld runs volcanic processes
     */
    @Override public boolean hasVolcanicProcesses() { return true; }

    /**
     * @return false: the Overworld uses crustal uplift, not fracture partitioning
     */
    @Override public boolean hasVoronoiFracture() { return false; }
}
