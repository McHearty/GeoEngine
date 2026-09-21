package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Long-term surface lowering E (TECHSPEC §22).
 *
 * <p>E = E₀ · F_weathering · F_age · K_climate · F_relief, where the
 * weathering texture is a 0.5-scale noise stream sampled at a fixed
 * 0.002 frequency (stable micro-pattern across configurations), the
 * age factor grows from 0.3 (young) to 1.0 (old), and the relief
 * factor adds up to 50% extra lowering for elevated terrain. The
 * result is clamped to E ≥ 0.
 */
public final class ErosionField {
    /** Weathering texture noise stream. */
    private final GeoNoise noise;
    /** Base lowering budget E₀ in blocks (TECHSPEC §22). */
    private final double baseRate;

    /**
     * Derives the EROSION seed domain and captures the base rate.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration supplying the base erosion rate
     */
    public ErosionField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.baseRate = config.baseErosionRate();
    }

    /**
     * Evaluates the surface lowering for one column.
     *
     * @param x world-space X
     * @param z world-space Z
     * @param age geological age factor in [0, 1]
     * @param climateMult bounded climate multiplier K
     * @param tectonicHeight tectonic relief driving the exposure factor
     * @return surface lowering in blocks, ≥ 0
     */
    public double evaluateErosion(double x, double z, double age, double climateMult, double tectonicHeight) {
        double raw = (noise.sample2D(x * 0.002, z * 0.002) + 1.0) * 0.5;
        double elevationFactor = Math.max(0.0, tectonicHeight / 300.0);
        double lowering = baseRate * raw * (0.3 + 0.7 * age) * climateMult * (1.0 + 0.5 * elevationFactor);
        return Math.max(0.0, lowering);
    }
}
