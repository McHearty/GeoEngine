package com.omms.geoengineforge.generator;

import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.feature.SpecialFeatureDetector;
import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.raster.SectionClassifier;
import com.omms.geoenginecore.simd.KernelProvider;
import com.omms.geoengineforge.feature.SpecialFeatureGenerator;
import com.omms.geoengineforge.integration.GeoDimensionProfile;
import com.omms.geoengineforge.raster.ChunkRasterizer;
import com.omms.geoengineforge.raster.HeightmapWriter;
import com.omms.geoengineforge.raster.MaterialResolver;
import com.omms.geoengineforge.surface.GeoSurfaceMaterialWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * GeoEngine chunk generator: the full raster pipeline
 * (TECHSPEC §210-§214).
 *
 * <p>fillFromNoise runs the 2-D surface raster plus the section
 * pipeline (SOLID/AIR/BAND) and heightmaps. buildSurface applies
 * data-driven SurfaceRules with the freshly rasterized scratchpad —
 * no vanilla NoiseChunk, no 2-D/3-D mismatch — then materializes
 * gated special features in the Overworld. Carvers are disabled; the
 * world is fully field-driven (TECHSPEC §210).
 */
public class GeoChunkGenerator extends ChunkGenerator {
    /** Serialization codec: (biome source, noise settings, seed, dimension id). */
    public static final MapCodec<GeoChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter((GeoChunkGenerator gen) -> gen.biomeSource),
            NoiseGeneratorSettings.CODEC.optionalFieldOf("settings").forGetter((GeoChunkGenerator gen) -> Optional.of(gen.settings)),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter((GeoChunkGenerator gen) -> gen.worldSeed),
            Codec.INT.optionalFieldOf("dimension_id", 0).forGetter((GeoChunkGenerator gen) -> gen.dimensionId),
            RegistryOps.<NoiseGeneratorSettings, GeoChunkGenerator>retrieveGetter(Registries.NOISE_SETTINGS)
        ).apply(instance, (biomeSource, optSettings, seed, dimId, settingsGetter) -> {
            Holder<NoiseGeneratorSettings> resolvedSettings = optSettings.orElseGet(() -> {
                if (dimId == 1) return settingsGetter.getOrThrow(NoiseGeneratorSettings.NETHER);
                if (dimId == 2) return settingsGetter.getOrThrow(NoiseGeneratorSettings.END);
                return settingsGetter.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            });
            return new GeoChunkGenerator(biomeSource, resolvedSettings, seed, dimId);
        })
    );

    /** World seed (reseedable). */
    private long worldSeed;
    /** Dimension id: 0 overworld, 1 nether, 2 end. */
    private final int dimensionId;
    /** Resolved noise generator settings (surface rules, materials). */
    private final Holder<NoiseGeneratorSettings> settings;
    /** Validated configuration. */
    private final GeoConfig config;
    /** Validated dimension profile. */
    private final DimensionProfile profile;
    /** Best available field kernel (SIMD or Scalar). */
    private FieldKernel kernel;
    /** Coarse section classifier. */
    private SectionClassifier sectionClassifier;
    /** Strata material resolver. */
    private MaterialResolver materialResolver;
    /** Section rasterizer. */
    private ChunkRasterizer chunkRasterizer;
    /** Surface rule writer. */
    private final GeoSurfaceMaterialWriter surfaceMaterialWriter = new GeoSurfaceMaterialWriter();

    /**
     * @param biomeSource biome source
     * @param settings resolved noise generator settings
     * @param worldSeed world seed
     * @param dimensionId dimension id: 0 overworld, 1 nether, 2 end
     */
    public GeoChunkGenerator(
        BiomeSource biomeSource, 
        Holder<NoiseGeneratorSettings> settings, 
        long worldSeed, 
        int dimensionId
    ) {
        super(biomeSource);
        this.worldSeed = worldSeed;
        this.dimensionId = dimensionId;
        this.settings = settings;

        this.profile = GeoDimensionProfile.getProfileFor(dimensionId, 1);
        this.config = this.profile.getConfig();

        NoiseGeneratorSettings noiseSettings = settings.value();
        this.materialResolver = new MaterialResolver(noiseSettings.defaultBlock(), noiseSettings.defaultFluid(), noiseSettings.seaLevel());
        reseed(worldSeed);
    }

    /**
     * Rebuilds all seed-bound components after a world seed change
     * (TECHSPEC §67).
     *
     * @param seed new world seed
     */
    public synchronized void reseed(long seed) {
        this.worldSeed = seed;
        this.kernel = KernelProvider.createKernel(seed, this.profile);
        this.sectionClassifier = new SectionClassifier(this.config, this.kernel.getCaveField());
        this.chunkRasterizer = new ChunkRasterizer(this.kernel, this.sectionClassifier, this.materialResolver);

        if (this.biomeSource instanceof GeoBiomeSource geoBiomeSource) {
            geoBiomeSource.reseed(seed);
        }
    }

    /**
     * Reseeds with the persisted state's seed before super.
     *
     * @param structureSetLookup structure set lookup
     * @param randomState random state
     * @param seed persisted world seed
     * @return generator state
     */
    @Override
    public ChunkGeneratorStructureState createState(
        HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed
    ) {
        reseed(seed);
        return super.createState(structureSetLookup, randomState, seed);
    }

    /**
     * @return this generator's codec
     */
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * Rasterizes the chunk from fields (TECHSPEC §210-§212).
     *
     * @param blender blending context
     * @param randomState random state
     * @param structureManager structure manager
     * @param chunk chunk to fill
     * @return completed future with the rasterized chunk
     */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
        Blender blender, RandomState randomState, 
        StructureManager structureManager, ChunkAccess chunk
    ) {
        rasterizeChunk(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Runs the 2-D surface raster, heightmaps, and section
     * classification for one chunk (TECHSPEC §210-§212).
     *
     * @param chunk chunk to fill
     */
    private void rasterizeChunk(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int chunkWorldX = pos.getMinBlockX();
        int chunkWorldZ = pos.getMinBlockZ();

        WorkerScratchpad scratchpad = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(scratchpad, chunkWorldX, chunkWorldZ);

        HeightmapWriter.populate(
            chunk, scratchpad.surfaceGrid, config.seaLevel(), materialResolver.resolveSolid(config.seaLevel())
        );

        chunkRasterizer.rasterizeSections(
            chunk.getSections(), chunk.getMinSection(), scratchpad,
            chunkWorldX, chunkWorldZ, config.seaLevel()
        );
    }

    /**
     * Applies SurfaceRules and gated special features
     * (TECHSPEC §213-§214).
     *
     * @param level generation region
     * @param structureManager structure manager
     * @param randomState random state
     * @param chunk chunk to decorate
     */
    @Override
    public void buildSurface(
        WorldGenRegion level, StructureManager structureManager, 
        RandomState randomState, ChunkAccess chunk
    ) {
        if (this.worldSeed == 0L && level.getSeed() != 0L) {
            reseed(level.getSeed());
        }

        if (SharedConstants.debugVoidTerrain(chunk.getPos())) {
            return;
        }

        ChunkPos pos = chunk.getPos();
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();

        WorkerScratchpad scratchpad = ScratchpadProvider.get();
        // Crucial fix: Freshly rasterize scratchpad for this chunk on this worker thread (§17, §18)
        kernel.rasterizeSurfaceChunk(scratchpad, originX, originZ);

        WorldGenerationContext genContext = new WorldGenerationContext(this, level);

        // Apply data-driven SurfaceRules without NoiseChunk
        surfaceMaterialWriter.apply(
            level, chunk, config, scratchpad, 
            this.settings.value().surfaceRule(), genContext
        );

        // Strictly gated special features in the Overworld
        if (dimensionId == 0) {
            materializeSpecialFeatures(chunk, scratchpad, originX, originZ);
        }
    }

    /**
     * Detects and materializes special features in the interior
     * 14×14 of the chunk (TECHSPEC §200-§204).
     *
     * @param chunk chunk being generated
     * @param sp worker scratchpad holding the fresh chunk raster
     * @param originX world X of the chunk origin
     * @param originZ world Z of the chunk origin
     */
    private void materializeSpecialFeatures(ChunkAccess chunk, WorkerScratchpad sp, int originX, int originZ) {
        SpecialFeatureDetector featureDetector = new SpecialFeatureDetector(config);
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (int lz = 1; lz < 15; lz++) {
            int wz = originZ + lz;
            for (int lx = 1; lx < 15; lx++) {
                int wx = originX + lx;
                int cIdx = (lz << 4) | lx;

                sp.populateSampleFromColumn(lx, lz, wx, wz);

                // Compute real downstream drop to steepest cardinal neighbor
                double hC = sp.surfaceGrid[cIdx];
                double hN = sp.surfaceGrid[((lz - 1) << 4) | lx];
                double hS = sp.surfaceGrid[((lz + 1) << 4) | lx];
                double hW = sp.surfaceGrid[(lz << 4) | (lx - 1)];
                double hE = sp.surfaceGrid[(lz << 4) | (lx + 1)];

                double minNeighbor = Math.min(Math.min(hN, hS), Math.min(hW, hE));
                double actualDrop = Math.max(0.0, hC - minNeighbor);

                SpecialFeatureDetector.FeatureType feature = featureDetector.detectFeature(sp.sample, actualDrop);
                if (feature != SpecialFeatureDetector.FeatureType.NONE) {
                    SpecialFeatureGenerator.materializeFeature(chunk, mutPos, feature, sp.sample, wx, wz);
                }
            }
        }
    }

    /**
     * Runs vanilla biome decoration on top of the rasterized
     * terrain.
     *
     * @param level generation level
     * @param chunk chunk to decorate
     * @param structureManager structure manager
     */
    @Override
    public void applyBiomeDecoration(
        WorldGenLevel level, ChunkAccess chunk, 
        StructureManager structureManager
    ) {
        super.applyBiomeDecoration(level, chunk, structureManager);
    }

    /**
     * No-op: mob spawning is unchanged.
     *
     * @param level generation region
     */
    @Override public void spawnOriginalMobs(WorldGenRegion level) {}

    /**
     * No-op: carvers are disabled; the world is fully field-driven.
     *
     * @param level generation level
     * @param seed world seed
     * @param randomState random state
     * @param biomeManager biome manager
     * @param structureManager structure manager
     * @param chunk chunk to carve
     * @param step carving step
     */
    @Override
    public void applyCarvers(
        WorldGenRegion level, long seed, RandomState randomState, 
        BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, 
        GenerationStep.Carving step
    ) {}

    /**
     * @return world height span
     */
    @Override public int getGenDepth() { return config.worldMaxY() - config.worldMinY(); }

    /**
     * @return configured sea level
     */
    @Override public int getSeaLevel() { return config.seaLevel(); }

    /**
     * @return configured world floor
     */
    @Override public int getMinY() { return config.worldMinY(); }

    /**
     * Base height for vanilla systems, taken from the final surface
     * H_f.
     *
     * @param x world X of the column
     * @param z world Z of the column
     * @param type heightmap type
     * @param level height accessor
     * @param randomState random state
     * @return clamped final surface height
     */
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(x, z, sp.sample);
        int surf = (int) Math.round(sp.sample.finalSurface);
        return Math.clamp(surf, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    /**
     * Base column of resolved solid materials from the world floor
     * to the final surface.
     *
     * @param x world X of the column
     * @param z world Z of the column
     * @param level height accessor
     * @param randomState random state
     * @return base column
     */
    @Override
    public net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        int height = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        height = Math.clamp(height, minY, maxY + 1);

        int columnMin = Math.max(config.worldMinY(), minY);
        int length = Math.max(0, height - columnMin);
        BlockState[] states = new BlockState[length];

        for (int y = columnMin; y < height; y++) {
            states[y - columnMin] = materialResolver.resolveSolid(y);
        }

        return new net.minecraft.world.level.NoiseColumn(columnMin, states);
    }

    /**
     * Debug-screen footer.
     *
     * @param info info list
     * @param randomState random state
     * @param pos query position
     */
    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add(String.format("GeoEngine 1.21.1: Seed=%d Dim=%d", worldSeed, dimensionId));
    }
}
