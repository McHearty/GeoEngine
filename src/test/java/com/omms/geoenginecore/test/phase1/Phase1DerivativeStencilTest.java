package com.omms.geoenginecore.test.phase1;

import com.omms.geoenginecore.derivative.DerivativeSampler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Derivative stencil conformance (TECHSPEC §37; Phase 1 acceptance
 * criterion "derivative tests pass").
 *
 * <p>The hot pipeline path inlines the same stencils, so pinning the
 * reference stencils on analytic surfaces validates the numerical
 * contract of every derivative the pipeline consumes (gradients,
 * |∇H|, and the Laplacian).
 */
public class Phase1DerivativeStencilTest {

    private static final double EPS = 1e-9;


    @Tag("must")
    @Tag("phase1")
    @Test
    @DisplayName("Unit Slope: central difference recovers slope 1.0 exactly on both axes")
    void testUnitSlopeExact() {
        assertEquals(1.0, DerivativeSampler.gradientX(9.0, 11.0, 1.0), 0.0,
            "a 2-block span of a unit slope must divide to exactly 1.0");
        assertEquals(1.0, DerivativeSampler.gradientZ(9.0, 11.0, 1.0), 0.0);
    }


    @Tag("must")
    @Tag("phase1")
    @Test
    @DisplayName("Tilted Plane: H = 0.3x - 0.2z reproduces both analytic gradient components")
    void testTiltedPlaneGradients() {
        double x0 = 100.0;
        double z0 = 50.0;
        double hWest = 0.3 * (x0 - 1.0) - 0.2 * z0;
        double hEast = 0.3 * (x0 + 1.0) - 0.2 * z0;
        double hNorth = 0.3 * x0 - 0.2 * (z0 - 1.0);
        double hSouth = 0.3 * x0 - 0.2 * (z0 + 1.0);
        assertEquals(0.3, DerivativeSampler.gradientX(hWest, hEast, 1.0), EPS);
        assertEquals(-0.2, DerivativeSampler.gradientZ(hNorth, hSouth, 1.0), EPS);
    }


    @Test
    @DisplayName("Non-positive Stencil Spacing Rejected Before Division")
    void testNonPositiveStepRejected() {
        assertThrows(IllegalArgumentException.class, () -> DerivativeSampler.gradientX(1.0, 3.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> DerivativeSampler.gradientZ(1.0, 3.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> DerivativeSampler.laplacian(1.0, 1.0, 1.0, 1.0, 1.0, 0.0));
    }


    @Test
    @DisplayName("Flat Surface: every stencil is exactly zero")
    void testFlatSurface() {
        double h = 100.0;
        assertEquals(0.0, DerivativeSampler.gradientX(h, h, 1.0), 0.0);
        assertEquals(0.0, DerivativeSampler.gradientZ(h, h, 1.0), 0.0);
        assertEquals(0.0, DerivativeSampler.laplacian(h, h, h, h, h, 1.0), 0.0);
        assertEquals(0.0, DerivativeSampler.magnitude(0.0, 0.0), 0.0);
    }


    @Test
    @DisplayName("Linear Surface: 5-point Laplacian is exactly zero")
    void testLinearSurfaceLaplacian() {
        double hc = 0.5 * 10.0 + 0.25 * 10.0;
        double hN = 0.5 * 10.0 + 0.25 * 9.0;
        double hS = 0.5 * 10.0 + 0.25 * 11.0;
        double hW = 0.5 * 9.0 + 0.25 * 10.0;
        double hE = 0.5 * 11.0 + 0.25 * 10.0;
        assertEquals(0.0, DerivativeSampler.laplacian(hc, hN, hS, hW, hE, 1.0), EPS);
    }


    @Tag("must")
    @Tag("phase1")
    @Test
    @DisplayName("Quadratic Bowl: 5-point Laplacian equals 2 * H'' (spec exactness claim)")
    void testQuadraticBowlLaplacian() {
        double k = 0.7;
        double x0 = 7.0;
        double z0 = 3.0;
        double lap = DerivativeSampler.laplacian(
            0.5 * k * (x0 * x0 + z0 * z0),
            0.5 * k * (x0 * x0 + (z0 + 1.0) * (z0 + 1.0)),
            0.5 * k * (x0 * x0 + (z0 - 1.0) * (z0 - 1.0)),
            0.5 * k * ((x0 + 1.0) * (x0 + 1.0) + z0 * z0),
            0.5 * k * ((x0 - 1.0) * (x0 - 1.0) + z0 * z0),
            1.0);
        assertEquals(2.0 * k, lap, EPS, "5-point stencil is exact for quadratic surfaces");
    }


    @Test
    @DisplayName("Quadratic Peak: 5-point Laplacian equals -2 * H''")
    void testQuadraticPeakLaplacian() {
        double k = 0.7;
        double x0 = 7.0;
        double z0 = 3.0;
        double lap = DerivativeSampler.laplacian(
            -0.5 * k * (x0 * x0 + z0 * z0),
            -0.5 * k * (x0 * x0 + (z0 + 1.0) * (z0 + 1.0)),
            -0.5 * k * (x0 * x0 + (z0 - 1.0) * (z0 - 1.0)),
            -0.5 * k * ((x0 + 1.0) * (x0 + 1.0) + z0 * z0),
            -0.5 * k * ((x0 - 1.0) * (x0 - 1.0) + z0 * z0),
            1.0);
        assertEquals(-2.0 * k, lap, EPS);
    }


    @Tag("must")
    @Tag("phase1")
    @Test
    @DisplayName("Magnitude: (3, 4) yields |∇H| = 5 and is sign-invariant")
    void testMagnitude() {
        assertEquals(5.0, DerivativeSampler.magnitude(3.0, 4.0), 1e-12);
        assertEquals(5.0, DerivativeSampler.magnitude(-3.0, -4.0), 1e-12);
    }
}
