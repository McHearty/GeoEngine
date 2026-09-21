package com.omms.geoenginecore.test;

import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 acceptance criterion "benchmark results are recorded"
 * (TECHSPEC §221).
 *
 * <p>Measures steady-state chunk rasterization throughput after JIT
 * warmup and fails the suite if it regresses below the committed
 * baseline. The measured rate is printed to the Gradle test report
 * and recorded in {@code docs/BENCHMARKS.md}, which is the durable
 * benchmark record required by the spec.
 */
public class Phase1BenchmarkTest {

    /** Committed regression floor; re-baseline in docs/BENCHMARKS.md when the math core changes. */
    private static final double BASELINE_CHUNKS_PER_SEC = 600.0;
    private static final int WARMUP_CHUNKS = 512;
    private static final int MEASURED_CHUNKS = 2048;

    @Test
    @DisplayName("Rasterized chunk throughput does not regress below the committed baseline")
    void testRasterThroughput() {
        ScalarFieldKernel kernel = new ScalarFieldKernel(0x9876543210FEDCBAL, GeoConfig.defaultOverworld(1));
        WorkerScratchpad sp = new WorkerScratchpad();

        for (int i = 0; i < WARMUP_CHUNKS; i++) {
            kernel.rasterizeSurfaceChunk(sp, (i & 15) * 16, (i >> 4) * 16);
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < MEASURED_CHUNKS; i++) {
            kernel.rasterizeSurfaceChunk(sp, (i & 31) * 16, (i >> 5) * 16);
        }
        long elapsedNanos = System.nanoTime() - t0;
        double chunksPerSec = MEASURED_CHUNKS / (elapsedNanos / 1e9);

        System.out.printf("[benchmark] rasterized chunk: %.1f chunks/sec (committed floor %.0f, %.2fs for %d chunks)%n",
            chunksPerSec, BASELINE_CHUNKS_PER_SEC, elapsedNanos / 1e9, MEASURED_CHUNKS);

        assertTrue(chunksPerSec >= BASELINE_CHUNKS_PER_SEC,
            String.format("throughput regressed: %.1f < %.0f chunks/sec", chunksPerSec, BASELINE_CHUNKS_PER_SEC));
    }
}
