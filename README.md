# GeoEngine

> **Bounded Deterministic Geomorphic Terrain Engine**  
> **Author:** Old Man Modding Studio (`com.omms`)  
> **Mod ID:** `geoengine`  
> **Target Platform:** Minecraft Java Edition 1.21.1 / NeoForge (21.1.93+) / Java 21  

GeoEngine is a procedural scalar-field geomorphic terrain engine designed to replace Minecraft's default `NoiseBasedChunkGenerator`. Rather than stitching together hundreds of independently authored biome decorators or executing slow, tick-dependent physical erosion simulations, GeoEngine deterministically evaluates the equilibrium terrain surfaces and volumetric void structures that geological processes would produce.

---

## 1. Architectural Model & Core Invariants

The codebase enforces a strict dependency inversion between the pure mathematical core and the Minecraft / NeoForge platform adapter:

```text
+==========================================================================+
|                     com.omms.geoenginecore (Pure Java)                   |
|                                                                          |
|  - Zero Minecraft/NeoForge imports                                       |
|  - Deterministic: F(seed, dimension, version, config, x, y, z) = const   |
|  - Thread-Safe & Reentrant: Worker-local Scratchpads                     |
|  - Zero Steady-State Heap Allocation in Inner Loops (§62)                |
|  - Canonical Density: D(x,y,z) = Hf(x,z) - (y + W(x,y,z)) - C(x,y,z)     |
|  - Pure D8 Downhill Topological Hydrology Network with Kahn's Algorithm  |
|  - Hardware SIMD Acceleration via Java 21 Vector API (Dynamic Isolation) |
+==========================================================================+
                                     │
                                     │ Pure field results (Hf, D, Classification)
                                     ▼
+==========================================================================+
|                    com.omms.geoengineforge (NeoForge Adapter)            |
|                                                                          |
|  - GeoChunkGenerator (Thin lifecycle orchestrator)                       |
|  - ChunkRasterizer (Voxel scanning & SectionClassifier pruning)          |
|  - SectionWriter (PalettedContainer locking & single-call bulk filling)   |
|  - HeightmapWriter (WORLD_SURFACE_WG & OCEAN_FLOOR_WG population)        |
|  - StrataMaterialResolver & SpecialFeatureGenerator                      |
+==========================================================================+
```

### Governing Invariants
1. **Determinism & Chunk Independence (§2.2, §2.3, §8)**: Identical coordinates evaluate to identical terrain regardless of chunk generation order, worker thread assignment, cache warming state, or world reload cycles.
2. **Topological Drainage & Mass-Budget Deposition (§24, §26–§34)**: River incision ($R$) operates on the pre-fluvial surface $H_{\text{pre}}$. Runoff accumulates strictly downstream via coarse D8 routing. Equilibrium deposition ($S$) is bounded by a strict mass budget: $0 \le S \le E_{\text{total}} = E_{\text{erosion}} + R_{\text{incision}}$.
3. **Vertical Scalability ($O(N_x N_z + N_{\text{band}})$)**: Designed for worlds spanning $Y = -64$ to $Y = 1984$ (2048 blocks, 128 sections). Subterranean sections outside active cave occupancy evaluate as `SOLID` and bulk-fill in $O(1)$. Active voxel scanning is restricted to `BAND` sections ($\sim 8\text{--}14\%$ of the column).
4. **Density Monotonicity (§50)**: When volumetric warp $W = 0$ and cave void $C = 0$, $\frac{\partial D}{\partial y} \equiv -1$.
5. **Class-Loading Isolation (§70)**: Vector API SIMD acceleration (`jdk.incubator.vector`) is loaded dynamically via reflection. Runtimes without incubator JVM flags run on the scalar numerical reference without class-loading errors.

---

## 2. Sealed Milestone Summary (Phases 1–9)

