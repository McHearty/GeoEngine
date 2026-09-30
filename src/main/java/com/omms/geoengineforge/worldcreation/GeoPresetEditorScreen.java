package com.omms.geoengineforge.worldcreation;

import com.omms.geoengineforge.config.GeoEngineConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * GeoEngine world customization screen.
 *
 * <p>Allows players to select a relief profile or manually configure all
 * terrain parameters before world creation.
 */
public class GeoPresetEditorScreen extends Screen implements PresetEditor {

    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private String selectedPreset = "default";

    private Button presetDefaultBtn;
    private Button presetContinentalBtn;
    private Button presetAlpineBtn;
    private Button presetCanyonlandsBtn;
    private Button presetCustomBtn;

    // Sliders for all 10 parameters
    private GeoSlider continentalScaleSlider;
    private GeoSlider mountainScaleSlider;
    private GeoSlider mountainReliefSlider;
    private GeoSlider ridgeRoughnessSlider;
    private GeoSlider valleyFlatnessSlider;
    private GeoSlider stressShearSlider;
    private GeoSlider erosionStrengthSlider;
    private GeoSlider riverIncisionDepthSlider;
    private GeoSlider riverIncisionRateSlider;
    private GeoSlider cliffOverhangIntensitySlider;

    public GeoPresetEditorScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.translatable("geoengine.world.preset.title"));
        this.parent = parent;
        this.context = context;
        this.selectedPreset = GeoEngineConfig.RELIEF_PROFILE.get();
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - 350) / 2;
        int y = 30;

        // Section 1: Preset Selection
        addRenderableWidget(LabelWidget(x, y, "geoengine.world.preset.section.preset"));
        y += 15;

        presetDefaultBtn = Button.builder(
            Component.translatable("geoengine.world.preset.default"),
            b -> selectPreset("default")
        ).bounds(x, y, 165, 20).build();
        addRenderableWidget(presetDefaultBtn);

        presetContinentalBtn = Button.builder(
            Component.translatable("geoengine.world.preset.continental"),
            b -> selectPreset("continental")
        ).bounds(x + 180, y, 165, 20).build();
        addRenderableWidget(presetContinentalBtn);

        y += 25;

        presetAlpineBtn = Button.builder(
            Component.translatable("geoengine.world.preset.alpine"),
            b -> selectPreset("alpine")
        ).bounds(x, y, 165, 20).build();
        addRenderableWidget(presetAlpineBtn);

        presetCanyonlandsBtn = Button.builder(
            Component.translatable("geoengine.world.preset.canyonlands"),
            b -> selectPreset("canyonlands")
        ).bounds(x + 180, y, 165, 20).build();
        addRenderableWidget(presetCanyonlandsBtn);

        y += 25;

        presetCustomBtn = Button.builder(
            Component.translatable("geoengine.world.preset.custom"),
            b -> selectPreset("custom")
        ).bounds(x, y, 350, 20).build();
        addRenderableWidget(presetCustomBtn);

        y += 30;

        // Section 2: Macro Scale & Geometry
        addRenderableWidget(LabelWidget(x, y, "geoengine.world.preset.section.scale"));
        y += 15;

        continentalScaleSlider = new GeoSlider(x, y, 350, GeoEngineConfig.CONTINENTAL_SCALE,
            "geoengine.world.preset.slider.continental_scale");
        addRenderableWidget(continentalScaleSlider);
        y += 25;

        mountainScaleSlider = new GeoSlider(x, y, 350, GeoEngineConfig.MOUNTAIN_SCALE,
            "geoengine.world.preset.slider.mountain_scale");
        addRenderableWidget(mountainScaleSlider);
        y += 25;

        stressShearSlider = new GeoSlider(x, y, 350, GeoEngineConfig.STRESS_SHEAR,
            "geoengine.world.preset.slider.stress_shear");
        addRenderableWidget(stressShearSlider);
        y += 30;

        // Section 3: Relief & Elevation
        addRenderableWidget(LabelWidget(x, y, "geoengine.world.preset.section.relief"));
        y += 15;

        mountainReliefSlider = new GeoSlider(x, y, 350, GeoEngineConfig.MOUNTAIN_RELIEF,
            "geoengine.world.preset.slider.mountain_relief");
        addRenderableWidget(mountainReliefSlider);
        y += 25;

        ridgeRoughnessSlider = new GeoSlider(x, y, 350, GeoEngineConfig.RIDGE_ROUGHNESS,
            "geoengine.world.preset.slider.ridge_roughness");
        addRenderableWidget(ridgeRoughnessSlider);
        y += 25;

        valleyFlatnessSlider = new GeoSlider(x, y, 350, GeoEngineConfig.VALLEY_FLATNESS,
            "geoengine.world.preset.slider.valley_flatness");
        addRenderableWidget(valleyFlatnessSlider);
        y += 30;

        // Section 4: Hydrology & Erosion
        addRenderableWidget(LabelWidget(x, y, "geoengine.world.preset.section.hydrology"));
        y += 15;

        erosionStrengthSlider = new GeoSlider(x, y, 350, GeoEngineConfig.EROSION_STRENGTH,
            "geoengine.world.preset.slider.erosion_strength");
        addRenderableWidget(erosionStrengthSlider);
        y += 25;

        riverIncisionDepthSlider = new GeoSlider(x, y, 350, GeoEngineConfig.RIVER_INCISION_DEPTH,
            "geoengine.world.preset.slider.river_incision_depth");
        addRenderableWidget(riverIncisionDepthSlider);
        y += 25;

        riverIncisionRateSlider = new GeoSlider(x, y, 350, GeoEngineConfig.RIVER_INCISION_RATE,
            "geoengine.world.preset.slider.river_incision_rate");
        addRenderableWidget(riverIncisionRateSlider);
        y += 30;

        // Section 5: Volumetric Detail
        addRenderableWidget(LabelWidget(x, y, "geoengine.world.preset.section.volumetric"));
        y += 15;

        cliffOverhangIntensitySlider = new GeoSlider(x, y, 350, GeoEngineConfig.CLIFF_OVERHANG_INTENSITY,
            "geoengine.world.preset.slider.cliff_overhang_intensity");
        addRenderableWidget(cliffOverhangIntensitySlider);

        y += 35;

        // Done and Cancel buttons
        Button doneButton = Button.builder(
            Component.translatable("geoengine.world.preset.done"),
            b -> onDone()
        ).bounds(x, y, 165, 20).build();
        addRenderableWidget(doneButton);

        Button cancelButton = Button.builder(
            Component.translatable("geoengine.world.preset.cancel"),
            b -> onCancel()
        ).bounds(x + 180, y, 165, 20).build();
        addRenderableWidget(cancelButton);

        // Load current config values into sliders
        loadConfigValues();
        updatePresetButtons();
    }

    private void loadConfigValues() {
        continentalScaleSlider.setValue(GeoEngineConfig.CONTINENTAL_SCALE.get());
        mountainScaleSlider.setValue(GeoEngineConfig.MOUNTAIN_SCALE.get());
        mountainReliefSlider.setValue(GeoEngineConfig.MOUNTAIN_RELIEF.get());
        ridgeRoughnessSlider.setValue(GeoEngineConfig.RIDGE_ROUGHNESS.get());
        valleyFlatnessSlider.setValue(GeoEngineConfig.VALLEY_FLATNESS.get());
        stressShearSlider.setValue(GeoEngineConfig.STRESS_SHEAR.get());
        erosionStrengthSlider.setValue(GeoEngineConfig.EROSION_STRENGTH.get());
        riverIncisionDepthSlider.setValue(GeoEngineConfig.RIVER_INCISION_DEPTH.get());
        riverIncisionRateSlider.setValue(GeoEngineConfig.RIVER_INCISION_RATE.get());
        cliffOverhangIntensitySlider.setValue(GeoEngineConfig.CLIFF_OVERHANG_INTENSITY.get());
    }

    private void selectPreset(String id) {
        selectedPreset = id;
        updatePresetButtons();

        if (!id.equals("custom")) {
            // Load preset values
            switch (id) {
                case "default":
                    loadPresetValues(0.50, 0.45, 0.35, 0.25, 0.60, 0.35, 0.40, 0.35, 0.30, 0.35);
                    break;
                case "continental":
                    loadPresetValues(0.75, 0.50, 0.25, 0.20, 0.70, 0.25, 0.30, 0.25, 0.25, 0.30);
                    break;
                case "alpine":
                    loadPresetValues(0.40, 0.35, 0.75, 0.45, 0.40, 0.40, 0.60, 0.55, 0.40, 0.45);
                    break;
                case "canyonlands":
                    loadPresetValues(0.55, 0.50, 0.45, 0.30, 0.50, 0.30, 0.70, 0.75, 0.50, 0.40);
                    break;
            }
        }
    }

    private void loadPresetValues(double continental, double mountain, double relief, double ridge,
                                  double valley, double stress, double erosion, double riverDepth,
                                  double riverRate, double cliff) {
        continentalScaleSlider.setValue(continental);
        mountainScaleSlider.setValue(mountain);
        mountainReliefSlider.setValue(relief);
        ridgeRoughnessSlider.setValue(ridge);
        valleyFlatnessSlider.setValue(valley);
        stressShearSlider.setValue(stress);
        erosionStrengthSlider.setValue(erosion);
        riverIncisionDepthSlider.setValue(riverDepth);
        riverIncisionRateSlider.setValue(riverRate);
        cliffOverhangIntensitySlider.setValue(cliff);
    }

    private void updatePresetButtons() {
        presetDefaultBtn.active = !selectedPreset.equals("default");
        presetContinentalBtn.active = !selectedPreset.equals("continental");
        presetAlpineBtn.active = !selectedPreset.equals("alpine");
        presetCanyonlandsBtn.active = !selectedPreset.equals("canyonlands");
        presetCustomBtn.active = !selectedPreset.equals("custom");
    }

    private void onDone() {
        // Persist the selected preset and all parameter values
        GeoEngineConfig.RELIEF_PROFILE.set(selectedPreset);
        GeoEngineConfig.CONTINENTAL_SCALE.set(continentalScaleSlider.getValue());
        GeoEngineConfig.MOUNTAIN_SCALE.set(mountainScaleSlider.getValue());
        GeoEngineConfig.MOUNTAIN_RELIEF.set(mountainReliefSlider.getValue());
        GeoEngineConfig.RIDGE_ROUGHNESS.set(ridgeRoughnessSlider.getValue());
        GeoEngineConfig.VALLEY_FLATNESS.set(valleyFlatnessSlider.getValue());
        GeoEngineConfig.STRESS_SHEAR.set(stressShearSlider.getValue());
        GeoEngineConfig.EROSION_STRENGTH.set(erosionStrengthSlider.getValue());
        GeoEngineConfig.RIVER_INCISION_DEPTH.set(riverIncisionDepthSlider.getValue());
        GeoEngineConfig.RIVER_INCISION_RATE.set(riverIncisionRateSlider.getValue());
        GeoEngineConfig.CLIFF_OVERHANG_INTENSITY.set(cliffOverhangIntensitySlider.getValue());
        this.minecraft.setScreen(parent);
    }

    private void onCancel() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public Screen createEditScreen(CreateWorldScreen screen, WorldCreationContext context) {
        return this;
    }

    /**
     * Helper to create a section label widget.
     */
    private static Button LabelWidget(int x, int y, String translationKey) {
        return Button.builder(
            Component.translatable(translationKey).withStyle(ChatFormatting.BOLD),
            b -> {}
        ).bounds(x, y, 350, 15).build();
    }

    /**
     * GeoEngine-specific slider that reads/writes from a ModConfigSpec.DoubleValue.
     */
    private static class GeoSlider extends AbstractSliderButton {
        private final ModConfigSpec.DoubleValue configValue;
        private final String labelKey;

        public GeoSlider(int x, int y, int width, ModConfigSpec.DoubleValue configValue, String labelKey) {
            super(x, y, width, 20, Component.translatable(labelKey), configValue.get());
            this.configValue = configValue;
            this.labelKey = labelKey;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(this.labelKey).append(" (" + String.format("%.2f", this.value) + ")"));
        }

        @Override
        protected void applyValue() {
            configValue.set(this.value);
        }

        public void setValue(double value) {
            this.value = value;
            this.updateMessage();
        }

        public double getValue() {
            return this.value;
        }
    }
}