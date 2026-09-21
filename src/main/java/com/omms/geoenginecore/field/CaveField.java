package com.omms.geoenginecore.field;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Volumetric cave void field C (TECHSPEC §45-§48).
 *
 * <p>Cave architecture combines two tunnel noise streams (a squared
 * sum isolates the tunnel cores), a high-threshold chamber stream,
 * and a coarse occupancy stream, all in independent seed domains. The
 * result satisfies C ≥ 0 and is masked by an overburden smoothstep so
 * caves never break the surface unless explicitly intended
 * (TECHSPEC §47).
 */
public final class CaveField {
    /** First tunnel core noise stream. */
    private final GeoNoise tunnelNoiseA;
    /** Second tunnel core noise stream, decorrelated by a salted seed. */
    private final GeoNoise tunnelNoiseB;
    /** Cavern/chamber noise stream. */
    private final GeoNoise chamberNoise;
    /** Coarse cave occupancy mask used by section classification (TECHSPEC §54). */
    private final GeoNoise occupancyNoise;

    /** Inclusive lower Y of the cave placement envelope. */
    private final int caveMinY;
    /** Upper Y of the cave placement envelope. */
    private final int caveMaxY;

    /** Overburden depth below which no cave void is allowed. */
    private static final double COVER_MIN = 8.0;
    /** Overburden depth at which the cave void is fully active. */
    private static final double COVER_MAX = 20.0;

    /**
     * Derives the four independent cave seed domains and captures the
     * vertical envelope.
     *
     * @param worldSeed world seed that roots every seed domain
     * @param config validated configuration supplying the cave envelope
     */
    public CaveField(long worldSeed, GeoConfig config) {
        long sA = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt(), config.generatorVersion());
        long sB = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt() ^ 0xCAFE01L, config.generatorVersion());
        long sChamber = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt() ^ 0xCAFE02L, config.generatorVersion());
        long sOcc = SeedDerivation.derive(worldSeed, config.dimensionId(), 
            NoiseDomain.CAVE.getSalt() ^ 0xCAFE03L, config.generatorVersion());

        this.tunnelNoiseA = new GeoNoise(sA);
        this.tunnelNoiseB = new GeoNoise(sB);
        this.chamberNoise = new GeoNoise(sChamber);
        this.occupancyNoise = new GeoNoise(sOcc);

        this.caveMinY = config.caveMinY();
        this.caveMaxY = config.caveMaxY();
    }

    /**
     * Coarse per-section cave occupancy test for the section
     * classifier (TECHSPEC §54).
     *
     * <p>A section that is entirely outside the cave envelope is
     * cave-free. Otherwise the occupancy field is sampled once at the
     * section center; a positive sample marks the section as
     * potentially cave-bearing, which forces BAND classification
     * instead of a false SOLID verdict. The threshold is tuned so
     * roughly 45% of subterranean sections contain caves while the
     * rest stay bulk SOLID.
     *
     * @param chunkWorldX world-coordinate X of the chunk
     * @param sectionMinY minimum Y of the 16-block section
     * @param chunkWorldZ world-coordinate Z of the chunk
     * @return true when the section may contain cave voids
     */
    public boolean hasCavePotential(int chunkWorldX, int sectionMinY, int chunkWorldZ) {
        int sectionMaxY = sectionMinY + 16;
        if (sectionMaxY <= caveMinY || sectionMinY >= caveMaxY) {
            return false;
        }

        double cx = (chunkWorldX + 8) * 0.0078125;
        double cy = (sectionMinY + 8) * 0.0078125;
        double cz = (chunkWorldZ + 8) * 0.0078125;

        double occSample = occupancyNoise.sample2D(cx, cz + cy * 0.5);
        return occSample > 0.05; // ~45% of subterranean sections contain caves; 55% are bulk SOLID
    }

    /**
     * Evaluates the cave void C at one voxel (TECHSPEC §45-§47).
     *
     * <p>Zero outside the vertical envelope and under the
     * overburden smoothstep mask. Tunnel voids appear where the
     * squared tunnel metric drops below its threshold; chamber voids
     * appear where the chamber stream exceeds its high threshold, and
     * the larger of the two wins.
     *
     * @param x world X of the voxel
     * @param y world Y of the voxel
     * @param z world Z of the voxel
     * @param surfaceHeight final surface H_f above the voxel
     * @return void contribution C ≥ 0
     */
    public double evaluateCave(double x, double y, double z, double surfaceHeight) {
        if (y < caveMinY || y > caveMaxY) {
            return 0.0;
        }

        double overburden = surfaceHeight - y;
        if (overburden <= COVER_MIN) {
            return 0.0;
        }

        double coverMask = smoothStep(COVER_MIN, COVER_MAX, overburden);

        double tA = tunnelNoiseA.sample2D(x * 0.016, (z + y * 0.45) * 0.016);
        double tB = tunnelNoiseB.sample2D((x - y * 0.45) * 0.016, z * 0.016);
        double tunnelMetric = (tA * tA) + (tB * tB);

        double voidIntensity = 0.0;
        if (tunnelMetric < 0.065) {
            voidIntensity = (0.065 - tunnelMetric) / 0.065 * 24.0;
        }

        double chamberSample = chamberNoise.sample2D(x * 0.008, (z - y * 0.3) * 0.008);
        if (chamberSample > 0.65) {
            double chamberVoid = (chamberSample - 0.65) / 0.35 * 38.0;
            voidIntensity = Math.max(voidIntensity, chamberVoid);
        }

        return voidIntensity * coverMask;
    }

    /**
     * Hermite smoothstep between two edges.
     *
     * @param edge0 lower edge where the fade-in starts
     * @param edge1 upper edge where the fade-in completes
     * @param x value to remap
     * @return 0 below edge0, 1 above edge1, smooth in between
     */
    private static double smoothStep(double edge0, double edge1, double x) {
        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - (2.0 * t));
    }
}
