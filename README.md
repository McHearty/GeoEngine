# GeoEngine

> **Bounded Deterministic Geomorphic Terrain Engine**  
> Target Platform: Minecraft Java 1.21.1 / NeoForge / Java 21

GeoEngine is a deterministic, scalar-field-based geomorphic terrain engine designed to replace Minecraft's default `NoiseBasedChunkGenerator`. It constructs procedural approximations of equilibrium landforms using interacting mathematical fields rather than hundreds of disconnected biome decorators or dynamic physical erosion simulations.

---

## 1. Architectural Invariants

GeoEngine strictly decouples geomorphic mathematics from Minecraft's voxel runtime:

```text
+===========================================================+
|                       GeoEngine Core                      |
|  Pure Java 21 | Zero Minecraft/Forge dependencies         |
|  Deterministic | Reentrant & Thread-Safe | Zero Allocations|
+===========================================================+
                             |
                             | Field evaluations (Hf, D, Classification)
                             v
+===========================================================+
|                  Minecraft / NeoForge Adapter             |
|  GeoChunkGenerator | GeoBiomeSource | SectionClassifier   |
|  StrataMaterialResolver | PalettedContainer Bulk Writers  |
+===========================================================+
```

1. **Determinism & Chunk Independence**: Every field evaluation is a pure function of $(seed, dimension, version, config, x, y, z)$. Neighboring chunk generation history, server restart state, worker thread identity, or cache status never affect output.
2. **Hydrological Feedback Integrity**: Hydrology operates strictly on the pre-carved surface $H_0 = T - E$. River incision $R$ and deposition $S$ satisfy explicit mass budgets ($0 \le S \le E_{total}$) without circular evaluation loops.
3. **Vertical Scalability ($O(N_x N_z + N_{band})$)**: Designed for worlds spanning $Y = -64$ to $Y = 1984$ (2048 blocks). Expensive 3D evaluations occur exclusively within an active boundary band ($H_f \pm \epsilon$). Solid and air sections are resolved via $O(1)$ bulk operations.
4. **Canonical Density Monotonicity**: 
   $$D(x, y, z) = H_f(x, z) - (y + W(x, y, z)) - C(x, y, z)$$
   When volumetric warp $W = 0$ and cave void $C = 0$, $\frac{\partial D}{\partial y} \equiv -1$.
5. **Zero Steady-State Hot-Loop Allocation**: All per-column and per-voxel operations reuse preallocated primitive scratchpads (`WorkerScratchpad`).

---

## 2. Feature & Subsystem Matrix (Phases 1–9)

| Subsystem | Components | Primary Responsibilities |
| :--- | :--- | :--- |
| **Crustal Baseline** | `TectonicField`, `StressWarp`, `EpochField` | Low/mid-frequency crustal uplift ($T = A u^p$), anisotropic stress warping, and age-based parameter blending. |
| **Predictive Hydrology** | `DrainageRouter`, `RiverField`, `DepositionField` | Deterministic coarse-grid flow accumulation proxy ($A_f$), saturating river incision, and mass-budget deposition ($S \le E_{total}$). |
| **Volumetric Solvers** | `WarpField`, `CaveField`, `SectionClassifier` | Altitude-damped 3D warp ($W$), overburden-gated cave voids ($C \ge 0$), and conservative section pruning (`SOLID`, `AIR`, `BAND`). |
| **Landform Classification** | `HessianSolver`, `LandformClassifier`, `LandformBits` | 2D Hessian principal curvature analysis ($\lambda_1, \lambda_2$), multi-scale relief, and 32-bit shape/process classification. |
| **Dimension Profiles** | `OverworldProfile`, `NetherProfile`, `EndProfile` | Continental Overworld, open-sky volcanic Nether (calderas, lava tubes, lava level $Y=32$), and Voronoi fracture plateaus in the End. |
| **Advanced Geomorphology** | `GlacialField`, `AeolianField`, `KarstField`, `CoastalField`, `AlluvialDeltaField` | Parabolic U-valleys, transverse dune fields, sinkholes, tower karst, wave-cut platforms, sea arches, alluvial fans, and deltas. |
| **Performance & SIMD** | `KernelProvider`, `VectorFieldKernel`, `MacroGridCache` | Dynamic Java 21 Vector API isolation, Structure-of-Arrays registers, and thread-safe bounded macro caching. |
| **Content Integration** | `StructureSuitabilityField`, `LithologyField`, `StrataMaterialResolver` | Settlement foundation rating ($F_{suit}$), stratified rock banding, sub-surface soil dressing, and waterfall/spring triggers. |

---

## 3. Package Layout

```text
com.geoengine
|
+-- core
|   +-- cache           # Thread-safe bounded segmented macro caches
|   +-- climate         # Climate classifiers, lapse rate attenuation, climate zones
|   +-- derivative      # 5-point finite difference gradient and Laplacian operators
|   +-- dimension       # Overworld, Nether, and End dimension profile implementations
|   +-- feature         # Special deterministic feature detectors (waterfalls, springs)
|   +-- field           # Tectonic, stress, epoch, climate, erosion, warp, and cave fields
|   |   +-- advanced    # Glacial, aeolian, karst, coastal, and delta field modules
|   +-- geomorphology   # Hessian eigenvalue solvers, landform grammar, eligibility masks
|   +-- hydrology       # Predictive coarse drainage routing, incision, and deposition
|   +-- material        # Rock family classifications and continuous lithology fields
|   +-- math            # Configuration records, sample carriers, and kernel interfaces
|   +-- memory          # Thread-isolated worker scratchpads and object pools
|   +-- noise           # Coordinate hashing, seed derivation, and continuous gradient noise
|   +-- raster          # Conservative section boundary classifiers
|   +-- simd            # Java 21 Vector API bridges and Structure-of-Arrays buffers
|   +-- structure       # Foundation stability and settlement suitability rating
|
+-- forge
    +-- generator       # ChunkGenerator and BiomeSource adapters for Minecraft 1.21.1
    +-- raster          # BlockState resolvers and stratified surface dressers
```

---

## 4. Build and Prerequisites

* **JDK**: OpenJDK 21 or later.
* **Build System**: Gradle 8.x with NeoGradle.
* **Platform Target**: Minecraft Java Edition 1.21.1 / NeoForge.
* **Optional SIMD Flag**: `--add-modules jdk.incubator.vector` (Vector API acceleration is dynamically loaded; runs on reference scalar kernel if omitted).
