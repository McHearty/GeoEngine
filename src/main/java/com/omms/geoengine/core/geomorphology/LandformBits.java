package com.geoengine.core.geomorphology;

/**
 * Bit layout and accessors for deterministic landform classification.
 *
 * <p>The integer is divided into four logical regions:
 *
 * <ul>
 *   <li>bits 0-3: mutually exclusive shape classification
 *   <li>bits 4-15: process flags
 *   <li>bits 16-23: environmental flags
 *   <li>bits 24-31: deterministic feature-eligibility flags
 * </ul>
 *
 * <p>Shape values occupy a four-bit field and are therefore stored with {@link #setShape(int, int)}
 * and retrieved with {@link #getShape(int)}. The remaining values are independent bit flags.
 */
public final class LandformBits {
    private LandformBits() {}

    // Bits 0-3: Shape classification. Only one shape value is stored in this field.
    public static final int SHAPE_MASK = 0x0000000F;
    public static final int SHAPE_UNKNOWN = 0;
    public static final int SHAPE_FLAT = 1;
    public static final int SHAPE_PEAK = 2;
    public static final int SHAPE_RIDGE = 3;
    public static final int SHAPE_PLATEAU = 4;
    public static final int SHAPE_VALLEY = 5;
    public static final int SHAPE_BASIN = 6;
    public static final int SHAPE_CANYON = 7;
    public static final int SHAPE_SADDLE = 8;

    // Bits 4-15: Independent geomorphological process flags.
    public static final int PROCESS_FLUVIAL = 1 << 4;
    public static final int PROCESS_GLACIAL = 1 << 5;
    public static final int PROCESS_VOLCANIC = 1 << 6;
    public static final int PROCESS_TECTONIC = 1 << 7;
    public static final int PROCESS_AEOLIAN = 1 << 8;
    public static final int PROCESS_KARST = 1 << 9;
    public static final int PROCESS_COASTAL = 1 << 10;
    public static final int PROCESS_EROSIONAL = 1 << 11;
    public static final int PROCESS_CRYOGENIC = 1 << 12;

    // Bits 16-23: Independent environmental classification flags.
    public static final int ENV_SUBMARINE = 1 << 16;
    public static final int ENV_COASTAL = 1 << 17;
    public static final int ENV_LOWLAND = 1 << 18;
    public static final int ENV_HIGHLAND = 1 << 19;
    public static final int ENV_ALPINE = 1 << 20;

    // Bits 24-31: Independent deterministic feature-eligibility flags.
    public static final int FEAT_RIVER_CHANNEL = 1 << 24;
    public static final int FEAT_WATERFALL = 1 << 25;
    public static final int FEAT_SINKHOLE = 1 << 26;
    public static final int FEAT_DUNE_FIELD = 1 << 27;
    public static final int FEAT_VOLCANO_VENT = 1 << 28;
    public static final int FEAT_GLACIER = 1 << 29;

    /**
     * Replaces the shape field while preserving all process, environment, and feature flags.
     *
     * @param bits existing classification bits
     * @param shape shape value to store
     * @return classification bits with the shape field replaced
     */
    public static int setShape(int bits, int shape) {
        return (bits & ~SHAPE_MASK) | (shape & SHAPE_MASK);
    }

    /**
     * Extracts the four-bit shape field from a classification value.
     *
     * @param bits classification bits
     * @return stored shape value
     */
    public static int getShape(int bits) {
        return bits & SHAPE_MASK;
    }

    /**
     * Tests whether a process flag is set.
     *
     * @param bits classification bits
     * @param processFlag process flag to test
     * @return {@code true} when the specified flag is set
     */
    public static boolean hasProcess(int bits, int processFlag) {
        return (bits & processFlag) != 0;
    }

    /**
     * Tests whether an environment flag is set.
     *
     * @param bits classification bits
     * @param envFlag environment flag to test
     * @return {@code true} when the specified flag is set
     */
    public static boolean hasEnvironment(int bits, int envFlag) {
        return (bits & envFlag) != 0;
    }
}
