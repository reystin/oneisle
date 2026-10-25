package com.hack337.omniisle.client;

import com.hack337.omniisle.continent.BiomeShare;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Compact companion page used when the GUI scale leaves no room for the full checklist. */
final class BiomeSelectionScreen extends Screen {
    private static final int LAND_ROW_H = 30;
    private static final int OTHER_ROW_H = 16;

    private final OmniIsleEditorScreen owner;
    private final IslandDraft state;
    private boolean showStructures;
    private BiomePreview.Category categoryFilter = null;
    private int scroll;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private BiomePreview.Type weightEditingType;
    private String weightEditBuffer = "";

    BiomeSelectionScreen(OmniIsleEditorScreen owner, IslandDraft state, boolean showStructures) {
        super(Component.translatable("omniisle.editor.biomes"));
        this.owner = owner;
        this.state = state;
        this.showStructures = showStructures;
    }

    @Override
    protected void init() {
        listWidth = Math.min(420, width - 20);
        listHeight = Math.max(90, height - 92);
        listX = (width - listWidth) / 2;
        listY = 48;
        addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.reroll"), button -> {
            state.rerollBiomes();
            owner.refreshPreview();
        }).bounds(width / 2 - 75, 25, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(width / 2 - 75, height - 25, 150, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        owner.tickShares();
        context.fill(0, 0, width, height, 0x30000000);
        panel(context, listX - 5, listY - 5, listWidth + 10, listHeight + 10, 0xFF202B2C);
        context.centeredText(font, title, width / 2, 9, 0xFFF4E8C1);
        renderList(context, mouseX, mouseY);
    }

    private List<BiomePreview.Type> getFilteredBiomes() {
        if (categoryFilter == null) return IslandDraft.OVERWORLD_BIOMES;
        return IslandDraft.OVERWORLD_BIOMES.stream().filter(type -> type.category == categoryFilter).toList();
    }

    private BiomePreview.Category nextCategory(BiomePreview.Category current) {
        if (current == null) return BiomePreview.Category.LAND;
        return switch (current) {
            case LAND -> BiomePreview.Category.SHORE;
            case SHORE -> BiomePreview.Category.OCEAN;
            case OCEAN -> BiomePreview.Category.RIVER;
            case RIVER -> BiomePreview.Category.CAVE;
            case CAVE -> null;
        };
    }

    private Component categoryName(BiomePreview.Category cat) {
        if (cat == null) return Component.translatable("omniisle.category.all");
        return switch (cat) {
            case LAND -> Component.translatable("omniisle.category.land");
            case SHORE -> Component.translatable("omniisle.category.shore");
            case OCEAN -> Component.translatable("omniisle.category.ocean");
            case RIVER -> Component.translatable("omniisle.category.river");
            case CAVE -> Component.translatable("omniisle.category.cave");
        };
    }

    private int viewTop() { return listY + (showStructures ? 40 : 60); }

    private int viewHeight() { return Math.max(1, listY + listHeight - viewTop() - 4); }

    private int rowHeight(int index) {
        if (showStructures) return OTHER_ROW_H;
        return getFilteredBiomes().get(index).isLand() ? LAND_ROW_H : OTHER_ROW_H;
    }

    private int contentHeight() {
        if (showStructures) return IslandDraft.STRUCTURE_OPTIONS.size() * OTHER_ROW_H;
        int total = 0;
        for (BiomePreview.Type type : getFilteredBiomes()) total += type.isLand() ? LAND_ROW_H : OTHER_ROW_H;
        return total;
    }

    private int maxScroll() { return Math.max(0, contentHeight() - viewHeight()); }

    private int rowY(int index) {
        int y = viewTop() - scroll;
        for (int i = 0; i < index; i++) y += rowHeight(i);
        return y;
    }

    private int indexAt(double mouseY) {
        int local = (int)Math.floor(mouseY) - viewTop() + scroll;
        if (local < 0) return -1;
        int count = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : getFilteredBiomes().size();
        int y = 0;
        for (int index = 0; index < count; index++) {
            int h = rowHeight(index);
            if (local < y + h) return index;
            y += h;
        }
        return -1;
    }

    private void renderList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0xF0101717);
        context.outline(listX, listY, listWidth, listHeight, 0xFF819086);

        int middle = listX + listWidth / 2;
        context.fill(showStructures ? middle : listX + 1, listY + 1,
            showStructures ? listX + listWidth - 1 : middle, listY + 19, 0xFF435A47);
        context.centeredText(font, Component.translatable("omniisle.editor.biomes"),
            listX + listWidth / 4, listY + 6, showStructures ? 0xFFAAAAAA : 0xFFFFFFFF);
        context.centeredText(font, Component.translatable("omniisle.editor.structures"),
            listX + listWidth * 3 / 4, listY + 6, showStructures ? 0xFFFFFFFF : 0xFFAAAAAA);

        int subY = listY + 20;
        context.fill(listX + 1, subY, listX + listWidth - 1, subY + 18, 0xFF1C2422);
        context.horizontalLine(listX, listX + listWidth - 1, subY + 18, 0xFF35443E);

        int filterW = Math.max(60, listWidth - 156);
        if (!showStructures) {
            boolean catHover = hover(mouseX, mouseY, listX + 2, subY + 1, filterW, 16);
            context.fill(listX + 2, subY + 1, listX + 2 + filterW, subY + 17, catHover ? 0xFF3D5048 : 0xFF2A3632);
            context.outline(listX + 2, subY + 1, filterW, 16, 0xFF586C62);
            Component catLabel = Component.literal(categoryName(categoryFilter).getString() + " ▼");
            context.centeredText(font, shortened(catLabel, filterW - 4), listX + 2 + filterW / 2, subY + 5, 0xFFD8E2DC);
        } else {
            context.centeredText(font, Component.translatable("omniisle.category.all"),
                listX + 2 + filterW / 2, subY + 5, 0xFF889890);
        }

        int btnX1 = listX + 6 + filterW;
        int btnX2 = btnX1 + 74;
        button(context, mouseX, mouseY, btnX1, subY + 1, 70, 16,
            Component.translatable("omniisle.editor.select_all"), 0xFF26402C, 0xFF4A7D55, 0xFFE0FFE0);
        button(context, mouseX, mouseY, btnX2, subY + 1, 70, 16,
            Component.translatable("omniisle.editor.deselect_all"), 0xFF402626, 0xFF7D4A4A, 0xFFFFE0E0);

        if (!showStructures) {
            int actionsY = listY + 39;
            int gap = 4;
            int actionW = (listWidth - 10) / 2;
            int resetX = listX + 2;
            int offX = resetX + actionW + gap;
            button(context, mouseX, mouseY, resetX, actionsY + 1, actionW, 16,
                Component.translatable("omniisle.editor.weights.reset_all"), 0xFF2A3C46, 0xFF4A6D7D, 0xFFDDEEFF);
            button(context, mouseX, mouseY, offX, actionsY + 1, actionW, 16,
                Component.translatable("omniisle.editor.weights.disable_selected"), 0xFF463A26, 0xFF7D6A4A, 0xFFFFE8C4);
            if (hover(mouseX, mouseY, resetX, actionsY + 1, actionW, 16)) {
                context.setTooltipForNextFrame(font,
                    Component.translatable("omniisle.editor.weights.reset_all.tooltip"), mouseX, mouseY);
            }
            if (hover(mouseX, mouseY, offX, actionsY + 1, actionW, 16)) {
                context.setTooltipForNextFrame(font,
                    Component.translatable("omniisle.editor.weights.disable.tooltip"), mouseX, mouseY);
            }
            context.horizontalLine(listX, listX + listWidth - 1, listY + 58, 0xFF35443E);
        }

        int top = viewTop();
        int viewH = viewHeight();
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        context.enableScissor(listX + 1, top, listX + listWidth - 1, top + viewH);
        int y = top - scroll;
        int count = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : getFilteredBiomes().size();
        for (int index = 0; index < count; index++) {
            int h = rowHeight(index);
            if (y + h >= top && y <= top + viewH) {
                if (showStructures) drawStructureRow(context, index, y, mouseX, mouseY);
                else drawBiomeRow(context, getFilteredBiomes().get(index), y, mouseX, mouseY);
            }
            y += h;
        }
        context.disableScissor();

        if (maxScroll() > 0) {
            int trackH = viewH;
            int thumbH = Math.max(12, trackH * viewH / Math.max(1, contentHeight()));
            int thumbY = top + (trackH - thumbH) * scroll / maxScroll();
            context.fill(listX + listWidth - 4, top, listX + listWidth - 2, top + trackH, 0xFF303840);
            context.fill(listX + listWidth - 4, thumbY, listX + listWidth - 2, thumbY + thumbH, 0xFFB0B8C0);
        }
    }

    private void drawStructureRow(GuiGraphicsExtractor context, int index, int y, int mouseX, int mouseY) {
        IslandDraft.StructureOption option = IslandDraft.STRUCTURE_OPTIONS.get(index);
        boolean checked = state.isStructureEnabled(option);
        if (hover(mouseX, mouseY, listX + 2, y, listWidth - 4, OTHER_ROW_H)) {
            context.fill(listX + 2, y, listX + listWidth - 2, y + OTHER_ROW_H, 0x6652684F);
        }
        colorCircle(context, listX + 8, y + 8, 0xFFE1B866);
        int boxX = listX + 17;
        context.outline(boxX, y + 4, 9, 9, 0xFFD0D0D0);
        if (checked) context.fill(boxX + 2, y + 6, boxX + 7, y + 11, 0xFF55FF55);
        context.text(font, shortened(Component.translatable(option.translationKey), listWidth - 40),
            listX + 30, y + 4, checked ? 0xFFFFFFFF : 0xFF777777, false);
    }

    private void drawBiomeRow(GuiGraphicsExtractor context, BiomePreview.Type type, int y,
                              int mouseX, int mouseY) {
        boolean land = type.isLand();
        int rowH = land ? LAND_ROW_H : OTHER_ROW_H;
        if (hover(mouseX, mouseY, listX + 2, y, listWidth - 4, rowH)) {
            context.fill(listX + 2, y, listX + listWidth - 2, y + rowH, 0x6652684F);
        }
        boolean enabled = state.isBiomeEnabled(type);
        boolean terrain = land && BiomeShare.isTerrainDriven(type.family);
        colorCircle(context, listX + 8, y + 8, type.color);
        int boxX = listX + 17;
        context.outline(boxX, y + 4, 9, 9, 0xFFD0D0D0);
        if (enabled) context.fill(boxX + 2, y + 6, boxX + 7, y + 11, 0xFF55FF55);
        Component label = Component.translatable(type.translationKey());
        context.text(font, shortened(label, listWidth - 40), listX + 30, y + 4,
            enabled ? 0xFFFFFFFF : 0xFF777777, false);

        if (!land) {
            if (hover(mouseX, mouseY, listX + 2, y, listWidth - 4, rowH)) {
                context.setTooltipForNextFrame(font,
                    Component.translatable("omniisle.editor.weights.climate_only"), mouseX, mouseY);
            }
            return;
        }
        if (terrain) {
            context.text(font, shortened(Component.translatable("omniisle.editor.weights.terrain"), listWidth - 40),
                listX + 30, y + 17, 0xFF909A94, false);
            if (hover(mouseX, mouseY, listX + 2, y, listWidth - 4, rowH)) {
                context.setTooltipForNextFrame(font,
                    Component.translatable("omniisle.editor.weights.terrain.tooltip"), mouseX, mouseY);
            }
            return;
        }

        int controlsY = y + 15;
        int minusX = listX + 30;
        int fieldX = minusX + 16;
        int fieldW = 44;
        int plusX = fieldX + fieldW + 2;
        int resetX = listX + listWidth - 30;
        boolean editing = weightEditingType == type;
        int weight = enabled ? state.biomeWeight(type) : 0;
        boolean hoverMinus = hover(mouseX, mouseY, minusX, controlsY, 14, 13);
        boolean hoverField = hover(mouseX, mouseY, fieldX, controlsY, fieldW, 13);
        boolean hoverPlus = hover(mouseX, mouseY, plusX, controlsY, 14, 13);
        boolean hoverReset = hover(mouseX, mouseY, resetX, controlsY, 14, 13);

        context.fill(minusX, controlsY, minusX + 14, controlsY + 13, hoverMinus ? 0xFF4A5650 : 0xFF2C3531);
        context.outline(minusX, controlsY, 14, 13, 0xFF566158);
        context.centeredText(font, "-", minusX + 7, controlsY + 3, 0xFFDDDDDD);
        context.fill(plusX, controlsY, plusX + 14, controlsY + 13, hoverPlus ? 0xFF4A5650 : 0xFF2C3531);
        context.outline(plusX, controlsY, 14, 13, 0xFF566158);
        context.centeredText(font, "+", plusX + 7, controlsY + 3, 0xFFDDDDDD);
        context.fill(fieldX, controlsY, fieldX + fieldW, controlsY + 13,
            editing ? 0xFF101A18 : hoverField ? 0xFF37423D : 0xFF202826);
        context.outline(fieldX, controlsY, fieldW, 13, editing ? 0xFF8FD0A8 : 0xFF566158);
        String value = editing
            ? weightEditBuffer + (((System.currentTimeMillis() / 500L) & 1L) == 0L ? "_" : "")
            : weight + "%";
        context.centeredText(font, value, fieldX + fieldW / 2, controlsY + 3,
            enabled ? 0xFFEAF4EC : 0xFF8A948E);
        context.fill(resetX, controlsY, resetX + 14, controlsY + 13, hoverReset ? 0xFF4A5650 : 0xFF2C3531);
        context.outline(resetX, controlsY, 14, 13, 0xFF566158);
        context.centeredText(font, "↺", resetX + 7, controlsY + 3, 0xFFDDDDDD);

        String actual = "—";
        int actualColor = 0xFF808A84;
        if (enabled && state.hasNonNeutralWeights()) {
            double achieved = state.achievedShare(type);
            double target = state.targetShare(type);
            if (achieved >= 0.0 && target >= 0.0) {
                actual = String.format(java.util.Locale.ROOT, "%.1f%%", achieved);
                double diff = Math.abs(achieved - target);
                actualColor = diff <= 3.0 ? 0xFF6BD97A : diff <= 6.0 ? 0xFFE0B84A : 0xFFE26A5E;
            }
        }
        context.text(font, actual, resetX - 6 - font.width(actual), controlsY + 3, actualColor, false);

        if (hoverMinus || hoverPlus || hoverField) {
            context.setTooltipForNextFrame(font,
                Component.translatable("omniisle.editor.weights.field.tooltip"), mouseX, mouseY);
        } else if (hoverReset) {
            context.setTooltipForNextFrame(font,
                Component.translatable("omniisle.editor.weights.row_reset.tooltip"), mouseX, mouseY);
        } else if (hover(mouseX, mouseY, listX + 2, y, listWidth - 4, rowH)
            && state.targetShare(type) >= 0.0 && state.achievedShare(type) >= 0.0) {
            context.setTooltipForNextFrame(font, Component.translatable(
                "omniisle.editor.weights.actual.tooltip",
                String.format(java.util.Locale.ROOT, "%.1f%%", state.achievedShare(type)),
                String.format(java.util.Locale.ROOT, "%.1f%%", state.targetShare(type))), mouseX, mouseY);
        }
    }

    private void button(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int w, int h,
                        Component label, int fill, int border, int textColor) {
        boolean hovered = hover(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, hovered ? brighten(fill) : fill);
        context.outline(x, y, w, h, border);
        context.centeredText(font, shortened(label, w - 4), x + w / 2, y + (h - 8) / 2, textColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && insideList(mouseX, mouseY)) {
            commitWeightEdit();
            if (mouseY < listY + 20) {
                showStructures = mouseX >= listX + listWidth / 2;
                scroll = 0;
                return true;
            }
            if (mouseY < listY + 39) {
                int filterW = Math.max(60, listWidth - 156);
                if (!showStructures && mouseX >= listX + 2 && mouseX < listX + 2 + filterW) {
                    categoryFilter = nextCategory(categoryFilter);
                    scroll = 0;
                    return true;
                }
                int btnX1 = listX + 6 + filterW;
                if (mouseX >= btnX1 && mouseX < btnX1 + 70) {
                    state.pushUndo();
                    if (showStructures) state.selectAllStructures();
                    else state.selectAllBiomes(categoryFilter);
                    owner.refreshPreview();
                    return true;
                }
                int btnX2 = btnX1 + 74;
                if (mouseX >= btnX2 && mouseX < btnX2 + 70) {
                    state.pushUndo();
                    if (showStructures) state.deselectAllStructures();
                    else state.deselectAllBiomes(categoryFilter);
                    owner.refreshPreview();
                    return true;
                }
                return true;
            }
            if (!showStructures && mouseY < listY + 60) {
                int actionW = (listWidth - 10) / 2;
                int resetX = listX + 2;
                if (hover(mouseX, mouseY, resetX, listY + 40, actionW, 16)) {
                    owner.resetWeightsFromUi(categoryFilter);
                    return true;
                }
                int offX = resetX + actionW + 4;
                if (hover(mouseX, mouseY, offX, listY + 40, actionW, 16)) {
                    owner.disableSelectedWeightsFromUi(categoryFilter);
                    return true;
                }
                return true;
            }
            if (mouseX >= listX + listWidth - 8) {
                if (maxScroll() > 0) {
                    double progress = Math.max(0.0, Math.min(1.0,
                        (mouseY - viewTop()) / (double) viewHeight()));
                    scroll = (int)Math.round(progress * maxScroll());
                }
                return true;
            }
            int index = indexAt(mouseY);
            if (showStructures) {
                if (index >= 0 && index < IslandDraft.STRUCTURE_OPTIONS.size()) {
                    state.toggleStructure(IslandDraft.STRUCTURE_OPTIONS.get(index));
                    owner.refreshPreview();
                }
                return true;
            }
            List<BiomePreview.Type> list = getFilteredBiomes();
            if (index < 0 || index >= list.size()) return true;
            BiomePreview.Type type = list.get(index);
            if (!type.isLand() || BiomeShare.isTerrainDriven(type.family)) {
                owner.toggleBiomeFromUi(type);
                return true;
            }
            if (doubleClick) {
                owner.resetBiomeWeightFromUi(type);
                return true;
            }
            int rowY = rowY(index);
            int controlsY = rowY + 15;
            int minusX = listX + 30;
            int fieldX = minusX + 16;
            int fieldW = 44;
            int plusX = fieldX + fieldW + 2;
            int resetX = listX + listWidth - 30;
            boolean enabled = state.isBiomeEnabled(type);
            int weight = enabled ? state.biomeWeight(type) : 0;
            if (hover(mouseX, mouseY, minusX, controlsY, 14, 13)) {
                owner.changeWeightFromUi(type, weight <= 5 ? 0 : weight - BiomeShare.WEIGHT_STEP);
            } else if (hover(mouseX, mouseY, plusX, controlsY, 14, 13)) {
                owner.changeWeightFromUi(type, weight <= 0 ? BiomeShare.DEFAULT_WEIGHT
                    : weight + BiomeShare.WEIGHT_STEP);
            } else if (hover(mouseX, mouseY, fieldX, controlsY, fieldW, 13)) {
                weightEditingType = type;
                weightEditBuffer = String.valueOf(weight);
            } else if (hover(mouseX, mouseY, resetX, controlsY, 14, 13)) {
                owner.resetBiomeWeightFromUi(type);
            } else {
                owner.toggleBiomeFromUi(type);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (weightEditingType != null) {
            int key = event.key();
            if (key == InputConstants.KEY_ESCAPE) {
                weightEditingType = null;
                weightEditBuffer = "";
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                commitWeightEdit();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!weightEditBuffer.isEmpty()) {
                    weightEditBuffer = weightEditBuffer.substring(0, weightEditBuffer.length() - 1);
                }
                return true;
            }
            int digit = -1;
            if (key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9) digit = key - InputConstants.KEY_0;
            else if (key >= 320 && key <= 329) digit = key - 320;
            if (digit >= 0 && weightEditBuffer.length() < 3) weightEditBuffer += digit;
            return true;
        }
        return super.keyPressed(event);
    }

    private void commitWeightEdit() {
        BiomePreview.Type type = weightEditingType;
        if (type == null) return;
        String buffer = weightEditBuffer;
        weightEditingType = null;
        weightEditBuffer = "";
        if (buffer.isEmpty()) return;
        owner.applyWeightEditFromUi(type, buffer);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insideList(mouseX, mouseY)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)Math.round(Math.signum(verticalAmount) * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean insideList(double x, double y) {
        return x >= listX && x < listX + listWidth && y >= listY && y < listY + listHeight;
    }

    @Override
    public void onClose() {
        commitWeightEdit();
        owner.refreshPreview();
        ClientScreenNavigator.open(minecraft, owner);
    }

    private boolean hover(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >>> 16) & 0xFF) + 18);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + 18);
        int b = Math.min(255, (color & 0xFF) + 18);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void colorCircle(GuiGraphicsExtractor context, int x, int y, int color) {
        context.fill(x - 2, y - 4, x + 3, y + 5, color);
        context.fill(x - 4, y - 2, x + 5, y + 3, color);
    }

    private String shortened(Component label, int maxWidth) {
        String value = label.getString();
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static void panel(GuiGraphicsExtractor context, int x, int y, int w, int h, int interior) {
        context.fill(x, y, x + w, y + h, 0xFF090B0A);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF788078);
        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF343A35);
        context.fill(x + 4, y + 4, x + w - 4, y + h - 4, interior);
    }
}
