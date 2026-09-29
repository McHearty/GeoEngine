package com.omms.geoenginecore.test.phase1;

import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.dimension.EndProfile;
import com.omms.geoenginecore.dimension.NetherProfile;
import com.omms.geoenginecore.dimension.OverworldProfile;
import com.omms.geoenginecore.field.CaveField;
import com.omms.geoenginecore.geomorphology.LandformBits;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.raster.SectionClassifier;
import com.omms.geoenginecore.raster.SectionClassification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No NaN/Infinity acceptance criterion (Phase 1): every field the
 * pipeline emits must be finite and within its domain across all
 * dimension profiles, the full seed matrix, and many chunk positions.
 */
public class Phase1FinitenessSweepTest {

    private static final long[] SEEDS = {
        0L, 0x9876543210FEDCBAL, 0x7FFFFFFFFFFFFFFFL, 0x7CAFEBABED00DCAFL
    };
    private static final int[][] POSITIONS = {
        {0, 0}, {16, 0}, {0, 16}, {-16, 0}, {256, 256}, {16, -16}
    };

    private static final String[] DOUBLE_GRID_NAMES = {
        "macroH0", "macroTectonic", "macroAge", "macroTemp", "macroHumid", "macroErosion",
        "surfaceGrid", "h0Grid", "gradXGrid", "gradZGrid", "laplacianGrid",
        "ageGrid", "tempGrid", "humidGrid", "climateMultGrid", "erosionGrid",
        "flowAccGrid", "riverIncisionGrid", "depositionGrid",
        "simdFx", "simdOneMinusFx", "simdN00", "simdN10", "simdN01", "simdN11",
        "simdYVals", "simdWVals"
    };

    @Test
    @DisplayName("Scratchpad Finiteness Sweep: every grid finite across all dimensions, seeds, and positions")
    void testScratchpadFinitenessSweep() {
        DimensionProfile[] profiles = {
            new OverworldProfile(1), new NetherProfile(1), new EndProfile(1)
        };
        for (DimensionProfile profile : profiles) {
            for (long seed : SEEDS) {
                ScalarFieldKernel kernel = new ScalarFieldKernel(seed, profile);
                for (int[] pos : POSITIONS) {
                    WorkerScratchpad sp = new WorkerScratchpad();
                    kernel.rasterizeSurfaceChunk(sp, pos[0], pos[1]);
                    assertAllGridsFinite(sp, profile.getDimensionType().name());
                    assertValidLandformIds(sp, profile.getDimensionType().name());
                    assertWaterLevelsInBounds(sp, profile.getConfig().worldMinY(),
                        profile.getDimensionType().name());
                }
            }
        }
    }

    @Test
    @DisplayName("Full-Column Finiteness Sweep: every GeoSample field finite and in domain")
    void testSampleFinitenessSweep() {
        DimensionProfile[] profiles = {
            new OverworldProfile(1), new NetherProfile(1), new EndProfile(1)
        };
        for (DimensionProfile profile : profiles) {
            for (long seed : SEEDS) {
                ScalarFieldKernel kernel = new ScalarFieldKernel(seed, profile);
                for (int[] pos : POSITIONS) {
                    GeoSample sample = new GeoSample();
                    kernel.evaluateFullColumn(pos[0] * 16.0 + 8.0, pos[1] * 16.0 + 8.0, sample);
                    assertSampleInDomain(sample, profile.getConfig().worldMinY(),
                        profile.getDimensionType().name() + " seed " + seed);
                }
            }
        }
    }

    @Test
    @DisplayName("Section Classification Invariants: totality, AIR above the surface+band, SOLID/BAND below")
    void testSectionClassificationInvariants() {
        GeoConfig config = GeoConfig.defaultOverworld(1);
        SectionClassifier classifier = new SectionClassifier(config, new CaveField(0x9876543210FEDCBAL, config));
        WorkerScratchpad sp = new WorkerScratchpad();
        new ScalarFieldKernel(0x9876543210FEDCBAL, new OverworldProfile(1)).rasterizeSurfaceChunk(sp, 0, 0);

        // Totality: every in-bounds section must classify (never null).
        for (int y = config.worldMinY(); y < config.worldMaxY(); y += 128) {
            SectionClassification section = classifier.classifySection(sp, 0, y, 0);
            assertNotNull(section, "section min Y=" + y + " must classify, never null");
        }

        // Per-column invariants from the rasterized surface field.
        int bandRadius = config.surfaceBandRadius();
        int margin = 64;
        int caveMinY = config.caveMinY();
        int caveMaxY = config.caveMaxY();
        for (int i = 0; i < sp.surfaceGrid.length; i += 17) {
            int surfaceY = (int) Math.floor(sp.surfaceGrid[i]);

            // A section entirely above the surface band and above the cave
            // envelope cannot contain terrain or caves, so it must be AIR.
            int aboveY = surfaceY + bandRadius + margin;
            if (aboveY >= caveMaxY && aboveY + 128 <= config.worldMaxY()) {
                SectionClassification above = classifier.classifySection(sp, 0, aboveY, 0);
                assertEquals(SectionClassification.AIR, above,
                    "column " + i + ": section [" + aboveY + ", " + (aboveY + 127) + "] is above the surface band and cave envelope");
            }

            // A section entirely below the surface band is never AIR; it is
            // SOLID unless the cave envelope can intersect it (BAND).
            int belowY = surfaceY - bandRadius - margin - 128;
            if (belowY >= config.worldMinY()) {
                SectionClassification below = classifier.classifySection(sp, 0, belowY, 0);
                assertNotEquals(SectionClassification.AIR, below,
                    "column " + i + ": section [" + belowY + ", " + (belowY + 127) + "] is below the surface band");
                if (belowY + 127 < caveMinY) {
                    assertEquals(SectionClassification.SOLID, below,
                        "column " + i + ": section below the cave envelope must be SOLID");
                }
            }
        }
    }

