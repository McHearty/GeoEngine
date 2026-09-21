package com.omms.geoengineforge.surface;

import java.util.Set;

/**
 * Result of a SurfaceRules compatibility audit (TECHSPEC §15):
 * which rule/condition nodes the GeoEngine interpreter supports
 * natively, which are adapted, and which are unsupported.
 *
 * @param fullySupported true when every node is supported or adapted
 * @param supportedConditions natively supported condition node names
 * @param adaptedConditions adapted condition node descriptions
 * @param unsupportedConditions unrecognized condition node names
 */
public record SurfaceRuleCompatibilityReport(
    boolean fullySupported,
    Set<String> supportedConditions,
    Set<String> adaptedConditions,
    Set<String> unsupportedConditions
) {
    /**
     * @return human-readable formatted report
     */
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
