package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Aeolian dune modifier (TECHSPEC §110-§112).
 *
 * <p>Dunes are anisotropic ridge functions oriented to a prevailing
 * wind vector: the along-wind coordinate forms the crest phase and the
 * cross-wind coordinate carries a lateral wiggle, so crests run
 * parallel to the wind with irregular offsets. A presence stream
 * restricts dunes to arid, gently sloping terrain (TECHSPEC §111).
 */
public final class AeolianField {
    /** Cross-wind crest wiggle stream. */
    private final GeoNoise duneNoise;
    /** Dune presence/field intensity stream. */
    private final GeoNoise modulationNoise;
    /** Prevailing wind direction cosine (35°). */
    private final double windCos;
    /** Prevailing wind direction sine (35°). */
    private final double windSin;
    /** Along-wind dune crest wavelength in blocks. */
    private final double duneWavelength;
    /** Peak dune height in blocks. */
    private final double maxDuneHeight;

    /**
     * Derives the two aeolian seed domains and resolves the prevailing
     * wind vector.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration
     */
    public AeolianField(long worldSeed, GeoConfig config) {
        long sDune = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.STRESS_X.getSalt() ^ 0xAE011A4L, config.generatorVersion());
        long sMod = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.STRESS_Z.getSalt() ^ 0xAE011A4L, config.generatorVersion());

        this.duneNoise = new GeoNoise(sDune);
        this.modulationNoise = new GeoNoise(sMod);

        double windAngle = Math.toRadians(35.0);
        this.windCos = Math.cos(windAngle);
        this.windSin = Math.sin(windAngle);

        this.duneWavelength = 48.0;
        this.maxDuneHeight = 18.0;
    }

    /**
     * Evaluates dune relief at one column (TECHSPEC §110-§111).
     *
     * <p>Active only in arid, warm, gently sloping terrain. The crest
     * profile is a smooth sawtooth (sine rising limb, cosine falling
     * limb) along the wind direction with a noisy cross-wind offset;
     * amplitude is scaled by field presence and aridity.
     *
     * @param x world-space X of the column
     * @param z world-space Z of the column
     * @param temperature normalized temperature at the column
     * @param humidity normalized humidity at the column
     * @param slope local gradient magnitude
     * @return dune relief in blocks, ≥ 0
     */
    public double evaluateDuneRelief(double x, double z, double temperature, double humidity, double slope) {
        if (humidity > 0.25 || temperature < 0.55 || slope > 0.25) {
            return 0.0;
        }

        double u = x * windCos + z * windSin;
        double v = -x * windSin + z * windCos;

        double presence = (modulationNoise.sample2D(x * 0.002, z * 0.002) + 1.0) * 0.5;
        if (presence < 0.3) {
            return 0.0;
        }

        double lateralWiggle = duneNoise.sample2D(v * 0.015, 0.0) * 12.0;
        double phase = (u + lateralWiggle) / duneWavelength;
        double cycle = phase - Math.floor(phase);

        double profile = (cycle < 0.75) 
            ? Math.sin((cycle / 0.75) * (Math.PI * 0.5))
            : Math.cos(((cycle - 0.75) / 0.25) * (Math.PI * 0.5));

        double aridityFactor = GeoMath.clamp((0.25 - humidity) / 0.25, 0.0, 1.0);
        return profile * maxDuneHeight * ((presence - 0.3) / 0.7) * aridityFactor;
    }
}
