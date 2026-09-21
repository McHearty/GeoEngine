package com.omms.geoenginecore.noise;

/**
 * 2-D gradient (Perlin-style) noise over a hexagonal lattice.
 *
 * <p>Lattice corners are mapped to gradient selectors through
 * {@link SeedDerivation#hashCoords}, so the noise is a pure function
 * of the domain seed and coordinate (TECHSPEC §8, §9). The 45.0 scale
 * is the engine's field amplitude normalization; consuming fields
 * renormalize the result to their own physical range.
 */
public final class GeoNoise {
    /** Domain seed this noise stream was derived from (TECHSPEC §9). */
    private final long seed;

    /**
     * @param seed domain seed this noise stream was derived from
     */
    public GeoNoise(long seed) {
        this.seed = seed;
    }

    /** Hexagonal lattice skew factor: 0.5·(√3 − 1). */
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    /** Hexagonal lattice unskew factor: (3 − √3) / 6. */
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    /**
     * Samples the 2-D gradient noise at (x, z).
     *
     * <p>The input point is mapped onto the hexagonal lattice and the
     * three contributing corners are dotted against deterministic
     * gradient selectors. All lattice hashes derive from the domain
     * seed, keeping the field reproducible across runs and platforms.
     *
     * @param x sample coordinate X in the domain's units
     * @param z sample coordinate Z in the domain's units
     * @return scaled noise value
     */
    public double sample2D(double x, double z) {
        double s = (x + z) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(z + s);

        double t = (i + j) * G2;
        double X0 = i - t;
        double Z0 = j - t;
        double x0 = x - X0;
        double z0 = z - Z0;

        int i1, j1;
        if (x0 > z0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        double x1 = x0 - i1 + G2;
        double z1 = z0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double z2 = z0 - 1.0 + 2.0 * G2;

        long h0 = SeedDerivation.hashCoords(seed, i, j);
        long h1 = SeedDerivation.hashCoords(seed, i + i1, j + j1);
        long h2 = SeedDerivation.hashCoords(seed, i + 1, j + 1);

        double n0 = grad(h0, x0, z0);
        double n1 = grad(h1, x1, z1);
        double n2 = grad(h2, x2, z2);

        return 45.0 * (n0 + n1 + n2);
    }

    /**
     * Evaluates the Perlin kernel t⁴·g at one lattice corner.
     *
     * @param hash pseudo-random gradient selector from the domain hash
     * @param x local offset X from the corner, in [0, 1]
     * @param z local offset Z from the corner, in [0, 1]
     * @return kernel contribution, zero outside the support radius
     */
    private static double grad(long hash, double x, double z) {
        double t = 0.5 - x * x - z * z;
        if (t < 0.0) return 0.0;
        t *= t;
        int h = (int) (hash & 7);
        double u = h < 4 ? x : z;
        double v = h < 4 ? z : x;
        double g = ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        return t * t * g;
    }

    /**
     * Fast floor for values whose integer part fits an
     * {@code int}, including negative fractional inputs.
     *
     * @param x value to floor
     * @return largest integer ≤ x
     */
    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
