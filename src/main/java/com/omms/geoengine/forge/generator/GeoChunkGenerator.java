package com.geoengine.forge.generator;

import com.geoengine.core.math.FieldKernel;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.memory.ScratchpadProvider;
import com.geoengine.core.memory.WorkerScratchpad;
import com.geoengine.core.raster.SectionClassification;
import com.geoengine.core.raster.SectionClassifier;
import com.geoengine.forge.raster.MaterialResolver;
import com.geoengine.forge.raster.StrataMaterialResolver;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class GeoChunkGenerator extends ChunkGenerator {
    public static final MapCodec<GeoChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource)
        ).apply(instance, GeoChunkGenerator::new)
    );

    private final FieldKernel kernel;
    private final GeoConfig config;
    private final SectionClassifier sectionClassifier;
    private final MaterialResolver materialResolver;

    public GeoChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
        this.config = GeoConfig.defaultOverworld(1);
        this.kernel = new com.geoengine.core.math.ScalarFieldKernel(0x5EEDL, this.config);
        this.sectionClassifier = new SectionClassifier(this.config);
        this.materialResolver = new MaterialResolver(this.config.seaLevel());
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
        Executor executor, Blender blender, RandomState randomState, 
        StructureManager structureManager, ChunkAccess chunk
    ) {
        return CompletableFuture.supplyAsync(() -> {
            rasterizeChunk(chunk);
            return chunk;
        }, executor);
    }

    private void rasterizeChunk(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int chunkWorldX = pos.getMinBlockX();
        int chunkWorldZ = pos.getMinBlockZ();

        WorkerScratchpad scratchpad = ScratchpadProvider.get();

        kernel.rasterizeSurfaceChunk(scratchpad, chunkWorldX, chunkWorldZ);

        Heightmap worldSurfaceWg = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloorWg = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int surfH = (int) Math.round(scratchpad.surfaceGrid[(z << 4) | x]);
                worldSurfaceWg.update(x, Math.max(surfH, config.seaLevel()), z, materialResolver.resolveSolid(surfH));
                oceanFloorWg.update(x, surfH, z, materialResolver.resolveSolid(surfH));
            }
        }

        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();

        for (int sIdx = 0; sIdx < sections.length; sIdx++) {
            LevelChunkSection section = sections[sIdx];
            int sectionBlockY = (minSectionY + sIdx) << 4;

            SectionClassification classification = sectionClassifier.classifySection(scratchpad, sectionBlockY);

            if (classification == SectionClassification.AIR) {
                if (sectionBlockY + 16 <= config.seaLevel()) {
                    fillBulk(section, materialResolver.resolveFluidOrAir(sectionBlockY));
                }
                continue;
            }

            if (classification == SectionClassification.SOLID) {
                fillBulk(section, materialResolver.resolveSolid(sectionBlockY));
                continue;
            }

            section.acquire();
            try {
                for (int ly = 0; ly < 16; ly++) {
                    int wy = sectionBlockY + ly;
                    for (int lz = 0; lz < 16; lz++) {
                        int wz = chunkWorldZ + lz;
                        for (int lx = 0; lx < 16; lx++) {
                            int wx = chunkWorldX + lx;

                            float d = kernel.evaluateDensity(scratchpad, wx, wy, wz);
                            BlockState state = materialResolver.resolve(wy, d);
                            section.setBlockState(lx, ly, lz, state, false);
                        }
                    }
                }
            } finally {
                section.release();
            }
        }
    }

    private static void fillBulk(LevelChunkSection section, BlockState state) {
        section.acquire();
        try {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        section.setBlockState(x, y, z, state, false);
                    }
                }
            }
        } finally {
            section.release();
        }
    }

    @Override
    public void buildSurface(
        WorldGenRegion level, StructureManager structureManager, 
        RandomState randomState, ChunkAccess chunk
    ) {
        ChunkPos pos = chunk.getPos();
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();

        WorkerScratchpad sp = ScratchpadProvider.get();
        StrataMaterialResolver strataResolver = new StrataMaterialResolver(0x5EEDL, config);
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            int wz = originZ + lz;
            for (int lx = 0; lx < 16; lx++) {
                int wx = originX + lx;
                int cIdx = (lz << 4) | lx;

                double surfaceH = sp.surfaceGrid[cIdx];
                double gx = sp.gradXGrid[cIdx];
                double gz = sp.gradZGrid[cIdx];
                double slope = Math.sqrt(gx * gx + gz * gz);

                double temp = sp.sample.temperature;
                double humid = sp.sample.humidity;

                int topSolidY = (int) Math.round(surfaceH);
                int minY = config.worldMinY();

                for (int y = topSolidY; y >= minY; y--) {
                    mutPos.set(wx, y, wz);
                    BlockState current = chunk.getBlockState(mutPos);

                    if (current.isAir() || current.is(Blocks.WATER)) {
                        continue;
                    }

                    double depth = surfaceH - y;

                    if (depth < 1.0) {
                        BlockState cover = strataResolver.resolveSurfaceCover(surfaceH, slope, temp, humid);
                        chunk.setBlockState(mutPos, cover, false);
                    } else if (depth <= 4.0 && slope <= 0.55) {
                        BlockState sub = strataResolver.resolveSubSurface(depth, temp, humid);
                        chunk.setBlockState(mutPos, sub, false);
                    } else {
                        BlockState rock = strataResolver.resolveCrustalRock(wx, y, wz, surfaceH);
                        chunk.setBlockState(mutPos, rock, false);
                    }
                }
            }
        }
    }

    @Override
    public void applyCarvers(
        WorldGenRegion level, long seed, RandomState randomState, 
        BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, 
        GenerationStep.Carving step
    ) {}

    @Override
    public void applyBiomeDecoration(
        net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, 
        StructureManager structureManager
    ) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {}

    @Override
    public int getGenDepth() {
        return config.worldMaxY() - config.worldMinY();
    }

    @Override
    public int getSeaLevel() {
        return config.seaLevel();
    }

    @Override
    public int getMinY() {
        return config.worldMinY();
    }

    @Override
    public int getBaseHeight(
        int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState
    ) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        return (int) Math.round(kernel.evaluateH0(x, z, sp.sample));
    }

    @Override
    public net.minecraft.world.level.NoiseColumn getBaseColumn(
        int x, int z, LevelHeightAccessor level, RandomState randomState
    ) {
        int height = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        BlockState[] states = new BlockState[Math.max(0, height - config.worldMinY())];
        for (int y = config.worldMinY(); y < height; y++) {
            states[y - config.worldMinY()] = materialResolver.resolveSolid(y);
        }
        return new net.minecraft.world.level.NoiseColumn(config.worldMinY(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("GeoEngine 1.21.1: Bounded Geomorphic Scalar Solver");
    }
}
