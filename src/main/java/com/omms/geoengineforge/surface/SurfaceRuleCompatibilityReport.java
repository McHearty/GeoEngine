package com.omms.geoengineforge.surface;

import java.util.Set;

public record SurfaceRuleCompatibilityReport(
    boolean fullySupported,
    Set<String> supportedConditions,
    Set<String> adaptedConditions,
    Set<String> unsupportedConditions
) {
    public String formatReport() {
        return String.format(
            "=== SurfaceRules Compatibility Report ===\n" +
            "  Fully Supported: %b\n" +
            "  Native Conditions:  %s\n" +
            "  Adapted Conditions: %s\n" +
            "  Unsupported:        %s\n" +
            "=========================================",
            fullySupported, supportedConditions, adaptedConditions, unsupportedConditions
        );
    }
}
