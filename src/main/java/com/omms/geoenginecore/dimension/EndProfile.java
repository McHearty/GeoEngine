package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

/**
 * End dimension profile (TECHSPEC §90-§91).
 *
 * <p>The End is non-terrestrial: the surface is built from
 * monolithic, fracture-partitioned terrain rather than crustal
 * uplift. Voronoi fracture is the only active process family; fluvial,
 * glacial, karst, aeolian, coastal, and volcanic processes are
 * disabled, and the dimension has no sea level (the fluid level is
 * pinned to the world floor).
 */
public final class EndProfile implements DimensionProfile {
    /** Validated End configuration. */
    private final GeoConfig config;

    /**
     * @param version generator version stored in the configuration
     */
    public EndProfile(int version) {
        this.config = new GeoConfig(
            version, 2, -64, 256, -64,
            0.001, 0.002, 0.003,
            64.0, 32.0, 16.0,
            1.5,
            0.002, 10.0, 0.35,
            0.001,
            0.0001, 0.0001,
            0.0, 0.0,
            0.0,
            0.0,
            8.0,
            16,
            0.0, 0.01,
            1,                  // drainageIterations: single pass
            0, 0
        );
    }

    /**
     * @return {@link DimensionType#THE_END}
     */
    @Override public DimensionType getDimensionType() { return DimensionType.THE_END; }

    /**
     * @return the validated End configuration
     */
    @Override public GeoConfig getConfig() { return config; }

    /**
     * @return -64, the world floor: the End has no sea level
     */
    @Override public int getFluidLevel() { return -64; }

    /**
     * @return false: the End has no fluvial hydrology
     */
    @Override public boolean hasFluvialHydrology() { return false; }

    /**
     * @return false: the End has no glacial processes
     */
    @Override public boolean hasGlacialProcesses() { return false; }

    /**
     * @return false: the End has no karst processes
     */
    @Override public boolean hasKarstProcesses() { return false; }

    /**
     * @return false: the End has no aeolian processes
     */
    @Override public boolean hasAeolianProcesses() { return false; }

    /**
     * @return false: the End has no coastal processes
     */
    @Override public boolean hasCoastalProcesses() { return false; }

    /**
     * @return false: the End has no volcanic processes
     */
    @Override public boolean hasVolcanicProcesses() { return false; }

    /**
     * @return true: Voronoi fracture partitions the End terrain
     */
    @Override public boolean hasVoronoiFracture() { return true; }
}
