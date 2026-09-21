package com.omms.geoenginecore.test;

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
 * Phase-3 section pruning budget (TECHSPEC §150): on a 2048-block
 * column, N_band / N_total must stay under 20%, with a healthy
 * SOLID/AIR mix.
 */
public class Phase3BandRatioAllocationTest {
    /** Default Overworld configuration. */
    private GeoConfig config;
    /** Scalar field kernel. */
    private ScalarFieldKernel kernel;
    /** Section classifier. */
    private SectionClassifier classifier;

    /**
     * Installs the shared test fixtures.
     */
    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(0x1337C0D3L, config);
        classifier = new SectionClassifier(config, kernel.getCaveField());
    }

    /**
     * Classifies all 128 sections of a 2048-block column and
     * asserts the &lt;20% BAND budget plus a healthy SOLID/AIR mix
     * (TECHSPEC §150).
     */
    @Test
    @DisplayName("Quantitative Section Pruning (N_band / N_total <= 20% on 2048-block column)")
    void testBandRatioMeasurementOn2048World() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = 64;

        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);

        int totalSections = 128;
        int solidCount = 0;
        int airCount = 0;
        int bandCount = 0;

        for (int s = 0; s < totalSections; s++) {
            int sectionMinY = -64 + (s * 16);
            SectionClassification classification = classifier.classifySection(sp, chunkX, sectionMinY, chunkZ);

            switch (classification) {
                case SOLID -> solidCount++;
                case AIR -> airCount++;
                case BAND -> bandCount++;
            }
        }

        double bandRatio = (double) bandCount / totalSections;
        assertTrue(bandRatio < 0.20);
        assertTrue(solidCount > 0);
        assertTrue(airCount > 50);
    }
}