* **Phase 1 (Crustal Baseline)**: Tectonic uplift ($T = A u^p$), stress warping, age/climate blending, and pre-carve baseline ($H_0 = T - E$).
* **Phase 2 (Topological Hydrology & Deposition)**: Coarse D8 downhill drainage graph with Kahn's topological accumulation, $18 \times 18$ halo lattice, river incision ($R$), and mass-budget deposition ($S$).
* **Phase 3 (Volumetric Layer & Regional Pruning)**: Slope-modulated warp ($W$), multi-class caves ($C \ge 0$, tunnels + chambers), smoothstep overburden protection, and regional 3D cave occupancy pruning.
* **Phase 4 (Decoupled Raster Pipeline)**: Refactored `GeoChunkGenerator` delegating to `ChunkRasterizer`, `SectionWriter`, and `HeightmapWriter`.
* **Phase 5 (Multi-Scale Landform Grammar)**: Concentric prominence evaluation ($r = 8, 32, 128, 512\text{m}$), 2D Hessian principal curvature eigenvalues ($\lambda_1, \lambda_2$), priority hierarchy, and compound classification (e.g., Fjords).
* **Phase 6 (Dimension Profiles)**: Stateless profile contracts for Overworld (continental), Nether (open volcanic sky, calderas, lava tubes, lava sea at $Y=32$, no ceiling), and The End (Voronoi fracture plateaus, anchored root hulls).
* **Phase 7 (Advanced Geomorphic Systems)**: Additive process terms for glacial U-valleys/cirques, karst sinkholes/towers, aeolian transverse dunes, alluvial fans, deltas, and coastal wave-cut platforms.
* **Phase 8 (SIMD Acceleration & Allocation Hardening)**: Vectorized bilinear interpolation and columnar density arithmetic via `DoubleVector`, preallocated lane buffers on `WorkerScratchpad`, and verified $0$-byte steady-state thread allocation.
* **Phase 9 (Content Integration & Structure Suitability)**: Four-factor foundation fitness rating ($F_{\text{suit}}$) and cave-breach safety gating for villages/outposts, dipping 3D lithological strata, and deterministic special feature materialization (waterfalls, springs, geothermal vents).

---

## 3. Repository Structure

```text
.
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradle/wrapper/gradle-wrapper.properties
└── src/
    ├── main/
    │   ├── java/com/omms/
    │   │   ├── geoenginecore/              # Pure Java Mathematical Core
    │   │   │   ├── cache/                  # Segmented LRU Macro Grid Caches
    │   │   │   ├── climate/                # Climate Classifiers, Thermal Lapse & Zones
    │   │   │   ├── derivative/             # Gradients, Curvature & Finite Differences
    │   │   │   ├── dimension/              # Dimension Profile Configurations
    │   │   │   ├── feature/                # Geomorphic Feature Detectors
    │   │   │   ├── field/                  # Tectonics, Warp, Caves, Erosion & Baseline Fields
    │   │   │   │   └── advanced/           # Glacial, Aeolian, Karst, Coastal & Delta Fields
    │   │   │   ├── geomorphology/          # Hessian Solver, Multi-Scale Relief & Grammar
    │   │   │   ├── hydrology/              # D8 Drainage Graph, Router, River & Deposition
    │   │   │   ├── material/               # Lithology & Rock Family Classification
    │   │   │   ├── math/                   # GeoConfig, GeoSample & Kernel Interfaces
    │   │   │   ├── memory/                 # ThreadLocal WorkerScratchpad & Buffers
    │   │   │   ├── noise/                  # Continuous Noise & Seed Derivations
    │   │   │   ├── raster/                 # SectionClassifier & Pruning Enums
    │   │   │   ├── simd/                   # Vector API SIMD Kernel & Dynamic Loader
    │   │   │   └── structure/              # Foundation Stability & Settlement Scoring
    │   │   └── geoengineforge/             # NeoForge Adapter Layer
    │   │       ├── GeoEngineMod.java       # Mod Entry Point
    │   │       ├── debug/                  # Diagnostic Commands & CSV Exporters
    │   │       ├── feature/                # Feature Block Materializers
    │   │       ├── generator/              # ChunkGenerator & BiomeSource
    │   │       ├── integration/            # Registries, Dimension Adapters & Bootstrap
    │   │       ├── raster/                 # SectionWriter, HeightmapWriter & Rasterizer
    │   │       └── structure/              # Structure Placement Validator Adapter
    │   └── resources/
    │       ├── pack.mcmeta
    │       ├── META-INF/neoforge.mods.toml
    │       └── data/geoengine/dimension/overworld.json
    └── test/
        └── java/com/omms/
            ├── geoenginecore/test/         # Headless Invariant & Performance Test Suites
            └── geoengineforge/test/        # Headless Simulated Raster Pipeline Tests
```

