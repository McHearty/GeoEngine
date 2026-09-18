package com.omms.geoengineforge.raster;

import com.omms.geoenginecore.material.LithologyField;
import com.omms.geoenginecore.material.RockFamily;
import com.omms.geoenginecore.math.GeoConfig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class StrataMaterialResolver {
    private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState PODZOL = Blocks.PODZOL.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();

    private final LithologyField lithologyField;
    private final int seaLevel;

    public StrataMaterialResolver(long worldSeed, GeoConfig config) {
        this.lithologyField = new LithologyField(worldSeed, config);
        this.seaLevel = config.seaLevel();
    }

    public BlockState resolveSurfaceCover(double surfaceH, double slope, double temp, double humid) {
        if (surfaceH > seaLevel + 220.0 || temp < 0.20) {
            return SNOW_BLOCK;
        }
        if (surfaceH < seaLevel) {
            if (surfaceH < seaLevel - 16.0) return GRAVEL;
            return (humid > 0.60) ? CLAY : SAND;
        }
        if (surfaceH <= seaLevel + 2.0) {
            return (temp > 0.70 && humid < 0.30) ? RED_SAND : SAND;
        }
        if (slope > 0.55) {
            return STONE;
        }
        if (temp > 0.65 && humid < 0.25) {
            return (temp > 0.85) ? RED_SAND : SAND;
        }
        if (humid > 0.65 && temp < 0.45) {
            return PODZOL;
        }
        if (slope > 0.32) {
            return COARSE_DIRT;
        }
        return GRASS_BLOCK;
    }

    public BlockState resolveSubSurface(double depth, double temp, double humid) {
        if (temp > 0.65 && humid < 0.25) {
            return (depth > 2.0) ? SANDSTONE : SAND;
        }
        return DIRT;
    }

    public BlockState resolveCrustalRock(int worldX, int worldY, int worldZ, double surfaceH) {
        RockFamily family = lithologyField.evaluateLithology(worldX, worldY, worldZ, surfaceH);
        return switch (family) {
            case DEEP_DEEPSLATE -> DEEPSLATE;
            case IGNEOUS_GRANITE -> GRANITE;
            case IGNEOUS_DIORITE -> DIORITE;
            case IGNEOUS_ANDESITE -> ANDESITE;
            case SEDIMENTARY_LIMESTONE -> CALCITE;
            case METAMORPHIC_TUFF -> TUFF;
            case SEDIMENTARY_SANDSTONE -> SANDSTONE;
            case STANDARD_STONE -> STONE;
        };
    }
}
