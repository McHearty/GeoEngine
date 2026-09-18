package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class GlacialField {
    private final GeoNoise troughNoise;
    private final GeoNoise cirqueNoise;
    private final double seaLevel;

    public GlacialField(long worldSeed, GeoConfig config) {
        long sTrough = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt() ^ 0x61AC1A1L, config.generatorVersion());
        long sCirque = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_DETAIL_A.getSalt() ^ 0xC180E1L, config.generatorVersion());
        this.troughNoise = new GeoNoise(sTrough);
        this.cirqueNoise = new GeoNoise(sCirque);
        this.seaLevel = config.seaLevel();
    }

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