    private void assertAllGridsFinite(WorkerScratchpad sp, String context) {
        double[][] grids = {
            sp.macroH0, sp.macroTectonic, sp.macroAge, sp.macroTemp, sp.macroHumid, sp.macroErosion,
            sp.surfaceGrid, sp.h0Grid, sp.gradXGrid, sp.gradZGrid, sp.laplacianGrid,
            sp.ageGrid, sp.tempGrid, sp.humidGrid, sp.climateMultGrid, sp.erosionGrid,
            sp.flowAccGrid, sp.riverIncisionGrid, sp.depositionGrid,
            sp.simdFx, sp.simdOneMinusFx, sp.simdN00, sp.simdN10, sp.simdN01, sp.simdN11,
            sp.simdYVals, sp.simdWVals
        };
        for (int g = 0; g < DOUBLE_GRID_NAMES.length; g++) {
            for (int i = 0; i < grids[g].length; i++) {
                assertTrue(Double.isFinite(grids[g][i]),
                    context + " " + DOUBLE_GRID_NAMES[g] + "[" + i + "] must be finite, was " + grids[g][i]);
            }
        }
    }

    private void assertValidLandformIds(WorkerScratchpad sp, String context) {
        // UNKNOWN (id 0) is the grammar's designed terminal sentinel for
        // unresolvable columns; a non-taxonomy id is a defect, and a
        // heavy fall-through rate signals a broken grammar.
        int unknownCount = 0;
        for (int i = 0; i < sp.classificationBitsGrid.length; i++) {
            int id = sp.classificationBitsGrid[i] & LandformBits.TYPE_MASK;
            assertTrue(isValidLandformId(id),
                context + " classificationBitsGrid[" + i + "] has non-taxonomy id " + id);
            if (id == 0) {
                unknownCount++;
            }
        }
        assertTrue(unknownCount * 20 <= sp.classificationBitsGrid.length,
            context + " UNKNOWN fallback used on " + unknownCount
                + " of " + sp.classificationBitsGrid.length + " columns (> 5%)");
    }

    /** Id 0 is the designed UNKNOWN sentinel; the real taxonomy is 1..20. */
    private static boolean isValidLandformId(int id) {
        return id == 0 || (id >= 1 && id <= 20);
    }

    private void assertWaterLevelsInBounds(WorkerScratchpad sp, int worldMinY, String context) {
        for (int i = 0; i < sp.riverWaterLevelGrid.length; i++) {
            assertTrue(sp.riverWaterLevelGrid[i] >= worldMinY,
                context + " riverWaterLevelGrid[" + i + "] must stay above the world floor");
        }
    }

    private void assertSampleInDomain(GeoSample sample, int worldMinY, String context) {
        assertTrue(Double.isFinite(sample.stressWarpX), context + " stressWarpX finite");
        assertTrue(Double.isFinite(sample.stressWarpZ), context + " stressWarpZ finite");
        assertTrue(Double.isFinite(sample.warpedX), context + " warpedX finite");
        assertTrue(Double.isFinite(sample.warpedZ), context + " warpedZ finite");
        assertTrue(Double.isFinite(sample.age), context + " age finite");
        assertTrue(Double.isFinite(sample.temperature), context + " temperature finite");
        assertTrue(Double.isFinite(sample.humidity), context + " humidity finite");
        assertTrue(Double.isFinite(sample.climateMultiplier), context + " climateMultiplier finite");
        assertTrue(Double.isFinite(sample.rawTectonic), context + " rawTectonic finite");
        assertTrue(Double.isFinite(sample.erosionLowering), context + " erosionLowering finite");
        assertTrue(Double.isFinite(sample.surfaceH0), context + " surfaceH0 finite");
        assertTrue(Double.isFinite(sample.gradX), context + " gradX finite");
        assertTrue(Double.isFinite(sample.gradZ), context + " gradZ finite");
        assertTrue(Double.isFinite(sample.gradMagnitude), context + " gradMagnitude finite");
        assertTrue(Double.isFinite(sample.laplacian), context + " laplacian finite");
        assertTrue(Double.isFinite(sample.flowAccumulation), context + " flowAccumulation finite");
        assertTrue(Double.isFinite(sample.riverIncision), context + " riverIncision finite");
        assertTrue(Double.isFinite(sample.deposition), context + " deposition finite");
        assertTrue(Double.isFinite(sample.finalSurface), context + " finalSurface finite");
        assertTrue(sample.waterSurfaceLevel >= worldMinY, context + " waterSurfaceLevel in world bounds");
        assertTrue(isValidLandformId(sample.classificationBits & LandformBits.TYPE_MASK),
            context + " classificationBits has non-taxonomy id "
                + (sample.classificationBits & LandformBits.TYPE_MASK));
    }
}
