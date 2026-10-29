package com.hack337.omniisle.client;

import com.hack337.omniisle.client.ui.OmniButton;
import com.hack337.omniisle.client.ui.OmniControlsPanel;
import com.hack337.omniisle.client.ui.OmniMetrics;
import com.hack337.omniisle.client.ui.OmniSegmented;
import com.hack337.omniisle.client.ui.OmniSlider;
import com.hack337.omniisle.client.ui.OmniTextField;
import com.hack337.omniisle.client.ui.OmniTheme;
import com.hack337.omniisle.client.ui.OmniToast;
import com.hack337.omniisle.client.ui.OmniToggle;
import com.hack337.omniisle.client.ui.OmniUi;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Export PNG maps and import masks or heightmaps with the flat editor chrome. */
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
    private OmniTextField fileNameBox;
    private int threshold = 128;
    private boolean invert;
    private ImportMode importMode = ImportMode.MASK;
    private String statusText = "";
    private boolean statusError;

    private OmniControlsPanel controls;
    private OmniSlider thresholdSlider;
    private final List<AbstractWidget> content = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int pad;
    private int statusY;
    private String titleText = "";

    public PngToolsScreen(OmniIsleEditorScreen parent, IslandDraft draft,
                          com.hack337.omniisle.continent.IslandTerrain terrain) {
        super(Component.translatable("omniisle.editor.import_export"));
        this.parent = parent;
        this.draft = draft;
        this.terrain = terrain;
    }

    @Override
    protected void init() {
        clearWidgets();
        content.clear();
        OmniMetrics.tick(font);
        titleText = text("omniisle.editor.import_export");

        panelW = Math.min(440, width - 16);
        panelH = Math.min(300, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        pad = panelW < 300 ? 6 : 8;
        int innerX = panelX + pad;
        int innerW = panelW - pad * 2;
        int footerY = panelY + panelH - pad - 22;
        statusY = footerY - 12;
        int controlsY = panelY + 24;
        int controlsH = Math.max(18, statusY - 4 - controlsY);
        controls = addRenderableWidget(new OmniControlsPanel(innerX, controlsY, innerW, controlsH));

        int rowX = innerX + 4;
        int rowW = Math.max(20, innerW - 10);
        int y = controlsY + 4;
        int rowH = 22;

        addControl(new OmniButton(rowX, y, rowW, rowH,
            text("omniisle.editor.export_png"), OmniButton.Style.PRIMARY, this::exportAll));
        y += rowH + 4;

        Path importsDir = importsDir();
        String autofill = "";
        try {
            if (importsDir != null && Files.isDirectory(importsDir)) {
                try (var stream = Files.list(importsDir)) {
                    var first = stream.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                        .findFirst();
                    if (first.isPresent()) autofill = first.get().getFileName().toString();
                }
            }
        } catch (Exception ignored) {
        }
        fileNameBox = new OmniTextField(font, rowX, y, rowW, rowH,
            text("omniisle.editor.png_hint"), autofill, null);
        controls.add(fileNameBox);
        controls.add(fileNameBox.box());
        content.add(fileNameBox);
        content.add(fileNameBox.box());
        y += rowH + 4;

        OmniSegmented mode = new OmniSegmented(rowX, y, rowW, rowH, new String[]{
            text("omniisle.editor.png_mode.mask"), text("omniisle.editor.png_mode.heightmap")
        }, importMode.ordinal(), index -> importMode = ImportMode.values()[index]);
        addControl(mode);
        y += rowH + 4;

        addControl(new OmniToggle(rowX, y, rowW, rowH, text("omniisle.editor.png_invert"),
            invert, value -> invert = value));
        y += rowH + 4;

        OmniSlider thresholdSlider = new OmniSlider(rowX, y, rowW, rowH,
            text("omniisle.ui.threshold"), threshold / 255.0, 1.0 / 255.0, 128.0 / 255.0,
            value -> {
                threshold = (int) Math.round(value * 255.0);
                thresholdSliderValue(threshold);
            });
        this.thresholdSlider = thresholdSlider;
        thresholdSliderValue(threshold);
        addControl(thresholdSlider);

        controls.setActive(content);

        int half = (innerW - 6) / 2;
        addRenderableWidget(new OmniButton(innerX, footerY, half, 22,
            text("omniisle.editor.png_import_button"), OmniButton.Style.PRIMARY, this::importPng));
        addRenderableWidget(new OmniButton(innerX + half + 6, footerY, innerW - half - 6, 22,
            text("gui.back"), OmniButton.Style.SECONDARY, this::onClose));
    }

    private void thresholdSliderValue(int value) {
        if (thresholdSlider != null) {
            thresholdSlider.setValueText(text("omniisle.ui.threshold_value", value));
        }
    }

    private void addControl(AbstractWidget widget) {
        controls.add(widget);
        content.add(widget);
    }

    private Path importsDir() {
        if (minecraft == null) return null;
        return minecraft.gameDirectory.toPath().resolve("omniisle").resolve("imports");
    }

    private void exportAll() {
        try {
            ImageImportExport.exportAll(draft, terrain, minecraft.gameDirectory.toPath());
            statusError = false;
            statusText = text("omniisle.editor.png_exported");
            parent.queueToast(statusText, OmniToast.Kind.SUCCESS);
        } catch (Exception e) {
            statusError = true;
            statusText = text("omniisle.editor.png_export_failed", message(e));
            parent.queueToast(statusText, OmniToast.Kind.ERROR);
        }
    }

    private void importPng() {
        String input = fileNameBox.text().trim();
        if (input.isEmpty()) {
            statusError = true;
            statusText = text("omniisle.editor.png_enter_name");
            return;
        }
        Path file = importsDir() == null ? null : importsDir().resolve(input);
        if (file == null || !Files.exists(file)) {
            file = Path.of(input);
        }
        if (!Files.exists(file)) {
            statusError = true;
            statusText = text("omniisle.editor.png_not_found", input);
            return;
        }
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                statusError = true;
                statusText = text("omniisle.editor.png_invalid");
                return;
            }
            draft.pushUndo();
            int res = draft.resolution();
            if (importMode == ImportMode.MASK) {
                boolean[] land = ImageImportExport.importMask(image, res, threshold, invert);
                for (int z = 0; z < res; z++) {
                    for (int x = 0; x < res; x++) {
                        draft.setLand(x, z, land[z * res + x]);
                    }
                }
            } else {
                ImageImportExport.ImportedDraftData data =
                    ImageImportExport.importHeightmap(image, res, threshold, invert);
                for (int z = 0; z < res; z++) {
                    for (int x = 0; x < res; x++) {
                        draft.setLand(x, z, data.land()[z * res + x]);
                        draft.setTier(x, z, data.height()[z * res + x]);
                    }
                }
            }
            parent.refreshPreview();
            parent.queueToast(text("omniisle.editor.png_imported"), OmniToast.Kind.SUCCESS);
            ClientScreenNavigator.open(minecraft, parent);
        } catch (Exception e) {
            statusError = true;
            statusText = text("omniisle.editor.png_import_failed", message(e));
            parent.queueToast(statusText, OmniToast.Kind.ERROR);
        }
    }

    private static String message(Exception error) {
        String raw = error.getMessage();
        return raw == null || raw.isBlank() ? "?" : raw;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        g.fill(0, 0, width, height, OmniTheme.OVERLAY);
        OmniUi.panel(g, panelX, panelY, panelW, panelH, OmniTheme.PANEL, OmniTheme.BORDER);
        OmniUi.textFit(g, font, titleText, panelX + pad, panelY + 9,
            panelW - pad * 2, OmniTheme.TEXT);
        OmniUi.divider(g, panelX + pad, panelY + 21, panelW - pad * 2);
        if (!statusText.isEmpty()) {
            int color = statusError ? OmniTheme.DANGER : OmniTheme.OK;
            OmniUi.statusGlyph(g, statusError ? OmniTheme.Status.ERROR : OmniTheme.Status.OK,
                panelX + pad, statusY + 1, 8, color);
            OmniUi.textFit(g, font, statusText, panelX + pad + 11, statusY,
                panelW - pad * 2 - 11, color);
        }
    }

    @Override
    public void onClose() {
        ClientScreenNavigator.open(minecraft, parent);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
