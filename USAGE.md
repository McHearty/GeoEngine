# GeoEngine Usage & Developer Guide

This document covers standalone usage of the pure Java core, configuration setup, NeoForge registration, structure placement queries, and deployment options.

---

## 1. Standalone Core Sampling (No Minecraft Dependencies)

The `com.geoengine.core` module is fully isolated and runnable in external CLI applications, testing harnesses, or map pre-generators.

### Evaluating Surface Elevation ($H_f$) and Density ($D$)

```java
import com.geoengine.core.math.*;
import com.geoengine.core.memory.ScratchpadProvider;
import com.geoengine.core.memory.WorkerScratchpad;
import com.geoengine.core.simd.KernelProvider;

public class StandaloneSamplingExample {
    public static void main(String[] args) {
        long worldSeed = 0x5EED1234ABCD9876L;
        GeoConfig config = GeoConfig.defaultOverworld(1);

        // KernelProvider automatically selects SIMD if available, falling back to Scalar
        FieldKernel kernel = KernelProvider.createKernel(worldSeed, config);
        WorkerScratchpad scratchpad = ScratchpadProvider.get();

        int chunkWorldX = 256;
        int chunkWorldZ = -512;

        // 1. Solve the 16x16 chunk surface grid
        kernel.rasterizeSurfaceChunk(scratchpad, chunkWorldX, chunkWorldZ);

        // 2. Read final surface elevation at local chunk coordinate (x=8, z=8)
        int localX = 8;
        int localZ = 8;
        double surfaceHeight = scratchpad.surfaceGrid[(localZ << 4) | localX];
        System.out.printf("Surface Height at (%d, %d): %.2f%n", 
            chunkWorldX + localX, chunkWorldZ + localZ, surfaceHeight);

        // 3. Evaluate 3D density (D > 0: Solid, D <= 0: Air)
        int worldY = 70;
        float density = kernel.evaluateDensity(scratchpad, chunkWorldX + localX, worldY, chunkWorldZ + localZ);
        System.out.printf("Density at Y=%d: %.4f (%s)%n", 
            worldY, density, density > 0.0f ? "SOLID" : "AIR");
    }
}
```

---

## 2. Configuration Parameters (`GeoConfig`)

`GeoConfig` is an immutable record that validates all parameters on construction.

```java
import com.geoengine.core.math.GeoConfig;

GeoConfig customConfig = new GeoConfig(
    1,                  // generatorVersion: Seed-hash versioning
    0,                  // dimensionId: 0=Overworld, 1=Nether, 2=End
    -64,                // worldMinY: Lowest generation bound
    1984,               // worldMaxY: Highest generation bound (2048 blocks total)
    64,                 // seaLevel: Baseline ocean/water plane
    0.0005,             // tectonicFreqLow: Continental macro frequency
    0.0017,             // tectonicFreqA: Primary mountain belt frequency
    0.0023,             // tectonicFreqB: Secondary structural frequency
    200.0,              // tectonicAmpLow: Continental baseline amplitude
    350.0,              // tectonicAmpA: Primary belt relief amplitude
    150.0,              // tectonicAmpB: Secondary ridge amplitude
    1.8,                // upliftExponent: Concentrates relief toward ridge axes (p >= 1.0)
    0.0007,             // stressFrequency: Horizontal stress coordinate warp frequency
    32.0,               // stressAmplitude: Maximum horizontal coordinate warp
    0.45,               // stressMaxJacobian: Maximum permissible warp gradient
    0.0003,             // epochFrequency: Geomorphic maturity/age spatial frequency
    0.0004,             // climateTempFrequency: Base temperature variation frequency
    0.0004,             // climateHumidFrequency: Base humidity variation frequency
    0.6,                // climateMin: Minimum geomorphic process multiplier
    1.4,                // climateMax: Maximum geomorphic process multiplier
    0.0012,             // lapseRatePerBlock: Linear thermal reduction per vertical block
    45.0,               // baseErosionRate: Macro erosion lowering budget
    16.0,               // maxWarpAmplitude: Maximum vertical 3D displacement (|W| <= Wmax)
    16                  // surfaceBandRadius: Margin for voxel-level density evaluation
);
```

---

## 3. Structure Foundation Suitability Queries

To prevent custom or vanilla structures from spawning over sheer drop-offs, deep chasms, or flood zones, query `StructureSuitabilityField`:

