package com.omms.geoengineforge.integration;

import com.omms.geoenginecore.dimension.*;
import com.omms.geoengineforge.config.GeoEngineConfig;

public final class GeoDimensionProfile {
    private GeoDimensionProfile() {}

    public static DimensionProfile getProfileFor(int dimensionId, int version) {
        if (dimensionId == 1) {
            return new NetherProfile(version);
        } else if (dimensionId == 2) {
            return new EndProfile(version);
        }
        // Overworld: reads normalized values dynamically from config/geoengine-common.toml
        return new OverworldProfile(GeoEngineConfig.getActiveOverworldConfig(version));
    }

    public static DimensionProfile getProfileFor(String dimensionKey, int version) {
        if (dimensionKey.contains("nether")) {
            return new NetherProfile(version);
        } else if (dimensionKey.contains("end")) {
            return new EndProfile(version);
        }
        return new OverworldProfile(GeoEngineConfig.getActiveOverworldConfig(version));
    }
}
