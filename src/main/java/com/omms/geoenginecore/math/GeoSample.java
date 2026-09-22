package com.omms.geoenginecore.math;

/**
 * Mutable scratch structure carrying every intermediate quantity of the
 * 2-D column pipeline (TECHSPEC §65).
 *
 * <p>Instances are allocated once per worker and reused across
 * evaluations. Callers rely on complete-overwrite contracts: every
 * field consumed downstream is written before it is read, so no full
 * reset is required (TECHSPEC §66). Do not share an instance between
 * threads.
 */
public final class GeoSample {
    /** World-space X of the column being evaluated. */
    public double worldX;
    /** World-space Z of the column being evaluated. */
    public double worldZ;

    /** Pre-erosion tectonic relief T evaluated in the warped domain. */
    public double rawTectonic;
    /** Horizontal X displacement applied by the stress warp (TECHSPEC §16). */
    public double stressWarpX;
    /** Horizontal Z displacement applied by the stress warp (TECHSPEC §16). */
    public double stressWarpZ;
    /** Warped domain coordinate x' = x + W_x used by all field queries. */
    public double warpedX;
    /** Warped domain coordinate z' = z + W_z used by all field queries. */
    public double warpedZ;
    /** Reserved for the isolated tectonic uplift component. */
    public double tectonicUplift;

    /** Geological age factor in [0, 1]; young (0) to old (1) (TECHSPEC §17). */
    public double age;
    /** Temperature at the warped domain location. */
    public double temperature;
    /** Humidity at the warped domain location. */
    public double humidity;
    /** Bounded climate process multiplier K ∈ [K_min, K_max] (TECHSPEC §20). */
    public double climateMultiplier;

    /** Long-term surface lowering E (TECHSPEC §22). */
    public double erosionLowering;
    /** Pre-carve surface H₀ = T − E on which derivatives are evaluated (TECHSPEC §23). */
    public double surfaceH0;
    /** Pre-fluvial surface H_pre = H₀ + process modifiers (TECHSPEC §24). */
    public double hPre;
    /** Deterministic flow accumulation proxy A_f (TECHSPEC §27). */
    public double flowAccumulation;
    /** Stable scoped ID of the column's drainage basin (§80); 0 = none. */
    public long basinId;
    /** Stable scoped ID of the column's confluence (§80); 0 = not a confluence. */
    public long confluenceId;
    /** Bounded river incision R (TECHSPEC §28). */
    public double riverIncision;
    /** Equilibrium deposition S within the budget 0 ≤ S ≤ S_max (TECHSPEC §32-§34). */
    public double deposition;
    /** Final surface H_f = H₀ − R + S (TECHSPEC §36). */
    public double finalSurface;
    /** Local river water level for inland rivers above sea level (TECHSPEC §149). */
    public int waterSurfaceLevel;

    /** X component of ∇H evaluated on the pre-fluvial surface (TECHSPEC §37). */
    public double gradX;
    /** Z component of ∇H evaluated on the pre-fluvial surface (TECHSPEC §37). */
    public double gradZ;
    /** |∇H| magnitude of the pre-fluvial surface gradient (TECHSPEC §37). */
    public double gradMagnitude;
    /** Laplacian ∇²H of the pre-fluvial surface (TECHSPEC §37). */
    public double laplacian;

    /** Volumetric rock warp W at this column (TECHSPEC §41-§44). */
    public double warpY;
    /** Cave void contribution C ≥ 0 (TECHSPEC §45-§46). */
    public double caveVoid;
    /** Final density D = H_f − (y + W) − C (TECHSPEC §49). */
    public double density;

    /** Bit-packed landform classification result (see {@code LandformBits}). */
    public int classificationBits;
}
