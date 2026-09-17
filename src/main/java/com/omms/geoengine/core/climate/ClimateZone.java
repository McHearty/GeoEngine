package com.geoengine.core.climate;

/**
 * Discrete climate classifications assigned by the terrain climate system.
 *
 * <p>The enum provides categorical climate labels used by downstream terrain-generation logic.
 * The criteria for assigning each zone are defined by the classifier rather than by this enum.
 */
public enum ClimateZone {

    /** Cold climate classification associated with polar conditions. */
    POLAR,

    /** Cold climate classification associated with boreal and tundra conditions. */
    BOREAL_TUNDRA,

    /** Mid-range climate classification for temperate conditions. */
    TEMPERATE,

    /** Warm climate classification associated with humid conditions. */
    WARM_HUMID,

    /** Dry climate classification associated with arid desert conditions. */
    ARID_DESERT
}
