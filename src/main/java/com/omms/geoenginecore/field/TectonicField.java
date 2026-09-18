package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class TectonicField {
    private final GeoNoise baseNoise;
    private final GeoNoise detailNoiseA;
    private final GeoNoise detailNoiseB;

    private final double fLow, fA, fB;
    private final double aLow, aA, aB;
    private final double upliftExponent;
    private final double seaLevel;

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
