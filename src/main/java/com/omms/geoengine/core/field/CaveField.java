package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class CaveField {
    private final GeoNoise primaryNoise;
    private final GeoNoise secondaryNoise;
    private static final double COVER_MIN = 8.0;
    private static final double COVER_MAX = 24.0;

    public CaveField(long worldSeed, GeoConfig config) {
        long s1 = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt(), config.generatorVersion());
        long s2 = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt() ^ 0xFEEDFACECAFEL, config.generatorVersion());
        this.primaryNoise = new GeoNoise(s1);
        this.secondaryNoise = new GeoNoise(s2);
    }

    public double evaluateCave(double x, double y, double z, double surfaceHeight) {
        double overburden = surfaceHeight - y;
        if (overburden <= COVER_MIN) {
            return 0.0;
        }

        double coverMask = smoothStep(COVER_MIN, COVER_MAX, overburden);
        double n1 = primaryNoise.sample2D(x * 0.015, (z + y * 0.5) * 0.015);
        double n2 = secondaryNoise.sample2D((x - y * 0.5) * 0.015, z * 0.015);
        
        double tunnel = (n1 * n1) + (n2 * n2);
        if (tunnel < 0.08) {
            double voidIntensity = (0.08 - tunnel) / 0.08 * 32.0;
            return voidIntensity * coverMask;
        }

        return 0.0;
    }

    private static double smoothStep(double edge0, double edge1, double x) {
        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
