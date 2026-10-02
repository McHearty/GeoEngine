# GEOENGINE-AMEND-2026-10-02-03

## Plate/Region Partition and Required Parametric Hydrology Controls

**Status:** Normative  
**Supersedes:** `GEOENGINE-AMEND-2026-10-02-02` and Sections 38–40 of `TECHSPEC.md`

---

# A1. Purpose

This amendment replaces the fixed macro-grid terrain sampling architecture with a deterministic plate/region partition and configurable regional sampling architecture.

It also promotes the principal hydrological and channel-shaping parameters from implementation assumptions into explicit configuration and mathematical contracts.

The amendment defines:

- deterministic plate/region partitioning;
- plate-local evaluation and caching;
- configurable terrain and hydrological sampling;
- predictive hydrology;
- accumulation-derived channel identification;
- bounded multi-plate reconnection;
- channel width;
- bounded meander;
- deterministic smoothing;
- continuous channel geometry;
- continuous bank geometry;
- lake and basin topology;
- continuous channel incision;
- continuous surface authority;
- discrete channel-bed realization;
- hydrology-local wetness;
- bounded configuration validation;
- deterministic diagnostics and acceptance tests.

The referenced `StreamsReflowing_CleanRoom_TechSpec.md` provides architectural background only. It does not override GeoEngine's deterministic, chunk-independent, continuous-surface, cache-independent, or scalar-authority contracts.

The amendment establishes one explicit continuous-to-discrete realization boundary. `R_quant` is the deterministic discrete realization of `R`; it is never an additional geomorphological incision term.

---

# A2. Plate / Region Partition

## A2.1 Plate Identity

A plate/region identity shall be derived from canonical world-generation inputs:

```text
P = PlateKey(
    seed,
    dimensionId,
    generatorVersion,
    configHash,
    x,
    z
)
```

`dimensionId` shall be the canonical dimension identifier.

Runtime dimension object identity, object references, worker identity, chunk identity, or evaluation order shall not participate in mathematical identity.

---

## A2.2 Plate-Local Evaluation

A plate/region defines the spatial organization used for expensive regional evaluation.

At minimum, the regional system shall support configurable:

```text
plateScale
gridSpacing
terrainSampleSpacing
```

These values control spatial partitioning and sampling resolution.

Plate-local evaluation may provide:

- regional sampling;
- derivative stencils;
- hydrological rasterization;
- channel topology construction;
- bounded regional searches;
- regional cache storage;
- regional diagnostics.

The exact number of samples per plate is implementation-defined.

---

## A2.3 Plate-Local Does Not Mean Plate-Dependent

Plate/region partitioning shall never become a correctness dependency.

A mathematical field value at `(x,z)` must be independently recomputable without requiring:

- another plate to have been evaluated;
- another chunk to have been generated;
- another worker to have executed;
- a cache entry to exist;
- a previous hydrology calculation to have completed;
- a particular evaluation order.

A plate is therefore an optimization and evaluation domain, not a mutable mathematical state boundary.

---

## A2.4 World-Space Coordinates

All continuous mathematical evaluation shall use absolute world-space coordinates.

Local plate coordinates may be used for:

- array indexing;
- raster storage;
- cache addressing;
- traversal;
- diagnostics.

Conversion to local coordinates shall not alter mathematical identity or field evaluation.

---

## A2.5 Plate Boundary Continuity

For any shared world-space coordinate, evaluation from adjacent plates shall produce the same continuous mathematical field within the implementation's defined numerical tolerance.

This contract applies to:

- tectonic fields;
- stress warp;
- erosion;
- potential surface;
- hydrological continuous fields;
- accumulation-derived continuous fields;
- channel geometry;
- continuous incision;
- deposition;
- final continuous surface;
- bank displacement.

Plate boundaries shall not themselves create mathematical discontinuities.

---

## A2.6 Cache Identity

Regional caches shall include sufficient identity to prevent cross-world or cross-configuration contamination.

At minimum, cache identity shall incorporate:

```text
seed
dimensionId
generatorVersion
configHash
plateKey
```

Cache contents shall remain an optimization only.

Cache eviction, cache warmth, cache population order, and cache implementation shall not change generated terrain.

---

# A3. Required Parametric Hydrology Controls

## A3.1 Minimum River Accumulation

Configuration:

```text
minRiverAccumulation
```

defines the threshold for channel identification.

Conceptually:

```text
M_channel(x,z) =
    1 if Af(x,z) >= minRiverAccumulation
    0 otherwise
```

where `Af` is the deterministic procedural accumulation/importance field.

`Af` represents procedural relative drainage importance.

It is not a physical simulation of water volume or discharge.

The exact accumulation algorithm remains implementation-defined.

