package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Volumetric 3-D rock warp W(x, y, z) (TECHSPEC §41-§44).
 *
 * <p>W introduces micro-overhangs, irregular cliff faces, rock grain,
 * and non-planar surfaces — controlled natural roughness, not the
 * primary mechanism for arches, caves, tunnels, or major cliffs. Two
 * decorrelated noise streams are combined and the result is clamped
 * to a bound that tightens with altitude (1 − aᵖ damping, no
 * division by y) and with local slope.
 */
public final class WarpField {
    /** Primary warp noise stream. */
    private final GeoNoise noiseA;
    /** Secondary warp noise stream, decorrelated by a salted seed. */
    private final GeoNoise noiseB;
    /** Configured global warp amplitude bound W_max (TECHSPEC §44). */
    private final double maxWarp;
    /** World floor used by the altitude normalization. */
    private final double minY;
    /** World ceiling used by the altitude normalization. */
    private final double maxY;
    /** Altitude damping exponent p in D_W(a) = 1 − aᵖ (TECHSPEC §43). */
    private final double altitudeExponent;

    /**
     * Derives the two independent warp seed domains and captures the
     * amplitude bound and vertical range.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration supplying the bound and bounds of the vertical range
     */
    public WarpField(long worldSeed, GeoConfig config) {
        long s1 = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.WARP.getSalt(), config.generatorVersion());
        long s2 = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.WARP.getSalt() ^ 0x510BE71L, config.generatorVersion());

        this.noiseA = new GeoNoise(s1);
        this.noiseB = new GeoNoise(s2);
        this.maxWarp = config.maxWarpAmplitude();
        this.minY = config.worldMinY();
        this.maxY = config.worldMaxY();
        this.altitudeExponent = 2.0;
    }

    /**
     * Evaluates the bounded warp at one voxel (TECHSPEC §41-§44).
     *
     * <p>The effective bound is W_max scaled by a slope factor and the
     * altitude damping 1 − aᵖ, so the warp fades toward the top of the
     * world and never produces unsupported floating geometry.
     *
     * @param x world X of the voxel
     * @param y world Y of the voxel
     * @param z world Z of the voxel
     * @param slope local surface gradient magnitude
     * @return warp displacement, bounded by the effective W_max
     */
    public double evaluateWarp(double x, double y, double z, double slope) {
        double slopeFactor = GeoMath.clamp(slope * 1.8, 0.05, 1.0);
        double normY = GeoMath.clamp((y - minY) / (maxY - minY), 0.0, 1.0);
        double altitudeDamping = 1.0 - Math.pow(normY, altitudeExponent);

        double n1 = noiseA.sample2D(x * 0.035, (z + y * 0.25) * 0.035);
        double n2 = noiseB.sample2D((x - y * 0.25) * 0.07, z * 0.07) * 0.5;

        double combinedNoise = (n1 + n2) / 1.5;
        double effectiveBound = maxWarp * slopeFactor * altitudeDamping;

        return GeoMath.clamp(combinedNoise * effectiveBound, -effectiveBound, effectiveBound);
    }

    /**
     * @return the configured global warp amplitude bound W_max
     */
    public double getMaxWarp() {
        return maxWarp;
    }
}
