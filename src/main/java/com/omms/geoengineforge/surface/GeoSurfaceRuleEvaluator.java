package com.omms.geoengineforge.surface;

import com.omms.geoenginecore.noise.SeedDerivation;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class GeoSurfaceRuleEvaluator {
    // Badlands Terracotta Canonical Band Colors
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState RED_TERRACOTTA = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();

    private final BlockState[] terracottaBands = new BlockState[64];
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    public GeoSurfaceRuleEvaluator() {
        initBadlandsBands();
    }

    private void initBadlandsBands() {
        for (int i = 0; i < 64; i++) {
            int cycle = i % 16;
            if (cycle == 0 || cycle == 1) terracottaBands[i] = WHITE_TERRACOTTA;
            else if (cycle == 2 || cycle == 3) terracottaBands[i] = ORANGE_TERRACOTTA;
            else if (cycle == 4) terracottaBands[i] = TERRACOTTA;
            else if (cycle == 5 || cycle == 6) terracottaBands[i] = YELLOW_TERRACOTTA;
            else if (cycle == 7 || cycle == 8) terracottaBands[i] = BROWN_TERRACOTTA;
            else if (cycle == 9 || cycle == 10) terracottaBands[i] = RED_TERRACOTTA;
            else if (cycle == 11) terracottaBands[i] = LIGHT_GRAY_TERRACOTTA;
            else terracottaBands[i] = TERRACOTTA;
        }
    }

    private static Method getAccessor(Class<?> clazz, String methodName) {
        String key = clazz.getName() + "#" + methodName;
        return METHOD_CACHE.computeIfAbsent(key, k -> {
            try {
                Method m = clazz.getDeclaredMethod(methodName);
                m.setAccessible(true);
                return m;
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Evaluates a RuleSource against the GeoSurfaceRuleContext (§11).
     */
    @SuppressWarnings("unchecked")
    public BlockState evaluate(SurfaceRules.RuleSource ruleSource, GeoSurfaceRuleContext context) {
        if (ruleSource == null) return null;

        String name = ruleSource.getClass().getSimpleName();

        // 1. BlockRuleSource: returns resultState()
        if (name.equals("BlockRuleSource")) {
            Method m = getAccessor(ruleSource.getClass(), "resultState");
            if (m != null) {
                try {
                    return (BlockState) m.invoke(ruleSource);
                } catch (Exception ignored) {}
            }
            return null;
        }

        // 2. TestRuleSource: evaluates ifTrue() and thenRun()
        if (name.equals("TestRuleSource")) {
            Method mIfTrue = getAccessor(ruleSource.getClass(), "ifTrue");
            Method mThenRun = getAccessor(ruleSource.getClass(), "thenRun");
            if (mIfTrue != null && mThenRun != null) {
                try {
                    SurfaceRules.ConditionSource condition = (SurfaceRules.ConditionSource) mIfTrue.invoke(ruleSource);
                    if (evaluateCondition(condition, context)) {
                        SurfaceRules.RuleSource thenRun = (SurfaceRules.RuleSource) mThenRun.invoke(ruleSource);
                        return evaluate(thenRun, context);
                    }
                } catch (Exception ignored) {}
            }
            return null;
        }

        // 3. SequenceRuleSource: iterates sequence() children
        if (name.equals("SequenceRuleSource")) {
            Method mSeq = getAccessor(ruleSource.getClass(), "sequence");
            if (mSeq != null) {
                try {
                    List<SurfaceRules.RuleSource> children = (List<SurfaceRules.RuleSource>) mSeq.invoke(ruleSource);
                    for (SurfaceRules.RuleSource child : children) {
                        BlockState state = evaluate(child, context);
                        if (state != null) return state;
                    }
                } catch (Exception ignored) {}
            }
            return null;
        }

        // 4. Bandlands: terracotta banding
        if (name.equals("Bandlands")) {
            return evaluateBadlandsBand(context);
        }

        return null;
    }

    /**
     * Evaluates ConditionSource nodes against GeoEngine context state (§12, §13).
     */
    @SuppressWarnings("unchecked")
    public boolean evaluateCondition(SurfaceRules.ConditionSource condition, GeoSurfaceRuleContext context) {
        if (condition == null) return false;

        String name = condition.getClass().getSimpleName();

        // 1. BiomeConditionSource
        if (name.equals("BiomeConditionSource")) {
            Method m = getAccessor(condition.getClass(), "biomes");
            if (m != null) {
                try {
                    List<ResourceKey<Biome>> biomes = (List<ResourceKey<Biome>>) m.invoke(condition);
                    Holder<Biome> holder = context.biome();
                    ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
                    if (key != null && biomes.contains(key)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            return false;
        }

        // 2. StoneDepthCheck
        if (name.equals("StoneDepthCheck")) {
            Method mOffset = getAccessor(condition.getClass(), "offset");
            Method mAddSurface = getAccessor(condition.getClass(), "addSurfaceDepth");
            Method mSurfaceType = getAccessor(condition.getClass(), "surfaceType");
            if (mOffset != null && mAddSurface != null && mSurfaceType != null) {
                try {
                    int offset = (int) mOffset.invoke(condition);
                    boolean addSurface = (boolean) mAddSurface.invoke(condition);
                    Object surfaceType = mSurfaceType.invoke(condition);

                    boolean isCeiling = surfaceType != null && surfaceType.toString().equals("CEILING");
                    int depth = isCeiling ? context.stoneDepthAbove() : context.stoneDepthBelow();
                    int required = offset;
                    if (addSurface) required += context.surfaceDepth();
                    return depth <= required;
                } catch (Exception ignored) {}
            }
            return false;
        }

        // 3. WaterConditionSource
        if (name.equals("WaterConditionSource")) {
            Method mOffset = getAccessor(condition.getClass(), "offset");
            Method mAddStone = getAccessor(condition.getClass(), "addStoneDepth");
            if (mOffset != null && mAddStone != null) {
                try {
                    int offset = (int) mOffset.invoke(condition);
                    boolean addStone = (boolean) mAddStone.invoke(condition);
                    if (addStone) offset -= context.stoneDepthBelow();
                    return context.blockY() >= context.waterHeight() + offset;
                } catch (Exception ignored) {}
            }
            return false;
        }

        // 4. YConditionSource
        if (name.equals("YConditionSource")) {
            Method mAnchor = getAccessor(condition.getClass(), "anchor");
            Method mAddStone = getAccessor(condition.getClass(), "addStoneDepth");
            if (mAnchor != null && mAddStone != null) {
                try {
                    VerticalAnchor anchor = (VerticalAnchor) mAnchor.invoke(condition);
                    boolean addStone = (boolean) mAddStone.invoke(condition);
                    int targetY = anchor.resolveY(context.generationContext());
                    int y = context.blockY();
                    if (addStone) y += context.stoneDepthBelow();
                    return y >= targetY;
                } catch (Exception ignored) {}
            }
            return false;
        }

        // 5. Steep
        if (name.equals("Steep")) {
            return context.isSteep();
        }

        // 6. Hole
        if (name.equals("Hole")) {
            return context.isHole();
        }

        // 7. AbovePreliminarySurface
        if (name.equals("AbovePreliminarySurface")) {
            return context.isAbovePreliminarySurface();
        }

        // 8. Temperature
        if (name.equals("Temperature")) {
            return context.temperature() < 0.15;
        }

        // 9. NotConditionSource
        if (name.equals("NotConditionSource")) {
            Method mTarget = getAccessor(condition.getClass(), "target");
            if (mTarget != null) {
                try {
                    SurfaceRules.ConditionSource target = (SurfaceRules.ConditionSource) mTarget.invoke(condition);
                    return !evaluateCondition(target, context);
                } catch (Exception ignored) {}
            }
            return false;
        }

        // 10. VerticalGradientConditionSource
        if (name.equals("VerticalGradientConditionSource")) {
            Method mTrue = getAccessor(condition.getClass(), "trueAtAndBelow");
            Method mFalse = getAccessor(condition.getClass(), "falseAtAndAbove");
            if (mTrue != null && mFalse != null) {
                try {
                    VerticalAnchor trueAnchor = (VerticalAnchor) mTrue.invoke(condition);
                    VerticalAnchor falseAnchor = (VerticalAnchor) mFalse.invoke(condition);
                    int bottomY = trueAnchor.resolveY(context.generationContext());
                    int topY = falseAnchor.resolveY(context.generationContext());
                    int y = context.blockY();
                    if (y <= bottomY) return true;
                    if (y >= topY) return false;

                    double t = (double) (y - bottomY) / (topY - bottomY);
                    long hash = SeedDerivation.hashCoords(context.position().getX(), y, context.position().getZ());
                    double rand = (hash & 0xFFFF) / 65535.0;
                    return rand > t;
                } catch (Exception ignored) {}
            }
            return false;
        }

        // 11. NoiseThresholdConditionSource
        if (name.equals("NoiseThresholdConditionSource")) {
            Method mMin = getAccessor(condition.getClass(), "minThreshold");
            Method mMax = getAccessor(condition.getClass(), "maxThreshold");
            if (mMin != null && mMax != null) {
                try {
                    double min = (double) mMin.invoke(condition);
                    double max = (double) mMax.invoke(condition);
                    long hash = SeedDerivation.hashCoords(context.position().getX() * 3, 0, context.position().getZ() * 3);
                    double val = ((hash & 0xFFFF) / 32768.0) - 1.0;
                    return val >= min && val <= max;
                } catch (Exception ignored) {}
            }
            return false;
        }

        return false;
    }

    private BlockState evaluateBadlandsBand(GeoSurfaceRuleContext context) {
        int bandIndex = Math.floorMod(context.blockY() + (context.position().getX() / 16), 64);
        return terracottaBands[bandIndex];
    }
}
