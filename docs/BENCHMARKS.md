# GeoEngine — Benchmark Record

Durable performance record required by TECHSPEC §221 ("benchmark
results are recorded"). Update this file in the same commit that lands
math-core changes, and re-baseline the committed floor when the core
(noise, derivatives, fluvial pass, classification) changes.

## Measurement environment

| Item | Value |
|---|---|
| OS | EndeavourOS (Ubuntu 22.04-based), x86_64 |
| CPU | AMD Ryzen 9 5950X, 16 cores / 32 threads |
| JVM | Eclipse Temurin 21.0.12.1 (Gradle-provisioned toolchain) |
| SIMD | `jdk.incubator.vector` enabled via `--add-modules` (Gradle `test` task) |
| Date | 2026-09-21 |
| Baseline commit | `b84ad0f` (last code-verified commit); values measured on the Phase 1 conformance tree that supersedes it |

## Measured results (2026-09-21)

| Metric | Measured | Committed floor | Headroom |
|---|---|---|---|
| Rasterized chunk throughput (scalar kernel) | **1253.7 chunks/sec** | 600 chunks/sec | 2.09× |
| Steady-state heap allocation (rasterized chunk) | **0 bytes/chunk** | 0 bytes/chunk (hard invariant) | — |

## Measurement protocol

- **Throughput** — `Phase1BenchmarkTest`: 512-chunk JIT warmup, then
  2048 chunks timed with `System.nanoTime()` through
  `ScalarFieldKernel.rasterizeSurfaceChunk` (256 columns × 16 Y levels,
  calibrated Overworld configuration, thread-confined
  `WorkerScratchpad`). The suite fails if the rate drops below the
  committed floor of 600 chunks/sec.
- **Zero steady-state allocation** — `Phase8PerformanceTest`
  (TECHSPEC §62, §164): adaptive warmup in 256-chunk windows until a
  window measures 0 bytes (ThreadMXBean `getThreadAllocatedBytes`),
  then a 1000-chunk window must also measure 0 bytes. Measured
  steady-state allocation: **0 bytes/chunk**. The pass additionally
  verifies the SIMD/scalar parity bound (≤ 1e-5) and 16-thread
  determinism of the raster pipeline.
- **Concurrent throughput** — `Phase8PerformanceTest`: 16 workers
  rasterize disjoint chunks; the aggregate rate is reported to the
  Gradle test log (not floored; informational).

## Notes

- The committed floor (600) is ~2× below this machine's measured
  throughput, leaving headroom for slower CI hardware while still
  catching order-of-magnitude regressions. Throughput scales with
  CPU; re-measure and re-baseline if the math core changes.
- The pre-conformance steady-state allocation was 12,288 bytes/chunk
  (256 columns × 48 B): every flow-accumulation lookup re-boxed a
  `Long` key against a `ConcurrentHashMap<Long, DrainageGraph>`, and
  the single-slot thread-confined register missed on every blended
  seam column. Fixed by a striped, primitive-`long`-keyed
  open-addressing cache (lock-free hit path) plus a 4-slot
  direct-mapped register in the thread-confined `WorkerScratchpad`.
- The SIMD kernel (`VectorFieldKernel`) is currently parity-checked
  (≤ 1e-5 vs the scalar kernel) but not throughput-floored; add a
  floor once the SIMD pass is performance-tuned.
