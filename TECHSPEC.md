# GeoEngine

## Bounded Deterministic Geomorphic Terrain Engine

### Technical Development Specification

### Target Platform: Minecraft Java 1.21.1 / NeoForge / Java 21

---

# 1. Document Purpose

GeoEngine is a bespoke procedural terrain-generation engine designed to replace Minecraft's conventional `NoiseBasedChunkGenerator` terrain model with a deterministic scalar-field-based geomorphic approximation.

The engine is intended to produce large-scale terrain that exhibits recognizable geomorphological structure through interacting mathematical fields rather than through hundreds of independently authored terrain generators.

The system is explicitly **not** a geological simulation.

It is a:

> **Bounded Deterministic Geomorphic Approximation**

The distinction is fundamental.

GeoEngine does not simulate geological history, particle transport, fluid dynamics, tectonic plates, glacial mass balance, sediment conservation, or erosion through time.

Instead, it constructs a deterministic approximation of the equilibrium terrain that those processes could plausibly produce.

For a fixed:

* world seed,
* dimension,
* generator version,
* configuration,
* coordinate,

the engine produces the same result regardless of chunk-generation order, worker thread, server restart, or neighboring chunk state.

The governing property is therefore:

$$
F(seed,dimension,version,config,x,y,z)
=
constant
$$

for identical inputs.

For surface generation:

$$
H_f =
F(seed,dimension,version,config,x,z)
$$

For volumetric terrain:

$$
D =
F(seed,dimension,version,config,x,y,z)
$$

No mutable geological state is required for correctness.

---

# 2. Design Objectives

GeoEngine has the following primary objectives.

## 2.1 Geomorphic coherence

Terrain should exhibit coherent:

* mountains,
* plateaus,
* ridges,
* valleys,
* basins,
* plains,
* escarpments,
* canyons,
* dunes,
* volcanic systems,
* coastal forms,
* glacial forms,
* fluvial systems,
* tectonic structures,
* weathering structures.

These should emerge from mathematical field combinations.

---

## 2.2 Determinism

Generation must be independent of:

* chunk order,
* neighboring chunk generation,
* worker thread,
* cache state,
* server restart,
* previous player activity.

Caches may accelerate generation but must never become sources of truth.

---

## 2.3 Chunk independence

A chunk must not require another chunk to have already been generated.

World-space field queries may sample coordinates outside the current chunk for:

* derivatives,
* finite differences,
* macro-field interpolation,
* drainage analysis,
* domain warping.

This does not constitute a chunk dependency because those queries evaluate pure mathematical functions directly.

---

## 2.4 Vertical scalability

The target vertical range is:

$$
Y_{min}=-64
$$

$$
Y_{max}=1984
$$

giving:

$$
2048
$$

vertical blocks.

A 16-block Minecraft section therefore produces:

$$
2048/16=128
$$

sections per vertical column.

GeoEngine must not evaluate expensive 3D terrain mathematics across all 128 sections.

The computational objective is:

$$
O(N_xN_z+N_{band})
$$

rather than:

$$
O(N_xN_zN_y)
$$

for the geomorphic solver.

This means:

> Generation cost should be proportional to the evaluated surface/detail band rather than the complete world height.

This does not mean the complete Minecraft chunk lifecycle has constant complexity with respect to world height. Section bookkeeping, lighting, structures, heightmaps, serialization, and other engine systems may still scale with vertical dimensions.

---

# 3. Non-Objectives

GeoEngine will not attempt to provide:

* physically accurate geology,
* real-time erosion,
* real-time water-flow simulation,
* particle sediment transport,
* exact watershed hydrodynamics,
* tectonic plate simulation,
* conservation of geological mass,
* scientifically accurate climate prediction,
* scientifically accurate atmospheric lapse rates,
* perfect reproduction of real-world landforms,
* unique procedural algorithms for every named landform.

The landform system is a classification and realization grammar rather than a one-generator-per-landform architecture.

---

# 4. Governing Engineering Principles

The system follows this priority hierarchy:

1. Correctness
2. Epistemic honesty
3. Determinism
4. Thread safety
5. Security/integration isolation
6. Operational realism
7. Performance
8. Maintainability
9. Feature breadth

Performance optimizations must never become correctness dependencies.

---

# 5. Core Architectural Model

GeoEngine is divided into two conceptual domains.

```text
+===========================================================+
|                       GeoEngine Core                      |
|                                                           |
|  Pure Java                                                |
|  No Minecraft references                                  |
|  No NeoForge references                                   |
|  Deterministic                                            |
|  Thread-safe                                               |
|  Allocation-controlled                                    |
|                                                           |
|  Configuration                                             |
|  Seed Domains                                              |
|  Noise                                                     |
|  Tectonics                                                 |
|  Stress                                                    |
|  Epoch                                                     |
|  Climate                                                   |
|  Erosion                                                   |
|  Hydrology                                                 |
|  Deposition                                                |
|  Surface                                                   |
|  Warp                                                      |
|  Caves                                                     |
|  Density                                                   |
|  Classification                                            |
+===========================================================+
                           |
                           | deterministic field results
                           v
+===========================================================+
|                  Minecraft / NeoForge Adapter             |
|                                                           |
|  GeoChunkGenerator                                        |
|  GeoBiomeSource                                           |
|  SectionClassifier                                        |
|  ChunkRasterizer                                          |
|  Heightmap Integration                                    |
|  PalettedContainer Integration                            |
|  Material Resolution                                      |
|  Structure Integration                                    |
|  Debug/Diagnostics                                        |
+===========================================================+
```

The core must be compilable and testable without Minecraft.

---

# 6. Package Architecture

Recommended package layout:

```text
com.geoengine
|
+-- core
|   |
|   +-- math
|   |   +-- GeoConfig
|   |   +-- GeoSample
|   |   +-- FieldKernel
|   |   +-- ScalarFieldKernel
|   |   +-- VectorFieldKernel
|   |
|   +-- noise
|   |   +-- GeoNoise
|   |   +-- NoiseDomain
|   |   +-- SeedDerivation
|   |
|   +-- field
|   |   +-- TectonicField
|   |   +-- StressWarp
|   |   +-- EpochField
|   |   +-- ClimateField
|   |   +-- ErosionField
|   |   +-- HydrologyField
|   |   +-- DepositionField
|   |   +-- SurfaceField
|   |   +-- WarpField
|   |   +-- CaveField
|   |   +-- DensityField
|   |
|   +-- derivative
|   |   +-- DerivativeSampler
|   |   +-- Gradient
|   |   +-- Curvature
|   |
|   +-- geomorphology
|   |   +-- LandformClassifier
|   |   +-- ShapeClassifier
|   |   +-- ProcessClassifier
|   |   +-- EnvironmentClassifier
|   |
|   +-- hydrology
|   |   +-- DrainagePotential
|   |   +-- DrainageRouter
|   |   +-- ChannelField
|   |   +-- BasinField
|   |
|   +-- climate
|   |   +-- ClimateClassifier
|   |   +-- TemperatureField
|   |   +-- HumidityField
|   |
|   +-- memory
|       +-- WorkerScratchpad
|       +-- ScratchpadProvider
|       +-- MacroFieldCache
|       +-- HydrologyRegionCache
|
+-- forge
    |
    +-- generator
    |   +-- GeoChunkGenerator
    |   +-- GeoBiomeSource
    |
    +-- raster
    |   +-- SectionClassifier
    |   +-- ChunkRasterizer
    |   +-- SectionWriter
    |   +-- MaterialResolver
    |   +-- HeightmapWriter
    |
    +-- integration
    |   +-- GeoEngineBootstrap
    |   +-- GeoRegistry
    |   +-- GeoDimensionProfile
    |
    +-- debug
        +-- GeoDebugExporter
        +-- GeoDebugCommands
```

---

# 7. Dependency Rule

The following dependency is forbidden:

```text
core -> Minecraft
core -> NeoForge
```

The following is permitted:

```text
forge -> core
```

Minecraft-specific types must never appear in:

* `GeoConfig`
* `GeoSample`
* `FieldKernel`
* `ScalarFieldKernel`
* field implementations,
* derivative implementations,
* hydrology implementations,
* classification implementations.

---

# 8. Determinism Contract

The canonical deterministic input is:

$$
K =
(seed,
dimension,
generatorVersion,
configHash,
x,
y,
z)
$$

Surface functions use:

$$
K_s =
(seed,
dimension,
generatorVersion,
configHash,
x,
z)
$$

No generation result may depend on:

* chunk generation order,
* cache contents,
* Java thread identity,
* wall-clock time,
* random mutable state,
* neighboring chunk objects,
* loaded entity state.

---

# 9. Seed Domain Separation

Every major field receives an independent deterministic seed domain.

Example:

```text
WORLD_SEED
    |
    +-- TECTONIC
    +-- STRESS
    +-- EPOCH
    +-- CLIMATE_TEMPERATURE
    +-- CLIMATE_HUMIDITY
    +-- EROSION
    +-- HYDROLOGY
    +-- DEPOSITION
    +-- WARP
    +-- CAVE
    +-- LANDFORM
```

A field must not reuse another field's raw noise stream merely because the same world seed is available.

Each domain is derived deterministically from:

$$
seed_{field}=Hash(worldSeed,dimensionId,domainSalt,generatorVersion)
$$

This prevents accidental correlation between fields.

---

# 10. Noise Repetition

Frequency selection must not be described as eliminating repetition.

For deterministic lattice/permutation-based noise, finite implementation periods may exist.

Using incommensurate or awkward frequency ratios can reduce visible interference patterns, but does not mathematically guarantee an infinite non-repeating field.

Therefore:

> Frequency ratios are a pattern-management technique, not a non-repetition proof.

Large-scale repetition must be empirically tested over representative world distances.

---

# 11. Coordinate System

All core fields operate in absolute world coordinates.

No field receives:

```text
localChunkX
localChunkZ
```

as its fundamental coordinate.

The Minecraft adapter converts:

```text
chunkOrigin + localCoordinate
```

into world coordinates before calling the core.

This guarantees chunk-boundary continuity.

---

# 12. Vertical Normalization

For any vertical function requiring normalized altitude:

