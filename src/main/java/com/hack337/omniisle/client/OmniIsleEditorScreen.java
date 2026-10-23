package com.hack337.omniisle.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.hack337.omniisle.mixin.client.CreateWorldBridge;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

public final class OmniIsleEditorScreen extends Screen {
    private enum Tool { LAND, ERASER, SMOOTH }

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
    private int controlsHeight = 192;
    private int biomeListX;
    private int biomeListY;
    private int biomeListWidth = 186;
    private int biomeListHeight;
    private boolean compactLayout;
    private int biomeScroll;
    private boolean showStructures;
    private BiomePreview.Category categoryFilter = null;
    private int lastPaintX = -1;
    private int lastPaintZ = -1;
    private boolean saving;
    private Component status = Component.empty();
    private Button landButton;
    private Button eraserButton;
    private Button smoothButton;
    private Button newShapeButton;
    private Button newBiomesButton;
    private Button clearButton;
    private Button doneButton;

    public OmniIsleEditorScreen(CreateWorldScreen parent) {
        super(Component.translatable("omniisle.editor.title"));
        this.parent = parent;
        this.state = EditorDraftStore.openDraft(parent);
        this.previewId = Identifier.fromNamespaceAndPath("omniisle", "editor/preview_" + Integer.toHexString(System.identityHashCode(this)));
    }

    @Override
    protected void init() {
        // At small scale or narrow resolutions, move the full checklist to its own companion screen.
        compactLayout = width < 640 || height < 320;
        previewSize = compactLayout
            ? Math.max(100, Math.min(160, Math.min(height - 104, width - controlsWidth - 36)))
            : Math.max(124, Math.min(286, Math.min(height - 134, width - 496)));
        int totalWidth = compactLayout
            ? previewSize + 14 + controlsWidth
            : previewSize + 18 + controlsWidth + 18 + biomeListWidth;
        previewX = Math.max(10, (width - totalWidth) / 2);
        previewY = compactLayout ? 32 : Math.max(38, (height - Math.max(previewSize, controlsHeight) - 50) / 2);
        controlsX = previewX + previewSize + (compactLayout ? 14 : 18);
        controlsY = previewY;
        biomeListX = controlsX + controlsWidth + 18;
        biomeListY = previewY;
        biomeListHeight = Math.max(previewSize, controlsHeight);
        int y = previewY;

        CycleButton<IslandDraft.Preset> preset = CycleButton
            .<IslandDraft.Preset>builder(value -> Component.translatable(value.translationKey), state.preset())
            .withValues(List.of(IslandDraft.Preset.values()))
            .create(controlsX, y, controlsWidth, 20, Component.translatable("omniisle.editor.preset"), (button, value) -> {
                state.setPreset(value);
                invalidatePreview();
            });
        addRenderableWidget(preset);
        y += 24;

        addRenderableWidget(new IslandSizeSlider(controlsX, y, controlsWidth, 20));
        y += 24;

        landButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.land.short"), button -> selectTool(Tool.LAND))
            .bounds(controlsX, y, 74, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.land.tooltip"))).build());
        eraserButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.eraser.short"), button -> selectTool(Tool.ERASER))
            .bounds(controlsX + 78, y, 74, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.eraser.tooltip"))).build());
        smoothButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.smooth.short"), button -> selectTool(Tool.SMOOTH))
            .bounds(controlsX + 156, y, 74, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.smooth.tooltip"))).build());
        updateToolButtons();
        y += 24;

        addRenderableWidget(new BrushSizeSlider(controlsX, y, controlsWidth, 20));
        y += 24;

        CycleButton<IslandDraft.Relief> reliefButton = CycleButton
            .<IslandDraft.Relief>builder(value -> Component.translatable(value.translationKey), state.relief())
            .withValues(List.of(IslandDraft.Relief.values()))
            .create(controlsX, y, controlsWidth, 20, Component.translatable("omniisle.editor.relief"), (button, value) -> {
                state.setRelief(value);
                invalidatePreview();
            });
        addRenderableWidget(reliefButton);
        y += 24;

        CycleButton<IslandDraft.BiomeTheme> themeButton = CycleButton
            .<IslandDraft.BiomeTheme>builder(value -> Component.translatable(value.translationKey), state.theme())
            .withValues(List.of(IslandDraft.BiomeTheme.values()))
            .create(controlsX, y, controlsWidth, 20, Component.translatable("omniisle.editor.theme"), (button, value) -> {
                state.applyTheme(value);
                invalidatePreview();
            });
        addRenderableWidget(themeButton);
        y += 24;

        newShapeButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.new_shape.short"), button -> {
            state.rerollShape();
            invalidatePreview();
        }).bounds(controlsX, y, 74, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.new_shape.tooltip"))).build());

        newBiomesButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.new_biomes.short"), button -> {
            if (compactLayout) {
                ClientScreenNavigator.open(minecraft, new BiomeSelectionScreen(this, state));
            } else {
                state.rerollBiomes();
                invalidatePreview();
            }
        }).bounds(controlsX + 78, y, 74, 20).tooltip(Tooltip.create(Component.translatable(
            compactLayout ? "omniisle.editor.biomes.tooltip" : "omniisle.editor.new_biomes.tooltip"))).build());

        clearButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.clear"), button -> {
            state.clearLand();
            invalidatePreview();
        }).bounds(controlsX + 156, y, 74, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.clear.tooltip"))).build());
        y += 24;

        addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.advanced"), button -> {
            ClientScreenNavigator.open(minecraft, new AdvancedSettingsScreen(this, state));
        }).bounds(controlsX, y, controlsWidth, 20).tooltip(Tooltip.create(Component.translatable("omniisle.editor.advanced.tooltip"))).build());

        int bottomY = Math.min(height - 29, Math.max(previewY + previewSize, controlsY + controlsHeight) + 14);
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

    /** Bridges constructor signature differences across Minecraft versions. */
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
        if (landButton == null || eraserButton == null || smoothButton == null) return;
        landButton.setMessage(Component.translatable("omniisle.editor.land.short")
            .withStyle(tool == Tool.LAND ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        eraserButton.setMessage(Component.translatable("omniisle.editor.eraser.short")
            .withStyle(tool == Tool.ERASER ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        smoothButton.setMessage(Component.translatable("omniisle.editor.smooth.short")
            .withStyle(tool == Tool.SMOOTH ? ChatFormatting.GREEN : ChatFormatting.WHITE));
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
        if (!saving && isInsidePreview(mouseX, mouseY)) {
            drawBrushCursor(context, mouseX, mouseY);
            drawMapInspectorTooltip(context, mouseX, mouseY);
        }
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
        drawPanel(context, titleX, 6, titleWidth, 22, 0xFF26312D);
        context.centeredText(font, title, width / 2, 13, 0xFFF4E8C1);

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

        int bottomY = Math.min(height - 29, Math.max(previewY + previewSize, controlsY + controlsHeight) + 14);
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
        int color = switch (tool) {
            case LAND -> 0xD0FFFFFF;
            case ERASER -> 0xD0FF6666;
            case SMOOTH -> 0xD066CCFF;
        };
        context.horizontalLine(mouseX - radius, mouseX + radius, mouseY - radius, color);
        context.horizontalLine(mouseX - radius, mouseX + radius, mouseY + radius, color);
        context.verticalLine(mouseX - radius, mouseY - radius, mouseY + radius, color);
        context.verticalLine(mouseX + radius, mouseY - radius, mouseY + radius, color);
    }

    private void drawMapInspectorTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int px = Math.max(0, Math.min(IslandDraft.RESOLUTION - 1,
            (int)((mouseX - previewX) * IslandDraft.RESOLUTION / (double)previewSize)));
        int pz = Math.max(0, Math.min(IslandDraft.RESOLUTION - 1,
            (int)((mouseY - previewY) * IslandDraft.RESOLUTION / (double)previewSize)));
        int worldX = (int)Math.round(state.worldX(px));
        int worldZ = (int)Math.round(state.worldZ(pz));
        boolean isLand = state.isLand(px, pz);
        double continentalness = isLand
            ? (hasOppositeNearby(px, pz, true, 2) ? 0.0 : 0.30)
            : (hasOppositeNearby(px, pz, false, 7) ? -0.32 : -0.68);
        BiomePreview.Type preferred = BiomePreview.surface(
            continentalness, worldX, worldZ, state.biomeSeed());
        BiomePreview.Type biome = state.selectBiome(preferred, worldX, worldZ);

        Component biomeName = Component.translatable(biome.translationKey());
        Component coords = Component.literal("X: " + worldX + "  Z: " + worldZ);
        Component typeTag = Component.translatable(isLand ? "omniisle.editor.inspector.land" : "omniisle.editor.inspector.water");

        int tipW = Math.max(font.width(biomeName) + 18, Math.max(font.width(coords), font.width(typeTag)) + 12);
        int tipH = 34;
        int tipX = mouseX + 12;
        int tipY = mouseY + 12;
        if (tipX + tipW > width - 4) tipX = mouseX - tipW - 12;
        if (tipY + tipH > height - 4) tipY = mouseY - tipH - 12;

        context.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF0101717);
        context.outline(tipX, tipY, tipW, tipH, 0xFF819086);
        drawColorCircle(context, tipX + 7, tipY + 7, biome.color);
        context.text(font, biomeName, tipX + 16, tipY + 4, 0xFFFFFFFF, false);
        context.text(font, coords, tipX + 6, tipY + 14, 0xFFAAAAAA, false);
        context.text(font, typeTag, tipX + 6, tipY + 24, isLand ? 0xFF88FF88 : 0xFF88CCFF, false);
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

    private void drawChoiceList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.fill(biomeListX, biomeListY, biomeListX + biomeListWidth, biomeListY + biomeListHeight, 0xF0101717);
        context.outline(biomeListX, biomeListY, biomeListWidth, biomeListHeight, 0xFF819086);

        // Top tabs: Biomes / Structures
        int middle = biomeListX + biomeListWidth / 2;
        context.fill(showStructures ? middle : biomeListX + 1, biomeListY + 1,
            showStructures ? biomeListX + biomeListWidth - 1 : middle, biomeListY + 19, 0xFF435A47);
        context.centeredText(font, Component.translatable("omniisle.editor.biomes"),
            biomeListX + biomeListWidth / 4, biomeListY + 6, showStructures ? 0xFFAAAAAA : 0xFFFFFFFF);
        context.centeredText(font, Component.translatable("omniisle.editor.structures"),
            biomeListX + biomeListWidth * 3 / 4, biomeListY + 6, showStructures ? 0xFFFFFFFF : 0xFFAAAAAA);

        // Subheader: Category filter & Select All / Deselect All
        int subY = biomeListY + 20;
        context.fill(biomeListX + 1, subY, biomeListX + biomeListWidth - 1, subY + 17, 0xFF1C2422);
        context.horizontalLine(biomeListX, biomeListX + biomeListWidth - 1, subY + 17, 0xFF35443E);

        if (!showStructures) {
            boolean catHover = mouseX >= biomeListX + 2 && mouseX < biomeListX + 96 && mouseY >= subY + 1 && mouseY < subY + 16;
            context.fill(biomeListX + 2, subY + 1, biomeListX + 96, subY + 16, catHover ? 0xFF3D5048 : 0xFF2A3632);
            context.outline(biomeListX + 2, subY + 1, 94, 15, 0xFF586C62);
            Component catLabel = Component.literal(categoryName(categoryFilter).getString() + " ▼");
            context.centeredText(font, shortened(catLabel, 90), biomeListX + 49, subY + 5, 0xFFD8E2DC);
        } else {
            context.centeredText(font, Component.translatable("omniisle.category.all"), biomeListX + 49, subY + 5, 0xFF889890);
        }

        boolean selAllHover = mouseX >= biomeListX + 98 && mouseX < biomeListX + 140 && mouseY >= subY + 1 && mouseY < subY + 16;
        context.fill(biomeListX + 98, subY + 1, biomeListX + 140, subY + 16, selAllHover ? 0xFF386040 : 0xFF26402C);
        context.outline(biomeListX + 98, subY + 1, 42, 15, 0xFF4A7D55);
        context.centeredText(font, Component.translatable("omniisle.editor.select_all"), biomeListX + 119, subY + 5, 0xFFE0FFE0);

        boolean deselHover = mouseX >= biomeListX + 142 && mouseX < biomeListX + 184 && mouseY >= subY + 1 && mouseY < subY + 16;
        context.fill(biomeListX + 142, subY + 1, biomeListX + 184, subY + 16, deselHover ? 0xFF603838 : 0xFF402626);
        context.outline(biomeListX + 142, subY + 1, 42, 15, 0xFF7D4A4A);
        context.centeredText(font, Component.translatable("omniisle.editor.deselect_all"), biomeListX + 163, subY + 5, 0xFFFFE0E0);

        // Checklist rows
        int rowHeight = 15;
        int firstY = biomeListY + 39;
        int visibleRows = Math.max(1, (biomeListHeight - 42) / rowHeight);
        int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : getFilteredBiomes().size();
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
                BiomePreview.Type biome = getFilteredBiomes().get(index);
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
            int trackY = biomeListY + 39;
            int trackH = biomeListHeight - 42;
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
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown()) {
            if (event.key() == InputConstants.KEY_Z) {
                if (event.hasShiftDown()) {
                    if (state.redo()) {
                        invalidatePreview();
                        return true;
                    }
                } else {
                    if (state.undo()) {
                        invalidatePreview();
                        return true;
                    }
                }
            } else if (event.key() == InputConstants.KEY_Y) {
                if (state.redo()) {
                    invalidatePreview();
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!saving && button == InputConstants.MOUSE_BUTTON_LEFT && isInsideBiomeList(mouseX, mouseY)) {
            // Tab header switch
            if (mouseY < biomeListY + 20) {
                showStructures = mouseX >= biomeListX + biomeListWidth / 2;
                biomeScroll = 0;
                return true;
            }
            // Subheader: Category filter, Select All, Deselect All
            if (mouseY < biomeListY + 38) {
                if (!showStructures && mouseX >= biomeListX + 2 && mouseX < biomeListX + 96) {
                    categoryFilter = nextCategory(categoryFilter);
                    biomeScroll = 0;
                    return true;
                }
                if (mouseX >= biomeListX + 98 && mouseX < biomeListX + 140) {
                    if (showStructures) {
                        state.selectAllStructures();
                    } else {
                        state.selectAllBiomes(categoryFilter);
                    }
                    invalidatePreview();
                    return true;
                }
                if (mouseX >= biomeListX + 142 && mouseX < biomeListX + 184) {
                    if (showStructures) {
                        state.deselectAllStructures();
                    } else {
                        state.deselectAllBiomes(categoryFilter);
                    }
                    invalidatePreview();
                    return true;
                }
                return true;
            }
            // Scrollbar click
            if (mouseX >= biomeListX + biomeListWidth - 8) {
                int visibleRows = Math.max(1, (biomeListHeight - 42) / 15);
                int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : getFilteredBiomes().size();
                int maxScroll = Math.max(0, itemCount - visibleRows);
                if (maxScroll > 0) {
                    double progress = Math.max(0.0, Math.min(1.0, (mouseY - (biomeListY + 39)) / (double)(biomeListHeight - 42)));
                    biomeScroll = (int)Math.round(progress * maxScroll);
                }
                return true;
            }
            // Item row click
            int index = biomeScroll + ((int)mouseY - (biomeListY + 39)) / 15;
            if (showStructures) {
                if (index >= 0 && index < IslandDraft.STRUCTURE_OPTIONS.size()) {
                    state.toggleStructure(IslandDraft.STRUCTURE_OPTIONS.get(index));
                    invalidatePreview();
                }
            } else {
                List<BiomePreview.Type> list = getFilteredBiomes();
                if (index >= 0 && index < list.size()) {
                    state.toggleBiome(list.get(index));
                    invalidatePreview();
                }
            }
            status = Component.empty();
            return true;
        }
        if (!saving && isInsidePreview(mouseX, mouseY) && (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT)) {
            state.pushUndo();
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
            int visibleRows = Math.max(1, (biomeListHeight - 42) / 15);
            int itemCount = showStructures ? IslandDraft.STRUCTURE_OPTIONS.size() : getFilteredBiomes().size();
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
                int curX = lastPaintX + dx * i / steps;
                int curZ = lastPaintZ + dz * i / steps;
                if (selectedTool == Tool.SMOOTH) {
                    state.smooth(curX, curZ, brushRadius);
                } else {
                    state.paint(curX, curZ, brushRadius, selectedTool == Tool.LAND);
                }
            }
        } else {
            if (selectedTool == Tool.SMOOTH) {
                state.smooth(px, pz, brushRadius);
            } else {
                state.paint(px, pz, brushRadius, selectedTool == Tool.LAND);
            }
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
