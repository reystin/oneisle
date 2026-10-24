package com.hack337.omniisle.client;

import com.hack337.omniisle.continent.IslandClimate;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Advanced world generation options for OmniIsle islands. */
final class AdvancedSettingsScreen extends Screen {
    private final OmniIsleEditorScreen owner;
    private final IslandDraft state;

    AdvancedSettingsScreen(OmniIsleEditorScreen owner, IslandDraft state) {
        super(Component.translatable("omniisle.editor.advanced.title"));
        this.owner = owner;
        this.state = state;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(260, width - 40);
        int panelHeight = Math.min(204, height - 36);
        int panelY = Math.max(16, (height - panelHeight) / 2);
        int startX = (width - panelWidth) / 2;
        int y = panelY + 24;

        CycleButton<IslandDraft.GenerationVersion> genButton = CycleButton
            .<IslandDraft.GenerationVersion>builder(val -> Component.translatable(val.translationKey), state.generationVersion())
            .withValues(List.of(IslandDraft.GenerationVersion.values()))
            .withTooltip(val -> Tooltip.create(Component.translatable("omniisle.editor.generation.tooltip")))
            .create(startX, y, panelWidth, 20, Component.translatable("omniisle.editor.generation"), (button, value) -> {
                state.setGenerationVersion(value);
            });
        addRenderableWidget(genButton);
        y += 24;

        ClimateScaleSlider scaleSlider = new ClimateScaleSlider(startX, y, panelWidth, 20);
        scaleSlider.setTooltip(Tooltip.create(Component.translatable("omniisle.editor.climate_scale.tooltip")));
        addRenderableWidget(scaleSlider);
        y += 24;

        CycleButton<IslandClimate.GradientMode> gradButton = CycleButton
            .<IslandClimate.GradientMode>builder(val -> Component.translatable(val.translationKey), state.gradientMode())
            .withValues(List.of(IslandClimate.GradientMode.values()))
            .withTooltip(val -> Tooltip.create(Component.translatable("omniisle.editor.gradient.tooltip")))
            .create(startX, y, panelWidth, 20, Component.translatable("omniisle.editor.gradient", Component.translatable(state.gradientMode().translationKey)), (button, value) -> {
                state.setGradientMode(value);
            });
        addRenderableWidget(gradButton);
        y += 24;

        GradientStrengthSlider strengthSlider = new GradientStrengthSlider(startX, y, panelWidth, 20);
        strengthSlider.setTooltip(Tooltip.create(Component.translatable("omniisle.editor.gradient_strength.tooltip")));
        addRenderableWidget(strengthSlider);
        y += 24;

        IsletDensitySlider isletSlider = new IsletDensitySlider(startX, y, panelWidth, 20);
        isletSlider.setTooltip(Tooltip.create(Component.translatable("omniisle.editor.islets.tooltip")));
        addRenderableWidget(isletSlider);
        y += 24;

        CycleButton<Double> structDensityButton = CycleButton
            .<Double>builder(val -> Component.literal(String.format(java.util.Locale.ROOT, "%.1fx", val)), state.structureDensity())
            .withValues(List.of(0.5, 1.0, 2.0))
            .withTooltip(val -> Tooltip.create(Component.translatable("omniisle.editor.structure_density.tooltip")))
            .create(startX, y, panelWidth, 20, Component.translatable("omniisle.editor.structure_density", String.format(java.util.Locale.ROOT, "%.1fx", state.structureDensity())), (button, value) -> {
                state.setStructureDensity(value);
            });
        addRenderableWidget(structDensityButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x30000000);
        int panelWidth = Math.min(280, width - 24);
        int panelHeight = Math.min(204, height - 36);
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(16, (height - panelHeight) / 2);
        panel(context, panelX, panelY, panelWidth, panelHeight, 0xFF202B2C);
        context.centeredText(font, title, width / 2, panelY + 7, 0xFFF4E8C1);
    }

    @Override
    public void onClose() {
        ClientScreenNavigator.open(minecraft, owner);
    }

    private static void panel(GuiGraphicsExtractor context, int x, int y, int w, int h, int interior) {
        context.fill(x, y, x + w, y + h, 0xFF090B0A);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF788078);
        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF343A35);
        context.fill(x + 4, y + 4, x + w - 4, y + h - 4, interior);
    }

    private final class ClimateScaleSlider extends AbstractSliderButton {
        private ClimateScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (state.climateScale() - 384) / (1536.0 - 384.0));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("omniisle.editor.climate_scale", state.climateScale()));
        }

        @Override
        protected void applyValue() {
            int snapped = 384 + (int) Math.round((value * (1536 - 384)) / 48.0) * 48;
            state.setClimateScale(snapped);
            updateMessage();
        }
    }

    private final class GradientStrengthSlider extends AbstractSliderButton {
        private GradientStrengthSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), state.gradientStrength());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int pct = (int) Math.round(state.gradientStrength() * 100.0);
            setMessage(Component.translatable("omniisle.editor.gradient_strength", pct + "%"));
        }

        @Override
        protected void applyValue() {
            state.setGradientStrength(value);
            updateMessage();
        }
    }

    private final class IsletDensitySlider extends AbstractSliderButton {
        private IsletDensitySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), state.isletDensity());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int pct = (int) Math.round(value * 100.0);
            setMessage(Component.translatable("omniisle.editor.islets", pct + "%"));
        }

        @Override
        protected void applyValue() {
            state.setIsletDensity(value);
            updateMessage();
        }
    }
}
