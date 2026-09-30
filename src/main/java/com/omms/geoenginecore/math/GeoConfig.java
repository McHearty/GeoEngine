package com.omms.geoenginecore.math;

/**
 * Immutable GeoEngine configuration (TECHSPEC §63-§64).
 *
 * <p>Every terrain-influencing parameter is captured here so that
 * {@link #configHash()} can participate in the deterministic key
 * K = (seed, dimension, version, config, coordinate) (TECHSPEC §8).
 * Instances are records and therefore value-equal, and the compact
 * constructor rejects invalid combinations, so a live instance is
 * always internally consistent.
 */
public record GeoConfig(
    /** Generator version baked into the deterministic key; changing it alters every generated value. */
    int generatorVersion,
    /** Dimension identity used for seed-domain separation (TECHSPEC §9). */
    int dimensionId,
    /** Inclusive lowest buildable Y of the dimension. */
    int worldMinY,
    /** Exclusive upper bound of the dimension's vertical range. */
    int worldMaxY,
    /** Sea/fluid level used by water masks and altitude normalization. */
    int seaLevel,
    /** Frequency of the very-low continental tectonic band T₀. */
    double tectonicFreqLow,
    /** Frequency of the primary mountain-belt tectonic band T₁. */
    double tectonicFreqA,
    /** Frequency of the secondary ridge tectonic band T₂. */
    double tectonicFreqB,
    /** Amplitude of the continental base band, in blocks. */
    double tectonicAmpLow,
    /** Amplitude of the primary mountain-belt relief, in blocks. */
    double tectonicAmpA,
    /** Amplitude of the secondary ridge relief, in blocks. */
    double tectonicAmpB,
    /** Uplift remap exponent p in T = A·uᵖ; must be ≥ 1 to concentrate relief (TECHSPEC §14). */
    double upliftExponent,
    /** Spatial frequency of the anisotropic stress warp. */
    double stressFrequency,
    /** Peak horizontal displacement of the stress warp, in blocks. */
    double stressAmplitude,
    /** Jacobian bound the stress warp must satisfy (TECHSPEC §16, §64). */
    double stressMaxJacobian,
    /** Frequency of the geological epoch (age) field. */
    double epochFrequency,
    /** Frequency of the horizontal temperature field. */
    double climateTempFrequency,
    /** Frequency of the horizontal humidity field. */
    double climateHumidFrequency,
    /** Lower bound K_min of the climate multiplier (TECHSPEC §20). */
    double climateMin,
    /** Upper bound K_max of the climate multiplier (TECHSPEC §20). */
    double climateMax,
    /** Approximate temperature lapse rate per vertical block (TECHSPEC §19). */
    double lapseRatePerBlock,
    /** Base long-term surface-lowering budget, in blocks (TECHSPEC §22). */
    double baseErosionRate,
    /** Global bound on the volumetric 3-D rock warp W (TECHSPEC §44). */
    double maxWarpAmplitude,
    /** Radius of the high-cost 3-D evaluation band around the final surface (TECHSPEC §51). */
    int surfaceBandRadius,
    /** Upper bound R_max of fluvial river incision, in blocks (TECHSPEC §28). */
    double riverMaxIncision,
    /** Steepness of the drainage-accumulation-to-incision transfer function. */
    double riverChannelSteepness,
    /** Fixed-iteration count K for bounded drainage refinement (TECHSPEC §24). */
    int drainageIterations,
    /** Inclusive lower Y of the cave placement envelope. */
    int caveMinY,
    /** Upper Y of the cave placement envelope. */
    int caveMaxY
) {
    /**
     * Validates the configuration (TECHSPEC §64).
     *
     * <p>Rejects non-finite floating point values (NaN and ±Infinity),
     * inverted vertical bounds, out-of-range sea level, non-positive
     * wavelengths, negative amplitudes, uplift exponents below 1,
     * stress warps exceeding their Jacobian bound, inverted cave
     * envelopes, cave envelopes outside world bounds, and inconsistent
     * climate or river bounds. Any failure throws before a
     * configuration instance can exist.
     *
     * @throws IllegalArgumentException if any invariant is violated
     */
    public GeoConfig {
        // §64: reject non-finite values up front. NaN compares false
        // against every relational check below, so it must be caught
        // explicitly or it would slip through the constructor.
        if (isNonFinite(tectonicFreqLow) || isNonFinite(tectonicFreqA) || isNonFinite(tectonicFreqB)
                || isNonFinite(tectonicAmpLow) || isNonFinite(tectonicAmpA) || isNonFinite(tectonicAmpB)
                || isNonFinite(upliftExponent)
                || isNonFinite(stressFrequency) || isNonFinite(stressAmplitude)
                || isNonFinite(stressMaxJacobian) || isNonFinite(epochFrequency)
                || isNonFinite(climateTempFrequency) || isNonFinite(climateHumidFrequency)
                || isNonFinite(climateMin) || isNonFinite(climateMax)
                || isNonFinite(lapseRatePerBlock) || isNonFinite(baseErosionRate)
                || isNonFinite(maxWarpAmplitude)
                || isNonFinite(riverMaxIncision) || isNonFinite(riverChannelSteepness)) {
            throw new IllegalArgumentException("Configuration contains non-finite values");
        }
        if (worldMinY >= worldMaxY) {
            throw new IllegalArgumentException("worldMinY must be strictly less than worldMaxY");
        }
        // §64: the cave placement envelope must be ordered (a degenerate
        // zero-height envelope means "no caves" and is legal) and must
        // lie inside the world vertical bounds.
        if (caveMinY > caveMaxY) {
            throw new IllegalArgumentException("Cave envelope inverted: caveMinY " + caveMinY + " > caveMaxY " + caveMaxY);
        }
        if (caveMaxY > worldMaxY || caveMinY < worldMinY) {
            throw new IllegalArgumentException("Cave envelope [" + caveMinY + ", " + caveMaxY + "] outside world bounds [" + worldMinY + ", " + worldMaxY + "]");
        }
        if (seaLevel < worldMinY || seaLevel > worldMaxY) {
            throw new IllegalArgumentException("seaLevel out of world bounds");
        }
        if (tectonicFreqLow <= 0.0 || tectonicFreqA <= 0.0 || tectonicFreqB <= 0.0) {
            throw new IllegalArgumentException("Tectonic frequencies must be positive");
        }
        if (tectonicAmpLow < 0.0 || tectonicAmpA < 0.0 || tectonicAmpB < 0.0) {
            throw new IllegalArgumentException("Tectonic amplitudes cannot be negative");
        }
        if (upliftExponent < 1.0) {
            throw new IllegalArgumentException("upliftExponent must be >= 1.0 to concentrate relief");
        }
        if (stressFrequency <= 0.0 || stressAmplitude < 0.0) {
            throw new IllegalArgumentException("Invalid stress configuration");
        }
        double maxJac = stressAmplitude * stressFrequency;
        if (maxJac > stressMaxJacobian) {
            throw new IllegalArgumentException("Stress warp exceeds Jacobian bound: " + maxJac + " > " + stressMaxJacobian);
        }
        if (epochFrequency <= 0.0) {
            throw new IllegalArgumentException("epochFrequency must be positive");
        }
        if (climateTempFrequency <= 0.0 || climateHumidFrequency <= 0.0) {
            throw new IllegalArgumentException("Climate frequencies must be positive");
        }
        if (climateMin < 0.0 || climateMax < climateMin) {
            throw new IllegalArgumentException("Invalid climate bounds: 0 <= climateMin <= climateMax");
        }
        if (baseErosionRate < 0.0) {
            throw new IllegalArgumentException("Erosion rate cannot be negative");
        }
        if (lapseRatePerBlock < 0.0) {
            throw new IllegalArgumentException("lapseRatePerBlock cannot be negative");
        }
        if (maxWarpAmplitude < 0.0) {
            throw new IllegalArgumentException("maxWarpAmplitude cannot be negative");
        }
        if (surfaceBandRadius <= 0) {
            throw new IllegalArgumentException("surfaceBandRadius must be positive");
        }
        if (riverMaxIncision < 0.0 || riverChannelSteepness <= 0.0) {
            throw new IllegalArgumentException("Invalid river parameters");
        }
        if (drainageIterations < 1 || drainageIterations > 8) {
            throw new IllegalArgumentException("drainageIterations must be in [1, 8]");
        }
    }

    /**
     * Computes the configuration's contribution to the deterministic
     * key K (TECHSPEC §8).
     *
     * <p>Hashes the terrain-influencing parameters only; the final
     * 32-bit fold keeps the value stable across 32- and 64-bit JVMs.
     *
     * @return deterministic hash of the terrain-influencing parameters
     */
    public long configHash() {
        long h = Double.doubleToLongBits(tectonicFreqLow);
        h = 31 * h + Double.doubleToLongBits(tectonicAmpA);
        h = 31 * h + Double.doubleToLongBits(stressFrequency);
        h = 31 * h + Double.doubleToLongBits(stressAmplitude);
        h = 31 * h + Double.doubleToLongBits(baseErosionRate);
        h = 31 * h + Double.doubleToLongBits(riverMaxIncision);
        h = 31 * h + worldMinY;
        h = 31 * h + worldMaxY;
        h = 31 * h + seaLevel;
        h = 31 * h + generatorVersion;
        h = 31 * h + drainageIterations;
        return h ^ (h >>> 32);
    }

    /**
     * Calibrated Overworld baseline matching
     * {@link NormalizedGeoParams#defaultOverworld()}.
     *
     * @param version generator version to bake into the returned configuration
     * @return Overworld configuration with calibrated tectonic, stress, erosion, and river parameters
     */
    public static GeoConfig defaultOverworld(int version) {
        return new GeoConfig(
            version, 
            0,                  // dimensionId: 0 (Overworld)
            -64,                // worldMinY
            1984,               // worldMaxY
            64,                 // seaLevel
            0.0003,             // tectonicFreqLow: Continental wavelength ~3300m
            0.0008,             // tectonicFreqA: Primary mountain belt wavelength ~1250m
            0.0016,             // tectonicFreqB: Secondary ridge wavelength ~625m
            160.0,              // tectonicAmpLow: Continental base amplitude
            160.0,              // tectonicAmpA: Primary belt relief amplitude
            50.0,               // tectonicAmpB: Foothill ridge amplitude
            2.2,                // upliftExponent: p=2.2 isolates peaks and flattens valleys
            0.0005,             // stressFrequency: Broad horizontal stress warp
            24.0,               // stressAmplitude: Maximum horizontal coordinate displacement
            0.45,               // stressMaxJacobian
            0.0003,             // epochFrequency: Geological age variation
            0.0004,             // climateTempFrequency
            0.0004,             // climateHumidFrequency
            0.6,                // climateMin
            1.4,                // climateMax
            0.0012,             // lapseRatePerBlock
            35.0,               // baseErosionRate
            5.0,                // maxWarpAmplitude: Natural 3-5 block rock overhangs
            16,                 // surfaceBandRadius
            18.0,               // riverMaxIncision: 3-8m stream valleys, max 18m in canyons
            0.15,               // riverChannelSteepness: Progressive incision along drainage paths
            1,                  // drainageIterations: single pass (TECHSPEC §24)
            -40,                // caveMinY
            128                 // caveMaxY
        );
    }

    /**
     * High-relief development preset for visual evaluation (Phase 9 Sprint 0).
     *
     * <p>Amplifies continental and mountain-belt energy, increases river
     * incision capacity for deep trunk valleys, and retains the DEFAULT
     * erosion baseline. This preset produces dramatically larger relief
     * than {@link #defaultOverworld(int)} and is intended for in-game
     * visual QA and screenshot evaluation, not for deterministic test
     * baselines.
     *
     * <p>Changes vs DEFAULT: tectonicAmpA 160→240, tectonicAmpB 50→80,
     * riverMaxIncision 18→28, baseErosionRate 35→30 (slightly less
     * flattening). All hard bounds (T_max, world Y, R_max) still enforced.
     *
     * @param version generator version to bake into the returned configuration
     * @return high-relief Overworld configuration
     */
    public static GeoConfig targetOverworld(int version) {
        return new GeoConfig(
            version,
            0,                  // dimensionId: 0 (Overworld)
            -64,                // worldMinY
            1984,               // worldMaxY
            64,                 // seaLevel
            0.0003,             // tectonicFreqLow
            0.0008,             // tectonicFreqA
            0.0016,             // tectonicFreqB
            160.0,              // tectonicAmpLow (same as DEFAULT)
            240.0,              // tectonicAmpA: stronger mountain belt relief (was 160)
            80.0,               // tectonicAmpB: stronger foothill ridges (was 50)
            2.2,                // upliftExponent (same as DEFAULT)
            0.0005,             // stressFrequency
            24.0,               // stressAmplitude
            0.45,               // stressMaxJacobian
            0.0003,             // epochFrequency
            0.0004,             // climateTempFrequency
            0.0004,             // climateHumidFrequency
            0.6,                // climateMin
            1.4,                // climateMax
            0.0012,             // lapseRatePerBlock
            30.0,               // baseErosionRate: slightly less flattening (was 35)
            5.0,                // maxWarpAmplitude
            16,                 // surfaceBandRadius
            28.0,               // riverMaxIncision: deeper trunk valleys (was 18)
            0.15,               // riverChannelSteepness
            2,                  // drainageIterations: two-pass for stabler corridors (was 1)
            -40,                // caveMinY
            128                 // caveMaxY
        );
    }

    /**
     * @param value configuration component to check
     * @return true if the value is NaN or infinite
     */
    private static boolean isNonFinite(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }
}
