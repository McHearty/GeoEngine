package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

public interface DimensionProfile {
    DimensionType getDimensionType();
    GeoConfig getConfig();
    int getFluidLevel();

    boolean hasFluvialHydrology();
    boolean hasGlacialProcesses();
    boolean hasKarstProcesses();
    boolean hasAeolianProcesses();
    boolean hasCoastalProcesses();
    boolean hasVolcanicProcesses();
    boolean hasVoronoiFracture();
}
