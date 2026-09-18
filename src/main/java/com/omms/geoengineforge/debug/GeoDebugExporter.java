package com.omms.geoengineforge.debug;

import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.geomorphology.LandformType;
import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.memory.WorkerScratchpad;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class GeoDebugExporter {
    private GeoDebugExporter() {}

    /**
     * Dumps all intermediate geomorphic scalar fields to CSV for tuning GeoConfig parameters (§153).
     */
    public static File exportFieldsCsv(
        FieldKernel kernel, GeoConfig config, 
        int centerChunkX, int centerChunkZ, int radiusChunks, File outputDir, String baseName
    ) throws IOException {
        outputDir.mkdirs();
        File file = new File(outputDir, baseName + "_fields.csv");

        int minBlockX = (centerChunkX - radiusChunks) << 4;
        int maxBlockX = (centerChunkX + radiusChunks + 1) << 4;
        int minBlockZ = (centerChunkZ - radiusChunks) << 4;
        int maxBlockZ = (centerChunkZ + radiusChunks + 1) << 4;

        GeoSample sample = new GeoSample();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("worldX,worldZ,rawTectonic,stressWarpX,stressWarpZ,warpedX,warpedZ," +
                         "age,temperature,humidity,climateMultiplier,erosionLowering,h0," +
                         "gradX,gradZ,slope,laplacian,flowAcc,riverIncision,deposition,finalSurface," +
                         "landformId,landformName\n");

            for (int z = minBlockZ; z < maxBlockZ; z += 4) {
                for (int x = minBlockX; x < maxBlockX; x += 4) {
                    kernel.evaluateFullColumn(x, z, sample);

                    LandformType type = LandformBits.getType(sample.classificationBits);

                    writer.write(String.format(
                        "%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f," +
                        "%.3f,%.3f,%.3f,%.3f,%.3f,%.3f," +
                        "%.4f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f," +
                        "%d,%s\n",
                        x, z, sample.rawTectonic, sample.stressWarpX, sample.stressWarpZ, sample.warpedX, sample.warpedZ,
                        sample.age, sample.temperature, sample.humidity, sample.climateMultiplier, sample.erosionLowering, sample.surfaceH0,
                        sample.gradX, sample.gradZ, sample.gradMagnitude, sample.laplacian, sample.flowAccumulation, sample.riverIncision, sample.deposition, sample.finalSurface,
                        type.getId(), type.name()
                    ));
                }
            }
        }
        return file;
    }

    /**
     * Exports a continuous 2D heightmap as a normalized grayscale PNG image (§154).
     */
    public static File exportHeightmapPng(
        FieldKernel kernel, GeoConfig config, 
        int centerChunkX, int centerChunkZ, int radiusChunks, File outputDir, String baseName
    ) throws IOException {
        outputDir.mkdirs();
        File file = new File(outputDir, baseName + "_heightmap.png");

        int width = (radiusChunks * 2 + 1) << 4;
        int height = (radiusChunks * 2 + 1) << 4;
        int originX = (centerChunkX - radiusChunks) << 4;
        int originZ = (centerChunkZ - radiusChunks) << 4;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        GeoSample sample = new GeoSample();

        double minY = config.worldMinY();
        double maxY = config.worldMaxY();
        double span = maxY - minY;

        for (int z = 0; z < height; z++) {
            int wz = originZ + z;
            for (int x = 0; x < width; x++) {
                int wx = originX + x;
                kernel.evaluateFullColumn(wx, wz, sample);

                double norm = Math.clamp((sample.finalSurface - minY) / span, 0.0, 1.0);
                int gray = (int) (norm * 255.0);

                int rgb;
                if (sample.finalSurface < config.seaLevel()) {
                    rgb = (gray / 2) | ((gray / 2) << 8) | (gray << 16);
                } else if (sample.finalSurface > config.seaLevel() + 200.0) {
                    rgb = gray | (gray << 8) | (gray << 16);
                } else {
                    rgb = (gray / 2) | (gray << 8) | (gray / 2);
                }

                image.setRGB(x, z, rgb);
            }
        }

        ImageIO.write(image, "PNG", file);
        return file;
    }

    /**
     * Renders a vertical 2D cross-section slice (X x Y at fixed Z).
     */
    public static File exportVerticalSlicePng(
        FieldKernel kernel, GeoConfig config, 
        int worldZ, int centerChunkX, int radiusChunks, File outputDir, String baseName
    ) throws IOException {
        outputDir.mkdirs();
        File file = new File(outputDir, baseName + "_slice_Z" + worldZ + ".png");

        int width = (radiusChunks * 2 + 1) << 4;
        int originX = (centerChunkX - radiusChunks) << 4;

        int minY = config.worldMinY();
        int maxY = Math.min(config.worldMaxY(), 512);
        int height = maxY - minY;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        WorkerScratchpad sp = new WorkerScratchpad();
        GeoSample sample = new GeoSample();

        for (int x = 0; x < width; x++) {
            int wx = originX + x;
            kernel.evaluateFullColumn(wx, worldZ, sample);
            double hf = sample.finalSurface;

            for (int y = minY; y < maxY; y++) {
                int imgY = (maxY - 1) - (y - minY);

                float density = kernel.evaluateDensity(sp, wx, y, worldZ);
                double cave = kernel.getCaveField().evaluateCave(wx, y, worldZ, hf);

                int color;
                if (y == (int) Math.round(hf)) {
                    color = Color.GREEN.getRGB();
                } else if (cave > 0.5) {
                    color = Color.RED.getRGB();
                } else if (density > 0.0f) {
                    color = Color.DARK_GRAY.getRGB();
                } else if (y < config.seaLevel()) {
                    color = new Color(30, 80, 180).getRGB();
                } else {
                    color = new Color(20, 20, 30).getRGB();
                }

                image.setRGB(x, imgY, color);
            }
        }

        ImageIO.write(image, "PNG", file);
        return file;
    }

    /**
     * Formats a complete single-column geomorphic diagnostic printout.
     */
    public static String queryPoint(FieldKernel kernel, GeoConfig config, int worldX, int worldY, int worldZ) {
        GeoSample s = new GeoSample();
        kernel.evaluateFullColumn(worldX, worldZ, s);
        WorkerScratchpad sp = new WorkerScratchpad();
        float d = kernel.evaluateDensity(sp, worldX, worldY, worldZ);
        double cave = kernel.getCaveField().evaluateCave(worldX, worldY, worldZ, s.finalSurface);
        double warp = kernel.getWarpField().evaluateWarp(worldX, worldY, worldZ, s.gradMagnitude);
        LandformType type = LandformBits.getType(s.classificationBits);

        return String.format(
            "=== GeoEngine Column Query at (%d, %d, %d) ===\n" +
            "  Surface Height (Hf):  %.2f (Pre-Carve H0: %.2f, Tectonic: %.2f, Erosion: -%.2f)\n" +
            "  Hydrology:            FlowAcc: %.2f, Incision: %.2f, Deposition: +%.2f\n" +
            "  Derivatives:          Slope: %.4f (GradX: %.3f, GradZ: %.3f), Laplacian: %.4f\n" +
            "  Climate:              Temp: %.2f, Humid: %.2f, Multiplier: %.2f\n" +
            "  Volumetric @ Y=%d:    Density: %.4f (%s), Warp(W): %.2f, Cave(C): %.2f\n" +
            "  Geomorphology:        Landform: %s (Bits: 0x%08X)\n" +
            "================================================",
            worldX, worldY, worldZ,
            s.finalSurface, s.surfaceH0, s.rawTectonic, s.erosionLowering,
            s.flowAccumulation, s.riverIncision, s.deposition,
            s.gradMagnitude, s.gradX, s.gradZ, s.laplacian,
            s.temperature, s.humidity, s.climateMultiplier,
            worldY, d, d > 0.0f ? "SOLID" : "AIR", warp, cave,
            type.name(), s.classificationBits
        );
    }
}
