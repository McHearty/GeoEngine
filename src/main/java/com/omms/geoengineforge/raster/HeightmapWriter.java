package com.omms.geoengineforge.raster;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Worldgen heightmap population (TECHSPEC §212).
 *
 * <p>Fills WORLD_SURFACE_WG and OCEAN_FLOOR_WG from the rasterized
 * continuous surface grid; both are clamped to the buildable range
 * and validated against the written chunk data (TECHSPEC §216).
 */
public final class HeightmapWriter {
    /** Hides the implicit constructor. This is a static utility class. */
    private HeightmapWriter() {}

    /**
     * Populates both WG heightmaps from the surface grid
     * (TECHSPEC §212).
     *
     * @param chunk chunk to stamp
     * @param surfaceGrid 16×16 continuous surface H grid
     * @param seaLevel configured sea level
     * @param solidSample representative solid state for validation
     */
    public static void populate(
        ChunkAccess chunk,
        double[] surfaceGrid,
        int seaLevel,
        BlockState solidSample
    ) {
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);

        final int minY = chunk.getMinBuildHeight();
        final int maxY = chunk.getMaxBuildHeight() - 1; // last valid block Y

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int surfH = (int) Math.round(surfaceGrid[(z << 4) | x]);
                surfH = Math.clamp(surfH, minY, maxY);

                int surfaceForWs = Math.clamp(Math.max(surfH, seaLevel), minY, maxY);

                worldSurface.update(x, surfaceForWs, z, solidSample);
                oceanFloor.update(x, surfH, z, solidSample);
            }
        }
    }
}
