package com.omms.geoenginecore.field.advanced;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

public final class AlluvialDeltaField {
    private final GeoNoise lobeNoise;
    private final double seaLevel;

    public AlluvialDeltaField(long worldSeed, GeoConfig config) {
        long sLobe = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.DEPOSITION.getSalt() ^ 0xDE17A5L, config.generatorVersion());
        this.lobeNoise = new GeoNoise(sLobe);
        this.seaLevel = config.seaLevel();
    }

    public double evaluateAlluvialFan(double flowAcc, double slope, double laplacian, double sedimentBudget) {
        if (flowAcc < 1.2 || slope > 0.12 || laplacian <= 0.0) {
            return 0.0;
        }
        double fanStrength = Math.clamp(laplacian * 4.0, 0.0, 1.0);
        double availableSediment = Math.min(18.0, sedimentBudget * 0.45);
        return availableSediment * fanStrength;
    }

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
