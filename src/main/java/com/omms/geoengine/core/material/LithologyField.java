package com.geoengine.core.material;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates the rock family assigned to a terrain position.
 *
 * <p>Lithology is determined from world depth, a warped stratification field, and a separate
 * intrusion field. Deep positions transition to deepslate, selected regions receive igneous
 * intrusion materials, and shallow stratified regions receive additional rock families.
 */
public final class LithologyField {

    /** Noise field used for the stratification pattern and deepslate transition variation. */
    private final GeoNoise strataNoise;

    /** Noise field used to identify igneous intrusion regions. */
    private final GeoNoise intrusionNoise;

    /** World Y coordinate around which the deepslate transition is evaluated. */
    private final int deepslateTransitionY;

    /**
     * Creates a lithology evaluator with deterministic noise derived from the world configuration.
     *
     * @param worldSeed world seed used to derive the lithology noise streams
     * @param config terrain configuration providing the dimension and generator version
     */
    public LithologyField(long worldSeed, GeoConfig config) {
        long sStrata =
            SeedDerivation.derive(
                worldSeed,
                config.dimensionId(),
                NoiseDomain.TECTONIC_BASE.getSalt() ^ 0x117401L,
                config.generatorVersion());

        long sIntrusion =
            SeedDerivation.derive(
                worldSeed,
                config.dimensionId(),
                NoiseDomain.TECTONIC_DETAIL_A.getSalt() ^ 0x117402L,
                config.generatorVersion());

        this.strataNoise = new GeoNoise(sStrata);
        this.intrusionNoise = new GeoNoise(sIntrusion);
        this.deepslateTransitionY = 0;
    }

    /**
     * Evaluates the rock family at a world position.
     *
     * <p>Positions below the deepslate transition are assigned deepslate, with a noise-modulated
     * transition band. Remaining positions are tested for igneous intrusions before shallow
     * stratification rules are evaluated.
     *
     * @param worldX world-space X coordinate
     * @param worldY world-space Y coordinate
     * @param worldZ world-space Z coordinate
     * @param surfaceH terrain surface height used to determine the position's depth below the
     *     surface
     * @return the rock family selected for the position
     */
    public RockFamily evaluateLithology(
        int worldX,
        int worldY,
        int worldZ,
        double surfaceH
    ) {
        if (worldY < deepslateTransitionY - 4) {
            return RockFamily.DEEP_DEEPSLATE;
        }

        if (worldY < deepslateTransitionY + 4) {
            double transitionWiggle =
                strataNoise.sample2D(worldX * 0.02, worldZ * 0.02) * 3.0;
            if (worldY < deepslateTransitionY + transitionWiggle) {
                return RockFamily.DEEP_DEEPSLATE;
            }
        }

        double depth = surfaceH - worldY;

        double intrusionSample =
            intrusionNoise.sample2D(
                worldX * 0.015,
                (worldZ + worldY * 0.3) * 0.015);

        if (intrusionSample > 0.62) {
            return RockFamily.IGNEOUS_GRANITE;
        } else if (intrusionSample < -0.65) {
            return RockFamily.IGNEOUS_DIORITE;
        }

        double strataWarp =
            strataNoise.sample2D(worldX * 0.01, worldZ * 0.01) * 8.0;
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
