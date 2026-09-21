package com.omms.geoengineforge.raster;

import com.omms.geoenginecore.material.LithologyField;
import com.omms.geoenginecore.material.RockFamily;
import com.omms.geoenginecore.math.GeoConfig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Strata material resolution from pipeline fields
 * (TECHSPEC §196-§199).
 *
 * <p>Surface cover follows a fixed geomorphic decision cascade:
 * altitude → submersion → shore → slope → aridity → moisture, with a
 * grass fallback (TECHSPEC §197). Sub-surface is dirt, sandy
 * (sandstone over sand) in arid columns (TECHSPEC §198). Crustal
 * rock comes from the deterministic lithology field (TECHSPEC
 * §196, §199).
 */
public final class StrataMaterialResolver {
    /** Temperate fallback cover. */
    private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    /** Default sub-surface soil. */
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    /** Wind-worked, stony soil. */
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    /** Cold, moist forest floor. */
    private static final BlockState PODZOL = Blocks.PODZOL.defaultBlockState();
    /** Dry/beach sediment. */
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    /** Hot-desert oxidized sediment. */
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    /** Deep channel bedload. */
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    /** High-altitude / cold cover. */
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    /** Moist shallow seabed. */
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();

    /** Exposed rock on steep slopes. */
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    /** Deep crust (TECHSPEC §199). */
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    /** Igneous granite family. */
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    /** Igneous diorite family. */
    private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
    /** Igneous andesite family. */
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    /** Sedimentary limestone family. */
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    /** Metamorphic tuff family. */
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    /** Sedimentary sandstone family. */
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();

    /** Deterministic lithology field (re-seeded with the world). */
    private final LithologyField lithologyField;
    /** Configured sea level. */
    private final int seaLevel;

    /**
     * @param worldSeed world seed (roots the lithology field)
     * @param config validated configuration
     */
    public StrataMaterialResolver(long worldSeed, GeoConfig config) {
        this.lithologyField = new LithologyField(worldSeed, config);
        this.seaLevel = config.seaLevel();
    }

    /**
     * Surface cover cascade (TECHSPEC §197).
     *
     * @param surfaceH final surface height
     * @param slope |∇H|
     * @param temp temperature field value
     * @param humid humidity field value
     * @return cover material state
     */
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

    /**
     * Sub-surface strata: sandy (sandstone over sand) in arid
     * columns, dirt otherwise (TECHSPEC §198).
     *
     * @param depth depth below the surface (positive)
     * @param temp temperature field value
     * @param humid humidity field value
     * @return sub-surface material state
     */
    public BlockState resolveSubSurface(double depth, double temp, double humid) {
        if (temp > 0.65 && humid < 0.25) {
            return (depth > 2.0) ? SANDSTONE : SAND;
        }
        return DIRT;
    }

    /**
     * Crustal rock from the deterministic lithology field
     * (TECHSPEC §196, §199).
     *
     * @param worldX world X
     * @param worldY world Y
     * @param worldZ world Z
     * @param surfaceH final surface height
     * @return crustal rock state
     */
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
