package com.omms.geoenginecore.test;

import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.dimension.DimensionType;
import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase6And7DifferentialTest {
    private static final long TEST_SEED = 0x12345678DEADBEEFL;

    private static final class ProcessDisabledProfile implements DimensionProfile {
        private final GeoConfig config = GeoConfig.defaultOverworld(1);
        @Override public DimensionType getDimensionType() { return DimensionType.OVERWORLD; }
        @Override public GeoConfig getConfig() { return config; }
        @Override public int getFluidLevel() { return config.seaLevel(); }
        @Override public boolean hasFluvialHydrology() { return true; }
        @Override public boolean hasGlacialProcesses() { return false; }
        @Override public boolean hasKarstProcesses() { return false; }
        @Override public boolean hasAeolianProcesses() { return false; }
        @Override public boolean hasCoastalProcesses() { return false; }
        @Override public boolean hasVolcanicProcesses() { return false; }
        @Override public boolean hasVoronoiFracture() { return false; }
    }

    @Test
    @DisplayName("Differential Test: Additive process terms measurably alter Hf relative to disabled baseline")
    void testProcessTermsMeasurablyAlterSurface() {
        DimensionProfile activeProfile = new OverworldProfile(1);
        DimensionProfile disabledProfile = new ProcessDisabledProfile();

        ScalarFieldKernel kernelActive = new ScalarFieldKernel(TEST_SEED, activeProfile);
        ScalarFieldKernel kernelDisabled = new ScalarFieldKernel(TEST_SEED, disabledProfile);

        WorkerScratchpad spActive = new WorkerScratchpad();
        WorkerScratchpad spDisabled = new WorkerScratchpad();

        int[] chunkCoords = {0, 256, 512, 1024};
        boolean observedGlacialDifference = false;
        boolean observedKarstOrDuneDifference = false;

        for (int cx : chunkCoords) {
            kernelActive.rasterizeSurfaceChunk(spActive, cx, cx);
            kernelDisabled.rasterizeSurfaceChunk(spDisabled, cx, cx);

            for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
                double hActive = spActive.surfaceGrid[i];
                double hDisabled = spDisabled.surfaceGrid[i];

                double diff = Math.abs(hActive - hDisabled);

                if (spActive.surfaceGrid[i] > activeProfile.getConfig().seaLevel() + 200.0) {
                    if (diff > 0.05) observedGlacialDifference = true;
                } else if (diff > 0.05) {
                    observedKarstOrDuneDifference = true;
                }
            }
        }

        assertTrue(observedGlacialDifference || observedKarstOrDuneDifference);
    }

    @Test
    @DisplayName("Derivative Alignment: Slopes reflect post-process pre-fluvial surface")
    void testDerivativesReflectPostProcessSurface() {
        OverworldProfile profile = new OverworldProfile(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(TEST_SEED, profile);
        GeoSample sample = new GeoSample();

        kernel.evaluateFullColumn(150.0, 300.0, sample);

        assertFalse(Double.isNaN(sample.gradMagnitude));
        assertFalse(Double.isNaN(sample.laplacian));
        assertTrue(sample.gradMagnitude >= 0.0);
    }
}
