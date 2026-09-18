package com.omms.geoengineforge.raster;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class SectionWriter {
    private SectionWriter() {}

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

    public static void setVoxel(LevelChunkSection section, int lx, int ly, int lz, BlockState state) {
        section.setBlockState(lx, ly, lz, state, false);
    }
}
