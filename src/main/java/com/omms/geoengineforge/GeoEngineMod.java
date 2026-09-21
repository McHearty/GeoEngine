package com.omms.geoengineforge;

import com.omms.geoengineforge.config.GeoEngineConfig;
import com.omms.geoengineforge.debug.GeoDebugCommands;
import com.omms.geoengineforge.integration.GeoEngineBootstrap;
import com.omms.geoengineforge.integration.GeoRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GeoEngine NeoForge mod entry point.
 *
 * <p>Bootstrap order: (1) register the shared ModConfigSpec, which
 * generates config/geoengine-common.toml; (2) register the worldgen
 * generator and biome-source codecs (TECHSPEC §204); (3) validate
 * the environment and report SIMD capability (TECHSPEC
 * §214-§216); (4) register the /geoengine debug commands on the game
 * event bus (TECHSPEC §152-§157).
 */
@Mod(GeoEngineMod.MOD_ID)
public class GeoEngineMod {
    /** Mod id / registry namespace. */
    public static final String MOD_ID = "geoengine";
    /** Mod logger. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Mod entry point (TECHSPEC §77).
     *
     * @param modEventBus mod event bus
     * @param modContainer mod container
     */
    public GeoEngineMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[GeoEngine] Bootstrapping Old Man Modding Studio Geomorphic Terrain Engine.");

        // Register configuration specification (generates config/geoengine-common.toml)
        modContainer.registerConfig(ModConfig.Type.COMMON, GeoEngineConfig.SPEC, "geoengine-common.toml");

        // Initialize worldgen registries
        GeoRegistry.init(modEventBus);

        // Validate environment and vector capabilities
        GeoEngineBootstrap.validateEnvironment();

        // Register debug commands on the game event bus
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    /**
     * Registers the /geoengine debug commands on the server command
     * bus (TECHSPEC §152-§157).
     *
     * @param event command registration event
     */
    private void onRegisterCommands(RegisterCommandsEvent event) {
        GeoDebugCommands.register(event.getDispatcher());
    }
}
