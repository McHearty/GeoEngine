package com.geoengine.core.field.advanced;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class KarstField {
    private final GeoNoise sinkholeLattice;
    private final GeoNoise towerNoise;

    public KarstField(long worldSeed, GeoConfig config) {
        long sSink = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt() ^ 0xCA5701L, config.generatorVersion());
        long sTower = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_BASE.getSalt() ^ 0xCA5702L, config.generatorVersion());
        this.sinkholeLattice = new GeoNoise(sSink);
        this.towerNoise = new GeoNoise(sTower);
    }

    public double evaluateSinkholeRelief(double x, double z, double temperature, double humidity) {
        if (temperature < 0.45 || humidity < 0.55) {
            return 0.0;
        }

        double cellSize = 80.0;
        long cx = Math.round(x / cellSize);
        long cz = Math.round(z / cellSize);

        double dx = x - cx * cellSize;
        double dz = z - cz * cellSize;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double maxRadius = 24.0;

        if (dist >= maxRadius) {
            return 0.0;
        }

        double normDist = dist / maxRadius;
        double depth = (1.0 - normDist * normDist);
        return -22.0 * depth * (humidity * temperature);
    }

    public double evaluateTowerKarstRelief(double x, double z, double temperature, double humidity) {
        if (temperature < 0.65 || humidity < 0.70) {
            return 0.0;
        }

        double n = towerNoise.sample2D(x * 0.012, z * 0.012);
        if (n > 0.72) {
            double pillarStrength = (n - 0.72) / 0.28;
            return 45.0 * Math.pow(pillarStrength, 0.5);
        }
        return 0.0;
    }
}
