package com.omms.geoenginecore.test.hydrology;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.omms.geoenginecore.hydrology.FeatureGrammar;
import com.omms.geoenginecore.hydrology.ChannelField;

/**
 * Sprint C.1: Signed lateral offset for bank asymmetry.
 * Verifies that point bar vs cut bank detection works with signed distance.
 */
class SignedLateralOffsetTest {

    @Test
    void pointBarOnInnerBank() {
        // Inner bank: positive signed distance (right side when facing downstream)
        // in a left-curving bend (positive curvature)
        int mask = FeatureGrammar.computeFeatureMask(
            3, 100.0, 0.05, 0.02,  // order, flowAcc, slope, curvature (left bend, >0.01 threshold)
            1.0,  // signed distance (right bank = inner bank for left bend)
            5.0,  // halfWidth
            false, false, false, false, 0.0);

        assertTrue((mask & FeatureGrammar.F_POINT_BAR) != 0, "Should detect point bar on inner bank");
    }

    @Test
    void cutBankOnOuterBank() {
        // Outer bank: negative signed distance (left side when facing downstream)
        // in a left-curving bend (positive curvature)
        int mask = FeatureGrammar.computeFeatureMask(
            3, 100.0, 0.05, 0.02,  // order, flowAcc, slope, curvature (left bend, >0.01 threshold)
            -1.0,  // signed distance (left bank = outer bank for left bend)
            5.0,   // halfWidth
            false, false, false, false, 0.0);

        assertTrue((mask & FeatureGrammar.F_CUT_BANK) != 0, "Should detect cut bank on outer bank");
    }

    @Test
    void noAsymmetryOnStraightReach() {
        // Straight reach: zero curvature → no point bar or cut bank
        int mask = FeatureGrammar.computeFeatureMask(
            3, 100.0, 0.05, 0.0,  // order, flowAcc, slope, curvature (straight)
            1.0,  // signed distance
            5.0,  // halfWidth
            false, false, false, false, 0.0);

        assertEquals(0, mask & FeatureGrammar.F_POINT_BAR, "No point bar on straight reach");
        assertEquals(0, mask & FeatureGrammar.F_CUT_BANK, "No cut bank on straight reach");
    }

    @Test
    void asymmetryRequiresChannel() {
        // Must be within channel (distance < halfWidth)
        int mask = FeatureGrammar.computeFeatureMask(
            3, 100.0, 0.05, 0.02,  // order, flowAcc, slope, curvature
            10.0,  // signed distance (outside channel)
            5.0,   // halfWidth
            false, false, false, false, 0.0);

        assertEquals(0, mask & FeatureGrammar.F_POINT_BAR, "No point bar outside channel");
        assertEquals(0, mask & FeatureGrammar.F_CUT_BANK, "No cut bank outside channel");
    }
}
