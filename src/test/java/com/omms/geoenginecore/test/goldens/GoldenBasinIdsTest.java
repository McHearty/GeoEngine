package com.omms.geoenginecore.test.goldens;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.*;
import java.nio.file.*;

import static com.omms.geoenginecore.test.fixtures.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P-H-02: Golden snapshots for basin/confluence IDs.
 *
 * <p>For a fixed (seed, region) pair, dump the basin and confluence ID grids
 * to binary files. On subsequent runs, compare against the stored golden files
 * to detect topology regressions.
 */
public class GoldenBasinIdsTest {

    private static final Path GOLDENS_DIR = Paths.get("src/test/resources/goldens/hydrology");
    private static final long GOLDEN_SEED = 0xCAFEBABEDEADBEEFL;
    private static final int GOLDEN_CHUNK_X = 64;
    private static final int GOLDEN_CHUNK_Z = 64;

    @Tag("golden")

    @Test
    @DisplayName("P-H-02: Basin ID grid matches golden")
    void testBasinIdGolden() throws IOException {
        testIdGridGolden("basin_ids", sp -> sp.basinIdGrid);
    }

    @Tag("golden")

    @Test
    @DisplayName("P-H-02: Confluence ID grid matches golden")
    void testConfluenceIdGolden() throws IOException {
        testIdGridGolden("confluence_ids", sp -> sp.confluenceIdGrid);
    }

    private void testIdGridGolden(String name, IdGridExtractor extractor) throws IOException {
        boolean updateGoldens = Boolean.getBoolean("update.goldens");

        // Generate the grid
        ScalarFieldKernel kernel = kernelWithSeed(GOLDEN_SEED);
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.rasterizeSurfaceChunk(sp, GOLDEN_CHUNK_X, GOLDEN_CHUNK_Z);
        long[] grid = extractor.extract(sp);

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
        long[] golden = readGolden(goldenFile);
        assertIdGridEquals(golden, grid, "Golden mismatch for " + name);
    }

    private void writeGolden(Path path, long[] grid) throws IOException {
        Files.createDirectories(path.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(grid.length);
            for (long v : grid) {
                out.writeLong(v);
            }
        }
    }

    private long[] readGolden(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            int length = in.readInt();
            long[] grid = new long[length];
            for (int i = 0; i < length; i++) {
                grid[i] = in.readLong();
            }
            return grid;
        }
    }

    private void assertIdGridEquals(long[] expected, long[] actual, String message) {
        assertEquals(expected.length, actual.length, message + ": length mismatch");
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                fail(message + ": mismatch at index " + i +
                        " (expected=" + expected[i] + ", actual=" + actual[i] + ")");
            }
        }
    }

    @FunctionalInterface
    private interface IdGridExtractor {
        long[] extract(WorkerScratchpad sp);
    }
}
