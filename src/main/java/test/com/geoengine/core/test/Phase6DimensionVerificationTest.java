package com.geoengine.core.test;

import com.geoengine.core.dimension.DimensionType;
import com.geoengine.core.dimension.EndProfile;
import com.geoengine.core.dimension.NetherProfile;
import com.geoengine.core.dimension.OverworldProfile;
import com.geoengine.core.math.GeoSample;
import com.geoengine.core.memory.ScratchpadProvider;
import com.geoengine.core.memory.WorkerScratchpad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase6DimensionVerificationTest {
    private static final long TEST_SEED = 0x5EED1234ABCD9876L;
    private OverworldProfile overworld;
    private NetherProfile nether;
    private EndProfile end;

    @BeforeEach
    void setUp() {
        overworld = new OverworldProfile(TEST_SEED, 1);
        nether = new NetherProfile(TEST_SEED, 1);
        end = new EndProfile(TEST_SEED, 1);
    }

    @Test
    @DisplayName("Dimension Identification and Baseline Fluids")
    void testProfileBasics() {
        assertEquals(DimensionType.OVERWORLD, overworld.getDimensionType());
        assertEquals(DimensionType.NETHER, nether.getDimensionType());
        assertEquals(DimensionType.THE_END, end.getDimensionType());

        assertEquals(64, overworld.getFluidLevel());
        assertEquals(32, nether.getFluidLevel());
        assertEquals(-64, end.getFluidLevel());
    }

    @Test
    @DisplayName("Nether Open Sky Verification: No Solid Ceiling at High Altitude")
    void testNetherOpenSky() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        int testX = 128;
        int testZ = -256;

        assertTrue(nether.evaluateDensity(sp, testX, 2, testZ) > 0.0f);

        double surfaceH = nether.evaluateSurface(testX, testZ, sp.sample);
        assertTrue(surfaceH > 20.0 && surfaceH < 180.0);

        for (int y = 200; y < 256; y += 8) {
            float density = nether.evaluateDensity(sp, testX, y, testZ);
            assertTrue(density < 0.0f);
        }
    }

    @Test
    @DisplayName("End Fracture Monoliths: Non-Floating Anchored Slabs")
    void testEndMonolithicAnchor() {
        WorkerScratchpad sp = ScratchpadProvider.get();
        GeoSample sample = new GeoSample();

        int outerX = 500;
        int outerZ = 500;
        double surfaceH = end.evaluateSurface(outerX, outerZ, sample);
        assertTrue(surfaceH > 0.0);

        float coreDensity = end.evaluateDensity(sp, outerX, (int) (surfaceH - 12), outerZ);
        assertTrue(coreDensity > 0.0f);

        float voidDensity = end.evaluateDensity(sp, outerX, (int) (surfaceH - 64), outerZ);
        assertTrue(voidDensity < 0.0f);
    }
}
