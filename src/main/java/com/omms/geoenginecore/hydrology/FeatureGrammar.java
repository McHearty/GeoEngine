package com.omms.geoenginecore.hydrology;

/**
 * River feature grammar detectors (TECHSPEC §31).
 *
 * <p>Derives feature masks from the drainage graph, meandered geometry,
 * and local terrain properties. All features are deterministic functions
 * of the graph + meander state; none are independent noise patches.
 */
public final class FeatureGrammar {

    /** Feature mask bits (TECHSPEC §31). */
    public static final int F_CONFLUENCE = 1 << 0;
    public static final int F_ANABRANCH = 1 << 1;
    public static final int F_BRAIDED = 1 << 2;
    public static final int F_OXBOW = 1 << 3;
    public static final int F_POINT_BAR = 1 << 4;
    public static final int F_CUT_BANK = 1 << 5;
    public static final int F_LEVEE = 1 << 6;
    public static final int F_FLOODPLAIN = 1 << 7;
    public static final int F_CREVASSE_SPLAY = 1 << 8;
    public static final int F_DELTA = 1 << 9;
    public static final int F_ALLUVIAL_FAN = 1 << 10;

    /** Slope threshold for low-gradient reaches (levees, floodplains). */
    public static final double LOW_SLOPE_THRESHOLD = 0.05;
    /** Curvature threshold for strong meander bends (oxbows). */
    public static final double STRONG_CURVATURE_THRESHOLD = 0.1;
    /** Maximum neck width for oxbow cutoff (blocks). */
    public static final double OXBOW_CUTOFF_WIDTH = 16.0;

    /**
     * Compute the complete feature mask for a column.
     *
     * @param channelOrder channel order (0-4)
     * @param flowAcc flow accumulation A_f
     * @param slope local channel slope
     * @param curvature local channel curvature (signed)
     * @param distanceToThalweg distance to meandered thalweg
     * @param halfWidth channel half-width
     * @param atConfluence whether column is at a confluence node
     * @param parallelFlow whether parallel high-A_f path exists nearby
     * @param atDelta whether column is at a delta outlet
     * @param atAlluvialFan whether column is at an alluvial fan outlet
     * @param leveeHeight local levee height (0 if none)
     * @return feature mask bitfield
     */
    public static int computeFeatureMask(int channelOrder, double flowAcc,
                                         double slope, double curvature,
                                         double distanceToThalweg, double halfWidth,
                                         boolean atConfluence, boolean parallelFlow,
                                         boolean atDelta, boolean atAlluvialFan,
                                         double leveeHeight) {
        int mask = 0;

        // Confluence: graph node with ≥ 2 active upstream edges
        if (atConfluence) {
            mask |= F_CONFLUENCE;
        }

        // Anabranch: parallel high-A_f paths that rejoin
        if (parallelFlow && channelOrder >= 2) {
            mask |= F_ANABRANCH;
        }

        // Braided: high A_f, low slope, wide corridor, multiple threads
        if (flowAcc >= 25.0 && slope < LOW_SLOPE_THRESHOLD && halfWidth >= 6.0) {
            mask |= F_BRAIDED;
        }

        // Point bar / cut bank: inner/outer bank of meander bend
        if (channelOrder >= 1 && distanceToThalweg < halfWidth) {
            double lateral = distanceToThalweg;
            if (Math.abs(curvature) > 0.01) {
                // Sign of curvature determines inner/outer
                if (curvature > 0.0) {
                    // Positive curvature: inner bank on one side, outer on other
                    if (lateral > 0.0) {
                        mask |= F_POINT_BAR;
                    } else {
                        mask |= F_CUT_BANK;
                    }
                } else {
                    if (lateral > 0.0) {
                        mask |= F_CUT_BANK;
                    } else {
                        mask |= F_POINT_BAR;
                    }
                }
            }
        }

        // Levee: natural embankment outside banks on low-gradient reaches
        if (distanceToThalweg >= halfWidth && distanceToThalweg < halfWidth * 2.0
            && slope < LOW_SLOPE_THRESHOLD && channelOrder >= 2) {
            mask |= F_LEVEE;
        }

        // Floodplain: low-gradient envelope around channel
        if (distanceToThalweg >= halfWidth && distanceToThalweg < halfWidth * 3.0
            && slope < LOW_SLOPE_THRESHOLD) {
            mask |= F_FLOODPLAIN;
        }

        // Delta: channel reaches standing water with low slope
        if (atDelta && slope < LOW_SLOPE_THRESHOLD) {
            mask |= F_DELTA;
        }

        // Alluvial fan: channel exits onto low-slope apron
        if (atAlluvialFan) {
            mask |= F_ALLUVIAL_FAN;
        }

        return mask;
    }

    /**
     * Detect oxbow formation from curvature and distance.
     * Oxbows form where the channel bends sharply and the neck is narrow.
     */
    public static boolean detectOxbow(double curvature, double distanceToThalweg, double halfWidth) {
        // Strong curvature + near the neck of the bend
        return Math.abs(curvature) > STRONG_CURVATURE_THRESHOLD
            && distanceToThalweg > halfWidth * 0.5
            && distanceToThalweg < halfWidth * 1.5;
    }

    /**
     * Detect crevasse splay points (levee breaches).
     */
    public static boolean detectCrevasseSplay(double slope, double distanceToThalweg,
                                               double halfWidth, double leveeHeight) {
        // Low point on levee + high flow potential
        return distanceToThalweg >= halfWidth
            && distanceToThalweg < halfWidth * 2.0
            && leveeHeight > 0.0
            && slope < LOW_SLOPE_THRESHOLD;
    }
}