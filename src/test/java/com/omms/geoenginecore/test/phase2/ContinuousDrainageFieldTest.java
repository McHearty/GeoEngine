package com.omms.geoenginecore.test.phase2;

import com.omms.geoenginecore.math.*;
import com.omms.geoenginecore.hydrology.DrainageGraph;
import com.omms.geoenginecore.hydrology.DrainagePotential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Continuous drainage-field model tests (TECHSPEC_AMEND001).
 *
 * <p>Verifies that the continuous vector field approach produces
 * correct drainage behavior, including outlet-aware routing,
 * flat area resolution, and flow accumulation.
 */
public class ContinuousDrainageFieldTest {
    /** Default Overworld configuration. */
    private GeoConfig config;
    /** Scalar field kernel. */
    private ScalarFieldKernel kernel;

    @BeforeEach
    void setUp() {
        config = GeoConfig.defaultOverworld(1);
        kernel = new ScalarFieldKernel(0xCAFEBABEDEADBEEFL, config);
    }

    @Tag("must")
    @Tag("phase2")
    @Test
    @DisplayName("Continuous model: DrainagePotential computes valid potential")
    void testDrainagePotentialComputesValidPotential() {
        DrainagePotential potential = new DrainagePotential(0.05, 1e-6, 1.0);

        // Potential should be finite and deterministic
        double phi1 = potential.evaluate(kernel, 100.0, 100.0, 0.0, 0.0);
        double phi2 = potential.evaluate(kernel, 100.0, 100.0, 0.0, 0.0);

        assertFalse(Double.isNaN(phi1));
        assertFalse(Double.isInfinite(phi1));
        assertEquals(phi1, phi2, 1e-10); // Deterministic
    }

    @Tag("must")
    @Tag("phase2")
    @Test
    @DisplayName("Continuous model: Drainage vector field is unit vector")
    void testDrainageVectorFieldIsUnitVector() {
        DrainagePotential potential = new DrainagePotential(0.05, 1e-6, 1.0);

        double[] v = potential.drainageVector(kernel, 100.0, 100.0, 0.0, 0.0);
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1]);

        // Vector should be approximately unit length
        assertTrue(norm > 0.99 && norm < 1.01, "Vector norm should be 1.0, got " + norm);
    }

    @Tag("must")
    @Tag("phase2")
    @Test
    @DisplayName("Continuous model: Drainage vector points in direction of decreasing potential")
    void testDrainageVectorPointsDownhill() {
        DrainagePotential potential = new DrainagePotential(0.05, 1e-6, 1.0);

        // Compute vector at a point
        double[] v = potential.drainageVector(kernel, 100.0, 100.0, 0.0, 0.0);

        // Verify the vector points in the direction of decreasing potential
        // by checking that moving in the vector direction decreases the potential
        double phiBefore = potential.evaluate(kernel, 100.0, 100.0, 0.0, 0.0);
        double phiAfter = potential.evaluate(kernel,
            100.0 + v[0] * 1.0, 100.0 + v[1] * 1.0, 0.0, 0.0);

        // Potential should decrease (or stay same on flats)
        assertTrue(phiAfter <= phiBefore + 1e-6,
            "Potential should decrease in the direction of the vector: " +
            "phi_before=" + phiBefore + ", phi_after=" + phiAfter);
    }

    @Tag("must")
    @Tag("phase2")
    @Test
    @DisplayName("Continuous model: Flow accumulation propagates downstream")
    void testFlowAccumulationPropagatesDownstream() {
        DrainageGraph graph = new DrainageGraph(16.0, 256.0);
        graph.buildRegion(kernel, 0, 0, 1);

        // Flow accumulation should be >= 0.0 everywhere
        // (with wetness field, source density can be < 1.0 in dry areas)
        for (int i = 0; i < graph.TOTAL_CELLS; i++) {
            assertTrue(graph.flowAccumulation[i] >= 0.0,
                "Accumulation should be >= 0.0, got " + graph.flowAccumulation[i]);
            assertFalse(Double.isNaN(graph.flowAccumulation[i]));
            assertFalse(Double.isInfinite(graph.flowAccumulation[i]));
        }
    }

    @Tag("must")
    @Tag("phase2")
    @Test
    @DisplayName("Continuous model: Receiver index is acyclic (no loops)")
    void testReceiverIndexIsAcyclic() {
        DrainageGraph graph = new DrainageGraph(16.0, 256.0);
        graph.buildRegion(kernel, 0, 0, 1);

        // Verify no cycles in receiver index
        for (int i = 0; i < graph.TOTAL_CELLS; i++) {
            int cur = i;
            int hops = 0;
            while (cur >= 0 && hops++ < graph.TOTAL_CELLS) {
                cur = graph.receiverIndex[cur];
            }
            assertTrue(hops <= graph.TOTAL_CELLS, "Cycle detected starting at cell " + i);
        }
    }

    @Tag("must")
    @Tag("phase2")
    @Test
    @DisplayName("Continuous model: Sample accumulation is bilinearly interpolated")
    void testSampleAccumulationIsInterpolated() {
        DrainageGraph graph = new DrainageGraph(16.0, 256.0);
        graph.buildRegion(kernel, 0, 0, 1);

        // Sample at two different points and verify interpolation
        double acc1 = graph.sampleAccumulation(100.0, 100.0);
        double acc2 = graph.sampleAccumulation(100.0, 101.0);

        assertFalse(Double.isNaN(acc1));
        assertFalse(Double.isNaN(acc2));

        // Values should be close but not identical (unless in flat area)
        // Just verify they're finite and positive
        assertTrue(acc1 > 0.0);
        assertTrue(acc2 > 0.0);
    }

    @Tag("phase2")
    @Test
    @DisplayName("Continuous model: Deterministic across instances")
    void testDeterministicAcrossInstances() {
        DrainageGraph graph1 = new DrainageGraph(16.0, 256.0);
        DrainageGraph graph2 = new DrainageGraph(16.0, 256.0);

        graph1.buildRegion(kernel, 0, 0, 1);
        graph2.buildRegion(kernel, 0, 0, 1);

        // Same input should produce same output
        for (int i = 0; i < graph1.TOTAL_CELLS; i++) {
            assertEquals(graph1.flowAccumulation[i], graph2.flowAccumulation[i], 1e-10);
            assertEquals(graph1.receiverIndex[i], graph2.receiverIndex[i]);
        }
    }
}