$$
a =
clamp
\left(
\frac{y-Y_{min}}
{Y_{max}-Y_{min}},
0,1
\right)
$$

This replaces unsafe expressions such as:

$$
1/y
$$

or any function that becomes singular at zero.

Dimension profiles may define different normalization behavior.

---

# 13. Macro-Tectonic Field

The tectonic field represents broad crustal relief.

The base architecture uses multiple frequency bands.

Example frequencies:

```text
fLow  ≈ 0.0005
fA    ≈ 0.0017
fB    ≈ 0.0023
```

These values are configuration parameters, not universal constants.

The tectonic field should be represented as a weighted combination of deterministic noise domains.

The lower frequency establishes continental-scale bias.

The two higher frequencies establish major structural organization.

---

# 14. Tectonic Uplift Remapping

Directly squaring bipolar noise is prohibited unless the resulting behavior is explicitly desired.

Given:

$$
n\in[-1,1]
$$

the expression:

$$
n^2
$$

makes both positive and negative regions positive.

Instead, uplift should generally operate on a nonnegative uplift field.

For example:

$$
u=max(0,n)
$$

followed by:

$$
T_{uplift}=A u^p
$$

with:

$$
p>1
$$

This concentrates uplift toward massif centers.

Separate depression energy may be used for ocean basins and tectonic lows.

---

# 15. Bichromatic Tectonic Function

The bichromatic tectonic field combines the major tectonic bands.

Conceptually:

$$
T_b =
w_0T_0+
w_1T_1+
w_2T_2
$$

where:

* $T_0$ = very-low-frequency continental structure,
* $T_1$ = primary tectonic band,
* $T_2$ = secondary tectonic band.

The field must be normalized before physical interpretation.

The implementation must avoid uncontrolled amplitude stacking.

The configuration must guarantee:

$$
|T_b|\le T_{max}
$$

after remapping.

---

# 16. Anisotropic Stress Warp

Stress introduces directional deformation into the tectonic domain.

The transform may use:

$$
x'=x+W_x(x,z)
$$

$$
z'=z+W_z(x,z)
$$

A simple directional component can use:

$$
W_x=A_s\sin(\omega z)
$$

but the engine must not treat:

$$
\omega<f
$$

as sufficient protection against structural fragmentation.

The actual coordinate transformation must be bounded.

A useful engineering condition is:

$$
\left|
\frac{\partial W_x}{\partial x}
\right|,
\left|
\frac{\partial W_x}{\partial z}
\right|,
\left|
\frac{\partial W_z}{\partial x}
\right|,
\left|
\frac{\partial W_z}{\partial z}
\right|
<
J_{max}
$$

with a conservative configured margin.

The goal is to prevent the warp from becoming a high-frequency coordinate distortion.

---

# 17. Epoch Field

A low-frequency age field controls the relative strength of geomorphic processes.

The field represents:

```text
Young
 |
 | high tectonic influence
 | low erosion
 | low weathering
 |
 v
Old
 |
 | lower tectonic influence
 | stronger erosion
 | stronger deposition
 | stronger weathering
```

The age transition must be smooth.

An age mask is therefore evaluated before derivatives.

---

# 18. Pre-Derivative Parameter Blending

The engine must not independently derive:

* young surface,
* old surface,
* young derivatives,
* old derivatives,

and then interpolate the results.

Instead:

```text
Age Field
    |
    v
Blend Process Parameters
    |
    v
Evaluate Combined Surface
    |
    v
Calculate Derivatives
```

This prevents derivative discontinuities.

---

# 19. Climate Field

Climate consists of at least:

* temperature,
* humidity.

These are horizontal fields.

Temperature may additionally use a vertical lapse component.

The lapse model is an approximation for terrain classification and process weighting.

It must not be described as a physically valid atmospheric model across 2048 vertical blocks.

---

# 20. Climate Multiplier

The climate multiplier is bounded:

$$
K\in[K_{min},K_{max}]
$$

The default target range is:

$$
K\in[0.6,1.4]
$$

The multiplier modifies process strength.

It must not replace tectonic structure.

The invariant is:

> Climate modifies geomorphic processes; it does not erase tectonic identity.

---

# 21. Climate Classification

GeoEngine may expose climate groups resembling:

```text
A  Tropical
B  Dry
C  Temperate
D  Continental
E  Polar
```

These are procedural control classes.

They should not be interpreted as a complete scientific Köppen implementation unless the required temperature/precipitation semantics are actually implemented.

High-altitude conditions may independently classify as alpine/polar.

---

# 22. Erosion Field

The erosion field represents long-term surface lowering.

It combines:

* climate,
* age,
* slope,
* curvature,
* relief,
* lithological resistance,
* exposure,
* drainage proximity.

A generic form is:

$$
E =
E_0
F_{climate}
F_{age}
F_{slope}
F_{curvature}
F_{material}
$$

with explicit bounds.

---

# 23. Pre-Carve Surface

The first stable surface is:

$$
H_0=T-E
$$

This is the surface on which derivatives should initially be evaluated.

It is important that this surface is distinct from the final surface.

---

# 24. Hydrological Circularity Resolution

The following construction is forbidden:

$$
H^*=T-E-R
$$

while simultaneously defining:

$$
R=f(\nabla^2H^*)
$$

in a single direct evaluation.

That is circular.

GeoEngine instead uses:

```text
T
 |
 v
Erosion
 |
 v
H0 = T - E
 |
 +----> derivatives
 |
 +----> drainage analysis
             |
             v
        river incision R
             |
             v
        H* = H0 - R
```

If future development requires feedback from $H^*$, it must use an explicitly bounded iterative solver with a fixed iteration count.

The default implementation remains single-pass.

---

# 25. Predictive Hydrology

GeoEngine does not perform real-time water simulation.

Instead it computes a deterministic drainage approximation.

The system may use:

* slope,
* curvature,
* basin potential,
* climate runoff,
* deterministic coarse drainage routing,
* flow accumulation proxies,
* channel convergence.

---

# 26. Drainage Graph

A robust implementation should use a coarse deterministic drainage grid.

Each coarse cell evaluates its terrain elevation and selects a deterministic downhill neighbor.

Conceptually:

```text
+-----+-----+-----+
|     |     |     |
|  \  |  v  |  /  |
+-----+-----+-----+
|     |     |     |
|  -> | basin | <-|
+-----+-----+-----+
|     |     |     |
|  /  |  ^  |  \  |
+-----+-----+-----+
```

The coarse graph establishes:

* drainage direction,
* basin connectivity,
* approximate accumulation,
* confluences,
* outlets.

It is still deterministic and chunk-independent because every elevation query is a pure world-space function.

A cache may retain graph results, but cache state must not affect correctness.

---

# 27. Flow Accumulation Proxy

Let:

$$
A_f
$$

represent deterministic accumulated upstream contribution.

It is not a physical flow quantity.

It is a procedural scalar indicating the relative importance of a drainage path.

River strength can depend on:

$$
A_f
$$

combined with:

$$
|\nabla H_0|
$$

and climate runoff.

---

# 28. River Incision

River incision is bounded.

A generic model is:

$$
R =
min(R_{base},R_{max})
$$

where:

$$
R_{base}
=
F(A_f)
F_{slope}
F_{climate}
F_{channel}
$$

and:

$$
R_{max}
=
D_{max}
F_{slope}
$$

The resulting field satisfies:

$$
R\ge0
$$

and:

$$
R\le R_{max}
$$

---

# 29. Channel Width

Channel width is derived from accumulated drainage strength.

A simplified relationship is:

$$
width =
width_{min}
+
width_{scale}F(A_f)
$$

The function must be saturating.

Without saturation, large drainage basins could produce absurdly wide channels.

---

# 30. Meandering

Meandering is not produced by arbitrary noise added to a river.

A channel centerline must remain generally consistent with downhill drainage.

Deterministic lateral perturbation may modify the path, but it must be bounded by:

* slope,
* channel width,
* valley width,
* terrain constraints.

The objective is a procedural meander rather than physical fluid simulation.

---

# 31. River Feature Grammar

The hydrology layer can derive:

```text
River
Stream
Tributary
Confluence
Anabranch
Braided Channel
Oxbow
Point Bar
Cut Bank
Levee
Floodplain
Crevasse Splay
Delta
Alluvial Fan
```

These are generated as relationships within the drainage graph rather than independent noise patches.

---

# 32. Deposition

Deposition is an equilibrium approximation.

It is not particle tracking.

The total deposition field is:

$$
S=S_{basin}+S_{fluvial}+S_{coastal}+S_{aeolian}
$$

subject to configured bounds.

---

# 33. Deposition Budget

The engine defines an explicit removal budget:

$$
E_{total}=E_{weathering}+R_{incision}
$$

Then:

$$
S_{max}=E_{total}
$$

and:

$$
0\le S\le S_{max}
$$

This is an engineering mass-budget proxy.

It must not be described as true geological conservation.

---

# 34. Basin Deposition

Basin deposition is strongest when:

* slope is low,
* local terrain is concave,
* elevation is relatively low,
* sediment availability is high.

A conceptual form is:

$$
S_{basin}
=
S_{max}
F_{flat}
F_{basin}
F_{sediment}
F_{age}
$$

---

# 35. Flatness

Flatness should not be derived solely from low gradient.

Use both:

$$
|\nabla H|
$$

and:

$$
|\nabla^2H|
$$

A surface with low gradient but high curvature is not necessarily suitable for structures.

---

# 36. Final Surface

After erosion, river incision, and deposition:

$$
H_f=H_0-R+S
$$

or equivalently:

$$
H_f=T-E-R+S
$$

This is the final macro terrain surface.

---

# 37. Derivative System

GeoEngine requires:

$$
\nabla H =
\left(
\frac{\partial H}{\partial x},
\frac{\partial H}{\partial z}
\right)
$$

and:

$$
\nabla^2H=
\frac{\partial^2H}{\partial x^2}
+
\frac{\partial^2H}{\partial z^2}
$$

Finite differences are preferred initially because they are:

* deterministic,
* simple,
* testable,
* portable,
* SIMD-friendly.

---

# 38. Macro Sampling Resolution

