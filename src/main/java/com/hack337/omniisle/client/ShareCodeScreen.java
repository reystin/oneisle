package com.hack337.omniisle.client;

import com.hack337.omniisle.client.ui.OmniButton;
import com.hack337.omniisle.client.ui.OmniMetrics;
import com.hack337.omniisle.client.ui.OmniTextField;
import com.hack337.omniisle.client.ui.OmniTheme;
import com.hack337.omniisle.client.ui.OmniToast;
import com.hack337.omniisle.client.ui.OmniUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Copy and import OMNI2 share codes with the flat editor chrome. */
public final class ShareCodeScreen extends Screen {
    private final OmniIsleEditorScreen parent;
    private final IslandDraft draft;
    private OmniTextField codeField;
    private String statusText = "";
    private boolean statusError;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int pad;
    private int statusY;
    private String titleText = "";

    public ShareCodeScreen(OmniIsleEditorScreen parent, IslandDraft draft) {
        super(Component.translatable("omniisle.editor.share_code"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        clearWidgets();
        OmniMetrics.tick(font);
        titleText = text("omniisle.editor.share_code");

        panelW = Math.min(420, width - 16);
        panelH = Math.min(200, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        pad = panelW < 280 ? 8 : 10;
        int innerX = panelX + pad;
        int innerW = panelW - pad * 2;

        String currentCode = "";
        try {
            currentCode = ShareCode.encode(draft);
        } catch (Exception e) {
            statusError = true;
            statusText = text("omniisle.editor.share_code_encode_failed", message(e));
        }

        codeField = new OmniTextField(font, innerX, panelY + 28, innerW, 22,
            text("omniisle.editor.share_code"), currentCode, null);
        codeField.setMaxLength(8192);
        addRenderableWidget(codeField.box());
        addRenderableWidget(codeField);

        int backY = panelY + panelH - pad - 22;
        int buttonsY = backY - 6 - 22;
        statusY = buttonsY - 16;
        int half = (innerW - 6) / 2;

        addRenderableWidget(new OmniButton(innerX, buttonsY, half, 22,
            text("omniisle.editor.share_code_copy"), OmniButton.Style.PRIMARY, () -> {
                if (minecraft != null) {
                    minecraft.keyboardHandler.setClipboard(codeField.text());
                }
                statusError = false;
                statusText = text("omniisle.editor.share_code_copied");
                parent.queueToast(statusText, OmniToast.Kind.SUCCESS);
            }));
        addRenderableWidget(new OmniButton(innerX + half + 6, buttonsY, innerW - half - 6, 22,
            text("omniisle.editor.share_code_import"), OmniButton.Style.SECONDARY, this::importCode));
        addRenderableWidget(new OmniButton(innerX + innerW - Math.min(120, innerW), backY,
            Math.min(120, innerW), 22, text("gui.back"), OmniButton.Style.SECONDARY, this::onClose));
    }

    private void importCode() {
        try {
            IslandDraft decoded = ShareCode.decode(codeField.text().trim());
            draft.setResolution(decoded.resolution());
            draft.setIslandSize(decoded.islandSize());
            draft.setShapeSeed(decoded.shapeSeed());
            draft.setBiomeSeed(decoded.biomeSeed());
            draft.setPreset(decoded.preset());
            draft.setRelief(decoded.relief());
            draft.applyTheme(decoded.theme());
            draft.setGenerationVersion(decoded.generationVersion());
            draft.setIsletDensity(decoded.isletDensity());
            draft.setStructureDensity(decoded.structureDensity());
            draft.setClimateScale(decoded.climateScale());
            draft.setGradientMode(decoded.gradientMode());
            draft.setGradientStrength(decoded.gradientStrength());
            draft.unpackMask(decoded.packedMask());
            if (decoded.hasCustomHeight()) {
                draft.unpackHeight(decoded.packedHeight());
            } else {
                draft.clearHeight();
            }
            if (decoded.hasExplicitSpawn()) {
                draft.setSpawn(decoded.explicitSpawnPx(), decoded.explicitSpawnPz());
            } else {
                draft.clearSpawn();
            }
            parent.refreshPreview();
            parent.queueToast(text("omniisle.editor.share_code_imported"), OmniToast.Kind.SUCCESS);
            ClientScreenNavigator.open(minecraft, parent);
        } catch (Exception e) {
            statusError = true;
            statusText = text("omniisle.editor.share_code_failed", message(e));
        }
    }

    private static String message(Exception error) {
        String raw = error.getMessage();
        return raw == null || raw.isBlank() ? text("omniisle.editor.share_code_invalid") : raw;
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