`M_channel` identifies established channel topology. It is not itself the final terrain-realization mask.

---

## A3.2 Channel Width

Required configuration:

```text
baseWidth
maxWidth
widthScale
```

Channel width shall be derived deterministically from `Af`.

Conceptually:

```text
width =
    clamp(
        baseWidth + widthScale * F(Af),
        baseWidth,
        maxWidth
    )
```

where `F` is a deterministic bounded mapping selected by the implementation.

The resulting width shall remain bounded by:

```text
baseWidth <= width <= maxWidth
```

when `baseWidth <= maxWidth`.

---

## A3.3 Continuous Channel Incision

Required configuration:

```text
streamDepth
stepDeltaY
```

`streamDepth` controls continuous channel incision magnitude.

The continuous terrain relationship is:

```text
H0 = T - E

R = continuous channel incision

H* = H0 - R
```

No vertical quantization occurs at this stage.

`R` is a continuous mathematical field.

It may not depend directly on derivatives of a surface that already contains that same incision unless a future explicit iterative solver is introduced.

`stepDeltaY` has no effect on `R`.

---

## A3.4 Continuous Surface Authority

The continuous surface is defined as:

```text
Hf = H* + S
```

or equivalently:

```text
Hf = H0 - R + S
```

where:

```text
H0 = T - E
H* = H0 - R
S  = deposition
```

`Hf` is the authoritative continuous geomorphological surface.

It remains the mathematical source of truth regardless of subsequent Minecraft voxel realization.

---

## A3.5 Channel Geometry Ordering

The normative dependency ordering is:

```text
Predictive Hydrology
        |
        v
Channel Topology
        |
        v
Bounded Reconnection
        |
        v
Channel Width
        |
        v
Meander
        |
        v
Fixed Deterministic Smoothing
        |
        v
Final Channel Centerline
        |
        v
Bank Geometry
```

Bank geometry shall therefore operate on the final channel geometry.

Banks shall not be generated against a pre-meander or pre-smoothing centerline.

---

## A3.6 Meander

Required configuration:

```text
meanderStrength
smoothingPasses
```

`meanderStrength` controls a bounded deterministic perturbation of channel geometry.

`meanderStrength` shall not permit unbounded displacement from the underlying drainage topology.

`smoothingPasses` shall be interpreted as a fixed deterministic number of smoothing passes.

Smoothing shall not introduce nondeterministic iteration or order dependence.

---

## A3.7 Bank Geometry

Required configuration:

```text
bankWidth
bankSlope
bankNoise
bankSteepFactor
valleySnapRadius
containmentBerm
```

Bank geometry shall be evaluated from the final continuous channel centerline.

It shall not depend on:

```text
R_quant
Minecraft blocks
heightmaps
chunk generation order
cache state
plate evaluation order
```

---

## A3.8 Continuous Bank Displacement

Bank snapping shall not directly expose a discontinuous discrete nearest-target selection.

A deterministic discrete candidate search may be used internally, but the resulting displacement must be transformed into a bounded continuous influence/displacement field.

The resulting bank displacement field shall be:

- deterministic;
- bounded;
- continuous within numerical tolerance;
- based on absolute world-space coordinates;
- independent of plate boundaries;
- independent of chunk generation order;
- independent of cache state.

Continuous input fields do not by themselves guarantee continuity when an argmin or nearest-target selection is directly exposed.

Therefore:

```text
discrete candidate selection
        |
        v
continuous influence/displacement
```

shall be the required architecture.

---

## A3.9 Permitted Bank-Snap Implementations

Two implementation families are permitted.

### Option A — Continuous Domain Warp

A deterministic continuous domain-warp function may displace the bank geometry.

It must satisfy:

- deterministic evaluation;
- bounded amplitude;
- continuous output;
- plate independence;
- absolute-coordinate evaluation.

### Option B — Drainage-Graph-Constrained Search

A discrete drainage graph may be searched for valley or terrain targets.

The discrete search result shall not itself become the continuous bank displacement.

Instead:

```text
graph target
    |
    v
bounded continuous influence
    |
    v
bank displacement
```

shall be used.

The exact target-search algorithm remains implementation-defined.

---

## A3.10 Plate-Independent Bank Targets

Plate boundaries shall not alter the mathematical bank target for the same channel geometry.

Candidate construction, search bounds, tie-breaking, and continuous influence generation shall be deterministic across plate boundaries.

The result shall not depend on which plate performs the evaluation.

---

## A3.11 Lake and Basin Topology

Required configuration:

```text
lakeMinArea
lakeMaxArea
```

Lake detection shall be treated as a continuous/topological hydrological operation rather than a consequence of Minecraft voxel quantization.