A 16×16 Minecraft chunk can use a 4-block horizontal macro spacing.

The central chunk therefore requires approximately:

```text
16 / 4 = 4
```

intervals per dimension.

Derivative evaluation requires a halo.

The implementation should therefore use a 6×6 node grid for a 16×16 chunk when the exact sampling layout is based on four-block spacing plus one derivative sample on each side.

The exact coordinate convention must be fixed before implementation.

The grid must be defined in **world coordinates**, not chunk-relative mathematical space.

---

# 39. Macro Grid Contract

The macro grid is not itself the final 16×16 surface.

It is a coarse representation of expensive fields.

Example:

```text
6x6 world-space nodes
       |
       v
interpolation
       |
       v
16x16 final surface
```

The engine may cache:

* tectonic field,
* potential surface,
* other expensive stable fields.

It should not automatically cache every derived field.

---

# 40. Macro Sampling Performance

Sampling every block column would require:

$$
16\times16=256
$$

macro evaluations.

A 4-block grid requires approximately:

$$
6\times6=36
$$

nodes including halo.

That is approximately an 86% reduction in macro sample locations for this particular grid arrangement.

It should not be described as exactly "75% less computation," because:

* halo samples exist,
* interpolation has cost,
* derivatives have cost,
* some fields may still be evaluated at higher resolution.

---

# 41. Volumetric Warp

The volumetric warp:

$$
W(x,y,z)
$$

introduces controlled 3D deviation from a pure heightfield.

Density is:

$$
D=
H_f-(y+W)-C
$$

Positive $W$ therefore modifies the effective vertical coordinate.

The sign convention must remain consistent throughout the engine.

---

# 42. Warp Objectives

$W$ exists to introduce:

* micro-overhangs,
* irregular cliff faces,
* rock grain,
* non-planar surfaces,
* controlled natural roughness.

It must not be used as the primary mechanism for:

* arches,
* caves,
* large tunnels,
* major valleys,
* major cliffs.

Those require explicit geometry or cave fields.

---

# 43. Altitude Damping

Define:

$$
a=
clamp
\left(
\frac{y-Y_{min}}
{Y_{max}-Y_{min}},
0,1
\right)
$$

Then:

$$
D_W(a)=1-a^p
$$

and:

$$
W_{effective}=W_{raw}D_W(a)
$$

The exponent $p$ is configurable.

No division by $y$ is permitted.

---

# 44. Warp Amplitude Bound

The warp must satisfy a configurable bound:

$$
|W|\le W_{max}
$$

Preferably:

$$
W_{max}
=
F(localMaterialThickness,
slope,
altitude,
geomorphicClass)
$$

This reduces the probability of unsupported floating geometry.

---

# 45. Cave Field

The cave field:

$$
C(x,y,z)
$$

represents volumetric voids.

Cave generation must not rely solely on ordinary thresholded 3D noise.

Pure threshold noise tends to produce disconnected "Swiss cheese."

Cave architecture may combine:

* tunnel fields,
* ridged noise,
* cellular distance,
* domain warping,
* cavern chambers,
* deterministic cave paths.

---

# 46. Cave Sign Convention

The density equation is:

$$
D=
H_f-y-W-C
$$

Therefore:

* larger $C$ means more void,
* $C=0$ means no cave contribution.

The cave field must satisfy:

$$
C\ge0
$$

---

# 47. Cave Surface Protection

Caves must not blindly operate everywhere.

Define overburden:

$$
O=H_f-y
$$

A cave eligibility mask can be:

$$
M_{cover}
=
smoothstep
(Cover_{min},Cover_{max},O)
$$

Then:

$$
C_{effective}=C_{raw}M_{cover}
$$

This prevents caves from randomly breaking the surface unless explicitly configured as:

* sea caves,
* arches,
* sinkholes,
* exposed caverns,
* volcanic tubes.

---

# 48. Cave Classes

The cave system may distinguish:

```text
Tunnel
Chamber
Cavern
Vertical Shaft
Sea Cave
Lava Tube
Karst Cave
Glacial Cave
Surface Shelter
Arch Void
```

These are masks and realizations, not separate random terrain generators.

---

# 49. Final Density

The canonical terrain density is:

$$
D(x,y,z)
=
H_f(x,z)
-
(y+W(x,y,z))
-
C(x,y,z)
$$

Material classification is:

$$
D>0 \Rightarrow Solid
$$

$$
D\le0 \Rightarrow Air
$$

Additional fluid rules may classify air volumes below sea level or within dimension-specific fluid regions.

---

# 50. Density Monotonicity

When:

$$
W=0
$$

and:

$$
C=0
$$

then:

$$
D=H_f-y
$$

Therefore:

$$
\frac{\partial D}{\partial y}=-1
$$

This property provides an extremely useful test.

Any scalar implementation violating this when W/C are disabled is incorrect.

---

# 51. Surface Band

High-cost 3D evaluation occurs only inside a configurable band:

$$
Band =
[H_f-\epsilon,H_f+\epsilon]
$$

where $\epsilon$ may initially be:

$$
16
$$

blocks.

The band must include sufficient margin for:

* W,
* surface caves,
* overhangs,
* material transition,
* feature placement.

---

# 52. Section Classification

Every vertical section is classified as:

```text
SOLID
AIR
BAND
```

The classifier uses conservative bounds.

For a section:

$$
[Y_s,Y_e)
$$

if it is provably below the terrain envelope and outside cave eligibility:

```text
SOLID
```

If it is provably above the terrain envelope:

```text
AIR
```

Otherwise:

```text
BAND
```

---

# 53. Conservative Classification Rule

False positives are unacceptable.

If the classifier says:

```text
SOLID
```

the section must actually contain no air that the engine intends to generate.

If the classifier says:

```text
AIR
```

the section must actually contain no solid terrain.

A false BAND classification is acceptable because it merely performs extra work.

Therefore:

> Section classification must be conservative.

---

# 54. Deep Cave Exception

The surface band alone cannot determine whether deep caves exist.

If caves can extend far below the surface, the classifier needs an additional cave occupancy test.

Possible strategies:

* coarse cave occupancy field,
* region-level cave activation mask,
* conservative cave envelope,
* explicit cave-depth bounds.

A section must not be marked solid merely because it is far below the surface.

---

# 55. Bulk Section Fast Path

A valid SOLID section may be filled using a bulk operation.

A valid AIR section may likewise be filled as air.

This avoids:

$$
16\times16\times16=4096
$$

individual voxel evaluations.

For a 2048-block world, this is critical.

---

# 56. Surface Rasterization

BAND sections receive direct voxel density evaluation.

The rasterizer evaluates:

$$
D(x,y,z)
$$

only where required.

The surface macro grid supplies:

$$
H_f(x,z)
$$

so the expensive 2D geomorphological pipeline is not recomputed for every voxel.

---

# 57. Heightmap Architecture

The complete 16×16 final surface grid must be calculated before downstream consumers depend on terrain height.

Conceptually:

```text
Field Solver
     |
     v
Hf[256]
     |
     +------> Heightmap population
     |
     +------> Section classification
     |
     +------> Surface rasterization
     |
     +------> Biome suitability
```

Heightmap semantics must be implemented according to the exact Minecraft heightmap type.

The engine must not assume all heightmaps are interchangeable.

---

# 58. Heightmap Requirements

At minimum, the integration must correctly account for the heightmaps required by the generation stage, including relevant:

* world surface,
* ocean floor,
* world-generation heightmaps.

The adapter must either:

1. populate them explicitly, or
2. ensure Minecraft's supported recalculation mechanism produces the correct result before dependent stages execute.

The exact API mechanism belongs in the Forge adapter rather than the core.

---

# 59. PalettedContainer Integration

The core engine never touches `PalettedContainer`.

Only the Minecraft adapter may do so.

The architecture is:

```text
GeoEngine
   |
   v
Voxel Result
   |
   v
SectionWriter
   |
   v
Minecraft Section
   |
   v
PalettedContainer
```

Internal palette representation must remain encapsulated.

---

# 60. Section Locking

Minecraft section/container access may involve locking semantics.

The implementation must determine, through the actual 1.21.1 runtime/API:

* when exclusive mutation is required,
* which thread owns the section,
* which generation phase owns it,
* whether bulk mutation is supported,
* whether direct backing-storage access is safe.

No assumption that "one generation worker always owns the chunk" may substitute for verification.

---

# 61. Palette Allocation

Repeatedly inserting arbitrary block states may force palette expansion.

The rasterizer should therefore:

1. determine the material palette needed by a section,
2. minimize the number of distinct states,
3. resolve `BlockState` instances before entering the hot loop,
4. use the supported section mutation path,
5. benchmark palette growth.

Direct manipulation of internal palette bit storage should only be introduced if profiling demonstrates that the supported API is a material bottleneck.

If required, it belongs behind:

```text
InternalSectionWriter
```

and nowhere else.

---

# 62. Zero Allocation Target

The implementation target is:

> Zero steady-state heap allocation inside the mathematical and voxel inner loops.

This excludes allocations made by:

* Minecraft,
* chunk construction,
* task infrastructure,
* registry initialization,
* cache management,
* section initialization.

The claim must be verified using a profiler or allocation benchmark.

---

# 63. `GeoConfig`

`GeoConfig` is immutable.

The configuration must contain at minimum:

```text
worldMinY
worldMaxY
seaLevel

tectonicFrequency
tectonicAmplitude

stressFrequency
stressAmplitude

climateMin
climateMax

maxWarpAmplitude
warpAltitudeExponent

surfaceBandRadius

erosion parameters
river parameters
deposition parameters
cave parameters
```

---

# 64. Configuration Validation

The configuration constructor must reject invalid configurations.

Examples:

$$
worldMinY < worldMaxY
$$

$$
stressFrequency < tectonicFrequency
$$

$$
climateMin\ge0
$$

$$
climateMax\ge climateMin
$$

$$
surfaceBandRadius>0
$$

$$
maxWarpAmplitude\ge0
$$

Additional validation must check:

* finite floating-point values,
* non-NaN values,
* positive wavelengths,
* valid dimension ranges,
* valid deposition limits,
* valid cave cover depths,
* valid noise amplitudes.

