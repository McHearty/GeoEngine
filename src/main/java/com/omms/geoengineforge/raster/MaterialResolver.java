package com.omms.geoengineforge.raster;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Basic strata material resolution for non-band section fills
 * (TECHSPEC §211): solid strata by depth, water or air by sea level.
 */
public final class MaterialResolver {
    /** Default solid strata. */
    private final BlockState defaultBlock;
    /** Default fluid (sub-surface water). */
    private final BlockState defaultFluid;
    /** Deepslate used below the deep transition. */
    private final BlockState deepslateBlock;
    /** Air. */
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Configured sea level. */
    private final int seaLevel;
    /** Deepslate transition depth (currently 0: stone everywhere above the floor). */
    private final int deepslateTransitionY;

    /**
     * Overworld convenience constructor (stone / water).
     *
     * @param seaLevel configured sea level
     */
    public MaterialResolver(int seaLevel) {
        this(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(), seaLevel);
    }

    /**
     * @param defaultBlock default solid strata
     * @param defaultFluid default fluid
     * @param seaLevel configured sea level
     */
    public MaterialResolver(BlockState defaultBlock, BlockState defaultFluid, int seaLevel) {
        this.defaultBlock = defaultBlock;
        this.defaultFluid = defaultFluid;
        this.deepslateBlock = Blocks.DEEPSLATE.defaultBlockState();
        this.seaLevel = seaLevel;
        this.deepslateTransitionY = 0;
    }

    /**
     * Solid strata for a depth, switching to deepslate below the
     * transition.
     *
     * @param worldY absolute block Y
     * @return solid strata state
     */
    public BlockState resolveSolid(int worldY) {
        if (defaultBlock.is(Blocks.STONE) && worldY < deepslateTransitionY) {
            return deepslateBlock;
        }
        return defaultBlock;
    }

    /**
     * Water below sea level, air above.
     *
     * @param worldY absolute block Y
     * @return fluid or air state
     */
    public BlockState resolveFluidOrAir(int worldY) {
        return (worldY < seaLevel) ? defaultFluid : AIR;
    }

    /**
     * Density-driven dispatch: solid when D &gt; 0, else
     * fluid/air.
     *
     * @param worldY absolute block Y
     * @param density canonical terrain density D
     * @return resolved state
     */
    public BlockState resolve(int worldY, float density) {
        if (density > 0.0f) {
            return resolveSolid(worldY);
        }
        return resolveFluidOrAir(worldY);
    }
}
