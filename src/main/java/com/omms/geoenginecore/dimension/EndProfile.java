package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

public final class EndProfile implements DimensionProfile {
    private final GeoConfig config;

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
            0, 0
        );
    }

    @Override public DimensionType getDimensionType() { return DimensionType.THE_END; }
    @Override public GeoConfig getConfig() { return config; }
    @Override public int getFluidLevel() { return -64; }

    @Override public boolean hasFluvialHydrology() { return false; }
    @Override public boolean hasGlacialProcesses() { return false; }
    @Override public boolean hasKarstProcesses() { return false; }
    @Override public boolean hasAeolianProcesses() { return false; }
    @Override public boolean hasCoastalProcesses() { return false; }
    @Override public boolean hasVolcanicProcesses() { return false; }
    @Override public boolean hasVoronoiFracture() { return true; }
}
