package com.omms.geoengineforge.client;

import com.omms.geoengineforge.GeoEngineMod;
import com.omms.geoengineforge.integration.GeoWorldPresets;
import com.omms.geoengineforge.worldcreation.GeoPresetEditorScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;

/**
 * Registers GeoEngine's PresetEditor for world customization.
 */
@EventBusSubscriber(modid = GeoEngineMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class GeoPresetEditorRegistrar {

    @SubscribeEvent
    public static void registerPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(GeoWorldPresets.GEOENGINE,
            (screen, context) -> new GeoPresetEditorScreen(screen, context));
    }
}