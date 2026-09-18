package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class EpochField {
    private final GeoNoise noise;
    private final double frequency;

    public EpochField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EPOCH.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.frequency = config.epochFrequency();
    }

    public double evaluateAge(double x, double z) {
        double raw = noise.sample2D(x * frequency, z * frequency);
        return Math.clamp((raw + 1.0) * 0.5, 0.0, 1.0);
    }
}