The implementation shall establish basin/lake topology before discrete terrain realization.

Area thresholds are filters over identified lake topology; they shall not themselves redefine the underlying continuous terrain surface.

The exact lake-detection algorithm is implementation-defined.

---

## A3.12 Bounded Multi-Plate Reconnection

Required configuration:

```text
reconnectRadius
seaLevelExtension
connectNearbyWater
outletBiomes
maxReconnectionSamples
```

`reconnectRadius` bounds the spatial extent of candidate searches.

`maxReconnectionSamples` bounds computational work.

Both bounds are normative.

A reconnection operation shall terminate when either:

```text
candidate distance > reconnectRadius
```

or:

```text
examined candidates >= maxReconnectionSamples
```

is reached.

---

## A3.13 Reconnection Candidate Sources

Candidates may be derived from deterministic sources including:

- local drainage graph;
- bounded water-body index;
- sea-level predicates;
- configured outlet-biome predicates;
- other deterministic world-space hydrological predicates.

Candidates shall not depend on:

- previously generated chunks;
- mutable cross-chunk river state;
- cache population order;
- worker execution order;
- plate evaluation order.

---

## A3.14 Reconnection Candidate Ordering

Candidate ordering shall be deterministic.

Tie-breaking shall be deterministic.

The exact candidate-priority algorithm is implementation-defined unless subsequently promoted to a normative configuration or mathematical contract.

No candidate-selection implementation may introduce nondeterministic ordering.

---

## A3.15 Reconnection Fail-Safe

If no valid reconnection target is found within the bounded search:

```text
local deterministic downhill drainage behavior
```

shall be used as the fallback.

The fallback shall not:

- recursively invoke another reconnection search without a bounded termination condition;
- require another plate to be generated;
- require another chunk;
- create global mutable hydrological state;
- block waiting for unavailable data.

---

## A3.16 Hydrology-Local Wetness

Required configuration:

```text
wetnessDryCutoff
wetnessWetReference
wetnessMultiplier
```

Wetness used by the hydrological/channel system shall remain a hydrology-local field.

Conceptually:

```text
wetnessDryCutoff
        |
        v
dry region
        |
        v
wetnessWetReference
        |
        v
wet region
```

`wetnessMultiplier` modifies hydrology-local influence.

This does not replace the global climate multiplier `K`.

The exact wetness interpolation function is implementation-defined.

---

# A4. Normative Hydrology and Terrain Pipeline

The normative dependency graph is:

```text
Seed / Dimension / Config Domains
                |
                v
Tectonic + Stress Warp
                |
                v
Epoch + Climate Parameter Blending
                |
                v
Erosion
H0 = T - E
                |
                v
Plate / Region Derivatives
                |
                v
Predictive Hydrology
Af + Drainage Topology
                |
                v
Channel Identification
M_channel
                |
                v
Bounded Multi-Plate Reconnection
                |
                v
Lake / Basin Topology
                |
                v
Channel Width
                |
                v
Meander
                |
                v
Fixed Deterministic Smoothing
                |
                v
Final Channel Centerline
                |
        +-------+-------+
        |               |
        v               v
Bank Geometry      Continuous
                   Incision R
        |               |
        +-------+-------+
                |
                v
H* = H0 - R
                |
                v
Deposition S
evaluated against H*
                |
                v
Hf = H* + S
                |
                v
Continuous Surface Authority
                |
        +-------+--------+
        |                |
        v                v
Continuous          Channel / Lake
Downstream          Realization Masks
Consumers                 |
                           v
                 R_quant = Q(R, stepDeltaY, M_realize)
                           |
                           v
                       Hterrain
                           |
                           v
                 Minecraft terrain columns
                           |
                           v
                       Heightmaps
```

`Hterrain` is a realization product. It is not a new continuous mathematical surface authority.

---

## A4.1 Continuous Surface Authority

The authoritative continuous surface is:

```text
H0 = T - E

R  = continuous channel incision

H* = H0 - R

S  = deposition

Hf = H* + S
```

Therefore:

```text
Hf = H0 - R + S
```

`Hf` is the sole continuous geomorphological surface authority.

It is the surface used by continuous downstream mathematical systems.

---

## A4.2 Channel Realization Mask

The channel terrain-realization mask shall be derived from already-established final channel topology and geometry.

Conceptually:

```text
M_realize(x,z) =
    1 if (x,z) lies within the deterministic
      final channel terrain-realization domain
    0 otherwise
```

The mask may depend on:

- `M_channel`;
- final channel centerline;
- channel width;
- final channel geometry;
- bank/channel realization parameters.

It shall not require a second hydrological solve.

It shall not depend on:

