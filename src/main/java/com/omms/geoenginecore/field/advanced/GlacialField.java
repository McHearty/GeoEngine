package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Cryogenic (glacial) surface modifier (TECHSPEC §118-§119).
 *
 * <p>U-shaped valley floors are carved along a deterministic
 * trough-stream whose centerline is the zero set of a low-frequency
 * noise; cirque bowls are excavated where a separate noise stream
 * exceeds its high threshold. Both are gated by temperature and a
 * glacial intensity mask, and both subtract from the pre-fluvial
 * surface.
 */
public final class GlacialField {
    /** Valley axis stream: its zero set is the U-valley centerline. */
    private final GeoNoise troughNoise;
    /** Cirque placement stream. */
    private final GeoNoise cirqueNoise;
    /** Sea level used by the cirque elevation gate. */
    private final double seaLevel;

    /**
     * Derives the two glacial seed domains and captures the sea level.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
    public GlacialField(long worldSeed, GeoConfig config) {
        long sTrough = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt() ^ 0x61AC1A1L, config.generatorVersion());
        long sCirque = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_DETAIL_A.getSalt() ^ 0xC180E1L, config.generatorVersion());
        this.troughNoise = new GeoNoise(sTrough);
        this.cirqueNoise = new GeoNoise(sCirque);
        this.seaLevel = config.seaLevel();
    }

    /**
     * Carves the U-shaped valley floor beneath a glacier path
     * (TECHSPEC §119).
     *
     * <p>Active only above a glacial intensity of 0.05 and below a
     * temperature of 0.35. The path is the zero set of the trough
     * stream; the floor deepening is parabolic across the trough and
     * scales with glacial intensity (up to 36 blocks).
     *
     * @param x world-space X of the column
     * @param z world-space Z of the column
     * @param currentSurface pre-fluvial surface elevation
     * @param slope local gradient magnitude
     * @param temperature normalized temperature at the column
     * @param glacialIntensity cryogenic process mask in [0, 1]
     * @return non-positive deepening in blocks
     */
    public double evaluateUValleyModification(double x, double z, double currentSurface, 
                                             double slope, double temperature, double glacialIntensity) {
        if (glacialIntensity <= 0.05 || temperature > 0.35) {
            return 0.0;
        }

        double pathSample = troughNoise.sample2D(x * 0.003, z * 0.003);
        double axisDist = Math.abs(pathSample);

        double troughWidth = 0.18;
        if (axisDist >= troughWidth) {
            return 0.0;
        }

        double normDist = axisDist / troughWidth;
        double parabolicProfile = 1.0 - (normDist * normDist);
        double maxDeepening = 36.0 * glacialIntensity;

        return -(maxDeepening * parabolicProfile);
    }

    /**
     * Excavates cirque bowls above 220 blocks above sea level in cold
     * terrain (TECHSPEC §118).
     *
     * @param x world-space X of the column
     * @param z world-space Z of the column
     * @param currentSurface pre-fluvial surface elevation
     * @param temperature normalized temperature at the column
     * @return non-positive bowl depth in blocks
     */
    public double evaluateCirqueBowl(double x, double z, double currentSurface, double temperature) {
        if (currentSurface < seaLevel + 220.0 || temperature > 0.20) {
            return 0.0;
        }

        double n = cirqueNoise.sample2D(x * 0.006, z * 0.006);
        if (n > 0.65) {
            double hollow = (n - 0.65) / 0.35;
            return -28.0 * hollow * hollow;
        }
        return 0.0;
    }
}
