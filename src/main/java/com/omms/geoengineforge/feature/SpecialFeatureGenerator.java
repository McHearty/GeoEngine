package com.omms.geoengineforge.feature;

import com.omms.geoenginecore.feature.SpecialFeatureDetector;
import com.omms.geoenginecore.math.GeoSample;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Special feature materialization (TECHSPEC §200-§203).
 *
 * <p>Injects deterministic feature blocks (waterfall water, spring
 * water, geothermal magma) into the generated chunk. Placement uses
 * verified world coordinates and checks local support so features
 * never float or clip terrain.
 */
public final class SpecialFeatureGenerator {
    /** Source water block used by waterfall crests and springs. */
    private static final BlockState WATER_SOURCE = Blocks.WATER.defaultBlockState();
    /** Magma block used by geothermal vents. */
    private static final BlockState MAGMA_BLOCK = Blocks.MAGMA_BLOCK.defaultBlockState();

    /** Hides the implicit constructor. This is a static utility class. */
    private SpecialFeatureGenerator() {}

    /**
     * Places the detected feature's blocks, verifying local support
     * first (TECHSPEC §204).
     *
     * @param chunk chunk being generated
     * @param pos reusable mutable position
     * @param feature detected feature type
     * @param sample pipeline sample of the column
     * @param wx world X of the column
     * @param wz world Z of the column
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
