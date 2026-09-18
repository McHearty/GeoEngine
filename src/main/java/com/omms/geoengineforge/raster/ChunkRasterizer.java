package com.omms.geoengineforge.raster;

import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.raster.SectionClassification;
import com.omms.geoenginecore.raster.SectionClassifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class ChunkRasterizer {
    private final FieldKernel kernel;
    private final SectionClassifier classifier;
    private final MaterialResolver materialResolver;

    public ChunkRasterizer(FieldKernel kernel, SectionClassifier classifier, MaterialResolver materialResolver) {
        this.kernel = kernel;
        this.classifier = classifier;
        this.materialResolver = materialResolver;
    }

    public void rasterizeSections(
        LevelChunkSection[] sections, int minSectionY, WorkerScratchpad scratchpad,
        int chunkWorldX, int chunkWorldZ, int seaLevel
    ) {
        for (int sIdx = 0; sIdx < sections.length; sIdx++) {
            LevelChunkSection section = sections[sIdx];
            int sectionBlockY = (minSectionY + sIdx) << 4;

            SectionClassification classification = classifier.classifySection(
                scratchpad, chunkWorldX, sectionBlockY, chunkWorldZ
            );

            if (classification == SectionClassification.AIR) {
                if (sectionBlockY + 16 <= seaLevel) {
                    SectionWriter.fillSectionBulk(section, materialResolver.resolveFluidOrAir(sectionBlockY));
                }
                continue;
            }

            if (classification == SectionClassification.SOLID) {
                SectionWriter.fillSectionBulk(section, materialResolver.resolveSolid(sectionBlockY));
                continue;
            }

            rasterizeBandSection(section, scratchpad, chunkWorldX, sectionBlockY, chunkWorldZ);
        }
    }

    public void rasterizeBandSection(
        LevelChunkSection section, WorkerScratchpad scratchpad,
        int chunkWorldX, int sectionBlockY, int chunkWorldZ
    ) {
        section.acquire();
        try {
            for (int ly = 0; ly < 16; ly++) {
                int wy = sectionBlockY + ly;
                for (int lz = 0; lz < 16; lz++) {
                    int wz = chunkWorldZ + lz;
                    for (int lx = 0; lx < 16; lx++) {
                        int wx = chunkWorldX + lx;

                        float d = kernel.evaluateDensity(scratchpad, wx, wy, wz);
                        BlockState state = materialResolver.resolve(wy, d);
                        SectionWriter.setVoxel(section, lx, ly, lz, state);
                    }
                }
            }
        } finally {
            section.release();
        }
    }
}
