# GeoEngine

> **Bounded Deterministic Geomorphic Terrain Engine**  
> **Author:** Old Man Modding Studio (`com.omms`)  
> **Mod ID:** `geoengine`  
> **Target Platform:** Minecraft Java Edition 1.21.1 / NeoForge (21.1.93+) / Java 21  

GeoEngine is a procedural scalar-field geomorphic terrain engine designed to replace Minecraft's default `NoiseBasedChunkGenerator`. Rather than stitching together hundreds of independently authored biome decorators or executing slow, tick-dependent physical erosion simulations, GeoEngine deterministically evaluates the equilibrium terrain surfaces and volumetric void structures that geological processes would produce.

The normative design contract is [`TECHSPEC.md`](TECHSPEC.md) (233 sections). Section numbers cited below (§…) refer to it.

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
|  - SectionWriter (PalettedContainer locking & single-call bulk filling)  |
|  - HeightmapWriter (WORLD_SURFACE_WG & OCEAN_FLOOR_WG population)        |
|  - StrataMaterialResolver & SpecialFeatureGenerator                      |
+==========================================================================+
```

### Governing Invariants
1. **Determinism & Chunk Independence (§2.2, §2.3, §8)**: Identical coordinates evaluate to identical terrain regardless of chunk generation order, worker thread assignment, cache warming state, or world reload cycles.
2. **Topological Drainage & Mass-Budget Deposition (§24, §26–§34)**: River incision ($R$) operates on the pre-fluvial surface $H_{\text{pre}}$ (never on a surface that already contains $R$). Runoff accumulates strictly downstream via coarse D8 routing. Equilibrium deposition ($S$) is bounded by a strict mass budget: $0 \le S \le E_{\text{total}} = E_{\text{erosion}} + R_{\text{incision}}$.
3. **Vertical Scalability ($O(N_x N_z + N_{\text{band}})$)**: Designed for worlds spanning $Y = -64$ to $Y = 1984$ (2048 blocks, 128 sections). Subterranean sections outside active cave occupancy evaluate as `SOLID` and bulk-fill in $O(1)$. Active voxel scanning is restricted to `BAND` sections ($\sim 8\text{--}14\%$ of the column).
4. **Density Monotonicity (§50)**: When volumetric warp $W = 0$ and cave void $C = 0$, $\frac{\partial D}{\partial y} \equiv -1$.
5. **Class-Loading Isolation (§70)**: Vector API SIMD acceleration (`jdk.incubator.vector`) is loaded dynamically via reflection (`KernelProvider`). Runtimes without incubator JVM flags run on the scalar numerical reference without class-loading errors.

---

## 2. Current Implementation Status

* **Phases 1–9**: SEALED & CONFORMANCE-VERIFIED — full headless suite green (19 test classes, 71 tests, 0 failures, 0 skipped).
* **Last verified**: 2026-09-22 — forced full re-run (`./gradlew test --rerun-tasks`) after the Phase 2 conformance re-pass (F_channel wiring, routing-lattice coordinate fix, core `GeoMath` clamp, bounded-incision test): **71/71 passed**, all headless (19 suites; matrix in §6, benchmark record in `docs/BENCHMARKS.md`).
* **Phase 1 conformance re-pass (lands with this update)**: re-audited all eight Phase 1 acceptance criteria against TECHSPEC §§220–230; suite extended 12 classes / 30 tests → 19 classes / 70 tests (seven new suites). Defects found and fixed:
  1. `DerivativeSampler` central-difference factor-of-2 error — dead code, now the standalone §37 reference pinned by `Phase1DerivativeStencilTest` (the kernel keeps its inline stencils).
  2. `GeoConfig` accepted NaN/±Infinity and malformed cave envelopes — §64 validation now rejects non-finite components, non-positive frequencies, negative lapse rates, and inverted/out-of-bounds cave envelopes.
  3. `LandformClassifier` held shared mutable solver scratch (`CurvatureResult`/`ReliefReport`), breaking 16-thread determinism — now ThreadLocal-confined per §66; `MultiSeedMultiThreadMatrixTest` compares the entire scratchpad (28 double + 3 int grids) bit-exactly across 16 threads × 5 seeds.
  4. §62 zero steady-state allocation violated — 12,288 B/chunk from `Long` autoboxing in `ConcurrentHashMap<Long, DrainageGraph>` lookups (the single-slot thread register missed on every blended seam column). Fixed: striped primitive-`long`-keyed open-addressing cache (lock-free hit path) + 4-slot direct-mapped register in the thread-confined `WorkerScratchpad`. Steady state is now **0 bytes/chunk**.
  5. `GeoDebugExporter.exportVerticalSlicePng` flip indexed `worldMinY`-offset rows, going negative for `worldMinY = -64` — now flips on image height (TECHSPEC §154).
  6. Terminal landform fallback resolved to `PLAINS` (the grammar's documented catch-all; residual Tier-4 convex cases now resolve to `MOUNTAIN`); `UNKNOWN` (id 0) remains a defensive sentinel for corrupted bits, capped at 5% in the finiteness sweep as a regression guard.
  7. Chunk-seam test corrected: continuity is asserted on warp *displacements* (coordinates include the 1.0-block identity offset), against the rigorous 0.144/block `GeoNoise` Lipschitz bound (2× margin).
  8. Core-purity hole (found during the Phase 2 re-pass): core code called `Math.clamp` — a method the NeoForge toolchain JDK patches into `java.lang.Math` that a standard JDK does not have, so the core compiled only under the mod toolchain; the import-based purity guard could not see it (a `java.lang` extension, not a platform import). Fixed: new core-local `GeoMath` (int/long/float/double overloads; NaN values pass through; `IllegalArgumentException` on NaN bounds or `min > max`, mirroring the toolchain contract) wired into all 30 core call sites; the core now compiles on a plain JDK 17 with only the documented `--add-modules jdk.incubator.vector` flag. `CorePurityGuardTest` now also fails on any `Math.clamp` reference in the core.
  Benchmark floor re-baselined 200 → 600 chunks/sec (measured 1,253.7 on the reference machine, 2.09× headroom).
* **In flight — river system (WIP `8941d5a`, partially landed):**
  - *Landed*: re-tuned incision model (activation at $A_f > 1.8$; lowland slope baseline 0.65 keeps defined trunk-river beds; $R_{\max}$ scaled by local slope, §28); saturated corridor width $W(A_f)$ per §29 (Brooks ~3–5 blk → trunk ~16–28 blk; zero below the $A_f = 2.5$ channel-initiation threshold); cosine-squared U-trough cross-section factor; River / Frozen River biome handoff in `GeoBiomeSource` keyed on column incision depth; **$F_{\text{channel}}$ wired into the incision path** — §28's $R_{\text{base}} = F(A_f)F_{\text{slope}}F_{\text{climate}}F_{\text{channel}}$ is now complete in both the `evaluateFullColumn` and bulk-raster paths: `ChannelField.corridorFactor` (cosine-squared U: thalweg 1.0, banks 0.0, monotone between) with the distance measured from the column to the column's routed-lattice downstream centerline by `DrainageRouter.evaluateChannelFactor` (allocation-free bounded walk, WALK_MARGIN 23 blk > 16√2 D8-hop bound), so incision rasterizes as cross-section-shaped corridors. Conformance re-pass fixed two latent defects: the new `DrainageGraph.latticeX/latticeZ` accessors had the z-major index axes swapped (X/Z coordinates transposed — centerline distances were measured against the lattice mirrored over the diagonal), and `segmentDistance` could divide by zero on a degenerate segment (guarded; unreachable under the strictly-downhill invariant).
  - *Not yet landed*:
    1. **Rivers carve but stay dry (§149)** — `GeoSample.waterSurfaceLevel` is declared but neither computed nor read, and the surface-rule context carries no channel/flow data, so no `WATER` is placed inside carved channels.
    2. **Meandering (§30) missing** — the legacy `getMeanderOffset` was removed during WIP; a bounded deterministic lateral meander has not been re-introduced, so centerlines are currently pure routing-lattice paths.
    3. **§31 river feature grammar partial** — confluence, delta, and alluvial-fan relationships exist; oxbow, point bar, cut bank, levee, floodplain, and crevasse splay are not yet derived from the drainage graph.
* **Not started — Phase 10 (production hardening)**: multiplayer, dedicated server, client/server compatibility, world save/reload, long-distance travel, chunk regeneration, crash recovery, configuration migration. Note: `build.gradle` already configures a `gameTestServer` run and `neoforged.enabledGameTestNamespaces` — the scaffolding is in place, but no `@GameTest` classes are written yet.
* **Open housekeeping**: none — the Phase 1 "benchmark results are recorded" criterion is durably satisfied in `docs/BENCHMARKS.md` (measured values + collection metadata, per §221).
* **Planned work order** (keep this list current as work progresses):
  1. ~~Wire $F_{\text{channel}}$ into the incision path (distance-to-centerline from the `DrainageGraph`)~~ — landed with this re-pass (see §2); region-seam and chunk-boundary continuity re-verified green; explicit `0 ≤ R ≤ R_max` test added to `Phase2And3VerificationTest`. Outstanding: thalweg-$R$ > bank-$R$ > 0 corridor-shape probe (fold into the water-surface work).
  2. Compute a deterministic `waterSurfaceLevel` in core; plumb channel/water data into `GeoSurfaceRuleContext`; place `WATER` in carved channel columns below the water surface; tests: water only inside the corridor, deterministic across seeds and threads.
  3. Re-introduce bounded deterministic meandering (§30); tests: centerline stays within its valley, bounded by channel width, downhill-consistent, deterministic.
  4. In-game Phase 9 verification (structures, vegetation, and special features against real vanilla placement).
  5. Phase 10: `@GameTest` suites (client/server determinism, save/reload, long-distance travel, chunk regeneration, config migration) plus a dedicated-server smoke run.
  6. ~~Commit the durable benchmark record~~ — done: `docs/BENCHMARKS.md` (open housekeeping item closed).

---

## 3. Sealed Milestone Summary (Phases 1–9)

* **Phase 1 (Crustal Baseline)**: Tectonic uplift ($T = A u^p$), stress warping, age/climate blending, and pre-carve baseline ($H_0 = T - E$).
* **Phase 2 (Topological Hydrology & Deposition)**: Coarse D8 downhill drainage graph on a $24 \times 24$ routing lattice (16×16 core + 4-cell halo on every side, covering 384×384 blocks) with Kahn's topological accumulation, river incision ($R$), and mass-budget deposition ($S$).
* **Phase 3 (Volumetric Layer & Regional Pruning)**: Slope-modulated warp ($W$), multi-class caves ($C \ge 0$, tunnels + chambers), smoothstep overburden protection, and regional 3D cave occupancy pruning.
* **Phase 4 (Decoupled Raster Pipeline)**: Refactored `GeoChunkGenerator` delegating to `ChunkRasterizer`, `SectionWriter`, and `HeightmapWriter`.
* **Phase 5 (Multi-Scale Landform Grammar)**: Concentric prominence evaluation ($r = 8, 32, 128, 512\text{m}$), 2D Hessian principal curvature eigenvalues ($\lambda_1, \lambda_2$), priority hierarchy, and compound classification (e.g., Fjords).
* **Phase 6 (Dimension Profiles)**: Stateless profile contracts for Overworld (continental), Nether (open volcanic sky, calderas, lava tubes, lava sea at $Y=32$, no ceiling), and The End (Voronoi fracture plateaus, anchored root hulls).
* **Phase 7 (Advanced Geomorphic Systems)**: Additive process terms for glacial U-valleys/cirques, karst sinkholes/towers, aeolian transverse dunes, alluvial fans, deltas, volcanic calderas, and coastal wave-cut platforms.
* **Phase 8 (SIMD Acceleration & Allocation Hardening)**: Vectorized bilinear interpolation and columnar density arithmetic via `DoubleVector`, preallocated lane buffers on `WorkerScratchpad`, and verified $0$-byte steady-state thread allocation.
* **Phase 9 (Content Integration & Structure Suitability)**: Four-factor foundation fitness rating ($F_{\text{suit}}$) and cave-breach safety gating for villages/outposts, dipping 3D lithological strata, deterministic special feature materialization (waterfalls, springs, geothermal vents), and surface-rule material evaluation (`GeoSurfaceRuleEvaluator`).

---

## 4. Repository Structure

```text
.
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradle/wrapper/gradle-wrapper.properties
├── TECHSPEC.md                     # Normative 233-section specification
├── docs/
│   └── BENCHMARKS.md               # recorded benchmark results (TECHSPEC §221)
└── src/
    ├── main/
    │   ├── java/com/omms/
    │   │   ├── geoenginecore/              # Pure Java mathematical core
    │   │   │   ├── cache/                  # KernelProvider (SIMD dynamic loader),
    │   │   │   │                           # MacroGridCache, SoABuffers, VectorFieldKernel
    │   │   │   ├── climate/                # ClimateClassifier, ClimateZone,
    │   │   │   │                           # TemperatureField, HumidityField
    │   │   │   ├── derivative/             # Gradient, Curvature, DerivativeSampler
    │   │   │   ├── dimension/              # DimensionProfile + Overworld/Nether/End profiles
    │   │   │   ├── field/                  # TectonicField, StressWarp, EpochField, ClimateField,
    │   │   │   │                           # ErosionField, SurfaceField, WarpField, CaveField,
    │   │   │   │                           # DensityField, VoronoiFractureField
    │   │   │   │   └── advanced/           # Glacial, Karst, Aeolian, Coastal, AlluvialDelta &
    │   │   │   │                           # VolcanicCaldera fields
    │   │   │   ├── geomorphology/          # HessianSolver, MultiScaleRelief, Landform
    │   │   │   │                           # Classifier/Bits/Type, Shape/Process/Environment
    │   │   │   │                           # Classifiers, FeatureEligibility
    │   │   │   ├── hydrology/              # DrainageGraph (24×24 D8), DrainageRouter,
    │   │   │   │                           # DrainagePotential, BasinField, RiverField,
    │   │   │   │                           # ChannelField, DepositionField
    │   │   │   ├── material/               # LithologyField, RockFamily, SpecialFeatureDetector,
    │   │   │   │                           # StructureSuitabilityField
    │   │   │   ├── math/                   # GeoConfig, GeoConfigNormalizer, NormalizedGeoParams,
    │   │   │   │                           # GeoSample, FieldKernel, ScalarFieldKernel
    │   │   │   ├── memory/                 # WorkerScratchpad, ScratchpadProvider,
    │   │   │   │                           # MacroFieldCache, HydrologyRegionCache
    │   │   │   ├── noise/                  # GeoNoise, NoiseDomain, SeedDerivation
    │   │   │   └── raster/                 # SectionClassifier, SectionClassification
    │   │   └── geoengineforge/             # NeoForge adapter layer
    │   │       ├── GeoEngineMod.java       # Mod entry point
    │   │       ├── config/                 # GeoEngineConfig (mod-side configuration)
    │   │       ├── debug/                  # GeoDebugCommands, GeoDebugExporter (CSV fields)
    │   │       ├── feature/                # SpecialFeatureGenerator
    │   │       ├── generator/              # GeoChunkGenerator, GeoBiomeSource
    │   │       ├── integration/            # GeoRegistry, GeoDimensionProfile, GeoEngineBootstrap
    │   │       ├── raster/                 # ChunkRasterizer, SectionWriter, HeightmapWriter,
    │   │       │                           # MaterialResolver, StrataMaterialResolver
    │   │       ├── structure/              # GeoStructureValidator
    │   │       └── surface/                # Surface rule engine: GeoSurfaceRuleEvaluator,
    │   │                                   # GeoSurfaceMaterialWriter, GeoSurfaceRuleContext,
    │   │                                   # MutableGeoSurfaceRuleContext,
    │   │                                   # SurfaceRuleCompatibility(+Report)
    │   └── resources/
    │       ├── pack.mcmeta
    │       ├── META-INF/neoforge.mods.toml
    │       └── data/
    │           ├── geoengine/
    │           │   ├── dimension/          # overworld.json, nether.json, end.json
    │           │   ├── dimension_type/     # overworld.json, nether.json, end.json
    │           │   └── lang/en_us.json     # (under data/assets/geoengine/)
    │           └── minecraft/
    │               ├── dimension/          # vanilla dimension overrides
    │               └── worldgen/           # world presets (geoengine/normal) + normal.json
    └── test/
        ├── java/com/omms/geoenginecore/test/    # 18 headless core suites (Phases 1–9 + conformance)
        └── java/com/omms/geoengineforge/test/   # Phase 4 raster pipeline + Phase 1 field-export suites
