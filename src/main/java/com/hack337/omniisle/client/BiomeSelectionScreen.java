package com.hack337.omniisle.client;

import com.hack337.omniisle.client.ui.OmniBiomeList;
import com.hack337.omniisle.client.ui.OmniButton;
import com.hack337.omniisle.client.ui.OmniDropdown;
import com.hack337.omniisle.client.ui.OmniMetrics;
import com.hack337.omniisle.client.ui.OmniSegmented;
import com.hack337.omniisle.client.ui.OmniTheme;
import com.hack337.omniisle.client.ui.OmniUi;
import com.hack337.omniisle.continent.BiomeShare;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Full biome/structure list used when the editor has no room for the right panel. */
final class BiomeSelectionScreen extends Screen {
    private final OmniIsleEditorScreen owner;
    private final IslandDraft state;
    private boolean showStructures;
    private BiomePreview.Category categoryFilter;

    private final List<BiomePreview.Type> filtered = new ArrayList<>();
    private final List<BiomePreview.Type> rowTypes = new ArrayList<>();
    private final List<OmniBiomeList.Row> rows = new ArrayList<>();
    private final Map<String, Component> rowTips = new HashMap<>();

    private OmniBiomeList list;
    private OmniSegmented modeSeg;
    private OmniButton rerollButton;
    private OmniButton categoryButton;
    private OmniButton selectAllButton;
    private OmniButton deselectAllButton;
    private OmniButton resetButton;
    private OmniButton disableButton;
    private OmniButton backButton;
    private OmniDropdown dropdown;

    private BiomePreview.Type weightEditingType;
    private String weightEditBuffer = "";
    private int lastSharesVersion;

    private String[] modeLabels;
    private String[] categoryItems;
    private String titleText = "";
    private String categoryLabelText = "";
    private Component rerollTip;
    private Component categoryTip;
    private Component selectTip;
    private Component deselectTip;
    private Component resetTip;
    private Component disableTip;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int pad;
    private int rowH;
    private int gap;
    private int contentX;
    private int contentW;

    BiomeSelectionScreen(OmniIsleEditorScreen owner, IslandDraft state, boolean showStructures) {
        super(Component.translatable("omniisle.editor.biomes"));
        this.owner = owner;
        this.state = state;
        this.showStructures = showStructures;
    }

