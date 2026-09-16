package com.geoengine.core.material;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class LithologyField {
    private final GeoNoise strataNoise;
    private final GeoNoise intrusionNoise;
    private final int deepslateTransitionY;

    public LithologyField(long worldSeed, GeoConfig config) {
        long sStrata = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_BASE.getSalt() ^ 0x117401L, config.generatorVersion());
        long sIntrusion = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_DETAIL_A.getSalt() ^ 0x117402L, config.generatorVersion());
        this.strataNoise = new GeoNoise(sStrata);
        this.intrusionNoise = new GeoNoise(sIntrusion);
        this.deepslateTransitionY = 0;
    }

    public RockFamily evaluateLithology(int worldX, int worldY, int worldZ, double surfaceH) {
        if (worldY < deepslateTransitionY - 4) {
            return RockFamily.DEEP_DEEPSLATE;
        }

        if (worldY < deepslateTransitionY + 4) {
            double transitionWiggle = strataNoise.sample2D(worldX * 0.02, worldZ * 0.02) * 3.0;
            if (worldY < deepslateTransitionY + transitionWiggle) {
                return RockFamily.DEEP_DEEPSLATE;
            }
        }

        double depth = surfaceH - worldY;

        double intrusionSample = intrusionNoise.sample2D(worldX * 0.015, (worldZ + worldY * 0.3) * 0.015);
        if (intrusionSample > 0.62) {
            return RockFamily.IGNEOUS_GRANITE;
        } else if (intrusionSample < -0.65) {
            return RockFamily.IGNEOUS_DIORITE;
        }

        double strataWarp = strataNoise.sample2D(worldX * 0.01, worldZ * 0.01) * 8.0;
        double layeredCoord = (worldY + strataWarp) * 0.08;
        double bandCycle = layeredCoord - Math.floor(layeredCoord);

        if (depth < 42.0) {
            if (bandCycle < 0.18) {
                return RockFamily.SEDIMENTARY_LIMESTONE;
            } else if (bandCycle > 0.72) {
                return RockFamily.IGNEOUS_ANDESITE;
            }
        }

        if (depth < 24.0 && bandCycle > 0.35 && bandCycle < 0.55) {
            return RockFamily.METAMORPHIC_TUFF;
        }

        return RockFamily.STANDARD_STONE;
    }
}
