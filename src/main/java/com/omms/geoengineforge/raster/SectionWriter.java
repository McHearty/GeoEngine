package com.omms.geoengineforge.raster;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Bulk section writes: 16³ loops over plain
 * {@code setBlockState(index, false)} (TECHSPEC §210, §212).
 */
public final class SectionWriter {
    /** Hides the implicit constructor. This is a static utility class. */
    private SectionWriter() {}

    /**
     * Fills every voxel of the section with one state, holding the
     * section lock for the whole fill.
     *
     * @param section section to fill
     * @param state state to write
     */
    public static void fillSectionBulk(LevelChunkSection section, BlockState state) {
        section.acquire();
        try {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        section.setBlockState(x, y, z, state, false);
                    }
                }
            }
        } finally {
            section.release();
        }
    }

    /**
     * Writes a single section voxel.
     *
     * @param section section
     * @param lx section-relative X
     * @param ly section-relative Y
     * @param lz section-relative Z
     * @param state state to write
     */
    public static void setVoxel(LevelChunkSection section, int lx, int ly, int lz, BlockState state) {
        section.setBlockState(lx, ly, lz, state, false);
    }
}
