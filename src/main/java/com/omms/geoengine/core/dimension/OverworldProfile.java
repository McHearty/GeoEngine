package com.geoengine.core.dimension;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;
import com.geoengine.core.math.ScalarFieldKernel;
import com.geoengine.core.memory.WorkerScratchpad;

public final class OverworldProfile implements DimensionProfile {
    private final GeoConfig config;
    private final ScalarFieldKernel kernel;

    public OverworldProfile(long worldSeed, int version) {
        this.config = GeoConfig.defaultOverworld(version);
        this.kernel = new ScalarFieldKernel(worldSeed, this.config);
    }

    @Override
    public DimensionType getDimensionType() {
        return DimensionType.OVERWORLD;
    }

    @Override
    public GeoConfig getConfig() {
        return config;
    }

    @Override
    public int getFluidLevel() {
        return config.seaLevel();
    }

    @Override
    public double evaluateSurface(double x, double z, GeoSample sample) {
        return kernel.evaluateH0(x, z, sample);
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        return kernel.evaluateDensity(scratchpad, worldX, worldY, worldZ);
    }

    public ScalarFieldKernel getKernel() {
        return kernel;
    }
}