```

---

## 5. Build Prerequisites & Setup

* **Java Development Kit (JDK)**: OpenJDK 21 or higher.
* **Build Tool**: Gradle 8.10+ (managed via provided `./gradlew`).
* **Target Environment**: NeoForge `21.1.93` on Minecraft `1.21.1` (verified against `gradle.properties`).

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

## 6. Testing & Invariant Verification

All test suites are located in `src/test/java/` and execute **completely headless** without launching a Minecraft client, server, or graphical environment.

**Suite status (2026-09-21, post conformance re-pass): 19 classes, 70 tests, 0 failures, 0 skipped — all green.**

### Running the Full Test Suite
```bash
./gradlew test
```

> **Gradle caching caveat:** `test` is input-cached — if the task reports `UP-TO-DATE`, the results are from a *previous* run. Use `./gradlew test --rerun-tasks` to force a fresh execution (e.g., after touching only `TECHSPEC.md`/`README.md` will *not* re-run tests, since they are not test inputs).

### Verification Test Suite Index

| Test Class | Tests | Spec Verification Target |
| :--- | :--- | :--- |
| `Phase1CoreVerificationTest` | 4 | Configuration validation (Phase 1 acceptance), position-correct climate variation across a chunk, `SOLID` classification for deep subterranean crust (§53), density monotonicity $\frac{\partial D}{\partial y} = -1$ when $W = 0, C = 0$ (§50). |
| `Phase2And3VerificationTest` | 6 | Deposition strictly within budget ($0 \le S \le E_{\text{total}}$, §33), incision strictly within bound ($0 \le R \le R_{\text{max}}$, §28, across a full 256-block hydrology region), continuous river accumulation across the chunk boundary (|Δ$A_f$| < 1.5, |Δ$R$| < 4.0, §136), `evaluateFullColumn` sample write-back (§65), cave void sign convention ($C \ge 0$, §46), conservative section classifier — no false `AIR`/`BAND`/`SOLID` above terrain bounds (§147). |
| `Phase3BandRatioAllocationTest` | 1 | 2048-block vertical scalability ($N_{\text{band}} / N_{\text{total}} < 20\%$ across 128 sections, §2.4, §55). |
| `Phase4RasterPipelineTest` | 1 | End-to-end simulated full-chunk rasterization lifecycle with decoupled pipeline delegation, verifying bulk fast-paths and heightmap synchronization without Minecraft classes (§141–§143). |
| `Phase5LandformGrammarTest` | 4 | Multi-scale prominence discrimination (Butte vs Mesa vs Plateau, §99), priority overrides (Canyon > Valley, §186), compound Fjord detection (Glacial + Coastal + Trough, §187), curvature/prominence mountain-massif identification. |
| `Phase6And7DifferentialTest` | 2 | Additive process terms produce measurable $H_f$ deltas relative to the disabled baseline (§155); derivative alignment — slopes reflect the post-process pre-fluvial surface (§24). |
| `Phase6And7IntegrationTest` | 3 | Additive process terms active in the Overworld pipeline (Phase 7); Nether profile reconfigures the field stack — zero fluvial, active lava (Phase 6); End profile enforces Voronoi monoliths and eliminates floating slabs (Phase 6). |
| `Phase8PerformanceTest` | 3 | True zero steady-state heap allocation at source level via `ThreadMXBean` (§62), scalar/vector numerical parity (|$\Delta$| ≤ 1e-5, §71), macro-cache bitwise idempotency and eviction (§77, §160). |
| `Phase9FinalContentTest` | 4 | Structure placement gating — village rejected on sheer cliffs or shallow voids (§135, §136), dipping strata continuity across chunk boundaries without shearing (§189), deterministic feature triggers: waterfall lip detection and geothermal magma vent detection. |
| `MultiSeedMultiThreadMatrixTest` | 1 | Concurrent stress test across 5 distinct 64-bit seeds under an 8-thread pool, asserting bit-identical output arrays (§8, §156). |
| `InterRegionHydrologyContinuityTest` | 1 | Cross-seam evaluation asserting continuous D8 flow accumulation across the 256-block region boundary ($X = 255 \leftrightarrow X = 256$, |Δ$A_f$| < 0.15) via the 4-cell halo of the 24×24 routing lattice (§26, §136). |
| `NormalizedGeoConfigTest` | 1 | Configuration validation (Phase 1 acceptance, §64): extreme input sweeps over [0.0, 1.0] strictly produce valid, finite `GeoConfig` instances. |
| `Phase1DerivativeStencilTest` | 8 | §37 on the standalone `DerivativeSampler` reference: central-difference gradients at 2Δ spacing and the exact 5-point Laplacian, incl. non-uniform step handling and `step > 0` guards. |
| `Phase1ConfigValidationTest` | 21 | §64 compact-constructor validation matrix: NaN/±Infinity rejection (NaN compares false to every relational check), positive frequency/wavelength guards, non-negative lapse rate and warp amplitude, cave envelope within world bounds; plus config-hash stability across equivalent instances. |
| `Phase1ChunkSeamContinuityTest` | 2 | §136: field continuity across the 16×16 chunk boundary (|Δ| ≤ 0.01), warp *displacement* continuity ≤ 0.3 (2× the rigorous 0.144/block `GeoNoise` Lipschitz bound; coordinates include the 1.0-block identity offset), sample internal consistency (`warped = world + W`), landform ids valid (0 or 1–20). |
| `Phase1FinitenessSweepTest` | 3 | §153/§154: every scratchpad grid finite and in-domain across dimensions, seeds, and positions; section-classification invariants (`AIR`/`SOLID`/`BAND`); landform ids within the 21-type taxonomy; `UNKNOWN` proportion capped at 5% as a grammar-regression guard. |
| `CorePurityGuardTest` | 1 | §7: source-tree walk — no `net.minecraft`/`com.mojang`/`net.neoforged` imports anywhere in `com.omms.geoenginecore`. |
| `Phase1BenchmarkTest` | 1 | §221: steady-state rasterized-chunk throughput after 512-chunk JIT warmup, floored at 600 chunks/sec (re-baselined from 200); measured values recorded in `docs/BENCHMARKS.md`. |
| `Phase1FieldExportTest` (forge) | 4 | §153/§154 field exports: 24-column CSV (22 numeric fields incl. `hPre` + `landformId` + `landformName`), 48-block heightmap PNG at radius 1, 48×576 vertical-slice PNG with correct Y flip for negative `worldMinY`, point-query round-trip. |
| *Total* | **71** | *All green as of the last verified run (§2).* |

Coverage notes:

* Phase 1 acceptance criterion "deterministic tests pass" is met by `MultiSeedMultiThreadMatrixTest` (full-scratchpad bit-exact comparison, 16 threads × 5 seeds, plus a stateless-kernel re-check); "configuration validation works" by `NormalizedGeoConfigTest`, `Phase1ConfigValidationTest`, and the first `Phase1CoreVerificationTest` case.
* Phase 1 acceptance criterion "benchmark results are recorded" is satisfied by `Phase1BenchmarkTest` (committed floor) and the durable record in `docs/BENCHMARKS.md` — the §2 open housekeeping item is closed.

---

## 7. Deployment Guide

GeoEngine can be deployed to production dedicated servers, development modding workspaces, or client instances.

### 7.1 Standard Installation (Client or Dedicated Server)
1. Install [NeoForge 21.1.93+](https://neoforged.net/) for Minecraft 1.21.1.
2. Drop `geoengine-1.0.0.jar` into the server or client `mods/` folder.
3. Start the server or client. GeoEngine registers its custom codecs:
   - Chunk Generator: `geoengine:geo_chunk_generator`
   - Biome Source: `geoengine:geo_biome_source`
4. GeoEngine's dimension data (`data/geoengine/dimension/` + `dimension_type/` for Overworld, Nether, and End) and the `geoengine` world preset are registered from `src/main/resources/`.

### 7.2 Enabling Hardware SIMD Acceleration (Vector API)
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

### 7.3 World Generation Activation via Data Pack
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

### 7.4 In-Game Diagnostics & Inspection
GeoEngine provides operator commands for live diagnostics (full set in `GeoDebugCommands` / `GeoDebugExporter`):

* `/geoengine info`: Displays the active solver state, Java 21 Vector API acceleration status, and underlying dimension parameters.
* `/geoengine export slice <chunkX> <chunkZ> <filePath>`: Dumps a numerical CSV raster slice of the surface, gradient vectors, and discrete Laplacians to disk for external visualization (§153, §154).

---

## 8. Performance & Operational Benchmarks

Empirical metrics collected from the automated test suites on Java 21 (x86_64, AVX2 enabled) during the Phase 8 sealing run, re-verified on the Phase 1 conformance re-pass (2026-09-21; durable record in `docs/BENCHMARKS.md`). Values are machine- and JVM-dependent — **re-run the suite and re-record after any change to the mathematical core**.

* **Section Classification Fast-Path Ratio (§179):**
  - `AIR` sections (stratosphere bulk-fill): **$65.6\%$** (84/128 sections)
  - `SOLID` sections (deep crust bulk-fill): **$22.7\%$** (29/128 sections)
  - `BAND` sections (voxel density loop): **$11.7\%$** (15/128 sections)
  - *Result: $>88\%$ of vertical sections bypass voxel-level evaluation.*
* **Inner Loop Allocation Rate (§62):** **`0 bytes/chunk`** — verified zero additional allocation in the hot surface and density loops after JIT warm-up; re-verified on the conformance re-pass (the pre-fix steady state was 12,288 B/chunk from `Long` boxing in hydrology lookups — see `docs/BENCHMARKS.md`).
* **Rasterized Chunk Throughput (§221):** **1,253.7 chunks/sec** (committed floor 600, 2.09× headroom) on the reference machine (AMD Ryzen 9 5950X / Temurin 21) — see `docs/BENCHMARKS.md`.
* **Vector/Scalar Numerical Tolerance (§71):** parity enforced at |$H_f^s - H_f^v$| ≤ 1e-5 — the SIMD `VectorFieldKernel` is numerically interchangeable with the scalar reference `ScalarFieldKernel` within that bound.
* **Hydrological Cache Hit Latency (§80):** $O(1)$ amortized retrieval for within-region flow accumulation queries.

---

## 9. License

This project is licensed under the **MIT License**. Created by **Old Man Modding Studio**.
