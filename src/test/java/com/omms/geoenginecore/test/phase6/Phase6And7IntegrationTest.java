package com.omms.geoenginecore.test.phase6;

import com.omms.geoenginecore.dimension.EndProfile;
import com.omms.geoenginecore.dimension.NetherProfile;
import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-6 dimension reconfiguration (TECHSPEC §210): Overworld
 * process terms are live in the pipeline; the Nether disables
 * fluvial and enables volcanic; the End disables fluvial and
 * anchors monoliths (no floating slabs).
 */
public class Phase6And7IntegrationTest {
    /** Fixed test world seed. */
    private static final long TEST_SEED = 0xABCD1234EF567890L;

    /**
     * Overworld H_f is finite and above the world floor with all
     * process terms active.
     */
    @Test
    @DisplayName("Phase 7 Verification: Additive Process Terms active in Overworld Pipeline")
    void testOverworldAdditiveProcessTerms() {
        OverworldProfile profile = new OverworldProfile(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(TEST_SEED, profile);
        WorkerScratchpad sp = ScratchpadProvider.get();

        int chunkX = 64;
        int chunkZ = -128;
        kernel.rasterizeSurfaceChunk(sp, chunkX, chunkZ);

        for (int i = 0; i < WorkerScratchpad.CHUNK_SURFACE_SIZE; i++) {
            double hf = sp.surfaceGrid[i];
            assertFalse(Double.isNaN(hf));
            assertFalse(Double.isInfinite(hf));
            assertTrue(hf > profile.getConfig().worldMinY());
        }
    }

    /**
     * Nether: no fluvial hydrology, volcanic processes active,
     * fluid level 32, and air (D &lt; 0) above the Nether surface
     * (TECHSPEC §210).
     */
    @Test
    @DisplayName("Phase 6 Verification: Nether profile reconfigures field stack (Zero Fluvial, Active Lava)")
    void testNetherStackReconfiguration() {
        NetherProfile nether = new NetherProfile(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(TEST_SEED, nether);
        WorkerScratchpad sp = ScratchpadProvider.get();

        assertFalse(nether.hasFluvialHydrology());
        assertTrue(nether.hasVolcanicProcesses());
        assertEquals(32, nether.getFluidLevel());

        float highAltitudeDensity = kernel.evaluateDensity(sp, 100, 220, 100);
        assertTrue(highAltitudeDensity < 0.0f);
    }

    /**
     * End: Voronoi fracture active, no fluvial; the monolith is
     * solid at mid-depth (D &gt; 0) and void in the abyss (D &lt; 0)
     * — no floating slabs (TECHSPEC §210).
     */
    @Test
    @DisplayName("Phase 6 Verification: End profile enforces Voronoi monoliths and eliminates floating slabs")
    void testEndMonolithicAnchorVerification() {
        EndProfile end = new EndProfile(1);
        ScalarFieldKernel kernel = new ScalarFieldKernel(TEST_SEED, end);
        WorkerScratchpad sp = ScratchpadProvider.get();
        GeoSample sample = new GeoSample();

        assertTrue(end.hasVoronoiFracture());
        assertFalse(end.hasFluvialHydrology());

        int outerX = 600;
        int outerZ = 600;
        kernel.rasterizeSurfaceChunk(sp, outerX, outerZ);
        kernel.evaluateFullColumn(outerX, outerZ, sample);
        double surfaceH = sample.finalSurface;
        assertTrue(surfaceH > 40.0);

        float midPlateauDensity = kernel.evaluateDensity(sp, outerX, (int) (surfaceH - 14), outerZ);
        assertTrue(midPlateauDensity > 0.0f);

        float abyssDensity = kernel.evaluateDensity(sp, outerX, (int) (surfaceH - 50), outerZ);
        assertTrue(abyssDensity < 0.0f);
    }
}
