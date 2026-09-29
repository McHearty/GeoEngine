package com.omms.geoenginecore.test.invariants;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.raster.SectionClassification;
import com.omms.geoenginecore.raster.SectionClassifier;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.defaultConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C15: Section classification invariant.
 *
 * <p>No false SOLID/AIR classifications. Sections below the surface
 * must be SOLID, sections above must be AIR.
 */
public class InvariantSectionClassificationTest {

    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private SectionClassifier classifier;
    private GeoSample sample;

    @BeforeEach
    void setUp() {
        config = defaultConfig();
        kernel = new ScalarFieldKernel(0xCAFEBABEDEADBEEFL, config);
        classifier = new SectionClassifier(config, kernel.getCaveField());
        sample = new GeoSample();
    }

    @Tag("invariant")
    @Tag("must")
    @Test
    @DisplayName("C15: No false SOLID/AIR section classifications")
    void testSectionClassification() {
        // Evaluate at multiple locations
        for (int x = 0; x < 256; x += 16) {
            for (int z = 0; z < 256; z += 16) {
                kernel.evaluateFullColumn(x, z, sample);

                double surfaceY = sample.finalSurface;

                // Check sections below surface (should be SOLID or BAND if near surface)
                WorkerScratchpad sp = ScratchpadProvider.get();
                kernel.rasterizeSurfaceChunk(sp, x, z);

                for (int section = 0; section < 16; section++) {
                    double sectionY = section * 128.0;
                    if (sectionY + 64.0 < surfaceY) {
                        // Entire section is below surface
                        SectionClassification classification =
                                classifier.classifySection(sp, x, section * 128, z);
                        // Sections entirely below surface must be SOLID (not AIR)
                        assertNotEquals(SectionClassification.AIR, classification,
                                "C15: False AIR classification below surface at section " + section +
                                        ", (" + x + "," + z + "): got " + classification);
                    }
                }

                // Check sections above surface (should be AIR or BAND if near surface)
                for (int section = 16; section < 128; section++) {
                    double sectionY = section * 128.0;
                    if (sectionY > surfaceY + 64.0) {
                        // Entire section is above surface
                        SectionClassification classification =
                                classifier.classifySection(sp, x, section * 128, z);
                        // Sections entirely above surface must be AIR (not SOLID)
                        assertNotEquals(SectionClassification.SOLID, classification,
                                "C15: False SOLID classification above surface at section " + section +
                                        ", (" + x + "," + z + "): got " + classification);
                    }
                }
            }
        }
    }
}
