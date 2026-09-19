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

    private SpecialFeatureGenerator() {}

    /**
     * Injects deterministic special feature blocks directly into the generated chunk.
     * Uses verified world coordinates and checks local support to prevent floating artifacts.
     */
    public static void materializeFeature(
        ChunkAccess chunk, BlockPos.MutableBlockPos pos, 
        SpecialFeatureDetector.FeatureType feature, GeoSample sample,
        int wx, int wz
    ) {
        int surfaceY = (int) Math.round(sample.finalSurface);

        switch (feature) {
            case WATERFALL_CREST -> {
                pos.set(wx, surfaceY, wz);
                BlockState floor = chunk.getBlockState(pos.below());

                // Must rest on solid rock with air at the crest block
                if (!floor.isAir() && !floor.is(Blocks.WATER) && chunk.getBlockState(pos).isAir()) {
                    chunk.setBlockState(pos, WATER_SOURCE, false);
                }
            }
            case MOUNTAIN_SPRING -> {
                pos.set(wx, surfaceY, wz);
                BlockState floor = chunk.getBlockState(pos.below());
                if (!floor.isAir() && !floor.is(Blocks.WATER)) {
                    chunk.setBlockState(pos, WATER_SOURCE, false);
                }
            }
            case GEOTHERMAL_VENT -> {
                pos.set(wx, surfaceY, wz);
                chunk.setBlockState(pos, MAGMA_BLOCK, false);
            }
            case NONE -> {}
        }
    }
}
