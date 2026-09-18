package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class KarstField {
    private final long seed;
    private final GeoNoise warpNoise;
    private final GeoNoise towerNoise;

    private static final double CELL_SIZE = 120.0;
    private static final double BASE_RADIUS = 28.0;

    public KarstField(long worldSeed, GeoConfig config) {
        this.seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt() ^ 0xCA5701L, config.generatorVersion());
        long sTower = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_BASE.getSalt() ^ 0xCA5702L, config.generatorVersion());
        this.warpNoise = new GeoNoise(seed);
        this.towerNoise = new GeoNoise(sTower);
    }

    public double evaluateSinkholeRelief(double x, double z, double temperature, double humidity) {
        if (temperature < 0.45 || humidity < 0.55) {
            return 0.0;
        }

        long cx = (long) Math.floor(x / CELL_SIZE);
        long cz = (long) Math.floor(z / CELL_SIZE);

        long cellHash = SeedDerivation.hashCoords(seed, (int) cx, (int) cz);

        // Sparse gating: Only ~20% of cells develop sinkholes (§125)
        if ((cellHash & 0xFF) > 52) {
            return 0.0;
        }

        // Jitter feature center within the cell bounds [0.15 .. 0.85]
        double jitterX = (cx + 0.15 + 0.70 * ((cellHash & 0xFFFF) / 65535.0)) * CELL_SIZE;
        double jitterZ = (cz + 0.15 + 0.70 * (((cellHash >>> 16) & 0xFFFF) / 65535.0)) * CELL_SIZE;

        double dx = x - jitterX;
        double dz = z - jitterZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Organic perimeter distortion: eliminates perfect circles
        double angle = Math.atan2(dz, dx);
        double perimeterWarp = 1.0 + 0.35 * warpNoise.sample2D(Math.cos(angle) * 2.0, Math.sin(angle) * 2.0);
        double maxRadius = BASE_RADIUS * perimeterWarp;

        if (dist >= maxRadius) {
            return 0.0;
        }

        double normDist = dist / maxRadius;
        double depth = 1.0 - (normDist * normDist);
        return -26.0 * depth * (humidity * temperature);
    }

    public double evaluateTowerKarstRelief(double x, double z, double temperature, double humidity) {
        if (temperature < 0.65 || humidity < 0.70) {
            return 0.0;
        }

        double n = towerNoise.sample2D(x * 0.012, z * 0.012);
        if (n > 0.74) {
            double pillarStrength = (n - 0.74) / 0.26;
            return 45.0 * Math.pow(pillarStrength, 0.5);
        }
        return 0.0;
    }
}
