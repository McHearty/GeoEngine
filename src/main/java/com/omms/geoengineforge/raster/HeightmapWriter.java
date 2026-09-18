package com.omms.geoengineforge.raster;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class HeightmapWriter {
    private HeightmapWriter() {}

    public static void populate(ChunkAccess chunk, double[] surfaceGrid, int seaLevel, BlockState solidSample) {
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int surfH = (int) Math.round(surfaceGrid[(z << 4) | x]);
                worldSurface.update(x, Math.max(surfH, seaLevel), z, solidSample);
                oceanFloor.update(x, surfH, z, solidSample);
            }
        }
    }
}
