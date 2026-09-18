package com.omms.geoengineforge.debug;

import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.simd.KernelProvider;
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

import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class GeoDebugCommands {
    private GeoDebugCommands() {}

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
        );
    }

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
}