---

## 4. Build Prerequisites & Setup

* **Java Development Kit (JDK)**: OpenJDK 21 or higher.
* **Build Tool**: Gradle 8.10+ (managed via provided `./gradlew`).
* **Target Environment**: NeoForge `21.1.93` on Minecraft `1.21.1`.

### Build Verification
Clone the repository and compile the mod jar:

```bash
# Unix / macOS
./gradlew build

# Windows
gradlew.bat build
```

The compiled binary will be located at:
```text
build/libs/geoengine-1.0.0.jar
```

---

## 5. Testing & Invariant Verification

All test suites are located in `src/test/java/` and execute **completely headless** without launching a Minecraft client, server, or graphical environment.

### Running the Full Test Suite
```bash
./gradlew test --info
```

### Verification Test Suite Index

| Test Class | Spec Verification Target |
| :--- | :--- |
| `Phase1CoreVerificationTest` | Seed derivation independence (§9), density monotonicity ($\frac{\partial D}{\partial y} = -1$, §50), gradient step accuracy on unit slopes (§37), and multithreaded determinism (§8, §139). |
| `Phase2And3VerificationTest` | Real-pipeline deposition budget enforcement ($0 \le S \le E_{\text{total}}$, §33), cave void sign convention ($C \ge 0$, §46), and `evaluateFullColumn` write-back (§65). |
| `Phase3BandRatioAllocationTest` | 2048-block vertical scalability ($N_{\text{band}} / N_{\text{total}} < 20\%$ across 128 sections, §2.4, §55) and zero steady-state heap allocation validation. |
| `Phase4RasterPipelineTest` | End-to-end simulated 128-section chunk rasterization, verifying bulk fast-paths and heightmap synchronization without Minecraft classes (§141–§143). |
| `Phase5LandformGrammarTest` | Multi-scale prominence discrimination (Butte vs. Mesa vs. Plateau, §99), priority overrides (Canyon > Valley, §186), and compound Fjords (§187). |
| `Phase6And7IntegrationTest` | Dimension stack reconfiguration (Nether open-sky density $D < 0$ at $Y=220$, End Voronoi fracture monoliths and anchored root hulls, §88–§91). |
| `Phase6And7DifferentialTest` | Differential verification proving that active geomorphic terms (glacial, karst, coastal) produce measurable elevation deltas relative to unaugmented terrain (§155). |
| `Phase8PerformanceTest` | Strict JVM thread allocation probe via `ThreadMXBean` proving **0 bytes allocated** over 1,000 chunks (§62), SIMD numerical parity ($|H_f^s - H_f^v| \le 10^{-12}$, §71), and macro cache idempotency (§77, §160). |
| `Phase9FinalContentTest` | Structure suitability gating (rejecting cliff edges and shallow cave voids, §135, §136), dipping strata continuity across seams (§189), and feature detection. |
| `MultiSeedMultiThreadMatrixTest` | Concurrent stress test across 5 distinct 64-bit seeds under an 8-thread pool, asserting bit-identical output arrays (§156). |
| `InterRegionHydrologyContinuityTest` | Cross-seam evaluation asserting continuous D8 accumulation across the $256$-block region boundary ($X = 255 \leftrightarrow X = 256$) via the $18 \times 18$ halo (§26, §80). |

