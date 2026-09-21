package com.omms.geoenginecore.geomorphology;

/**
 * Landform taxonomy with grammar priority (TECHSPEC §95).
 *
 * <p>Each type has a stable id (packed into
 * {@link LandformBits}) and a resolution priority used when several
 * landform candidates apply to the same column; the higher priority
 * wins.
 */
public enum LandformType {
    /** Flat low-gradient terrain. */
    PLAINS(1, 10),
    /** Elevated flat surface. */
    PLATEAU(2, 50),
    /** Flat-topped erosional remnant of moderate relief. */
    MESA(3, 70),
    /** Small isolated erosional remnant. */
    BUTTE(4, 80),

    /** Low rounded positive relief. */
    HILL(5, 30),
    /** Elongated positive relief (one concave principal curvature). */
    RIDGE(6, 40),
    /** Convex summit relief. */
    MOUNTAIN(7, 60),
    /** Large regional mountain mass. */
    MASSIF(8, 65),

    /** Fluvial concave channel. */
    VALLEY(9, 45),
    /** Steep walled fluvial channel. */
    GORGE(10, 75),
    /** Deep, steep, strongly incised channel. */
    CANYON(11, 85),
    /** Depositional concave surface. */
    BASIN(12, 35),
    /** Mixed-sign curvature pass. */
    SADDLE(13, 25),

    /** Broad low-gradient volcanic shield. */
    SHIELD_VOLCANO(14, 68),
    /** Convex volcanic edifice. */
    VOLCANIC_CONE(15, 82),
    /** Collapsed volcanic depression with rim. */
    CALDERA(16, 95),

    /** Aeolian dune terrain. */
    DUNE_FIELD(17, 55),
    /** Wind-carved ridged terrain. */
    YARDANG(18, 58),
    /** Glacially carved coastal inlet. */
    FJORD(19, 90),
    /** Karst dissolution terrain. */
    SINKHOLE_FIELD(20, 78),
    /** Unresolvable landform. */
    UNKNOWN(0, 0);

    /** Stable taxonomy id, packed into {@link LandformBits}. */
    private final int id;
    /** Grammar resolution priority (higher wins). */
    private final int priority;

    /**
     * @param id stable taxonomy id
     * @param priority grammar resolution priority
     */
    LandformType(int id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    /**
     * @return stable taxonomy id
     */
    public int getId() {
        return id;
    }

    /**
     * @return grammar resolution priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @param id stable taxonomy id
     * @return matching type, or {@link #UNKNOWN} when not found
     */
    public static LandformType fromId(int id) {
        for (LandformType type : values()) {
            if (type.id == id) return type;
        }
        return UNKNOWN;
    }
}
