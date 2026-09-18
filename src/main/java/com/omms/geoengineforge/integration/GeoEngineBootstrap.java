package com.omms.geoengineforge.integration;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.simd.KernelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeoEngineBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("GeoEngine-Bootstrap");

    public static void validateEnvironment() {
        LOGGER.info("[GeoEngine] Performing geomorphic environment verification...");

        try {
            GeoConfig config = GeoConfig.defaultOverworld(1);
            if (config.worldMinY() >= config.worldMaxY()) {
                throw new IllegalStateException("World bounds invalid");
            }
        } catch (Exception e) {
            LOGGER.error("[GeoEngine] FATAL: Baseline configuration failed validation!", e);
            throw new RuntimeException("GeoEngine initialization aborted", e);
        }

        if (KernelProvider.isVectorApiAvailable()) {
            LOGGER.info("[GeoEngine] Hardware SIMD (Vector API) acceleration is ACTIVE.");
        } else {
            LOGGER.info("[GeoEngine] Running on verified Scalar Reference Authority.");
        }
    }
}
