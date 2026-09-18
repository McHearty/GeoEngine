package com.omms.geoenginecore.geomorphology;

public enum LandformType {
    PLAINS(1, 10),
    PLATEAU(2, 50),
    MESA(3, 70),
    BUTTE(4, 80),

    HILL(5, 30),
    RIDGE(6, 40),
    MOUNTAIN(7, 60),
    MASSIF(8, 65),

    VALLEY(9, 45),
    GORGE(10, 75),
    CANYON(11, 85),
    BASIN(12, 35),
    SADDLE(13, 25),

    SHIELD_VOLCANO(14, 68),
    VOLCANIC_CONE(15, 82),
    CALDERA(16, 95),

    DUNE_FIELD(17, 55),
    YARDANG(18, 58),
    FJORD(19, 90),
    SINKHOLE_FIELD(20, 78),
    UNKNOWN(0, 0);

    private final int id;
    private final int priority;

    LandformType(int id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    public int getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public static LandformType fromId(int id) {
        for (LandformType type : values()) {
            if (type.id == id) return type;
        }
        return UNKNOWN;
    }
}
