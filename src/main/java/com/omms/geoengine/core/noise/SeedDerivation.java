package com.geoengine.core.noise;

/**
 * Provides deterministic seed derivation and coordinate hashing for terrain noise fields.
 *
 * <p>The derivation functions combine the world seed with dimension, noise-domain, generator
 * version, or coordinate inputs so that terrain fields remain reproducible while different
 * domains and coordinates receive different hash inputs.
 */
public final class SeedDerivation {

    private SeedDerivation() {}

    /**
     * Derives a deterministic seed for a specific world, dimension, noise domain, and generator
     * version.
     *
     * <p>Changing any of the supplied inputs changes the hash input used to produce the derived
     * seed. The result is intended for initializing independent deterministic noise fields.
     *
     * @param worldSeed world seed
     * @param dimensionId dimension identifier
     * @param domainSalt salt identifying the noise domain
     * @param version generator version used as part of seed separation
     * @return deterministically derived seed
     */
    public static long derive(
        long worldSeed,
        int dimensionId,
        long domainSalt,
        int version
    ) {
        long h = worldSeed ^ (domainSalt + 0x9E3779B97F4A7C15L);
        h ^= (long) dimensionId * 0x517CC1B727220A95L;
        h ^= (long) version * 0x3C6EF372FE94F82BL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * Produces a deterministic hash for a two-dimensional integer coordinate.
     *
     * <p>The coordinate values are mixed with the supplied seed before the final bit-mixing
     * sequence is applied. The result can be used to select deterministic local noise gradients.
     *
     * @param seed base seed for the coordinate field
     * @param x integer X coordinate
     * @param z integer Z coordinate
     * @return deterministic hash for {@code (seed, x, z)}
     */
    public static long hashCoords(long seed, int x, int z) {
        long h =
            seed
                + (long) x * 0x9E3779B97F4A7C15L
                + (long) z * 0x517CC1B727220A95L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