Frequency ordering is a design constraint, not proof of warp safety.

Warp Jacobian/amplitude constraints must also be validated.

---

# 65. `GeoSample`

`GeoSample` is a mutable scratch structure.

It must not be a record.

Suggested fields:

```text
tectonic
stressX
stressZ

age
temperature
humidity

erosion
riverIncision
deposition

potentialSurface
finalSurface

gradientX
gradientZ
gradientMagnitude
laplacian

warp
caveDensity
finalDensity

classificationBits
```

Additional fields may be added if they are repeatedly reused by the hot path.

---

# 66. `GeoSample.reset()`

Resetting every field is not inherently required.

If an evaluation guarantees that every field consumed by downstream code is overwritten, a reset is unnecessary.

If the object is exposed across evaluation stages where stale values are possible, explicit reset is appropriate.

The implementation should therefore prefer:

> Complete overwrite contracts over unconditional full-object clearing.

This avoids unnecessary writes.

---

# 67. `FieldKernel`

The interface separates 2D surface evaluation from 3D density evaluation.

Conceptually:

```text
FieldKernel
|
+-- evaluateSurfaceGrid(...)
|
+-- evaluateDensity(...)
```

The interface must never expose Minecraft types.

---

# 68. Scalar Reference Kernel

`ScalarFieldKernel` is the numerical authority.

All correctness tests are initially written against it.

It must prioritize:

* deterministic behavior,
* simple control flow,
* transparent mathematics,
* predictable floating-point behavior.

The scalar implementation is not a temporary prototype.

It is the reference implementation against which optimized implementations are validated.

---

# 69. Vector Kernel

`VectorFieldKernel` is optional.

It must implement the same mathematical contract.

It must not be required for:

* mod loading,
* world creation,
* dedicated servers,
* clients,
* correctness.

If unavailable:

```text
VectorFieldKernel
       |
       v
ScalarFieldKernel
```

or the scalar kernel is selected directly.

---

# 70. Java Vector API

Java 21's Vector API is an incubating module.

Therefore the engine must not make:

```text
jdk.incubator.vector
```

a mandatory class-loading dependency.

The safest architecture is a separate vector implementation isolated from the scalar implementation.

The deployment path must allow the JVM to run the scalar implementation without Vector API availability.

---

# 71. SIMD Correctness

For representative inputs:

$$
D_s=D_{scalar}
$$

and:

$$
D_v=D_{vector}
$$

must satisfy:

$$
|D_s-D_v|<\epsilon
$$

The same comparison should exist for:

* tectonic,
* erosion,
* surface,
* derivatives,
* density.

The tolerance must be established empirically.

---

# 72. Floating-Point Determinism

GeoEngine should avoid unnecessary floating-point behavior that differs between scalar and vector paths.

Particular care is required around:

* transcendental functions,
* reductions,
* summation order,
* fused operations,
* NaN propagation,
* infinity,
* signed zero.

Exact bit-identical SIMD output is not mandatory if a bounded numerical tolerance is part of the contract.

---

# 73. `WorkerScratchpad`

The scratchpad contains reusable primitive storage.

Minimum structure:

```text
WorkerScratchpad
|
+-- GeoSample
|
+-- macroTectonic[36]
+-- macroPotentialSurface[36]
|
+-- finalSurfaceGrid[256]
+-- laplacianGrid[256]
|
+-- densityBand[4096]
```

The arrays must be reused.

---

# 74. Scratchpad Ownership

A scratchpad is owned by one worker thread.

It must never be shared concurrently.

Recommended conceptual lifecycle:

```text
Worker
 |
 +-- obtains scratchpad
 |
 +-- clears/reinitializes required ranges
 |
 +-- evaluates chunk
 |
 +-- releases logically
 |
 +-- reuses scratchpad
```

No synchronization is necessary inside a worker's scratchpad.

---

# 75. Scratchpad Sizing

The initial:

$$
6\times6
$$

macro grid supports derivative halo requirements under the selected four-block spacing.

The exact indexing scheme must be documented.

For example, every grid cell must have a deterministic world coordinate:

$$
x=x_0+i\Delta
$$

$$
z=z_0+j\Delta
$$

where:

$$
\Delta=4
$$

for the initial macro resolution.

---

# 76. Chunk Boundary Sampling

A derivative near a chunk edge must sample the world-space coordinate outside the chunk.

For example:

```text
Chunk A               Chunk B
|----------------|----------------|
             x=15 | x=16
                  |
        derivative neighborhood
             <----+---->
```

The field sampler directly evaluates the outside coordinate.

It must not ask Chunk B for its already-generated data.

---

# 77. Macro Cache

The macro cache may store:

```text
(seed/domain/config/coordinate)
    ->
field value
```

or chunk/region macro grids.

The cache is an optimization only.

Cache eviction must not change output.

The same chunk generated:

```text
with cache
```

and:

```text
without cache
```

must produce identical terrain.

---

# 78. Cache Lifecycle

Static global caches are discouraged.

A cache must be scoped to:

* world,
* generator instance,
* dimension,
* seed/config identity.

This prevents stale data crossing worlds.

---

# 79. Cache Eviction

A bounded cache is required.

Eviction policy may be:

* LRU,
* segmented LRU,
* bounded ring,
* concurrent map plus eviction queue.

The first implementation should favor correctness and observability over sophisticated lock-free machinery.

"Lock-free" is not automatically faster.

---

# 80. Hydrology Region Cache

If coarse drainage routing is implemented, a region cache may store:

* coarse elevation,
* flow direction,
* basin ID,
* accumulation,
* channel strength.

The cache must include enough deterministic identity to prevent collisions between:

* worlds,
* dimensions,
* seeds,
* generator versions,
* configuration variants.

---

# 81. Region Dependency Rule

Hydrology may query neighboring coarse cells mathematically.

This does not violate chunk independence.

The prohibited behavior is:

```text
Generate chunk A
   |
   v
mutate river state
   |
   v
chunk B reads state from A
```

The permitted behavior is:

```text
chunk B
  |
  v
query pure world-space hydrology field
  |
  v
same result regardless of A
```

---

# 82. Biome Source

`GeoBiomeSource` is responsible for converting geomorphic/climate classification into Minecraft biome selections.

It should use the same underlying world-space fields as terrain.

It must not independently generate a contradictory terrain.

---

# 83. Biome Macro Resolution

The initial target is a coarse climate/geomorphic grid.

The 4×4 concept is a performance optimization.

It must not be assumed that Minecraft's biome lookup semantics permit arbitrary nearest-neighbor 4×4 classification.

The adapter must respect the actual biome sampling expectations of Minecraft 1.21.1.

---

# 84. Biome Inputs

Biome selection may use:

```text
temperature
humidity
altitude
slope
landform
surface material
water proximity
coast proximity
glacial state
volcanic state
```

---

# 85. Material Resolver

`MaterialResolver` maps classification into pre-resolved `BlockState` values.

It should not create block-state objects during rasterization.

For example:

```text
STONE
DEEP_STONE
SAND
SANDSTONE
GRAVEL
DIRT
GRASS
SNOW
ICE
BASALT
NETHERRACK
END_STONE
```

The exact material palette belongs to the dimension profile.

---

# 86. Dimension Profiles

GeoEngine uses the same mathematical architecture for:

```text
Overworld
Nether
End
```

but not the same parameter set.

A dimension profile defines:

* vertical interpretation,
* tectonic parameters,
* erosion strength,
* climate behavior,
* hydrology availability,
* cave behavior,
* materials,
* sea/fluid level,
* landform families,
* feature eligibility.

---

# 87. Overworld Profile

The Overworld targets:

* sea level around Y=64,
* continental relief,
* mountains approaching the upper world range,
* large basins,
* rivers,
* coastlines,
* glacial terrain,
* deserts,
* temperate terrain,
* forests,
* wetlands.

The exact maximum mountain height is configurable.

---

# 88. Nether Profile

The Nether uses volcanic geomorphology.

Expected features include:

* volcanic fields,
* shield volcanoes,
* calderas,
* lava plains,
* lava channels,
* volcanic plugs,
* vents,
* tubes,
* ridges,
* cliffs.

Ordinary terrestrial hydrology is disabled or heavily transformed.

Lava is treated as a material/environmental field rather than real-time volcanic fluid simulation.

---

# 89. Nether Fluid Interface

The Nether may use:

```text
lavaLevel
```

as an environmental threshold.

The terrain solver should create terrain compatible with the fluid level.

Long lava channels should be represented primarily through:

* terrain channels,
* source placement,
* deterministic fluid regions,

rather than large numbers of active fluid sources.

---

# 90. End Profile

The End uses deliberately non-terrestrial geomorphology.

The target is:

* monolithic elevated terrain,
* dissected plateaus,
* fracture systems,
* discrete terrain offsets,
* sharp cliffs,
* isolated highlands.

Voronoi-style fracture fields may provide major structural partitioning.

---

# 91. End Fracture Integrity

Independent random vertical offsets are prohibited if they produce:

* floating slabs,
* disconnected terrain,
* unsupported islands.

Terrain pieces must share coherent fracture boundaries.

A fracture field should determine:

* region identity,
* boundary location,
* vertical displacement,
* cliff geometry.

---

# 92. Landform Architecture

The engine does not implement 200 independent terrain algorithms.

Instead:

```text
Mathematical Families
        |
        v
Field Interactions
        |
        v
Shape Features
        |
        v
Process Classification
        |
        v
Landform Classification
```

---

# 93. Shape Classes

Primary shape classes:

```text
POSITIVE
DEPRESSION
FLAT
```

---

# 94. Positive Forms

Positive terrain is associated with:

* positive local prominence,
* positive relief,
* convexity,
* ridge structure,
* elevation,
* local maxima.

Examples:

* hill,
* mountain,
* massif,
* dome,
* ridge,
* mesa,
* butte,
* inselberg,
* bornhardt,
* shield volcano,
* volcanic cone,
* horn,
* tor,
* monadnock.

---

# 95. Depression Forms

Depressions use:

* negative local prominence,
* concavity,
* drainage convergence,
* negative curvature,
* relative relief.

Examples:

