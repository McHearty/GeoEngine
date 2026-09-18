package com.omms.geoenginecore.noise;

public final class SeedDerivation {
    private SeedDerivation() {}

    public static long derive(long worldSeed, int dimensionId, long domainSalt, int version) {
        long h = worldSeed ^ (domainSalt + 0x9E3779B97F4A7C15L);
        h ^= (long) dimensionId * 0x517CC1B727220A95L;
        h ^= (long) version * 0x3C6EF372FE94F82BL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    public static long hashCoords(long seed, int x, int z) {
        long h = seed + (long) x * 0x9E3779B97F4A7C15L + (long) z * 0x517CC1B727220A95L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
