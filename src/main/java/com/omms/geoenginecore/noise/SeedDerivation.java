package com.omms.geoenginecore.noise;

/**
 * Deterministic seed-domain derivation (TECHSPEC §9).
 *
 * <p>All hashes use SplitMix64-style mixing, so every output is a
 * full 64-bit avalanche of its inputs: changing the world seed,
 * dimension, domain salt, generator version, or coordinate flips
 * roughly half of the result bits and prevents any two fields from
 * correlating.
 */
public final class SeedDerivation {
    /** Hides the implicit constructor. This is a static utility class. */
    private SeedDerivation() {}

    /**
     * Derives the effective noise seed for one field domain.
     *
     * @param worldSeed seed of the generating world
     * @param dimensionId identity of the target dimension
     * @param domainSalt domain salt from {@link NoiseDomain}
     * @param version generator version
     * @return fully mixed 64-bit domain seed
     */
    public static long derive(long worldSeed, int dimensionId, long domainSalt, int version) {
        long h = worldSeed ^ (domainSalt + 0x9E3779B97F4A7C15L);
        h ^= (long) dimensionId * 0x517CC1B727220A95L;
        h ^= (long) version * 0x3C6EF372FE94F82BL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * Hashes a lattice coordinate pair into a gradient selector.
     *
     * @param seed effective domain seed from {@link #derive}
     * @param x lattice coordinate X of the noise corner
     * @param z lattice coordinate Z of the noise corner
     * @return deterministic pseudo-random hash of (seed, x, z)
     */
    public static long hashCoords(long seed, int x, int z) {
        long h = seed + (long) x * 0x9E3779B97F4A7C15L + (long) z * 0x517CC1B727220A95L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