* basin,
* valley,
* canyon,
* gorge,
* ravine,
* crater,
* caldera,
* sinkhole,
* doline,
* uvala,
* polje,
* graben,
* trench,
* playa.

---

# 96. Flat Forms

Flat forms use:

* low gradient,
* low curvature,
* broad-scale low relief.

Examples:

* plain,
* plateau,
* floodplain,
* basin floor,
* terrace,
* pediment,
* pediplain,
* peneplain,
* coastal plain,
* lacustrine plain.

---

# 97. Geomorphic Process Groups

The process taxonomy is:

```text
Aeolian
Coastal/Oceanic
Cryogenic
Erosion
Fluvial
Impact
Lacustrine
Mountain/Glacial
Slope
Tectonic
Volcanic
Weathering
```

A landform may have multiple process labels.

---

# 98. Classification Features

The classifier should use:

```text
elevation
local prominence
regional relief
gradient
curvature
principal curvature
anisotropy
orientation
roughness
distance to water
coastal exposure
drainage accumulation
channel proximity
climate
age
tectonic intensity
volcanic intensity
glacial intensity
sediment availability
surface material
```

---

# 99. Multi-Scale Relief

A single-scale prominence cannot reliably distinguish:

* hill,
* mountain,
* massif,
* plateau,
* mesa.

Therefore use multiple spatial scales:

```text
micro relief
meso relief
regional relief
continental relief
```

This is a key requirement for robust classification.

---

# 100. Hessian-Based Shape Classification

For advanced terrain classification, use the Hessian:

$$
H=
\begin{bmatrix}
H_{xx}&H_{xz}\\
H_{zx}&H_{zz}
\end{bmatrix}
$$

Its eigenvalues describe principal curvature.

This allows the engine to distinguish:

* ridge-like structures,
* valley-like structures,
* domes,
* saddles,
* planar surfaces.

---

# 101. Ridge Detection

A ridge generally exhibits:

* high relative elevation,
* elongated geometry,
* directional curvature,
* positive local prominence.

The classifier should use orientation and curvature rather than elevation alone.

---

# 102. Valley Detection

Valleys generally exhibit:

* negative prominence,
* convergent slopes,
* elongated depression geometry,
* drainage concentration.

A valley is therefore both:

```text
Shape = Depression
```

and:

```text
Process = Fluvial / Glacial / Erosional / Tectonic
```

depending on the surrounding fields.

---

# 103. Canyon Detection

A canyon requires:

* strong negative relief,
* steep sidewalls,
* elongated valley geometry,
* drainage or erosion signal.

A simple depression is not sufficient.

---

# 104. Mesa and Butte

Mesa:

```text
broad flat summit
+
steep margins
+
positive prominence
```

Butte:

```text
small flat summit
+
steep margins
+
strong positive prominence
```

The distinction is primarily scale.

---

# 105. Plateau

Plateau requires:

* broad elevated region,
* low summit gradient,
* relatively steep boundary,
* positive regional prominence.

This is a multi-scale classification problem.

---

# 106. Basin

Basin requires:

* negative regional prominence,
* convergent drainage,
* low central gradient,
* elevated surrounding terrain.

A basin is therefore not merely:

$$
\nabla^2H>0
$$

or:

$$
\nabla^2H<0
$$

It requires regional context.

---

# 107. Craters

Craters require annular morphology.

A crater signature contains:

```text
outer terrain
     |
     v
raised rim
     |
     v
depression
     |
     v
possible central uplift
```

Impact and volcanic craters share geometry but differ in process classification.

---

# 108. Impact Terrain

Impact features can be generated from deterministic radial fields:

* crater rim,
* central depression,
* central uplift,
* ejecta blanket.

Large impacts may use asymmetric ejecta modulation.

---

# 109. Volcanic Terrain

Volcanic families include:

* shield volcano,
* stratovolcano,
* cone,
* caldera,
* maar,
* lava field,
* lava dome,
* volcanic plateau,
* volcanic plug,
* fissure vent,
* volcanic island,
* tuya.

These are controlled primarily by a volcanic intensity field plus radial/conic/fracture families.

---

# 110. Aeolian Terrain

Aeolian features use:

* prevailing wind vector,
* sediment availability,
* aridity,
* slope,
* directional anisotropy.

Families include:

* dune,
* barchan,
* erg,
* loess,
* yardang,
* blowout,
* desert pavement,
* ventifact,
* sandhill,
* mushroom rock.

---

# 111. Dune Field

A dune field should use anisotropic ridge functions.

A prevailing wind vector:

$$
\vec W=(W_x,W_z)
$$

defines the dominant orientation.

Terrain displacement should be stronger perpendicular to the wind direction than parallel to it.

---

# 112. Yardang

Yardangs require strongly directional erosion.

The classifier should use:

* anisotropic ridges,
* elongated relief,
* arid climate,
* prevailing wind direction.

---

# 113. Fluvial Terrain

The hydrology graph provides the foundation for:

* rivers,
* streams,
* tributaries,
* valleys,
* floodplains,
* oxbows,
* deltas,
* alluvial fans.

These features must be graph-consistent.

---

# 114. Floodplains

Floodplain geometry is derived from:

* river channel,
* local valley floor,
* accumulation,
* low gradient,
* historical channel envelope.

It is not merely:

```text
near river = floodplain
```

---

# 115. Oxbows

Oxbow lakes require:

1. strong meander curvature,
2. adjacent channel limbs,
3. abandoned channel mask,
4. cutoff condition.

This is a composite hydrological feature.

---

# 116. Deltas

A delta occurs where:

```text
river
   |
   v
standing water
   |
   +--> distributary channels
   +--> sediment lobes
```

Delta generation requires:

* river outlet,
* water body,
* low slope,
* sediment availability.

---

# 117. Alluvial Fans

Alluvial fans are generated where:

* channel exits constrained terrain,
* slope decreases,
* sediment availability is high.

Geometry is fan-shaped and radiates from a confined outlet.

---

# 118. Glacial Terrain

Glacial morphology is represented through a glacial process field.

Potential features:

* cirque,
* arête,
* horn,
* U-shaped valley,
* fjord,
* moraine,
* esker,
* drumlin,
* kame,
* kettle,
* outwash,
* nunatak,
* ice field,
* glacier.

Some are surface forms and some are material/process states.

---

# 119. Glacial Valley

A U-shaped valley requires broad concavity with relatively steep sidewalls.

It differs from a fluvial V-valley through:

* valley width,
* cross-sectional curvature,
* floor flatness,
* glacial process mask.

---

# 120. Fjord

A fjord requires:

```text
glacial valley
+
coastal intersection
+
water fill
```

Therefore it cannot be produced solely from a 2D mountain field.

---

# 121. Cryogenic Terrain

Cryogenic features include:

* pingo,
* patterned ground,
* ice-wedge terrain,
* solifluction forms,
* frost-shattered terrain.

Some require material/temperature masks beyond scalar terrain shape.

---

# 122. Coastal Terrain

Coastal features use:

* sea-level mask,
* distance to shoreline,
* wave exposure,
* coastal slope,
* sediment supply,
* regional terrain.

Potential forms:

* bay,
* gulf,
* cove,
* inlet,
* estuary,
* lagoon,
* cape,
* headland,
* spit,
* tombolo,
* barrier island,
* beach,
* shoal,
* bar,
* marine terrace,
* wave-cut platform,
* sea stack,
* sea arch.

---

# 123. Coastline Continuity

The coastline must derive from the same $H_f$ used by terrain generation.

The water mask is therefore:

$$
M_{water}=H_f<SeaLevel
$$

subject to configured ocean-floor behavior.

A separate coastline noise function must not independently redefine the shoreline.

---

# 124. Sea Stacks and Arches

These require 3D geometry.

A coastline mask can identify:

```text
cliff
+
wave exposure
+
erosion
```

A controlled cave/void field then produces the arch.

Sea stacks emerge when the connecting material is removed.

---

# 125. Karst Terrain

Karst features can use:

* soluble-rock mask,
* water infiltration,
* low-frequency cave network,
* surface depressions.

Potential features:

* doline,
* sinkhole,
* uvala,
* polje,
* karst tower,
* cave,
* natural bridge.

---

# 126. Slope Features

Slope processes use:

* gradient,
* curvature,
* material resistance,
* climate,
* erosion.

Features include:

* scree,
* talus,
* gully,
* ravine,
* bluff,
* escarpment,
* badlands,
* hoodoo,
* terracette.

---

# 127. Badlands

Badlands require:

* high erosion,
* low vegetation/climate protection,
* steep dissected terrain,
* high drainage density,
* weak material resistance.

The visual signature is multi-scale dissection rather than random roughness.

---

# 128. Tectonic Features

Tectonic terrain can include:

* horst,
* graben,
* rift valley,
* fault scarp,
* pull-apart basin,
* structural basin,
* structural ridge,
* dome,
* uplifted block,
* tilted block.

The stress field and fracture functions control these.

---

# 129. Fault Scarp

A fault scarp requires:

* sharp elevation discontinuity,
* coherent linear orientation,
* tectonic process classification.

The implementation should use a smoothed signed-distance fault field rather than random cliff noise.

---

# 130. Graben

A graben consists of:

```text
uplift | downthrow | uplift
```

A deterministic parallel fault-pair field can generate the geometry.

---

# 131. Horst

A horst is the inverse structural relationship:

```text
downthrow | uplift | downthrow
```

The same structural family can generate both.

---

# 132. Coastal/Oceanic Island Families

Island types can emerge through:

* tectonic uplift,
* volcanic cones,
* shield fields,
* erosion remnants,
* coral/coastal masks.

The engine should classify islands based on genesis rather than treat "island" as one terrain generator.

---

# 133. Landform Realization Tiers

Every requested landform belongs to one of four tiers.

### Tier 1 — Scalar Field

Can be generated directly from the continuous surface.

Examples:

* hill,
* mountain,
* ridge,
* valley,
* basin,
* plateau,
* mesa,
* butte.

### Tier 2 — Composite Field

Requires several interacting fields.

Examples:

* canyon,
* crater,
* dune,
* badlands,
* glacial valley,
* caldera.

