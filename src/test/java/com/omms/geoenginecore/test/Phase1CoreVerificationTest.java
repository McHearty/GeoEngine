package com.omms.geoenginecore.test;

import com.omms.geoenginecore.field.DensityField;
import com.omms.geoenginecore.math.*;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.raster.SectionClassification;
import com.omms.geoenginecore.raster.SectionClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase1CoreVerificationTest {
    private static final long TEST_SEED = 0xD3ADBEEFCAFEBABEL;
    private GeoConfig config;
    private ScalarFieldKernel kernel;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(TEST_SEED, config);
    }

    @Test
    @DisplayName("Configuration validation")
    void testConfigurationValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GeoConfig(1, 0, 100, 50, 64, 0.001, 0.002, 0.003, 100, 100, 100, 2.0, 0.001, 10.0, 0.5, 0.001, 0.001, 0.001, 0.6, 1.4, 0.001, 10.0, 16.0, 16, 48.0, 0.35, -40, 128));
    }

    @Test
    @DisplayName("Position-correct climate variation across chunk")
    void testLocalClimatePositionCorrectness() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 0, 0);

        double firstTemp = sp.tempGrid[0];
        double firstErosion = sp.erosionGrid[0];

        boolean hasVariation = false;
        for (int i = 1; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
            if (sp.tempGrid[i] != firstTemp || sp.erosionGrid[i] != firstErosion) {
                hasVariation = true;
                break;
            }
        }
        assertTrue(hasVariation);
    }

    @Test
    @DisplayName("SectionClassifier emits SOLID for deep subterranean crust")
    void testSectionClassifierEmitsSolid() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, 0, 0);
        SectionClassifier classifier = new SectionClassifier(config, kernel.getCaveField());

        SectionClassification deepSection = classifier.classifySection(sp, 0, -64, 0);
        assertEquals(SectionClassification.SOLID, deepSection);
    }

    @Test
    @DisplayName("Invariant: Density Monotonicity when W=0, C=0 (dD/dy == -1)")
    void testDensityMonotonicity() {
        WorkerScratchpad scratchpad = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = -128;
        kernel.rasterizeSurfaceChunk(scratchpad, chunkX, chunkZ);

        for (int y = config.worldMinY(); y < config.worldMaxY() - 1; y++) {
            float d1 = DensityField.evaluate(scratchpad.surfaceGrid[0], y, 0.0, 0.0);
            float d2 = DensityField.evaluate(scratchpad.surfaceGrid[0], y + 1, 0.0, 0.0);
            // 1e-3f accounts for IEEE 754 float32 ULP precision at magnitude 2000
            assertEquals(-1.0f, d2 - d1, 1e-3f, "Density must decrease strictly by 1.0 per vertical block when W=0, C=0");
        }
    }
}
