package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

/**
 * Nether dimension profile (TECHSPEC §88-§89).
 *
 * <p>The Nether uses volcanic geomorphology: lava fields, shield
 * volcanoes, calderas, and lava channels. Ordinary terrestrial
 * hydrology and the glacial, karst, and aeolian families are disabled;
 * lava is treated as a material/environmental field rather than a
 * simulated fluid, with {@link #NETHER_LAVA_LEVEL} as the
 * environmental threshold.
 */
public final class NetherProfile implements DimensionProfile {
    /** Environmental lava threshold used as the dimension's fluid level (TECHSPEC §89). */
    public static final int NETHER_LAVA_LEVEL = 32;
    /** Validated Nether configuration. */
    private final GeoConfig config;

    /**
     * @param version generator version stored in the configuration
     */
    public NetherProfile(int version) {
        this.config = new GeoConfig(
            version, 1, 0, 256, NETHER_LAVA_LEVEL,
            0.0015, 0.004, 0.007,
            35.0, 50.0, 25.0,
            1.4,
            0.002, 14.0, 0.40,
            0.001,
            0.0008, 0.0008,
            0.0, 2.0,
            0.0,
            0.0,
            12.0,
            16,
            0.0, 0.01,
            1,                  // drainageIterations: single pass
            10, 110
        );
    }

    /**
     * @return {@link DimensionType#NETHER}
     */
    @Override public DimensionType getDimensionType() { return DimensionType.NETHER; }

    /**
     * @return the validated Nether configuration
     */
    @Override public GeoConfig getConfig() { return config; }

    /**
     * @return {@link #NETHER_LAVA_LEVEL}, the lava interface
     */
    @Override public int getFluidLevel() { return NETHER_LAVA_LEVEL; }

    /**
     * @return false: ordinary terrestrial hydrology is disabled in the Nether
     */
    @Override public boolean hasFluvialHydrology() { return false; }

    /**
     * @return false: the Nether has no glacial processes
     */
    @Override public boolean hasGlacialProcesses() { return false; }

    /**
     * @return false: the Nether has no karst processes
     */
    @Override public boolean hasKarstProcesses() { return false; }

    /**
     * @return false: the Nether has no aeolian processes
     */
    @Override public boolean hasAeolianProcesses() { return false; }

    /**
     * @return false: the Nether has no coastal processes
     */
    @Override public boolean hasCoastalProcesses() { return false; }

    /**
     * @return true: volcanic geomorphology drives the Nether terrain
     */
    @Override public boolean hasVolcanicProcesses() { return true; }

    /**
     * @return false: the Nether has no Voronoi fracture
     */
    @Override public boolean hasVoronoiFracture() { return false; }
}