### Tier 3 — Network/3D Feature

Requires topology or volumetric geometry.

Examples:

* river,
* delta,
* oxbow,
* cave,
* arch,
* sea stack,
* esker.

### Tier 4 — Environmental/Structural Feature

Requires additional material, biome, fluid, or structure systems.

Examples:

* geyser,
* waterfall,
* glacier,
* lava lake,
* quarry,
* swamp,
* oasis.

This classification prevents false claims that every named feature is directly derivable from a single scalar field.

---

# 134. Features That Are Not Pure Landforms

The following should not be represented as ordinary terrain functions:

* geyser,
* spring,
* waterfall,
* quarry,
* swamp,
* oasis,
* glacier,
* ice field,
* lava lake.

They are better represented as:

```text
Terrain
+
Environment
+
Feature
+
Material/Fluid
```

---

# 135. Structure Suitability

Village and structure placement can consume a suitability field.

A generic settlement score may depend on:

```text
flatness
slope
curvature
water access
flood risk
landform
biome
surface material
altitude
```

This is superior to simply asking whether the biome is plains.

---

# 136. Cave/Structure Interaction

Caves should not randomly destroy important structure foundations.

Structure placement can query:

* final surface,
* cave risk,
* slope,
* stability classification.

This is an eligibility query, not a terrain mutation.

---

# 137. Minecraft Integration

`GeoChunkGenerator` is the adapter between Minecraft's chunk-generation lifecycle and GeoEngine.

Its responsibilities include:

* exposing the generator to Minecraft,
* invoking the core engine,
* supplying world seed/context,
* providing height information,
* integrating terrain with chunk sections,
* allowing downstream generation stages to consume the result.

It must not contain geomorphological mathematics.

---

# 138. `fillFromNoise`

The generator should bypass the mathematical assumptions of `NoiseBasedChunkGenerator` and route terrain evaluation into GeoEngine.

The exact 1.21.1 method signature and generation context must be verified against the actual development environment.

The adapter must isolate version-specific API details.

---

# 139. Threading

GeoEngine does not own Minecraft's global chunk-generation executor.

It must operate on the thread/context supplied by the generation framework.

The core engine must therefore be:

* stateless,
* thread-safe,
* reentrant.

`ThreadLocal<WorkerScratchpad>` is acceptable for reusable worker-local memory.

---

# 140. No Blocking

GeoEngine must never block chunk generation waiting for:

* another chunk,
* another worker,
* hydrology generation,
* cache population,
* feature placement.

If data is unavailable from cache, it is deterministically recomputed.

---

# 141. Minecraft Chunk Pipeline

The intended conceptual pipeline is:

```text
Minecraft Chunk Generation
          |
          v
GeoChunkGenerator
          |
          v
GeoBiomeSource / Macro Classification
          |
          v
GeomorphicFieldSampler
          |
          v
Final Surface Grid Hf
          |
          +-------------------+
          |                   |
          v                   v
Heightmaps              Section Classifier
                              |
                    +---------+---------+
                    |                   |
                    v                   v
                  SOLID                BAND
                    |                   |
                    v                   v
              Bulk Section        Density Evaluation
              Writer              W + C
                    |                   |
                    +---------+---------+
                              |
                              v
                        Section Writer
                              |
                              v
                       Chunk Sections
```

AIR sections are also handled through the section writer.

---

# 142. Direct Section Injection

Direct section access is permitted only inside the Forge raster layer.

The mathematical engine must return field results.

It must never:

```text
setBlock()
```

or manipulate:

```text
LevelChunk
LevelChunkSection
PalettedContainer
BlockState
```

---

# 143. Rasterization Pipeline

For each chunk:

```text
1. Acquire WorkerScratchpad
2. Evaluate macro field
3. Interpolate final surface
4. Calculate required derivatives
5. Populate heightmap data
6. Classify vertical sections
7. Bulk-fill provably solid sections
8. Bulk-fill provably air sections
9. Rasterize BAND sections
10. Finalize section/chunk state
11. Release/reuse scratchpad
```

---

# 144. Surface Grid First

The 16×16 final surface grid is the principal intermediate product.

Everything that can reuse it should.

```text
Hf[256]
 |
 +-- heightmap
 +-- section classifier
 +-- biome altitude
 +-- structure suitability
 +-- cave overburden
 +-- coastal classification
```

---

# 145. Section Envelope

The classifier should compute conservative local surface bounds.

For example:

$$
H_{min}
$$

and:

$$
H_{max}
$$

over the chunk.

The bounds must include the maximum possible surface deformation relevant to classification.

---

# 146. Surface Envelope and Warp

If:

$$
|W|\le W_{max}
$$

then the terrain envelope should account for:

$$
H_f\pm W_{max}
$$

where appropriate.

Otherwise an overhang may be incorrectly classified as AIR.

---

# 147. Cave Envelope

If the cave field has a known maximum depth/activation range, that range should be included in section classification.

If no conservative bound exists, the section cannot safely be classified as solid solely from surface distance.

---

# 148. Heightmap Timing

Heightmaps must be established before generation stages that depend upon them.

The rasterizer should therefore calculate surface heights before finalizing terrain-generation stage transitions.

---

# 149. Fluids

GeoEngine should initially use deterministic fluid placement rather than fluid simulation.

Water and lava bodies are defined from:

* terrain surface,
* sea/lava level,
* environmental masks.

Minecraft fluid mechanics may still tick after generation.

GeoEngine is not responsible for making arbitrary active fluid simulation free.

---

# 150. Lighting

The terrain engine cannot guarantee constant lighting cost.

A 2048-block vertical world contains many sections and Minecraft's lighting systems may still process them.

GeoEngine's responsibility is to avoid unnecessary block evaluation.

Lighting optimization must be benchmarked separately.

---

# 151. Structures

Structures remain a separate Minecraft generation concern.

GeoEngine provides:

* terrain heights,
* slope,
* landform classification,
* suitability masks.

The structure system decides whether and where a structure is placed.

---

# 152. Stronghold and Special Structure Policy

Special structures that depend on vanilla assumptions may require custom placement integration.

GeoEngine must not silently assume that all vanilla structure algorithms remain semantically correct after replacing the terrain height model.

---

# 153. Debugging Architecture

GeoEngine requires diagnostic output independent of Minecraft terrain rendering.

The debug system should be able to export:

```text
T
E
R
S
H0
H*
Hf
gradient
laplacian
climate
age
W
C
D
landform
```

as numerical maps.

---

# 154. Field Visualization

The engine should support exporting 2D slices for:

```text
T(x,z)
E(x,z)
H0(x,z)
R(x,z)
S(x,z)
Hf(x,z)
```

and vertical slices for:

```text
D(x,y,z)
```

This makes mathematical failures visible without Minecraft.

---

# 155. Deterministic Test Suite

Minimum tests:

```text
DeterminismTest
ChunkSeamTest
DerivativeContinuityTest
DensityMonotonicityTest
ConfigurationValidationTest
SectionClassifierTest
ScalarVectorEquivalenceTest
HydrologyContinuityTest
CaveSurfaceProtectionTest
NaNInfinityTest
CacheIndependenceTest
```

---

# 156. Determinism Test

For randomly selected coordinates:

```text
same seed
same config
same dimension
same coordinates
```

must produce identical outputs.

Run the same test:

* repeatedly,
* from different threads,
* in different evaluation orders.

---

# 157. Chunk Seam Test

Evaluate adjacent chunks independently.

Compare shared boundary fields.

There must be no discontinuity attributable to chunk ownership.

---

# 158. Derivative Seam Test

Evaluate derivative fields at:

```text
x = chunkBoundary - 1
x = chunkBoundary
x = chunkBoundary + 1
```

The derivative must remain continuous within configured numerical tolerance.

---

# 159. Scalar/Vector Test

Generate identical sample sets using:

```text
ScalarFieldKernel
VectorFieldKernel
```

Compare all fields.

Failure disables SIMD for that implementation until corrected.

---

# 160. Cache Independence Test

Generate terrain:

```text
without cache
```

then:

```text
with warm cache
```

and compare results.

The output must be identical.

---

# 161. Section Safety Test

For every section classified:

```text
SOLID
```

perform direct density sampling at a conservative test pattern.

No unexpected air may exist.

For:

```text
AIR
```

no unexpected solid may exist.

---

# 162. Density Test

With:

$$
W=0
$$

and:

$$
C=0
$$

test:

$$
D(x,y,z)=H_f-y
$$

for a broad coordinate set.

---

# 163. NaN/Infinity Test

Every public field evaluation must reject or sanitize invalid numeric values.

The engine must never emit:

```text
NaN
+Infinity
-Infinity
```

as terrain state.

---

# 164. Configuration Hash

The configuration should have a stable identity.

A world generated with:

```text
config A
```

must never reuse field cache entries generated with:

```text
config B
```

even if the seed is identical.

---

# 165. Generator Version

The generator version must participate in deterministic identity.

Example:

```text
GeoEngine generatorVersion = 1
```

Changing the mathematical algorithm should normally increment the generator version.

This prevents silent interpretation of old cached data.

---

# 166. Numerical Precision

The engine may use `float` storage for cached fields where precision is sufficient.

However, calculations that accumulate significant numerical error may use `double`.

Recommended rule:

```text
double:
    major mathematical derivations
    coordinate transformations
    derivatives where needed

float:
    cached field grids
    bounded normalized values
    density buffers
```

This must be benchmarked.

---

# 167. Coordinate Magnitude

Minecraft world coordinates may become large.

Noise functions must remain numerically stable at large absolute coordinates.

The engine should avoid repeatedly converting huge coordinates into low-precision intermediates.

---

# 168. Localized Coordinate Evaluation

For high-frequency noise, coordinate transforms may subtract a stable local origin where appropriate.

However, this must not introduce chunk-dependent behavior.

Any normalization must remain based on absolute world coordinates.

---

# 169. Thread Safety

Immutable objects:

```text
GeoConfig
SeedContext
Noise configuration
DimensionProfile
FieldKernel
```

may be shared.

Mutable objects:

```text
GeoSample
WorkerScratchpad
temporary arrays
```

must be worker-local.

