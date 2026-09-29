package com.omms.geoengineforge.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/**
 * Rule evaluation context fed by the chunk raster
 * (TECHSPEC §11, §12, §13).
 *
 * <p>GeoEngine-specific axes: {@link #isSteep()} from the derivative
 * sampler (TECHSPEC §12); {@link #isHole()} from the cave field
 * (TECHSPEC §13); {@link #isAbovePreliminarySurface()} from the
 * pre-carve H₀ (TECHSPEC §11); {@link #temperature()} from the
 * climate field.
 */
public interface GeoSurfaceRuleContext {
    /** @return current voxel position. */
    BlockPos position();
    /** @return biome at the current position. */
    Holder<Biome> biome();
    /** @return absolute block Y. */
    int blockY();
    /** @return true solid surface top of the column. */
    int surfaceHeight();
    /** @return sea level. */
    int waterHeight();
    /** @return consecutive solid depth below the current voxel. */
    int stoneDepthBelow();
    /** @return consecutive solid depth above the current voxel (ceiling). */
    int stoneDepthAbove();
    /** @return incision depth of the column. */
    int surfaceDepth();
    /** @return true when |∇H| exceeds the steepness gate. */
    boolean isSteep();
    /** @return true when the column top sits below sea level. */
    boolean isHole();
    /** @return true when the voxel is at/above the pre-carve H₀. */
    boolean isAbovePreliminarySurface();
    /** @return temperature field value. */
    double temperature();
    /** @return world generation context (sea-level anchors). */
    WorldGenerationContext generationContext();

    // Phase 9: channel and water fields
    /** @return water surface level (0 = none). */
    int waterSurfaceLevel();
    /** @return channel order (0-4). */
    byte channelOrder();
    /** @return feature mask bitfield. */
    int featureMask();
    /** @return distance to thalweg. */
    double distanceToThalweg();
    /** @return channel half-width. */
    double channelHalfWidth();
}
