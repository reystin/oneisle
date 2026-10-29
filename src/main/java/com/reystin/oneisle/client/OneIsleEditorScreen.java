package com.reystin.oneisle.client;

import com.reystin.oneisle.client.ui.OneBiomeList;
import com.reystin.oneisle.client.ui.OneButton;
import com.reystin.oneisle.client.ui.OneControlsPanel;
import com.reystin.oneisle.client.ui.OneDropdown;
import com.reystin.oneisle.client.ui.OneIcons;
import com.reystin.oneisle.client.ui.OneMetrics;
import com.reystin.oneisle.client.ui.OnePopover;
import com.reystin.oneisle.client.ui.OneSegmented;
import com.reystin.oneisle.client.ui.OneSlider;
import com.reystin.oneisle.client.ui.OneTabBar;
import com.reystin.oneisle.client.ui.OneTextField;
import com.reystin.oneisle.client.ui.OneTheme;
import com.reystin.oneisle.client.ui.OneToast;
import com.reystin.oneisle.client.ui.OneToggle;
import com.reystin.oneisle.client.ui.OneTooltip;
import com.reystin.oneisle.client.ui.OneUi;
import com.reystin.oneisle.continent.BiomeShare;
import com.reystin.oneisle.continent.IslandClimate;
import com.reystin.oneisle.mixin.client.CreateWorldBridge;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/** 1.0 flat editor shell: top bar, tool column, canvas, tabs, controls and biome list. */
public final class OneIsleEditorScreen extends Screen {
    public enum Layer {
        LAND("oneisle.editor.layer.land"),
        HEIGHT("oneisle.editor.layer.height");

        public final String translationKey;
        Layer(String key) { this.translationKey = key; }
    }

    public enum Tool {
        LAND("oneisle.editor.land.short", "oneisle.editor.land.tooltip", OneIcons.BRUSH),
        ERASER("oneisle.editor.eraser.short", "oneisle.editor.eraser.tooltip", OneIcons.ERASER),
        SMOOTH("oneisle.editor.smooth.short", "oneisle.editor.smooth.tooltip", OneIcons.SMOOTH),
        FILL("oneisle.editor.fill.short", "oneisle.editor.fill.tooltip", OneIcons.FILL),
        LINE("oneisle.editor.line.short", "oneisle.editor.line.tooltip", OneIcons.LINE),
        RECTANGLE("oneisle.editor.rect.short", "oneisle.editor.rect.tooltip", OneIcons.RECTANGLE),
        ELLIPSE("oneisle.editor.ellipse.short", "oneisle.editor.ellipse.tooltip", OneIcons.ELLIPSE),
        SPAWN("oneisle.editor.spawn.short", "oneisle.editor.spawn.tooltip", OneIcons.SPAWN);

        public final String labelKey;
        public final String tooltipKey;
        public final OneIcons icon;
        Tool(String labelKey, String tooltipKey, OneIcons icon) {
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.icon = icon;
        }
    }

    public enum EditorTab {
        SHAPE("oneisle.editor.tabs.shape", OneIcons.PRESET),
        TERRAIN("oneisle.editor.tabs.terrain", OneIcons.SMOOTH),
        CLIMATE("oneisle.editor.tabs.climate", OneIcons.SYMMETRY),
        BIOMES("oneisle.editor.tabs.biomes", OneIcons.BRUSH),
        STRUCTURES("oneisle.editor.tabs.structures", OneIcons.SPAWN),
        TOOLS("oneisle.editor.tabs.tools", OneIcons.SETTINGS);

        public final String translationKey;
        public final OneIcons icon;
        EditorTab(String key, OneIcons icon) {
            this.translationKey = key;
            this.icon = icon;
        }
    }

    public enum PreviewMode {
        LAND("oneisle.preview.land"),
        HEIGHT("oneisle.preview.height"),
        BIOMES("oneisle.preview.biomes"),
        SHORE("oneisle.preview.shore");

        public final String translationKey;
        PreviewMode(String key) { this.translationKey = key; }
    }

