package com.omms.geoenginecore.raster;

/**
 * Per-section raster classification (TECHSPEC §55).
 *
 * <p>SOLID: the section can be written directly from cached fields;
 * AIR: the section can be skipped as empty space; BAND: the surface
 * or caves intersect the section, and the full per-voxel density
 * pass must be run.
 */
public enum SectionClassification {
    /** The whole section is solid; write from cached fields only. */
    SOLID,
    /** The whole section is air; skip it. */
    AIR,
    /** Surface and/or caves intersect; run the per-voxel density pass. */
    BAND
}
