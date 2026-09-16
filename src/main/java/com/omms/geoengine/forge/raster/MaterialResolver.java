package com.geoengine.forge.raster;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialResolver {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final int seaLevel;
    private final int deepslateTransitionY;

    public MaterialResolver(int seaLevel) {
        this.seaLevel = seaLevel;
        this.deepslateTransitionY = 0;
    }

    public BlockState resolveSolid(int worldY) {
        return (worldY < deepslateTransitionY) ? DEEPSLATE : STONE;
    }

    public BlockState resolveFluidOrAir(int worldY) {
        return (worldY < seaLevel) ? WATER : AIR;
    }

    public BlockState resolve(int worldY, float density) {
        if (density > 0.0f) {
            return resolveSolid(worldY);
        }
        return resolveFluidOrAir(worldY);
    }
}
