package com.omms.geoengineforge.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public final class MutableGeoSurfaceRuleContext implements GeoSurfaceRuleContext {
    public final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    public Holder<Biome> currentBiome;
    public int currentBlockY;
    public int currentSurfaceHeight;
    public int currentWaterHeight;
    public int currentStoneDepthBelow;
    public int currentStoneDepthAbove;
    public int currentSurfaceDepth;
    public boolean currentIsSteep;
    public boolean currentIsHole;
    public boolean currentIsAbovePreliminarySurface;
    public double currentTemperature;
    public WorldGenerationContext currentGenContext;

    @Override public BlockPos position() { return pos; }
    @Override public Holder<Biome> biome() { return currentBiome; }
    @Override public int blockY() { return currentBlockY; }
    @Override public int surfaceHeight() { return currentSurfaceHeight; }
    @Override public int waterHeight() { return currentWaterHeight; }
    @Override public int stoneDepthBelow() { return currentStoneDepthBelow; }
    @Override public int stoneDepthAbove() { return currentStoneDepthAbove; }
    @Override public int surfaceDepth() { return currentSurfaceDepth; }
    @Override public boolean isSteep() { return currentIsSteep; }
    @Override public boolean isHole() { return currentIsHole; }
    @Override public boolean isAbovePreliminarySurface() { return currentIsAbovePreliminarySurface; }
    @Override public double temperature() { return currentTemperature; }
    @Override public WorldGenerationContext generationContext() { return currentGenContext; }
}
