package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic terrain erosion-lowering field.
 *
 * <p>Erosion combines spatially varying noise with terrain age, climate, and tectonic elevation.
 * Older terrain, stronger climate multipliers, and greater positive tectonic elevation increase
 * the computed lowering, while the final result is constrained to be non-negative.
 */
public final class ErosionField {
    /** Deterministic noise source used to provide spatial variation in erosion rate. */
    private final GeoNoise noise;

    /** Base erosion rate supplied by the terrain configuration. */
    private final double baseRate;

    /**
     * Creates an erosion field using a deterministic noise seed derived from the world configuration.
     *
     * @param worldSeed world seed used for deterministic field generation
     * @param config terrain-generation configuration
     */
    public ErosionField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.EROSION.getSalt(),
            config.generatorVersion());

        this.noise = new GeoNoise(seed);
        this.baseRate = config.baseErosionRate();
    }

    /**
     * Evaluates erosion lowering at a horizontal world-space position.
     *
     * <p>The erosion rate is spatially varied by a fixed-frequency noise field. Age scales the
     * result from {@code 0.3} to {@code 1.0} over its intended normalized range, climate scales
     * the result directly, and positive tectonic elevation contributes an additional elevation
     * factor. The returned lowering is clamped to a minimum of zero.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @param age normalized terrain-age value
     * @param climateMult climate multiplier applied to the erosion rate
     * @param tectonicHeight tectonic elevation used to derive the elevation factor
     * @return non-negative erosion lowering
     */
    public double evaluateErosion(
        double x,
        double z,
        double age,
        double climateMult,
        double tectonicHeight) {

        double raw = (noise.sample2D(x * 0.002, z * 0.002) + 1.0) * 0.5;
        double elevationFactor = Math.max(0.0, tectonicHeight / 300.0);
        double lowering =
            baseRate
                * raw
                * (0.3 + 0.7 * age)
                * climateMult
                * (1.0 + 0.5 * elevationFactor);

        return Math.max(0.0, lowering);
    }
}
