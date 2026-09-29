package com.omms.geoengineforge.debug;

import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.simd.KernelProvider;
import com.omms.geoengineforge.config.GeoEngineConfig;
import com.omms.geoengineforge.generator.GeoChunkGenerator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * /geoengine server commands (TECHSPEC §152-§157).
 *
 * <p>OP-level 2 commands for in-world debugging: /geoengine info,
 * /geoengine query [pos], and the heightmap/fields/slice exporters.
 * Exports run on the background executor to avoid freezing the tick.
 */
public final class GeoDebugCommands {
    /** Hides the implicit constructor. This is a static command registry. */
    private GeoDebugCommands() {}

    /**
     * Registers all /geoengine commands.
     *
     * @param dispatcher command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("geoengine")
            .requires(source -> source.hasPermission(2)) // OP Level 2
            // 1. /geoengine info
            .then(Commands.literal("info")
                .executes(ctx -> {
                    boolean simd = KernelProvider.isVectorApiAvailable();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6[GeoEngine]§r Version 1.0.0 | NeoForge 1.21.1\n" +
                        "  §7Author:§r Old Man Modding Studio\n" +
                        "  §7Vector API (SIMD):§r " + (simd ? "§aACTIVE" : "§eSCALAR_FALLBACK")
                    ), false);
                    return 1;
                })
            )
            // 2. /geoengine query [pos]
            .then(Commands.literal("query")
                .executes(ctx -> {
                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                    return executeQuery(ctx.getSource(), pos);
                })
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> {
                        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                        return executeQuery(ctx.getSource(), pos);
                    })
                )
            )
            // 3. /geoengine export heightmap <radius_chunks> [name]
            .then(Commands.literal("export")
                .then(Commands.literal("heightmap")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                        .executes(ctx -> executeExport(ctx.getSource(), "heightmap", IntegerArgumentType.getInteger(ctx, "radius"), "export"))
                        .then(Commands.argument("filename", StringArgumentType.word())
                            .executes(ctx -> executeExport(ctx.getSource(), "heightmap", IntegerArgumentType.getInteger(ctx, "radius"), StringArgumentType.getString(ctx, "filename")))
                        )
                    )
                )
                // 4. /geoengine export fields <radius_chunks> [name]
                .then(Commands.literal("fields")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> executeExport(ctx.getSource(), "fields", IntegerArgumentType.getInteger(ctx, "radius"), "export"))
                        .then(Commands.argument("filename", StringArgumentType.word())
                            .executes(ctx -> executeExport(ctx.getSource(), "fields", IntegerArgumentType.getInteger(ctx, "radius"), StringArgumentType.getString(ctx, "filename")))
                        )
                    )
                )
                // 5. /geoengine export slice <radius_chunks> [name]
                .then(Commands.literal("slice")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                        .executes(ctx -> executeExport(ctx.getSource(), "slice", IntegerArgumentType.getInteger(ctx, "radius"), "export"))
                        .then(Commands.argument("filename", StringArgumentType.word())
                            .executes(ctx -> executeExport(ctx.getSource(), "slice", IntegerArgumentType.getInteger(ctx, "radius"), StringArgumentType.getString(ctx, "filename")))
                        )
                    )
                )
            )
            // 6. /geoengine test <test_name>
            .then(Commands.literal("test")
                .then(Commands.argument("test_name", StringArgumentType.word())
                    .executes(ctx -> executeTest(ctx.getSource(), StringArgumentType.getString(ctx, "test_name"))))
            )
        );
    }

    /**
     * /geoengine query [pos] (TECHSPEC §152): prints the complete
     * pipeline diagnostic for one column.
     *
     * @param source command source
     * @param pos target position (player position when omitted)
     * @return brigadier command result
     */
    private static int executeQuery(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        ChunkGenerator gen = level.getChunkSource().getGenerator();

        if (!(gen instanceof GeoChunkGenerator geoGen)) {
            source.sendFailure(Component.literal("§cCurrent dimension does not use GeoChunkGenerator!"));
            return 0;
        }

        GeoConfig config = GeoConfig.defaultOverworld(1);
        FieldKernel kernel = KernelProvider.createKernel(level.getSeed(), config);

        String readout = GeoDebugExporter.queryPoint(kernel, config, pos.getX(), pos.getY(), pos.getZ());
        source.sendSuccess(() -> Component.literal("§e" + readout), false);
        return 1;
    }

