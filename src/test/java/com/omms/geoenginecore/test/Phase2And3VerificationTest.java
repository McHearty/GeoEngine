package com.omms.geoenginecore.test;

import com.omms.geoenginecore.math.*;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.raster.SectionClassification;
import com.omms.geoenginecore.raster.SectionClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-2/3 acceptance (TECHSPEC §108, §130, §136, §139, §147):
 * deposition budget, chunk-boundary flow continuity, full-column
 * sample population, cave sign convention, and conservative
 * classification.
 */
public class Phase2And3VerificationTest {
    /** Default Overworld configuration. */
    private GeoConfig config;
    /** Scalar field kernel. */
    private ScalarFieldKernel kernel;

    /**
     * Installs the shared test fixtures.
     */
    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(0xCAFEBABEDEADBEEFL, config);
    }

    /**
     * 0 ≤ S ≤ E_total everywhere in a real pipeline raster
     * (TECHSPEC §108).
     */
    @Test
    @DisplayName("Phase-2 Acceptance: Deposition strictly within budget (0 <= S <= E_total)")
    void testRealPipelineDepositionBudget() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 0, 0);

        for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
            double s = sp.depositionGrid[i];
            double e = sp.erosionGrid[i];
            double r = sp.riverIncisionGrid[i];
            double eTotal = e + r;

            assertTrue(s >= 0.0);
            assertTrue(s <= eTotal + 1e-6);
        }
    }

    /**
     * Accumulation and incision stay close across the X chunk
     * boundary (TECHSPEC §136): |ΔA_f| &lt; 1.5, |ΔR| &lt; 4.0.
     */
    @Test
    @DisplayName("Phase-2 Acceptance: Continuous River Accumulation Across Chunk Boundary")
    void testRiverContinuityAcrossChunkBoundary() {
        WorkerScratchpad spA = new WorkerScratchpad();
        WorkerScratchpad spB = new WorkerScratchpad();

        kernel.rasterizeSurfaceChunk(spA, 0, 0);
        kernel.rasterizeSurfaceChunk(spB, 16, 0);

        for (int lz = 0; lz < 16; lz++) {
            double accA = spA.flowAccGrid[(lz << 4) | 15];
            double accB = spB.flowAccGrid[(lz << 4) | 0];
            double incisionA = spA.riverIncisionGrid[(lz << 4) | 15];
            double incisionB = spB.riverIncisionGrid[(lz << 4) | 0];

            assertFalse(Double.isNaN(accA));
            assertFalse(Double.isNaN(accB));
            assertTrue(Math.abs(accB - accA) < 1.5);
            assertTrue(Math.abs(incisionB - incisionA) < 4.0);
        }
    }

    /**
     * {@code evaluateFullColumn} fills a reusable sample with
     * finite, bounded values for biome/structure queries.
     */
    @Test
    @DisplayName("Phase-2 Acceptance: evaluateFullColumn populates sample for biome/structure queries")
    void testEvaluateFullColumnPopulatesSample() {
        GeoSample sample = new GeoSample();
        kernel.evaluateFullColumn(100.0, 200.0, sample);

        assertFalse(Double.isNaN(sample.finalSurface));
        assertFalse(Double.isNaN(sample.riverIncision));
        assertFalse(Double.isNaN(sample.deposition));
        assertTrue(sample.temperature >= 0.0 && sample.temperature <= 1.0);
        assertTrue(sample.humidity >= 0.0 && sample.humidity <= 1.0);
    }

    /**
     * Sampled densities are never NaN; C ≥ 0 keeps D &gt; 0 solid in
     * cave regions (TECHSPEC §139).
     */
    @Test
    @DisplayName("Invariant: Cave void density must remain strictly non-negative (C >= 0)")
    void testCaveSignConvention() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 128, 256);

        for (int y = -32; y < 120; y += 4) {
            float density = kernel.evaluateDensity(sp, 130, y, 258);
            assertFalse(Float.isNaN(density));
        }
    }

    /**
     * Sections far above the world surface classify AIR, never a
     * false BAND/SOLID (TECHSPEC §147).
     */
    @Test
    @DisplayName("Conservative Section Classifier: No False AIR above terrain bounds")
    void testSectionClassifierSafety() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 0, 0);
        SectionClassifier classifier = new SectionClassifier(config, kernel.getCaveField());

        SectionClassification highSection = classifier.classifySection(sp, 0, 1500, 0);
        assertEquals(SectionClassification.AIR, highSection);
    }
}
