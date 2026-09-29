# GeoEngine Test Suite

## Overview

The GeoEngine test suite validates the correctness of the bounded deterministic geomorphic terrain engine across all 10 phases of the generation pipeline. The suite comprises:

- **Phase tests**: Per-phase correctness validation (P1-P10)
- **Invariant tests**: Cross-phase continuous gate (C1-C16)
- **Property tests**: Metamorphic and structural properties (P-M-01 to P-M-05)
- **Golden tests**: Binary regression artifacts (P-H-01, P-H-02)
- **Performance tests**: Timing and allocation measurements (P-J-01 to P-J-03)
- **Adapter tests**: NeoForge integration (P4-02 to P4-05)

## Running the Tests

### All tests (default)
```bash
./gradlew test
```

### Specific test class
```bash
./gradlew test --tests "com.omms.geoenginecore.test.Phase2RiverCarvingTest"
```

### Specific test method
```bash
./gradlew test --tests "com.omms.geoenginecore.test.Phase2RiverCarvingTest.testNonZeroRiverCarving"
```

### Regenerate golden artifacts
```bash
./gradlew test -Dupdate.goldens=true
```

## Test Packages

```
src/test/java/com/omms/geoenginecore/test/
├── fixtures/          # Shared test infrastructure
├── invariants/        # Cross-phase invariant tests (C1-C16)
├── phase1/            # Phase 1 tests
├── phase2/            # Phase 2 tests
├── phase3/            # Phase 3 tests
├── ...
├── phase10/           # Phase 10 tests
├── property/          # Property/metamorphic tests (P-M-*)
├── goldens/           # Golden regression tests (P-H-*)
├── perf/              # Performance/reliability tests (P-J-*)
└── adapter/           # NeoForge integration tests (P4-*)
```

## Tag Taxonomy

Tests use JUnit tags for categorization:

- `must`: Must-pass for phase seal (MUST priority from spec)
- `should`: Should-pass, may be skipped in fast CI (SHOULD priority)
- `phase1` through `phase10`: Phase-specific tests
- `invariant`: Cross-phase invariant tests
- `property`: Property/metamorphic tests
- `golden`: Golden regression tests
- `perf`: Performance tests (report-only)
- `timeout`: Reliability timeout tests
- `slow`: Tests expected to take >5s
- `gametests`: NeoForge integration tests requiring Forge environment

## Phase Seal Rules

A phase may be sealed (considered complete) when:

1. All `must`-tagged tests for that phase pass
2. All relevant invariant tests pass
3. No regression in golden artifacts (if applicable)

Tests that are known to fail due to product bugs (not test bugs) are documented in the `open` section of the compaction file and excluded from the seal criteria.

## Known Expected Failures

As of the last test run, the following tests are expected to fail due to a known product bug in the routing lattice channel factor computation:

- P2-10: Non-zero river carving over full hydrology region
- P2-11: Incision couples to surface lowering (Hf coupling)
- P9-01: Water surface level computed when R >= EPS_R inland

These will pass once the channel factor product bug is fixed.

## Golden Artifacts

Golden artifacts are stored under `src/test/resources/goldens/`. Each golden test:

1. Creates the artifact on first run
2. Compares against the stored artifact on subsequent runs
3. Fails if the output differs from the golden

To update goldens after intentional math changes:
```bash
./gradlew test -Dupdate.goldens=true
```

Commit the updated golden files with a message containing "golden bump".

## Traceability

| Test ID | TECHSPEC Section | Description |
|---------|-----------------|-------------|
| P1-09 | §9 | Seed domain decorrelation |
| P1-10 | §14 | H₀ identity |
| P2-10 | §27, §29 | Non-zero river carving |
| P2-11 | §31 | Incision couples to surface lowering |
| P2-12 | §30 | U-curve channel corridor |
| P2-13 | §27 | Channel initiation thresholds |
| P2-14 | §36 | F_channel bounded [0,1] |
| P3-01 | §51 | Density identity |
| P3-06 | §51 | Density monotonicity |
| P4-02 | §59 | Heightmap continuity |
| P4-03 | §59 | Hf fidelity |
| P4-04 | §59 | Section palette |
| P4-05 | §59 | Generation order |
| P9-01 | §149 | Water surface level |
| C1 | §137 | Determinism |
| C2 | §59 | Seam continuity |
| C3 | §22 | Deposition budget |
| C4 | §51 | Surface height identity |
| P-M-01 | — | Translation invariance |
| P-M-02 | §22 | Erosion non-negativity |
| P-M-03 | §27, §29 | Channel threshold boundaries |
| P-M-04 | — | Seed robustness |
| P-M-05 | §47 | Surface height range |
| P-H-01 | — | Golden hydrology grids |
| P-H-02 | — | Golden basin IDs |
| P-J-01 | §111 | Performance measurements |
| P-J-03 | — | Timeout reliability |

## Test Changelog

| Date | Change | Rationale |
|------|--------|-----------|
| 2026-09-28 | Sprint A: P2-10..P2-14 | River carving correctness tests |
| 2026-09-28 | Sprint B: P3-01, P3-06 | Phase 3 density tests |
| 2026-09-28 | Sprint C: P1-09, P1-10 | Phase 1 hygiene tests |
| 2026-09-28 | Sprint D: P9-01 | Phase 9 wet rivers test |
| 2026-09-28 | Sprint E: P4-02..P4-05 | Phase 4 adapter tests |
| 2026-09-29 | Sprint F: C1-C4 | Invariant extraction |
| 2026-09-29 | Sprint G: P-M-01..P-M-05 | Property/metamorphic tests |
| 2026-09-29 | Sprint H: P-H-01, P-H-02 | Golden regression tests |
| 2026-09-29 | Sprint J: P-J-01, P-J-03 | Performance/reliability tests |
| 2026-09-29 | Sprint K: Documentation | TESTING.md, traceability |
| 2026-09-29 | Housekeeping: Package reorganization | Tests moved to phase-specific packages |

## TODO

- [ ] Sprint I: Adapter test de-duplication (skipped, deferred)
- [ ] P2-15: Circularity test (optional)
- [ ] P9-02/P9-03: Water block placement (requires @GameTest)
- [ ] Remaining invariants C5-C16 (12 more)