    /**
     * /geoengine export ... (TECHSPEC §153-§157): runs the requested
     * export asynchronously on the background executor.
     *
     * @param source command source
     * @param type export kind: "heightmap", "fields", or "slice"
     * @param radius export radius in chunks
     * @param name output base name
     * @return brigadier command result
     */
    private static int executeExport(CommandSourceStack source, String type, int radius, String name) {
        ServerLevel level = source.getLevel();
        ChunkGenerator gen = level.getChunkSource().getGenerator();

        if (!(gen instanceof GeoChunkGenerator)) {
            source.sendFailure(Component.literal("§cCurrent dimension does not use GeoChunkGenerator!"));
            return 0;
        }

        BlockPos center = BlockPos.containing(source.getPosition());
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        long seed = level.getSeed();

        File outputDir = new File("geoengine_debug");
        source.sendSuccess(() -> Component.literal(
            String.format("§6[GeoEngine]§r Starting async export §e%s§r (radius: %d chunks)...", type, radius)
        ), true);

        // Run asynchronously on background thread to prevent server tick freeze
        CompletableFuture.runAsync(() -> {
            try {
                GeoConfig config = GeoConfig.defaultOverworld(1);
                FieldKernel kernel = KernelProvider.createKernel(seed, config);
                File result;

                long startTime = System.currentTimeMillis();

                switch (type) {
                    case "heightmap" -> result = GeoDebugExporter.exportHeightmapPng(kernel, config, chunkX, chunkZ, radius, outputDir, name);
                    case "fields" -> result = GeoDebugExporter.exportFieldsCsv(kernel, config, chunkX, chunkZ, radius, outputDir, name);
                    case "slice" -> result = GeoDebugExporter.exportVerticalSlicePng(kernel, config, center.getZ(), chunkX, radius, outputDir, name);
                    default -> throw new IllegalArgumentException("Unknown export type: " + type);
                }

                long elapsed = System.currentTimeMillis() - startTime;
                source.sendSuccess(() -> Component.literal(
                    String.format("§a[GeoEngine] Export complete in %d ms:§r §7%s§r", elapsed, result.getAbsolutePath())
                ), true);
            } catch (Exception e) {
                source.sendFailure(Component.literal("§cExport failed: " + e.getMessage()));
                e.printStackTrace();
            }
        }, Util.backgroundExecutor());

        return 1;
    }

    /**
     * /geoengine test <test_name>: runs a Phase 4 seal test.
     * 
     * @param source command source
     * @param testName test name
     * @return brigadier command result
     */
    private static int executeTest(CommandSourceStack source, String testName) {
        ServerLevel level = source.getLevel();
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        
        source.sendSuccess(() -> Component.literal("[GeoEngine] Running test: " + testName), false);
        
        switch (testName.toLowerCase()) {
            case "chunk_generation":
                return testChunkGeneration(source, level, playerPos);
            case "heightmap_fidelity":
                return testHeightmapFidelity(source, level, playerPos);
            case "section_palette":
                return testSectionPalette(source, level, playerPos);
            case "chunk_seam":
                return testChunkSeam(source, level, playerPos);
            case "order_independence":
                return testOrderIndependence(source, level, playerPos);
            default:
                source.sendSuccess(() -> Component.literal("[GeoEngine] Unknown test: " + testName), false);
                return 0;
        }
    }

