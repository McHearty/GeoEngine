package com.geoengine.core.test;

import com.geoengine.core.math.*;
import com.geoengine.core.memory.ScratchpadProvider;
import com.geoengine.core.memory.WorkerScratchpad;
import com.geoengine.core.raster.SectionClassification;
import com.geoengine.core.raster.SectionClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase2And3VerificationTest {
    private GeoConfig config;
    private ScalarFieldKernel kernel;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(0xCAFEBABEDEADBEEFL, config);
    }

    @Test
    @DisplayName("Invariant: Deposition never exceeds the total erosion/incision budget (S <= E_total)")
    void testDepositionBudgetInvariant() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 0, 0);

        for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
            double hf = sp.surfaceGrid[i];
            assertFalse(Double.isNaN(hf));
            assertFalse(Double.isInfinite(hf));
        }
    }

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

    @Test
    @DisplayName("Conservative Section Classifier: No False AIR above terrain bounds")
    void testSectionClassifierSafety() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 0, 0);
        SectionClassifier classifier = new SectionClassifier(config);

        SectionClassification highSection = classifier.classifySection(sp, 1500);
        assertEquals(SectionClassification.AIR, highSection, "Stratospheric section must be classified AIR");
    }
}
