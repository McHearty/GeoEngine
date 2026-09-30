package com.omms.geoenginecore.preset;

import com.omms.geoenginecore.math.NormalizedGeoParams;

import java.util.HashMap;
import java.util.Map;

/**
 * GeoEngine terrain presets define named configurations of
 * normalized terrain parameters for world generation.
 *
 * <p>Each preset maps to a specific {@link NormalizedGeoParams}
 * instance. Presets are identified by string IDs and stored
 * in the BUILTIN map.
 */
public final class GeoEnginePreset {

    /**
     * Map of built-in GeoEngine presets.
     */
    public static final Map<String, GeoEnginePreset> BUILTIN = new HashMap<>();

    static {
        // Default preset: balanced terrain with moderate relief
        BUILTIN.put("default", new GeoEnginePreset("default",
            NormalizedGeoParams.defaultOverworld()));

        // Continental preset: broad landmasses, gentle relief
        BUILTIN.put("continental", new GeoEnginePreset("continental",
            new NormalizedGeoParams(0.8, 0.6, 0.5, 0.4, 0.6, 0.5,
                0.3, 0.5, 0.4, 0.5)));

        // Alpine preset: high relief mountains, deep valleys
        BUILTIN.put("alpine", new GeoEnginePreset("alpine",
            new NormalizedGeoParams(0.5, 0.8, 0.9, 0.8, 0.3, 0.5,
                0.7, 0.6, 0.7, 0.5)));

        // Canyonlands preset: high erosion, deep incision
        BUILTIN.put("canyonlands", new GeoEnginePreset("canyonlands",
            new NormalizedGeoParams(0.6, 0.7, 0.6, 0.7, 0.5, 0.5,
                0.9, 0.8, 0.9, 0.5)));
    }

    /**
     * Returns the built-in preset with the given ID.
     *
     * @param id preset identifier
     * @return preset, or null if not found
     */
    public static GeoEnginePreset getBuiltIn(String id) {
        return BUILTIN.get(id);
    }

    /**
     * Creates a custom GeoEngine preset.
     *
     * @param id unique preset identifier
     * @param params normalized terrain parameters
     * @return new preset instance
     */
    public static GeoEnginePreset create(String id, NormalizedGeoParams params) {
        return new GeoEnginePreset(id, params);
    }

    private final String id;
    private final NormalizedGeoParams parameters;

    private GeoEnginePreset(String id, NormalizedGeoParams parameters) {
        this.id = id;
        this.parameters = parameters;
    }

    /**
     * Returns the preset identifier.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the normalized parameters for this preset.
     */
    public NormalizedGeoParams parameters() {
        return parameters;
    }
}