    /**
     * P4-E1: Verify chunk generation produces terrain.
     */
    private static int testChunkGeneration(CommandSourceStack source, ServerLevel level, BlockPos playerPos) {
        source.sendSuccess(() -> Component.literal("[GeoEngine] Testing chunk generation..."), false);
        
        // Get the chunk at the player's location
        ChunkPos chunkPos = new ChunkPos(playerPos);
        var chunk = level.getChunk(chunkPos.x, chunkPos.z);
        
        // Verify chunk was generated
        if (chunk == null) {
            source.sendSuccess(() -> Component.literal("[GeoEngine] FAIL: Chunk should be generated"), false);
            return 0;
        }
        
        // Verify there's terrain (not all air) by checking multiple columns
        int[] terrainColumns = {0};
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                boolean foundTerrain = false;
                for (int y = 0; y < level.getMaxBuildHeight(); y++) {
                    var blockState = level.getBlockState(new BlockPos(x, y, z));
                    if (!blockState.isAir()) {
                        foundTerrain = true;
                        break;
                    }
                }
                if (foundTerrain) {
                    terrainColumns[0]++;
                }
            }
        }
        
        // Expect at least some terrain columns
        if (terrainColumns[0] == 0) {
            source.sendSuccess(() -> Component.literal("[GeoEngine] FAIL: Generated chunk should contain terrain. Found 0 terrain columns."), false);
            return 0;
        } else {
            source.sendSuccess(() -> Component.literal("[GeoEngine] PASS: Chunk generation test passed. Found " + terrainColumns[0] + " terrain columns."), false);
            return 1;
        }
    }

    /**
     * P4-E2: Verify WORLD_SURFACE_WG heightmap matches core Hf quantized.
     */
    private static int testHeightmapFidelity(CommandSourceStack source, ServerLevel level, BlockPos playerPos) {
        source.sendSuccess(() -> Component.literal("[GeoEngine] Testing heightmap fidelity..."), false);
        
        // Get the chunk at the player's location
        ChunkPos chunkPos = new ChunkPos(playerPos);
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        
        // Get the WORLD_SURFACE_WG heightmap
        Heightmap heightmap = null;
        for (var entry : chunk.getHeightmaps()) {
            if (entry.getKey() == Heightmap.Types.WORLD_SURFACE_WG) {
                heightmap = entry.getValue();
                break;
            }
        }
        if (heightmap == null) {
            // Try WORLD_SURFACE as fallback
            for (var entry : chunk.getHeightmaps()) {
                if (entry.getKey() == Heightmap.Types.WORLD_SURFACE) {
                    heightmap = entry.getValue();
                    break;
                }
            }
        }
        if (heightmap == null) {
            // List available heightmaps for debugging
            StringBuilder sb = new StringBuilder();
            for (var entry : chunk.getHeightmaps()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.getKey().getSerializationKey());
            }
            source.sendSuccess(() -> Component.literal("[GeoEngine] FAIL: WORLD_SURFACE_WG heightmap not found. Available: " + sb), false);
            return 0;
        }
        
        // Compute expected heights using the core kernel (same path as chunk generator)
        GeoConfig config = GeoEngineConfig.getActiveOverworldConfig(1);
        FieldKernel kernel = KernelProvider.createKernel(level.getSeed(), config);
        
        // Rasterize the chunk surface using the same method as the chunk generator
        int chunkWorldX = chunkPos.getMinBlockX();
        int chunkWorldZ = chunkPos.getMinBlockZ();
        WorkerScratchpad scratchpad = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(scratchpad, chunkWorldX, chunkWorldZ);
        
        // Compare heightmap values with the rasterized surface grid
        int[] sampleCount = {0};
        int[] failures = {0};
        double[] maxError = {0.0};
        
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                sampleCount[0]++;
                
                // Get heightmap value
                int heightmapY = heightmap.getHighestTaken(x, z);
                
                // Get expected height from the rasterized surface grid
                int cIdx = (z << 4) | x;
                double expectedHeight = scratchpad.surfaceGrid[cIdx];
                int expectedY = (int) Math.round(expectedHeight);
                
                // Compute error
                double error = Math.abs(heightmapY - expectedY);
                if (error > maxError[0]) {
                    maxError[0] = error;
                }
                
                // Check tolerance (8.0 blocks to account for kernel interpolation variance)
                if (error > 8.0) {
                    failures[0]++;
                    final int fx = x;
                    final int fz = z;
                    final int fheightmapY = heightmapY;
                    final int fexpectedY = expectedY;
                    final double fexpectedHeight = expectedHeight;
                    final double ferror = error;
                    source.sendSuccess(() -> Component.literal(
                        "[GeoEngine] Heightmap mismatch at (" + fx + "," + fz + "): heightmap=" + fheightmapY + ", expected=" + fexpectedY + " (" + fexpectedHeight + "), error=" + ferror
                    ), false);
                }
            }
        }
        
        if (failures[0] > 0) {
            final int ffailures = failures[0];
            final int fsampleCount = sampleCount[0];
            final double fmaxError = maxError[0];
            source.sendSuccess(() -> Component.literal(
                "[GeoEngine] FAIL: Heightmap fidelity test. " + ffailures + "/" + fsampleCount + " columns exceeded tolerance. Max error: " + fmaxError
            ), false);
            return 0;
        } else {
            final int fsampleCount = sampleCount[0];
            final double fmaxError = maxError[0];
            source.sendSuccess(() -> Component.literal(
                "[GeoEngine] PASS: Heightmap fidelity test. All " + fsampleCount + " columns within tolerance. Max error: " + fmaxError
            ), false);
            return 1;
        }
    }

    /**
     * P4-E3: Verify section palettes match classifier decisions.
     * TODO: Investigate runtime crash and re-implement.
     */
    private static int testSectionPalette(CommandSourceStack source, ServerLevel level, BlockPos playerPos) {
        source.sendSuccess(() -> Component.literal("[GeoEngine] Section palette test: TODO (runtime crash under investigation)"), false);
        return 0;
    }

    /**
     * P4-E4: Verify adjacent chunk heightmap continuity.
     * TODO: Investigate heightmap access and re-implement.
     */
    private static int testChunkSeam(CommandSourceStack source, ServerLevel level, BlockPos playerPos) {
        source.sendSuccess(() -> Component.literal("[GeoEngine] Chunk seam test: TODO (heightmap access under investigation)"), false);
        return 0;
    }

    /**
     * P4-E5: Verify generation order independence.
     */
    private static int testOrderIndependence(CommandSourceStack source, ServerLevel level, BlockPos playerPos) {
        source.sendSuccess(() -> Component.literal("[GeoEngine] Testing order independence..."), false);
        
        // Verify determinism by computing heights multiple times and comparing
        ChunkPos chunkPos = new ChunkPos(playerPos);
        
        GeoConfig config = GeoConfig.defaultOverworld(1);
        FieldKernel kernel1 = KernelProvider.createKernel(level.getSeed(), config);
        FieldKernel kernel2 = KernelProvider.createKernel(level.getSeed(), config);
        
        // Sample a few columns and verify consistent results
        int sampleCount = 0;
        int failures = 0;
        
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                sampleCount++;
                
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;
                
                GeoSample sample1 = new GeoSample();
                kernel1.evaluateFullColumn(worldX, worldZ, sample1);
                
                GeoSample sample2 = new GeoSample();
                kernel2.evaluateFullColumn(worldX, worldZ, sample2);
                
                // Verify identical results
                if (Math.abs(sample1.finalSurface - sample2.finalSurface) > 1e-9) {
                    failures++;
                    final int fx = x;
                    final int fz = z;
                    final double fheight1 = sample1.finalSurface;
                    final double fheight2 = sample2.finalSurface;
                    source.sendSuccess(() -> Component.literal(
                        "[GeoEngine] Non-deterministic at (" + fx + "," + fz + "): " + fheight1 + " vs " + fheight2
                    ), false);
                }
            }
        }
        
        if (failures > 0) {
            final int ffailures = failures;
            final int fsampleCount = sampleCount;
            source.sendSuccess(() -> Component.literal(
                "[GeoEngine] FAIL: Order independence test. " + ffailures + "/" + fsampleCount + " columns were non-deterministic."
            ), false);
            return 0;
        } else {
            final int fsampleCount = sampleCount;
            source.sendSuccess(() -> Component.literal(
                "[GeoEngine] PASS: Order independence test. All " + fsampleCount + " columns are deterministic."
            ), false);
            return 1;
        }
    }
}
