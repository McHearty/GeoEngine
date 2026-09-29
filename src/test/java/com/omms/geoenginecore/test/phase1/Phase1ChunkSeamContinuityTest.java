package com.omms.geoenginecore.test.phase1;

import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk-boundary seam conformance (TECHSPEC §136; Phase 1 acceptance
 * criterion "chunk seam tests pass").
 *
 * <p>Every pipeline field is evaluated at both columns of every
 * 16-block chunk seam. Pure world-coordinate fields are continuous, so
 * their cross-seam difference is bounded by the field's Lipschitz
 * constant times one block (documented per field below); region
 * quantized hydrology fields carry the tolerances established by the
 * Phase 2 verification. A seam discontinuity larger than these
 * bounds indicates a halo or lattice defect.
 */
public class Phase1ChunkSeamContinuityTest {

    /**
     * Per-block Lipschitz bound for the stress warp displacement
     * W = stressAmplitude · GeoNoise(f·x, f·z). GeoNoise is a sum of
     * three Perlin kernels t⁴·g with t = 0.5 − r²; each kernel has
     * |∇| ≤ √2·0.5³·0.5 ≈ 0.088, so |∇noise| ≤ 45 · 3 · 0.088 ≈ 11.9
     * per input unit and |∂W| ≤ 24.0 · 0.0005 · 11.9 ≈ 0.144 per block
     * for the calibrated stack. The tolerance carries a 2x margin.
     */
    private static final double TOL_WARP = 0.3;
    /** Age is a normalized ratio in [0,1] at frequency 0.0003, so |Δ| ≤ ~0.001 per block. */
    private static final double TOL_AGE = 0.01;
    /** Temperature/humidity: low-frequency climate noise (0.0004), |Δ| ≤ ~0.001 per block. */
    private static final double TOL_CLIMATE = 0.01;
    /** Tectonic relief: octave gradient bound ≈ Σ Aᵢfᵢ ≈ 0.26 per block for the calibrated stack. */
    private static final double TOL_TECTONIC = 1.0;
    /** Erosion lowering: derivative of the 0.002-frequency raw field plus the elevation factor. */
    private static final double TOL_EROSION = 1.0;
    /** H₀ = T − E, so |ΔH₀| ≤ |ΔT| + |ΔE|. */
    private static final double TOL_H0 = 2.0;
    /** Gradient components: second-derivative scale of the smooth crustal fields. */
    private static final double TOL_GRADIENT = 1.0;
    /** Laplacian: 5-point stencil of pre-fluvial modifiers; jumps at feature boundaries. */
    private static final double TOL_LAPLACIAN = 4.0;
    /** Drainage accumulation: tolerance established by Phase 2 region-quantized hydrology. */
    private static final double TOL_ACCUMULATION = 1.5;
    /** River incision: tolerance established by Phase 2 river continuity verification. */
    private static final double TOL_INCISION = 5.0;
    /** Deposition: S is budget bounded by E_total but gated by flatness thresholds. */
    private static final double TOL_DEPOSITION = 8.0;
    /** Final surface: dominated by the incision and deposition tolerances above. */
    private static final double TOL_SURFACE = 4.0;

    private static final long[] SEEDS = {0x9876543210FEDCBAL, 0x7CAFEBABED00DCAFL};
    private static final int[] SEAM_CHUNKS = {0, 16, 128};
    private static final int[] OFFSETS = {0, 7, 15};
    private static final double WORLD_MIN_Y = -64.0;


    @Test
    @DisplayName("X-direction chunk seams: every field continuous within Lipschitz bounds")
    void testXSeamContinuity() {
        for (long seed : SEEDS) {
            ScalarFieldKernel kernel = new ScalarFieldKernel(seed, GeoConfig.defaultOverworld(1));
            for (int cx : SEAM_CHUNKS) {
                for (int z : OFFSETS) {
                    checkSeam(kernel, cx * 16 + 15, z, (cx + 1) * 16, z);
                }
            }
        }
    }


    @Test
    @DisplayName("Z-direction chunk seams: every field continuous within Lipschitz bounds")
    void testZSeamContinuity() {
        for (long seed : SEEDS) {
            ScalarFieldKernel kernel = new ScalarFieldKernel(seed, GeoConfig.defaultOverworld(1));
            for (int cz : SEAM_CHUNKS) {
                for (int x : OFFSETS) {
                    checkSeam(kernel, x, cz * 16 + 15, x, (cz + 1) * 16);
                }
            }
        }
    }

