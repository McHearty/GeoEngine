package com.omms.geoenginecore.geomorphology;

public final class LandformBits {
    private LandformBits() {}

    public static final int TYPE_MASK           = 0x000000FF;

    public static final int PROCESS_FLUVIAL     = 1 << 8;
    public static final int PROCESS_GLACIAL     = 1 << 9;
    public static final int PROCESS_VOLCANIC    = 1 << 10;
    public static final int PROCESS_TECTONIC    = 1 << 11;
    public static final int PROCESS_AEOLIAN     = 1 << 12;
    public static final int PROCESS_KARST       = 1 << 13;
    public static final int PROCESS_COASTAL     = 1 << 14;
    public static final int PROCESS_EROSIONAL   = 1 << 15;
    public static final int PROCESS_CRYOGENIC   = 1 << 16;

    public static final int ENV_SUBMARINE       = 1 << 20;
    public static final int ENV_COASTAL         = 1 << 21;
    public static final int ENV_LOWLAND         = 1 << 22;
    public static final int ENV_HIGHLAND        = 1 << 23;
    public static final int ENV_ALPINE          = 1 << 24;

    public static final int FEAT_RIVER_CHANNEL  = 1 << 26;
    public static final int FEAT_WATERFALL      = 1 << 27;
    public static final int FEAT_SINKHOLE       = 1 << 28;
    public static final int FEAT_DUNE_FIELD      = 1 << 29;
    public static final int FEAT_VOLCANO_VENT   = 1 << 30;
    public static final int FEAT_GLACIER        = 1 << 31;

    public static int setType(int bits, LandformType type) {
        return (bits & ~TYPE_MASK) | (type.getId() & TYPE_MASK);
    }

    public static LandformType getType(int bits) {
        return LandformType.fromId(bits & TYPE_MASK);
    }

    public static boolean hasProcess(int bits, int processFlag) {
        return (bits & processFlag) != 0;
    }

    public static boolean hasEnvironment(int bits, int envFlag) {
        return (bits & envFlag) != 0;
    }
}
