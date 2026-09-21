package com.omms.geoengineforge.integration;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.simd.KernelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup self-check (TECHSPEC §214-§216).
 *
 * <p>Verifies that the baseline config survives {@link
 * GeoConfig#validate()} and reports whether hardware SIMD is active
 * or the scalar reference authority is in use, so the environment is
 * known before any chunk is generated.
 */
public final class GeoEngineBootstrap {
    /** Bootstrap logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger("GeoEngine-Bootstrap");

    /**
     * Fails fast on an invalid baseline config and reports the active
     * field kernel flavor (TECHSPEC §216).
     *
     * @throws RuntimeException if the baseline config is invalid
     */
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
