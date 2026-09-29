package com.omms.geoenginecore.test.goldens;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P-H-01: Golden binary dumps for hydrology grids.
 *
 * <p>For a fixed (seed, region) pair, dump the flow accumulation, river incision,
 * and surface height grids to binary files. On subsequent runs, compare against
 * the stored golden files to detect regressions.
 *
 * <p>Golden files are stored under src/test/resources/goldens/hydrology/.
 * To regenerate goldens, run with -Dupdate.goldens=true.
 */
public class GoldenHydrologyGridsTest {

    private static final Path GOLDENS_DIR = Paths.get("src/test/resources/goldens/hydrology");
    private static final long GOLDEN_SEED = 0xCAFEBABEDEADBEEFL;
    private static final int GOLDEN_CHUNK_X = 64;
    private static final int GOLDEN_CHUNK_Z = 64;

    @Tag("golden")

    @Test
    @DisplayName("P-H-01: Flow accumulation grid matches golden")
    void testFlowAccumulationGolden() throws IOException {
        testGridGolden("flow_acc", sp -> sp.flowAccGrid);
    }

    @Tag("golden")

    @Test
    @DisplayName("P-H-01: River incision grid matches golden")
    void testRiverIncisionGolden() throws IOException {
        testGridGolden("river_incision", sp -> sp.riverIncisionGrid);
    }

    @Tag("golden")

    @Test
    @DisplayName("P-H-01: Surface height grid matches golden")
    void testSurfaceHeightGolden() throws IOException {
        testGridGolden("surface_height", sp -> sp.surfaceGrid);
    }

    private void testGridGolden(String name, GridExtractor extractor) throws IOException {
        boolean updateGoldens = Boolean.getBoolean("update.goldens");

        // Generate the grid
        ScalarFieldKernel kernel = kernelWithSeed(GOLDEN_SEED);
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, GOLDEN_CHUNK_X, GOLDEN_CHUNK_Z);
        double[] grid = extractor.extract(sp);

        Path goldenFile = GOLDENS_DIR.resolve(name + ".bin");

        if (updateGoldens) {
            writeGolden(goldenFile, grid);
            System.out.println("Updated golden: " + goldenFile);
            return;
        }

        // If no golden exists, create it
        if (!Files.exists(goldenFile)) {
            writeGolden(goldenFile, grid);
            System.out.println("Created golden: " + goldenFile);
            return;
        }

        // Compare against golden
        double[] golden = readGolden(goldenFile);
        assertGridEquals(golden, grid, "Golden mismatch for " + name);
    }

    private void writeGolden(Path path, double[] grid) throws IOException {
        Files.createDirectories(path.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(grid.length);
            for (double v : grid) {
                out.writeDouble(v);
            }
        }
    }

    private double[] readGolden(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            int length = in.readInt();
            double[] grid = new double[length];
            for (int i = 0; i < length; i++) {
                grid[i] = in.readDouble();
            }
            return grid;
        }
    }

    private void assertGridEquals(double[] expected, double[] actual, String message) {
        assertEquals(expected.length, actual.length, message + ": length mismatch");
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                fail(message + ": mismatch at index " + i +
                        " (expected=" + expected[i] + ", actual=" + actual[i] + ")");
            }
        }
    }

    @FunctionalInterface
    private interface GridExtractor {
        double[] extract(WorkerScratchpad sp);
    }
}
