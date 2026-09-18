package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class StressWarp {
    private final GeoNoise noiseX;
    private final GeoNoise noiseZ;
    private final double frequency;
    private final double amplitude;

    public StressWarp(long worldSeed, GeoConfig config) {
        long seedX = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.STRESS_X.getSalt(), config.generatorVersion());
        long seedZ = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.STRESS_Z.getSalt(), config.generatorVersion());
        this.noiseX = new GeoNoise(seedX);
        this.noiseZ = new GeoNoise(seedZ);
        this.frequency = config.stressFrequency();
        this.amplitude = config.stressAmplitude();
    }

    public double getWarpX(double x, double z) {
        return noiseX.sample2D(x * frequency, z * frequency) * amplitude;
    }

    public double getWarpZ(double x, double z) {
        return noiseZ.sample2D(x * frequency, z * frequency) * amplitude;
    }
}
