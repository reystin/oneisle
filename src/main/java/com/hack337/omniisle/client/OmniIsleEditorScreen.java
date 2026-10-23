package com.hack337.omniisle.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.hack337.omniisle.mixin.client.CreateWorldBridge;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

public final class OmniIsleEditorScreen extends Screen {
    private enum Tool { LAND, ERASER }

    private final CreateWorldScreen parent;
    private final IslandDraft state;
    private final Identifier previewId;
    private DynamicTexture previewTexture;
    private boolean previewDirty = true;
    private long lastPreviewUpload;
    private Tool tool = Tool.LAND;
    private int brushRadius = 7;
    private int previewX;
    private int previewY;
    private int previewSize;
    private int controlsX;
    private int controlsY;
    private int controlsWidth = 230;
    private int controlsHeight = 132;
    private int biomeListX;
    private int biomeListY;
    private int biomeListWidth = 180;
    private int biomeListHeight;
    private boolean compactLayout;
    private int biomeScroll;
    private boolean showStructures;
    private int lastPaintX = -1;
    private int lastPaintZ = -1;
    private boolean saving;
    private Component status = Component.empty();
    private Button landButton;
    private Button eraserButton;
    private Button newShapeButton;
    private Button newBiomesButton;
    private Button doneButton;

    public OmniIsleEditorScreen(CreateWorldScreen parent) {
        super(Component.translatable("omniisle.editor.title"));
        this.parent = parent;
        this.state = EditorDraftStore.openDraft(parent);
        this.previewId = Identifier.fromNamespaceAndPath("omniisle", "editor/preview_" + Integer.toHexString(System.identityHashCode(this)));
    }