- `R_quant`;
- Minecraft blocks;
- heightmaps;
- generated neighboring chunks;
- mutable cross-chunk state.

`M_channel` establishes channel topology; `M_realize` establishes where channel-specific discrete realization rules apply. The two masks shall not be conflated unless the implementation can prove that the same domain is intended.

---

## A4.3 Discrete Channel Incision Realization

`R_quant` is the deterministic discrete realization of the continuous incision field `R`.

It is **not an additional incision field**.

The relationship is:

```text
R       = continuous mathematical incision
R_quant = deterministic realization of R
```

The realization operator is conceptually:

```text
R_quant(x,z) =
    Q(
        R(x,z),
        stepDeltaY,
        M_realize(x,z)
    )
```

where `Q` is deterministic and bounded.

Within the active channel realization domain, `stepDeltaY` controls the vertical quantization of the continuous incision.

Outside the active channel realization domain, the realization shall preserve the continuous value of `R`:

```text
M_realize(x,z) = 0  =>  R_quant(x,z) = R(x,z)
```

unless another explicitly defined terrain-realization rule is introduced.

The exact quantization operator is implementation-defined.

`R_quant` therefore represents replacement of `R` at the realization boundary, not an additional subtraction from a surface that already contains `R`.

---

## A4.4 Realized Terrain Surface

The discrete terrain surface shall be derived by replacing the continuous incision `R` with its discrete realization `R_quant`.

Therefore:

```text
Hterrain = H0 - R_quant + S
```

where:

```text
H0 = T - E
R_quant = Q(R, stepDeltaY, M_realize)
S = deposition evaluated against H*
```

The continuous mathematical surface remains:

```text
Hf = H0 - R + S
```

Therefore the relationship between the continuous and realized surfaces is:

```text
Hterrain = Hf - (R_quant - R)
```

The term:

```text
R_quant - R
```

is realization/quantization error introduced by terrain realization.

It is not additional geomorphological incision.

This replacement relationship is normative.

---

## A4.5 No Double Incision

The implementation shall not calculate:

```text
Hterrain = Hf - R_quant
```

because that would apply `R` and `R_quant` as two independent incision terms.

The correct realization is:

```text
Hterrain = H0 - R_quant + S
```

or the mathematically equivalent form:

```text
Hterrain = Hf - (R_quant - R)
```

Both expressions represent the same realization.

The first form is preferred for implementation because it makes the replacement relationship explicit.

---

## A4.6 Continuous Surface Authority

The authoritative continuous surface remains:

```text
Hf = H0 - R + S
```

`Hf` shall be used by continuous mathematical consumers.

These include, where applicable:

- continuous derivatives;
- landform classification;
- climate/terrain relationships;
- hydrological queries;
- continuous bank displacement;
- cave overburden;
- continuous surface suitability;
- volumetric density.

`Hterrain` is a downstream realization product.

It shall not replace `Hf` as the mathematical source of truth.

---

## A4.7 One-Way Realization Boundary

The dependency direction is:

```text
Continuous Mathematical Domain
        |
        v
R
        |
        v
R_quant
        |
        v
Hterrain
        |
        v
Minecraft Voxel Materialization
```

The broader continuous dependency remains:

```text
H0 -> R -> H* -> S -> Hf
```

No information from `R_quant`, `Hterrain`, blocks, or heightmaps may flow back into:

```text
R
S
Hf
Af
continuous derivatives
channel topology
lake topology
continuous bank geometry
```

unless a future specification explicitly introduces an iterative solver.

No such iterative solver is introduced by this amendment.

---

## A4.8 Deposition Ordering

Deposition remains evaluated against the continuously incised surface:

```text
H* = H0 - R
```

Therefore:

```text
H0
 |
 v
R
 |
 v
H*
 |
 v
S
 |
 v
Hf
```

Only after `Hf` has been established is `R` replaced by its discrete realization:

```text
R -> R_quant
```

giving:

```text
Hterrain = H0 - R_quant + S
```

Consequently, discrete channel stepping cannot become an input to deposition.

The existing deposition budget remains:

```text
0 <= S <= E_total
```

where `E_total` is the explicitly defined available removal budget.

---

## A4.9 Lake Realization Ordering

Lake/basin topology is established in the continuous/topological phase.

Area filtering occurs before discrete terrain realization.

The resulting lake topology may then be consumed by downstream terrain and Minecraft realization systems.

Discrete voxel quantization shall not redefine whether a mathematically identified basin exists.

Lake realization may have its own discrete realization rules, but such rules are downstream products and shall not feed back into continuous lake topology or `Hf`.

---

## A4.10 Final Bank Geometry Ordering

Final bank geometry is established from the final continuous channel centerline before voxel realization.

The dependency is:

