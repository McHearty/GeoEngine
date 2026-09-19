package com.omms.geoengineforge.surface;

import net.minecraft.world.level.levelgen.SurfaceRules;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SurfaceRuleCompatibility {
    private SurfaceRuleCompatibility() {}

    public static SurfaceRuleCompatibilityReport audit(SurfaceRules.RuleSource ruleSource) {
        Set<String> supported = new HashSet<>();
        Set<String> adapted = new HashSet<>();
        Set<String> unsupported = new HashSet<>();

        scanRule(ruleSource, supported, adapted, unsupported);

        boolean fullySupported = unsupported.isEmpty();
        return new SurfaceRuleCompatibilityReport(fullySupported, supported, adapted, unsupported);
    }

    @SuppressWarnings("unchecked")
    private static void scanRule(
        SurfaceRules.RuleSource rule, 
        Set<String> supported, Set<String> adapted, Set<String> unsupported
    ) {
        if (rule == null) return;
        String name = rule.getClass().getSimpleName();

        if (name.equals("BlockRuleSource")) {
            supported.add("BlockRuleSource");
        } else if (name.equals("SequenceRuleSource")) {
            supported.add("SequenceRuleSource");
            try {
                Method m = rule.getClass().getDeclaredMethod("sequence");
                m.setAccessible(true);
                List<SurfaceRules.RuleSource> children = (List<SurfaceRules.RuleSource>) m.invoke(rule);
                for (SurfaceRules.RuleSource child : children) {
                    scanRule(child, supported, adapted, unsupported);
                }
            } catch (Exception ignored) {}
        } else if (name.equals("TestRuleSource")) {
            supported.add("TestRuleSource");
            try {
                Method mIfTrue = rule.getClass().getDeclaredMethod("ifTrue");
                Method mThenRun = rule.getClass().getDeclaredMethod("thenRun");
                mIfTrue.setAccessible(true);
                mThenRun.setAccessible(true);
                scanCondition((SurfaceRules.ConditionSource) mIfTrue.invoke(rule), supported, adapted, unsupported);
                scanRule((SurfaceRules.RuleSource) mThenRun.invoke(rule), supported, adapted, unsupported);
            } catch (Exception ignored) {}
        } else if (name.equals("Bandlands")) {
            adapted.add("Bandlands (Terracotta Strata Engine)");
        } else {
            unsupported.add("CustomRuleSource: " + name);
        }
    }

    private static void scanCondition(
        SurfaceRules.ConditionSource condition, 
        Set<String> supported, Set<String> adapted, Set<String> unsupported
    ) {
        if (condition == null) return;
        String name = condition.getClass().getSimpleName();

        if (name.equals("BiomeConditionSource")) {
            supported.add("BiomeConditionSource");
        } else if (name.equals("StoneDepthCheck")) {
            supported.add("StoneDepthCheck");
        } else if (name.equals("WaterConditionSource")) {
            supported.add("WaterConditionSource");
        } else if (name.equals("YConditionSource")) {
            supported.add("YConditionSource");
        } else if (name.equals("Steep")) {
            supported.add("Steep (GeoEngine Derivative Slope)");
        } else if (name.equals("Hole")) {
            supported.add("Hole (GeoEngine Cave Void)");
        } else if (name.equals("AbovePreliminarySurface")) {
            adapted.add("AbovePreliminarySurface (Pre-carve H0)");
        } else if (name.equals("Temperature")) {
            supported.add("Temperature");
        } else if (name.equals("NotConditionSource")) {
            supported.add("NotConditionSource");
            try {
                Method m = condition.getClass().getDeclaredMethod("target");
                m.setAccessible(true);
                scanCondition((SurfaceRules.ConditionSource) m.invoke(condition), supported, adapted, unsupported);
            } catch (Exception ignored) {}
        } else if (name.equals("VerticalGradientConditionSource")) {
            adapted.add("VerticalGradient (Deterministic Hash)");
        } else if (name.equals("NoiseThresholdConditionSource")) {
            adapted.add("NoiseThreshold (GeoEngine Tectonic Domain)");
        } else {
            unsupported.add("CustomCondition: " + name);
        }
    }
}
