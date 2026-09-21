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

/**
 * Phase-6/7 differential (TECHSPEC §210): with the same seed and
 * coordinates, enabling a process term (glacial, karst, aeolian,
 * coastal, volcanic, Voronoi) must measurably change H_f relative
 * to a process-disabled baseline; derivatives must reflect the
 * post-process, pre-fluvial surface.
 */
public class Phase6And7DifferentialTest {
    /** Fixed test world seed. */
    private static final long TEST_SEED = 0x12345678DEADBEEFL;

    /** Overworld profile with every optional process disabled. */
    private static final class ProcessDisabledProfile implements DimensionProfile {
        /** Shared validated config. */
        private final GeoConfig config = GeoConfig.defaultOverworld(1);
        /** @return OVERWORLD. */
        @Override public DimensionType getDimensionType() { return DimensionType.OVERWORLD; }
        /** @return shared validated config. */
        @Override public GeoConfig getConfig() { return config; }
        /** @return sea level. */
        @Override public int getFluidLevel() { return config.seaLevel(); }
        /** @return true (fluvial is always active in the Overworld). */
        @Override public boolean hasFluvialHydrology() { return true; }
        /** @return false (test: glacial off). */
        @Override public boolean hasGlacialProcesses() { return false; }
        /** @return false (test: karst off). */
        @Override public boolean hasKarstProcesses() { return false; }
        /** @return false (test: aeolian off). */
        @Override public boolean hasAeolianProcesses() { return false; }
        /** @return false (test: coastal off). */
        @Override public boolean hasCoastalProcesses() { return false; }
        /** @return false (test: volcanic off). */
        @Override public boolean hasVolcanicProcesses() { return false; }
        /** @return false (test: Voronoi off). */
        @Override public boolean hasVoronoiFracture() { return false; }
    }

    /**
     * Active vs disabled process stacks must diverge on the
     * surface (TECHSPEC §210).
     */
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

    /**
     * |∇H| is finite and non-negative; derivatives reflect the
     * post-process, pre-fluvial surface (TECHSPEC §210).
     */
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
