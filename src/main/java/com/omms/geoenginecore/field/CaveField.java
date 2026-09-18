package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class CaveField {
    private final GeoNoise tunnelNoiseA;
    private final GeoNoise tunnelNoiseB;
    private final GeoNoise chamberNoise;
    private final GeoNoise occupancyNoise;

    private final int caveMinY;
    private final int caveMaxY;

    private static final double COVER_MIN = 8.0;
    private static final double COVER_MAX = 20.0;

    public CaveField(long worldSeed, GeoConfig config) {
        long sA = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt(), config.generatorVersion());
        long sB = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt() ^ 0xCAFE01L, config.generatorVersion());
        long sChamber = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt() ^ 0xCAFE02L, config.generatorVersion());
        long sOcc = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt() ^ 0xCAFE03L, config.generatorVersion());

        this.tunnelNoiseA = new GeoNoise(sA);
        this.tunnelNoiseB = new GeoNoise(sB);
        this.chamberNoise = new GeoNoise(sChamber);
        this.occupancyNoise = new GeoNoise(sOcc);

        this.caveMinY = config.caveMinY();
        this.caveMaxY = config.caveMaxY();
    }

    public boolean hasCavePotential(int chunkWorldX, int sectionMinY, int chunkWorldZ) {
        int sectionMaxY = sectionMinY + 16;
        if (sectionMaxY <= caveMinY || sectionMinY >= caveMaxY) {
            return false;
        }

        double cx = (chunkWorldX + 8) * 0.0078125;
        double cy = (sectionMinY + 8) * 0.0078125;
        double cz = (chunkWorldZ + 8) * 0.0078125;

        double occSample = occupancyNoise.sample2D(cx, cz + cy * 0.5);
        return occSample > 0.05; // ~45% of subterranean sections contain caves; 55% are bulk SOLID
    }

    public double evaluateCave(double x, double y, double z, double surfaceHeight) {
        if (y < caveMinY || y > caveMaxY) {
            return 0.0;
        }

        double overburden = surfaceHeight - y;
        if (overburden <= COVER_MIN) {
            return 0.0;
        }

        double coverMask = smoothStep(COVER_MIN, COVER_MAX, overburden);

        double tA = tunnelNoiseA.sample2D(x * 0.016, (z + y * 0.45) * 0.016);
        double tB = tunnelNoiseB.sample2D((x - y * 0.45) * 0.016, z * 0.016);
        double tunnelMetric = (tA * tA) + (tB * tB);

        double voidIntensity = 0.0;
        if (tunnelMetric < 0.065) {
            voidIntensity = (0.065 - tunnelMetric) / 0.065 * 24.0;
        }

        double chamberSample = chamberNoise.sample2D(x * 0.008, (z - y * 0.3) * 0.008);
        if (chamberSample > 0.65) {
            double chamberVoid = (chamberSample - 0.65) / 0.35 * 38.0;
            voidIntensity = Math.max(voidIntensity, chamberVoid);
        }

        return voidIntensity * coverMask;
    }

    private static double smoothStep(double edge0, double edge1, double x) {
        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - (2.0 * t));
    }
}
