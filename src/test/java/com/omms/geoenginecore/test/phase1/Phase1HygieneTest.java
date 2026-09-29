package com.omms.geoenginecore.test.phase1;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 implicit hygiene tests (TECHSPEC §9, §48).
 *
 * <p>P1-09: Seed domains produce decorrelated streams.
 * <p>P1-10: H₀ identity holds.
 *
 * <p>Seal gate: P1-09 must be green; P1-10 is SHOULD.
 */
public class Phase1HygieneTest {

    private static final long WORLD_SEED = 0xCAFEBABEDEADBEEFL;
    private static final int DIMENSION = 1;
    private static final int VERSION = 1;

    /**
     * P1-09: Seed domains produce decorrelated streams.
     *
     * <p>For each pair of distinct domain salts, derive the effective
     * seeds and verify they differ in at least 16 bits (Hamming
     * distance ≥ 16). This ensures the SplitMix64-style derivation
     * provides sufficient avalanche and prevents accidental
     * correlation between fields.
     */
    @Test
    @DisplayName("P1-09: Seed domains produce decorrelated streams")
    void testSeedDomainDecorrelation() {
        // Derive seeds for all domain salts
        long[] derivedSeeds = new long[NoiseDomain.values().length];
        for (int i = 0; i < NoiseDomain.values().length; i++) {
            NoiseDomain domain = NoiseDomain.values()[i];
            derivedSeeds[i] = SeedDerivation.derive(WORLD_SEED, DIMENSION, domain.getSalt(), VERSION);
        }

        // Check pairwise Hamming distance
        for (int i = 0; i < derivedSeeds.length; i++) {
            for (int j = i + 1; j < derivedSeeds.length; j++) {
                long xor = derivedSeeds[i] ^ derivedSeeds[j];
                int hamming = Long.bitCount(xor);
                assertTrue(hamming >= 16,
                        "Domain seeds too similar: " + NoiseDomain.values()[i] + " vs "
                        + NoiseDomain.values()[j] + " differ by only " + hamming
                        + " bits (expected ≥ 16)");
            }
        }

        // Also verify that changing the world seed produces different derived seeds
        long altWorldSeed = WORLD_SEED ^ 0x1;
        for (NoiseDomain domain : NoiseDomain.values()) {
            long seed1 = SeedDerivation.derive(WORLD_SEED, DIMENSION, domain.getSalt(), VERSION);
            long seed2 = SeedDerivation.derive(altWorldSeed, DIMENSION, domain.getSalt(), VERSION);
            int hamming = Long.bitCount(seed1 ^ seed2);
            assertTrue(hamming >= 16,
                    "World seed change not reflected in derived seed for " + domain
                    + ": differ by only " + hamming + " bits");
        }
    }

    /**
     * P1-10: H₀ identity.
     *
     * <p>For every evaluated column, verify that sample.rawTectonic −
     * sample.erosionLowering == sample.surfaceH0. This validates the
     * fundamental surface elevation identity (TECHSPEC §48).
     */
    @Test
    @DisplayName("P1-10: H0 identity rawTectonic - erosionLowering == surfaceH0")
    void testH0Identity() {
        GeoConfig config = defaultConfig();
        ScalarFieldKernel kernel = new ScalarFieldKernel(WORLD_SEED, config);
        GeoSample sample = new GeoSample();

        // Evaluate multiple columns
        int[] testX = {130, 258, 386, 514, 642};
        int[] testZ = {130, 258, 386, 514, 642};

        for (int x : testX) {
            for (int z : testZ) {
                kernel.evaluateFullColumn(x, z, sample);

                double expected = sample.rawTectonic - sample.erosionLowering;
                assertEquals(expected, sample.surfaceH0, 1e-9,
                        "H0 identity violated at (" + x + "," + z + "): "
                        + "rawTectonic(" + sample.rawTectonic + ") - erosion("
                        + sample.erosionLowering + ") = " + expected
                        + " but surfaceH0 = " + sample.surfaceH0);
            }
        }
    }
}
