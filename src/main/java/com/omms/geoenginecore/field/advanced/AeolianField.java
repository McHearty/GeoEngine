package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class AeolianField {
    private final GeoNoise duneNoise;
    private final GeoNoise modulationNoise;
    private final double windCos;
    private final double windSin;
    private final double duneWavelength;
    private final double maxDuneHeight;

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

        double aridityFactor = Math.clamp((0.25 - humidity) / 0.25, 0.0, 1.0);
        return profile * maxDuneHeight * ((presence - 0.3) / 0.7) * aridityFactor;
    }
}
