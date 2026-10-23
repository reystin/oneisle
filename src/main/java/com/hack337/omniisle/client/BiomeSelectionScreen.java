package com.hack337.omniisle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact companion page used when the GUI scale leaves no room for the full checklist. */
final class BiomeSelectionScreen extends Screen {
    private final OmniIsleEditorScreen owner;
    private final IslandDraft state;
    private boolean showStructures;
    private int scroll;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;

    BiomeSelectionScreen(OmniIsleEditorScreen owner, IslandDraft state) {
        super(Component.translatable("omniisle.editor.biomes"));
        this.owner = owner;
        this.state = state;
    }

    @Override
    protected void init() {
        listWidth = Math.min(300, width - 20);
        listHeight = Math.max(90, height - 92);
        listX = (width - listWidth) / 2;
        listY = 48;
        addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.reroll"), button -> {
            state.rerollBiomes();
            owner.refreshPreview();
        }).bounds(width / 2 - 75, 27, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(width / 2 - 75, height - 27, 150, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x30000000);
        panel(context, listX - 5, listY - 5, listWidth + 10, listHeight + 10, 0xFF202B2C);
        context.centeredText(font, title, width / 2, 10, 0xFFF4E8C1);
        renderList(context, mouseX, mouseY);
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

        int rowHeight = 15;
        int firstY = listY + 20;
        int visibleRows = Math.max(1, (listHeight - 22) / rowHeight);
        int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size()
            : IslandDraft.OVERWORLD_BIOMES.size();
        int maxScroll = Math.max(0, itemCount - visibleRows);
        scroll = Math.min(scroll, maxScroll);
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = visible + scroll;
            if (index >= itemCount) break;
            int y = firstY + visible * rowHeight;
            boolean hovered = mouseX >= listX + 2 && mouseX < listX + listWidth - 2
                && mouseY >= y && mouseY < y + rowHeight;
            if (hovered) context.fill(listX + 2, y, listX + listWidth - 2, y + rowHeight, 0x6652684F);
            boolean checked;
            Component label;
            int marker;
            if (showStructures) {
                IslandDraft.StructureOption option = IslandDraft.STRUCTURE_OPTIONS.get(index);
                checked = state.isStructureEnabled(option);
                label = Component.translatable(option.translationKey);
                marker = 0xFFE1B866;
            } else {
                BiomePreview.Type biome = IslandDraft.OVERWORLD_BIOMES.get(index);
                checked = state.isBiomeEnabled(biome);
                label = Component.translatable(biome.translationKey());
                marker = biome.color;
            }
            colorCircle(context, listX + 8, y + 7, marker);
            int boxX = listX + 17;
            context.outline(boxX, y + 3, 9, 9, 0xFFD0D0D0);
            if (checked) context.fill(boxX + 2, y + 5, boxX + 7, y + 10, 0xFF55FF55);
            context.text(font, shortened(label, listWidth - 40), listX + 30, y + 3,
                checked ? 0xFFFFFFFF : 0xFF777777, false);
        }
        if (maxScroll > 0) {
            int trackY = listY + 20;
            int trackHeight = listHeight - 23;
            int thumbHeight = Math.max(12, trackHeight * visibleRows / itemCount);
            int thumbY = trackY + (trackHeight - thumbHeight) * scroll / maxScroll;
            context.fill(listX + listWidth - 4, trackY, listX + listWidth - 2, trackY + trackHeight, 0xFF303840);
            context.fill(listX + listWidth - 4, thumbY, listX + listWidth - 2, thumbY + thumbHeight, 0xFFB0B8C0);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && insideList(mouseX, mouseY)) {
            if (mouseY < listY + 20) {
                showStructures = mouseX >= listX + listWidth / 2;
                scroll = 0;
                return true;
            }
            if (mouseX >= listX + listWidth - 8) {
                int visibleRows = Math.max(1, (listHeight - 22) / 15);
                int count = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : IslandDraft.OVERWORLD_BIOMES.size();
                int maxScroll = Math.max(0, count - visibleRows);
                if (maxScroll > 0) {
                    double progress = Math.max(0.0, Math.min(1.0, (mouseY - (listY + 20)) / (double)(listHeight - 23)));
                    scroll = (int)Math.round(progress * maxScroll);
                }
                return true;
            }
            int index = scroll + ((int)mouseY - (listY + 20)) / 15;
            int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size()
                : IslandDraft.OVERWORLD_BIOMES.size();
            if (index >= 0 && index < itemCount) {
                if (showStructures) state.toggleStructure(IslandDraft.STRUCTURE_OPTIONS.get(index));
                else state.toggleBiome(IslandDraft.OVERWORLD_BIOMES.get(index));
                owner.refreshPreview();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insideList(mouseX, mouseY)) {
            int visibleRows = Math.max(1, (listHeight - 22) / 15);
            int count = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size()
                : IslandDraft.OVERWORLD_BIOMES.size();
            scroll = Math.max(0, Math.min(Math.max(0, count - visibleRows), scroll - (int)Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean insideList(double x, double y) {
        return x >= listX && x < listX + listWidth && y >= listY && y < listY + listHeight;
    }

    @Override
    public void onClose() {
        owner.refreshPreview();
        ClientScreenNavigator.open(minecraft, owner);
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
