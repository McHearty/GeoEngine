package com.omms.geoenginecore.test.phase1;

import com.omms.geoenginecore.geomorphology.LandformType;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoengineforge.debug.GeoDebugExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 acceptance criterion "surface fields can be exported"
 * (TECHSPEC §153-§154): the headless field exporters must produce
 * complete, finite, well-formed artifacts from the deterministic
 * pipeline, with no Minecraft runtime present.
 */
public class Phase1FieldExportTest {

    @TempDir
    Path tempDir;

    private static final String CSV_HEADER =
        "worldX,worldZ,rawTectonic,stressWarpX,stressWarpZ,warpedX,warpedZ," +
        "age,temperature,humidity,climateMultiplier,erosionLowering,h0,hPre," +
        "gradX,gradZ,slope,laplacian,flowAcc,riverIncision,deposition,finalSurface," +
        "basinId,confluenceId,landformId,landformName";


    @Test
    @DisplayName("Field CSV export: header matches §153, every value finite, id/name consistent")
    void testFieldsCsvExport() throws Exception {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(0x9876543210FEDCBAL, config);
        File out = GeoDebugExporter.exportFieldsCsv(
            kernel, config, 0, 0, 1, tempDir.toFile(), "phase1");

        assertTrue(out.isFile(), "CSV file must be written: " + out);
        List<String> lines = java.nio.file.Files.readAllLines(out.toPath());
        assertEquals(CSV_HEADER, lines.get(0), "header must match the §153 column list");
        assertTrue(lines.size() > 1, "at least one data row is required");

        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",");
            assertEquals(26, cells.length, "row " + i + " must have 26 columns");
            for (int c = 0; c < 22; c++) {
                double value = Double.parseDouble(cells[c]);
                assertTrue(Double.isFinite(value),
                    "row " + i + " column " + c + " (" + CSV_HEADER.split(",")[c] + ") must be finite");
            }
            // 22 numeric fields, then the stable basin/confluence IDs
            // (64-bit hashes — may be negative; 0 is the reserved
            // "none" marker), then landformId, then landformName. Both
            // parse calls above already validate the column form.
            int id = Integer.parseInt(cells[24]);
            // UNKNOWN (id 0) is the grammar's designed terminal sentinel.
            assertTrue(id == 0 || (id >= 1 && id <= 20),
                "row " + i + " landform id " + id + " must be within the taxonomy");
            assertEquals(LandformType.fromId(id).name(), cells[25],
                "row " + i + " landformName must match its id");
        }
    }


    @Test
    @DisplayName("Heightmap PNG export: decodable image with the expected dimensions")
    void testHeightmapPngExport() throws Exception {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(0x9876543210FEDCBAL, config);
        File out = GeoDebugExporter.exportHeightmapPng(
            kernel, config, 0, 0, 1, tempDir.toFile(), "phase1");

        assertTrue(out.isFile(), "PNG file must be written: " + out);
        BufferedImage image = ImageIO.read(out);
        assertNotNull(image, "PNG must be decodable headlessly");
        assertEquals(48, image.getWidth(), "3 chunks x 16 blocks/chunk = 48 blocks");
        assertEquals(48, image.getHeight(), "3 chunks x 16 blocks/chunk = 48 blocks");
    }


    @Test
    @DisplayName("Vertical slice PNG export: decodable image with the expected dimensions")
    void testVerticalSlicePngExport() throws Exception {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(0x9876543210FEDCBAL, config);
        File out = GeoDebugExporter.exportVerticalSlicePng(
            kernel, config, 100, 0, 1, tempDir.toFile(), "phase1");

        assertTrue(out.isFile(), "PNG file must be written: " + out);
        BufferedImage image = ImageIO.read(out);
        assertNotNull(image, "PNG must be decodable headlessly");
        assertEquals(48, image.getWidth(), "3 chunks x 16 blocks/chunk = 48 blocks");
        assertEquals(576, image.getHeight(), "slice spans worldMinY..min(worldMaxY, 512)");
    }


    @Test
    @DisplayName("Point query reports SOLID below the surface and AIR above it")
    void testQueryPoint() throws Exception {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(0x9876543210FEDCBAL, config);

        GeoSample sample = new GeoSample();
        kernel.evaluateFullColumn(8.0, 8.0, sample);
        int surfaceY = (int) Math.floor(sample.finalSurface);
        int deepY = surfaceY - 16;

        String solid = GeoDebugExporter.queryPoint(kernel, config, 8, deepY, 8);
        String air = GeoDebugExporter.queryPoint(kernel, config, 8, surfaceY + 8, 8);
        assertNotNull(solid);
        assertNotNull(air);
        assertTrue(solid.contains("SOLID"), "deep column must be SOLID:\n" + solid);
        assertTrue(air.contains("AIR"), "air column must be AIR:\n" + air);
        assertTrue(solid.contains("(8, " + deepY + ", 8)"), "query must report its position");
    }
}
