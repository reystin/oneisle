package com.reystin.oneisle.client;

import com.reystin.oneisle.client.ui.OneButton;
import com.reystin.oneisle.client.ui.OneControlsPanel;
import com.reystin.oneisle.client.ui.OneMetrics;
import com.reystin.oneisle.client.ui.OneSegmented;
import com.reystin.oneisle.client.ui.OneSlider;
import com.reystin.oneisle.client.ui.OneTheme;
import com.reystin.oneisle.client.ui.OneToggle;
import com.reystin.oneisle.client.ui.OneUi;
import com.reystin.oneisle.continent.IslandClimate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Advanced world generation options with the flat editor chrome. */
final class AdvancedSettingsScreen extends Screen {
    private static final double[] STRUCTURE_DENSITIES = {0.5, 1.0, 2.0};

    private final OneIsleEditorScreen owner;
    private final IslandDraft state;
    private final List<AbstractWidget> content = new ArrayList<>();
    private final Map<AbstractWidget, Component> tips = new IdentityHashMap<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int pad;
    private String titleText = "";

    AdvancedSettingsScreen(OneIsleEditorScreen owner, IslandDraft state) {
        super(Component.translatable("oneisle.editor.advanced.title"));
        this.owner = owner;
        this.state = state;
    }

    @Override
    protected void init() {
        clearWidgets();
        content.clear();
        tips.clear();
        OneMetrics.tick(font);
        titleText = text("oneisle.editor.advanced.title");

        panelW = Math.min(420, width - 16);
        panelH = Math.min(330, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        pad = panelW < 280 ? 6 : 8;
        int innerX = panelX + pad;
        int innerW = panelW - pad * 2;
        int footerY = panelY + panelH - pad - 22;
        int controlsY = panelY + 24;
        int controlsH = Math.max(18, footerY - 4 - controlsY);
        OneControlsPanel controls = addRenderableWidget(
            new OneControlsPanel(innerX, controlsY, innerW, controlsH));

        int rowX = innerX + 4;
        int rowW = Math.max(20, innerW - 10);
        int y = controlsY + 4;
        int rowH = 22;

        IslandDraft.GenerationVersion[] versions = IslandDraft.GenerationVersion.values();
        String[] versionLabels = new String[versions.length];
        for (int i = 0; i < versions.length; i++) versionLabels[i] = text(versions[i].translationKey);
        int selectedVersion = 0;
        for (int i = 0; i < versions.length; i++) {
            if (versions[i] == state.generationVersion()) selectedVersion = i;
        }
        OneSegmented generation = new OneSegmented(rowX, y, rowW, rowH, versionLabels,
            selectedVersion, index -> state.setGenerationVersion(versions[index]));
        add(controls, generation, "oneisle.editor.generation.tooltip");
        y += rowH + 4;

        OneSlider climate = new OneSlider(rowX, y, rowW, rowH,
            text("oneisle.ui.climate_scale"), (state.climateScale() - 384.0) / 1152.0,
            48.0 / 1152.0, (720.0 - 384.0) / 1152.0, null);
        climate.setValueText(text("oneisle.editor.climate_scale", state.climateScale()));
        climate.setOnChange(value -> {
            state.setClimateScale((int) Math.round(384 + value * 1152));
            climate.setValueText(text("oneisle.editor.climate_scale", state.climateScale()));
        });
        add(controls, climate, "oneisle.editor.climate_scale.tooltip");
        y += rowH + 4;

        IslandClimate.GradientMode[] modes = IslandClimate.GradientMode.values();
        String[] modeLabels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) modeLabels[i] = text(modes[i].translationKey);
        OneSegmented gradient = new OneSegmented(rowX, y, rowW, rowH, modeLabels,
            state.gradientMode().ordinal(), index -> state.setGradientMode(modes[index]));
        add(controls, gradient, "oneisle.editor.gradient.tooltip");
        y += rowH + 4;

        OneSlider strength = new OneSlider(rowX, y, rowW, rowH,
            text("oneisle.ui.gradient_strength"), state.gradientStrength(), 0.05, 0.5, null);
        strength.setValueText(text("oneisle.editor.gradient_strength",
            Math.round(state.gradientStrength() * 100.0) + "%"));
        strength.setOnChange(value -> {
            state.setGradientStrength(Math.round(value * 20.0) / 20.0);
            strength.setValueText(text("oneisle.editor.gradient_strength",
                Math.round(state.gradientStrength() * 100.0) + "%"));
        });
        add(controls, strength, "oneisle.editor.gradient_strength.tooltip");
        y += rowH + 4;

        OneSlider islets = new OneSlider(rowX, y, rowW, rowH,
            text("oneisle.advanced.islets"), state.isletDensity(), 0.05, 0.25, null);
        islets.setValueText(text("oneisle.editor.islets",
            Math.round(state.isletDensity() * 100.0) + "%"));
        islets.setOnChange(value -> {
            state.setIsletDensity(Math.round(value * 20.0) / 20.0);
            islets.setValueText(text("oneisle.editor.islets",
                Math.round(state.isletDensity() * 100.0) + "%"));
        });
        add(controls, islets, "oneisle.editor.islets.tooltip");
        y += rowH + 4;

        OneSlider rivers = new OneSlider(rowX, y, rowW, rowH,
            text("oneisle.advanced.rivers"), state.riverDensity(), 0.05, 0.35, null);
        rivers.setValueText(text("oneisle.editor.rivers",
            Math.round(state.riverDensity() * 100.0) + "%"));
        rivers.setOnChange(value -> {
            state.setRiverDensity(Math.round(value * 20.0) / 20.0);
            rivers.setValueText(text("oneisle.editor.rivers",
                Math.round(state.riverDensity() * 100.0) + "%"));
        });
        add(controls, rivers, "oneisle.editor.rivers.tooltip");
        y += rowH + 4;

        String[] densityLabels = new String[STRUCTURE_DENSITIES.length];
        int selectedDensity = 1;
        for (int i = 0; i < STRUCTURE_DENSITIES.length; i++) {
            densityLabels[i] = densityLabel(STRUCTURE_DENSITIES[i]);
            if (Math.abs(state.structureDensity() - STRUCTURE_DENSITIES[i]) < 1.0E-6) selectedDensity = i;
        }
        OneSegmented structures = new OneSegmented(rowX, y, rowW, rowH, densityLabels,
            selectedDensity, index -> state.setStructureDensity(STRUCTURE_DENSITIES[index]));
        add(controls, structures, "oneisle.editor.structure_density.tooltip");
        y += rowH + 4;

        OneToggle border = new OneToggle(rowX, y, rowW, rowH,
            text("oneisle.editor.world_border"), state.isWorldBorderEnabled(),
            value -> state.setWorldBorderEnabled(value));
        add(controls, border, "oneisle.editor.world_border.tooltip");
        y += rowH + 4;

        OneSlider margin = new OneSlider(rowX, y, rowW, rowH,
            text("oneisle.advanced.border_margin"), (state.worldBorderMargin() - 500.0) / 3500.0,
            1.0 / 3500.0, (1000.0 - 500.0) / 3500.0, null);
        margin.setValueText(text("oneisle.editor.border_margin", state.worldBorderMargin()));
        margin.setOnChange(value -> {
            state.setWorldBorderMargin((int) Math.round(500 + value * 3500));
            margin.setValueText(text("oneisle.editor.border_margin", state.worldBorderMargin()));
        });
        add(controls, margin, "oneisle.editor.border_margin.tooltip");

        controls.setActive(content);

        addRenderableWidget(new OneButton(innerX + innerW - Math.min(120, innerW), footerY,
            Math.min(120, innerW), 22, text("gui.done"), OneButton.Style.PRIMARY, this::onClose));
    }

    private void add(OneControlsPanel controls, AbstractWidget widget, String tipKey) {
        controls.add(widget);
        content.add(widget);
        tips.put(widget, Component.translatable(tipKey));
    }

    private static String densityLabel(double value) {
        return value == Math.rint(value)
            ? (int) value + ".0x"
            : String.format(java.util.Locale.ROOT, "%.1fx", value);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        g.fill(0, 0, width, height, OneTheme.OVERLAY);
        OneUi.panel(g, panelX, panelY, panelW, panelH, OneTheme.PANEL, OneTheme.BORDER);
        OneUi.textFit(g, font, titleText, panelX + pad, panelY + 9,
            panelW - pad * 2, OneTheme.TEXT);
        OneUi.divider(g, panelX + pad, panelY + 21, panelW - pad * 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        for (Map.Entry<AbstractWidget, Component> entry : tips.entrySet()) {
            if (entry.getKey().visible && entry.getKey().isMouseOver(mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, entry.getValue(), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public void onClose() {
        ClientScreenNavigator.open(minecraft, owner);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
