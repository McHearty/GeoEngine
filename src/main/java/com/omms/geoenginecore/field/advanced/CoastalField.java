package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Coastal surface modifier (TECHSPEC §122-§124).
 *
 * <p>Wave-cut platforms flatten the landward few blocks of the
 * shoreline toward a terrace 1 block below sea level. Sea arches
 * open in steep coastal cliffs in a short band above sea level where
 * a noise stream exceeds its threshold; stacks emerge as the
 * connecting material is removed.
 */
public final class CoastalField {
    /** Wave exposure / arch placement stream. */
    private final GeoNoise waveNoise;
    /** Sea stack placement stream. */
    private final GeoNoise stackNoise;
    /** Coastline reference: the dimension's sea level (TECHSPEC §123). */
    private final double seaLevel;

    /**
     * Derives the two coastal seed domains and captures the sea level.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
    public CoastalField(long worldSeed, GeoConfig config) {
        long sWave = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt() ^ 0xC0A57A1L, config.generatorVersion());
        long sStack = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.WARP.getSalt() ^ 0xC0A57A2L, config.generatorVersion());
        this.waveNoise = new GeoNoise(sWave);
        this.stackNoise = new GeoNoise(sStack);
        this.seaLevel = config.seaLevel();
    }

    /**
     * Flattens the landward 4-block band of the shoreline toward a
     * wave-cut terrace (TECHSPEC §122).
     *
     * @param currentSurface pre-fluvial surface elevation
     * @return platform adjustment in blocks (negative on land)
     */
    public double evaluateWaveCutPlatform(double currentSurface) {
        double delta = currentSurface - seaLevel;
        if (delta > -4.0 && delta < 2.0) {
            double flatteningFactor = 1.0 - (Math.abs(delta) / 4.0);
            return (seaLevel - 1.0 - currentSurface) * (0.65 * flatteningFactor);
        }
        return 0.0;
    }

    /**
     * Opens a sea arch void in a steep coastal cliff (TECHSPEC §124).
     *
     * <p>Active only on steep slopes (≥ 0.5) between 6 and 35 blocks
     * above sea level, and within a 5-block vertical notch centered 2
     * blocks above sea level, where the arch stream exceeds its
     * threshold.
     *
     * @param x world X of the voxel
     * @param y world Y of the voxel
     * @param z world Z of the voxel
     * @param surfaceH final surface H_f of the column
     * @param slope local surface gradient magnitude
     * @return void contribution, ≥ 0
     */
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