```text
Final Channel Centerline
        |
        v
Continuous Bank Geometry
        |
        v
Continuous Terrain Authority
        |
        v
Discrete Terrain Realization
```

Bank geometry shall not consume `R_quant`.

---

## A4.11 Heightmap and Terrain Materialization

The Minecraft adapter shall materialize the realized terrain represented by `Hterrain`.

The conceptual sequence is:

```text
Hf
 |
 | continuous mathematical authority
 |
 v
R_quant
 |
 v
Hterrain
 |
 v
Minecraft terrain columns
 |
 v
Heightmaps
```

Heightmaps must represent the actual generated Minecraft terrain, consistent with the parent heightmap contract.

The heightmap is therefore a downstream realization product rather than a representation of `Hf` independent of discrete terrain realization.

---

## A4.12 Volumetric Density

The existing mathematical density contract remains:

```text
D = Hf - y - W - C
```

where:

```text
Hf = continuous surface authority
W  = volumetric warp
C  = cave contribution
```

`Hterrain` does not replace `Hf` in the density equation.

The density function therefore continues to describe the continuous mathematical terrain model.

Minecraft voxel materialization may introduce the discrete terrain realization represented by `Hterrain`.

This preserves the separation between:

```text
GEOMORPHOLOGICAL MATHEMATICS
```

and:

```text
MINECRAFT VOXEL MATERIALIZATION
```

Section classification and voxel materialization shall consume the appropriate continuous density and downstream realization products according to the parent density/classification contract. They shall not feed realized blocks or heightmaps back into `Hf`.

---

# A5. Preserved GeoEngine Invariants

This amendment does not relax the existing GeoEngine invariants.

The following remain normative.

## A5.1 Determinism

For fixed:

```text
seed
dimension
generatorVersion
config
x
y
z
```

the result shall be deterministic.

---

## A5.2 Chunk Independence

No generated chunk may require another generated chunk for correctness.

World-space mathematical queries outside the current chunk are permitted.

---

## A5.3 Plate Independence

A field evaluated from one plate and the same field evaluated from an adjacent plate must agree at shared world-space coordinates within numerical tolerance.

---

## A5.4 Cache Independence

Cache state cannot affect mathematical output.

---

## A5.5 Surface Continuity

Continuous mathematical surface fields remain continuous within the defined numerical tolerance.

Discrete Minecraft realization may contain intentional terrain steps.

Those steps do not redefine the continuous surface.

---

## A5.6 Pre-Derivative Blending

Fields that are blended before derivative operations must remain blended before those derivatives are calculated.

No downstream discrete realization may be introduced into those derivatives.

---

## A5.7 Hydrology / Laplacian Distinction

A Laplacian is a curvature signal.

It is not watershed accumulation.

`Af` remains a separately derived deterministic hydrological importance field.

---

## A5.8 Hydrology Non-Circularity

Channel incision may not directly depend on derivatives of a surface containing that same incision unless an explicit iterative solver is introduced.

No such iterative solver is introduced by this amendment.

---

## A5.9 Deposition Budget

```text
0 <= S <= E_total
```

shall remain enforced.

---

## A5.10 Climate Bounds

The climate multiplier remains bounded:

```text
K_min <= K <= K_max
```

---

## A5.11 Warp Bounds

Warp amplitude and coordinate distortion remain bounded.

---

## A5.12 Altitude

No unbounded or singular `1/y` altitude expression shall be introduced.

---

## A5.13 Density

The authoritative density relationship remains:

```text
D = Hf - y - W - C
```

---

## A5.14 Scalar Authority

The scalar mathematical implementation defines correctness.

SIMD, caches, rasterization optimizations, regional partitioning, and other implementation optimizations are subordinate to it.

---

## A5.15 Allocation

No steady-state heap allocation shall occur inside the mathematical/raster hot loops.

---

## A5.16 Conservative Section Classification

False SOLID/AIR classifications remain forbidden.

A false BAND classification is acceptable because it performs additional work rather than producing incorrect terrain.

---

# A6. Acceptance Tests

The following tests shall be added or updated.

## A6.1 PlateSeamFieldTest

Evaluate equivalent world-space coordinates from adjacent plates.

Verify equality within numerical tolerance for all relevant continuous fields.

---

## A6.2 BankContinuityTest

Sample bank displacement across a spatial sweep.

Verify that displacement remains continuous within numerical tolerance.

---

## A6.3 BankTargetTransitionTest

Construct a case where the discrete nearest-target selection changes.

Verify that the resulting continuous bank displacement does not exhibit a discontinuity at the target transition.

---

## A6.4 PostMeanderBankConsistencyTest

Verify that bank geometry follows the final post-meander, post-smoothing channel centerline rather than an intermediate centerline.

