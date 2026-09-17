package com.geoengine.core.material;

/**
 * Material families that can be assigned by the lithology system.
 */
public enum RockFamily {

    /** Sedimentary rock formed from sandstone deposits. */
    SEDIMENTARY_SANDSTONE,

    /** Sedimentary limestone used by shallow stratification rules. */
    SEDIMENTARY_LIMESTONE,

    /** Igneous granite assigned by the intrusion field. */
    IGNEOUS_GRANITE,

    /** Igneous diorite assigned by the intrusion field. */
    IGNEOUS_DIORITE,

    /** Igneous andesite assigned by shallow stratification rules. */
    IGNEOUS_ANDESITE,

    /** Metamorphic tuff assigned by a shallow band within the stratification field. */
    METAMORPHIC_TUFF,

    /** Default rock family when no specialized lithology rule matches. */
    STANDARD_STONE,

    /** Deep rock family assigned below the deepslate transition. */
    DEEP_DEEPSLATE
}
