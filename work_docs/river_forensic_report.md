# GeoEngine River Implementation — Forensic Report

**Date:** 2026-10-01  
**Status:** Phase 9 Sprint R complete; evaluation in progress  
**Scope:** Full river system (flow accumulation, incision, channels, water, meanders, deposition)

---

## Executive Summary

Sprint R resolved the "particles yes, cuts no" defect by unifying flow accumulation semantics (removing log1p transform). Rivers now cut into terrain. However, evaluation reveals 9 remaining issues spanning P1–P4 severity. Three issues (channel initiation density, channel width at low flow, incision depth variance) are the most impactful and should be addressed first.

---

## Issue Forensics

### P1 — Channel Initiation Too Aggressive

**Root Cause:** Channel initiation threshold is 2.5 cell counts (`CHANNEL_INITIATION_FLOW` in `ChannelField.java`). On a 24×24 (576-cell) region, almost any cell with 2–3 upstream cells qualifies as a channel.

**In-Game Presentation:**
- Overly dense network of tiny stream channels
- Terrain appears "veiny" or "cracked" rather than having distinct river valleys
- Small tributaries everywhere, including on flat plains where no water should flow
- Visual clutter detracts from major river systems

**Evidence:** `ChannelField.CHANNEL_INITIATION_FLOW = 2.5` — this threshold was chosen for log1p-transformed values (where 2.5 ≈ 12 raw cells). With raw values, it's far too low.

**Proposed Fix:** Increase threshold to ~5.0–8.0 raw cell counts. This corresponds to cells with 5–8 upstream cells, which is more realistic for channel initiation.

---

### P2 — Channel Width Too Wide at Low Flow

**Root Cause:** Channel width formula `W = 3.0 + 25.0 * (1 - exp(-strength * 0.18))` produces widths that are too large at low flow values.

**In-Game Presentation:**
- Small streams (A_f=5) are 12 blocks wide — wider than many real creeks
- Visual disconnect between flow accumulation (small) and channel width (large)
- "Big channel, little water" appearance
- Exaggerated floodplains for minor tributaries

**Evidence:** At A_f=5 (just above threshold), `strength = 2.5`, `W = 3 + 25*(1 - exp(-0.45)) = 12` blocks. At A_f=10, `W = 20.8` blocks. Width grows too rapidly at low flow.

**Proposed Fix:** Recalibrate width formula for lower flow values. Options:
- Reduce saturation width for low-order channels
- Use steeper exponential coefficient (e.g., 0.25 instead of 0.18)
- Introduce a flow-dependent scaling factor that tapers at low flow

---

### P2 — Incision Depth Variance Too Extreme

**Root Cause:** With raw flow accumulation values, incision depth scales exponentially with flow. Small streams have ~2 blocks of incision; trunk rivers can have 16–18 blocks (or 28 with target config).

**In-Game Presentation:**
- Dramatic depth contrast between small streams and major rivers
- Small streams look like shallow rills; major rivers look like canyons
- May appear unrealistic for some biomes (e.g., tropical lowlands)
- Vertical exaggeration can make terrain look "carved" rather than naturally eroded

**Evidence:** `RiverField.computeIncision` uses `flowStrength = 1 - exp(-(A_f - 1.8) * 0.15)`. At A_f=5, flowStrength=0.36, incision≈6 blocks (default config). At A_f=100, flowStrength=0.999, incision≈16 blocks. 2.7x depth ratio.

**Proposed Fix:**
- Clamp incision depth at low flow to a minimum depth (e.g., 3–4 blocks)
- Reduce channelSteepness parameter (e.g., from 0.15 to 0.10) to flatten the flow-strength curve
- Consider biome-dependent incision scaling

---

### P2 — Water Depth Too Shallow at Low Flow

**Root Cause:** Water depth formula uses the same exponential saturation as incision, but scales with incision depth. For small streams, incision is shallow, so water depth is very shallow.

**In-Game Presentation:**
- Small streams appear dry or nearly dry
- Water level barely above channel bed
- Difficult to distinguish water from wet terrain
- "Dry riverbed" appearance despite channel being classified as active

**Evidence:** `ScalarFieldKernel.evaluateFullColumn` computes water depth as `flowStrength * min(incision, 4.0)`. At A_f=5, flowStrength=0.36, incision=6, waterDepth=0.36*4=1.4 blocks. After rounding, this may be 1 block or even 0 blocks.

**Proposed Fix:**
- Add minimum water depth (e.g., 2 blocks) for active channels
- Scale water depth independently of incision (use channel order instead)
- Consider freeboard (water above bed) for larger channels

---

### P3 — Meander Wavelength and Amplitude Need Tuning

**Root Cause:** Meander parameters (wavelength range 100–800 blocks, amplitude fraction 0.6) may not produce visually natural patterns.

**In-Game Presentation:**
- Meanders may look too tight or too loose
- Amplitude may be too small (nearly straight) or too large (exaggerated loops)
- Pattern may not match expected natural river morphology

**Evidence:** `MeanderField` constants: `MEANDER_WAVELENGTH_MIN = 100`, `MEANDER_WAVELENGTH_MAX = 800`, `MEANDER_AMPLITUDE_FRACTION = 0.6`. These are uncalibrated defaults.

**Proposed Fix:**
- Calibrate wavelength and amplitude against reference terrain
- Consider flow-dependent wavelength (larger rivers = longer wavelength)
- Test visually in-game

---

### P3 — Sediment Transport Capacity May Not Scale Correctly

**Root Cause:** Deposition formula uses channel factor and order to reduce deposition in channels, but the scaling may not match natural sediment transport capacity.

