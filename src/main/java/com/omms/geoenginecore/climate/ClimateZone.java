package com.omms.geoenginecore.climate;

/**
 * Climate zone taxonomy (TECHSPEC §21).
 *
 * <p>Classification order is fixed: polar and tundra are decided by
 * temperature first, then aridity and warmth decide desert, warm
 * humid, or the temperate fallback.
 */
public enum ClimateZone {
    /** Effective temperature below 0.20. */
    POLAR,
    /** Effective temperature 0.20..0.40. */
    BOREAL_TUNDRA,
    /** Default zone when no other rule matches. */
    TEMPERATE,
    /** Warm (above 0.65) and humid (above 0.60). */
    WARM_HUMID,
    /** Arid (humidity below 0.22) and warm (above 0.55). */
    ARID_DESERT
}
