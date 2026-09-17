package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic tectonic elevation field.
 *
 * <p>The field combines a low-frequency continental component with two independent detail fields.
 * Positive values from the detail fields contribute uplift, while negative values from the first
 * detail field contribute a separate basin term. The detail contributions are shaped by the
 * configured uplift exponent.
 */
public final class TectonicField {
    /** Low-frequency noise source controlling the broad continental component. */
    private final GeoNoise baseNoise;

    /** First detail noise source used for uplift and basin formation. */
    private final GeoNoise detailNoiseA;

    /** Second detail noise source used for uplift. */
    private final GeoNoise detailNoiseB;

    /** Sampling frequencies for the low-frequency and two detail components. */
    private final double fLow, fA, fB;

    /** Amplitudes applied to the low-frequency and two detail components. */
    private final double aLow, aA, aB;

    /** Exponent controlling the response of detail magnitudes to their noise values. */
    private final double upliftExponent;

    /**
     * Creates a tectonic field using independently derived noise domains for each component.
     *
     * @param worldSeed world seed used for deterministic field generation
     * @param config terrain-generation configuration
     */
    public TectonicField(long worldSeed, GeoConfig config) {
        long sBase = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.TECTONIC_BASE.getSalt(),
            config.generatorVersion());

        long sA = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.TECTONIC_DETAIL_A.getSalt(),
            config.generatorVersion());

        long sB = SeedDerivation.derive(
            worldSeed,
            config.dimensionId(),
            NoiseDomain.TECTONIC_DETAIL_B.getSalt(),
            config.generatorVersion());

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

    /**
     * Evaluates the tectonic elevation field at a horizontal world-space position.
     *
     * <p>The low-frequency noise contributes directly to the continental component. Positive
     * portions of both detail fields are transformed into uplift contributions, while the negative
     * portion of the first detail field is transformed into a basin contribution and subtracted
     * from the result.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @return tectonic elevation contribution
     */
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
