package com.omms.geoengineforge.feature;

import com.omms.geoenginecore.feature.SpecialFeatureDetector;
import com.omms.geoenginecore.math.GeoSample;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class SpecialFeatureGenerator {
    private static final BlockState WATER_SOURCE = Blocks.WATER.defaultBlockState();
    private static final BlockState MAGMA_BLOCK = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();

    private SpecialFeatureGenerator() {}

    public static void materializeFeature(
        ChunkAccess chunk, BlockPos.MutableBlockPos pos, 
        SpecialFeatureDetector.FeatureType feature, GeoSample sample
    ) {
        int surfaceY = (int) Math.round(sample.finalSurface);

        switch (feature) {
            case WATERFALL_CREST -> {
                pos.set(pos.getX(), surfaceY, pos.getZ());
                chunk.setBlockState(pos, WATER_SOURCE, false);
                pos.set(pos.getX(), surfaceY - 1, pos.getZ());
                chunk.setBlockState(pos, Blocks.COBBLESTONE.defaultBlockState(), false);
            }
            case MOUNTAIN_SPRING -> {
                pos.set(pos.getX(), surfaceY, pos.getZ());
                chunk.setBlockState(pos, WATER_SOURCE, false);
            }
            case GEOTHERMAL_VENT -> {
                pos.set(pos.getX(), surfaceY, pos.getZ());
                chunk.setBlockState(pos, MAGMA_BLOCK, false);
                pos.set(pos.getX(), surfaceY - 1, pos.getZ());
                chunk.setBlockState(pos, BASALT, false);
            }
            case NONE -> {}
        }
    }
}
