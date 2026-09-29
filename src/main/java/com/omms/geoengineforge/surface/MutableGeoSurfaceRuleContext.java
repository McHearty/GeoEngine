package com.omms.geoengineforge.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/**
 * Reused {@link GeoSurfaceRuleContext} implementation
 * (TECHSPEC §17): plain fields rewritten per voxel so the rule
 * evaluator allocates nothing per column.
 */
public final class MutableGeoSurfaceRuleContext implements GeoSurfaceRuleContext {
    /** Reused voxel position. */
    public final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Biome at the current position. */
    public Holder<Biome> currentBiome;
    /** Current absolute block Y. */
    public int currentBlockY;
    /** True solid surface top of the column. */
    public int currentSurfaceHeight;
    /** Sea level. */
    public int currentWaterHeight;
    /** Consecutive solid depth below the current voxel. */
    public int currentStoneDepthBelow;
    /** Consecutive solid depth above the current voxel (ceiling). */
    public int currentStoneDepthAbove;
    /** Column incision depth. */
    public int currentSurfaceDepth;
    /** Steepness gate verdict. */
    public boolean currentIsSteep;
    /** Hole flag (top below sea level). */
    public boolean currentIsHole;
    /** At/above pre-carve H₀ flag. */
    public boolean currentIsAbovePreliminarySurface;
    /** Temperature field value. */
    public double currentTemperature;
    /** World generation context (sea-level anchors). */
    public WorldGenerationContext currentGenContext;
    // Phase 9: channel and water fields
    /** Water surface level (0 = none). */
    public int currentWaterSurfaceLevel;
    /** Channel order (0-4). */
    public byte currentChannelOrder;
    /** Feature mask bitfield. */
    public int currentFeatureMask;
    /** Distance to thalweg. */
    public double currentDistanceToThalweg;
    /** Channel half-width. */
    public double currentChannelHalfWidth;

    /** @return reused voxel position. */
    @Override public BlockPos position() { return pos; }
    /** @return current biome holder. */
    @Override public Holder<Biome> biome() { return currentBiome; }
    /** @return current absolute block Y. */
    @Override public int blockY() { return currentBlockY; }
    /** @return true solid surface top of the column. */
    @Override public int surfaceHeight() { return currentSurfaceHeight; }
    /** @return sea level. */
    @Override public int waterHeight() { return currentWaterHeight; }
    /** @return consecutive solid depth below the current voxel. */
    @Override public int stoneDepthBelow() { return currentStoneDepthBelow; }
    /** @return consecutive solid depth above the current voxel (ceiling). */
    @Override public int stoneDepthAbove() { return currentStoneDepthAbove; }
    /** @return column incision depth. */
    @Override public int surfaceDepth() { return currentSurfaceDepth; }
    /** @return steepness gate verdict. */
    @Override public boolean isSteep() { return currentIsSteep; }
    /** @return hole flag (top below sea level). */
    @Override public boolean isHole() { return currentIsHole; }
    /** @return at/above pre-carve H₀ flag. */
    @Override public boolean isAbovePreliminarySurface() { return currentIsAbovePreliminarySurface; }
    /** @return temperature field value. */
    @Override public double temperature() { return currentTemperature; }
    /** @return world generation context. */
    @Override public WorldGenerationContext generationContext() { return currentGenContext; }

    // Phase 9: channel and water fields
    @Override public int waterSurfaceLevel() { return currentWaterSurfaceLevel; }
    @Override public byte channelOrder() { return currentChannelOrder; }
    @Override public int featureMask() { return currentFeatureMask; }
    @Override public double distanceToThalweg() { return currentDistanceToThalweg; }
    @Override public double channelHalfWidth() { return currentChannelHalfWidth; }
}