---

## 6. Deployment Guide

GeoEngine can be deployed to production dedicated servers, development modding workspaces, or client instances.

### 6.1 Standard Installation (Client or Dedicated Server)
1. Install [NeoForge 21.1.93+](https://neoforged.net/) for Minecraft 1.21.1.
2. Drop `geoengine-1.0.0.jar` into the server or client `mods/` folder.
3. Start the server or client. GeoEngine registers its custom codecs:
   - Chunk Generator: `geoengine:geo_chunk_generator`
   - Biome Source: `geoengine:geo_biome_source`

---

### 6.2 Enabling Hardware SIMD Acceleration (Vector API)
GeoEngine features Java 21 Vector API acceleration. Because the Vector API is an incubating module in Java 21, the JVM flag must be explicitly passed at startup.

#### Production Dedicated Server Launch Script (`run.sh`)
```bash
#!/usr/bin/env sh
java -Xms4G -Xmx8G \
     -XX:+UseG1GC \
     -XX:+IgnoreUnrecognizedVMOptions \
     --add-modules jdk.incubator.vector \
     -jar server.jar nogui
```

#### Windows Batch Launch Script (`run.bat`)
```bat
@echo off
java -Xms4G -Xmx8G ^
     -XX:+UseG1GC ^
     -XX:+IgnoreUnrecognizedVMOptions ^
     --add-modules jdk.incubator.vector ^
     -jar server.jar nogui
pause
```

> **Dynamic Fallback Behavior (§70):** If `--add-modules jdk.incubator.vector` is omitted, `KernelProvider` logs an informational message and routes execution to the reference `ScalarFieldKernel`. The game will continue running without crashing.

---

### 6.3 World Generation Activation via Data Pack
To generate an Overworld driven by GeoEngine, configure a worldgen data pack referencing the generator codec.

#### File: `data/geoengine/dimension/overworld.json`
```json
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "geoengine:geo_chunk_generator",
    "seed": 0,
    "dimension_id": 0,
    "biome_source": {
      "type": "geoengine:geo_biome_source",
      "seed": 0
    }
  }
}
```

*Note: The `"seed": 0` field in JSON is automatically merged with the server's master world seed during level initialization.*

---

### 6.4 In-Game Diagnostics & Inspection
GeoEngine provides operator commands for live diagnostics:

* `/geoengine info`: Displays the active solver state, Java 21 Vector API acceleration status, and underlying dimension parameters.
* `/geoengine export slice <chunkX> <chunkZ> <filePath>`: Dumps a numerical CSV raster slice of the surface, gradient vectors, and discrete Laplacians to disk for external visualization (§153, §154).

---

## 7. Performance & Operational Benchmarks

Empirical metrics collected from automated test suites on Java 21 (x86_64, AVX2 enabled):

* **Section Classification Fast-Path Ratio (§179):**
  - `AIR` sections (stratosphere bulk-fill): **$65.6\%$** (84/128 sections)
  - `SOLID` sections (deep crust bulk-fill): **$22.7\%$** (29/128 sections)
  - `BAND` sections (voxel density loop): **$11.7\%$** (15/128 sections)
  - *Result: $>88\%$ of vertical sections bypass voxel-level evaluation.*
* **Inner Loop Allocation Rate (§62):** **`0 bytes/chunk`** verified zero additional allocation in the hot surface and density loops after JIT warm-up.
* **Vector/Scalar Numerical Tolerance (§71):** Maximum observed delta $|H_f^s - H_f^v| \le 10^{-12}$ (strict parity conforming to IEEE 754 FMA precision).
* **Hydrological Cache Hit Latency (§80):** $O(1)$ amortized retrieval for within-region flow accumulation queries.

---

## 8. License

This project is licensed under the **MIT License**. Created by **Old Man Modding Studio**.