    @Override
    protected void init() {
        // At GUI scale 5 the logical window is often only about 512 pixels wide.
        // Keep the editor usable there by moving the long checklist to its own page.
        compactLayout = width < 620 || height < 310;
        previewSize = compactLayout
            ? Math.max(100, Math.min(160, Math.min(height - 104, width - controlsWidth - 36)))
            : Math.max(124, Math.min(286, Math.min(height - 134, width - 486)));
        int totalWidth = compactLayout
            ? previewSize + 14 + controlsWidth
            : previewSize + 18 + controlsWidth + 18 + biomeListWidth;
        previewX = Math.max(10, (width - totalWidth) / 2);
        previewY = compactLayout ? 34 : Math.max(42, (height - previewSize - 52) / 2);
        controlsX = previewX + previewSize + (compactLayout ? 14 : 18);
        controlsY = previewY;
        biomeListX = controlsX + controlsWidth + 18;
        biomeListY = previewY;
        biomeListHeight = previewSize;
        int y = previewY;

        CycleButton<IslandDraft.Preset> preset = CycleButton
            .<IslandDraft.Preset>builder(value -> Component.translatable(value.translationKey), state.preset())
            .withValues(java.util.List.of(IslandDraft.Preset.values()))
            .create(controlsX, y, controlsWidth, 20, Component.translatable("omniisle.editor.preset"), (button, value) -> {
                state.setPreset(value);
                invalidatePreview();
            });
        addRenderableWidget(preset);
        y += 24;

        addRenderableWidget(new IslandSizeSlider(controlsX, y, controlsWidth, 20));
        y += 28;

        landButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.land.short"), button -> selectTool(Tool.LAND))
            .bounds(controlsX, y, 113, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.land.tooltip"))).build());
        eraserButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.eraser.short"), button -> selectTool(Tool.ERASER))
            .bounds(controlsX + 117, y, 113, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.eraser.tooltip"))).build());
        updateToolButtons();
        y += 24;

        addRenderableWidget(new BrushSizeSlider(controlsX, y, controlsWidth, 20));
        y += 28;

        newShapeButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.new_shape.short"), button -> {
            state.rerollShape();
            invalidatePreview();
        }).bounds(controlsX, y, 113, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.new_shape.tooltip"))).build());
        newBiomesButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.new_biomes.short"), button -> {
            if (compactLayout) {
                ClientScreenNavigator.open(minecraft, new BiomeSelectionScreen(this, state));
            } else {
                state.rerollBiomes();
                invalidatePreview();
            }
        }).bounds(controlsX + 117, y, 113, 20).tooltip(Tooltip.create(Component.translatable(
            compactLayout ? "omniisle.editor.biomes.tooltip" : "omniisle.editor.new_biomes.tooltip"))).build());

        int bottomY = Math.min(height - 29, previewY + previewSize + 20);
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> commitDesign())
            .bounds(width / 2 - 154, bottomY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 4, bottomY, 150, 20).build());

        if (previewTexture == null) {
            previewTexture = createPreviewTexture();
            minecraft.getTextureManager().register(previewId, previewTexture);
        }
        invalidatePreview();
    }

    /** Bridges the constructor signature changed by Minecraft 1.21.5. */
    private static DynamicTexture createPreviewTexture() {
        try {
            var named = DynamicTexture.class.getDeclaredConstructor(
                String.class, int.class, int.class, boolean.class);
            named.setAccessible(true);
            return named.newInstance("OmniIsle biome preview",
                IslandDraft.RESOLUTION, IslandDraft.RESOLUTION, false);
        } catch (ReflectiveOperationException ignored) {
            try {
                var legacy = DynamicTexture.class.getDeclaredConstructor(
                    int.class, int.class, boolean.class);
                legacy.setAccessible(true);
                return legacy.newInstance(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION, false);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported NativeImageBackedTexture constructor", exception);
            }
        }
    }

    private void selectTool(Tool selected) {
        tool = selected;
        updateToolButtons();
    }

    private void updateToolButtons() {
        if (landButton == null || eraserButton == null) return;
        landButton.setMessage(Component.translatable("omniisle.editor.land.short")
            .withStyle(tool == Tool.LAND ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        eraserButton.setMessage(Component.translatable("omniisle.editor.eraser.short")
            .withStyle(tool == Tool.ERASER ? ChatFormatting.GREEN : ChatFormatting.WHITE));
    }

    private void invalidatePreview() { previewDirty = true; }

    private void refreshPreviewTexture() {
        long now = System.nanoTime();
        if (!previewDirty || now - lastPreviewUpload < 16_000_000L || previewTexture == null) return;
        NativeImage image = previewTexture.getPixels();
        int resolution = IslandDraft.RESOLUTION;
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                boolean land = state.isLand(x, z);
                double continentalness;
                if (land) {
                    continentalness = hasOppositeNearby(x, z, true, 2) ? 0.0 : 0.30;
                } else {
                    continentalness = hasOppositeNearby(x, z, false, 7) ? -0.32 : -0.68;
                }
                BiomePreview.Type preferred = BiomePreview.surface(
                    continentalness, state.worldX(x), state.worldZ(z), state.biomeSeed());
                int color = state.selectBiome(preferred, state.worldX(x), state.worldZ(z)).color;
                image.setPixel(x, z, color);
            }
        }
        previewTexture.upload();
        previewDirty = false;
        lastPreviewUpload = now;
    }

    private boolean hasOppositeNearby(int centerX, int centerZ, boolean currentLand, int radius) {
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(IslandDraft.RESOLUTION - 1, centerZ + radius); z += 2) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(IslandDraft.RESOLUTION - 1, centerX + radius); x += 2) {
                if (state.isLand(x, z) != currentLand) return true;
            }
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        refreshPreviewTexture();
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (!saving && isInsidePreview(mouseX, mouseY)) drawBrushCursor(context, mouseX, mouseY);
        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2, height - 11,
                saving ? 0xFFFFA000 : 0xFFFF5555);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x24000000);

        int titleWidth = Math.min(250, width - 24);
        int titleX = (width - titleWidth) / 2;
        drawPanel(context, titleX, 7, titleWidth, 22, 0xFF26312D);
        context.centeredText(font, title, width / 2, 14, 0xFFF4E8C1);

        drawPanel(context, previewX - 6, previewY - 6, previewSize + 12, previewSize + 12, 0xFF25312E);
        context.blit(RenderPipelines.GUI_TEXTURED, previewId, previewX, previewY, 0.0F, 0.0F,
            previewSize, previewSize, IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            IslandDraft.RESOLUTION, IslandDraft.RESOLUTION);
        context.outline(previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, 0xFFB6C1AA);

        drawPanel(context, controlsX - 6, controlsY - 6, controlsWidth + 12, controlsHeight + 12, 0xFF2A302B);
        if (!compactLayout) {
            drawPanel(context, biomeListX - 4, biomeListY - 4,
                biomeListWidth + 8, biomeListHeight + 8, 0xFF202B2C);
            drawChoiceList(context, mouseX, mouseY);
        }

        int bottomY = Math.min(height - 29, previewY + previewSize + 20);
        drawPanel(context, width / 2 - 162, bottomY - 5, 324, 30, 0xFF2A302B);
    }

    private static void drawPanel(GuiGraphicsExtractor context, int x, int y, int panelWidth,
                                           int panelHeight, int interior) {
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xFF090B0A);
        context.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, 0xFF788078);
        context.fill(x + 2, y + 2, x + panelWidth - 2, y + panelHeight - 2, 0xFF343A35);
        context.fill(x + 4, y + 4, x + panelWidth - 4, y + panelHeight - 4, interior);
        context.horizontalLine(x + 4, x + panelWidth - 5, y + 4, 0xFF566158);
        context.verticalLine(x + 4, y + 4, y + panelHeight - 5, 0xFF566158);
        context.horizontalLine(x + 4, x + panelWidth - 5, y + panelHeight - 5, 0xFF141916);
        context.verticalLine(x + panelWidth - 5, y + 4, y + panelHeight - 5, 0xFF141916);
    }

    private void drawBrushCursor(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int radius = Math.max(2, (int)Math.round(brushRadius * previewSize / (double)IslandDraft.RESOLUTION));
        int color = tool == Tool.LAND ? 0xD0FFFFFF : 0xD0FF6666;
        context.horizontalLine(mouseX - radius, mouseX + radius, mouseY - radius, color);
        context.horizontalLine(mouseX - radius, mouseX + radius, mouseY + radius, color);
        context.verticalLine(mouseX - radius, mouseY - radius, mouseY + radius, color);
        context.verticalLine(mouseX + radius, mouseY - radius, mouseY + radius, color);
    }

    private void drawChoiceList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.fill(biomeListX, biomeListY, biomeListX + biomeListWidth, biomeListY + biomeListHeight, 0xF0101717);
        context.outline(biomeListX, biomeListY, biomeListWidth, biomeListHeight, 0xFF819086);
        int middle = biomeListX + biomeListWidth / 2;
        context.fill(showStructures ? middle : biomeListX + 1, biomeListY + 1,
            showStructures ? biomeListX + biomeListWidth - 1 : middle, biomeListY + 19, 0xFF435A47);
        context.centeredText(font, Component.translatable("omniisle.editor.biomes"),
            biomeListX + biomeListWidth / 4, biomeListY + 6, showStructures ? 0xFFAAAAAA : 0xFFFFFFFF);
        context.centeredText(font, Component.translatable("omniisle.editor.structures"),
            biomeListX + biomeListWidth * 3 / 4, biomeListY + 6, showStructures ? 0xFFFFFFFF : 0xFFAAAAAA);
        int rowHeight = 15;
        int firstY = biomeListY + 20;
        int visibleRows = Math.max(1, (biomeListHeight - 22) / rowHeight);
        int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : IslandDraft.OVERWORLD_BIOMES.size();
        int maxScroll = Math.max(0, itemCount - visibleRows);
        biomeScroll = Math.min(biomeScroll, maxScroll);
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = visible + biomeScroll;
            if (index >= itemCount) break;
            int y = firstY + visible * rowHeight;
            boolean hovered = mouseX >= biomeListX + 2 && mouseX < biomeListX + biomeListWidth - 2
                && mouseY >= y && mouseY < y + rowHeight;
            if (hovered) context.fill(biomeListX + 2, y, biomeListX + biomeListWidth - 2, y + rowHeight, 0x6652684F);
            boolean checked;
            Component label;
            int markerColor;
            if (showStructures) {
                IslandDraft.StructureOption option = IslandDraft.STRUCTURE_OPTIONS.get(index);
                checked = state.isStructureEnabled(option);
                label = Component.translatable(option.translationKey);
                markerColor = 0xFFE1B866;
            } else {
                BiomePreview.Type biome = IslandDraft.OVERWORLD_BIOMES.get(index);
                checked = state.isBiomeEnabled(biome);
                label = Component.translatable(biome.translationKey());
                markerColor = biome.color;
            }
            drawColorCircle(context, biomeListX + 8, y + 7, markerColor);
            int boxX = biomeListX + 17;
            context.outline(boxX, y + 3, 9, 9, 0xFFD0D0D0);
            if (checked) {
                context.fill(boxX + 2, y + 5, boxX + 7, y + 10, 0xFF55FF55);
            }
            context.text(font, shortened(label, biomeListWidth - 40), biomeListX + 30, y + 3,
                checked ? 0xFFFFFFFF : 0xFF777777, false);
        }
        if (maxScroll > 0) {
            int trackY = biomeListY + 20;
            int trackH = biomeListHeight - 23;
            int thumbH = Math.max(12, trackH * visibleRows / itemCount);
            int thumbY = trackY + (trackH - thumbH) * biomeScroll / maxScroll;
            context.fill(biomeListX + biomeListWidth - 4, trackY,
                biomeListX + biomeListWidth - 2, trackY + trackH, 0xFF303840);
            context.fill(biomeListX + biomeListWidth - 4, thumbY,
                biomeListX + biomeListWidth - 2, thumbY + thumbH, 0xFFB0B8C0);
        }
    }

    private static void drawColorCircle(GuiGraphicsExtractor context, int centerX, int centerY, int color) {
        context.fill(centerX - 2, centerY - 4, centerX + 3, centerY + 5, color);
        context.fill(centerX - 4, centerY - 2, centerX + 5, centerY + 3, color);
    }

    private String shortened(Component label, int maxWidth) {
        String value = label.getString();
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!saving && button == InputConstants.MOUSE_BUTTON_LEFT && isInsideBiomeList(mouseX, mouseY)) {
            if (mouseY < biomeListY + 20) {
                showStructures = mouseX >= biomeListX + biomeListWidth / 2;
                biomeScroll = 0;
                return true;
            }
            if (mouseX >= biomeListX + biomeListWidth - 8) {
                int visibleRows = Math.max(1, (biomeListHeight - 22) / 15);
                int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : IslandDraft.OVERWORLD_BIOMES.size();
                int maxScroll = Math.max(0, itemCount - visibleRows);
                if (maxScroll > 0) {
                    double progress = Math.max(0.0, Math.min(1.0, (mouseY - (biomeListY + 20)) / (double)(biomeListHeight - 23)));
                    biomeScroll = (int)Math.round(progress * maxScroll);
                }
                return true;
            }
            int index = biomeScroll + ((int)mouseY - (biomeListY + 20)) / 15;
            int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : IslandDraft.OVERWORLD_BIOMES.size();
            if (index >= 0 && index < itemCount) {
                if (showStructures) {
                    state.toggleStructure(IslandDraft.STRUCTURE_OPTIONS.get(index));
                } else if (!state.toggleBiome(IslandDraft.OVERWORLD_BIOMES.get(index))) {
                    status = Component.translatable("omniisle.editor.error.one_biome");
                    return true;
                }
                status = Component.empty();
                invalidatePreview();
            }
            return true;
        }
        if (!saving && isInsidePreview(mouseX, mouseY) && (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT)) {
            lastPaintX = -1;
            lastPaintZ = -1;
            applyBrush(mouseX, mouseY, button == InputConstants.MOUSE_BUTTON_RIGHT ? Tool.ERASER : tool);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsideBiomeList(mouseX, mouseY)) {
            int visibleRows = Math.max(1, (biomeListHeight - 22) / 15);
            int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : IslandDraft.OVERWORLD_BIOMES.size();
            int maxScroll = Math.max(0, itemCount - visibleRows);
            biomeScroll = Math.max(0, Math.min(maxScroll, biomeScroll - (int)Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!saving && (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) && isInsidePreview(mouseX, mouseY)) {
            applyBrush(mouseX, mouseY, button == InputConstants.MOUSE_BUTTON_RIGHT ? Tool.ERASER : tool);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        lastPaintX = -1;
        lastPaintZ = -1;
        return super.mouseReleased(event);
    }

    private void applyBrush(double mouseX, double mouseY, Tool selectedTool) {
        int px = Math.max(0, Math.min(IslandDraft.RESOLUTION - 1,
            (int)((mouseX - previewX) * IslandDraft.RESOLUTION / previewSize)));
        int pz = Math.max(0, Math.min(IslandDraft.RESOLUTION - 1,
            (int)((mouseY - previewY) * IslandDraft.RESOLUTION / previewSize)));
        if (lastPaintX >= 0) {
            int dx = px - lastPaintX;
            int dz = pz - lastPaintZ;
            int steps = Math.max(Math.abs(dx), Math.abs(dz));
            for (int i = 1; i <= steps; i++) {
                state.paint(lastPaintX + dx * i / steps, lastPaintZ + dz * i / steps,
                    brushRadius, selectedTool == Tool.LAND);
            }
        } else {
            state.paint(px, pz, brushRadius, selectedTool == Tool.LAND);
        }
        lastPaintX = px;
        lastPaintZ = pz;
        invalidatePreview();
    }

    private boolean isInsidePreview(double x, double y) {
        return x >= previewX && y >= previewY && x < previewX + previewSize && y < previewY + previewSize;
    }

    private boolean isInsideBiomeList(double x, double y) {
        return !compactLayout && x >= biomeListX && y >= biomeListY
            && x < biomeListX + biomeListWidth && y < biomeListY + biomeListHeight;
    }

    void refreshPreview() { invalidatePreview(); }

    private void commitDesign() {
        if (saving) return;
        if (!state.hasLand()) {
            status = Component.translatable("omniisle.editor.error.empty");
            return;
        }
        Pair<Path, PackRepository> pair = ((CreateWorldBridge)parent).omniisle$getScannedPack(
            parent.getUiState().getSettings().dataConfiguration());
        if (pair == null) {
            status = Component.translatable("omniisle.editor.error.datapack");
            return;
        }
        saving = true;
        doneButton.active = false;
        status = Component.translatable("omniisle.editor.saving");
        IslandDraft snapshot = state.copy();
        CompletableFuture.runAsync(() -> {
            try {
                WorldPackBuilder.export(pair.getFirst(), snapshot);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((ignored, throwable) -> minecraft.execute(() -> {
            if (throwable != null) {
                saving = false;
                doneButton.active = true;
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                status = Component.literal(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                return;
            }
            PackRepository manager = pair.getSecond();
            manager.reload();
            String generatedId = manager.getAvailableIds().stream()
                .filter(id -> id.endsWith(WorldPackBuilder.FILE_NAME)).findFirst().orElse(null);
            if (generatedId == null || (!manager.getSelectedIds().contains(generatedId) && !manager.addPack(generatedId))) {
                saving = false;
                doneButton.active = true;
                status = Component.translatable("omniisle.editor.error.datapack");
                return;
            }
            EditorDraftStore.saveDraft(parent, snapshot);
            ((CreateWorldBridge)parent).omniisle$applyPack(
                manager, true, configuration -> ClientScreenNavigator.open(minecraft, parent));
        }));
    }

    @Override
    public void onClose() { ClientScreenNavigator.open(minecraft, parent); }

    @Override
    public void removed() {
        if (previewTexture != null && minecraft != null) {
            minecraft.getTextureManager().release(previewId);
            previewTexture = null;
        }
    }

    private final class IslandSizeSlider extends AbstractSliderButton {
        private IslandSizeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (state.islandSize() - 2500.0) / 5500.0);
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("omniisle.editor.size", state.islandSize()));
        }
        @Override protected void applyValue() {
            int size = 2500 + (int)Math.round(value * 55.0) * 100;
            state.setIslandSize(size);
            updateMessage();
            invalidatePreview();
        }
    }

    private final class BrushSizeSlider extends AbstractSliderButton {
        private BrushSizeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (brushRadius - 2.0) / 16.0);
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("omniisle.editor.brush_size", brushRadius));
        }
        @Override protected void applyValue() {
            brushRadius = 2 + (int)Math.round(value * 16.0);
            updateMessage();
        }
    }
}
