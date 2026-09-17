package com.geoengine.core.field;

import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic scalar field representing underground cave void intensity.
 *
 * <p>Cave intensity is generated from two independently seeded noise fields and modulated by the
 * amount of terrain covering the evaluated position. Positions with insufficient overburden are
 * assigned zero cave intensity.
 */
public final class CaveField {

    /** Primary noise field used to construct the cave tunnel metric. */
    private final GeoNoise primaryNoise;

    /** Secondary noise field used to construct the cave tunnel metric. */
    private final GeoNoise secondaryNoise;

    /** Minimum overburden required before cave intensity can be generated. */
    private static final double COVER_MIN = 8.0;

    /** Overburden at which the cover mask reaches its maximum value. */
    private static final double COVER_MAX = 24.0;

    /**
     * Creates a cave field with deterministic noise streams derived from the cave noise domain.
     *
     * @param worldSeed world seed used to derive the cave noise streams
     * @param config terrain configuration providing the dimension and generator version
     */
    public CaveField(long worldSeed, GeoConfig config) {
        long s1 =
            SeedDerivation.derive(
                worldSeed,
                config.dimensionId(),
                NoiseDomain.CAVE.getSalt(),
                config.generatorVersion());

        long s2 =
            SeedDerivation.derive(
                worldSeed,
                config.dimensionId(),
                NoiseDomain.CAVE.getSalt() ^ 0xFEEDFACECAFEL,
                config.generatorVersion());

        this.primaryNoise = new GeoNoise(s1);
        this.secondaryNoise = new GeoNoise(s2);
    }

    /**
     * Evaluates cave void intensity at a world-space position.
     *
     * <p>The amount of terrain above the position controls a smooth cover mask. Two noise samples
     * are combined into a squared-distance tunnel metric; values below the tunnel threshold
     * produce a positive void intensity that is scaled by the cover mask.
     *
     * @param x world-space X coordinate
     * @param y world-space Y coordinate
     * @param z world-space Z coordinate
     * @param surfaceHeight terrain surface height above the evaluated position
     * @return cave void intensity, or {@code 0.0} when insufficient overburden exists or the
     *     tunnel threshold is not satisfied
     */
    public double evaluateCave(
        double x,
        double y,
        double z,
        double surfaceHeight
    ) {
        double overburden = surfaceHeight - y;
        if (overburden <= COVER_MIN) {
            return 0.0;
        }

        double coverMask = smoothStep(COVER_MIN, COVER_MAX, overburden);
        double n1 = primaryNoise.sample2D(x * 0.015, (z + y * 0.5) * 0.015);
        double n2 = secondaryNoise.sample2D((x - y * 0.5) * 0.015, z * 0.015);

        double tunnel = (n1 * n1) + (n2 * n2);
        if (tunnel < 0.08) {
            double voidIntensity = (0.08 - tunnel) / 0.08 * 32.0;
            return voidIntensity * coverMask;
        }

        return 0.0;
    }

    /**
     * Computes a cubic smooth transition between two edges.
     *
     * <p>The input is first normalized and clamped to {@code [0, 1]}, then transformed so that the
     * output changes smoothly from zero at {@code edge0} to one at {@code edge1}.
     *
     * @param edge0 lower transition edge
     * @param edge1 upper transition edge
     * @param x input value
     * @return smooth transition value in the range {@code [0, 1]}
     */
    private static double smoothStep(double edge0, double edge1, double x) {
        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
