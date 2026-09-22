package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Fluvial deposition modifier: alluvial fans and delta lobes
 * (TECHSPEC §116-§117).
 *
 * <p>Fans form where a confined channel exits onto low ground with
 * high concavity and sediment supply; delta lobes form where a
 * strong river meets standing water and prograde sediment shallows
 * the seafloor.
 */
public final class AlluvialDeltaField {
    /** Delta lobe distribution stream. */
    private final GeoNoise lobeNoise;
    /** Standing-water reference for delta lobes. */
    private final double seaLevel;

    /**
     * Derives the deposition seed domain and captures the sea level.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
    public AlluvialDeltaField(long worldSeed, GeoConfig config) {
        long sLobe = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.DEPOSITION.getSalt() ^ 0xDE17A5L, config.generatorVersion());
        this.lobeNoise = new GeoNoise(sLobe);
        this.seaLevel = config.seaLevel();
    }

    /**
     * Deposits an alluvial fan at a confined-channel outlet
     * (TECHSPEC §117).
     *
     * @param flowAcc flow accumulation proxy A_f
     * @param slope |∇H|; fans need low gradient
     * @param laplacian ∇²H; fans need concave (positive) curvature
     * @param sedimentBudget available sediment from the erosion/incision budget (TECHSPEC §33)
     * @return deposited thickness in blocks, ≥ 0
     */
    public double evaluateAlluvialFan(double flowAcc, double slope, double laplacian, double sedimentBudget) {
        if (flowAcc < 1.2 || slope > 0.12 || laplacian <= 0.0) {
            return 0.0;
        }
        double fanStrength = GeoMath.clamp(laplacian * 4.0, 0.0, 1.0);
        double availableSediment = Math.min(18.0, sedimentBudget * 0.45);
        return availableSediment * fanStrength;
    }

    /**
     * Deposits a prograde delta lobe in standing water
     * (TECHSPEC §116).
     *
     * @param x world-space X of the column
     * @param z world-space Z of the column
     * @param currentSurface pre-fluvial surface elevation
     * @param flowAcc flow accumulation proxy A_f
     * @param riverIncision channel incision depth
     * @return deposited lobe thickness in blocks, ≥ 0
     */
    public double evaluateDeltaLobe(double x, double z, double currentSurface, double flowAcc, double riverIncision) {
        if (flowAcc < 2.0 || riverIncision < 2.0) {
            return 0.0;
        }

        double depthBelowSea = seaLevel - currentSurface;
        if (depthBelowSea <= 0.0 || depthBelowSea > 12.0) {
            return 0.0;
        }

        double lobePattern = (lobeNoise.sample2D(x * 0.018, z * 0.018) + 1.0) * 0.5;
        double shallowing = 1.0 - (depthBelowSea / 12.0);
        return shallowing * lobePattern * 6.0;
    }
}
