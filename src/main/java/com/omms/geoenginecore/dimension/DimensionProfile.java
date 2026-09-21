package com.omms.geoenginecore.dimension;

import com.omms.geoenginecore.math.GeoConfig;

/**
 * Dimension-specific generation policy (TECHSPEC §86).
 *
 * <p>A profile binds a {@link GeoConfig} to the vertical layout and
 * the set of geomorphic process families that are active in that
 * dimension. Kernels consult the profile instead of hard-coding
 * process availability, so the same mathematical architecture serves
 * the Overworld, Nether, and End with different parameter sets.
 */
public interface DimensionProfile {
    /**
     * @return the Minecraft dimension this profile models
     */
    DimensionType getDimensionType();

    /**
     * @return the validated configuration for this dimension
     */
    GeoConfig getConfig();

    /**
     * @return the sea/lava level used as the dimension's fluid interface (TECHSPEC §89)
     */
    int getFluidLevel();

    /**
     * @return true when river incision and fluvial deposition are active
     */
    boolean hasFluvialHydrology();

    /**
     * @return true when cryogenic modifiers (U-valleys, cirques) are active
     */
    boolean hasGlacialProcesses();

    /**
     * @return true when karst modifiers (sinkholes, towers) are active
     */
    boolean hasKarstProcesses();

    /**
     * @return true when aeolian modifiers (dunes) are active
     */
    boolean hasAeolianProcesses();

    /**
     * @return true when coastal modifiers (wave-cut platforms, sea arches) are active
     */
    boolean hasCoastalProcesses();

    /**
     * @return true when volcanic modifiers are active
     */
    boolean hasVolcanicProcesses();

    /**
     * @return true when the Voronoi fracture field replaces the baseline surface
     */
    boolean hasVoronoiFracture();
}