---

## A6.5 QuantisedIncisionIsolationTest

Verify that changing `stepDeltaY` or the implementation of `Q` does not change:

- `Af`;
- channel topology;
- lake topology;
- continuous derivatives;
- `R`;
- `H*`;
- `S`;
- `Hf`;
- continuous bank displacement.

It may change:

- `R_quant`;
- `Hterrain`;
- Minecraft terrain columns;
- heightmaps;

within the explicitly defined realization semantics.

---

## A6.6 ContinuousSurfaceAuthorityTest

Verify:

```text
Hf = H0 - R + S
```

independently of voxel realization.

---

## A6.7 NoDoubleIncisionTest

For a known continuous incision field:

```text
Hf = H0 - R + S
```

construct:

```text
R_quant = Q(R, stepDeltaY, M_realize)
```

and verify:

```text
Hterrain = H0 - R_quant + S
```

is equivalent within the defined realization precision to:

```text
Hterrain = Hf - (R_quant - R)
```

The test shall explicitly reject:

```text
Hterrain = Hf - R_quant
```

as an incorrect double-incision implementation.

---

## A6.8 ChannelMaskNonCircularityTest

Verify that the channel terrain-realization mask can be derived from existing channel topology and geometry without invoking a second hydrology solve.

---

## A6.9 LakeTopologyQuantisationIsolationTest

Verify that changing discrete terrain realization does not alter continuous lake/basin topology.

---

## A6.10 ReconnectionBoundTest

Verify both:

```text
maximum candidate distance <= reconnectRadius
```

and:

```text
candidate evaluations <= maxReconnectionSamples
```

---

## A6.11 ReconnectionFailSafeTest

Verify deterministic fallback behavior when no valid reconnection target exists.

---

## A6.12 ReconnectionDeterminismTest

Evaluate reconnection under different:

- candidate ordering;
- plate evaluation order;
- chunk generation order;
- cache states;
- worker execution order.

The resulting topology must remain identical.

---

## A6.13 ParameterExerciseTest

Verify that each required configuration parameter materially controls its specified subsystem without altering unrelated mathematical contracts.

In particular:

```text
stepDeltaY
```

may alter discrete realization but must not alter:

```text
R
H*
S
Hf
Af
channel topology
lake topology
continuous bank geometry
```

---

## A6.14 CacheIndependenceTest

Evaluate the same world-space region with:

- cold cache;
- warm cache;
- different eviction patterns;
- different plate evaluation order.

Results must remain identical.

---

## A6.15 RealizationOutsideMaskIdentityTest

For coordinates outside the active channel realization domain, verify:

```text
M_realize(x,z) = 0
```

and:

```text
R_quant(x,z) = R(x,z)
```

unless another explicitly defined terrain-realization rule applies.

This prevents the realization operator from silently replacing non-channel incision semantics with zero incision.

---

## A6.16 RealizationReplacementTest

For active realization coordinates, verify that:

```text
Hterrain = H0 - R_quant + S
```

and:

```text
Hterrain = Hf - (R_quant - R)
```

produce equivalent results within defined realization precision.

Also verify that:

```text
Hterrain = Hf - R_quant
```

does not satisfy the expected terrain result except in degenerate cases where `R = 0`.

---

# A7. Configuration Validation

The following relationships are normative.

```text
plateScale > 0
gridSpacing > 0
terrainSampleSpacing > 0

baseWidth >= 0
maxWidth >= baseWidth
widthScale >= 0

streamDepth >= 0
stepDeltaY > 0

meanderStrength >= 0
smoothingPasses >= 0

bankWidth >= 0
bankSlope >= 0
bankNoise >= 0
bankSteepFactor >= 0
valleySnapRadius >= 0
containmentBerm >= 0

lakeMinArea >= 0
lakeMaxArea >= lakeMinArea

reconnectRadius >= 0
maxReconnectionSamples > 0

wetnessDryCutoff >= 0
wetnessWetReference > wetnessDryCutoff
wetnessMultiplier >= 0
```

Additional relationships shall be added where mathematically required.

Arbitrary cross-constraints shall not be invented solely for configuration symmetry.

---

# A8. Performance and Work Bounding

Plate/region partitioning exists to reduce repeated expensive field evaluation and provide bounded regional caching.

It must not become a correctness dependency.

`reconnectRadius` bounds search space.

`maxReconnectionSamples` bounds search work.

Both are required because a bounded spatial radius alone does not guarantee bounded computational work.

GeoEngine shall never block chunk generation waiting for:

- another chunk;
- another worker;
- another plate;
- hydrology generation;
- cache population;
- feature placement.

If required mathematical data is unavailable from cache, it shall be deterministically recomputed.

