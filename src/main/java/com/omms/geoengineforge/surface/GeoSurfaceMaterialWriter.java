package com.omms.geoengineforge.surface;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public final class GeoSurfaceMaterialWriter {
    private final GeoSurfaceRuleEvaluator evaluator = new GeoSurfaceRuleEvaluator();
    private final MutableGeoSurfaceRuleContext context = new MutableGeoSurfaceRuleContext();

    public void apply(
        WorldGenRegion level, ChunkAccess chunk, 
        GeoConfig config, WorkerScratchpad scratchpad,
        SurfaceRules.RuleSource surfaceRule,
        WorldGenerationContext genContext
    ) {
        ChunkPos chunkPos = chunk.getPos();
        int originX = chunkPos.getMinBlockX();
        int originZ = chunkPos.getMinBlockZ();

        context.currentGenContext = genContext;
        context.currentWaterHeight = config.seaLevel();

        int maxBuildY = chunk.getMaxBuildHeight() - 1;
        int minBuildY = chunk.getMinBuildHeight();

        for (int lz = 0; lz < 16; lz++) {
            int wz = originZ + lz;
            for (int lx = 0; lx < 16; lx++) {
                int wx = originX + lx;
                int cIdx = (lz << 4) | lx;

                double surfaceH = scratchpad.surfaceGrid[cIdx];
                int estTopY = (int) Math.round(surfaceH);
                double h0 = scratchpad.h0Grid[cIdx]; // Correct per-column preliminary height

                // Find the true solid surface top of the column
                int trueTopY = Integer.MIN_VALUE;
                int scanStart = Math.min(maxBuildY, estTopY + 4);
                for (int y = scanStart; y >= minBuildY; y--) {
                    context.pos.set(wx, y, wz);
                    BlockState s = chunk.getBlockState(context.pos);
                    if (!s.isAir() && !s.is(Blocks.WATER) && !s.is(Blocks.LAVA)) {
                        trueTopY = y;
                        break;
                    }
                }

                // If column has no solid blocks, nothing to dress
                if (trueTopY == Integer.MIN_VALUE) {
                    continue;
                }

                double slope = Math.sqrt(
                    (scratchpad.gradXGrid[cIdx] * scratchpad.gradXGrid[cIdx]) + 
                    (scratchpad.gradZGrid[cIdx] * scratchpad.gradZGrid[cIdx])
                );
                boolean isSteep = slope > 0.45;
                double temp = scratchpad.tempGrid[cIdx];

                context.currentSurfaceHeight = trueTopY;
                context.currentIsSteep = isSteep;
                context.currentTemperature = temp;
                context.currentSurfaceDepth = (int) Math.clamp(scratchpad.erosionGrid[cIdx] * 0.1, 0, 3);
                context.currentIsHole = trueTopY < config.seaLevel();

                int stoneDepthBelow = 0;

                // Traverse downward from trueTopY: NEVER places blocks in air
                for (int y = trueTopY; y >= minBuildY; y--) {
                    context.pos.set(wx, y, wz);
                    BlockState current = chunk.getBlockState(context.pos);

                    if (current.isAir() || current.is(Blocks.WATER) || current.is(Blocks.LAVA)) {
                        stoneDepthBelow = 0; // Reset depth when traversing into caves
                        continue;
                    }

                    context.currentBlockY = y;
                    context.currentStoneDepthBelow = stoneDepthBelow;
                    context.currentStoneDepthAbove = Math.max(0, trueTopY - y);
                    context.currentIsAbovePreliminarySurface = y >= (int) Math.round(h0);

                    Holder<Biome> biome = chunk.getNoiseBiome(wx >> 2, y >> 2, wz >> 2);
                    context.currentBiome = biome;

                    BlockState replacement = evaluator.evaluate(surfaceRule, context);
                    if (replacement != null) {
                        chunk.setBlockState(context.pos, replacement, false);
                    }

                    stoneDepthBelow++;
                }
            }
        }
    }
}