    @Override
    protected void init() {
        clearWidgets();
        dropdown = null;
        OmniMetrics.tick(font);
        modeLabels = new String[]{
            text("omniisle.ui.list.biomes"), text("omniisle.ui.list.structures")
        };
        categoryItems = new String[]{
            text("omniisle.category.all"), text("omniisle.category.land"),
            text("omniisle.category.shore"), text("omniisle.category.ocean"),
            text("omniisle.category.river"), text("omniisle.category.cave")
        };
        categoryLabelText = text("omniisle.ui.category", categoryItems[categoryIndex()]);
        titleText = text(showStructures ? "omniisle.editor.structures" : "omniisle.editor.biomes");
        rerollTip = Component.translatable("omniisle.editor.reroll.tooltip");
        categoryTip = Component.translatable("omniisle.ui.category.tooltip");
        selectTip = Component.translatable("omniisle.ui.select_all.tooltip");
        deselectTip = Component.translatable("omniisle.ui.deselect_all.tooltip");
        resetTip = Component.translatable("omniisle.editor.weights.reset_all.tooltip");
        disableTip = Component.translatable("omniisle.editor.weights.disable.tooltip");

        panelW = Math.min(460, width - 16);
        panelH = Math.min(560, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        pad = panelW < 260 ? 4 : 8;
        rowH = panelH < 200 ? 16 : 22;
        gap = panelH < 200 ? 2 : 4;
        contentX = panelX + pad;
        contentW = panelW - pad * 2;

        list = new OmniBiomeList(0, 0, 10, 10, 18);
        list.setListener(new OmniBiomeList.Listener() {
            @Override public void onToggle(int index, boolean enabled) {
                if (showStructures) {
                    IslandDraft.StructureOption option = structureAt(index);
                    if (option != null) state.toggleStructure(option);
                } else {
                    BiomePreview.Type type = biomeAt(index);
                    if (type != null) owner.toggleBiomeFromUi(type);
                }
                owner.refreshPreview();
                refreshRowValues();
            }
            @Override public void onWeight(int index, int delta) {
                BiomePreview.Type type = biomeAt(index);
                if (type == null || showStructures) return;
                int weight = state.isBiomeEnabled(type) ? state.biomeWeight(type) : 0;
                owner.changeWeightFromUi(type, delta < 0
                    ? (weight <= 5 ? 0 : weight - BiomeShare.WEIGHT_STEP)
                    : (weight <= 0 ? BiomeShare.DEFAULT_WEIGHT : weight + BiomeShare.WEIGHT_STEP));
                refreshRowValues();
            }
            @Override public void onWeightEdit(int index, int mouseX, int mouseY) {
                BiomePreview.Type type = biomeAt(index);
                if (type != null && !showStructures) beginWeightEdit(type);
            }
            @Override public void onActivate(int index) {
                if (showStructures) {
                    IslandDraft.StructureOption option = structureAt(index);
                    if (option != null) state.toggleStructure(option);
                    owner.refreshPreview();
                    refreshRowValues();
                    return;
                }
                BiomePreview.Type type = biomeAt(index);
                if (type == null) return;
                if (type.isLand() && !BiomeShare.isTerrainDriven(type.family)) {
                    owner.resetBiomeWeightFromUi(type);
                    refreshRowValues();
                } else {
                    owner.toggleBiomeFromUi(type);
                    owner.refreshPreview();
                    refreshRowValues();
                }
            }
        });
        addRenderableWidget(list);

        modeSeg = addRenderableWidget(new OmniSegmented(0, 0, 10, 10, modeLabels,
            showStructures ? 1 : 0, index -> setListMode(index == 1)));
        rerollButton = addRenderableWidget(new OmniButton(0, 0, 10, 10,
            text("omniisle.editor.reroll"), OmniButton.Style.SECONDARY, () -> {
                state.rerollBiomes();
                owner.refreshPreview();
                refreshRowValues();
            }));
        categoryButton = addRenderableWidget(new OmniButton(0, 0, 10, 10, categoryLabelText,
            OmniButton.Style.SECONDARY, this::openCategoryMenu));
        selectAllButton = addRenderableWidget(new OmniButton(0, 0, 10, 10,
            text("omniisle.editor.select_all"), OmniButton.Style.SECONDARY, () -> {
                state.pushUndo();
                if (showStructures) state.selectAllStructures();
                else state.selectAllBiomes(categoryFilter);
                owner.refreshPreview();
                refreshRowValues();
            }));
        deselectAllButton = addRenderableWidget(new OmniButton(0, 0, 10, 10,
            text("omniisle.editor.deselect_all"), OmniButton.Style.SECONDARY, () -> {
                state.pushUndo();
                if (showStructures) state.deselectAllStructures();
                else state.deselectAllBiomes(categoryFilter);
                owner.refreshPreview();
                refreshRowValues();
            }));
        resetButton = addRenderableWidget(new OmniButton(0, 0, 10, 10,
            text("omniisle.editor.weights.reset_all"), OmniButton.Style.SECONDARY, () -> {
                owner.resetWeightsFromUi(categoryFilter);
                refreshRowValues();
            }));
        disableButton = addRenderableWidget(new OmniButton(0, 0, 10, 10,
            text("omniisle.editor.weights.disable_selected"), OmniButton.Style.DANGER, () -> {
                owner.disableSelectedWeightsFromUi(categoryFilter);
                refreshRowValues();
            }));
        backButton = addRenderableWidget(new OmniButton(0, 0, 10, 10,
            text("gui.back"), OmniButton.Style.SECONDARY, this::onClose));

        layoutWidgets();
        rebuildRows(true);
        lastSharesVersion = owner.sharesVersion();
    }

    private void layoutWidgets() {
        categoryButton.visible = !showStructures;
        resetButton.visible = !showStructures;
        disableButton.visible = !showStructures;

        int y = panelY + pad + 12 + gap;
        int segW = Math.min(200, Math.max(80, contentW / 2));
        modeSeg.setX(contentX);
        modeSeg.setY(y);
        modeSeg.setWidth(segW);
        modeSeg.setHeight(rowH);
        int rerollW = Math.max(60, contentW - segW - gap);
        rerollButton.setX(contentX + contentW - rerollW);
        rerollButton.setY(y);
        rerollButton.setWidth(rerollW);
        rerollButton.setHeight(rowH);
        y += rowH + gap;

        categoryButton.setX(contentX);
        categoryButton.setY(y);
        categoryButton.setWidth(contentW);
        categoryButton.setHeight(rowH);
        if (!showStructures) y += rowH + gap;

        int half = (contentW - gap) / 2;
        selectAllButton.setX(contentX);
        selectAllButton.setY(y);
        selectAllButton.setWidth(half);
        selectAllButton.setHeight(rowH);
        deselectAllButton.setX(contentX + half + gap);
        deselectAllButton.setY(y);
        deselectAllButton.setWidth(contentW - half - gap);
        deselectAllButton.setHeight(rowH);
        y += rowH + gap;

        if (!showStructures) {
            resetButton.setX(contentX);
            resetButton.setY(y);
            resetButton.setWidth(half);
            resetButton.setHeight(rowH);
            disableButton.setX(contentX + half + gap);
            disableButton.setY(y);
            disableButton.setWidth(contentW - half - gap);
            disableButton.setHeight(rowH);
            y += rowH + gap;
        }

        int footerY = panelY + panelH - pad - rowH;
        int backW = Math.min(120, contentW);
        backButton.setX(contentX + contentW - backW);
        backButton.setY(footerY);
        backButton.setWidth(backW);
        backButton.setHeight(rowH);

        list.setX(contentX);
        list.setY(y);
        list.setWidth(contentW);
        list.setHeight(Math.max(18, footerY - gap - y));
    }

    private void setListMode(boolean structures) {
        if (showStructures == structures) return;
        showStructures = structures;
        commitWeightEdit();
        titleText = text(showStructures ? "omniisle.editor.structures" : "omniisle.editor.biomes");
        modeSeg.setSelected(structures ? 1 : 0);
        layoutWidgets();
        rebuildRows(true);
    }

    private void updateFiltered() {
        filtered.clear();
        for (BiomePreview.Type type : IslandDraft.OVERWORLD_BIOMES) {
            if (categoryFilter == null || type.category == categoryFilter) filtered.add(type);
        }
    }

    private void rebuildRows(boolean resetScroll) {
        rows.clear();
        rowTypes.clear();
        rowTips.clear();
        if (showStructures) {
            for (IslandDraft.StructureOption option : IslandDraft.STRUCTURE_OPTIONS) {
                OmniBiomeList.Row row = new OmniBiomeList.Row(option.name(), text(option.translationKey),
                    0xFFE1B866, state.isStructureEnabled(option));
                row.weighted = false;
                row.actual = "";
                row.status = OmniTheme.Status.NEUTRAL;
                rows.add(row);
            }
        } else {
            updateFiltered();
            for (BiomePreview.Type type : filtered) {
                rows.add(new OmniBiomeList.Row(type.name(), text(type.translationKey()),
                    type.color, state.isBiomeEnabled(type)));
                rowTypes.add(type);
            }
            refreshRowValues();
        }
        list.setRows(rows);
        if (resetScroll) list.scrollArea().setScroll(0);
    }

    private void refreshRowValues() {
        if (showStructures) {
            for (int i = 0; i < rows.size(); i++) {
                IslandDraft.StructureOption option = structureAt(i);
                if (option != null) rows.get(i).enabled = state.isStructureEnabled(option);
            }
            return;
        }
        for (int i = 0; i < rows.size() && i < rowTypes.size(); i++) {
            OmniBiomeList.Row row = rows.get(i);
            BiomePreview.Type type = rowTypes.get(i);
            row.enabled = state.isBiomeEnabled(type);
            row.weighted = type.isLand() && !BiomeShare.isTerrainDriven(type.family);
            row.weight = row.enabled ? state.biomeWeight(type) : 0;
            row.actual = owner.actualShareText(type, row.enabled);
            row.status = owner.shareStatus(type, row.enabled, row.weighted);
            row.editing = weightEditingType == type && !weightEditBuffer.isEmpty()
                ? weightEditBuffer : null;
            row.tooltip = rowTooltip(type, row);
            rowTips.put(row.tooltip, Component.literal(row.tooltip));
        }
    }

    private String rowTooltip(BiomePreview.Type type, OmniBiomeList.Row row) {
        if (!type.isLand()) return text("omniisle.editor.weights.climate_only");
        if (!row.weighted) return text("omniisle.editor.weights.terrain.tooltip");
        double achieved = state.achievedShare(type);
        double target = state.targetShare(type);
        if (achieved >= 0.0 && target >= 0.0) {
            return text("omniisle.editor.weights.actual.tooltip",
                percent(achieved), percent(target));
        }
        return text("omniisle.editor.weights.field.tooltip");
    }

    private BiomePreview.Type biomeAt(int index) {
        return index >= 0 && index < rowTypes.size() ? rowTypes.get(index) : null;
    }

    private IslandDraft.StructureOption structureAt(int index) {
        return index >= 0 && index < IslandDraft.STRUCTURE_OPTIONS.size()
            ? IslandDraft.STRUCTURE_OPTIONS.get(index) : null;
    }

    private int categoryIndex() {
        if (categoryFilter == null) return 0;
        return switch (categoryFilter) {
            case LAND -> 1;
            case SHORE -> 2;
            case OCEAN -> 3;
            case RIVER -> 4;
            case CAVE -> 5;
        };
    }

    private BiomePreview.Category categoryFor(int index) {
        return switch (index) {
            case 1 -> BiomePreview.Category.LAND;
            case 2 -> BiomePreview.Category.SHORE;
            case 3 -> BiomePreview.Category.OCEAN;
            case 4 -> BiomePreview.Category.RIVER;
            case 5 -> BiomePreview.Category.CAVE;
            default -> null;
        };
    }

    private void openCategoryMenu() {
        closeCategoryMenu();
        int dropdownW = Math.max(categoryButton.getWidth(), Math.min(220, contentW));
        int dropdownH = Math.min(9, categoryItems.length) * 20 + 8;
        int x = Math.min(categoryButton.getX(), panelX + panelW - pad - dropdownW);
        int y = categoryButton.getY() + categoryButton.getHeight() + 2;
        y = Math.min(y, height - dropdownH - 4);
        dropdown = new OmniDropdown(x, Math.max(4, y), dropdownW, categoryLabelText, categoryItems,
            categoryIndex(), index -> {
                closeCategoryMenu();
                applyCategory(index);
            }, this::closeCategoryMenu);
        addRenderableWidget(dropdown);
    }

    private void closeCategoryMenu() {
        if (dropdown != null) {
            removeWidget(dropdown);
            dropdown = null;
        }
    }

    private void applyCategory(int index) {
        categoryFilter = categoryFor(index);
        categoryLabelText = text("omniisle.ui.category", categoryItems[categoryIndex()]);
        categoryButton.setLabel(categoryLabelText);
        rebuildRows(true);
    }

    private void beginWeightEdit(BiomePreview.Type type) {
        if (type == null || !type.isLand() || BiomeShare.isTerrainDriven(type.family)) return;
        commitWeightEdit();
        weightEditingType = type;
        weightEditBuffer = String.valueOf(state.isBiomeEnabled(type) ? state.biomeWeight(type) : 0);
        refreshEditingRow();
    }

    private void commitWeightEdit() {
        BiomePreview.Type type = weightEditingType;
        String buffer = weightEditBuffer;
        if (type == null) return;
        weightEditingType = null;
        weightEditBuffer = "";
        refreshEditingRow();
        if (!buffer.isEmpty()) {
            owner.applyWeightEditFromUi(type, buffer);
            refreshRowValues();
        }
    }

    private void cancelWeightEdit() {
        weightEditingType = null;
        weightEditBuffer = "";
        refreshEditingRow();
    }

    private void refreshEditingRow() {
        for (OmniBiomeList.Row row : rows) {
            row.editing = weightEditingType != null && row.id.equals(weightEditingType.name())
                && !weightEditBuffer.isEmpty() ? weightEditBuffer : null;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        owner.tickShares();
        if (owner.sharesVersion() != lastSharesVersion) {
            lastSharesVersion = owner.sharesVersion();
            refreshRowValues();
        }
        g.fill(0, 0, width, height, OmniTheme.OVERLAY);
        OmniUi.panel(g, panelX, panelY, panelW, panelH, OmniTheme.PANEL, OmniTheme.BORDER);
        OmniUi.textFit(g, font, titleText, panelX + pad, panelY + pad + 1,
            panelW - pad * 2, OmniTheme.TEXT);
        OmniUi.divider(g, panelX + pad, panelY + pad + 12, panelW - pad * 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (dropdown != null) return;
        Component tip = null;
        if (rerollButton.isMouseOver(mouseX, mouseY)) tip = rerollTip;
        else if (categoryButton.visible && categoryButton.isMouseOver(mouseX, mouseY)) tip = categoryTip;
        else if (selectAllButton.isMouseOver(mouseX, mouseY)) tip = selectTip;
        else if (deselectAllButton.isMouseOver(mouseX, mouseY)) tip = deselectTip;
        else if (resetButton.visible && resetButton.isMouseOver(mouseX, mouseY)) tip = resetTip;
        else if (disableButton.visible && disableButton.isMouseOver(mouseX, mouseY)) tip = disableTip;
        if (tip == null && list.isMouseOver(mouseX, mouseY)) {
            String rowTip = list.tooltipAt(mouseX, mouseY);
            if (!rowTip.isEmpty()) tip = rowTips.get(rowTip);
        }
        if (tip != null) g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (dropdown != null) {
            if (dropdown.isMouseOver(event.x(), event.y())) dropdown.mouseClicked(event, doubleClick);
            else closeCategoryMenu();
            return true;
        }
        if (weightEditingType != null) commitWeightEdit();
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dropdown != null) {
            if (dropdown.isMouseOver(mouseX, mouseY)) {
                dropdown.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (weightEditingType != null) {
            int key = event.key();
            if (key == InputConstants.KEY_ESCAPE) {
                cancelWeightEdit();
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                commitWeightEdit();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!weightEditBuffer.isEmpty()) {
                    weightEditBuffer = weightEditBuffer.substring(0, weightEditBuffer.length() - 1);
                    refreshEditingRow();
                }
                return true;
            }
            int digit = -1;
            if (key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9) digit = key - InputConstants.KEY_0;
            else if (key >= 320 && key <= 329) digit = key - 320;
            if (digit >= 0 && weightEditBuffer.length() < 3) {
                weightEditBuffer += digit;
                refreshEditingRow();
            }
            return true;
        }
        if (dropdown != null) return dropdown.keyPressed(event);
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        commitWeightEdit();
        owner.refreshPreview();
        ClientScreenNavigator.open(minecraft, owner);
    }

    private static String percent(double percent) {
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