**In-Game Presentation:**
- Channels may fill with sediment too quickly (or too slowly)
- Alluvial fans may be too large or too small
- Delta lobes may not form naturally

**Evidence:** `DepositionField.computeDeposition` uses channel factor to suppress deposition in channels. With raw flow values, channel factor is more effective, but the overall scaling may be off.

**Proposed Fix:**
- Calibrate deposition rates against reference terrain
- Test sediment transport capacity scaling with flow
- Adjust channel factor scaling as needed

---

### P3 — Channel Cross-Section Is Simplified

**Root Cause:** U-trough cross-section is smooth and symmetric, but real channels are more complex.

**In-Game Presentation:**
- Channels look too "perfect" and uniform
- Lack of braiding, point bars, and cut banks in cross-section
- Less natural appearance

**Evidence:** `ChannelField.computeChannelFactor` uses a cosine U-trough. `FeatureGrammar` computes feature mask for point bars/cut banks, but the cross-section itself is still symmetric.

**Proposed Fix:**
- Introduce cross-section asymmetry based on signed lateral offset
- Model braided channels for low-gradient, high-sediment environments
- Add channel bar formation

---

### P4 — No Fluvial Terrace Formation

**Root Cause:** Terrace formation requires base-level changes over time, which are not modeled.

**In-Game Presentation:**
- Missing terraces along river valleys
- Less mature appearance for old river systems

**Proposed Fix:**
- Model base-level changes (e.g., sea level fluctuations)
- Add terrace formation logic to erosion/depsoition pipeline
- Requires temporal dimension (not feasible in current architecture)

---

### P4 — No Channel Migration or Avulsion

**Root Cause:** Channel migration and avulsion require dynamic processes over time.

**In-Game Presentation:**
- Rivers don't change course
- No oxbow lakes or abandoned channels
- Less dynamic landscape

**Proposed Fix:**
- Model channel migration over time
- Add avulsion logic for overflow events
- Requires temporal dimension (not feasible in current architecture)

---

## Sprint Plan

### Sprint R.1 — Channel Initiation Calibration (P1)

**Goal:** Reduce channel network density by increasing initiation threshold.

**Tasks:**
1. Increase `ChannelField.CHANNEL_INITIATION_FLOW` from 2.5 to 5.0
2. Regenerate golden hydrology grids
3. Run full test suite
4. Verify in-game: channel network density is reduced

**Acceptance Criteria:**
- Channel network is less dense
- Small rills on flat terrain are eliminated
- Major river systems remain intact
- All 131 tests pass

### Sprint R.2 — Channel Width Recalibration (P2)

**Goal:** Recalibrate channel width formula for lower flow values.

**Tasks:**
1. Reduce exponential coefficient in `ChannelField.getWidth` from 0.18 to 0.25
2. Regenerate golden hydrology grids
3. Run full test suite
4. Verify in-game: channel widths are appropriate for flow values

**Acceptance Criteria:**
- Small streams (A_f=5) are ~5–7 blocks wide
- Major rivers (A_f=100+) remain ~25–28 blocks wide
- All 131 tests pass

### Sprint R.3 — Incision Depth and Water Depth Tuning (P2)

**Goal:** Reduce incision depth variance and improve water depth at low flow.

**Tasks:**
1. Reduce `riverChannelSteepness` from 0.15 to 0.10
2. Add minimum incision depth (3 blocks) for active channels
3. Add minimum water depth (2 blocks) for active channels
4. Regenerate golden hydrology grids
5. Run full test suite
6. Verify in-game: incision and water depth are appropriate

**Acceptance Criteria:**
- Incision depth variance is reduced
- Small streams have visible water
- Major rivers still have deep incision
- All 131 tests pass

### Sprint R.4 — Meander and Deposition Calibration (P3)

**Goal:** Calibrate meander parameters and sediment transport.

**Tasks:**
1. Adjust `MeanderField` constants based on visual testing
2. Calibrate `DepositionField` scaling against reference terrain
3. Regenerate golden hydrology grids
4. Run full test suite
5. Verify in-game: meanders and deposition are natural-looking

**Acceptance Criteria:**
- Meander patterns look natural
- Sediment transport capacity is appropriate
- All 131 tests pass

### Sprint R.5 — Cross-Section Asymmetry (P3)

**Goal:** Introduce cross-section asymmetry based on signed lateral offset.

**Tasks:**
1. Modify `ChannelField.computeChannelFactor` to use signed lateral offset
2. Add cross-section asymmetry based on meander phase
3. Regenerate golden hydrology grids
4. Run full test suite
5. Verify in-game: channels have natural cross-sections

**Acceptance Criteria:**
- Channels have asymmetric cross-sections
- Point bars and cut banks are visible
- All 131 tests pass

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Calibration changes break seam continuity | Medium | Medium | Run seam continuity tests after each calibration |
| In-game visual appearance is subjective | High | High | Frequent in-game testing and user feedback |
| Performance regression from wider channels | Low | Medium | Run benchmark tests after calibration |
| Golden file regeneration introduces drift | Low | Medium | Compare golden files before and after changes |

---

## Conclusion

Sprint R resolved the critical "particles yes, cuts no" defect. The remaining issues are calibration and tuning problems that can be addressed in subsequent sprints. The P1 and P2 issues (channel initiation, channel width, incision depth) should be prioritized as they have the most significant impact on in-game appearance.

The P3 and P4 issues are lower priority and can be addressed in future phases.

---

**Prepared by:** GeoEngine Development Team  
**Date:** 2026-10-01  
**Version:** 1.0