Caches must be explicitly thread-safe.

---

# 170. Static State

Static mutable state is discouraged.

If static data exists, it must be immutable.

Examples acceptable:

```text
lookup tables
constants
precomputed immutable curves
```

Examples discouraged:

```text
static currentWorld
static currentSeed
static sharedScratchpad
static mutable field cache
```

---

# 171. Memory Layout

Performance-sensitive arrays should use contiguous primitive storage.

Prefer:

```text
float[]
int[]
long[]
```

over:

```text
Float[]
Integer[]
List<Float>
List<GeoSample>
```

The goal is predictable memory access.

---

# 172. Structure of Arrays

For vectorization, prefer:

```text
tectonic[]
erosion[]
surface[]
gradientX[]
gradientZ[]
```

over:

```text
GeoSample[]
```

for batch operations.

This allows SIMD implementations to process homogeneous data.

---

# 173. Scalar Hot Loop

The scalar implementation should be optimized only after correctness is established.

Avoid:

* streams,
* lambdas,
* boxing,
* unnecessary object creation,
* virtual dispatch in inner loops,
* repeated configuration lookup,
* repeated seed derivation.

Constants should be hoisted.

---

# 174. Seed Hoisting

The world/dimension/config seed domain should be derived once per generation context rather than once per voxel.

---

# 175. Noise Object Lifetime

Noise samplers should be immutable and reusable.

Do not construct a noise generator for every field evaluation.

---

# 176. Benchmarking

GeoEngine requires benchmarks for:

```text
surface sampling
derivative calculation
density evaluation
section classification
bulk section filling
BAND rasterization
hydrology
cache hit/miss
scalar vs vector
```

---

# 177. Benchmark Scenarios

At minimum:

```text
Flat world
Mountain world
Mixed terrain
Heavy cave world
Heavy hydrology world
2048-height world
Large-distance coordinate samples
Cold climate
Volcanic dimension
```

---

# 178. Performance Metrics

Record:

```text
ns/sample
samples/chunk
allocations/chunk
bytes allocated/chunk
sections classified
sections rasterized
cache hit rate
cache miss rate
density evaluations
chunk generation time
```

Do not optimize based only on wall-clock observations from a single world.

---

# 179. Fast Path Metrics

The key metric is:

$$
\frac{N_{band}}{N_{total}}
$$

where:

$$
N_{total}=16\times16\times2048
$$

for a complete column.

The objective is to keep expensive 3D evaluations concentrated near the surface and explicit volumetric feature regions.

---

# 180. Cave Performance

Caves are the primary exception to surface-only evaluation.

The engine should use a coarse cave occupancy test to avoid evaluating cave density in every underground voxel.

Potential pipeline:

```text
Coarse cave mask
       |
       v
Section eligibility
       |
       v
Only candidate sections
       |
       v
Detailed cave density
```

---

# 181. Region-Based Cave Mask

A cave region mask can be deterministic and low-resolution.

It answers:

> Can this region possibly contain a cave?

It does not answer:

> Is this individual voxel a cave?

The detailed field answers the second question.

---

# 182. Error Budget

Every approximation should have a defined acceptable error.

Examples:

```text
Scalar/vector density tolerance
Derivative tolerance
Chunk seam tolerance
Height interpolation error
Hydrology channel displacement tolerance
Section envelope safety margin
```

---

# 183. Interpolation Error

Macro sampling introduces interpolation error.

The engine must test:

```text
direct surface evaluation
vs
macro interpolated surface
```

at randomly selected coordinates.

If the error exceeds the terrain-quality threshold, the macro resolution must be increased or the interpolation model improved.

---

# 184. Adaptive Macro Resolution

Future versions may use:

```text
4-block spacing
```

for smooth terrain and:

```text
2-block spacing
```

for highly complex terrain.

Adaptive resolution must remain deterministic.

The choice must depend on field values, not generation history.

---

# 185. Landform Classification Bitmask

`classificationBits` may encode:

```text
Shape
Process
Environment
Material
Hydrology
Climate
```

Separate bit ranges should be reserved.

For example:

```text
bits 0-3    Shape
bits 4-15   Process
bits 16-23  Environment
bits 24-31  Feature flags
```

The exact allocation should be finalized before implementation.

---

# 186. Landform Priority

Multiple landforms may overlap.

Classification therefore requires priority.

Example:

```text
CRATER
  overrides
BASIN
  overrides
DEPRESSION
```

and:

```text
CALDERA
  implies
VOLCANIC + DEPRESSION
```

Classification should support both primary and secondary labels.

---

# 187. Compound Landforms

Many natural forms are combinations.

Examples:

```text
Volcanic + Island
Glacial + Coastal = Fjord
Fluvial + Coastal = Delta
Aeolian + Coastal = Dune Coast
Karst + Tropical = Tower Karst
Tectonic + Fluvial = Rift Valley
Glacial + Volcanic = Subglacial Volcanic Terrain
```

The classifier should represent these combinations rather than forcing one exclusive label.

---

# 188. Material System

Terrain material should derive from:

```text
dimension
landform
climate
elevation
slope
process
lithology
water proximity
```

The initial implementation should keep material classification simpler than the full landform classifier.

---

# 189. Lithology

A future lithology field can determine:

* erosion resistance,
* cliff stability,
* cave susceptibility,
* material palette,
* volcanic rock distribution.

Lithology should be deterministic and independent of block placement.

---

# 190. Weathering

Weathering strength depends on:

```text
climate
age
temperature variation
humidity
slope
material
```

The result modifies erosion and surface material rather than directly generating every weathering feature.

---

# 191. Weathering Feature Examples

Possible derived features:

* tafoni-like cavities,
* tors,
* mushroom rocks,
* limestone pavement,
* karst surfaces,
* desert pavement,
* hoodoos.

Some require 3D feature fields.

---

# 192. Mountain System

Mountains should emerge from:

```text
tectonic uplift
+
stress anisotropy
+
erosion
+
glacial process
+
climate
```

The system should avoid a single "mountain noise" function.

---

# 193. Mountain Age

Young mountain systems:

```text
high relief
sharp ridges
low erosion
strong tectonic signal
```

Old mountain systems:

```text
lower relief
broader valleys
strong erosion
rounded ridges
higher sedimentation
```

This is one of the primary uses of the Epoch field.

---

# 194. Basin Evolution

Basins should use:

```text
tectonic depression
+
erosion
+
drainage
+
deposition
```

The final basin floor should therefore differ from a simple inverted noise crater.

---

# 195. Continental Structure

The very-low-frequency tectonic field determines continental-scale organization.

The engine must empirically test whether the selected wavelengths produce useful structure over:

```text
1 km
10 km
100 km
```

The mathematical frequencies must be judged against actual terrain scale rather than naming conventions.

---

# 196. Ocean Generation

The ocean is not merely:

```text
Hf < 64
```

The ocean system may additionally use:

* continental mask,
* bathymetric shaping,
* coastal erosion,
* ocean basin depth,
* trenches,
* shelves.

---

# 197. Continental Shelf

Near coastlines, bathymetry should transition gradually.

A coastal shelf function may depend on:

$$
distanceToCoast
$$

rather than a hard threshold.

---

# 198. Ocean Trenches

Tectonic/oceanic profiles may add elongated negative structures using directional fault fields.

These are not generated by random 3D noise.

---

# 199. Lake System

Lakes may be derived from:

```text
closed basins
+
water threshold
+
drainage topology
```

A lake should not simply be a random low-elevation patch.

---

# 200. Lake Types

Possible classes:

```text
Tectonic Lake
Glacial Lake
Volcanic Crater Lake
Oxbow Lake
Playa
Proglacial Lake
Lacustrine Basin
```

Classification follows terrain genesis.

---

# 201. Waterfall

A waterfall requires:

* channel,
* sharp elevation drop,
* sufficient water flow.

It is therefore a feature placement problem rather than simply a terrain geometry class.

---

# 202. Spring

A spring can be associated with:

* aquifer proxy,
* slope,
* geological contact,
* drainage convergence.

Initially this can be a feature-placement system driven by field classification.

---

# 203. Geyser

Geysers require a geothermal feature mask.

They should not be inferred solely from terrain elevation.

A future geothermal field may derive from volcanic/tectonic intensity.

---

# 204. Lava Flow

Lava flows can use a deterministic downhill path from volcanic vents.

They are represented as static terrain/material regions rather than physically simulating lava.

---

# 205. Lava Tube

Lava tubes use:

```text
volcanic field
+
flow corridor
+
cave field
```

and therefore naturally fit the composite feature architecture.

---

# 206. Glacial Cave

Glacial caves require:

```text
glacial mask
+
ice/water interface
+
volumetric void
```

They are not generic caves with a different texture.

---

# 207. Feature Eligibility

Every expensive feature should have a cheap eligibility test.

Example:

```text
Volcano:
    volcanicStrength > threshold

Cave:
    caveRegionMask == active

River:
    drainageAccumulation > threshold

Glacier:
    climate + altitude + glacialStrength

Dune:
    aridity + sediment + wind exposure
```

Only eligible regions proceed to expensive feature evaluation.

---

# 208. Generation Ordering

The conceptual field order is:

```text
Seed
 |
 v
Tectonics
 |
 v
Stress Warp
 |
 v
Epoch / Climate Parameters
 |
 v
Erosion
 |
 v
H0
 |
 +------> Derivatives
 |
 +------> Predictive Hydrology
                  |
                  v
              River R
                  |
                  v
            H* = H0 - R
                  |
                  v
             Deposition S
                  |
                  v
             Hf = H* + S
                  |
                  +----> Landform classification
                  |
                  +----> Heightmap
                  |
                  +----> Section classification
                  |
                  v
            Volumetric W
                  |
                  v
              Cave C
                  |
                  v
              Density D
```

This ordering is normative.

---

# 209. Derivative Ordering

Derivatives must be taken after the relevant field blending.

Never:

```text
derive young
derive old
blend derivatives
```

Prefer:

```text
blend parameters
calculate surface
calculate derivatives
```

---

# 210. Hydrology Ordering

Hydrology should initially operate on:

$$
H_0=T-E
$$

not:

$$
H_f
$$

