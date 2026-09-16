package com.geoengine.core.field.advanced;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class CoastalField {
    private final GeoNoise waveNoise;
    private final GeoNoise stackNoise;
    private final double seaLevel;

    public CoastalField(long worldSeed, GeoConfig config) {
        long sWave = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt() ^ 0xC0A57A1L, config.generatorVersion());
        long sStack = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.WARP.getSalt() ^ 0xC0A57A2L, config.generatorVersion());
        this.waveNoise = new GeoNoise(sWave);
        this.stackNoise = new GeoNoise(sStack);
        this.seaLevel = config.seaLevel();
    }

    public double evaluateWaveCutPlatform(double currentSurface) {
        double delta = currentSurface - seaLevel;
        if (delta > -4.0 && delta < 2.0) {
            double flatteningFactor = 1.0 - (Math.abs(delta) / 4.0);
            double target = seaLevel - 1.0;
            return (target - currentSurface) * (0.65 * flatteningFactor);
        }
        return 0.0;
    }

    public double evaluateSeaArchVoid(double x, double y, double z, double surfaceH, double slope) {
        if (slope < 0.50 || surfaceH < seaLevel + 6.0 || surfaceH > seaLevel + 35.0) {
            return 0.0;
        }

        if (y < seaLevel - 2 || y > seaLevel + 8) {
            return 0.0;
        }

        double verticalNotch = 1.0 - Math.abs((y - (seaLevel + 2.0)) / 5.0);
        double archNoise = waveNoise.sample2D(x * 0.04, z * 0.04);

        if (archNoise > 0.45) {
            double voidTunnel = (archNoise - 0.45) / 0.55;
            return voidTunnel * verticalNotch * 24.0;
        }
        return 0.0;
    }
}
