package com.geoengine.core.geomorphology;

public final class LandformBits {
    private LandformBits() {}

    // Bits 0-3: Shape Class
    public static final int SHAPE_MASK          = 0x0000000F;
    public static final int SHAPE_UNKNOWN       = 0;
    public static final int SHAPE_FLAT          = 1;
    public static final int SHAPE_PEAK          = 2;
    public static final int SHAPE_RIDGE         = 3;
    public static final int SHAPE_PLATEAU       = 4;
    public static final int SHAPE_VALLEY        = 5;
    public static final int SHAPE_BASIN         = 6;
    public static final int SHAPE_CANYON        = 7;
    public static final int SHAPE_SADDLE        = 8;

    // Bits 4-15: Process Flags
    public static final int PROCESS_FLUVIAL     = 1 << 4;
    public static final int PROCESS_GLACIAL     = 1 << 5;
    public static final int PROCESS_VOLCANIC    = 1 << 6;
    public static final int PROCESS_TECTONIC    = 1 << 7;
    public static final int PROCESS_AEOLIAN     = 1 << 8;
    public static final int PROCESS_KARST       = 1 << 9;
    public static final int PROCESS_COASTAL     = 1 << 10;
    public static final int PROCESS_EROSIONAL   = 1 << 11;
    public static final int PROCESS_CRYOGENIC   = 1 << 12;

    // Bits 16-23: Environment Flags
    public static final int ENV_SUBMARINE       = 1 << 16;
    public static final int ENV_COASTAL         = 1 << 17;
    public static final int ENV_LOWLAND         = 1 << 18;
    public static final int ENV_HIGHLAND        = 1 << 19;
    public static final int ENV_ALPINE          = 1 << 20;

    // Bits 24-31: Deterministic Feature Eligibility Flags
    public static final int FEAT_RIVER_CHANNEL  = 1 << 24;
    public static final int FEAT_WATERFALL      = 1 << 25;
    public static final int FEAT_SINKHOLE       = 1 << 26;
    public static final int FEAT_DUNE_FIELD      = 1 << 27;
    public static final int FEAT_VOLCANO_VENT   = 1 << 28;
    public static final int FEAT_GLACIER        = 1 << 29;

    public static int setShape(int bits, int shape) {
        return (bits & ~SHAPE_MASK) | (shape & SHAPE_MASK);
    }

    public static int getShape(int bits) {
        return bits & SHAPE_MASK;
    }

    public static boolean hasProcess(int bits, int processFlag) {
        return (bits & processFlag) != 0;
    }

    public static boolean hasEnvironment(int bits, int envFlag) {
        return (bits & envFlag) != 0;
    }
}
