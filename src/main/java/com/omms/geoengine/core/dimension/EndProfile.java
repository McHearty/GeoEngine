package com.geoengine.core.dimension;

import com.geoengine.core.field.VoronoiFractureField;
import com.geoengine.core.field.WarpField;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.GeoSample;
import com.geoengine.core.memory.WorkerScratchpad;

public final class EndProfile implements DimensionProfile {
    private final GeoConfig config;
    private final VoronoiFractureField fractureField;
    private final WarpField warpField;
    private final VoronoiFractureField.FractureSample fractureScratch = new VoronoiFractureField.FractureSample();

    private static final double FRACTURE_CELL_SIZE = 144.0;
    private static final double SCARP_WIDTH = 14.0;

    public EndProfile(long worldSeed, int version) {
        this.config = new GeoConfig(
            version, 2, 0, 256, -64,
            0.001, 0.002, 0.003,
            64.0, 32.0, 16.0,
            1.5,
            0.002, 10.0, 0.35,
            0.001,
            0.0001, 0.0001,
            0.0, 0.0,
            0.0,
            0.0,
            8.0,
            16
        );

        this.fractureField = new VoronoiFractureField(worldSeed, FRACTURE_CELL_SIZE);
        this.warpField = new WarpField(worldSeed, this.config);
    }

    @Override
    public DimensionType getDimensionType() {
        return DimensionType.THE_END;
    }

    @Override
    public GeoConfig getConfig() {
        return config;
    }

    @Override
    public int getFluidLevel() {
        return -64;
    }

    @Override
    public double evaluateSurface(double x, double z, GeoSample sample) {
        double radialDist = Math.sqrt(x * x + z * z);
        if (radialDist < 280.0) {
            sample.finalSurface = -100.0;
            return -100.0;
        }

        fractureField.sample(x, z, fractureScratch);
        double baseElev = fractureScratch.cellElevation;
        double edgeDist = fractureScratch.distanceToEdge;

        double surface;
        if (edgeDist < SCARP_WIDTH) {
            double dropFactor = edgeDist / SCARP_WIDTH;
            surface = baseElev - (38.0 * (1.0 - dropFactor * dropFactor));
        } else {
            surface = baseElev;
        }

        sample.finalSurface = surface;
        return surface;
    }

    @Override
    public float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ) {
        double surfaceH = evaluateSurface(worldX, worldZ, scratchpad.sample);
        if (surfaceH < 0.0) {
            return -64.0f;
        }

        double w = warpField.evaluateWarp(worldX, worldY, worldZ);
        double effectiveY = (double) worldY + w;

        if (effectiveY > surfaceH) {
            return (float) (surfaceH - effectiveY);
        }

        double islandBottom = surfaceH - 36.0;
        if (effectiveY < islandBottom) {
            return (float) (effectiveY - islandBottom);
        }

        return (float) Math.min(surfaceH - effectiveY, effectiveY - islandBottom);
    }
}
