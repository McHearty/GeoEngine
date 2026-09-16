package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class WarpField {
    private final GeoNoise noise;
    private final double maxWarp;
    private final double minY;
    private final double maxY;
    private final double altitudeExponent;

    public WarpField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.WARP.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.maxWarp = config.maxWarpAmplitude();
        this.minY = config.worldMinY();
        this.maxY = config.worldMaxY();
        this.altitudeExponent = 2.0;
    }

    public double evaluateWarp(double x, double y, double z) {
        double normY = Math.clamp((y - minY) / (maxY - minY), 0.0, 1.0);
        double damping = 1.0 - Math.pow(normY, altitudeExponent);
        double raw = noise.sample2D(x * 0.02, z * 0.02);
        return Math.clamp(raw * maxWarp * damping, -maxWarp, maxWarp);
    }
}
