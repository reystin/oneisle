package com.hack337.omniisle.client;

import com.hack337.omniisle.mixin.client.CreateWorldBridge;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
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
    public enum Layer {
        LAND("omniisle.editor.layer.land"),
        HEIGHT("omniisle.editor.layer.height");

        public final String translationKey;
        Layer(String key) { this.translationKey = key; }
    }

    public enum Tool {
        LAND, ERASER, SMOOTH, FILL, LINE, RECTANGLE, ELLIPSE, SPAWN
    }

    public enum EditorTab {
        SHAPE("omniisle.editor.tabs.shape"),
        TERRAIN("omniisle.editor.tabs.terrain"),
        CLIMATE("omniisle.editor.tabs.climate"),
        BIOMES("omniisle.editor.tabs.biomes"),
        STRUCTURES("omniisle.editor.tabs.structures"),
        TOOLS("omniisle.editor.tabs.tools");

        public final String translationKey;
        EditorTab(String key) { this.translationKey = key; }
    }

    private final CreateWorldScreen parent;
    private final IslandDraft state;
    private final Identifier previewId;
    private DynamicTexture previewTexture;
    private final PreviewTextureSlot previewSlot = new PreviewTextureSlot();
    private int currentTextureRes = -1;
    private boolean previewDirty = true;
    private long lastPreviewUpload;
    private Layer layer = Layer.LAND;
    private Tool tool = Tool.LAND;
    private EditorTab currentTab = EditorTab.SHAPE;
    private IslandDraft.Symmetry symmetry = IslandDraft.Symmetry.OFF;
    private boolean shapeFilled = true;
    private int activeTier = 2;
    private boolean heightSmooth = false;
    private boolean reliefShading = true;
    private boolean showGrid = false;
    private com.hack337.omniisle.continent.IslandTerrain currentTerrain;

    // Zoom & Pan, normalized to the map so it survives resolution changes
    private final PreviewViewport viewport = new PreviewViewport();
    private boolean isPanning = false;
    private double lastDragMouseX;
    private double lastDragMouseY;

    // Shape dragging state
    private boolean isDraggingShape = false;
    private int dragStartX = -1;
    private int dragStartZ = -1;
    private int dragCurX = -1;
    private int dragCurZ = -1;

    private int brushRadius = 7;
    private int previewX;
    private int previewY;
    private int previewSize;
    private int controlsX;
    private int controlsY;
    private int controlsWidth = 230;
    private int controlsHeight = 224;
    private int bottomY;
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
    private long waterClickFlash = 0L;

    // Tab widget lists
    private final List<AbstractWidget> shapeTabWidgets = new ArrayList<>();
    private final List<AbstractWidget> terrainTabWidgets = new ArrayList<>();
    private final List<AbstractWidget> climateTabWidgets = new ArrayList<>();
    private final List<AbstractWidget> biomesTabWidgets = new ArrayList<>();
    private final List<AbstractWidget> structuresTabWidgets = new ArrayList<>();
    private final List<AbstractWidget> toolsTabWidgets = new ArrayList<>();

    // Buttons
    private Button tabShapeButton;
    private Button tabTerrainButton;
    private Button tabClimateButton;
    private Button tabBiomesButton;
    private Button tabStructuresButton;
    private Button tabToolsButton;

    private Button landButton;
    private Button eraserButton;
    private Button smoothButton;
    private Button fillButton;
    private Button lineButton;
    private Button rectButton;
    private Button ellipseButton;
    private Button spawnButton;

    private Button tier0Button;
    private Button tier1Button;
    private Button tier2Button;
    private Button tier3Button;
    private Button heightSmoothButton;
    private Button shapeModeButton;
    private Button zoomResetButton;
    private Button openBiomeListButton;
    private Button openStructureListButton;

    private EditBox shapeSeedBox;
    private EditBox biomeSeedBox;
    private Button doneButton;

    public OmniIsleEditorScreen(CreateWorldScreen parent) {
        super(Component.translatable("omniisle.editor.title"));
        this.parent = parent;
        this.state = EditorDraftStore.openDraft(parent);
        this.previewId = Identifier.fromNamespaceAndPath("omniisle", "editor/preview_" + Integer.toHexString(System.identityHashCode(this)));
    }

    @Override
    protected void init() {
        shapeTabWidgets.clear();
        terrainTabWidgets.clear();
        climateTabWidgets.clear();
        biomesTabWidgets.clear();
        structuresTabWidgets.clear();
        toolsTabWidgets.clear();

        EditorLayout.Layout layout = EditorLayout.compute(width, height);
        compactLayout = layout.compact();
        controlsWidth = layout.controlsWidth();
        controlsHeight = layout.controlsHeight();
        previewSize = layout.previewSize();
        previewX = layout.previewX();
        previewY = layout.previewY();
        controlsX = layout.controlsX();
        controlsY = layout.controlsY();
        biomeListX = layout.biomeListX();
        biomeListY = layout.biomeListY();
        biomeListHeight = layout.biomeListHeight();

        int rowStep = layout.rowStep();
        int rowH = layout.rowH();
        int halfW = (controlsWidth - 3) / 2;
        int thirdW = (controlsWidth - 4) / 3;
        int tabH = layout.tabH();
        int tabStep = layout.tabStep();

        // Two rows of three tabs keep all six categories readable at GUI scale 4.
        tabShapeButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tabs.shape"), b -> switchTab(EditorTab.SHAPE))
            .bounds(controlsX, controlsY, thirdW, tabH).build());
        tabTerrainButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tabs.terrain"), b -> switchTab(EditorTab.TERRAIN))
            .bounds(controlsX + thirdW + 2, controlsY, thirdW, tabH).build());
        tabClimateButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tabs.climate"), b -> switchTab(EditorTab.CLIMATE))
            .bounds(controlsX + (thirdW + 2) * 2, controlsY, thirdW, tabH).build());
        tabBiomesButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tabs.biomes"), b -> switchTab(EditorTab.BIOMES))
            .bounds(controlsX, controlsY + tabStep, thirdW, tabH).build());
        tabStructuresButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tabs.structures"), b -> switchTab(EditorTab.STRUCTURES))
            .bounds(controlsX + thirdW + 2, controlsY + tabStep, thirdW, tabH).build());
        tabToolsButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tabs.tools"), b -> switchTab(EditorTab.TOOLS))
            .bounds(controlsX + (thirdW + 2) * 2, controlsY + tabStep, thirdW, tabH).build());

        int contentY = layout.contentY();
        bottomY = layout.bottomY();

        // ---------------- TAB: SHAPE ----------------
        int sy = contentY;
        CycleButton<IslandDraft.Preset> presetBtn = CycleButton
            .<IslandDraft.Preset>builder(v -> Component.translatable(v.translationKey), state.preset())
            .withValues(List.of(IslandDraft.Preset.values()))
            .create(controlsX, sy, controlsWidth, rowH, Component.translatable("omniisle.editor.preset"), (b, v) -> {
                state.setPreset(v);
                invalidatePreview();
            });
        shapeTabWidgets.add(addRenderableWidget(presetBtn));
        sy += rowStep;

        shapeTabWidgets.add(addRenderableWidget(new IslandSizeSlider(controlsX, sy, controlsWidth, rowH)));
        sy += rowStep;

        CycleButton<IslandDraft.Relief> reliefButton = CycleButton
            .<IslandDraft.Relief>builder(v -> Component.translatable(v.translationKey), state.relief())
            .withValues(List.of(IslandDraft.Relief.values()))
            .create(controlsX, sy, controlsWidth, rowH, Component.translatable("omniisle.editor.relief"), (b, v) -> {
                state.setRelief(v);
                invalidatePreview();
            });
        shapeTabWidgets.add(addRenderableWidget(reliefButton));
        sy += rowStep;

        Button newShapeBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.new_shape.short"), b -> {
            state.rerollShape();
            if (shapeSeedBox != null) shapeSeedBox.setValue(Long.toHexString(state.shapeSeed()));
            invalidatePreview();
        }).bounds(controlsX, sy, halfW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.new_shape.tooltip"))).build());
        shapeTabWidgets.add(newShapeBtn);

        CycleButton<Integer> resBtn = CycleButton
            .<Integer>builder(v -> Component.literal(v + "x" + v), state.resolution())
            .withValues(List.of(192, 256, 384))
            .create(controlsX + halfW + 3, sy, halfW, rowH, Component.translatable("omniisle.editor.resolution", state.resolution()), (b, v) -> {
                state.setResolution(v);
                invalidatePreview();
            });
        shapeTabWidgets.add(addRenderableWidget(resBtn));
        sy += rowStep;

        shapeSeedBox = new EditBox(font, controlsX + 2, sy, controlsWidth - 30, rowH - 2, Component.translatable("omniisle.editor.shape_seed"));
        shapeSeedBox.setValue(Long.toHexString(state.shapeSeed()));
        shapeSeedBox.setTooltip(Tooltip.create(Component.translatable("omniisle.editor.shape_seed.tooltip")));
        shapeSeedBox.setResponder(val -> {
            try {
                long s = parseSeed(val);
                if (s != state.shapeSeed()) { state.setShapeSeed(s); invalidatePreview(); }
            } catch (Exception ignored) {}
        });
        shapeTabWidgets.add(addRenderableWidget(shapeSeedBox));

        Button shapeDice = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.dice"), b -> {
            state.rerollShape();
            shapeSeedBox.setValue(Long.toHexString(state.shapeSeed()));
            invalidatePreview();
        }).bounds(controlsX + controlsWidth - 26, sy, 26, rowH - 2).tooltip(Tooltip.create(Component.translatable("omniisle.editor.shape_seed.tooltip"))).build());
        shapeTabWidgets.add(shapeDice);
        sy += rowStep;

        Button copySeedsBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.copy_seeds"), b -> {
            minecraft.keyboardHandler.setClipboard(Component.translatable(
                "omniisle.editor.seeds_format", shapeSeedBox.getValue(), biomeSeedBox.getValue()).getString());
            status = Component.translatable("omniisle.editor.seeds_copied");
        }).bounds(controlsX, sy, controlsWidth, rowH).build());
        shapeTabWidgets.add(copySeedsBtn);

        // ---------------- TAB: TERRAIN ----------------
        int ty = contentY;
        CycleButton<Layer> layerToggle = CycleButton
            .<Layer>builder(value -> Component.translatable(value.translationKey), layer)
            .withValues(List.of(Layer.values()))
            .create(controlsX, ty, controlsWidth, rowH, Component.translatable("omniisle.editor.layer"), (button, value) -> {
                layer = value;
                if (layer == Layer.HEIGHT && tool == Tool.SPAWN) tool = Tool.LAND;
                updateToolButtons();
            });
        terrainTabWidgets.add(addRenderableWidget(layerToggle));
        ty += rowStep;

        int tierW = (controlsWidth - 9) / 4;
        tier0Button = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tier0"), b -> selectTier(0))
            .bounds(controlsX, ty, tierW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.tier0.tooltip"))).build());
        tier1Button = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tier1"), b -> selectTier(1))
            .bounds(controlsX + tierW + 3, ty, tierW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.tier1.tooltip"))).build());
        tier2Button = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tier2"), b -> selectTier(2))
            .bounds(controlsX + (tierW + 3) * 2, ty, tierW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.tier2.tooltip"))).build());
        tier3Button = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.tier3"), b -> selectTier(3))
            .bounds(controlsX + (tierW + 3) * 3, ty, tierW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.tier3.tooltip"))).build());
        terrainTabWidgets.add(tier0Button);
        terrainTabWidgets.add(tier1Button);
        terrainTabWidgets.add(tier2Button);
        terrainTabWidgets.add(tier3Button);
        ty += rowStep;

        heightSmoothButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.smooth.short"), b -> selectHeightSmooth())
            .bounds(controlsX, ty, controlsWidth, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.height_smooth.tooltip"))).build());
        terrainTabWidgets.add(heightSmoothButton);
        ty += rowStep;

        Button clearBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.clear"), b -> {
            if (layer == Layer.LAND) state.clearLand();
            else state.clearHeight();
            invalidatePreview();
        }).bounds(controlsX, ty, controlsWidth, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.clear.tooltip"))).build());
        terrainTabWidgets.add(clearBtn);

        // ---------------- TAB: CLIMATE ----------------
        int cy = contentY;
        CycleButton<IslandDraft.BiomeTheme> themeButton = CycleButton
            .<IslandDraft.BiomeTheme>builder(v -> Component.translatable(v.translationKey), state.theme())
            .withValues(List.of(IslandDraft.BiomeTheme.values()))
            .create(controlsX, cy, controlsWidth, rowH, Component.translatable("omniisle.editor.theme"), (b, v) -> {
                state.applyTheme(v);
                invalidatePreview();
            });
        climateTabWidgets.add(addRenderableWidget(themeButton));
        cy += rowStep;

        CycleButton<com.hack337.omniisle.continent.IslandClimate.GradientMode> gradBtn = CycleButton
            .<com.hack337.omniisle.continent.IslandClimate.GradientMode>builder(v -> Component.translatable(v.translationKey), state.gradientMode())
            .withValues(List.of(com.hack337.omniisle.continent.IslandClimate.GradientMode.values()))
            .create(controlsX, cy, controlsWidth, rowH, Component.translatable("omniisle.advanced.gradient_mode"), (b, v) -> {
                state.setGradientMode(v);
                invalidatePreview();
            });
        climateTabWidgets.add(addRenderableWidget(gradBtn));
        cy += rowStep;

        climateTabWidgets.add(addRenderableWidget(new ClimateScaleSlider(controlsX, cy, controlsWidth, rowH)));
        cy += rowStep;

        climateTabWidgets.add(addRenderableWidget(new GradientStrengthSlider(controlsX, cy, controlsWidth, rowH)));
        cy += rowStep;

        climateTabWidgets.add(addRenderableWidget(new ShoreDensitySlider(controlsX, cy, controlsWidth, rowH)));
        cy += rowStep;

        climateTabWidgets.add(addRenderableWidget(new ShoreVarietySlider(controlsX, cy, controlsWidth, rowH)));
        cy += rowStep;

        CycleButton<Boolean> rockyToggle = CycleButton
            .onOffBuilder(state.shoreRocky())
            .create(controlsX, cy, controlsWidth, rowH, Component.translatable("omniisle.editor.shore_rocky"), (b, v) -> {
                state.setShoreRocky(v);
                invalidatePreview();
            });
        climateTabWidgets.add(addRenderableWidget(rockyToggle));
        cy += rowStep;

        CycleButton<Boolean> shadingToggle = CycleButton
            .onOffBuilder(reliefShading)
            .create(controlsX, cy, controlsWidth, rowH, Component.translatable("omniisle.editor.shading"), (b, v) -> {
                reliefShading = v;
                invalidatePreview();
            });
        climateTabWidgets.add(addRenderableWidget(shadingToggle));

        // ---------------- TAB: BIOMES ----------------
        int by = contentY;
        Button categoryBtn = addRenderableWidget(Button.builder(categoryButtonLabel(), b -> {
            categoryFilter = nextCategory(categoryFilter);
            b.setMessage(categoryButtonLabel());
            biomeScroll = 0;
        }).bounds(controlsX, by, controlsWidth, rowH).build());
        biomesTabWidgets.add(categoryBtn);
        by += rowStep;

        Button biomeSelectAll = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.select_all"), b -> {
            state.selectAllBiomes(categoryFilter);
            invalidatePreview();
        }).bounds(controlsX, by, halfW, rowH).build());
        biomesTabWidgets.add(biomeSelectAll);

        Button biomeDeselectAll = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.deselect_all"), b -> {
            state.deselectAllBiomes(categoryFilter);
            invalidatePreview();
        }).bounds(controlsX + halfW + 3, by, halfW, rowH).build());
        biomesTabWidgets.add(biomeDeselectAll);
        by += rowStep;

        biomeSeedBox = new EditBox(font, controlsX + 2, by, controlsWidth - 30, rowH - 2, Component.translatable("omniisle.editor.biome_seed"));
        biomeSeedBox.setValue(Long.toHexString(state.biomeSeed()));
        biomeSeedBox.setTooltip(Tooltip.create(Component.translatable("omniisle.editor.biome_seed.tooltip")));
        biomeSeedBox.setResponder(val -> {
            try {
                long s = parseSeed(val);
                if (s != state.biomeSeed()) { state.setBiomeSeed(s); invalidatePreview(); }
            } catch (Exception ignored) {}
        });
        biomesTabWidgets.add(addRenderableWidget(biomeSeedBox));

        Button biomeDice = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.dice"), b -> {
            state.rerollBiomes();
            biomeSeedBox.setValue(Long.toHexString(state.biomeSeed()));
            invalidatePreview();
        }).bounds(controlsX + controlsWidth - 26, by, 26, rowH - 2).tooltip(Tooltip.create(Component.translatable("omniisle.editor.biome_seed.tooltip"))).build());
        biomesTabWidgets.add(biomeDice);
        by += rowStep;

        Button copyBiomeSeeds = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.copy_seeds"), b -> {
            minecraft.keyboardHandler.setClipboard(Component.translatable(
                "omniisle.editor.seeds_format", shapeSeedBox.getValue(), biomeSeedBox.getValue()).getString());
            status = Component.translatable("omniisle.editor.seeds_copied");
        }).bounds(controlsX, by, controlsWidth, rowH).build());
        biomesTabWidgets.add(copyBiomeSeeds);
        by += rowStep;

        openBiomeListButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.open_biomes"), b -> {
            ClientScreenNavigator.open(minecraft, new BiomeSelectionScreen(this, state, false));
        }).bounds(controlsX, by, controlsWidth, rowH).build());
        biomesTabWidgets.add(openBiomeListButton);

        // ---------------- TAB: STRUCTURES ----------------
        int sty = contentY;
        CycleButton<Double> structDensityButton = CycleButton
            .<Double>builder(val -> Component.literal(String.format(java.util.Locale.ROOT, "%.1fx", val)), state.structureDensity())
            .withValues(List.of(0.5, 1.0, 2.0))
            .create(controlsX, sty, controlsWidth, rowH, Component.translatable("omniisle.editor.structure_density", String.format(java.util.Locale.ROOT, "%.1fx", state.structureDensity())), (b, v) -> {
                state.setStructureDensity(v);
            });
        structuresTabWidgets.add(addRenderableWidget(structDensityButton));
        sty += rowStep;

        Button structSelectAll = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.select_all"), b -> {
            state.selectAllStructures();
            invalidatePreview();
        }).bounds(controlsX, sty, halfW, rowH).build());
        structuresTabWidgets.add(structSelectAll);

        Button structDeselectAll = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.deselect_all"), b -> {
            state.deselectAllStructures();
            invalidatePreview();
        }).bounds(controlsX + halfW + 3, sty, halfW, rowH).build());
        structuresTabWidgets.add(structDeselectAll);
        sty += rowStep;

        openStructureListButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.open_structures"), b -> {
            ClientScreenNavigator.open(minecraft, new BiomeSelectionScreen(this, state, true));
        }).bounds(controlsX, sty, controlsWidth, rowH).build());
        structuresTabWidgets.add(openStructureListButton);

        // ---------------- TAB: TOOLS ----------------
        int toy = contentY;
        int toolW = (controlsWidth - 9) / 4;
        landButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.land.short"), b -> selectTool(Tool.LAND))
            .bounds(controlsX, toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.land.tooltip"))).build());
        eraserButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.eraser.short"), b -> selectTool(Tool.ERASER))
            .bounds(controlsX + (toolW + 3), toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.eraser.tooltip"))).build());
        smoothButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.smooth.short"), b -> selectTool(Tool.SMOOTH))
            .bounds(controlsX + (toolW + 3) * 2, toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.smooth.tooltip"))).build());
        fillButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.fill.short"), b -> selectTool(Tool.FILL))
            .bounds(controlsX + (toolW + 3) * 3, toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.fill.tooltip"))).build());
        toolsTabWidgets.add(landButton);
        toolsTabWidgets.add(eraserButton);
        toolsTabWidgets.add(smoothButton);
        toolsTabWidgets.add(fillButton);
        toy += rowStep;

        lineButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.line.short"), b -> selectTool(Tool.LINE))
            .bounds(controlsX, toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.line.tooltip"))).build());
        rectButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.rect.short"), b -> selectTool(Tool.RECTANGLE))
            .bounds(controlsX + (toolW + 3), toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.rect.tooltip"))).build());
        ellipseButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.ellipse.short"), b -> selectTool(Tool.ELLIPSE))
            .bounds(controlsX + (toolW + 3) * 2, toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.ellipse.tooltip"))).build());
        spawnButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.spawn.short"), b -> selectTool(Tool.SPAWN))
            .bounds(controlsX + (toolW + 3) * 3, toy, toolW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.spawn.tooltip"))).build());
        toolsTabWidgets.add(lineButton);
        toolsTabWidgets.add(rectButton);
        toolsTabWidgets.add(ellipseButton);
        toolsTabWidgets.add(spawnButton);
        toy += rowStep;

        CycleButton<IslandDraft.Symmetry> symToggle = CycleButton
            .<IslandDraft.Symmetry>builder(value -> Component.translatable(value.translationKey), symmetry)
            .withValues(List.of(IslandDraft.Symmetry.values()))
            .create(controlsX, toy, halfW, rowH, Component.translatable("omniisle.symmetry"), (button, value) -> {
                symmetry = value;
            });
        toolsTabWidgets.add(addRenderableWidget(symToggle));

        shapeModeButton = addRenderableWidget(Button.builder(Component.translatable(shapeFilled ? "omniisle.editor.shape_fill" : "omniisle.editor.shape_outline"), b -> {
            shapeFilled = !shapeFilled;
            b.setMessage(Component.translatable(shapeFilled ? "omniisle.editor.shape_fill" : "omniisle.editor.shape_outline"));
        }).bounds(controlsX + halfW + 3, toy, halfW, rowH).build());
        toolsTabWidgets.add(shapeModeButton);
        toy += rowStep;

        BrushSizeSlider brushSlider = new BrushSizeSlider(controlsX, toy, controlsWidth, rowH);
        toolsTabWidgets.add(addRenderableWidget(brushSlider));
        toy += rowStep;

        CycleButton<Boolean> gridBtn = CycleButton
            .onOffBuilder(showGrid)
            .create(controlsX, toy, halfW, rowH, Component.translatable("omniisle.editor.grid", ""), (b, v) -> {
                showGrid = v;
            });
        toolsTabWidgets.add(addRenderableWidget(gridBtn));

        zoomResetButton = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.zoom_reset"), b -> {
            viewport.reset();
        }).bounds(controlsX + halfW + 3, toy, halfW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.zoom_reset.tooltip"))).build());
        toolsTabWidgets.add(zoomResetButton);
        toy += rowStep;

        Button undoBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.undo"), b -> {
            if (state.undo()) invalidatePreview();
        }).bounds(controlsX, toy, halfW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.undo.tooltip"))).build());
        toolsTabWidgets.add(undoBtn);

        Button redoBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.redo"), b -> {
            if (state.redo()) invalidatePreview();
        }).bounds(controlsX + halfW + 3, toy, halfW, rowH).tooltip(Tooltip.create(Component.translatable("omniisle.editor.redo.tooltip"))).build());
        toolsTabWidgets.add(redoBtn);
        toy += rowStep;

        Button shareCodeBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.share_code"), b -> {
            ClientScreenNavigator.open(minecraft, new ShareCodeScreen(this, state));
        }).bounds(controlsX, toy, halfW, rowH).build());
        toolsTabWidgets.add(shareCodeBtn);

        Button pngToolsBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.import_export"), b -> {
            ClientScreenNavigator.open(minecraft, new PngToolsScreen(this, state, currentTerrain));
        }).bounds(controlsX + halfW + 3, toy, halfW, rowH).build());
        toolsTabWidgets.add(pngToolsBtn);
        toy += rowStep;

        Button advBtn = addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.advanced"), b -> {
            ClientScreenNavigator.open(minecraft, new AdvancedSettingsScreen(this, state));
        }).bounds(controlsX, toy, controlsWidth, rowH).build());
        toolsTabWidgets.add(advBtn);

        // Bottom Done / Cancel Buttons
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> commitDesign())
            .bounds(width / 2 - 154, bottomY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 4, bottomY, 150, 20).build());

        switchTab(currentTab);
        updateToolButtons();

        if (previewTexture == null || currentTextureRes != state.resolution()) {
            ensurePreviewTexture();
        }
        invalidatePreview();
    }

    private Component categoryButtonLabel() {
        return Component.translatable("omniisle.editor.biome_category", categoryName(categoryFilter));
    }

    private static long parseSeed(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            return Long.parseUnsignedLong(s, 16);
        } catch (Exception e) {
            try {
                return Long.parseLong(s);
            } catch (Exception ignored) {
                return s.hashCode();
            }
        }
    }

    private void switchTab(EditorTab tab) {
        currentTab = tab;
        shapeTabWidgets.forEach(w -> w.visible = (tab == EditorTab.SHAPE));
        terrainTabWidgets.forEach(w -> w.visible = (tab == EditorTab.TERRAIN));
        climateTabWidgets.forEach(w -> w.visible = (tab == EditorTab.CLIMATE));
        biomesTabWidgets.forEach(w -> w.visible = (tab == EditorTab.BIOMES));
        structuresTabWidgets.forEach(w -> w.visible = (tab == EditorTab.STRUCTURES));
        toolsTabWidgets.forEach(w -> w.visible = (tab == EditorTab.TOOLS));

        styleTab(tabShapeButton, "omniisle.editor.tabs.shape", tab == EditorTab.SHAPE);
        styleTab(tabTerrainButton, "omniisle.editor.tabs.terrain", tab == EditorTab.TERRAIN);
        styleTab(tabClimateButton, "omniisle.editor.tabs.climate", tab == EditorTab.CLIMATE);
        styleTab(tabBiomesButton, "omniisle.editor.tabs.biomes", tab == EditorTab.BIOMES);
        styleTab(tabStructuresButton, "omniisle.editor.tabs.structures", tab == EditorTab.STRUCTURES);
        styleTab(tabToolsButton, "omniisle.editor.tabs.tools", tab == EditorTab.TOOLS);

        if (tab == EditorTab.BIOMES) {
            showStructures = false;
            biomeScroll = 0;
        } else if (tab == EditorTab.STRUCTURES) {
            showStructures = true;
            biomeScroll = 0;
        }
        updateToolButtons();
    }

    private static void styleTab(Button button, String key, boolean selected) {
        button.setMessage(Component.translatable(key).withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
    }

    private void ensurePreviewTexture() {
        previewSlot.ensure(state.resolution(), this::releasePreviewTexture, () -> {
            currentTextureRes = state.resolution();
            previewTexture = createPreviewTexture(currentTextureRes);
            minecraft.getTextureManager().register(previewId, previewTexture);
        });
    }

    private void releasePreviewTexture() {
        if (previewTexture != null && minecraft != null) {
            minecraft.getTextureManager().release(previewId);
            previewTexture = null;
        }
    }

    private static DynamicTexture createPreviewTexture(int resolution) {
        try {
            var named = DynamicTexture.class.getDeclaredConstructor(
                String.class, int.class, int.class, boolean.class);
            named.setAccessible(true);
            return named.newInstance("OmniIsle biome preview",
                resolution, resolution, false);
        } catch (ReflectiveOperationException ignored) {
            try {
                var legacy = DynamicTexture.class.getDeclaredConstructor(
                    int.class, int.class, boolean.class);
                legacy.setAccessible(true);
                return legacy.newInstance(resolution, resolution, false);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported NativeImageBackedTexture constructor", exception);
            }
        }
    }

    private void selectTool(Tool selected) {
        tool = selected;
        updateToolButtons();
    }

    private void selectTier(int tier) {
        activeTier = tier;
        heightSmooth = false;
        updateToolButtons();
    }

    private void selectHeightSmooth() {
        heightSmooth = true;
        updateToolButtons();
    }

    private void updateToolButtons() {
        boolean isLand = layer == Layer.LAND;
        boolean inTerrainTab = currentTab == EditorTab.TERRAIN;
        boolean inToolsTab = currentTab == EditorTab.TOOLS;

        if (landButton != null) landButton.visible = inToolsTab;
        if (eraserButton != null) eraserButton.visible = inToolsTab;
        if (smoothButton != null) smoothButton.visible = inToolsTab;
        if (fillButton != null) fillButton.visible = inToolsTab;
        if (lineButton != null) lineButton.visible = inToolsTab;
        if (rectButton != null) rectButton.visible = inToolsTab;
        if (ellipseButton != null) ellipseButton.visible = inToolsTab;
        if (spawnButton != null) spawnButton.visible = inToolsTab && isLand;
        if (shapeModeButton != null) shapeModeButton.visible = inToolsTab;

        if (tier0Button != null) tier0Button.visible = inTerrainTab && !isLand;
        if (tier1Button != null) tier1Button.visible = inTerrainTab && !isLand;
        if (tier2Button != null) tier2Button.visible = inTerrainTab && !isLand;
        if (tier3Button != null) tier3Button.visible = inTerrainTab && !isLand;
        if (heightSmoothButton != null) heightSmoothButton.visible = inTerrainTab && !isLand;

        if (landButton != null) landButton.setMessage(Component.translatable("omniisle.editor.land.short").withStyle(tool == Tool.LAND ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (eraserButton != null) eraserButton.setMessage(Component.translatable("omniisle.editor.eraser.short").withStyle(tool == Tool.ERASER ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (smoothButton != null) smoothButton.setMessage(Component.translatable("omniisle.editor.smooth.short").withStyle(tool == Tool.SMOOTH ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (fillButton != null) fillButton.setMessage(Component.translatable("omniisle.editor.fill.short").withStyle(tool == Tool.FILL ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (lineButton != null) lineButton.setMessage(Component.translatable("omniisle.editor.line.short").withStyle(tool == Tool.LINE ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (rectButton != null) rectButton.setMessage(Component.translatable("omniisle.editor.rect.short").withStyle(tool == Tool.RECTANGLE ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (ellipseButton != null) ellipseButton.setMessage(Component.translatable("omniisle.editor.ellipse.short").withStyle(tool == Tool.ELLIPSE ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (spawnButton != null) spawnButton.setMessage(Component.translatable("omniisle.editor.spawn.short").withStyle(tool == Tool.SPAWN ? ChatFormatting.GOLD : ChatFormatting.WHITE));

        if (tier0Button != null) tier0Button.setMessage(Component.literal("T0").withStyle(!heightSmooth && activeTier == 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN));
        if (tier1Button != null) tier1Button.setMessage(Component.literal("T1").withStyle(!heightSmooth && activeTier == 1 ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        if (tier2Button != null) tier2Button.setMessage(Component.literal("T2").withStyle(!heightSmooth && activeTier == 2 ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        if (tier3Button != null) tier3Button.setMessage(Component.literal("T3").withStyle(!heightSmooth && activeTier == 3 ? ChatFormatting.GREEN : ChatFormatting.AQUA));
        if (heightSmoothButton != null) heightSmoothButton.setMessage(Component.translatable("omniisle.editor.smooth.short").withStyle(heightSmooth ? ChatFormatting.GREEN : ChatFormatting.WHITE));
    }

    private void invalidatePreview() { previewDirty = true; }

    private void refreshPreviewTexture() {
        long now = System.nanoTime();
        if (!previewDirty || now - lastPreviewUpload < 16_000_000L) return;
        if (previewTexture == null || currentTextureRes != state.resolution()) {
            ensurePreviewTexture();
        }
        NativeImage image = previewTexture.getPixels();
        int resolution = state.resolution();

        double halfSpan = state.worldSpan() / 2.0;
        int[] sp = state.effectiveSpawnPx();
        com.hack337.omniisle.continent.IslandShapeConfig cfg = new com.hack337.omniisle.continent.IslandShapeConfig(
            state.generationVersionNumber(),
            state.isletDensity(),
            state.riverDensity(),
            state.isWorldBorderEnabled(),
            state.worldBorderMargin(),
            state.hasCustomHeight()
                ? java.util.Optional.of(new com.hack337.omniisle.continent.IslandShapeConfig.HeightLayer(2, state.packedHeight()))
                : java.util.Optional.empty(),
            java.util.Optional.of(new com.hack337.omniisle.continent.IslandShapeConfig.SpawnPos((int)Math.round(state.worldX(sp[0])), (int)Math.round(state.worldZ(sp[1])))),
            java.util.Collections.emptyList(),
            java.util.Optional.of(new com.hack337.omniisle.continent.IslandMask(
                resolution, resolution,
                -halfSpan, halfSpan, -halfSpan, halfSpan,
                state.relief().coastWidth, 400.0,
                state.packedMask()
            )),
            state.biomeSeed(),
            state.generationVersionNumber() >= 3
                ? java.util.Optional.of(state.shoreSettings())
                : java.util.Optional.empty()
        );
        this.currentTerrain = com.hack337.omniisle.continent.IslandTerrain.create(cfg);
        boolean shoreAllowed = state.generationVersionNumber() >= 3
            && !state.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();

        float[] elevations = new float[resolution * resolution];
        float[] continentalnessGrid = new float[resolution * resolution];
        int[] baseColors = new int[resolution * resolution];

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                double wx = state.worldX(x);
                double wz = state.worldZ(z);
                double c = currentTerrain.continentalness(wx, wz);
                continentalnessGrid[z * resolution + x] = (float) c;
                elevations[z * resolution + x] = (float) com.hack337.omniisle.continent.IslandTerrain.continentalnessToElevation(c);
            }
        }

        double blockSize = state.worldSpan() / (double) resolution;

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                int xPrev = Math.max(0, x - 1);
                int xNext = Math.min(resolution - 1, x + 1);
                int zPrev = Math.max(0, z - 1);
                int zNext = Math.min(resolution - 1, z + 1);
                double dx = (elevations[z * resolution + xNext] - elevations[z * resolution + xPrev]) / ((xNext - xPrev) * blockSize);
                double dz = (elevations[zNext * resolution + x] - elevations[zPrev * resolution + x]) / ((zNext - zPrev) * blockSize);
                double slope = Math.hypot(dx, dz);
                double cdx = (continentalnessGrid[z * resolution + xNext] - continentalnessGrid[z * resolution + xPrev]) / ((xNext - xPrev) * blockSize);
                double cdz = (continentalnessGrid[zNext * resolution + x] - continentalnessGrid[zPrev * resolution + x]) / ((zNext - zPrev) * blockSize);
                double biomeSlope = Math.hypot(cdx, cdz) * 64.0;

                double wx = state.worldX(x);
                double wz = state.worldZ(z);
                double c = continentalnessGrid[z * resolution + x];
                BiomePreview.Type preferred = BiomePreview.surfaceV3(
                    c, wx, wz, state.biomeSeed(),
                    state.climateScale(), state.gradientMode(), state.gradientStrength(),
                    state.theme(), biomeSlope, state.generationVersionNumber(), halfSpan,
                    currentTerrain != null ? currentTerrain.rivers() : null,
                    currentTerrain, state.shoreSettings(), shoreAllowed
                );
                baseColors[z * resolution + x] = state.selectBiome(preferred, wx, wz).color;

                int color = baseColors[z * resolution + x];
                if (reliefShading) {
                    double norm = Math.sqrt(dx * dx + dz * dz + 1.0);
                    double dot = (0.5 * dx + 0.70710678 + 0.5 * dz) / norm;
                    double diff = (dot - 0.70710678) / 0.70710678;
                    double factor = 1.0 + 0.35 * Math.max(-1.0, Math.min(1.0, diff * 1.8));

                    int a = (color >>> 24) & 0xFF;
                    int r = Math.max(0, Math.min(255, (int) Math.round(((color >>> 16) & 0xFF) * factor)));
                    int g = Math.max(0, Math.min(255, (int) Math.round(((color >>> 8) & 0xFF) * factor)));
                    int b = Math.max(0, Math.min(255, (int) Math.round((color & 0xFF) * factor)));
                    color = (a << 24) | (r << 16) | (g << 8) | b;
                }
                image.setPixel(x, z, color);
            }
        }

        previewTexture.upload();
        previewDirty = false;
        lastPreviewUpload = now;
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

        // Preview box
        drawPanel(context, previewX - 6, previewY - 6, previewSize + 12, previewSize + 12, 0xFF25312E);

        // Render the viewport subregion so zoom and pan affect every overlay
        int res = state.resolution();
        double span = viewport.span();
        float u = (float) (viewport.u0() * res);
        float v = (float) (viewport.v0() * res);
        int sourceWidth = (int) Math.ceil(span * res) + 1;
        sourceWidth = Math.max(1, Math.min(sourceWidth, res - (int) Math.floor(u)));
        context.blit(RenderPipelines.GUI_TEXTURED, previewId, previewX, previewY, u, v,
            previewSize, previewSize, sourceWidth, sourceWidth, res, res);

        // Grid overlay
        if (showGrid) {
            drawGridOverlay(context);
        }

        // Flash outline on error
        long flashElapsed = System.currentTimeMillis() - waterClickFlash;
        int outlineColor = (flashElapsed < 400L) ? 0xFFFF3333 : 0xFFB6C1AA;
        context.outline(previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, outlineColor);

        drawSpawnMarker(context);

        // Controls box
        drawPanel(context, controlsX - 6, controlsY - 6, controlsWidth + 12, controlsHeight + 12, 0xFF2A302B);
        if (!compactLayout) {
            drawPanel(context, biomeListX - 4, biomeListY - 4,
                biomeListWidth + 8, biomeListHeight + 8, 0xFF202B2C);
            drawChoiceList(context, mouseX, mouseY);
        }

        drawPanel(context, width / 2 - 162, bottomY - 5, 324, 30, 0xFF2A302B);
    }

    private void drawGridOverlay(GuiGraphicsExtractor context) {
        int res = state.resolution();
        for (int p = 16; p < res; p += 16) {
            double fx = viewport.screenFractionU(p / (double) res);
            double fz = viewport.screenFractionV(p / (double) res);
            if (fx > 0.0 && fx < 1.0) {
                int sx = previewX + (int) Math.round(fx * previewSize);
                context.verticalLine(sx, previewY, previewY + previewSize, 0x40FFFFFF);
            }
            if (fz > 0.0 && fz < 1.0) {
                int sy = previewY + (int) Math.round(fz * previewSize);
                context.horizontalLine(previewX, previewX + previewSize, sy, 0x40FFFFFF);
            }
        }
    }

    private void drawSpawnMarker(GuiGraphicsExtractor context) {
        int[] spawn = state.effectiveSpawnPx();
        if (spawn == null || spawn.length < 2) return;
        Integer sx = screenXForPixel(spawn[0]);
        Integer sy = screenYForPixel(spawn[1]);
        if (sx == null || sy == null) return;
        boolean explicit = state.hasExplicitSpawn();
        if (explicit) {
            context.horizontalLine(sx - 5, sx + 5, sy, 0xFFFFD700);
            context.verticalLine(sx, sy - 5, sy + 5, 0xFFFFD700);
            context.outline(sx - 3, sy - 3, 7, 7, 0xFFFFD700);
            context.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFFF3333);
        } else {
            context.horizontalLine(sx - 4, sx + 4, sy, 0xC0E0D080);
            context.verticalLine(sx, sy - 4, sy + 4, 0xC0E0D080);
            context.outline(sx - 2, sy - 2, 5, 5, 0xC0E0D080);
        }
    }

    private Integer screenXForPixel(int pixelX) {
        double fraction = viewport.screenFractionU((pixelX + 0.5) / state.resolution());
        if (fraction < -0.02 || fraction > 1.02) return null;
        return previewX + (int) Math.round(fraction * previewSize);
    }

    private Integer screenYForPixel(int pixelZ) {
        double fraction = viewport.screenFractionV((pixelZ + 0.5) / state.resolution());
        if (fraction < -0.02 || fraction > 1.02) return null;
        return previewY + (int) Math.round(fraction * previewSize);
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
        if (layer == Layer.LAND && tool == Tool.SPAWN) {
            context.horizontalLine(mouseX - 6, mouseX + 6, mouseY, 0xFFFFD700);
            context.verticalLine(mouseX, mouseY - 6, mouseY + 6, 0xFFFFD700);
            context.outline(mouseX - 3, mouseY - 3, 7, 7, 0xFFFFD700);
            return;
        }
        int radius = Math.max(2, (int) Math.round(brushRadius * previewSize / (double) state.resolution() * viewport.zoom()));
        int color = (layer == Layer.LAND)
            ? (tool == Tool.ERASER ? 0xD0FF6666 : (tool == Tool.SMOOTH ? 0xD066CCFF : 0xD0FFFFFF))
            : (heightSmooth ? 0xD066CCFF : switch (activeTier) {
                case 0 -> 0xD07FA974;
                case 1 -> 0xD05B8C5A;
                case 2 -> 0xD0D4A359;
                case 3 -> 0xD0FFFFFF;
                default -> 0xD0FFFFFF;
            });

        context.horizontalLine(mouseX - radius, mouseX + radius, mouseY - radius, color);
        context.horizontalLine(mouseX - radius, mouseX + radius, mouseY + radius, color);
        context.verticalLine(mouseX - radius, mouseY - radius, mouseY + radius, color);
        context.verticalLine(mouseX + radius, mouseY - radius, mouseY + radius, color);

        // Preview line/rect/ellipse when dragging
        if (isDraggingShape && dragStartX >= 0 && dragStartZ >= 0) {
            Integer sx0 = screenXForPixel(dragStartX);
            Integer sy0 = screenYForPixel(dragStartZ);
            if (sx0 != null && sy0 != null) {
                context.horizontalLine(Math.min(sx0, mouseX), Math.max(sx0, mouseX), sy0, 0xFFFFFF00);
                context.verticalLine(mouseX, Math.min(sy0, mouseY), Math.max(sy0, mouseY), 0xFFFFFF00);
            }
        }
    }

    private void drawMapInspectorTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int res = state.resolution();
        int px = Math.max(0, Math.min(res - 1, (int) Math.floor(viewport.sourceU((mouseX - previewX) / (double) previewSize) * res)));
        int pz = Math.max(0, Math.min(res - 1, (int) Math.floor(viewport.sourceV((mouseY - previewY) / (double) previewSize) * res)));
        int worldX = (int) Math.round(state.worldX(px));
        int worldZ = (int) Math.round(state.worldZ(pz));
        boolean isLand = state.isLand(px, pz);
        double continentalness = currentTerrain != null
            ? currentTerrain.continentalness(worldX, worldZ)
            : (isLand ? 0.30 : -0.50);
        double dist = currentTerrain != null
            ? currentTerrain.signedDistance(worldX, worldZ)
            : (isLand ? 100.0 : -100.0);
        int elev = (int) Math.round(com.hack337.omniisle.continent.IslandTerrain.continentalnessToElevation(continentalness));
        byte tier = state.tier(px, pz);

        double slope = 0.0;
        if (currentTerrain != null) {
            double cE = currentTerrain.continentalness(worldX + 8, worldZ);
            double cW = currentTerrain.continentalness(worldX - 8, worldZ);
            double cS = currentTerrain.continentalness(worldX, worldZ + 8);
            double cN = currentTerrain.continentalness(worldX, worldZ - 8);
            slope = Math.hypot((cE - cW) / 16.0, (cS - cN) / 16.0) * 64.0;
        }
        boolean shoreAllowed = state.generationVersionNumber() >= 3
            && !state.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();
        com.hack337.omniisle.continent.ShoreSettings shore = state.shoreSettings();
        int shoreOutcome = com.hack337.omniisle.continent.ShoreField.NONE;
        if (shoreAllowed && shore != null && shore.enabled() && currentTerrain != null
            && dist >= com.hack337.omniisle.continent.ShoreField.intertidalLimit(worldX, worldZ, state.biomeSeed())
            && dist <= com.hack337.omniisle.continent.ShoreField.MAX_INLAND) {
            double bay = com.hack337.omniisle.continent.ShoreField.bayness(currentTerrain, worldX, worldZ, dist);
            shoreOutcome = com.hack337.omniisle.continent.ShoreField.outcome(
                dist, worldX, worldZ, slope, bay, shore, state.biomeSeed());
        }

        BiomePreview.Type preferred = BiomePreview.surfaceV3(
            continentalness, worldX, worldZ, state.biomeSeed(),
            state.climateScale(), state.gradientMode(), state.gradientStrength(),
            state.theme(), slope, state.generationVersionNumber(), state.worldSpan() / 2.0,
            currentTerrain != null ? currentTerrain.rivers() : null,
            currentTerrain, shore, shoreAllowed
        );
        BiomePreview.Type biome = state.selectBiome(preferred, worldX, worldZ);

        Component biomeName = Component.translatable(biome.translationKey());
        Component coords = Component.translatable("omniisle.editor.inspector.coords", worldX, worldZ, elev);
        String coastStr = (dist >= 0 ? "+" : "") + (int) Math.round(dist) + "m";

        String isletKey;
        if (isLand) {
            isletKey = "omniisle.editor.inspector.mainland";
        } else if (dist < -400.0) {
            isletKey = "omniisle.editor.inspector.deep_ocean";
        } else if (continentalness >= -0.15) {
            isletKey = "omniisle.editor.inspector.satellite_islet";
        } else {
            isletKey = "omniisle.editor.inspector.coastal_water";
        }

        Component statusTag = Component.translatable("omniisle.editor.inspector.status",
            Component.translatable(isLand ? "omniisle.editor.inspector.land" : "omniisle.editor.inspector.water"),
            coastStr, Component.translatable(isletKey));
        Component tierTag = Component.translatable("omniisle.editor.inspector.tier",
            Component.translatable("omniisle.editor.tier_name." + (tier & 3)));
        String shoreKey = shoreOutcome == com.hack337.omniisle.continent.ShoreField.SAND
            ? "omniisle.editor.inspector.shore.sand"
            : shoreOutcome == com.hack337.omniisle.continent.ShoreField.STONY
                ? "omniisle.editor.inspector.shore.stony"
                : "omniisle.editor.inspector.shore.none";
        Component shoreTag = Component.translatable("omniisle.editor.inspector.shore", Component.translatable(shoreKey));

        int textWidth = Math.max(font.width(coords),
            Math.max(font.width(statusTag), Math.max(font.width(tierTag), font.width(shoreTag))));
        int tipW = Math.max(font.width(biomeName) + 18, textWidth + 12);
        int tipH = 54;
        int tipX = mouseX + 12;
        int tipY = mouseY + 12;
        if (tipX + tipW > width - 4) tipX = mouseX - tipW - 12;
        if (tipY + tipH > height - 4) tipY = mouseY - tipH - 12;

        context.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF0101717);
        context.outline(tipX, tipY, tipW, tipH, 0xFF819086);
        drawColorCircle(context, tipX + 7, tipY + 7, biome.color);
        context.text(font, biomeName, tipX + 16, tipY + 4, 0xFFFFFFFF, false);
        context.text(font, coords, tipX + 6, tipY + 14, 0xFFAAAAAA, false);
        context.text(font, statusTag, tipX + 6, tipY + 24, isLand ? 0xFF88FF88 : 0xFF88CCFF, false);
        context.text(font, tierTag, tipX + 6, tipY + 34, 0xFFE0D080, false);
        context.text(font, shoreTag, tipX + 6, tipY + 44, 0xFFD8C8A0, false);
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

        int middle = biomeListX + biomeListWidth / 2;
        context.fill(showStructures ? middle : biomeListX + 1, biomeListY + 1,
            showStructures ? biomeListX + biomeListWidth - 1 : middle, biomeListY + 19, 0xFF435A47);
        context.centeredText(font, Component.translatable("omniisle.editor.biomes"),
            biomeListX + biomeListWidth / 4, biomeListY + 6, showStructures ? 0xFFAAAAAA : 0xFFFFFFFF);
        context.centeredText(font, Component.translatable("omniisle.editor.structures"),
            biomeListX + biomeListWidth * 3 / 4, biomeListY + 6, showStructures ? 0xFFFFFFFF : 0xFFAAAAAA);

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
            if (mouseY < biomeListY + 20) {
                showStructures = mouseX >= biomeListX + biomeListWidth / 2;
                biomeScroll = 0;
                return true;
            }
            if (mouseY < biomeListY + 38) {
                if (!showStructures && mouseX >= biomeListX + 2 && mouseX < biomeListX + 96) {
                    categoryFilter = nextCategory(categoryFilter);
                    biomeScroll = 0;
                    return true;
                }
                if (mouseX >= biomeListX + 98 && mouseX < biomeListX + 140) {
                    if (showStructures) state.selectAllStructures();
                    else state.selectAllBiomes(categoryFilter);
                    invalidatePreview();
                    return true;
                }
                if (mouseX >= biomeListX + 142 && mouseX < biomeListX + 184) {
                    if (showStructures) state.deselectAllStructures();
                    else state.deselectAllBiomes(categoryFilter);
                    invalidatePreview();
                    return true;
                }
                return true;
            }
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

        if (!saving && isInsidePreview(mouseX, mouseY)) {
            int res = state.resolution();
            int px = sourcePixelX(mouseX);
            int pz = sourcePixelZ(mouseY);

            if (button == InputConstants.MOUSE_BUTTON_MIDDLE
                || (button == InputConstants.MOUSE_BUTTON_LEFT && isSpaceDown())) {
                isPanning = true;
                lastDragMouseX = mouseX;
                lastDragMouseY = mouseY;
                return true;
            }

            if (layer == Layer.LAND && tool == Tool.SPAWN) {
                if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    if (state.isLand(px, pz)) {
                        state.setSpawn(px, pz);
                        status = Component.empty();
                        invalidatePreview();
                    } else {
                        waterClickFlash = System.currentTimeMillis();
                        status = Component.translatable("omniisle.editor.spawn.water_error");
                    }
                } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                    state.clearSpawn();
                    status = Component.empty();
                    invalidatePreview();
                }
                return true;
            }

            if (tool == Tool.FILL) {
                state.pushUndo();
                if (layer == Layer.LAND) {
                    boolean targetVal = button != InputConstants.MOUSE_BUTTON_RIGHT;
                    state.floodFillLand(px, pz, targetVal, symmetry);
                } else {
                    int targetTier = button == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : activeTier;
                    state.floodFillHeight(px, pz, targetTier, symmetry);
                }
                invalidatePreview();
                return true;
            }

            if (tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.ELLIPSE) {
                isDraggingShape = true;
                dragStartX = px;
                dragStartZ = pz;
                dragCurX = px;
                dragCurZ = pz;
                return true;
            }

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
        if (isInsidePreview(mouseX, mouseY)) {
            double fractionX = (mouseX - previewX) / (double) previewSize;
            double fractionZ = (mouseY - previewY) / (double) previewSize;
            viewport.zoomAt(fractionX, fractionZ, Math.pow(1.25, verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (isPanning) {
            viewport.panByScreenFraction(
                (mouseX - lastDragMouseX) / previewSize,
                (mouseY - lastDragMouseY) / previewSize);
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            return true;
        }

        if (!saving && isInsidePreview(mouseX, mouseY)) {
            int px = sourcePixelX(mouseX);
            int pz = sourcePixelZ(mouseY);

            if (isDraggingShape) {
                dragCurX = px;
                dragCurZ = pz;
                return true;
            }

            if (tool == Tool.SPAWN || tool == Tool.FILL) {
                return false;
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) {
                applyBrush(mouseX, mouseY, button == InputConstants.MOUSE_BUTTON_RIGHT ? Tool.ERASER : tool);
                return true;
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isPanning) {
            isPanning = false;
            return true;
        }

        if (isDraggingShape && dragStartX >= 0 && dragStartZ >= 0) {
            state.pushUndo();
            boolean isEraser = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
            boolean value = !isEraser;
            int tier = isEraser ? 1 : activeTier;

            int endX = dragCurX;
            int endZ = dragCurZ;
            if (isShiftDown()) {
                int dx = Math.abs(endX - dragStartX);
                int dz = Math.abs(endZ - dragStartZ);
                int d = Math.max(dx, dz);
                endX = dragStartX + (endX >= dragStartX ? d : -d);
                endZ = dragStartZ + (endZ >= dragStartZ ? d : -d);
            }

            if (tool == Tool.LINE) {
                if (layer == Layer.LAND) {
                    state.drawLine(dragStartX, dragStartZ, endX, endZ, brushRadius, value, symmetry);
                } else {
                    state.drawLineHeight(dragStartX, dragStartZ, endX, endZ, brushRadius, tier, symmetry);
                }
            } else if (tool == Tool.RECTANGLE) {
                if (layer == Layer.LAND) {
                    state.drawRect(dragStartX, dragStartZ, endX, endZ, shapeFilled, brushRadius, value, symmetry);
                } else {
                    state.drawRectHeight(dragStartX, dragStartZ, endX, endZ, shapeFilled, brushRadius, tier, symmetry);
                }
            } else if (tool == Tool.ELLIPSE) {
                if (layer == Layer.LAND) {
                    state.drawEllipse(dragStartX, dragStartZ, endX, endZ, shapeFilled, brushRadius, value, symmetry);
                } else {
                    state.drawEllipseHeight(dragStartX, dragStartZ, endX, endZ, shapeFilled, brushRadius, tier, symmetry);
                }
            }
            isDraggingShape = false;
            dragStartX = -1;
            dragStartZ = -1;
            invalidatePreview();
            return true;
        }

        lastPaintX = -1;
        lastPaintZ = -1;
        return super.mouseReleased(event);
    }

    private void applyBrush(double mouseX, double mouseY, Tool selectedTool) {
        int px = sourcePixelX(mouseX);
        int pz = sourcePixelZ(mouseY);
        if (lastPaintX >= 0) {
            int dx = px - lastPaintX;
            int dz = pz - lastPaintZ;
            int steps = Math.max(Math.abs(dx), Math.abs(dz));
            for (int i = 1; i <= steps; i++) {
                int curX = lastPaintX + dx * i / steps;
                int curZ = lastPaintZ + dz * i / steps;
                applyAt(curX, curZ, selectedTool);
            }
        } else {
            applyAt(px, pz, selectedTool);
        }
        lastPaintX = px;
        lastPaintZ = pz;
        invalidatePreview();
    }

    private void applyAt(int curX, int curZ, Tool selectedTool) {
        if (layer == Layer.LAND) {
            if (selectedTool == Tool.SMOOTH) {
                state.smooth(curX, curZ, brushRadius, symmetry);
            } else {
                state.paint(curX, curZ, brushRadius, selectedTool == Tool.LAND, symmetry);
            }
        } else {
            if (heightSmooth) {
                state.smoothHeight(curX, curZ, brushRadius, symmetry);
            } else {
                int tierToPaint = (selectedTool == Tool.ERASER) ? 1 : activeTier;
                state.paintHeight(curX, curZ, brushRadius, tierToPaint, symmetry);
            }
        }
    }

    private int sourcePixelX(double screenX) {
        int res = state.resolution();
        int value = (int) Math.floor(viewport.sourceU((screenX - previewX) / (double) previewSize) * res);
        return Math.max(0, Math.min(res - 1, value));
    }

    private int sourcePixelZ(double screenY) {
        int res = state.resolution();
        int value = (int) Math.floor(viewport.sourceV((screenY - previewY) / (double) previewSize) * res);
        return Math.max(0, Math.min(res - 1, value));
    }

    private boolean isInsidePreview(double x, double y) {
        return x >= previewX && y >= previewY && x < previewX + previewSize && y < previewY + previewSize;
    }

    private boolean isInsideBiomeList(double x, double y) {
        return !compactLayout && x >= biomeListX && y >= biomeListY
            && x < biomeListX + biomeListWidth && y < biomeListY + biomeListHeight;
    }

    public void refreshPreview() { invalidatePreview(); }

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
        previewSlot.release(this::releasePreviewTexture);
    }

    private boolean isShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    private boolean isSpaceDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_SPACE);
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

    private final class ClimateScaleSlider extends AbstractSliderButton {
        private ClimateScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (state.climateScale() - 384.0) / (1536.0 - 384.0));
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("omniisle.editor.climate_scale", state.climateScale()));
        }
        @Override protected void applyValue() {
            int snapped = 384 + (int) Math.round((value * (1536 - 384)) / 48.0) * 48;
            state.setClimateScale(snapped);
            updateMessage();
            invalidatePreview();
        }
    }

    private final class GradientStrengthSlider extends AbstractSliderButton {
        private GradientStrengthSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), state.gradientStrength());
            updateMessage();
        }
        @Override protected void updateMessage() {
            int pct = (int) Math.round(state.gradientStrength() * 100.0);
            setMessage(Component.translatable("omniisle.editor.gradient_strength", pct + "%"));
        }
        @Override protected void applyValue() {
            state.setGradientStrength(value);
            updateMessage();
            invalidatePreview();
        }
    }

    private final class ShoreDensitySlider extends AbstractSliderButton {
        private ShoreDensitySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), state.shoreDensity());
            updateMessage();
        }
        @Override protected void updateMessage() {
            int pct = (int) Math.round(state.shoreDensity() * 100.0);
            setMessage(Component.translatable("omniisle.editor.shore_density", pct + "%"));
        }
        @Override protected void applyValue() {
            state.setShoreDensity(Math.round(value * 20.0) / 20.0);
            updateMessage();
            invalidatePreview();
        }
    }

    private final class ShoreVarietySlider extends AbstractSliderButton {
        private ShoreVarietySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), state.shoreVariety());
            updateMessage();
        }
        @Override protected void updateMessage() {
            int pct = (int) Math.round(state.shoreVariety() * 100.0);
            setMessage(Component.translatable("omniisle.editor.shore_variety", pct + "%"));
        }
        @Override protected void applyValue() {
            state.setShoreVariety(Math.round(value * 20.0) / 20.0);
            updateMessage();
            invalidatePreview();
        }
    }
}
