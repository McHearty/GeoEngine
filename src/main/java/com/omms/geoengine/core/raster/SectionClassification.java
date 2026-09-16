package com.geoengine.core.raster;

/**
 * Classification of a vertical terrain section for rasterization.
 */
public enum SectionClassification {
    /** Section can be treated as entirely solid. */
    SOLID,

    /** Section can be treated as entirely air. */
    AIR,

    /** Section requires per-block or finer-grained terrain evaluation. */
    BAND
}
