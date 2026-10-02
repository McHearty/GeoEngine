package com.omms.geoenginecore.test.perf;

import com.omms.geoenginecore.hydrology.DrainageGraph;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

/**
 * TECHSPEC_AMEND001: Cold-region graph construction benchmark.
 *
 * <p>Measures the cost of building a new drainage region from scratch,
 * which dominates first-touch world generation performance.
 */
@Tag("phase0")
class PerformanceColdRegionTest {

    @Test
    @DisplayName("Cold region graph construction timing")
    void testColdRegionBuild() {
        ScalarFieldKernel kernel = new ScalarFieldKernel(0x1234567890ABCDEFL, GeoConfig.defaultOverworld(1));

        // Warm up JVM
        DrainageGraph warmGraph = new DrainageGraph(16.0, 256.0);
        warmGraph.buildRegion(kernel, 0, 0, 1);

        // Measure cold region build
        long t0 = System.nanoTime();
        DrainageGraph coldGraph = new DrainageGraph(16.0, 256.0);
        coldGraph.buildRegion(kernel, 256, 256, 1);
        long elapsed = System.nanoTime() - t0;

        System.out.printf("[PERF] Cold region build: %.1f ms (%d H0 samples)%n",
            elapsed / 1e6, 576);

        // Sanity check: should be under 100ms
        assert elapsed < 100_000_000 : "Cold region build took too long: " + elapsed / 1e6 + " ms";
    }
}