The `R -> R_quant` realization shall be a bounded downstream operation and shall not trigger a new hydrology solve.

---

# A9. Cache Architecture

This amendment extends the existing cache architecture.

Regional caches may contain:

- sampled terrain;
- derivatives;
- hydrological fields;
- drainage topology;
- channel geometry;
- bank geometry;
- lake topology;
- diagnostics.

Cached values must remain recomputable from deterministic inputs.

No mutable cross-chunk or cross-plate river state is required for correctness.

No cached `R_quant`, block state, heightmap, or generated terrain may become an upstream source of mathematical truth.

---

# A10. Macro-Sampling Replacement

The former fixed macro-grid architecture based on:

```text
4-block spacing
6x6 derivative halo
16x16 final interpolation
```

is superseded.

The replacement architecture is:

```text
World Coordinates
       |
       v
Plate / Region Partition
       |
       v
Configurable Sampling
       |
       v
Continuous Mathematical Fields
       |
       v
Hydrology / Channel Geometry
       |
       v
Continuous Hf
       |
       v
Discrete Terrain Realization
```

The exact number of regional samples and storage layout are implementation-defined.

Fixed scratchpad dimensions previously associated with the superseded macro-grid are no longer normative.

---

# A11. Diagnostics

Development diagnostics shall expose the distinction between continuous mathematics and discrete realization.

At minimum, diagnostics shall support inspection of:

```text
Af
M_channel
M_realize
channelWidth
channelCenterline
R
H*
S
Hf
R_quant
Hterrain
bankDisplacement
lakeMask
reconnectionTarget
wetness
```

Diagnostics shall expose both:

```text
R
R_quant
H*
Hf
Hterrain
```

and shall make the relationship visible:

```text
incision quantization error = R_quant - R
```

This difference is expected when discrete channel realization is active.

The diagnostic system shall not report:

```text
R_quant - R
```

as additional geomorphological incision.

Where practical, diagnostics shall identify whether a value belongs to:

```text
CONTINUOUS MATHEMATICAL DOMAIN
```

or:

```text
DISCRETE REALIZATION DOMAIN
```

This distinction is required when diagnosing apparent discrepancies between mathematical terrain and Minecraft voxel terrain.

---

# A12. Architectural Summary

The resulting architecture is:

```text
                 WORLD-SPACE DETERMINISTIC MODEL
                              |
             +----------------+----------------+
             |                                 |
             v                                 v
      Plate / Region A                  Plate / Region B
      Evaluation / Cache                Evaluation / Cache
             |                                 |
             +----------------+----------------+
                              |
                              v
                    CONTINUOUS FIELDS
                              |
                              v
                    PREDICTIVE HYDROLOGY
                              |
                              v
                  DRAINAGE / LAKE TOPOLOGY
                              |
                              v
                   CHANNEL RECONNECTION
                              |
                              v
                   CHANNEL GEOMETRY
                              |
                  +-----------+-----------+
                  |                       |
                  v                       v
             BANK GEOMETRY        CONTINUOUS INCISION
                  |                       |
                  +-----------+-----------+
                              |
                              v
                         DEPOSITION
                              |
                              v
                             Hf
                              |
                  CONTINUOUS SURFACE AUTHORITY
                              |
                              v
                      REALIZATION MASKS
                              |
                              v
                  R -> R_quant replacement
                              |
                              v
                         Hterrain
                              |
                              v
                  MINECRAFT TERRAIN
                              |
                              v
                       HEIGHTMAPS
```

The continuous mathematical chain is:

```text
H0 = T - E
 |
 v
R
 |
 v
H* = H0 - R
 |
 v
S
 |
 v
Hf = H0 - R + S
```

The realization boundary is:

```text
                 CONTINUOUS DOMAIN
                        |
                        v
                       Hf
                        |
                ONE-WAY REALIZATION
                        |
              +---------+---------+
              |                   |
              v                   v
             R                  M_realize
              \                   /
               \                 /
                v               v
                    R_quant
                       |
                       v
                   Hterrain
                       |
                       v
              Minecraft terrain
                       |
                       v
                  Heightmaps
```

The critical algebra is:

```text
Hf       = H0 - R + S

Hterrain = H0 - R_quant + S

therefore

Hterrain = Hf - (R_quant - R)
```

Thus:

```text
R
```

is the geomorphological incision,

```text
R_quant
```

is its discrete realization,

and:

```text
R_quant - R
```

is realization/quantization error.

There is no second incision term.

No discrete realization product feeds back into continuous geomorphological mathematics.

---

# A13. Changelog

This amendment:

