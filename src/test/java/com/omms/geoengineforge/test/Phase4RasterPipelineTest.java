package com.omms.geoengineforge.test;

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

public class Phase4RasterPipelineTest {
    private GeoConfig config;
    private ScalarFieldKernel kernel;
    private SectionClassifier classifier;

    public static final class SimulatedChunkColumn {
        public static final int TOTAL_SECTIONS = 128;
        public final SectionClassification[] classifications = new SectionClassification[TOTAL_SECTIONS];
        public int bulkSolidCount = 0;
        public int bulkAirCount = 0;
        public int bandVoxelEvaluatedSections = 0;
        public long totalVoxelEvaluations = 0;

        public final int[] worldSurfaceHeightmap = new int[256];
        public final int[] oceanFloorHeightmap = new int[256];
    }

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(0xCAFEBABEL, config);
        classifier = new SectionClassifier(config, kernel.getCaveField());
    }

    @Test
    @DisplayName("Phase-4 Harness: Full chunk rasterization lifecycle with decoupled pipeline delegation")
    void testSimulatedChunkLifecycle() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        int chunkX = 64;
        int chunkZ = -128;

        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);

        SimulatedChunkColumn chunk = new SimulatedChunkColumn();

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int surfH = (int) Math.round(sp.surfaceGrid[(z << 4) | x]);
                chunk.worldSurfaceHeightmap[(z << 4) | x] = Math.max(surfH, config.seaLevel());
                chunk.oceanFloorHeightmap[(z << 4) | x] = surfH;
            }
        }

        for (int sIdx = 0; sIdx < SimulatedChunkColumn.TOTAL_SECTIONS; sIdx++) {
            int sectionBlockY = config.worldMinY() + (sIdx << 4);
            SectionClassification classification = classifier.classifySection(sp, chunkX, sectionBlockY, chunkZ);
            chunk.classifications[sIdx] = classification;

            switch (classification) {
                case AIR -> chunk.bulkAirCount++;
                case SOLID -> chunk.bulkSolidCount++;
                case BAND -> {
                    chunk.bandVoxelEvaluatedSections++;
                    for (int ly = 0; ly < 16; ly++) {
                        int wy = sectionBlockY + ly;
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                float d = kernel.evaluateDensity(sp, chunkX + lx, wy, chunkZ + lz);
                                assertFalse(Float.isNaN(d));
                                chunk.totalVoxelEvaluations++;
                            }
                        }
                    }
                }
            }
        }

        assertTrue(chunk.bulkSolidCount > 0);
        assertTrue(chunk.bulkAirCount > 60);
        assertTrue(chunk.bandVoxelEvaluatedSections < 25);

        long maxPossibleVoxels = 128L * 4096L;
        double evaluationRatio = (double) chunk.totalVoxelEvaluations / maxPossibleVoxels;
        assertTrue(evaluationRatio < 0.20);

        for (int i = 0; i < 256; i++) {
            assertTrue(chunk.worldSurfaceHeightmap[i] >= config.seaLevel());
            assertTrue(chunk.oceanFloorHeightmap[i] >= config.worldMinY());
        }
    }
}
