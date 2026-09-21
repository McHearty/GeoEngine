package com.omms.geoenginecore.geomorphology;

/**
 * Bit-packed landform classification (TECHSPEC §96).
 *
 * <p>One {@code int} carries the landform type (low 8 bits), the
 * process flags (bits 8-16), the environment flags (bits 20-24),
 * and the feature flags (bits 26-31). The packed form is the single
 * deterministic classification artifact stored on
 * {@link GeoSample}; the unused bits are reserved headroom.
 */
public final class LandformBits {
    /** Hides the implicit constructor. This is a static utility class. */
    private LandformBits() {}

    /** Mask of the landform-type id (low 8 bits). */
    public static final int TYPE_MASK           = 0x000000FF;

    /** Fluvial (river) process active. */
    public static final int PROCESS_FLUVIAL     = 1 << 8;
    /** Glacial process active. */
    public static final int PROCESS_GLACIAL     = 1 << 9;
    /** Volcanic process active. */
    public static final int PROCESS_VOLCANIC    = 1 << 10;
    /** Tectonic process dominant. */
    public static final int PROCESS_TECTONIC    = 1 << 11;
    /** Aeolian (wind) process active. */
    public static final int PROCESS_AEOLIAN     = 1 << 12;
    /** Karst process active. */
    public static final int PROCESS_KARST       = 1 << 13;
    /** Coastal process active. */
    public static final int PROCESS_COASTAL     = 1 << 14;
    /** Erosional (weathering) process active. */
    public static final int PROCESS_EROSIONAL   = 1 << 15;
    /** Cryogenic process active. */
    public static final int PROCESS_CRYOGENIC   = 1 << 16;

    /** Submarine environment. */
    public static final int ENV_SUBMARINE       = 1 << 20;
    /** Coastal environment (0..+6 above sea level). */
    public static final int ENV_COASTAL         = 1 << 21;
    /** Lowland environment (+6..+120 above sea level). */
    public static final int ENV_LOWLAND         = 1 << 22;
    /** Highland environment (+120..+260 above sea level). */
    public static final int ENV_HIGHLAND        = 1 << 23;
    /** Alpine environment (above +260 sea level). */
    public static final int ENV_ALPINE          = 1 << 24;

    /** River channel feature present. */
    public static final int FEAT_RIVER_CHANNEL  = 1 << 26;
    /** Waterfall feature present. */
    public static final int FEAT_WATERFALL      = 1 << 27;
    /** Sinkhole feature present. */
    public static final int FEAT_SINKHOLE       = 1 << 28;
    /** Dune field feature present. */
    public static final int FEAT_DUNE_FIELD      = 1 << 29;
    /** Volcanic vent feature present. */
    public static final int FEAT_VOLCANO_VENT   = 1 << 30;
    /** Glacier feature present. */
    public static final int FEAT_GLACIER        = 1 << 31;

    /**
     * Stores a landform type id in the type field.
     *
     * @param bits current packed bits
     * @param type landform type to store
     * @return bits with the type id replaced
     */
    public static int setType(int bits, LandformType type) {
        return (bits & ~TYPE_MASK) | (type.getId() & TYPE_MASK);
    }

    /**
     * @param bits packed classification bits
     * @return decoded landform type
     */
    public static LandformType getType(int bits) {
        return LandformType.fromId(bits & TYPE_MASK);
    }

    /**
     * @param bits packed classification bits
     * @param processFlag one of the {@code PROCESS_*} flags
     * @return true when the process flag is set
     */
    public static boolean hasProcess(int bits, int processFlag) {
        return (bits & processFlag) != 0;
    }

    /**
     * @param bits packed classification bits
     * @param envFlag one of the {@code ENV_*} flags
     * @return true when the environment flag is set
     */
    public static boolean hasEnvironment(int bits, int envFlag) {
        return (bits & envFlag) != 0;
    }
}
