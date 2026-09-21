package com.omms.geoengineforge.raster;

import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.raster.SectionClassification;
import com.omms.geoenginecore.raster.SectionClassifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Section pipeline rasterizer (TECHSPEC §211): fills 16³ chunk
 * sections from the deterministic pipeline.
 *
 * <p>Per section: AIR → bulk-fill with water (below sea level) or
 * air (above); SOLID → bulk-fill with solid strata; BAND → per-voxel
 * D-driven fill, the only case that touches the kernel at all.
 *
 * <p>Every write path is a plain {@link LevelChunkSection}
 * {@code setBlockState(index, false)} — no vanilla NoiseChunk, no
 * 2-D/3-D mismatch (TECHSPEC §210, §212).
 */
public final class ChunkRasterizer {
    /** Field kernel (density evaluation). */
    private final FieldKernel kernel;
    /** Coarse section classifier. */
    private final SectionClassifier classifier;
    /** Strata material resolver. */
    private final MaterialResolver materialResolver;

    /**
     * @param kernel field kernel
     * @param classifier coarse section classifier
     * @param materialResolver strata material resolver
     */
    public ChunkRasterizer(FieldKernel kernel, SectionClassifier classifier, MaterialResolver materialResolver) {
        this.kernel = kernel;
        this.classifier = classifier;
        this.materialResolver = materialResolver;
    }

    /**
     * Runs the §211 section pipeline for every section of the chunk.
     *
     * @param sections chunk sections, ascending from the world floor
     * @param minSectionY world Y of the bottom section
     * @param scratchpad worker scratchpad
     * @param chunkWorldX world X of the chunk origin
     * @param chunkWorldZ world Z of the chunk origin
     * @param seaLevel configured sea level
     */
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

    /**
     * Per-voxel fill for a BAND section: every voxel takes the
     * density-resolved material (TECHSPEC §211).
     *
     * @param section section to fill
     * @param scratchpad worker scratchpad
     * @param chunkWorldX world X of the chunk origin
     * @param sectionBlockY world Y of the section's bottom block
     * @param chunkWorldZ world Z of the chunk origin
     */
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