because $H_f$ contains the hydrological result being calculated.

---

# 211. Deposition Ordering

Deposition follows incision:

$$
H^*=H_0-R
$$

then:

$$
H_f=H^*+S
$$

The deposition field may use:

* slope of $H^*$,
* basin potential,
* sediment budget,
* hydrological context.

---

# 212. Surface Stability

The final surface should be bounded:

$$
H_{min}\le H_f\le H_{max}
$$

with configured safety margins.

Clamping should be applied deliberately and documented because hard clipping can introduce plateaus.

Prefer smooth remapping before hard clamping.

---

# 213. Boundary Conditions

At world vertical limits:

```text
Ymin
Ymax
```

the density system must remain valid.

No field may depend on evaluating outside an invalid numerical domain without explicit handling.

---

# 214. World Ceiling

If terrain approaches the upper world limit, the engine must prevent:

* clipped mountains,
* flat artificial ceilings,
* invalid heightmaps.

The terrain amplitude must be configured with vertical headroom.

---

# 215. World Floor

Likewise, terrain should not unintentionally intersect the lower world boundary.

Dimension profiles define appropriate lower terrain limits.

---

# 216. Performance Isolation

The following components must be independently benchmarkable:

```text
TectonicField
ErosionField
HydrologyField
DepositionField
SurfaceField
WarpField
CaveField
DensityField
```

A regression in one subsystem must be measurable.

---

# 217. Failure Handling

Configuration failures should be fatal during generator initialization.

Numerical anomalies during generation should be:

1. detected,
2. logged with coordinate/field context,
3. converted into a safe deterministic fallback where possible.

A silent NaN must never propagate into terrain.

---

# 218. Debug Fallback

A safe fallback density can be:

$$
D=H_f-y
$$

if an optional debug mode detects a volumetric field failure.

This should be a development diagnostic, not a normal production recovery path.

---

# 219. Version Compatibility

Minecraft/NeoForge-specific API assumptions belong exclusively to:

```text
com.geoengine.forge
```

The core mathematical engine should survive API changes.

If Minecraft 1.21.2 or later changes chunk internals, the adapter changes without rewriting the geomorphological solver.

---

# 220. Phase 1 Implementation

Phase 1 consists of:

```text
GeoConfig
GeoSample
SeedDerivation
GeoNoise
FieldKernel
ScalarFieldKernel
WorkerScratchpad
DerivativeSampler
TectonicField
StressWarp
EpochField
ClimateField
ErosionField
SurfaceField
```

Hydrology may initially use a placeholder deterministic drainage model while the surface core is validated.

---

# 221. Phase 1 Acceptance Criteria

Phase 1 is complete only when:

* core compiles without Minecraft,
* deterministic tests pass,
* no NaN/Infinity output exists,
* configuration validation works,
* chunk seam tests pass,
* derivative tests pass,
* surface fields can be exported,
* benchmark results are recorded.

---

# 222. Phase 2

Phase 2 introduces:

```text
HydrologyField
DrainageRouter
RiverField
DepositionField
```

Acceptance criteria:

* continuous rivers across chunk boundaries,
* deterministic basin identification,
* deterministic confluences,
* no chunk-generation ordering dependence,
* bounded incision,
* bounded deposition.

---

# 223. Phase 3

Phase 3 introduces:

```text
WarpField
CaveField
DensityField
SectionClassifier
```

Acceptance criteria:

* density correctness,
* surface protection,
* controlled overhangs,
* caves remain bounded,
* section classification is conservative.

---

# 224. Phase 4

Phase 4 introduces:

```text
GeoChunkGenerator
GeoBiomeSource
ChunkRasterizer
SectionWriter
HeightmapWriter
MaterialResolver
```

Acceptance criteria:

* playable generated Minecraft world,
* correct terrain seams,
* correct heightmaps,
* correct section states,
* no generation-order dependency.

---

# 225. Phase 5

Phase 5 introduces:

```text
LandformClassifier
ClimateClassifier
FeatureEligibility
```

Acceptance criteria:

* landform labels match field morphology,
* biome transitions are coherent,
* feature eligibility is deterministic.

---

# 226. Phase 6

Phase 6 introduces:

```text
OverworldProfile
NetherProfile
EndProfile
```

Each dimension is independently tested.

---

# 227. Phase 7

Phase 7 introduces:

* advanced caves,
* glacial systems,
* coastal systems,
* volcanic systems,
* advanced rivers,
* deltas,
* terraces,
* karst,
* aeolian fields.

---

# 228. Phase 8

Phase 8 is optimization:

```text
allocation profiling
cache profiling
SIMD
macro resolution tuning
section fast paths
palette optimization
```

No optimization may alter the mathematical contract.

---

# 229. Phase 9

Phase 9 is content integration:

* structures,
* vegetation,
* biome decoration,
* fluids,
* special features,
* gameplay-facing terrain rules.

---

# 230. Phase 10

Phase 10 is production hardening:

* multiplayer testing,
* dedicated server testing,
* client/server compatibility,
* world save/reload,
* long-distance travel,
* chunk regeneration,
* profiling,
* crash recovery,
* configuration migration.

---

# 231. Final Architecture

The complete engine becomes:

```text
                         WORLD SEED
                             |
                             v
                    +----------------+
                    | Seed Domains   |
                    +----------------+
                             |
                             v
                 +----------------------+
                 | Tectonic Field       |
                 +----------------------+
                             |
                             v
                 +----------------------+
                 | Stress Coordinate    |
                 | Warp                 |
                 +----------------------+
                             |
                             v
                 +----------------------+
                 | Epoch + Climate      |
                 | Parameter Blending   |
                 +----------------------+
                             |
                             v
                 +----------------------+
                 | Erosion Field        |
                 +----------------------+
                             |
                             v
                         H0 = T - E
                             |
                +------------+-------------+
                |                          |
                v                          v
          Derivatives              Climate/Process
                |                   Classification
                v
       +---------------------+
       | Predictive          |
       | Hydrology           |
       +---------------------+
                |
                v
             River R
                |
                v
             H* = H0 - R
                |
                v
       +---------------------+
       | Deposition          |
       +---------------------+
                |
                v
             Hf = H* + S
                |
       +--------+---------+----------------+
       |                  |                |
       v                  v                v
   Heightmap         Landform         Section
                     Classifier       Classifier
                                            |
                                +-----------+-----------+
                                |                       |
                                v                       v
                              BULK                   BAND
                                |                       |
                                |                       v
                                |                W + Cave C
                                |                       |
                                |                       v
                                |                       D
                                |                       |
                                +-----------+-----------+
                                            |
                                            v
                                   Minecraft Sections
```

---

# 232. Final Invariants

The following invariants are normative.

### Determinism

$$
F(seed,dimension,version,config,x,y,z)
$$

must be deterministic.

### Chunk independence

No generated chunk may be a correctness dependency for another chunk.

### Surface continuity

Chunk boundaries must not create field discontinuities.

### Pre-derivative blending

Parameters are blended before derivative evaluation.

### Hydrology

Laplacian is a curvature signal, not watershed accumulation.

### Hydrology circularity

River incision cannot depend directly on derivatives of the surface that already contains that same incision unless an explicit iterative solver exists.

### Deposition

$$
0\le S\le E_{total}
$$

where $E_{total}$ is explicitly defined as the available removal budget.

### Climate

$$
K_{min}\le K\le K_{max}
$$

### Warp

Warp amplitude and coordinate distortion are explicitly bounded.

### Altitude

No $1/y$ expression is used for altitude attenuation.

### Cave

Cave density is nonnegative and surface-gated unless an exposed cave feature is explicitly enabled.

### Density

$$
D=H_f-y-W-C
$$

### Scalar authority

The scalar implementation defines correctness.

### SIMD

SIMD is optional and must remain within numerical tolerance.

### Allocation

No steady-state heap allocation occurs inside the mathematical/raster hot loops.

### Section classification

False SOLID/AIR classifications are forbidden.

### Cache

Cache state cannot affect terrain output.

### Heightmap

Heightmap data must represent the generated terrain correctly before dependent generation stages consume it.

---

# 233. Final Technical Position

GeoEngine is feasible as a Minecraft 1.21.1 / NeoForge terrain-generation architecture provided that it is treated as a deterministic mathematical terrain solver rather than as a simulation engine.

The central abstraction is:

$$
\boxed{
(seed,dimension,config,x,y,z)
\rightarrow
D
}
$$

with the expensive geomorphological work factored into:

$$
\boxed{
(seed,dimension,config,x,z)
\rightarrow
H_f
}
$$

and the volumetric layer restricted to regions where it is necessary.

The principal architectural achievement is therefore not merely replacing `NoiseBasedChunkGenerator`.

It is separating:

```text
GEOMORPHOLOGICAL MATHEMATICS
```

from:

```text
MINECRAFT VOXEL MATERIALIZATION
```

The mathematical core can consequently be validated independently, optimized independently, benchmarked independently, and eventually reused by a different voxel engine without changing its underlying geomorphic model.

The implementation sequence is therefore frozen as:

```text
PHASE 1
Pure mathematical core
        |
        v
PHASE 2
Predictive hydrology + deposition
        |
        v
PHASE 3
Volumetric warp + caves + density
        |
        v
PHASE 4
NeoForge chunk integration
        |
        v
PHASE 5
Landform + climate classification
        |
        v
PHASE 6
Overworld / Nether / End profiles
        |
        v
PHASE 7
Advanced geomorphic features
        |
        v
PHASE 8
Performance optimization / SIMD
        |
        v
PHASE 9
Structures / biomes / content
        |
        v
PHASE 10
Production hardening
```

The **first executable implementation target is therefore the pure `com.geoengine.core` module**, beginning with `GeoConfig`, `GeoSample`, `SeedDerivation`, `GeoNoise`, `FieldKernel`, `ScalarFieldKernel`, `WorkerScratchpad`, `DerivativeSampler`, `TectonicField`, `StressWarp`, `EpochField`, `ClimateField`, `ErosionField`, and `SurfaceField`.

`GeoChunkGenerator`, `NativeChunkInjector`, and all `net.minecraft.*` interaction remain outside that first correctness boundary.
