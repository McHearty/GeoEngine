package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

public final class OverworldProfile implements DimensionProfile {
    private final GeoConfig config;

    public OverworldProfile(int version) {
        this.config = GeoConfig.defaultOverworld(version);
    }

    public OverworldProfile(GeoConfig config) {
        this.config = config;
    }

    @Override public DimensionType getDimensionType() { return DimensionType.OVERWORLD; }
    @Override public GeoConfig getConfig() { return config; }
    @Override public int getFluidLevel() { return config.seaLevel(); }

    @Override public boolean hasFluvialHydrology() { return true; }
    @Override public boolean hasGlacialProcesses() { return true; }
    @Override public boolean hasKarstProcesses() { return true; }
    @Override public boolean hasAeolianProcesses() { return true; }
    @Override public boolean hasCoastalProcesses() { return true; }
    @Override public boolean hasVolcanicProcesses() { return true; }
    @Override public boolean hasVoronoiFracture() { return false; }
}
