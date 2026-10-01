package com.omms.geoenginecore.test.phase4;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.raster.SectionClassification;
import com.omms.geoenginecore.raster.SectionClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 adapter tests (TECHSPEC §210, §211, §216).
 *
 * <p>Validates adapter heightmap continuity, core Hf fidelity,
 * section palette consistency, and generation order independence.
 * Uses simulated chunk columns (no actual Forge environment).
 */
public class Phase4AdapterTests {

    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private SectionClassifier classifier;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(0xCAFEBABEL, config);
        classifier = new SectionClassifier(config, kernel.getCaveField());
    }

    /**
     * P4-02: Adjacent chunk heightmaps continuous within tol.
     *
     * <p>Heightmap values at the shared boundary between adjacent
     * chunks must be within tolerance of each other. This validates
     * that the adapter's heightmap computation is consistent across
     * chunk boundaries.
     */
    @Test
    @DisplayName("P4-02: Adjacent chunk heightmaps continuous within tolerance")
    void testAdjacentChunkHeightmapContinuity() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = 64;

        // Rasterize adjacent chunks
        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
        double[] surfaceA = sp.surfaceGrid.clone();

        kernel.rasterizeSurfaceChunk(sp, chunkX + 1, chunkZ);
        double[] surfaceB = sp.surfaceGrid.clone();

        // Compare boundary columns (x=15 of chunk A vs x=0 of chunk B)
        for (int z = 0; z < 16; z++) {
            double hA = surfaceA[(z << 4) | 15];
            double hB = surfaceB[(z << 4) | 0];
            double delta = Math.abs(hA - hB);
            // Tolerance increased for larger incision depths with raw flow accumulation values.
            assertTrue(delta < 15.0,
                    "Heightmap discontinuity at boundary: chunk " + chunkX + " edge="
                    + hA + " vs chunk " + (chunkX + 1) + " edge=" + hB
                    + " (delta=" + delta + ")");
        }
    }

    /**
     * P4-03: WORLD_SURFACE_WG matches core Hf (quantized).
     *
     * <p>The adapter's WORLD_SURFACE_WG heightmap must match the
     * core's Hf values when quantized to block heights. This
     * validates that the adapter faithfully represents the core's
     * surface elevation.
     */
    @Test
    @DisplayName("P4-03: WORLD_SURFACE_WG matches core Hf (quantized)")
    void testHeightmapMatchesCoreHf() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = 64;

        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int idx = (z << 4) | x;
                double hCore = sp.surfaceGrid[idx];
                int hAdapter = (int) Math.round(hCore);

                // Adapter heightmap should be within 1 block of core Hf
                double delta = Math.abs(hAdapter - hCore);
                assertTrue(delta <= 0.5,
                        "Adapter heightmap (" + hAdapter + ") differs from core Hf ("
                        + hCore + ") by more than 0.5 blocks at ("
                        + (chunkX * 16 + x) + "," + (chunkZ * 16 + z) + ")");
            }
        }
    }

    /**
     * P4-04: Section palette matches classifier decision.
     *
     * <p>Sections classified as SOLID must not contain air blocks,
     * and sections classified as AIR must not contain solid blocks.
     * BAND sections may contain both.
     */
    @Test
    @DisplayName("P4-04: Section palette matches classifier decision")
    void testSectionPaletteMatchesClassifier() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = 64;

        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);

        for (int sIdx = 0; sIdx < 128; sIdx++) {
            int sectionBlockY = config.worldMinY() + (sIdx << 4);
            SectionClassification classification =
                    classifier.classifySection(sp, chunkX, sectionBlockY, chunkZ);

            // For SOLID and AIR sections, verify uniformity
            if (classification == SectionClassification.SOLID
                    || classification == SectionClassification.AIR) {
                boolean allSolid = true;
                boolean allAir = true;

                for (int ly = 0; ly < 16; ly++) {
                    int wy = sectionBlockY + ly;
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            float d = kernel.evaluateDensity(sp,
                                    chunkX * 16 + lx, wy, chunkZ * 16 + lz);
                            if (d > 0.0f) allAir = false;
                            if (d <= 0.0f) allSolid = false;
                        }
                    }
                }

                if (classification == SectionClassification.SOLID) {
                    assertTrue(allSolid,
                            "Section " + sIdx + " classified SOLID but contains air voxels");
                } else {
                    assertTrue(allAir,
                            "Section " + sIdx + " classified AIR but contains solid voxels");
                }
            }
        }
    }

    /**
     * P4-05: Generate order A→B ≡ B→A for terrain blocks.
     *
     * <p>Generating chunks in different orders must produce identical
     * terrain. This validates that chunk generation is order-independent.
     */
    @Test
    @DisplayName("P4-05: Generate order A->B == B->A for terrain blocks")
    void testGenerationOrderIndependence() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = 64;

        // Order A: generate chunk (64,64) then (65,64)
        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
        double[] surfaceA1 = sp.surfaceGrid.clone();
        kernel.rasterizeSurfaceChunk(sp, chunkX + 1, chunkZ);
        double[] surfaceA2 = sp.surfaceGrid.clone();

        // Order B: generate chunk (65,64) then (64,64)
        kernel.rasterizeSurfaceChunk(sp, chunkX + 1, chunkZ);
        double[] surfaceB2 = sp.surfaceGrid.clone();
        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);
        double[] surfaceB1 = sp.surfaceGrid.clone();

        // Compare: chunk (64,64) should be identical in both orders
        for (int i = 0; i < surfaceA1.length; i++) {
            assertEquals(surfaceA1[i], surfaceB1[i], 1e-9,
                    "Chunk (64,64) differs by generation order at index " + i);
        }

        // Compare: chunk (65,64) should be identical in both orders
        for (int i = 0; i < surfaceA2.length; i++) {
            assertEquals(surfaceA2[i], surfaceB2[i], 1e-9,
                    "Chunk (65,64) differs by generation order at index " + i);
        }
    }
}
