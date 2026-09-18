package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

public final class NetherProfile implements DimensionProfile {
    public static final int NETHER_LAVA_LEVEL = 32;
    private final GeoConfig config;

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
            10, 110
        );
    }

    @Override public DimensionType getDimensionType() { return DimensionType.NETHER; }
    @Override public GeoConfig getConfig() { return config; }
    @Override public int getFluidLevel() { return NETHER_LAVA_LEVEL; }

    @Override public boolean hasFluvialHydrology() { return false; }
    @Override public boolean hasGlacialProcesses() { return false; }
    @Override public boolean hasKarstProcesses() { return false; }
    @Override public boolean hasAeolianProcesses() { return false; }
    @Override public boolean hasCoastalProcesses() { return false; }
    @Override public boolean hasVolcanicProcesses() { return true; }
    @Override public boolean hasVoronoiFracture() { return false; }
}
