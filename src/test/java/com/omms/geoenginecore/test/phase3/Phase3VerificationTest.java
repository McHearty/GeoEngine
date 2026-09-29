package com.omms.geoenginecore.test.phase3;

import com.omms.geoenginecore.math.*;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.raster.SectionClassification;
import com.omms.geoenginecore.raster.SectionClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-3 acceptance (TECHSPEC §147):
 * conservative section classification.
 */
public class Phase3VerificationTest {
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
     * Sections far above the world surface classify AIR, never a
     * false BAND/SOLID (TECHSPEC §147).
     */

    @Tag("must")
    @Tag("phase3")
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
