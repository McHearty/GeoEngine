package com.omms.geoengineforge.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public interface GeoSurfaceRuleContext {
    BlockPos position();
    Holder<Biome> biome();
    int blockY();
    int surfaceHeight();
    int waterHeight();
    int stoneDepthBelow();
    int stoneDepthAbove();
    int surfaceDepth();
    boolean isSteep();
    boolean isHole();
    boolean isAbovePreliminarySurface();
    double temperature();
    WorldGenerationContext generationContext();
}
