package com.omms.geoengineforge.raster;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialResolver {
    private final BlockState defaultBlock;
    private final BlockState defaultFluid;
    private final BlockState deepslateBlock;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final int seaLevel;
    private final int deepslateTransitionY;

    public MaterialResolver(int seaLevel) {
        this(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(), seaLevel);
    }

    public MaterialResolver(BlockState defaultBlock, BlockState defaultFluid, int seaLevel) {
        this.defaultBlock = defaultBlock;
        this.defaultFluid = defaultFluid;
        this.deepslateBlock = Blocks.DEEPSLATE.defaultBlockState();
        this.seaLevel = seaLevel;
        this.deepslateTransitionY = 0;
    }

    public BlockState resolveSolid(int worldY) {
        if (defaultBlock.is(Blocks.STONE) && worldY < deepslateTransitionY) {
            return deepslateBlock;
        }
        return defaultBlock;
    }

    public BlockState resolveFluidOrAir(int worldY) {
        return (worldY < seaLevel) ? defaultFluid : AIR;
    }

    public BlockState resolve(int worldY, float density) {
        if (density > 0.0f) {
            return resolveSolid(worldY);
        }
        return resolveFluidOrAir(worldY);
    }
}
