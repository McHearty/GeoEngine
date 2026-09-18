package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class WarpField {
    private final GeoNoise noiseA;
    private final GeoNoise noiseB;
    private final double maxWarp;
    private final double minY;
    private final double maxY;
    private final double altitudeExponent;

    public WarpField(long worldSeed, GeoConfig config) {
        long s1 = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.WARP.getSalt(), config.generatorVersion());
        long s2 = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.WARP.getSalt() ^ 0x510BE71L, config.generatorVersion());

        this.noiseA = new GeoNoise(s1);
        this.noiseB = new GeoNoise(s2);
        this.maxWarp = config.maxWarpAmplitude();
        this.minY = config.worldMinY();
        this.maxY = config.worldMaxY();
        this.altitudeExponent = 2.0;
    }

    public double evaluateWarp(double x, double y, double z, double slope) {
        double slopeFactor = Math.clamp(slope * 1.8, 0.05, 1.0);
        double normY = Math.clamp((y - minY) / (maxY - minY), 0.0, 1.0);
        double altitudeDamping = 1.0 - Math.pow(normY, altitudeExponent);

        double n1 = noiseA.sample2D(x * 0.035, (z + y * 0.25) * 0.035);
        double n2 = noiseB.sample2D((x - y * 0.25) * 0.07, z * 0.07) * 0.5;

        double combinedNoise = (n1 + n2) / 1.5;
        double effectiveBound = maxWarp * slopeFactor * altitudeDamping;

        return Math.clamp(combinedNoise * effectiveBound, -effectiveBound, effectiveBound);
    }

    public double getMaxWarp() {
        return maxWarp;
    }
}