    private static final BiomePreview.Type[] TYPES = BiomePreview.Type.values();
    private static final BiomePreview.Category[] CATEGORIES = {
        null, BiomePreview.Category.LAND, BiomePreview.Category.SHORE,
        BiomePreview.Category.OCEAN, BiomePreview.Category.RIVER, BiomePreview.Category.CAVE
    };
    private static final double[] STRUCTURE_DENSITIES = {0.5, 1.0, 2.0};
    private static final long CALIBRATION_DEBOUNCE_NANOS = 150_000_000L;
    private static final double[] HEIGHT_STOPS = {-64, -40, -20, -5, 0, 6, 24, 48, 84, 130, 200, 300};
    private static final int[] HEIGHT_COLORS = {
        0xFF0B1E2E, 0xFF123450, 0xFF1B4F6E, 0xFF2A6D87, 0xFFD8C89A,
        0xFF8FB56B, 0xFF6E9C52, 0xFF7D8A5A, 0xFF8A8C80, 0xFFA5A7A3, 0xFFE8ECEF, 0xFFFFFFFF
    };
    private static final ExecutorService CALIBRATION_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OneIsle-ShareCalibration");
        thread.setDaemon(true);
        return thread;
    });

    private record CalibrationOutcome(int generation, long fingerprint, BiomeShareCalibrator.Result result) {}

    private record Tip(Object owner, AbstractWidget area, String title, String description, String shortcut) {}

    private final CreateWorldScreen createParent;
    private final Screen parentScreen;
    private final IslandDraft state;
    private final Identifier previewId;
    private DynamicTexture previewTexture;
    private final PreviewTextureSlot previewSlot = new PreviewTextureSlot();
    private int currentTextureRes = -1;
    private boolean previewDirty = true;
    private boolean previewUploadDirty = true;
    private boolean palettesReady;
    private long lastPreviewUpload;
    private int previewVersion;

    private EditorLayout.Layout layout;
    private final PreviewViewport viewport = new PreviewViewport();
    private EditorTab currentTab = EditorTab.SHAPE;
    private PreviewMode previewMode = PreviewMode.BIOMES;
    private Layer layer = Layer.LAND;
    private Tool tool = Tool.LAND;
    private IslandDraft.Symmetry symmetry = IslandDraft.Symmetry.OFF;
    private boolean shapeFilled = true;
    private int activeTier = 2;
    private boolean heightSmooth;
    private boolean reliefShading = true;
    private boolean showGrid;
    private int brushRadius = 7;
    private com.reystin.oneisle.continent.IslandTerrain currentTerrain;

    private boolean isPanning;
    private double lastDragMouseX;
    private double lastDragMouseY;
    private boolean isDraggingShape;
    private int dragStartX = -1;
    private int dragStartZ = -1;
    private int dragCurX = -1;
    private int dragCurZ = -1;
    private int lastPaintX = -1;
    private int lastPaintZ = -1;
    private long waterClickFlash;

    private int previewX;
    private int previewY;
    private int previewSize;
    private int[] paletteLand;
    private int[] paletteHeight;
    private int[] paletteBiomes;
    private int[] paletteShore;
    private float[] continentalGrid;
    private float[] elevationGrid;
    private short[] biomeGrid;
    private byte[] landGrid;
    private int paletteRes = -1;
    private final int[] previewFamilyCounts = new int[18];
    private int previewLandCount;

    private final List<BiomePreview.Type> filteredBiomes = new ArrayList<>();
    private BiomePreview.Category categoryFilter;
    private boolean showStructures;
    private final List<OneBiomeList.Row> rowCache = new ArrayList<>();
    private boolean listSharesDirty;
    private OneBiomeList.Row hoverTipRow;
    private int hoverTipVersion = -1;
    private String hoverTipText = "";

    private BiomePreview.Type weightEditingType;
    private String weightEditBuffer = "";
    private int calibrationGeneration;
    private long calibrationDueAtNanos;
    private boolean calibrationRunning;
    private int sharesVersion;
    private final AtomicReference<CalibrationOutcome> calibrationOutcome = new AtomicReference<>();
    private boolean saving;

    private OneTabBar tabBar;
    private OneControlsPanel controlsPanel;
    private OneSegmented previewModes;
    private final OneButton[] toolButtons = new OneButton[EditorLayout.TOOL_COUNT];
    private OneButton undoButton;
    private OneButton redoButton;
    private OneButton presetsButton;
    private OneBiomeList list;
    private OneButton listCategoryButton;
    private OneButton doneButton;
    private OneButton cancelButton;
    private OneDropdown dropdown;
    private OnePopover popover;
    private final OneTooltip tooltip = new OneTooltip();
    private final OneToast toasts = new OneToast();
    private String pendingToast;
    private OneToast.Kind pendingToastKind = OneToast.Kind.INFO;

    private static final long[] PERF_SAMPLES = new long[512];
    private static boolean perfCapturing;
    private static int perfSampleCount;
    private static long perfFrameStart;
    private final List<Tip> persistentTips = new ArrayList<>();
    private final List<Tip> tabTips = new ArrayList<>();
    private final List<Tip> activeTips = new ArrayList<>();
    private final List<FormattedCharSequence> noticeLines = new ArrayList<>();
    private final int[] tipOut = new int[4];

    private EditorLayout.Rect screenRect = EditorLayout.Rect.EMPTY;
    private EditorLayout.Rect canvasRect = EditorLayout.Rect.EMPTY;

    // Controls that need relabelling or availability updates.
    private OneButton shapePresetButton;
    private OneSlider islandSizeSlider;
    private OneButton resolutionButton;
    private OneTextField shapeSeedField;
    private OneSegmented terrainTierSeg;
    private OneToggle terrainSmooth;
    private OneButton themeButton;
    private OneButton biomeCategoryButton;
    private OneTextField biomeSeedField;
    private OneToggle rockyToggle;
    private OneToggle shadingToggle;
    private OneButton symmetryButton;
    private OneSegmented shapeModeSeg;
    private OneSlider brushSlider;
    private OneToggle gridToggle;

    private String statusLine = "";
    private int statusPx = Integer.MIN_VALUE;
    private int statusPz = Integer.MIN_VALUE;
    private int statusZoom = -1;
    private Tool statusTool;
    private IslandDraft.Symmetry statusSymmetry;
    private boolean statusSaving;

    private int inspectPx = -1;
    private int inspectPz = -1;
    private int inspectVersion = -1;
    private String inspectName = "";
    private String inspectCoords = "";
    private String inspectStatus = "";
    private String inspectTier = "";
    private String inspectShore = "";
    private int inspectColor = OneTheme.MUTED;
    private boolean inspectLand;
    private int inspectWidth = 120;

    public OneIsleEditorScreen(CreateWorldScreen parent) {
        this(parent, EditorDraftStore.openDraft(parent), parent);
    }

    /** Harness/dev constructor: opens the editor without a CreateWorldScreen. */
    public OneIsleEditorScreen(Screen parent, IslandDraft draft) {
        this(null, draft, parent);
    }

    private OneIsleEditorScreen(CreateWorldScreen createParent, IslandDraft draft, Screen parentScreen) {
        super(Component.translatable("oneisle.editor.title"));
        this.createParent = createParent;
        this.parentScreen = parentScreen;
        this.state = draft;
        this.previewId = Identifier.fromNamespaceAndPath("oneisle",
            "editor/preview_" + Integer.toHexString(System.identityHashCode(this)));
    }

    @Override
    protected void init() {
        commitWeightEdit();
        OneMetrics.tick(font);
        dropdown = null;
        popover = null;
        tooltip.clear();
        toasts.clear();
        persistentTips.clear();
        tabTips.clear();
        activeTips.clear();
        Arrays.fill(toolButtons, null);
        undoButton = null;
        redoButton = null;
        presetsButton = null;
        list = null;
        listCategoryButton = null;
        doneButton = null;
        cancelButton = null;
        shapePresetButton = null;
        islandSizeSlider = null;
        resolutionButton = null;
        shapeSeedField = null;
        terrainTierSeg = null;
        terrainSmooth = null;
        themeButton = null;
        biomeCategoryButton = null;
        biomeSeedField = null;
        rockyToggle = null;
        shadingToggle = null;
        symmetryButton = null;
        shapeModeSeg = null;
        brushSlider = null;
        gridToggle = null;
        hoverTipRow = null;
        hoverTipVersion = -1;
        inspectPx = -1;
        inspectPz = -1;
        inspectVersion = -1;

        layout = EditorLayout.compute(width, height);
        screenRect = new EditorLayout.Rect(0, 0, width, height);
        canvasRect = layout.canvas();
        previewX = canvasRect.x();
        previewY = canvasRect.y();
        previewSize = canvasRect.width();
        updateFilteredCache();

        if (layout.minimal()) {
            initMinimal();
            return;
        }

        buildTopBar();
        buildToolColumn();
        buildPreviewModes();
        buildTabBar();
        buildControlsPanel();
        buildList();
        buildStatusBar();
        switchTab(currentTab.ordinal());
        updateTopBar();
        updateToolButtons();

        ensurePreviewTexture();
        invalidatePreview();
    }

    private void initMinimal() {
        noticeLines.clear();
        noticeLines.addAll(font.split(Component.translatable("oneisle.ui.too_small.hint"),
            Math.max(40, layout.notice().width() - 24)));
        cancelButton = addRenderableWidget(new OneButton(
            layout.cancel().x(), layout.cancel().y(), layout.cancel().width(), layout.cancel().height(),
            text("gui.cancel"), OneButton.Style.SECONDARY, this::onClose));
        doneButton = addRenderableWidget(new OneButton(
            layout.done().x(), layout.done().y(), layout.done().width(), layout.done().height(),
            text("gui.done"), OneButton.Style.PRIMARY, this::commitDesign));
    }

    private void buildTopBar() {
        int iconSize = layout.undo().width();
        undoButton = addRenderableWidget(OneButton.icon(layout.undo().x(), layout.undo().y(),
            iconSize, iconSize, OneIcons.UNDO, text("oneisle.editor.undo"),
            OneButton.Style.GHOST, this::undo));
        persistentTips.add(new Tip(undoButton, undoButton, text("oneisle.editor.undo"),
            text("oneisle.editor.undo.tooltip"), "Ctrl+Z"));
        redoButton = addRenderableWidget(OneButton.icon(layout.redo().x(), layout.redo().y(),
            iconSize, iconSize, OneIcons.REDO, text("oneisle.editor.redo"),
            OneButton.Style.GHOST, this::redo));
        persistentTips.add(new Tip(redoButton, redoButton, text("oneisle.editor.redo"),
            text("oneisle.editor.redo.tooltip"), "Ctrl+Y"));

        presetsButton = addRenderableWidget(new OneButton(layout.presets().x(), layout.presets().y(),
            layout.presets().width(), layout.presets().height(), presetShortLabel(), OneButton.Style.SECONDARY,
            () -> openPresetMenu(presetsButton), OneIcons.PRESET, layout.iconsOnly()));
        persistentTips.add(new Tip(presetsButton, presetsButton,
            text("oneisle.ui.presets.title"),
            text("oneisle.ui.presets.tooltip"), ""));

        boolean iconOnly = layout.iconsOnly();
        OneButton share = addRenderableWidget(new OneButton(layout.share().x(), layout.share().y(),
            layout.share().width(), layout.share().height(), text("oneisle.editor.share_code"),
            OneButton.Style.SECONDARY, this::openShareCode, OneIcons.COPY, iconOnly));
        persistentTips.add(new Tip(share, share, text("oneisle.editor.share_code"),
            text("oneisle.ui.share.tooltip"), ""));

        OneButton png = addRenderableWidget(new OneButton(layout.importExport().x(), layout.importExport().y(),
            layout.importExport().width(), layout.importExport().height(), text("oneisle.editor.import_export"),
            OneButton.Style.SECONDARY, this::openPngTools, OneIcons.IMPORT, true));
        persistentTips.add(new Tip(png, png, text("oneisle.editor.import_export"),
            text("oneisle.ui.png.tooltip"), ""));

        OneButton settings = addRenderableWidget(new OneButton(layout.settings().x(), layout.settings().y(),
            layout.settings().width(), layout.settings().height(), text("oneisle.editor.advanced"),
            OneButton.Style.GHOST, this::openAdvanced, OneIcons.SETTINGS, true));
        persistentTips.add(new Tip(settings, settings, text("oneisle.editor.advanced"),
            text("oneisle.editor.advanced.tooltip"), ""));
    }

    private void buildToolColumn() {
        Tool[] tools = Tool.values();
        boolean iconOnly = layout.iconsOnly();
        for (int i = 0; i < tools.length; i++) {
            Tool value = tools[i];
            EditorLayout.Rect slot = layout.toolSlot(i);
            OneButton button = addRenderableWidget(new OneButton(slot.x(), slot.y(),
                slot.width(), slot.height(), text(value.labelKey), OneButton.Style.SECONDARY,
                () -> selectTool(value), value.icon, iconOnly));
            toolButtons[i] = button;
            persistentTips.add(new Tip(button, button, text(value.labelKey), text(value.tooltipKey), ""));
        }
    }

    private void buildPreviewModes() {
        PreviewMode[] modes = PreviewMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = text(modes[i].translationKey);
        OneSegmented segmented = new OneSegmented(layout.canvasModes().x(), layout.canvasModes().y(),
            layout.canvasModes().width(), layout.canvasModes().height(), labels, null,
            previewMode.ordinal(), this::setPreviewMode);
        previewModes = addRenderableWidget(segmented);
        persistentTips.add(new Tip(segmented, segmented, text("oneisle.ui.preview"),
            text("oneisle.ui.preview.tooltip"), ""));
    }

    private void buildTabBar() {
        EditorTab[] tabs = EditorTab.values();
        String[] labels = new String[tabs.length];
        OneIcons[] icons = new OneIcons[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = text(tabs[i].translationKey);
            icons[i] = tabs[i].icon;
        }
        tabBar = addRenderableWidget(new OneTabBar(layout.tabs().x(), layout.tabs().y(),
            layout.tabs().width(), layout.tabs().height(), labels, icons,
            currentTab.ordinal(), this::switchTab));
        for (int i = 0; i < tabs.length; i++) {
            EditorLayout.Rect slot = layout.tab(i);
            tabBar.setSlot(i, slot.x(), slot.y(), slot.width(), slot.height());
        }
    }

    private void buildControlsPanel() {
        controlsPanel = addRenderableWidget(new OneControlsPanel(layout.controls().x(),
            layout.controls().y(), layout.controls().width(), layout.controls().height())
            .setPaintBackground(false));
    }

    private void buildStatusBar() {
        cancelButton = addRenderableWidget(new OneButton(layout.cancel().x(), layout.cancel().y(),
            layout.cancel().width(), layout.cancel().height(), text("gui.cancel"),
            OneButton.Style.SECONDARY, this::onClose));
        doneButton = addRenderableWidget(new OneButton(layout.done().x(), layout.done().y(),
            layout.done().width(), layout.done().height(), text("gui.done"),
            OneButton.Style.PRIMARY, this::commitDesign));
    }

    private void buildList() {
        if (!layout.listVisible()) return;
        list = new OneBiomeList(layout.listContent().x(), layout.listContent().y(),
            layout.listContent().width(), layout.listContent().height(), 18);
        list.setListener(new OneBiomeList.Listener() {
            @Override public void onToggle(int index, boolean enabled) {
                if (index < 0 || index >= rowCache.size()) return;
                if (showStructures) {
                    state.toggleStructure(IslandDraft.STRUCTURE_OPTIONS.get(index));
                } else {
                    BiomePreview.Type type = filteredBiomes.get(index);
                    toggleBiomeFromUi(type);
                }
                afterWeightChange();
            }
            @Override public void onWeight(int index, int delta) {
                if (showStructures || index < 0 || index >= filteredBiomes.size()) return;
                BiomePreview.Type type = filteredBiomes.get(index);
                int weight = state.isBiomeEnabled(type) ? state.biomeWeight(type) : 0;
                changeWeightFromUi(type, delta < 0
                    ? (weight <= 5 ? 0 : weight - BiomeShare.WEIGHT_STEP)
                    : (weight <= 0 ? BiomeShare.DEFAULT_WEIGHT : weight + BiomeShare.WEIGHT_STEP));
            }
            @Override public void onWeightEdit(int index, int mouseX, int mouseY) {
                if (showStructures || index < 0 || index >= filteredBiomes.size()) return;
                beginWeightEdit(filteredBiomes.get(index));
            }
            @Override public void onActivate(int index) {
                if (index < 0 || index >= rowCache.size()) return;
                if (showStructures) {
                    state.toggleStructure(IslandDraft.STRUCTURE_OPTIONS.get(index));
                    afterWeightChange();
                } else {
                    BiomePreview.Type type = filteredBiomes.get(index);
                    if (type.isLand() && !BiomeShare.isTerrainDriven(type.family)) {
                        resetBiomeWeightFromUi(type);
                    } else {
                        toggleBiomeFromUi(type);
                        afterWeightChange();
                    }
                }
            }
        });
        addRenderableWidget(list);

        listCategoryButton = addRenderableWidget(new OneButton(layout.listHeader().x(),
            layout.listHeader().y(), layout.listHeader().width(), layout.listHeader().height(),
            categoryLabel(), OneButton.Style.SECONDARY, () -> openCategoryMenu(listCategoryButton)));
        persistentTips.add(new Tip(listCategoryButton, listCategoryButton, categoryLabel(),
            text("oneisle.ui.category.tooltip"), ""));
        updateListHeader();
        rebuildListRows(true);
    }

    private void switchTab(int index) {
        EditorTab[] tabs = EditorTab.values();
        currentTab = tabs[Math.max(0, Math.min(index, tabs.length - 1))];
        if (tabBar != null) tabBar.setSelected(currentTab.ordinal());
        if (list != null) {
            boolean structures = currentTab == EditorTab.STRUCTURES;
            if (showStructures != structures) setListMode(structures);
        }
        buildTab(currentTab.ordinal());
    }

    private void buildTab(int index) {
        if (controlsPanel == null) return;
        controlsPanel.clearContent();
        tabTips.clear();
        resetTabRefs();
        Rows rows = new Rows();
        switch (EditorTab.values()[index]) {
            case SHAPE -> buildShapeTab(rows);
            case TERRAIN -> buildTerrainTab(rows);
            case CLIMATE -> buildClimateTab(rows);
            case BIOMES -> buildBiomesTab(rows);
            case STRUCTURES -> buildStructuresTab(rows);
            case TOOLS -> buildToolsTab(rows);
        }
        rows.finish();
        activeTips.clear();
        activeTips.addAll(persistentTips);
        activeTips.addAll(tabTips);
    }

    private void resetTabRefs() {
        shapePresetButton = null;
        islandSizeSlider = null;
        resolutionButton = null;
        shapeSeedField = null;
        terrainTierSeg = null;
        terrainSmooth = null;
        themeButton = null;
        biomeCategoryButton = null;
        biomeSeedField = null;
        rockyToggle = null;
        shadingToggle = null;
        symmetryButton = null;
        shapeModeSeg = null;
        brushSlider = null;
        gridToggle = null;
    }

    private void buildShapeTab(Rows rows) {
        EditorLayout.Rect row = rows.next();
        shapePresetButton = rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            presetLabel(), OneButton.Style.SECONDARY, () -> openPresetMenu(shapePresetButton),
            OneIcons.PRESET, false), text("oneisle.ui.presets", ""),
            text("oneisle.ui.presets.tooltip"), "");

        row = rows.next();
        OneSlider size = new OneSlider(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.ui.size"), normalizeSize(state.islandSize()), 100.0 / 5500.0,
            normalizeSize(IslandDraft.DEFAULT_ISLAND_SIZE), null);
        size.setValueText(text("oneisle.ui.size_value", state.islandSize()));
        size.setOnChange(value -> {
            int next = 2500 + (int) Math.round(value * 55.0) * 100;
            state.setIslandSize(next);
            size.setValueText(text("oneisle.ui.size_value", state.islandSize()));
            invalidatePreview();
        });
        islandSizeSlider = rows.add(size, text("oneisle.ui.size"), text("oneisle.ui.size.tooltip"), "");

        row = rows.next();
        IslandDraft.Relief[] reliefs = IslandDraft.Relief.values();
        String[] reliefLabels = new String[reliefs.length];
        for (int i = 0; i < reliefs.length; i++) reliefLabels[i] = text(reliefs[i].translationKey);
        rows.add(new OneSegmented(row.x(), row.y(), row.width(), row.height(), reliefLabels, null,
            state.relief().ordinal(), value -> {
                state.setRelief(reliefs[value]);
                invalidatePreview();
            }), text("oneisle.ui.relief"), text("oneisle.ui.relief.tooltip"), "");

        row = rows.next();
        resolutionButton = rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            resolutionLabel(state.resolution()), OneButton.Style.SECONDARY, () -> {
                int next = EditorLayout.nextResolution(state.resolution());
                state.setResolution(next);
                if (resolutionButton != null) resolutionButton.setLabel(resolutionLabel(next));
                invalidatePreview();
            }), text("oneisle.editor.resolution", "", ""),
            text("oneisle.editor.resolution.tooltip"), "");

        row = rows.next();
        shapeSeedField = rows.addSeed(row, text("oneisle.editor.shape_seed"),
            Long.toHexString(state.shapeSeed()), value -> {
                try {
                    long seed = parseSeed(value);
                    if (seed != state.shapeSeed()) {
                        state.setShapeSeed(seed);
                        invalidatePreview();
                    }
                } catch (RuntimeException ignored) {
                }
            }, text("oneisle.editor.shape_seed.tooltip"));
        rows.addIconButton(new EditorLayout.Rect(row.right() - 24, row.y(), 24, row.height()),
            OneIcons.DICE, text("oneisle.editor.dice"), () -> {
                state.rerollShape();
                if (shapeSeedField != null) shapeSeedField.setText(Long.toHexString(state.shapeSeed()));
                invalidatePreview();
            }, text("oneisle.editor.shape_seed.tooltip"));

        row = rows.next();
        rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.copy_seeds"), OneButton.Style.SECONDARY, this::copySeeds));
    }

    private void buildTerrainTab(Rows rows) {
        EditorLayout.Rect row = rows.next();
        Layer[] layers = Layer.values();
        rows.add(new OneSegmented(row.x(), row.y(), row.width(), row.height(),
            new String[]{text(layers[0].translationKey), text(layers[1].translationKey)}, null,
            layer.ordinal(), value -> {
                layer = layers[value];
                if (layer == Layer.HEIGHT && tool == Tool.SPAWN) selectTool(Tool.LAND);
                updateTerrainAvailability();
                updateToolButtons();
            }), text("oneisle.editor.layer"), text("oneisle.editor.layer.tooltip"), "");

        row = rows.next();
        terrainTierSeg = rows.add(new OneSegmented(row.x(), row.y(), row.width(), row.height(),
            new String[]{"T0", "T1", "T2", "T3"}, null, activeTier, this::selectTier),
            text("oneisle.ui.terrain_tiers"), text("oneisle.ui.terrain_tiers.tooltip"), "");

        row = rows.next();
        terrainSmooth = rows.add(new OneToggle(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.smooth.short"), heightSmooth, value -> {
                heightSmooth = value;
                updateTerrainAvailability();
            }), text("oneisle.editor.smooth.short"), text("oneisle.editor.height_smooth.tooltip"), "");

        EditorLayout.Rect clearRow = rows.next();
        rows.add(new OneButton(clearRow.x(), clearRow.y(), clearRow.width(), clearRow.height(),
            text("oneisle.editor.clear"), OneButton.Style.DANGER, () -> openClearPopover(clearRow)),
            text("oneisle.editor.clear"), text("oneisle.editor.clear.tooltip"), "");
        updateTerrainAvailability();
    }

    private void buildClimateTab(Rows rows) {
        EditorLayout.Rect row = rows.next();
        themeButton = rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            themeLabel(), OneButton.Style.SECONDARY, () -> openThemeMenu(themeButton)),
            text("oneisle.ui.theme", ""), text("oneisle.ui.theme.tooltip"), "");

        row = rows.next();
        IslandClimate.GradientMode[] modes = IslandClimate.GradientMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = text(modes[i].translationKey);
        rows.add(new OneSegmented(row.x(), row.y(), row.width(), row.height(), labels, null,
            Math.max(0, state.gradientMode().ordinal()), value -> {
                state.setGradientMode(modes[value]);
                invalidatePreview();
            }), text("oneisle.advanced.gradient_mode"), text("oneisle.ui.gradient.tooltip"), "");

        row = rows.next();
        OneSlider climate = new OneSlider(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.ui.climate_scale"), (state.climateScale() - 384.0) / 1152.0, 48.0 / 1152.0,
            (640.0 - 384.0) / 1152.0, null);
        climate.setValueText(Integer.toString(state.climateScale()));
        climate.setOnChange(value -> {
            int snapped = 384 + (int) Math.round((value * 1152.0) / 48.0) * 48;
            state.setClimateScale(snapped);
            climate.setValueText(Integer.toString(state.climateScale()));
            invalidatePreview();
        });
        rows.add(climate, text("oneisle.ui.climate_scale"),
            text("oneisle.editor.climate_scale.tooltip"), "");

        row = rows.next();
        OneSlider strength = new OneSlider(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.ui.gradient_strength"), state.gradientStrength(), 0.01, 0.6, null);
        strength.setValueText(percent(state.gradientStrength()));
        strength.setOnChange(value -> {
            state.setGradientStrength(value);
            strength.setValueText(percent(state.gradientStrength()));
            invalidatePreview();
        });
        rows.add(strength, text("oneisle.ui.gradient_strength"),
            text("oneisle.editor.gradient_strength.tooltip"), "");

        row = rows.next();
        OneSlider beaches = new OneSlider(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.ui.beaches"), state.shoreDensity(), 0.05, 0.35, null);
        beaches.setValueText(percent(state.shoreDensity()));
        beaches.setOnChange(value -> {
            state.setShoreDensity(Math.round(value * 20.0) / 20.0);
            beaches.setValueText(percent(state.shoreDensity()));
            invalidatePreview();
        });
        rows.add(beaches, text("oneisle.ui.beaches"), text("oneisle.ui.beaches.tooltip"), "");

        row = rows.next();
        OneSlider variety = new OneSlider(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.ui.shore_variety"), state.shoreVariety(), 0.05, 0.5, null);
        variety.setValueText(percent(state.shoreVariety()));
        variety.setOnChange(value -> {
            state.setShoreVariety(Math.round(value * 20.0) / 20.0);
            variety.setValueText(percent(state.shoreVariety()));
            invalidatePreview();
        });
        rows.add(variety, text("oneisle.ui.shore_variety"), text("oneisle.ui.shore_variety.tooltip"), "");

        row = rows.next();
        rockyToggle = rows.add(new OneToggle(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.shore_rocky"), state.shoreRocky(), value -> {
                state.setShoreRocky(value);
                invalidatePreview();
            }), text("oneisle.editor.shore_rocky"), text("oneisle.ui.rocky.tooltip"), "");

        row = rows.next();
        shadingToggle = rows.add(new OneToggle(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.shading"), reliefShading, value -> {
                reliefShading = value;
                invalidatePreview();
            }), text("oneisle.editor.shading"), text("oneisle.editor.shading.tooltip"), "");
    }

    private void buildBiomesTab(Rows rows) {
        EditorLayout.Rect row = rows.next();
        biomeCategoryButton = rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            categoryLabel(), OneButton.Style.SECONDARY, () -> openCategoryMenu(biomeCategoryButton)),
            categoryLabel(), text("oneisle.ui.category.tooltip"), "");

        row = rows.next();
        biomeSeedField = rows.addSeed(row, text("oneisle.editor.biome_seed"),
            Long.toHexString(state.biomeSeed()), value -> {
                try {
                    long seed = parseSeed(value);
                    if (seed != state.biomeSeed()) {
                        state.setBiomeSeed(seed);
                        invalidatePreview();
                    }
                } catch (RuntimeException ignored) {
                }
            }, text("oneisle.editor.biome_seed.tooltip"));
        rows.addIconButton(new EditorLayout.Rect(row.right() - 24, row.y(), 24, row.height()),
            OneIcons.DICE, text("oneisle.editor.dice"), () -> {
                state.rerollBiomes();
                if (biomeSeedField != null) biomeSeedField.setText(Long.toHexString(state.biomeSeed()));
                invalidatePreview();
            }, text("oneisle.editor.biome_seed.tooltip"));

        row = rows.next();
        rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.copy_seeds"), OneButton.Style.SECONDARY, this::copySeeds));

        EditorLayout.Rect[] pair = rows.split();
        rows.add(new OneButton(pair[0].x(), pair[0].y(), pair[0].width(), pair[0].height(),
            text("oneisle.editor.select_all"), OneButton.Style.SECONDARY, () -> {
                state.selectAllBiomes(categoryFilter);
                afterWeightChange();
            }), text("oneisle.editor.select_all"), text("oneisle.ui.select_all.tooltip"), "");
        rows.add(new OneButton(pair[1].x(), pair[1].y(), pair[1].width(), pair[1].height(),
            text("oneisle.editor.deselect_all"), OneButton.Style.SECONDARY, () -> {
                state.deselectAllBiomes(categoryFilter);
                afterWeightChange();
            }), text("oneisle.editor.deselect_all"), text("oneisle.ui.deselect_all.tooltip"), "");

        pair = rows.split();
        rows.add(new OneButton(pair[0].x(), pair[0].y(), pair[0].width(), pair[0].height(),
            text("oneisle.editor.weights.reset_all_short"), OneButton.Style.SECONDARY,
            () -> resetWeightsFromUi(categoryFilter)), text("oneisle.editor.weights.reset_all_short"),
            text("oneisle.editor.weights.reset_all.tooltip"), "");
        rows.add(new OneButton(pair[1].x(), pair[1].y(), pair[1].width(), pair[1].height(),
            text("oneisle.editor.weights.disable_short"), OneButton.Style.SECONDARY,
            () -> disableSelectedWeightsFromUi(categoryFilter)), text("oneisle.editor.weights.disable_short"),
            text("oneisle.editor.weights.disable.tooltip"), "");

        row = rows.next();
        rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.open_biomes"), OneButton.Style.SECONDARY,
            () -> openBiomeList(false)), text("oneisle.editor.open_biomes"),
            text("oneisle.ui.open_list.tooltip"), "");
    }

    private void buildStructuresTab(Rows rows) {
        EditorLayout.Rect row = rows.next();
        String[] labels = new String[STRUCTURE_DENSITIES.length];
        int selected = 1;
        for (int i = 0; i < STRUCTURE_DENSITIES.length; i++) {
            labels[i] = String.format(Locale.ROOT, "%.1f×", STRUCTURE_DENSITIES[i]);
            if (Math.abs(state.structureDensity() - STRUCTURE_DENSITIES[i]) < 1.0E-6) selected = i;
        }
        rows.add(new OneSegmented(row.x(), row.y(), row.width(), row.height(), labels, null, selected,
            value -> state.setStructureDensity(STRUCTURE_DENSITIES[value])),
            text("oneisle.ui.structure_density"), text("oneisle.editor.structure_density.tooltip"), "");

        EditorLayout.Rect[] pair = rows.split();
        rows.add(new OneButton(pair[0].x(), pair[0].y(), pair[0].width(), pair[0].height(),
            text("oneisle.editor.select_all"), OneButton.Style.SECONDARY, () -> {
                state.selectAllStructures();
                invalidatePreview();
                updateListModeOnly();
            }), text("oneisle.editor.select_all"), text("oneisle.ui.select_all.tooltip"), "");
        rows.add(new OneButton(pair[1].x(), pair[1].y(), pair[1].width(), pair[1].height(),
            text("oneisle.editor.deselect_all"), OneButton.Style.SECONDARY, () -> {
                state.deselectAllStructures();
                invalidatePreview();
                updateListModeOnly();
            }), text("oneisle.editor.deselect_all"), text("oneisle.ui.deselect_all.tooltip"), "");

        row = rows.next();
        rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.open_structures"), OneButton.Style.SECONDARY,
            () -> openBiomeList(true)), text("oneisle.editor.open_structures"),
            text("oneisle.ui.open_list.tooltip"), "");
    }

    private void buildToolsTab(Rows rows) {
        EditorLayout.Rect row = rows.next();
        symmetryButton = rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            symmetryLabel(), OneButton.Style.SECONDARY, () -> openSymmetryMenu(symmetryButton)),
            text("oneisle.symmetry"), text("oneisle.ui.symmetry.tooltip"), "");

        row = rows.next();
        shapeModeSeg = rows.add(new OneSegmented(row.x(), row.y(), row.width(), row.height(),
            new String[]{text("oneisle.ui.fill"), text("oneisle.ui.outline")}, null,
            shapeFilled ? 0 : 1, value -> shapeFilled = value == 0),
            text("oneisle.ui.shape_mode"), text("oneisle.ui.shape.tooltip"), "");

        row = rows.next();
        OneSlider brush = new OneSlider(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.ui.brush"), (brushRadius - 2.0) / 16.0, 1.0 / 16.0, 5.0 / 16.0, null);
        brush.setValueText(text("oneisle.ui.size_value", brushRadius));
        brush.setOnChange(value -> {
            brushRadius = 2 + (int) Math.round(value * 16.0);
            brush.setValueText(text("oneisle.ui.size_value", brushRadius));
        });
        brushSlider = rows.add(brush, text("oneisle.ui.brush"), text("oneisle.ui.brush.tooltip"), "");

        EditorLayout.Rect[] pair = rows.split();
        gridToggle = rows.add(new OneToggle(pair[0].x(), pair[0].y(), pair[0].width(), pair[0].height(),
            text("oneisle.ui.grid"), showGrid, value -> showGrid = value),
            text("oneisle.ui.grid"), text("oneisle.ui.grid.tooltip"), "");
        rows.add(new OneButton(pair[1].x(), pair[1].y(), pair[1].width(), pair[1].height(),
            text("oneisle.editor.zoom_reset"), OneButton.Style.SECONDARY,
            () -> viewport.reset()), text("oneisle.editor.zoom_reset"),
            text("oneisle.editor.zoom_reset.tooltip"), "");

        pair = rows.split();
        rows.add(new OneButton(pair[0].x(), pair[0].y(), pair[0].width(), pair[0].height(),
            text("oneisle.editor.undo"), OneButton.Style.SECONDARY, this::undo),
            text("oneisle.editor.undo"), text("oneisle.editor.undo.tooltip"), "Ctrl+Z");
        rows.add(new OneButton(pair[1].x(), pair[1].y(), pair[1].width(), pair[1].height(),
            text("oneisle.editor.redo"), OneButton.Style.SECONDARY, this::redo),
            text("oneisle.editor.redo"), text("oneisle.editor.redo.tooltip"), "Ctrl+Y");

        pair = rows.split();
        rows.add(new OneButton(pair[0].x(), pair[0].y(), pair[0].width(), pair[0].height(),
            text("oneisle.editor.share_code"), OneButton.Style.SECONDARY, this::openShareCode));
        rows.add(new OneButton(pair[1].x(), pair[1].y(), pair[1].width(), pair[1].height(),
            text("oneisle.editor.import_export"), OneButton.Style.SECONDARY, this::openPngTools));

        row = rows.next();
        rows.add(new OneButton(row.x(), row.y(), row.width(), row.height(),
            text("oneisle.editor.advanced"), OneButton.Style.SECONDARY, this::openAdvanced));
    }

    private final class Rows {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private int index;

        EditorLayout.Rect next() {
            return layout.controlRow(index++);
        }

        EditorLayout.Rect[] split() {
            return layout.controlCells(index++, 2);
        }

        <T extends AbstractWidget> T add(T widget, String title, String description, String shortcut) {
            widgets.add(widget);
            controlsPanel.add(widget);
            if (description != null && !description.isEmpty()) {
                tabTips.add(new Tip(widget, widget, title, description, shortcut));
            }
            return widget;
        }

        void add(AbstractWidget widget) {
            widgets.add(widget);
            controlsPanel.add(widget);
        }

        void addIconButton(EditorLayout.Rect rect, OneIcons icon, String label, Runnable action, String tip) {
            add(OneButton.icon(rect.x(), rect.y(), rect.width(), rect.height(), icon, label,
                OneButton.Style.SECONDARY, action), label, tip, "");
        }

        OneTextField addSeed(EditorLayout.Rect row, String hint, String initial,
                             java.util.function.Consumer<String> responder, String tip) {
            OneTextField field = new OneTextField(font, row.x(), row.y(), row.width() - 28, row.height(),
                hint, initial, responder);
            controlsPanel.add(field);
            controlsPanel.add(field.box());
            widgets.add(field);
            widgets.add(field.box());
            tabTips.add(new Tip(field, field.box(), hint, tip, ""));
            return field;
        }

        void finish() {
            controlsPanel.setActive(widgets);
        }
    }

    private void updateFilteredCache() {
        filteredBiomes.clear();
        if (categoryFilter == null) {
            filteredBiomes.addAll(IslandDraft.OVERWORLD_BIOMES);
        } else {
            for (BiomePreview.Type type : IslandDraft.OVERWORLD_BIOMES) {
                if (type.category == categoryFilter) filteredBiomes.add(type);
            }
        }
    }

    private void rebuildListRows(boolean resetScroll) {
        if (list == null) return;
        rowCache.clear();
        if (showStructures) {
            for (IslandDraft.StructureOption option : IslandDraft.STRUCTURE_OPTIONS) {
                rowCache.add(new OneBiomeList.Row(option.name(), text(option.translationKey),
                    0xFFE1B866, state.isStructureEnabled(option)));
            }
        } else {
            for (BiomePreview.Type type : filteredBiomes) {
                rowCache.add(new OneBiomeList.Row(type.name(), text(type.translationKey()),
                    type.color, state.isBiomeEnabled(type)));
            }
        }
        updateRowValues();
        list.setRows(rowCache);
        if (resetScroll) list.scrollArea().setScroll(0);
        hoverTipRow = null;
        hoverTipVersion = -1;
    }

    private void updateRowValues() {
        for (int i = 0; i < rowCache.size(); i++) {
            OneBiomeList.Row row = rowCache.get(i);
            if (showStructures) {
                row.enabled = state.isStructureEnabled(IslandDraft.STRUCTURE_OPTIONS.get(i));
                row.weighted = false;
                row.actual = "";
                row.status = OneTheme.Status.NEUTRAL;
                row.editing = null;
                continue;
            }
            BiomePreview.Type type = filteredBiomes.get(i);
            row.enabled = state.isBiomeEnabled(type);
            row.weighted = type.isLand() && !BiomeShare.isTerrainDriven(type.family);
            row.weight = row.enabled ? state.biomeWeight(type) : 0;
            row.actual = actualShareText(type, row.enabled);
            row.status = shareStatus(type, row.enabled, row.weighted);
            row.editing = weightEditingType == type && !weightEditBuffer.isEmpty()
                ? weightEditBuffer : null;
        }
    }

    private void afterWeightChange() {
        invalidatePreview();
        if (list != null) {
            updateRowValues();
            list.setRows(rowCache);
        }
    }

    private void updateListModeOnly() {
        if (list != null) rebuildListRows(false);
    }

    private void setListMode(boolean structures) {
        showStructures = structures;
        updateListHeader();
        if (list != null) rebuildListRows(true);
    }

    /** The list follows the current tab; the header names its contents and only biome rows are filterable. */
    private void updateListHeader() {
        if (listCategoryButton == null) return;
        listCategoryButton.setLabel(showStructures
            ? text("oneisle.ui.list.structures") : categoryLabel());
        listCategoryButton.active = !showStructures;
    }

    private void updateTopBar() {
        if (undoButton != null) undoButton.active = state.canUndo();
        if (redoButton != null) redoButton.active = state.canRedo();
        if (doneButton != null) doneButton.active = !saving;
    }

    private void updateTerrainAvailability() {
        if (terrainTierSeg != null) terrainTierSeg.active = layer == Layer.HEIGHT && !heightSmooth;
        if (terrainSmooth != null) terrainSmooth.active = layer == Layer.HEIGHT;
        if (terrainSmooth != null) terrainSmooth.setValue(heightSmooth);
    }

    private void updateToolButtons() {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            OneButton button = toolButtons[i];
            if (button == null) continue;
            button.setStyle(tool == tools[i] ? OneButton.Style.PRIMARY : OneButton.Style.SECONDARY);
            button.active = tools[i] != Tool.SPAWN || layer == Layer.LAND;
        }
        updateTopBar();
    }

    private void selectTool(Tool selected) {
        if (selected == Tool.SPAWN && layer != Layer.LAND) return;
        tool = selected;
        updateToolButtons();
    }

    private void selectTier(int tier) {
        activeTier = Math.max(0, Math.min(3, tier));
        heightSmooth = false;
        if (terrainTierSeg != null) terrainTierSeg.setSelected(activeTier);
        updateTerrainAvailability();
    }

    private void undo() {
        if (state.undo()) {
            invalidatePreview();
            updateTopBar();
            afterWeightChange();
        }
    }

    private void redo() {
        if (state.redo()) {
            invalidatePreview();
            updateTopBar();
            afterWeightChange();
        }
    }

    private void setPreviewMode(int index) {
        previewMode = PreviewMode.values()[Math.max(0, Math.min(index, PreviewMode.values().length - 1))];
        if (previewModes != null && previewModes.selected() != previewMode.ordinal()) {
            previewModes.setSelected(previewMode.ordinal());
        }
        previewUploadDirty = true;
    }

    private void openPresetMenu(AbstractWidget anchor) {
        IslandDraft.Preset[] presets = IslandDraft.Preset.values();
        String[] items = new String[presets.length];
        for (int i = 0; i < presets.length; i++) items[i] = text(presets[i].translationKey);
        int selected = state.preset().ordinal();
        openDropdown(anchor, text("oneisle.ui.presets", ""), items, selected, index -> {
            state.setPreset(presets[index]);
            updatePresetLabels();
            invalidatePreview();
            if (currentTab == EditorTab.SHAPE) buildTab(currentTab.ordinal());
        });
    }

    private void openThemeMenu(AbstractWidget anchor) {
        IslandDraft.BiomeTheme[] themes = IslandDraft.BiomeTheme.values();
        String[] items = new String[themes.length];
        for (int i = 0; i < themes.length; i++) items[i] = text(themes[i].translationKey);
        openDropdown(anchor, text("oneisle.ui.theme", ""), items, state.theme().ordinal(), index -> {
            state.applyTheme(themes[index]);
            if (themeButton != null) themeButton.setLabel(themeLabel());
            invalidatePreview();
        });
    }

    private void openSymmetryMenu(AbstractWidget anchor) {
        IslandDraft.Symmetry[] values = IslandDraft.Symmetry.values();
        String[] items = new String[values.length];
        for (int i = 0; i < values.length; i++) items[i] = text(values[i].translationKey);
        openDropdown(anchor, text("oneisle.symmetry"), items, symmetry.ordinal(), index -> {
            symmetry = values[index];
            if (symmetryButton != null) symmetryButton.setLabel(symmetryLabel());
        });
    }

    private void openCategoryMenu(AbstractWidget anchor) {
        String[] items = new String[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) items[i] = categoryName(CATEGORIES[i]).getString();
        int selected = 0;
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i] == categoryFilter) selected = i;
        }
        openDropdown(anchor, categoryLabel(), items, selected, index -> {
            categoryFilter = CATEGORIES[index];
            updateFilteredCache();
            if (biomeCategoryButton != null) biomeCategoryButton.setLabel(categoryLabel());
            updateListHeader();
            if (list != null) rebuildListRows(true);
        });
    }

    private void openDropdown(AbstractWidget anchor, String title, String[] items, int selected,
                              java.util.function.IntConsumer pick) {
        closeDropdown();
        int dropdownWidth = Math.max(140, Math.min(280, width - 16));
        int dropdownHeight = Math.min(9, items.length) * 20 + 8;
        int x = Math.max(8, Math.min(anchor.getX() + anchor.getWidth() / 2 - dropdownWidth / 2,
            width - 8 - dropdownWidth));
        int y = anchor.getY() + anchor.getHeight() + 2;
        if (y + dropdownHeight > height - 8) y = Math.max(8, anchor.getY() - dropdownHeight - 2);
        dropdown = new OneDropdown(x, y, dropdownWidth, title, items, selected, index -> {
            closeDropdown();
            pick.accept(index);
        }, this::closeDropdown);
        addRenderableWidget(dropdown);
        setFocused(dropdown);
    }

    private void closeDropdown() {
        if (dropdown == null) return;
        removeWidget(dropdown);
        dropdown = null;
        setFocused(null);
    }

    private void openClearPopover(EditorLayout.Rect anchor) {
        closePopover();
        int popoverWidth = Math.max(160, Math.min(280, width - 16));
        int x = Math.max(8, Math.min(anchor.centerX() - popoverWidth / 2, width - 8 - popoverWidth));
        int y = Math.max(8, Math.min(anchor.y() + anchor.height() + 4, height - 120));
        popover = new OnePopover(x, y, popoverWidth, font,
            text("oneisle.ui.clear.title"),
            text("oneisle.ui.clear.message", text(layer.translationKey)),
            text("oneisle.ui.clear.confirm"), text("gui.cancel"),
            this::confirmClear, this::closePopover);
        addRenderableWidget(popover);
        setFocused(popover);
    }

    private void closePopover() {
        if (popover == null) return;
        removeWidget(popover);
        popover = null;
        setFocused(null);
    }

    private void confirmClear() {
        boolean wasLand = layer == Layer.LAND;
        if (wasLand) state.clearLand();
        else state.clearHeight();
        invalidatePreview();
        closePopover();
        afterWeightChange();
        toasts.push(text(wasLand ? "oneisle.ui.clear.land_done" : "oneisle.ui.clear.height_done"),
            OneToast.Kind.SUCCESS, Util.getMillis());
    }

    private void openBiomeList(boolean structures) {
        if (list != null) {
            setListMode(structures);
            if (controlsPanel != null) buildTab(currentTab.ordinal());
            return;
        }
        if (minecraft != null) {
            ClientScreenNavigator.open(minecraft, new BiomeSelectionScreen(this, state, structures));
        }
    }

    private void openShareCode() {
        if (minecraft != null) ClientScreenNavigator.open(minecraft, new ShareCodeScreen(this, state));
    }

    private void openPngTools() {
        if (minecraft != null) {
            ClientScreenNavigator.open(minecraft, new PngToolsScreen(this, state, currentTerrain));
        }
    }

    private void openAdvanced() {
        if (minecraft != null) ClientScreenNavigator.open(minecraft, new AdvancedSettingsScreen(this, state));
    }

    private void copySeeds() {
        if (minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(text("oneisle.editor.seeds_format",
            Long.toHexString(state.shapeSeed()), Long.toHexString(state.biomeSeed())));
        toasts.push(text("oneisle.editor.seeds_copied"), OneToast.Kind.SUCCESS, Util.getMillis());
    }

    private void updatePresetLabels() {
        if (presetsButton != null) presetsButton.setLabel(presetShortLabel());
        if (shapePresetButton != null) shapePresetButton.setLabel(presetLabel());
    }

    private String presetShortLabel() {
        return text(state.preset().translationKey);
    }

    private String presetLabel() {
        return text("oneisle.ui.presets", text(state.preset().translationKey));
    }

    private String themeLabel() {
        return text("oneisle.ui.theme", text(state.theme().translationKey));
    }

    private String symmetryLabel() {
        return text("oneisle.ui.symmetry", text(symmetry.translationKey));
    }

    private String categoryLabel() {
        return text("oneisle.ui.category", categoryName(categoryFilter).getString());
    }

    private static String resolutionLabel(int resolution) {
        return text("oneisle.editor.resolution", resolution, resolution);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static double normalizeSize(int size) {
        return Math.max(0.0, Math.min(1.0, (size - 2500.0) / 5500.0));
    }

    private static long parseSeed(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            return Long.parseUnsignedLong(value, 16);
        } catch (RuntimeException ignored) {
            try {
                return Long.parseLong(value);
            } catch (RuntimeException ignoredAgain) {
                return value.hashCode();
            }
        }
    }

    private void ensurePreviewTexture() {
        previewSlot.ensure(state.resolution(), this::releasePreviewTexture, () -> {
            currentTextureRes = state.resolution();
            previewTexture = createPreviewTexture(currentTextureRes);
            if (minecraft != null) minecraft.getTextureManager().register(previewId, previewTexture);
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
            return named.newInstance("OneIsle biome preview", resolution, resolution, false);
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

    private void invalidatePreview() {
        previewDirty = true;
        listSharesDirty = true;
        if (state.hasNonNeutralWeights()) {
            calibrationDueAtNanos = System.nanoTime() + CALIBRATION_DEBOUNCE_NANOS;
        }
    }

    public void refreshPreview() {
        invalidatePreview();
    }

    void tickShares() {
        CalibrationOutcome outcome = calibrationOutcome.getAndSet(null);
        if (outcome != null) {
            calibrationRunning = false;
            if (outcome.result() != null && outcome.generation() == calibrationGeneration
                && outcome.fingerprint() == shareFingerprint() && state.hasNonNeutralWeights()) {
                state.applyCalibration(outcome.result());
                sharesVersion++;
                if (!outcome.result().feasible()) {
                    toasts.push(text("oneisle.editor.weights.infeasible",
                        String.format(Locale.ROOT, "%.1f", outcome.result().maxErrorPp())),
                        OneToast.Kind.ERROR, Util.getMillis());
                }
                previewDirty = true;
                listSharesDirty = true;
            }
        }
        if (!calibrationRunning && calibrationDueAtNanos != 0
            && System.nanoTime() >= calibrationDueAtNanos) {
            calibrationDueAtNanos = 0;
            final int generation = ++calibrationGeneration;
            final long fingerprint = shareFingerprint();
            final IslandDraft snapshot = state.copy();
            calibrationRunning = true;
            CALIBRATION_EXECUTOR.submit(() -> {
                BiomeShareCalibrator.Result result = null;
                try {
                    result = BiomeShareCalibrator.calibrate(snapshot);
                } catch (RuntimeException ignored) {
                    // Keep the editor responsive; the current bias stays in effect.
                }
                calibrationOutcome.set(new CalibrationOutcome(generation, fingerprint, result));
            });
        }
    }

    int sharesVersion() {
        return sharesVersion;
    }

    /** Satellite screens hand their feedback back here; it fires on the next editor frame. */
    void queueToast(String message, OneToast.Kind kind) {
        pendingToast = message;
        pendingToastKind = kind;
    }

    private long shareFingerprint() {
        IslandDraft probe = state.copy();
        probe.clearCalibration();
        String json = ShareCode.toJson(probe);
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < json.length(); i++) {
            hash ^= json.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    void setBiomeWeightFromUi(BiomePreview.Type type, int weight) {
        if (type == null || !type.isLand() || BiomeShare.isTerrainDriven(type.family)) return;
        int clamped = BiomeShare.clampWeight(weight);
        boolean enabled = state.isBiomeEnabled(type);
        if (clamped <= 0) {
            if (!enabled) return;
            state.pushUndo();
            state.setBiomeWeight(type, 0);
        } else {
            if (enabled && state.biomeWeight(type) == clamped) return;
            state.pushUndo();
            state.setBiomeWeight(type, clamped);
        }
        afterWeightChange();
    }

    void changeWeightFromUi(BiomePreview.Type type, int weight) {
        setBiomeWeightFromUi(type, weight);
    }

    void toggleBiomeFromUi(BiomePreview.Type type) {
        if (type == null) return;
        state.pushUndo();
        state.toggleBiome(type);
        invalidatePreview();
    }

    void resetBiomeWeightFromUi(BiomePreview.Type type) {
        if (type == null || !type.isLand() || BiomeShare.isTerrainDriven(type.family)) return;
        if (state.isBiomeEnabled(type) && state.biomeWeight(type) == BiomeShare.DEFAULT_WEIGHT) return;
        state.pushUndo();
        state.setBiomeWeight(type, BiomeShare.DEFAULT_WEIGHT);
        afterWeightChange();
    }

    void resetWeightsFromUi(BiomePreview.Category category) {
        boolean changed = false;
        for (BiomePreview.Type type : filteredBiomes) {
            if (!type.isLand()) continue;
            if (category != null && type.category != category) continue;
            if (state.isBiomeEnabled(type) && state.biomeWeight(type) != BiomeShare.DEFAULT_WEIGHT) {
                changed = true;
                break;
            }
        }
        if (!changed) return;
        state.pushUndo();
        state.resetAllBiomeWeights();
        afterWeightChange();
    }

    void disableSelectedWeightsFromUi(BiomePreview.Category category) {
        List<BiomePreview.Type> targets = new ArrayList<>();
        for (BiomePreview.Type type : filteredBiomes) {
            if (!type.isLand() || !state.isBiomeEnabled(type)) continue;
            if (category != null && type.category != category) continue;
            targets.add(type);
        }
        if (targets.isEmpty()) return;
        state.pushUndo();
        for (BiomePreview.Type type : targets) {
            state.setBiomeWeight(type, 0);
        }
        afterWeightChange();
    }

    void beginWeightEdit(BiomePreview.Type type) {
        if (type == null || !type.isLand() || BiomeShare.isTerrainDriven(type.family)) return;
        commitWeightEdit();
        weightEditingType = type;
        weightEditBuffer = String.valueOf(state.isBiomeEnabled(type) ? state.biomeWeight(type) : 0);
        syncEditingRow();
    }

    void commitWeightEdit() {
        BiomePreview.Type type = weightEditingType;
        if (type == null) return;
        String buffer = weightEditBuffer;
        weightEditingType = null;
        weightEditBuffer = "";
        syncEditingRow();
        if (!buffer.isEmpty()) applyWeightEditFromUi(type, buffer);
    }

    void applyWeightEditFromUi(BiomePreview.Type type, String buffer) {
        if (state.applyBiomeWeightText(type, buffer)) {
            invalidatePreview();
            afterWeightChange();
        }
    }

    void cancelWeightEdit() {
        weightEditingType = null;
        weightEditBuffer = "";
        syncEditingRow();
    }

    BiomePreview.Type weightEditingType() {
        return weightEditingType;
    }

    private void syncEditingRow() {
        if (list == null) return;
        for (OneBiomeList.Row row : rowCache) {
            row.editing = weightEditingType != null && row.id.equals(weightEditingType.name())
                && !weightEditBuffer.isEmpty() ? weightEditBuffer : null;
        }
    }

    private void updateStatusLine(int mouseX, int mouseY) {
        int px = -1;
        int pz = -1;
        boolean inside = isInsidePreview(mouseX, mouseY);
        if (inside) {
            px = sourcePixelX(mouseX);
            pz = sourcePixelZ(mouseY);
        }
        int zoom = (int) Math.round(viewport.zoom() * 100.0);
        if (px == statusPx && pz == statusPz && zoom == statusZoom && tool == statusTool
            && symmetry == statusSymmetry && saving == statusSaving) return;
        statusPx = px;
        statusPz = pz;
        statusZoom = zoom;
        statusTool = tool;
        statusSymmetry = symmetry;
        statusSaving = saving;
        String coords = inside ? text("oneisle.ui.coords", px, pz) : "—";
        String biome = "—";
        if (inside && palettesReady && biomeGrid != null) {
            int index = pz * paletteRes + px;
            if (index >= 0 && index < biomeGrid.length) {
                int ordinal = biomeGrid[index] & 0xFFFF;
                if (ordinal < TYPES.length) biome = text(TYPES[ordinal].translationKey());
            }
        }
        String line = text("oneisle.ui.status", coords, biome,
            text("oneisle.ui.zoom", zoom), text(tool.labelKey), text(symmetry.translationKey));
        if (saving) line = line + " · " + text("oneisle.editor.saving");
        statusLine = line;
    }

    private void drawChrome(GuiGraphicsExtractor g) {
        g.fill(0, 0, width, height, OneTheme.BG);
        EditorLayout.Rect top = layout.topBar();
        g.fill(top.x(), top.y(), top.right(), top.bottom(), OneTheme.PANEL);
        OneUi.divider(g, 0, top.bottom() - 1, width);
        drawBrand(g);

        EditorLayout.Rect tools = layout.toolColumn();
        OneUi.panel(g, tools.x(), tools.y(), tools.width(), tools.height(), OneTheme.PANEL, OneTheme.BORDER);
        OneUi.panel(g, layout.controls().x(), layout.controls().y(), layout.controls().width(),
            layout.controls().height(), OneTheme.PANEL, OneTheme.BORDER);
        if (layout.listVisible()) {
            g.fill(layout.listPanel().x(), layout.listPanel().y(), layout.listPanel().right(),
                layout.listPanel().bottom(), OneTheme.PANEL);
        }

        drawCanvas(g);

        EditorLayout.Rect status = layout.status();
        g.fill(status.x(), status.y(), status.right(), status.bottom(), OneTheme.PANEL);
        OneUi.divider(g, 0, status.y(), width);
        OneUi.textFit(g, font, statusLine, layout.statusText().x(),
            OneUi.verticalCenter(font, status.y(), status.height() - 1),
            layout.statusText().width(), OneTheme.MUTED);
    }

    private void drawBrand(GuiGraphicsExtractor g) {
        EditorLayout.Rect brand = layout.brand();
        int square = 6;
        int squareY = brand.y() + (brand.height() - square) / 2;
        g.fill(brand.x(), squareY, brand.x() + square, squareY + square, OneTheme.ACCENT);
        int textWidth = brand.width() - 10;
        if (textWidth > 24) {
            OneUi.textFit(g, font, "OneIsle", brand.x() + 10,
                OneUi.verticalCenter(font, brand.y(), brand.height()), textWidth, OneTheme.TEXT);
        }
    }

    private void drawCanvas(GuiGraphicsExtractor g) {
        EditorLayout.Rect canvas = canvasRect;
        OneUi.panel(g, canvas.x() - 4, canvas.y() - 4, canvas.width() + 8, canvas.height() + 8,
            OneTheme.PANEL, OneTheme.BORDER);
        g.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), 0xFF0B141A);
        if (previewTexture != null && previewSize > 0) {
            int res = state.resolution();
            double span = viewport.span();
            float u = (float) (viewport.u0() * res);
            float v = (float) (viewport.v0() * res);
            int sourceWidth = (int) Math.ceil(span * res) + 1;
            sourceWidth = Math.max(1, Math.min(sourceWidth, res - (int) Math.floor(u)));
            g.blit(RenderPipelines.GUI_TEXTURED, previewId, canvas.x(), canvas.y(), u, v,
                previewSize, previewSize, sourceWidth, sourceWidth, res, res);
        }
        if (showGrid) drawGridOverlay(g);
        long flashElapsed = System.currentTimeMillis() - waterClickFlash;
        int outlineColor = flashElapsed < 400L ? OneTheme.DANGER : OneTheme.BORDER;
        g.outline(canvas.x() - 1, canvas.y() - 1, canvas.width() + 2, canvas.height() + 2, outlineColor);
        drawSpawnMarker(g);
    }

    private void drawGridOverlay(GuiGraphicsExtractor g) {
        int res = state.resolution();
        for (int p = 16; p < res; p += 16) {
            double fx = viewport.screenFractionU(p / (double) res);
            double fz = viewport.screenFractionV(p / (double) res);
            if (fx > 0.0 && fx < 1.0) {
                int sx = previewX + (int) Math.round(fx * previewSize);
                g.verticalLine(sx, previewY, previewY + previewSize, 0x40FFFFFF);
            }
            if (fz > 0.0 && fz < 1.0) {
                int sy = previewY + (int) Math.round(fz * previewSize);
                g.horizontalLine(previewX, previewX + previewSize, sy, 0x40FFFFFF);
            }
        }
    }

    private void drawSpawnMarker(GuiGraphicsExtractor g) {
        int[] spawn = state.effectiveSpawnPx();
        if (spawn == null || spawn.length < 2) return;
        Integer sx = screenXForPixel(spawn[0]);
        Integer sy = screenYForPixel(spawn[1]);
        if (sx == null || sy == null) return;
        if (state.hasExplicitSpawn()) {
            g.horizontalLine(sx - 5, sx + 5, sy, OneTheme.WARN);
            g.verticalLine(sx, sy - 5, sy + 5, OneTheme.WARN);
            g.outline(sx - 3, sy - 3, 7, 7, OneTheme.WARN);
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, OneTheme.DANGER);
        } else {
            g.horizontalLine(sx - 4, sx + 4, sy, OneTheme.withAlpha(OneTheme.WARN, 0.65F));
            g.verticalLine(sx, sy - 4, sy + 4, OneTheme.withAlpha(OneTheme.WARN, 0.65F));
            g.outline(sx - 2, sy - 2, 5, 5, OneTheme.withAlpha(OneTheme.WARN, 0.65F));
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

    private void refreshPreviewTexture() {
        if (!previewDirty && !previewUploadDirty) return;
        long now = System.nanoTime();
        if (now - lastPreviewUpload < 16_000_000L) return;
        if (previewTexture == null || currentTextureRes != state.resolution()) {
            ensurePreviewTexture();
        }
        if (previewDirty) {
            recomputePalettes();
            previewDirty = false;
        }
        uploadSelectedPalette();
        previewUploadDirty = false;
        lastPreviewUpload = now;
    }

    private void recomputePalettes() {
        int resolution = state.resolution();
        if (resolution <= 0) return;
        ensurePaletteArrays(resolution);
        Arrays.fill(previewFamilyCounts, 0);
        previewLandCount = 0;

        double halfSpan = state.worldSpan() / 2.0;
        int[] spawn = state.effectiveSpawnPx();
        com.reystin.oneisle.continent.IslandShapeConfig cfg =
            new com.reystin.oneisle.continent.IslandShapeConfig(
                state.generationVersionNumber(),
                state.isletDensity(),
                state.riverDensity(),
                state.isWorldBorderEnabled(),
                state.worldBorderMargin(),
                state.hasCustomHeight()
                    ? java.util.Optional.of(new com.reystin.oneisle.continent.IslandShapeConfig.HeightLayer(
                        2, state.packedHeight()))
                    : java.util.Optional.empty(),
                java.util.Optional.of(new com.reystin.oneisle.continent.IslandShapeConfig.SpawnPos(
                    (int) Math.round(state.worldX(spawn[0])),
                    (int) Math.round(state.worldZ(spawn[1])))),
                java.util.Collections.emptyList(),
                java.util.Optional.of(new com.reystin.oneisle.continent.IslandMask(
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
        currentTerrain = com.reystin.oneisle.continent.IslandTerrain.create(cfg);
        boolean shoreAllowed = state.generationVersionNumber() >= 3
            && !state.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();
        com.reystin.oneisle.continent.ShoreSettings shore = state.shoreSettings();
        boolean shorePainted = shoreAllowed && shore != null && shore.enabled();
        double blockSize = state.worldSpan() / (double) resolution;

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                double wx = state.worldX(x);
                double wz = state.worldZ(z);
                double c = currentTerrain.continentalness(wx, wz);
                continentalGrid[z * resolution + x] = (float) c;
                elevationGrid[z * resolution + x] = (float)
                    com.reystin.oneisle.continent.IslandTerrain.continentalnessToElevation(c);
            }
        }

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                int index = z * resolution + x;
                int xPrev = Math.max(0, x - 1);
                int xNext = Math.min(resolution - 1, x + 1);
                int zPrev = Math.max(0, z - 1);
                int zNext = Math.min(resolution - 1, z + 1);
                float elevation = elevationGrid[index];
                double dx = (elevationGrid[z * resolution + xNext] - elevationGrid[z * resolution + xPrev])
                    / ((xNext - xPrev) * blockSize);
                double dz = (elevationGrid[zNext * resolution + x] - elevationGrid[zPrev * resolution + x])
                    / ((zNext - zPrev) * blockSize);
                double cdx = (continentalGrid[z * resolution + xNext] - continentalGrid[z * resolution + xPrev])
                    / ((xNext - xPrev) * blockSize);
                double cdz = (continentalGrid[zNext * resolution + x] - continentalGrid[zPrev * resolution + x])
                    / ((zNext - zPrev) * blockSize);
                double biomeSlope = Math.hypot(cdx, cdz) * 64.0;

                double wx = state.worldX(x);
                double wz = state.worldZ(z);
                BiomePreview.Selection selection = BiomePreview.selectionV3(
                    continentalGrid[index], wx, wz, state.biomeSeed(),
                    state.climateScale(), state.gradientMode(), state.gradientStrength(),
                    state.theme(), biomeSlope, state.generationVersionNumber(), halfSpan,
                    currentTerrain.rivers(), currentTerrain, shore, shoreAllowed
                );
                BiomePreview.Type selected = state.selectPreviewBiome(selection, wx, wz);
                biomeGrid[index] = (short) selected.ordinal();
                boolean land = selected.isLand();
                landGrid[index] = (byte) (land ? 1 : 0);
                if (land && selected.family >= 0 && selected.family < previewFamilyCounts.length) {
                    previewFamilyCounts[selected.family]++;
                    previewLandCount++;
                }

                double shade = reliefShading ? shadeFactor(dx, dz) : 1.0;
                paletteBiomes[index] = shadeColor(selected.color, shade);
                int heightBase = heightColor(elevation);
                paletteHeight[index] = reliefShading ? shadeColor(heightBase, shade) : heightBase;
                paletteLand[index] = land
                    ? shadeColor(0xFFAFBDAF, shade)
                    : shadeColor(0xFF16303F, shade);

                int shoreColor = land ? 0xFF33453C : 0xFF142B3A;
                if (shorePainted && Math.abs(continentalGrid[index]) < 0.45) {
                    double dist = currentTerrain.signedDistance(wx, wz);
                    double limit = com.reystin.oneisle.continent.ShoreField.intertidalLimit(
                        wx, wz, state.biomeSeed());
                    if (dist >= limit && dist <= com.reystin.oneisle.continent.ShoreField.MAX_INLAND) {
                        com.reystin.oneisle.continent.ShoreField.Ring ring =
                            com.reystin.oneisle.continent.ShoreField.ring(currentTerrain, wx, wz, dist);
                        int outcome = com.reystin.oneisle.continent.ShoreField.outcome(
                            dist, wx, wz, biomeSlope, currentTerrain.heightTierAt(wx, wz),
                            continentalGrid[index], ring, shore, state.biomeSeed());
                        if (outcome == com.reystin.oneisle.continent.ShoreField.SAND) {
                            shoreColor = 0xFFE8D8A0;
                        } else if (outcome == com.reystin.oneisle.continent.ShoreField.STONY) {
                            shoreColor = 0xFF9AA0A6;
                        }
                    }
                }
                paletteShore[index] = shoreColor;
            }
        }

        palettesReady = true;
        previewVersion++;
        listSharesDirty = true;
    }

    private void ensurePaletteArrays(int resolution) {
        if (paletteRes == resolution && paletteLand != null) return;
        int total = resolution * resolution;
        paletteLand = new int[total];
        paletteHeight = new int[total];
        paletteBiomes = new int[total];
        paletteShore = new int[total];
        continentalGrid = new float[total];
        elevationGrid = new float[total];
        biomeGrid = new short[total];
        landGrid = new byte[total];
        paletteRes = resolution;
        palettesReady = false;
        inspectPx = -1;
        inspectPz = -1;
    }

    private int[] paletteFor(PreviewMode mode) {
        return switch (mode) {
            case LAND -> paletteLand;
            case HEIGHT -> paletteHeight;
            case BIOMES -> paletteBiomes;
            case SHORE -> paletteShore;
        };
    }

    private void uploadSelectedPalette() {
        int[] source = paletteFor(previewMode);
        if (previewTexture == null || source == null) return;
        int resolution = state.resolution();
        if (source.length != resolution * resolution) return;
        NativeImage image = previewTexture.getPixels();
        for (int z = 0; z < resolution; z++) {
            int row = z * resolution;
            for (int x = 0; x < resolution; x++) {
                image.setPixel(x, z, source[row + x]);
            }
        }
        previewTexture.upload();
    }

    private static double shadeFactor(double dx, double dz) {
        double norm = Math.sqrt(dx * dx + dz * dz + 1.0);
        double dot = (0.5 * dx + 0.70710678 + 0.5 * dz) / norm;
        double diff = (dot - 0.70710678) / 0.70710678;
        return 1.0 + 0.35 * Math.max(-1.0, Math.min(1.0, diff * 1.8));
    }

    private static int shadeColor(int color, double factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) Math.max(0, Math.min(255, Math.round(((color >>> 16) & 0xFF) * factor)));
        int g = (int) Math.max(0, Math.min(255, Math.round(((color >>> 8) & 0xFF) * factor)));
        int b = (int) Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int heightColor(double elevation) {
        if (elevation <= HEIGHT_STOPS[0]) return HEIGHT_COLORS[0];
        for (int i = 1; i < HEIGHT_STOPS.length; i++) {
            if (elevation <= HEIGHT_STOPS[i]) {
                double from = HEIGHT_STOPS[i - 1];
                double to = HEIGHT_STOPS[i];
                float t = (float) ((elevation - from) / (to - from));
                return OneTheme.lerp(HEIGHT_COLORS[i - 1], HEIGHT_COLORS[i], t);
            }
        }
        return HEIGHT_COLORS[HEIGHT_COLORS.length - 1];
    }

    String actualShareText(BiomePreview.Type type, boolean enabled) {
        if (!enabled) return "—";
        double achieved = state.achievedShare(type);
        if (achieved >= 0.0) return String.format(Locale.ROOT, "%.1f%%", achieved);
        double preview = previewShare(type);
        if (preview >= 0.0) return String.format(Locale.ROOT, "%.1f%%", preview);
        return "—";
    }

    private double previewShare(BiomePreview.Type type) {
        if (previewLandCount <= 0 || type.family < 0 || type.family >= previewFamilyCounts.length) {
            return -1.0;
        }
        int samples = previewFamilyCounts[type.family];
        return samples > 0 ? samples * 100.0 / previewLandCount : -1.0;
    }

    OneTheme.Status shareStatus(BiomePreview.Type type, boolean enabled, boolean weighted) {
        if (!enabled || !weighted) return OneTheme.Status.NEUTRAL;
        double achieved = state.achievedShare(type);
        double target = state.targetShare(type);
        if (achieved < 0.0 || target < 0.0) return OneTheme.Status.NEUTRAL;
        double diff = Math.abs(achieved - target);
        if (diff <= 3.0) return OneTheme.Status.OK;
        if (diff <= 6.0) return OneTheme.Status.WARN;
        return OneTheme.Status.ERROR;
    }

    private void drawBrushCursor(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (layer == Layer.LAND && tool == Tool.SPAWN) {
            g.horizontalLine(mouseX - 6, mouseX + 6, mouseY, OneTheme.WARN);
            g.verticalLine(mouseX, mouseY - 6, mouseY + 6, OneTheme.WARN);
            g.outline(mouseX - 3, mouseY - 3, 7, 7, OneTheme.WARN);
            return;
        }
        int radius = Math.max(2,
            (int) Math.round(brushRadius * previewSize / (double) state.resolution() * viewport.zoom()));
        int color = (layer == Layer.LAND)
            ? (tool == Tool.ERASER ? OneTheme.DANGER
                : (tool == Tool.SMOOTH ? OneTheme.ACCENT : OneTheme.TEXT))
            : (heightSmooth ? OneTheme.ACCENT : switch (activeTier) {
                case 0 -> 0xFF7FA974;
                case 1 -> 0xFF5B8C5A;
                case 2 -> 0xFFD4A359;
                default -> 0xFFF0F2F4;
            });

        g.horizontalLine(mouseX - radius, mouseX + radius, mouseY - radius, color);
        g.horizontalLine(mouseX - radius, mouseX + radius, mouseY + radius, color);
        g.verticalLine(mouseX - radius, mouseY - radius, mouseY + radius, color);
        g.verticalLine(mouseX + radius, mouseY - radius, mouseY + radius, color);

        if (isDraggingShape && dragStartX >= 0 && dragStartZ >= 0) {
            Integer sx0 = screenXForPixel(dragStartX);
            Integer sy0 = screenYForPixel(dragStartZ);
            if (sx0 != null && sy0 != null) {
                g.horizontalLine(Math.min(sx0, mouseX), Math.max(sx0, mouseX), sy0, OneTheme.WARN);
                g.verticalLine(mouseX, Math.min(sy0, mouseY), Math.max(sy0, mouseY), OneTheme.WARN);
            }
        }
    }

    private void drawInspector(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int res = state.resolution();
        int px = Math.max(0, Math.min(res - 1,
            (int) Math.floor(viewport.sourceU((mouseX - previewX) / (double) previewSize) * res)));
        int pz = Math.max(0, Math.min(res - 1,
            (int) Math.floor(viewport.sourceV((mouseY - previewY) / (double) previewSize) * res)));
        if (px != inspectPx || pz != inspectPz || inspectVersion != previewVersion) {
            rebuildInspector(px, pz, res);
        }
        EditorLayout.placeTooltip(canvasRect, mouseX, mouseY, inspectWidth, 54, tipOut);
        int tipX = tipOut[0];
        int tipY = tipOut[1];
        int tipW = tipOut[2];
        OneUi.panel(g, tipX, tipY, tipW, 54, 0xE6101519, OneTheme.BORDER);
        OneUi.swatch(g, tipX + 5, tipY + 5, 6, 6, inspectColor, 0);
        OneUi.textFit(g, font, inspectName, tipX + 15, tipY + 4, tipW - 20, OneTheme.TEXT);
        int textMax = Math.max(10, tipW - 10);
        OneUi.textFit(g, font, inspectCoords, tipX + 5, tipY + 14, textMax, OneTheme.MUTED);
        OneUi.textFit(g, font, inspectStatus, tipX + 5, tipY + 24, textMax,
            inspectLand ? OneTheme.OK : 0xFF8FC8E8);
        OneUi.textFit(g, font, inspectTier, tipX + 5, tipY + 34, textMax, OneTheme.WARN);
        OneUi.textFit(g, font, inspectShore, tipX + 5, tipY + 44, textMax, 0xFFD8C8A0);
    }

    private void rebuildInspector(int px, int pz, int res) {
        inspectPx = px;
        inspectPz = pz;
        inspectVersion = previewVersion;
        int index = pz * res + px;
        int worldX = (int) Math.round(state.worldX(px));
        int worldZ = (int) Math.round(state.worldZ(pz));
        boolean land = landGrid != null && index < landGrid.length && landGrid[index] != 0;
        double continentalness = continentalGrid != null && index < continentalGrid.length
            ? continentalGrid[index] : (land ? 0.30 : -0.50);
        int elev = elevationGrid != null && index < elevationGrid.length
            ? Math.round(elevationGrid[index])
            : (int) Math.round(com.reystin.oneisle.continent.IslandTerrain
                .continentalnessToElevation(continentalness));
        byte tier = state.tier(px, pz);
        double dist = currentTerrain != null
            ? currentTerrain.signedDistance(worldX, worldZ)
            : (land ? 100.0 : -100.0);
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
        com.reystin.oneisle.continent.ShoreSettings shore = state.shoreSettings();
        int shoreOutcome = com.reystin.oneisle.continent.ShoreField.NONE;
        if (shoreAllowed && shore != null && shore.enabled() && currentTerrain != null
            && dist >= com.reystin.oneisle.continent.ShoreField.intertidalLimit(
                worldX, worldZ, state.biomeSeed())
            && dist <= com.reystin.oneisle.continent.ShoreField.MAX_INLAND) {
            com.reystin.oneisle.continent.ShoreField.Ring ring =
                com.reystin.oneisle.continent.ShoreField.ring(currentTerrain, worldX, worldZ, dist);
            shoreOutcome = com.reystin.oneisle.continent.ShoreField.outcome(
                dist, worldX, worldZ, slope, currentTerrain.heightTierAt(worldX, worldZ),
                continentalness, ring, shore, state.biomeSeed());
        }
        BiomePreview.Type biome = TYPES[0];
        if (biomeGrid != null && index < biomeGrid.length) {
            int ordinal = biomeGrid[index] & 0xFFFF;
            if (ordinal < TYPES.length) biome = TYPES[ordinal];
        }
        String coastStr = (dist >= 0 ? "+" : "") + (int) Math.round(dist) + "m";
        String isletKey;
        if (land) {
            isletKey = "oneisle.editor.inspector.mainland";
        } else if (dist < -400.0) {
            isletKey = "oneisle.editor.inspector.deep_ocean";
        } else if (continentalness >= -0.15) {
            isletKey = "oneisle.editor.inspector.satellite_islet";
        } else {
            isletKey = "oneisle.editor.inspector.coastal_water";
        }
        String shoreKey = shoreOutcome == com.reystin.oneisle.continent.ShoreField.SAND
            ? "oneisle.editor.inspector.shore.sand"
            : shoreOutcome == com.reystin.oneisle.continent.ShoreField.STONY
                ? "oneisle.editor.inspector.shore.stony"
                : "oneisle.editor.inspector.shore.none";
        inspectName = text(biome.translationKey());
        inspectCoords = text("oneisle.editor.inspector.coords", worldX, worldZ, elev);
        inspectStatus = text("oneisle.editor.inspector.status",
            text(land ? "oneisle.editor.inspector.land" : "oneisle.editor.inspector.water"),
            coastStr, text(isletKey));
        inspectTier = text("oneisle.editor.inspector.tier",
            text("oneisle.editor.tier_name." + (tier & 3)));
        inspectShore = text("oneisle.editor.inspector.shore", text(shoreKey));
        inspectColor = biome.color;
        inspectLand = land;
        inspectWidth = Math.max(86, Math.max(OneMetrics.width(inspectName) + 24,
            Math.max(OneMetrics.width(inspectCoords),
                Math.max(OneMetrics.width(inspectStatus),
                    Math.max(OneMetrics.width(inspectTier), OneMetrics.width(inspectShore))))) + 10);
    }

    private void updateHoverTip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (dropdown != null || popover != null) {
            tooltip.clear();
            return;
        }
        long now = Util.getMillis();
        Tip hovered = null;
        for (int i = activeTips.size() - 1; i >= 0; i--) {
            Tip tip = activeTips.get(i);
            AbstractWidget area = tip.area();
            if (area != null && area.visible && area.active && area.isMouseOver(mouseX, mouseY)) {
                hovered = tip;
                break;
            }
        }
        if (hovered != null) {
            tooltip.hover(hovered.owner(), hovered.title(), hovered.description(),
                hovered.shortcut(), now, font);
        } else if (list != null && list.isMouseOver(mouseX, mouseY) && list.hoveredRow() != null) {
            OneBiomeList.Row row = list.hoveredRow();
            if (row != hoverTipRow || hoverTipVersion != previewVersion) {
                hoverTipRow = row;
                hoverTipVersion = previewVersion;
                hoverTipText = buildRowTip(row);
            }
            tooltip.hover(row, row.name, hoverTipText, "", now, font);
        } else if (tabBar != null && tabBar.isMouseOver(mouseX, mouseY) && layout.iconsOnly()) {
            int index = tabBar.tabAt(mouseX, mouseY);
            if (index >= 0) tooltip.hover(tabBar, tabBar.label(index), "", "", now, font);
            else tooltip.clear();
        } else {
            tooltip.clear();
        }
        if (tooltip.visible(now)) {
            EditorLayout.placeTooltip(screenRect, mouseX, mouseY, tooltip.width(), tooltip.height(), tipOut);
            tooltip.render(g, font, tipOut[0], tipOut[1]);
        }
    }

    private String buildRowTip(OneBiomeList.Row row) {
        if (showStructures) {
            return text(row.enabled ? "oneisle.ui.structure.on" : "oneisle.ui.structure.off");
        }
        int index = -1;
        for (int i = 0; i < rowCache.size(); i++) {
            if (rowCache.get(i) == row) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= filteredBiomes.size()) return text("oneisle.editor.weights.field.tooltip");
        BiomePreview.Type type = filteredBiomes.get(index);
        if (!row.enabled) return text("oneisle.editor.weights.field.tooltip");
        if (!type.isLand()) return text("oneisle.editor.weights.climate_only");
        if (BiomeShare.isTerrainDriven(type.family)) return text("oneisle.editor.weights.terrain.tooltip");
        double target = state.targetShare(type);
        double achieved = state.achievedShare(type);
        if (target >= 0.0 && achieved >= 0.0) {
            return text("oneisle.editor.weights.actual.tooltip",
                String.format(Locale.ROOT, "%.1f%%", achieved),
                String.format(Locale.ROOT, "%.1f%%", target));
        }
        double preview = previewShare(type);
        if (preview >= 0.0) {
            return text("oneisle.editor.weights.preview.tooltip",
                String.format(Locale.ROOT, "%.1f%%", preview));
        }
        return text("oneisle.editor.weights.field.tooltip");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (perfCapturing) perfFrameStart = System.nanoTime();
        super.extractBackground(context, mouseX, mouseY, delta);
        tickShares();
        if (pendingToast != null) {
            toasts.push(pendingToast, pendingToastKind, Util.getMillis());
            pendingToast = null;
        }
        refreshPreviewTexture();
        if (layout == null) return;
        if (layout.minimal()) {
            drawMinimalNotice(context);
            return;
        }
        drawChrome(context);
        updateStatusLine(mouseX, mouseY);
        OneUi.textFit(context, font, statusLine, layout.statusText().x(),
            OneUi.verticalCenter(font, layout.status().y(), layout.status().height() - 1),
            layout.statusText().width(), OneTheme.MUTED);
    }

    private void drawMinimalNotice(GuiGraphicsExtractor g) {
        g.fill(0, 0, width, height, OneTheme.BG);
        EditorLayout.Rect notice = layout.notice();
        OneUi.panel(g, notice.x(), notice.y(), notice.width(), notice.height(),
            OneTheme.PANEL, OneTheme.BORDER);
        OneUi.textCentered(g, font, text("oneisle.ui.too_small"), notice.centerX(),
            notice.y() + 10, notice.width() - 16, OneTheme.ACCENT);
        int y = notice.y() + 26;
        int bottom = layout.done().y() - 12;
        for (FormattedCharSequence line : noticeLines) {
            if (y + 10 > bottom) break;
            g.text(font, line, notice.x() + 12, y, OneTheme.MUTED);
            y += 10;
        }
        String size = text("oneisle.ui.actual_size", width, height);
        OneUi.textCentered(g, font, size, notice.centerX(), Math.min(y + 2, bottom - 2),
            notice.width() - 16, OneTheme.TEXT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long perfStart = perfCapturing ? System.nanoTime() : 0L;
        refreshPreviewTexture();
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (layout == null) {
            recordPerf(perfStart);
            return;
        }
        if (layout.minimal()) {
            toasts.render(context, font, width - 8, height - 8, Util.getMillis());
            recordPerf(perfStart);
            return;
        }
        if (listSharesDirty) {
            listSharesDirty = false;
            if (list != null) updateRowValues();
        }
        if (!saving && isInsidePreview(mouseX, mouseY)) {
            drawBrushCursor(context, mouseX, mouseY);
            drawInspector(context, mouseX, mouseY);
        }
        updateHoverTip(context, mouseX, mouseY);
        toasts.render(context, font, width - 8, layout.status().y() - 8, Util.getMillis());
        recordPerf(perfStart);
    }

    private static void recordPerf(long fallbackStart) {
        if (!perfCapturing) return;
        long start = perfFrameStart != 0L ? perfFrameStart : fallbackStart;
        perfFrameStart = 0L;
        if (start != 0L && perfSampleCount < PERF_SAMPLES.length) {
            PERF_SAMPLES[perfSampleCount++] = System.nanoTime() - start;
        }
    }

    /** Dev harness: measures background + foreground extraction time per frame. */
    public static void beginPerfCapture() {
        perfSampleCount = 0;
        perfFrameStart = 0L;
        perfCapturing = true;
    }

    /** Dev harness: returns the captured samples in nanoseconds and stops capturing. */
    public static long[] endPerfCapture() {
        perfCapturing = false;
        return Arrays.copyOf(PERF_SAMPLES, perfSampleCount);
    }

    public static int perfSampleCount() {
        return perfSampleCount;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (dropdown != null) {
            if (dropdown.isMouseOver(event.x(), event.y())) dropdown.mouseClicked(event, doubleClick);
            else closeDropdown();
            return true;
        }
        if (popover != null) {
            if (popover.isMouseOver(event.x(), event.y())) popover.mouseClicked(event, doubleClick);
            else closePopover();
            return true;
        }
        if (layout == null || layout.minimal()) return super.mouseClicked(event, doubleClick);
        if (weightEditingType != null) commitWeightEdit();
        if (!saving && isInsidePreview(event.x(), event.y())) {
            return handleCanvasClick(event);
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleCanvasClick(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
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
                    invalidatePreview();
                } else {
                    waterClickFlash = System.currentTimeMillis();
                    toasts.push(text("oneisle.editor.spawn.water_error"), OneToast.Kind.ERROR,
                        Util.getMillis());
                }
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                state.clearSpawn();
                invalidatePreview();
            }
            return true;
        }

        if (tool == Tool.FILL) {
            state.pushUndo();
            if (layer == Layer.LAND) {
                boolean target = button != InputConstants.MOUSE_BUTTON_RIGHT;
                state.floodFillLand(px, pz, target, symmetry);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        if (dropdown != null) {
            if (dropdown.isMouseOver(mouseX, mouseY)) dropdown.mouseScrolled(mouseX, mouseY,
                horizontalAmount, verticalAmount);
            return true;
        }
        if (popover != null) return true;
        if (layout == null || layout.minimal()) return super.mouseScrolled(
            mouseX, mouseY, horizontalAmount, verticalAmount);
        if (controlsPanel != null && controlsPanel.scrollable()
            && controlsPanel.isMouseOver(mouseX, mouseY)) {
            if (controlsPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        if (list != null && list.isMouseOver(mouseX, mouseY)) {
            return list.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        if (dropdown != null || popover != null) return true;
        if (layout == null || layout.minimal()) return super.mouseDragged(event, deltaX, deltaY);
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
            if (tool == Tool.SPAWN || tool == Tool.FILL) return false;
            if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) {
                applyBrush(mouseX, mouseY,
                    button == InputConstants.MOUSE_BUTTON_RIGHT ? Tool.ERASER : tool);
                return true;
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dropdown != null || popover != null) return true;
        if (layout == null || layout.minimal()) return super.mouseReleased(event);
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
                    state.drawRect(dragStartX, dragStartZ, endX, endZ, shapeFilled, brushRadius,
                        value, symmetry);
                } else {
                    state.drawRectHeight(dragStartX, dragStartZ, endX, endZ, shapeFilled,
                        brushRadius, tier, symmetry);
                }
            } else if (tool == Tool.ELLIPSE) {
                if (layer == Layer.LAND) {
                    state.drawEllipse(dragStartX, dragStartZ, endX, endZ, shapeFilled, brushRadius,
                        value, symmetry);
                } else {
                    state.drawEllipseHeight(dragStartX, dragStartZ, endX, endZ, shapeFilled,
                        brushRadius, tier, symmetry);
                }
            }
            isDraggingShape = false;
            dragStartX = -1;
            dragStartZ = -1;
            invalidatePreview();
            updateTopBar();
            return true;
        }
        lastPaintX = -1;
        lastPaintZ = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (dropdown != null) return dropdown.keyPressed(event);
        if (popover != null) return popover.keyPressed(event);
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
                    syncEditingRow();
                }
                return true;
            }
            int digit = -1;
            if (key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9) {
                digit = key - InputConstants.KEY_0;
            } else if (key >= 320 && key <= 329) {
                digit = key - 320;
            }
            if (digit >= 0) {
                if (weightEditBuffer.length() < 3) {
                    weightEditBuffer += digit;
                    syncEditingRow();
                }
                return true;
            }
            return true;
        }
        if (event.hasControlDown()) {
            if (event.key() == InputConstants.KEY_Z) {
                if (event.hasShiftDown()) redo();
                else undo();
                return true;
            }
            if (event.key() == InputConstants.KEY_Y) {
                redo();
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_ESCAPE && tooltip.hasOwner()) {
            tooltip.clear();
            return true;
        }
        return super.keyPressed(event);
    }

    private void applyBrush(double mouseX, double mouseY, Tool selectedTool) {
        int px = sourcePixelX(mouseX);
        int pz = sourcePixelZ(mouseY);
        if (lastPaintX >= 0) {
            int dx = px - lastPaintX;
            int dz = pz - lastPaintZ;
            int steps = Math.max(Math.abs(dx), Math.abs(dz));
            for (int i = 1; i <= steps; i++) {
                applyAt(lastPaintX + dx * i / steps, lastPaintZ + dz * i / steps, selectedTool);
            }
        } else {
            applyAt(px, pz, selectedTool);
        }
        lastPaintX = px;
        lastPaintZ = pz;
        invalidatePreview();
    }

    private void applyAt(int px, int pz, Tool selectedTool) {
        if (layer == Layer.LAND) {
            if (selectedTool == Tool.SMOOTH) {
                state.smooth(px, pz, brushRadius, symmetry);
            } else {
                state.paint(px, pz, brushRadius, selectedTool == Tool.LAND, symmetry);
            }
        } else {
            if (heightSmooth) {
                state.smoothHeight(px, pz, brushRadius, symmetry);
            } else {
                int tier = selectedTool == Tool.ERASER ? 1 : activeTier;
                state.paintHeight(px, pz, brushRadius, tier, symmetry);
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

    private void commitDesign() {
        if (saving) return;
        commitWeightEdit();
        if (!state.hasLand()) {
            toasts.push(text("oneisle.editor.error.empty"), OneToast.Kind.ERROR, Util.getMillis());
            return;
        }
        if (createParent == null) return;
        Pair<Path, PackRepository> pair = ((CreateWorldBridge) createParent).oneisle$getScannedPack(
            createParent.getUiState().getSettings().dataConfiguration());
        if (pair == null) {
            toasts.push(text("oneisle.editor.error.datapack"), OneToast.Kind.ERROR, Util.getMillis());
            return;
        }
        saving = true;
        updateTopBar();
        IslandDraft snapshot = state.copy();
        var loadContext = createParent.getUiState().getSettings() == null
            ? null : createParent.getUiState().getSettings().worldgenLoadContext();
        CompletableFuture.runAsync(() -> {
            try {
                WorldPackBuilder.export(pair.getFirst(), snapshot);
                if (loadContext != null) WorldPackBuilder.validatePreset(pair.getFirst(), loadContext);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((ignored, throwable) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (throwable != null) {
                    saving = false;
                    updateTopBar();
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    String message;
                    if (cause instanceof WorldPackBuilder.PresetValidationException) {
                        message = text("oneisle.editor.error.preset", cause.getMessage());
                    } else {
                        message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    }
                    toasts.push(message, OneToast.Kind.ERROR, Util.getMillis());
                    return;
                }
                PackRepository repository = pair.getSecond();
                repository.reload();
                String generatedId = repository.getAvailableIds().stream()
                    .filter(id -> id.endsWith(WorldPackBuilder.FILE_NAME)).findFirst().orElse(null);
                if (generatedId == null
                    || (!repository.getSelectedIds().contains(generatedId)
                        && !repository.addPack(generatedId))) {
                    saving = false;
                    updateTopBar();
                    toasts.push(text("oneisle.editor.error.datapack"), OneToast.Kind.ERROR,
                        Util.getMillis());
                    return;
                }
                EditorDraftStore.saveDraft(createParent, snapshot);
                ((CreateWorldBridge) createParent).oneisle$applyPack(repository, true,
                    configuration -> ClientScreenNavigator.open(minecraft, createParent));
            });
        });
    }

    @Override
    public void onClose() {
        commitWeightEdit();
        if (minecraft != null) ClientScreenNavigator.open(minecraft, parentScreen);
    }

    @Override
    public void removed() {
        previewSlot.release(this::releasePreviewTexture);
    }

    private static boolean isShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    private static boolean isSpaceDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_SPACE);
    }

    private static Component categoryName(BiomePreview.Category category) {
        if (category == null) return Component.translatable("oneisle.category.all");
        return switch (category) {
            case LAND -> Component.translatable("oneisle.category.land");
            case SHORE -> Component.translatable("oneisle.category.shore");
            case OCEAN -> Component.translatable("oneisle.category.ocean");
            case RIVER -> Component.translatable("oneisle.category.river");
            case CAVE -> Component.translatable("oneisle.category.cave");
        };
    }
}
