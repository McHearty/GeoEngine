package com.omms.geoengineforge.integration;

import com.omms.geoenginecore.dimension.*;
import com.omms.geoengineforge.config.GeoEngineConfig;

/**
 * Maps NeoForge dimension identity to a validated GeoEngine
 * {@link DimensionProfile} (TECHSPEC §77).
 *
 * <p>Overworld profiles are read dynamically from
 * config/geoengine-common.toml, so slider changes take effect for
 * new worlds without touching code.
 */
public final class GeoDimensionProfile {
    /** Hides the implicit constructor. This is a static factory. */
    private GeoDimensionProfile() {}

    /**
     * Resolves the profile for a numeric dimension id (0 overworld,
     * 1 nether, 2 end).
     *
     * @param dimensionId dimension id: 0 overworld, 1 nether, 2 end
     * @param version generator version bound into the profile
     * @return validated dimension profile
     */
    public static DimensionProfile getProfileFor(int dimensionId, int version) {
        if (dimensionId == 1) {
            return new NetherProfile(version);
        } else if (dimensionId == 2) {
            return new EndProfile(version);
        }
        // Overworld: reads normalized values dynamically from config/geoengine-common.toml
        return new OverworldProfile(GeoEngineConfig.getActiveOverworldConfig(version));
    }

    /**
     * Resolves the profile from a dimension registry key.
     *
     * @param dimensionKey dimension registry key
     * @param version generator version bound into the profile
     * @return validated dimension profile
     */
    public static DimensionProfile getProfileFor(String dimensionKey, int version) {
        if (dimensionKey.contains("nether")) {
            return new NetherProfile(version);
        } else if (dimensionKey.contains("end")) {
            return new EndProfile(version);
        }
        return new OverworldProfile(GeoEngineConfig.getActiveOverworldConfig(version));
    }
}