1. Replaces the fixed macro-grid model with deterministic plate/region partitioning.
2. Defines canonical plate identity using `dimensionId`.
3. Establishes that plate partitioning is an evaluation/cache architecture rather than mathematical state.
4. Requires absolute world-space coordinates for continuous field evaluation.
5. Establishes plate-boundary continuity requirements.
6. Promotes hydrological/channel parameters to explicit configuration contracts.
7. Defines deterministic channel identification from `Af`.
8. Defines bounded channel width.
9. Separates continuous incision `R` from discrete realization `R_quant`.
10. Establishes `Hf = H0 - R + S` as the continuous surface authority.
11. Defines `R_quant` as a realization of `R`, not additional incision.
12. Makes `stepDeltaY` a terrain-realization parameter.
13. Moves bank geometry onto the final post-meander/post-smoothing centerline.
14. Requires continuous bank displacement despite any internal discrete candidate selection.
15. Establishes lake/basin topology before discrete terrain realization.
16. Adds both spatial and computational reconnection bounds.
17. Requires deterministic reconnection candidates, ordering, tie-breaking, and fallback behavior.
18. Prohibits recursive hydrological recomputation during realization.
19. Establishes a one-way continuous-to-discrete boundary.
20. Preserves the parent `D = Hf - y - W - C` density contract.
21. Adds explicit tests for quantization isolation, double incision, bank continuity, lake topology, and bounded reconnection.
22. Extends diagnostics to distinguish `Hf`, `R_quant`, and realized terrain.
23. Preserves existing determinism, chunk-independence, cache-independence, continuity, hydrology non-circularity, deposition, climate, warp, density, allocation, and conservative-classification invariants.
24. Resolves the `R_quant` semantic contradiction by defining `R_quant` as a discrete replacement realization of `R`, rather than an additional incision.
25. Defines the realized terrain as `Hterrain = H0 - R_quant + S`.
26. Establishes `Hterrain = Hf - (R_quant - R)` as the equivalent relationship between continuous and realized surfaces.
27. Explicitly prohibits `Hterrain = Hf - R_quant`.
28. Defines `R_quant - R` as realization/quantization error rather than geomorphological incision.
29. Establishes `stepDeltaY` as a realization parameter that cannot alter continuous geomorphological state.
30. Clarifies that heightmaps represent realized Minecraft terrain rather than independently representing `Hf`.
31. Separates `M_channel`, which identifies channel topology, from `M_realize`, which defines the domain in which channel-specific discrete realization is applied.
32. Requires `R_quant = R` outside the active channel realization domain unless another explicit terrain-realization rule applies.
33. Adds explicit acceptance tests for realization behavior outside the mask and for replacement algebra.

---

# A14. Final Normative Position

GeoEngine shall use plate/region partitioning to organize expensive regional evaluation and caching while retaining a single deterministic world-space mathematical model as the source of truth.

Predictive hydrology shall construct deterministic drainage topology and accumulation-derived fields without mutable cross-chunk or cross-plate state.

Channel geometry shall be established before bank geometry.

Bank geometry shall operate on the final continuous channel centerline.

Bank displacement shall remain continuous even when discrete graph or candidate selection is used internally.

Lake and basin topology shall be established in the continuous/topological domain before discrete terrain realization.

The continuous surface shall be:

```text
Hf = H0 - R + S
```

with:

```text
H0 = T - E
H* = H0 - R
```

`Hf` is the continuous scalar authority.

Discrete terrain realization replaces the continuous incision `R` with its deterministic realization:

```text
R_quant = Q(R, stepDeltaY, M_realize)
```

Inside the active realization domain, `Q` applies the configured deterministic vertical quantization. Outside that domain, `R_quant` preserves `R` unless another explicitly defined terrain-realization rule applies.

The realized terrain is:

```text
Hterrain = H0 - R_quant + S
```

and equivalently:

```text
Hterrain = Hf - (R_quant - R)
```

`R_quant - R` is realization/quantization error.

`R_quant` is never an additional incision.

The implementation shall never use:

```text
Hterrain = Hf - R_quant
```

because that would double-count channel incision.

`stepDeltaY` may change `R_quant` and therefore `Hterrain`, terrain columns, and heightmaps within the defined realization semantics. It shall not change:

```text
R
H*
S
Hf
Af
channel topology
lake topology
continuous bank geometry
continuous derivatives
```

The discrete realization remains downstream of all continuous geomorphological mathematics and cannot feed information back into the continuous solver.

This preserves the parent specification's separation between the continuous geomorphological surface `Hf` and Minecraft voxel materialization, while permitting intentional stepped channel-bed realization.

The exact implementations of accumulation, lake detection, bank target search, continuous bank influence, and the `R -> R_quant` realization operator remain implementation-defined, subject to all normative contracts established above.
