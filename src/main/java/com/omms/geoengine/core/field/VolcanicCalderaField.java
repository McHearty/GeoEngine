package com.geoengine.core.field;

import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class VolcanicCalderaField {
    private final GeoNoise ventNoise;
    private static final double CELL_SIZE = 280.0;

    public VolcanicCalderaField(long worldSeed, int version) {
        long seed = SeedDerivation.derive(worldSeed, 1, NoiseDomain.TECTONIC_DETAIL_A.getSalt(), version);
        this.ventNoise = new GeoNoise(seed);
    }

    public double evaluateVolcanicRelief(double x, double z) {
        long cellX = Math.round(x / CELL_SIZE);
        long cellZ = Math.round(z / CELL_SIZE);

        double centerX = cellX * CELL_SIZE;
        double centerZ = cellZ * CELL_SIZE;

        double dx = x - centerX;
        double dz = z - centerZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double radius = 96.0;

        if (dist > radius * 1.5) {
            return 0.0;
        }

        double normDist = dist / radius;
        if (normDist < 0.28) {
            double depression = 1.0 - (normDist / 0.28);
            return -38.0 * depression * depression;
        } else if (normDist <= 0.85) {
            double rim = Math.sin((normDist - 0.28) / 0.57 * Math.PI);
            return 52.0 * rim;
        } else {
            double falloff = 1.0 - ((normDist - 0.85) / 0.65);
            return 14.0 * Math.max(0.0, falloff);
        }
    }
}
