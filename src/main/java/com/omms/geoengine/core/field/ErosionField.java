package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

public final class ErosionField {
    private final GeoNoise noise;
    private final double baseRate;

    public ErosionField(long worldSeed, GeoConfig config) {
        long seed = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.EROSION.getSalt(), config.generatorVersion());
        this.noise = new GeoNoise(seed);
        this.baseRate = config.baseErosionRate();
    }

    public double evaluateErosion(double x, double z, double age, double climateMult, double tectonicHeight) {
        double raw = (noise.sample2D(x * 0.002, z * 0.002) + 1.0) * 0.5;
        double elevationFactor = Math.max(0.0, tectonicHeight / 300.0);
        double lowering = baseRate * raw * (0.3 + 0.7 * age) * climateMult * (1.0 + 0.5 * elevationFactor);
        return Math.max(0.0, lowering);
    }
}