    private void checkSeam(ScalarFieldKernel kernel, double x1, double z1, double x2, double z2) {
        GeoSample a = new GeoSample();
        GeoSample b = new GeoSample();
        kernel.evaluateFullColumn(x1, z1, a);
        kernel.evaluateFullColumn(x2, z2, b);

        String where = String.format("seam (%.0f,%.0f)|%.0f,%.0f", x1, z1, x2, z2);

        assertTrue(Double.isFinite(a.rawTectonic), where + " rawTectonic finite");
        assertTrue(Double.isFinite(a.erosionLowering), where + " erosionLowering finite");
        assertTrue(Double.isFinite(a.surfaceH0), where + " surfaceH0 finite");
        assertTrue(Double.isFinite(a.flowAccumulation), where + " flowAccumulation finite");
        assertTrue(Double.isFinite(a.riverIncision), where + " riverIncision finite");
        assertTrue(Double.isFinite(a.deposition), where + " deposition finite");
        assertTrue(Double.isFinite(a.finalSurface), where + " finalSurface finite");

        // warped = world + W, so the cross-seam difference of the
        // coordinates includes the 1.0 identity step; continuity is
        // checked on the displacement W itself (TECHSPEC §16, §136).
        assertBounded("stressWarpX", a.stressWarpX, b.stressWarpX, TOL_WARP, where);
        assertBounded("warpDX", a.warpedX - a.worldX, b.warpedX - b.worldX, TOL_WARP, where);
        assertBounded("stressWarpZ", a.stressWarpZ, b.stressWarpZ, TOL_WARP, where);
        assertBounded("warpDZ", a.warpedZ - a.worldZ, b.warpedZ - b.worldZ, TOL_WARP, where);

        // Sample internal consistency: the stored warp equals the
        // coordinate minus the world coordinate.
        assertEquals(a.warpedX - a.worldX, a.stressWarpX, 1e-9, where + " warpedX - worldX == stressWarpX");
        assertEquals(a.warpedZ - a.worldZ, a.stressWarpZ, 1e-9, where + " warpedZ - worldZ == stressWarpZ");
        assertBounded("age", a.age, b.age, TOL_AGE, where);
        assertBounded("temperature", a.temperature, b.temperature, TOL_CLIMATE, where);
        assertBounded("humidity", a.humidity, b.humidity, TOL_CLIMATE, where);
        assertBounded("climateMultiplier", a.climateMultiplier, b.climateMultiplier, TOL_CLIMATE, where);
        assertBounded("rawTectonic", a.rawTectonic, b.rawTectonic, TOL_TECTONIC, where);
        assertBounded("erosionLowering", a.erosionLowering, b.erosionLowering, TOL_EROSION, where);
        assertBounded("surfaceH0", a.surfaceH0, b.surfaceH0, TOL_H0, where);
        assertBounded("gradX", a.gradX, b.gradX, TOL_GRADIENT, where);
        assertBounded("gradZ", a.gradZ, b.gradZ, TOL_GRADIENT, where);
        assertBounded("laplacian", a.laplacian, b.laplacian, TOL_LAPLACIAN, where);
        assertBounded("flowAccumulation", a.flowAccumulation, b.flowAccumulation, TOL_ACCUMULATION, where);
        assertBounded("riverIncision", a.riverIncision, b.riverIncision, TOL_INCISION, where);
        assertBounded("deposition", a.deposition, b.deposition, TOL_DEPOSITION, where);
        assertBounded("finalSurface", a.finalSurface, b.finalSurface, TOL_SURFACE, where);

        assertTrue(a.waterSurfaceLevel >= WORLD_MIN_Y, where + " waterSurfaceLevel in world bounds");
        assertTrue(b.waterSurfaceLevel >= WORLD_MIN_Y, where + " waterSurfaceLevel in world bounds");
        // UNKNOWN (id 0) is the grammar's designed terminal sentinel for
        // unresolvable columns; any id outside the taxonomy is a defect.
        assertTrue(isValidLandformId(a.classificationBits & LandformBits.TYPE_MASK),
            where + " valid landform id");
        assertTrue(isValidLandformId(b.classificationBits & LandformBits.TYPE_MASK),
            where + " valid landform id");
    }

    /** Id 0 is the designed UNKNOWN sentinel; the real taxonomy is 1..20. */
    private static boolean isValidLandformId(int id) {
        return id == 0 || (id >= 1 && id <= 20);
    }

    private static void assertBounded(String field, double v1, double v2, double tol, String where) {
        double delta = Math.abs(v1 - v2);
        assertTrue(delta <= tol,
            String.format("%s: |Δ%s| = %.6f exceeds seam tolerance %.2f", where, field, delta, tol));
    }
}
