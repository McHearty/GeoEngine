package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Macro-tectonic relief field T, the crustal base of the pipeline
 * (TECHSPEC §13-§15).
 *
 * <p>Combines three frequency bands — very-low continental structure,
 * a primary mountain belt, and a secondary ridge band — each in its
 * own seed domain (TECHSPEC §9). Uplift operates only on the
 * nonnegative part of the detail bands, split between a linear base
 * and a power-law tail with the configured exponent, so relief
 * concentrates toward massif centers (TECHSPEC §14). A separate
 * depression energy term carves ocean basins and tectonic lows.
 */
public final class TectonicField {
    /** Continental base band noise stream. */
    private final GeoNoise baseNoise;
    /** Primary mountain-belt noise stream. */
    private final GeoNoise detailNoiseA;
    /** Secondary ridge noise stream. */
    private final GeoNoise detailNoiseB;

    /** Inverse wavelengths of the three tectonic bands. */
    private final double fLow, fA, fB;
    /** Band amplitudes in blocks. */
    private final double aLow, aA, aB;
    /** Uplift remap exponent p ≥ 1 (TECHSPEC §14). */
    private final double upliftExponent;
    /** Continental baseline reference: the dimension's sea level. */
    private final double seaLevel;

    /**
     * Derives the three independent seed domains and captures the
     * band parameters.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration supplying frequencies and amplitudes
     */
    public TectonicField(long worldSeed, GeoConfig config) {
        long sBase = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_BASE.getSalt(), config.generatorVersion());
        long sA = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_DETAIL_A.getSalt(), config.generatorVersion());
        long sB = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.TECTONIC_DETAIL_B.getSalt(), config.generatorVersion());

        this.baseNoise = new GeoNoise(sBase);
        this.detailNoiseA = new GeoNoise(sA);
        this.detailNoiseB = new GeoNoise(sB);

        this.fLow = config.tectonicFreqLow();
        this.fA = config.tectonicFreqA();
        this.fB = config.tectonicFreqB();

        this.aLow = config.tectonicAmpLow();
        this.aA = config.tectonicAmpA();
        this.aB = config.tectonicAmpB();

        this.upliftExponent = config.upliftExponent();
        this.seaLevel = config.seaLevel();
    }

    /**
     * Evaluates the bichromatic tectonic field T_b at (x, z)
     * (TECHSPEC §15).
     *
     * <p>Continental baseline = sea level + base band. Uplift is
     * applied to the nonnegative detail noise with a mixed
     * linear/power curve, and a separate depression term opens ocean
     * basins and trenches. All noise streams are 45-scaled gradient
     * noise, so their dynamic range exceeds the unit interval and the
     * configured amplitudes are calibrated against that range.
     *
     * @param x world-space X
     * @param z world-space Z
     * @return tectonic relief elevation
     */
    public double evaluate(double x, double z) {
        double nLow = baseNoise.sample2D(x * fLow, z * fLow);
        double nA = detailNoiseA.sample2D(x * fA, z * fA);
        double nB = detailNoiseB.sample2D(x * fB, z * fB);

        // Continental baseline: oceans Y=15..55, plains Y=68..95
        double continental = seaLevel + (nLow * aLow);

        double uA = Math.max(0.0, nA);
        double uB = Math.max(0.0, nB);

        // Composite uplift curve: 25% linear base for foothills, 75% power for soaring peaks
        double curveA = (0.25 * uA) + (0.75 * Math.pow(uA, upliftExponent));
        double curveB = (0.35 * uB) + (0.65 * Math.pow(uB, upliftExponent));

        double upliftA = aA * curveA;
        double upliftB = aB * curveB;

        // Ocean basins and trenches
        double dA = Math.max(0.0, -nA);
        double basinA = (aA * 0.15) * Math.pow(dA, 1.5);

        return continental + upliftA + upliftB - basinA;
    }
}
