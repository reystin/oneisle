package com.hack337.omniisle.client;

import com.hack337.omniisle.client.ui.OmniButton;
import com.hack337.omniisle.client.ui.OmniControlsPanel;
import com.hack337.omniisle.client.ui.OmniMetrics;
import com.hack337.omniisle.client.ui.OmniSegmented;
import com.hack337.omniisle.client.ui.OmniSlider;
import com.hack337.omniisle.client.ui.OmniTheme;
import com.hack337.omniisle.client.ui.OmniToggle;
import com.hack337.omniisle.client.ui.OmniUi;
import com.hack337.omniisle.continent.IslandClimate;
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

    private final OmniIsleEditorScreen owner;
    private final IslandDraft state;
    private final List<AbstractWidget> content = new ArrayList<>();
    private final Map<AbstractWidget, Component> tips = new IdentityHashMap<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int pad;
    private String titleText = "";

    AdvancedSettingsScreen(OmniIsleEditorScreen owner, IslandDraft state) {
        super(Component.translatable("omniisle.editor.advanced.title"));
        this.owner = owner;
        this.state = state;
    }

    @Override
    protected void init() {
        clearWidgets();
        content.clear();
        tips.clear();
        OmniMetrics.tick(font);
        titleText = text("omniisle.editor.advanced.title");

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
        OmniControlsPanel controls = addRenderableWidget(
            new OmniControlsPanel(innerX, controlsY, innerW, controlsH));

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
        OmniSegmented generation = new OmniSegmented(rowX, y, rowW, rowH, versionLabels,
            selectedVersion, index -> state.setGenerationVersion(versions[index]));
        add(controls, generation, "omniisle.editor.generation.tooltip");
        y += rowH + 4;

        OmniSlider climate = new OmniSlider(rowX, y, rowW, rowH,
            text("omniisle.ui.climate_scale"), (state.climateScale() - 384.0) / 1152.0,
            48.0 / 1152.0, (720.0 - 384.0) / 1152.0, null);
        climate.setValueText(text("omniisle.editor.climate_scale", state.climateScale()));
        climate.setOnChange(value -> {
            state.setClimateScale((int) Math.round(384 + value * 1152));
            climate.setValueText(text("omniisle.editor.climate_scale", state.climateScale()));
        });
        add(controls, climate, "omniisle.editor.climate_scale.tooltip");
        y += rowH + 4;

        IslandClimate.GradientMode[] modes = IslandClimate.GradientMode.values();
        String[] modeLabels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) modeLabels[i] = text(modes[i].translationKey);
        OmniSegmented gradient = new OmniSegmented(rowX, y, rowW, rowH, modeLabels,
            state.gradientMode().ordinal(), index -> state.setGradientMode(modes[index]));
        add(controls, gradient, "omniisle.editor.gradient.tooltip");
        y += rowH + 4;

        OmniSlider strength = new OmniSlider(rowX, y, rowW, rowH,
            text("omniisle.ui.gradient_strength"), state.gradientStrength(), 0.05, 0.5, null);
        strength.setValueText(text("omniisle.editor.gradient_strength",
            Math.round(state.gradientStrength() * 100.0) + "%"));
        strength.setOnChange(value -> {
            state.setGradientStrength(Math.round(value * 20.0) / 20.0);
            strength.setValueText(text("omniisle.editor.gradient_strength",
                Math.round(state.gradientStrength() * 100.0) + "%"));
        });
        add(controls, strength, "omniisle.editor.gradient_strength.tooltip");
        y += rowH + 4;

        OmniSlider islets = new OmniSlider(rowX, y, rowW, rowH,
            text("omniisle.advanced.islets"), state.isletDensity(), 0.05, 0.25, null);
        islets.setValueText(text("omniisle.editor.islets",
            Math.round(state.isletDensity() * 100.0) + "%"));
        islets.setOnChange(value -> {
            state.setIsletDensity(Math.round(value * 20.0) / 20.0);
            islets.setValueText(text("omniisle.editor.islets",
                Math.round(state.isletDensity() * 100.0) + "%"));
        });
        add(controls, islets, "omniisle.editor.islets.tooltip");
        y += rowH + 4;

        OmniSlider rivers = new OmniSlider(rowX, y, rowW, rowH,
            text("omniisle.advanced.rivers"), state.riverDensity(), 0.05, 0.35, null);
        rivers.setValueText(text("omniisle.editor.rivers",
            Math.round(state.riverDensity() * 100.0) + "%"));
        rivers.setOnChange(value -> {
            state.setRiverDensity(Math.round(value * 20.0) / 20.0);
            rivers.setValueText(text("omniisle.editor.rivers",
                Math.round(state.riverDensity() * 100.0) + "%"));
        });
        add(controls, rivers, "omniisle.editor.rivers.tooltip");
        y += rowH + 4;

        String[] densityLabels = new String[STRUCTURE_DENSITIES.length];
        int selectedDensity = 1;
        for (int i = 0; i < STRUCTURE_DENSITIES.length; i++) {
            densityLabels[i] = densityLabel(STRUCTURE_DENSITIES[i]);
            if (Math.abs(state.structureDensity() - STRUCTURE_DENSITIES[i]) < 1.0E-6) selectedDensity = i;
        }
        OmniSegmented structures = new OmniSegmented(rowX, y, rowW, rowH, densityLabels,
            selectedDensity, index -> state.setStructureDensity(STRUCTURE_DENSITIES[index]));
        add(controls, structures, "omniisle.editor.structure_density.tooltip");
        y += rowH + 4;

        OmniToggle border = new OmniToggle(rowX, y, rowW, rowH,
            text("omniisle.editor.world_border"), state.isWorldBorderEnabled(),
            value -> state.setWorldBorderEnabled(value));
        add(controls, border, "omniisle.editor.world_border.tooltip");
        y += rowH + 4;

        OmniSlider margin = new OmniSlider(rowX, y, rowW, rowH,
            text("omniisle.advanced.border_margin"), (state.worldBorderMargin() - 500.0) / 3500.0,
            1.0 / 3500.0, (1000.0 - 500.0) / 3500.0, null);
        margin.setValueText(text("omniisle.editor.border_margin", state.worldBorderMargin()));
        margin.setOnChange(value -> {
            state.setWorldBorderMargin((int) Math.round(500 + value * 3500));
            margin.setValueText(text("omniisle.editor.border_margin", state.worldBorderMargin()));
        });
        add(controls, margin, "omniisle.editor.border_margin.tooltip");

        controls.setActive(content);

        addRenderableWidget(new OmniButton(innerX + innerW - Math.min(120, innerW), footerY,
            Math.min(120, innerW), 22, text("gui.done"), OmniButton.Style.PRIMARY, this::onClose));
    }

    private void add(OmniControlsPanel controls, AbstractWidget widget, String tipKey) {
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
        g.fill(0, 0, width, height, OmniTheme.OVERLAY);
        OmniUi.panel(g, panelX, panelY, panelW, panelH, OmniTheme.PANEL, OmniTheme.BORDER);
        OmniUi.textFit(g, font, titleText, panelX + pad, panelY + 9,
            panelW - pad * 2, OmniTheme.TEXT);
        OmniUi.divider(g, panelX + pad, panelY + 21, panelW - pad * 2);
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
