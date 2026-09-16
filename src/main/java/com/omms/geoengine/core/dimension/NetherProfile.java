package com.geoengine.core.dimension;

import com.geoengine.core.field.VolcanicCalderaField;
import com.geoengine.core.field.WarpField;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;
import com.geoengine.core.memory.WorkerScratchpad;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class NetherProfile implements DimensionProfile {
    public static final int NETHER_LAVA_LEVEL = 32;

    private final GeoConfig config;
    private final GeoNoise plainsNoise;
    private final GeoNoise ridgeNoise;
    private final GeoNoise lavaTubeNoiseA;
    private final GeoNoise lavaTubeNoiseB;
    private final VolcanicCalderaField volcanicField;
    private final WarpField warpField;

    private static final double PLAINS_BASE = 48.0;

    public NetherProfile(long worldSeed, int version) {
        this.config = new GeoConfig(
            version, 1, 0, 256, NETHER_LAVA_LEVEL,
            0.0015, 0.004, 0.007,
            35.0, 50.0, 25.0,
            1.4,
            0.002, 14.0, 0.40,
            0.001,
            0.0008, 0.0008,
            0.0, 2.0,
            0.0,
            0.0,
            12.0,
            16
        );

        long sPlains = SeedDerivation.derive(worldSeed, 1, NoiseDomain.TECTONIC_BASE.getSalt(), version);
        long sRidge = SeedDerivation.derive(worldSeed, 1, NoiseDomain.TECTONIC_DETAIL_B.getSalt(), version);
        long sTubeA = SeedDerivation.derive(worldSeed, 1, NoiseDomain.CAVE.getSalt(), version);
        long sTubeB = SeedDerivation.derive(worldSeed, 1, NoiseDomain.CAVE.getSalt() ^ 0xDEADBEEF5EEDL, version);

        this.plainsNoise = new GeoNoise(sPlains);
        this.ridgeNoise = new GeoNoise(sRidge);
        this.lavaTubeNoiseA = new GeoNoise(sTubeA);
        this.lavaTubeNoiseB = new GeoNoise(sTubeB);
        this.volcanicField = new VolcanicCalderaField(worldSeed, version);
        this.warpField = new WarpField(worldSeed, this.config);
    }

    @Override
    public DimensionType getDimensionType() {
        return DimensionType.NETHER;
    }

    @Override
    public GeoConfig getConfig() {
        return config;
    }

    @Override
    public int getFluidLevel() {
        return NETHER_LAVA_LEVEL;
    }

    @Override
    public double evaluateSurface(double x, double z, GeoSample sample) {
        double plains = plainsNoise.sample2D(x * 0.003, z * 0.003) * 22.0;
        double rawRidge = ridgeNoise.sample2D(x * 0.008, z * 0.008);
        double ridge = Math.pow(Math.max(0.0, rawRidge), 1.6) * 45.0;
        double volcanic = volcanicField.evaluateVolcanicRelief(x, z);

        double surface = PLAINS_BASE + plains + ridge + volcanic;
        sample.finalSurface = surface;
        return surface;
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        if (worldY <= 3) {
            return 64.0f;
        }

        double surfaceH = evaluateSurface(worldX, worldZ, scratchpad.sample);
        double w = warpField.evaluateWarp(worldX, worldY, worldZ);

        double cVoid = 0.0;
        double overburden = surfaceH - worldY;
        
        if (overburden > 10.0 && worldY > NETHER_LAVA_LEVEL - 12) {
            double tA = lavaTubeNoiseA.sample2D(worldX * 0.02, (worldZ + worldY * 0.5) * 0.02);
            double tB = lavaTubeNoiseB.sample2D((worldX - worldY * 0.5) * 0.02, worldZ * 0.02);
            double conduit = (tA * tA) + (tB * tB);
            
            if (conduit < 0.07) {
                cVoid = (0.07 - conduit) / 0.07 * 28.0;
            }
        }

        double density = surfaceH - ((double) worldY + w) - cVoid;

        if (Double.isNaN(density) || Double.isInfinite(density)) {
            return (worldY < NETHER_LAVA_LEVEL) ? 1.0f : -1.0f;
        }

        return (float) density;
    }
}
