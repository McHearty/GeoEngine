package com.omms.geoenginecore.dimension;

/**
 * The Minecraft dimensions GeoEngine generates terrain for
 * (TECHSPEC §86).
 */
public enum DimensionType {
    /** Overworld: conventional terrestrial geomorphology. */
    OVERWORLD,
    /** Nether: volcanic geomorphology with lava as the fluid interface. */
    NETHER,
    /** End: non-terrestrial fracture-based monolithic terrain. */
    THE_END
}
