package com.omms.geoenginecore.material;

/**
 * Rock family taxonomy (TECHSPEC §72-§73).
 *
 * <p>Material resolution maps each family to one or more concrete
 * materials; families that may occur as strata must carry an
 * explicit strata flag.
 */
public enum RockFamily {
    /** Sedimentary sandstone. */
    SEDIMENTARY_SANDSTONE,
    /** Sedimentary limestone. */
    SEDIMENTARY_LIMESTONE,
    /** Coarse-grained intrusive igneous rock. */
    IGNEOUS_GRANITE,
    /** Medium-grained intrusive igneous rock. */
    IGNEOUS_DIORITE,
    /** Volcanic extrusive igneous rock. */
    IGNEOUS_ANDESITE,
    /** Metamorphic volcanic tuff. */
    METAMORPHIC_TUFF,
    /** Default bedrock. */
    STANDARD_STONE,
    /** World-floor deep slate. */
    DEEP_DEEPSLATE
}
