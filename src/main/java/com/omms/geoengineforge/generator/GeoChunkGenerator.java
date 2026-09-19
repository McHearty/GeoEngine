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
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
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
import net.minecraft.world.level.block.Blocks;
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

public class GeoChunkGenerator extends ChunkGenerator {
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

    private long worldSeed;
    private final int dimensionId;
    private final Holder<NoiseGeneratorSettings> settings;
    private final GeoConfig config;
    private final DimensionProfile profile;
    private FieldKernel kernel;
    private SectionClassifier sectionClassifier;
    private MaterialResolver materialResolver;
    private ChunkRasterizer chunkRasterizer;
    private final Aquifer.FluidPicker globalFluidPicker;

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
        Aquifer.FluidStatus fluidStatus = new Aquifer.FluidStatus(noiseSettings.seaLevel(), noiseSettings.defaultFluid());
        this.globalFluidPicker = (x, y, z) -> fluidStatus;

        this.materialResolver = new MaterialResolver(noiseSettings.defaultBlock(), noiseSettings.defaultFluid(), noiseSettings.seaLevel());
        reseed(worldSeed);
    }

    public synchronized void reseed(long seed) {
        this.worldSeed = seed;
        this.kernel = KernelProvider.createKernel(seed, this.profile);
        this.sectionClassifier = new SectionClassifier(this.config, this.kernel.getCaveField());
        this.chunkRasterizer = new ChunkRasterizer(this.kernel, this.sectionClassifier, this.materialResolver);

        if (this.biomeSource instanceof GeoBiomeSource geoBiomeSource) {
            geoBiomeSource.reseed(seed);
        }
    }

    @Override
    public ChunkGeneratorStructureState createState(
        HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed
    ) {
        reseed(seed);
        return super.createState(structureSetLookup, randomState, seed);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
        Blender blender, RandomState randomState, 
        StructureManager structureManager, ChunkAccess chunk
    ) {
        rasterizeChunk(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

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

    @Override
    public void buildSurface(
        WorldGenRegion level, StructureManager structureManager, 
        RandomState randomState, ChunkAccess chunk
    ) {
        if (this.worldSeed == 0L && level.getSeed() != 0L) {
            reseed(level.getSeed());
        }

        if (!SharedConstants.debugVoidTerrain(chunk.getPos())) {
            WorldGenerationContext context = new WorldGenerationContext(this, level);
            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            NoiseGeneratorSettings noiseSettings = this.settings.value();

            NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(c ->
                NoiseChunk.forChunk(
                    c,
                    randomState,
                    Beardifier.forStructuresInChunk(structureManager, chunk.getPos()),
                    noiseSettings,
                    this.globalFluidPicker,
                    Blender.of(level)
                )
            );

            randomState.surfaceSystem().buildSurface(
                randomState,
                level.getBiomeManager(),
                biomes,
                noiseSettings.useLegacyRandomSource(),
                context,
                chunk,
                noiseChunk,
                noiseSettings.surfaceRule()
            );

            if (dimensionId == 0) {
                materializeSpecialFeatures(chunk);
            }
        }
    }

    private void materializeSpecialFeatures(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();

        WorkerScratchpad sp = ScratchpadProvider.get();
        SpecialFeatureDetector featureDetector = new SpecialFeatureDetector(config);
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            int wz = originZ + lz;
            for (int lx = 0; lx < 16; lx++) {
                int wx = originX + lx;
                int cIdx = (lz << 4) | lx;

                sp.populateSampleFromColumn(lx, lz, wx, wz);
                double slope = sp.sample.gradMagnitude;
                double downstreamDrop = slope * 10.0;

                SpecialFeatureDetector.FeatureType feature = featureDetector.detectFeature(sp.sample, downstreamDrop);
                if (feature != SpecialFeatureDetector.FeatureType.NONE) {
                    SpecialFeatureGenerator.materializeFeature(chunk, mutPos, feature, sp.sample);
                }
            }
        }
    }

    @Override
    public void applyBiomeDecoration(
        WorldGenLevel level, ChunkAccess chunk, 
        StructureManager structureManager
    ) {
        super.applyBiomeDecoration(level, chunk, structureManager);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {}

    @Override
    public void applyCarvers(
        WorldGenRegion level, long seed, RandomState randomState, 
        BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, 
        GenerationStep.Carving step
    ) {}

    @Override public int getGenDepth() { return config.worldMaxY() - config.worldMinY(); }
    @Override public int getSeaLevel() { return config.seaLevel(); }
    @Override public int getMinY() { return config.worldMinY(); }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(x, z, sp.sample);
        return (int) Math.round(sp.sample.finalSurface);
    }

    @Override
    public net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int height = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        BlockState[] states = new BlockState[Math.max(0, height - config.worldMinY())];
        for (int y = config.worldMinY(); y < height; y++) {
            states[y - config.worldMinY()] = materialResolver.resolveSolid(y);
        }
        return new net.minecraft.world.level.NoiseColumn(config.worldMinY(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add(String.format("GeoEngine 1.21.1: Seed=%d Dim=%d", worldSeed, dimensionId));
    }
}
