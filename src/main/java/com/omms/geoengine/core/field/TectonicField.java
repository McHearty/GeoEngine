package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class TectonicField {
    private final GeoNoise baseNoise;
    private final GeoNoise detailNoiseA;
    private final GeoNoise detailNoiseB;

    private final double fLow, fA, fB;
    private final double aLow, aA, aB;
    private final double upliftExponent;

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
    }

    public double evaluate(double x, double z) {
        double nLow = baseNoise.sample2D(x * fLow, z * fLow);
        double nA = detailNoiseA.sample2D(x * fA, z * fA);
        double nB = detailNoiseB.sample2D(x * fB, z * fB);

        double continental = nLow * aLow;
        double uA = Math.max(0.0, nA);
        double uB = Math.max(0.0, nB);

        double upliftA = aA * Math.pow(uA, upliftExponent);
        double upliftB = aB * Math.pow(uB, upliftExponent);

        double dA = Math.max(0.0, -nA);
        double basinA = (aA * 0.4) * Math.pow(dA, upliftExponent);

        return continental + upliftA + upliftB - basinA;
    }
}