```java
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;
import com.geoengine.core.structure.StructureSuitabilityField;

public class StructurePlacementValidator {
    private final StructureSuitabilityField suitability;

    public StructurePlacementValidator(GeoConfig config) {
        this.suitability = new StructureSuitabilityField(config);
    }

    public boolean canSpawnVillage(GeoSample sample, double measuredSubsurfaceCaveVoid) {
        StructureSuitabilityField.FoundationReport report = 
            new StructureSuitabilityField.FoundationReport();

        suitability.evaluateSuitability(sample, measuredSubsurfaceCaveVoid, report);

        // Safe placement requires high settlement score and zero major safety flags
        return report.settlementScore > 0.65
            && !report.isCliffEdge
            && !report.isCaveBreachRisk
            && !report.isFloodRisk;
    }
}
```

---

## 4. Multi-Dimension Architecture Setup

Each dimension employs custom field transformations through `DimensionProfile`:

```java
import com.geoengine.core.dimension.*;

long seed = 123456789L;
int version = 1;

// Overworld: Continental crust, fluvial networks, stratified topsoils
DimensionProfile overworld = new OverworldProfile(seed, version);

// Nether: Open volcanic relief, shield calderas, lava channels, lava tubes at Y=32 (No ceiling)
DimensionProfile nether = new NetherProfile(seed, version);

// The End: Coherent Voronoi fracture plateaus, shear scarps, void abyss
DimensionProfile end = new EndProfile(seed, version);
```

---

## 5. NeoForge 1.21.1 Mod Registration

Register `GeoChunkGenerator` and `GeoBiomeSource` within the NeoForge registration lifecycle.

### Codec Registration (Mod Event Bus)

```java
import com.geoengine.forge.generator.GeoBiomeSource;
import com.geoengine.forge.generator.GeoChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GeoEngineMod {
    public static final String MODID = "geoengine";

    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, MODID);

    public GeoEngineMod(IEventBus modEventBus) {
        CHUNK_GENERATORS.register("geo_chunk_generator", () -> GeoChunkGenerator.CODEC);
        BIOME_SOURCES.register("geo_biome_source", () -> GeoBiomeSource.CODEC);

        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
    }
}
```

### Dimension Worldgen JSON Definition (`data/geoengine/dimension/overworld.json`)

```json
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "geoengine:geo_chunk_generator",
    "biome_source": {
      "type": "geoengine:geo_biome_source",
      "seed": 0
    }
  }
}
```

---

## 6. Execution Modes and JVM Flags

### Reference Scalar Mode (Default)
Runs standard portable floating-point arithmetic without incubating JVM modules:
```bash
java -jar server.jar nogui
```

### Vector API SIMD Mode
Enables hardware SIMD operations (256/512-bit vector lanes) via Java 21's Vector API:
```bash
java --add-modules jdk.incubator.vector -jar server.jar nogui
```
*Note: If the incubator module is omitted at runtime, `KernelProvider` logs an informational notice and falls back to `ScalarFieldKernel` without throwing `NoClassDefFoundError`.*

---

## 7. Running Unit & Invariant Tests

All core verification suites run in headless test runners without bootstrapping Minecraft:

```bash
./gradlew test
```

### Verification Test Suite Index

* `Phase1CoreVerificationTest`: Validates seed domain separation, chunk seam continuity, and density monotonicity ($\frac{\partial D}{\partial y} \equiv -1$).
* `Phase2And3VerificationTest`: Enforces mass-budget deposition bounds ($S \le E_{total}$) and cave void sign constraints ($C \ge 0$).
* `Phase5VerificationTest`: Verifies 2D Hessian principal curvature eigenvalues on quadratic test surfaces and landform bitmask integrity.
* `Phase6DimensionVerificationTest`: Confirms Nether open-sky density ($D < 0$ at high Y) and End fracture monolith anchoring.
* `Phase7AdvancedGeomorphologyTest`: Verifies parabolic U-valley cross-sections and aridity gating for aeolian dunes.
* `Phase8PerformanceTest`: Verifies SIMD/scalar numerical parity and warm/cold cache output equivalence ($H_f^{\text{cold}} \equiv H_f^{\text{warm}}$).
* `Phase9ContentIntegrationTest`: Evaluates structure stability scores, lithological strata consistency, and waterfall feature triggers.
