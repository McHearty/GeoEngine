package com.omms.geoenginecore.material;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Dipped-strata lithology field (TECHSPEC §71-§74).
 *
 * <p>Strata are evaluated in a space that absorbs the dip vector
 * (15° in X, about 9° in Z), so horizontal coordinate noise produces
 * dipping layers without any per-voxel coordinate transform. Fault
 * offsets shift the strata by a noise-driven amount, intrusive
 * igneous bodies override the sequence, and a noisy deep-slate
 * transition separates the world floor. All inputs are pure fields.
 */
public final class LithologyField {
    /** Strata transition wiggle stream. */
    private final GeoNoise strataNoise;
    /** Igneous intrusion stream. */
    private final GeoNoise intrusionNoise;
    /** Fault offset stream. */
    private final GeoNoise faultNoise;
    /** Nominal Y of the deep-slate transition. */
    private final int deepslateTransitionY;

    /** Dip vector X component (15° ≈ 0.2588). */
    private static final double DIP_X = 0.2588;
    /** Dip vector Z component (≈ 9° ≈ 0.1500). */
    private static final double DIP_Z = 0.1500;

    /**
     * Derives the three independent lithology seed domains.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
    public LithologyField(long worldSeed, GeoConfig config) {
        long sStrata = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_BASE.getSalt() ^ 0x117401L, config.generatorVersion());
        long sIntrusion = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_DETAIL_A.getSalt() ^ 0x117402L, config.generatorVersion());
        long sFault = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.STRESS_X.getSalt() ^ 0x117403L, config.generatorVersion());

        this.strataNoise = new GeoNoise(sStrata);
        this.intrusionNoise = new GeoNoise(sIntrusion);
        this.faultNoise = new GeoNoise(sFault);
        this.deepslateTransitionY = 0;
    }

    /**
     * Resolves the rock family for one voxel (TECHSPEC §71-§74).
     *
     * <p>Priority: deep slate (world floor), intrusive granite or
     * diorite, then the dipped stratigraphic cycle bands (limestone,
     * tuff, andesite near the surface; a thin sandstone band at
     * depth). Everything else is standard stone.
     *
     * @param worldX world X of the voxel
     * @param worldY world Y of the voxel
     * @param worldZ world Z of the voxel
     * @param surfaceH final surface H_f of the column
     * @return rock family at the voxel
     */
    public RockFamily evaluateLithology(int worldX, int worldY, int worldZ, double surfaceH) {
        if (worldY < deepslateTransitionY - 4) {
            return RockFamily.DEEP_DEEPSLATE;
        }

        if (worldY < deepslateTransitionY + 6) {
            double transitionWiggle = strataNoise.sample2D(worldX * 0.015, worldZ * 0.015) * 4.0;
            if (worldY < deepslateTransitionY + transitionWiggle) {
                return RockFamily.DEEP_DEEPSLATE;
            }
        }

        double intrusionSample = intrusionNoise.sample2D(worldX * 0.012, (worldZ + worldY * 0.25) * 0.012);
        if (intrusionSample > 0.64) {
            return RockFamily.IGNEOUS_GRANITE;
        } else if (intrusionSample < -0.66) {
            return RockFamily.IGNEOUS_DIORITE;
        }

        double faultShift = faultNoise.sample2D(worldX * 0.005, worldZ * 0.005) * 14.0;
        double dippedElevation = worldY + (worldX * DIP_X) + (worldZ * DIP_Z) + faultShift;

        double layerCoord = dippedElevation * 0.08333;
        double cycle = layerCoord - Math.floor(layerCoord);

        double depth = surfaceH - worldY;

        if (depth < 64.0) {
            if (cycle < 0.22) {
                return RockFamily.SEDIMENTARY_LIMESTONE;
            } else if (cycle > 0.40 && cycle < 0.58) {
                return RockFamily.METAMORPHIC_TUFF;
            } else if (cycle > 0.78) {
                return RockFamily.IGNEOUS_ANDESITE;
            }
        }

        if (depth > 20.0 && depth < 120.0 && cycle > 0.25 && cycle < 0.35) {
            return RockFamily.SEDIMENTARY_SANDSTONE;
        }

        return RockFamily.STANDARD_STONE;
    }
}
