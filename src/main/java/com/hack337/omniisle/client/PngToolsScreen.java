package com.hack337.omniisle.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen for exporting PNG masks, biomes, height layers, and hillshade,
 * and importing PNG files into OmniIsle drafts.
 */
public final class PngToolsScreen extends Screen {
    public enum ImportMode {
        MASK("omniisle.editor.png_mode.mask"),
        HEIGHTMAP("omniisle.editor.png_mode.heightmap");

        public final String translationKey;
        ImportMode(String key) { this.translationKey = key; }
    }

    private final OmniIsleEditorScreen parent;
    private final IslandDraft draft;
    private final com.hack337.omniisle.continent.IslandTerrain terrain;
    private EditBox fileNameBox;
    private int threshold = 128;
    private boolean invert = false;
    private ImportMode importMode = ImportMode.MASK;
    private Component status = Component.empty();
    private boolean isError = false;

    public PngToolsScreen(OmniIsleEditorScreen parent, IslandDraft draft, com.hack337.omniisle.continent.IslandTerrain terrain) {
        super(Component.translatable("omniisle.editor.import_export"));
        this.parent = parent;
        this.draft = draft;
        this.terrain = terrain;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(380, width - 30);
        int panelHeight = 220;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        int y = panelY + 32;

        // Export All button
        addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.export_png"), b -> {
            try {
                Path gameDir = minecraft.gameDirectory.toPath();
                Path out = ImageImportExport.exportAll(draft, terrain, gameDir);
                status = Component.translatable("omniisle.editor.png_exported");
                isError = false;
            } catch (Exception e) {
                status = Component.literal("Export failed: " + e.getMessage());
                isError = true;
            }
        }).bounds(panelX + 20, y, panelWidth - 40, 20).build());

        y += 30;

        // Import filename / path box
        fileNameBox = new EditBox(font, panelX + 20, y, panelWidth - 40, 20, Component.literal("Filename"));
        fileNameBox.setHint(Component.literal("e.g. island.png in omniisle/imports/"));
        // Autofill first found file if any
        try {
            Path importsDir = minecraft.gameDirectory.toPath().resolve("omniisle").resolve("imports");
            if (Files.isDirectory(importsDir)) {
                try (var stream = Files.list(importsDir)) {
                    stream.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                        .findFirst()
                        .ifPresent(p -> fileNameBox.setValue(p.getFileName().toString()));
                }
            }
        } catch (Exception ignored) {}
        addRenderableWidget(fileNameBox);

        y += 24;

        // Import Mode CycleButton
        CycleButton<ImportMode> modeButton = CycleButton
            .<ImportMode>builder(v -> Component.translatable(v.translationKey), importMode)
            .withValues(List.of(ImportMode.values()))
            .create(panelX + 20, y, (panelWidth - 44) / 2, 20, Component.translatable("omniisle.editor.png_mode"), (b, v) -> {
                importMode = v;
            });
        addRenderableWidget(modeButton);

        // Invert CycleButton
        CycleButton<Boolean> invertButton = CycleButton
            .onOffBuilder(invert)
            .create(panelX + 24 + (panelWidth - 44) / 2, y, (panelWidth - 44) / 2, 20, Component.translatable("omniisle.editor.png_invert"), (b, v) -> {
                invert = v;
            });
        addRenderableWidget(invertButton);

        y += 24;

        // Threshold slider
        addRenderableWidget(new ThresholdSlider(panelX + 20, y, panelWidth - 40, 20));

        y += 26;

        // Import Action Button
        addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.png_import_button"), b -> {
            String input = fileNameBox.getValue().trim();
            if (input.isEmpty()) {
                status = Component.literal("Enter a PNG filename");
                isError = true;
                return;
            }
            try {
                Path importsDir = minecraft.gameDirectory.toPath().resolve("omniisle").resolve("imports");
                Path file = importsDir.resolve(input);
                if (!Files.exists(file)) {
                    file = Path.of(input);
                }
                if (!Files.exists(file)) {
                    status = Component.literal("File not found: " + input);
                    isError = true;
                    return;
                }
                BufferedImage img = ImageIO.read(file.toFile());
                if (img == null) {
                    status = Component.literal("Invalid image file");
                    isError = true;
                    return;
                }
                draft.pushUndo();
                int res = draft.resolution();
                if (importMode == ImportMode.MASK) {
                    boolean[] land = ImageImportExport.importMask(img, res, threshold, invert);
                    for (int z = 0; z < res; z++) {
                        for (int x = 0; x < res; x++) {
                            draft.setLand(x, z, land[z * res + x]);
                        }
                    }
                } else {
                    ImageImportExport.ImportedDraftData data = ImageImportExport.importHeightmap(img, res, threshold, invert);
                    for (int z = 0; z < res; z++) {
                        for (int x = 0; x < res; x++) {
                            draft.setLand(x, z, data.land()[z * res + x]);
                            draft.setTier(x, z, data.height()[z * res + x]);
                        }
                    }
                }
                parent.refreshPreview();
                ClientScreenNavigator.open(minecraft, parent);
            } catch (Exception e) {
                status = Component.literal("Import failed: " + e.getMessage());
                isError = true;
            }
        }).bounds(panelX + 20, y, (panelWidth - 44) / 2, 20).build());

        // Cancel / Back Button
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> {
            ClientScreenNavigator.open(minecraft, parent);
        }).bounds(panelX + 24 + (panelWidth - 44) / 2, y, (panelWidth - 44) / 2, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x50000000);

        int panelWidth = Math.min(380, width - 30);
        int panelHeight = 220;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF018201E);
        context.outline(panelX, panelY, panelWidth, panelHeight, 0xFF65786C);
        context.centeredText(font, title, width / 2, panelY + 12, 0xFFE0E8E2);

        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2, panelY + panelHeight - 16, isError ? 0xFFFF6666 : 0xFF88FF88);
        }
    }

    @Override
    public void onClose() {
        ClientScreenNavigator.open(minecraft, parent);
    }

    private final class ThresholdSlider extends AbstractSliderButton {
        private ThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), threshold / 255.0);
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("omniisle.editor.png_threshold", threshold));
        }
        @Override protected void applyValue() {
            threshold = (int) Math.round(value * 255.0);
            updateMessage();
        }
    }
}
