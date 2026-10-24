package com.hack337.omniisle.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen for copying and importing OmniIsle share codes (OMNI2-...).
 */
public final class ShareCodeScreen extends Screen {
    private final OmniIsleEditorScreen parent;
    private final IslandDraft draft;
    private EditBox codeBox;
    private Component status = Component.empty();
    private boolean isError = false;

    public ShareCodeScreen(OmniIsleEditorScreen parent, IslandDraft draft) {
        super(Component.translatable("omniisle.editor.share_code"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, width - 40);
        int panelHeight = 160;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        String currentCode = "";
        try {
            currentCode = ShareCode.encode(draft);
        } catch (Exception e) {
            currentCode = "Error generating code: " + e.getMessage();
        }

        codeBox = new EditBox(font, panelX + 16, panelY + 40, panelWidth - 32, 20, Component.literal("Code"));
        codeBox.setMaxLength(8192);
        codeBox.setValue(currentCode);
        addRenderableWidget(codeBox);

        // Copy button
        addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.share_code_copy"), b -> {
            minecraft.keyboardHandler.setClipboard(codeBox.getValue());
            status = Component.translatable("omniisle.editor.share_code_copied");
            isError = false;
        }).bounds(panelX + 16, panelY + 70, (panelWidth - 38) / 2, 20).build());

        // Import button
        addRenderableWidget(Button.builder(Component.translatable("omniisle.editor.share_code_import"), b -> {
            String text = codeBox.getValue().trim();
            try {
                IslandDraft decoded = ShareCode.decode(text);
                // Copy properties into draft
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
                ClientScreenNavigator.open(minecraft, parent);
            } catch (Exception e) {
                status = Component.literal(e.getMessage() != null ? e.getMessage() : "Invalid share code");
                isError = true;
            }
        }).bounds(panelX + 22 + (panelWidth - 38) / 2, panelY + 70, (panelWidth - 38) / 2, 20).build());

        // Back button
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> {
            ClientScreenNavigator.open(minecraft, parent);
        }).bounds(panelX + 16, panelY + 115, panelWidth - 32, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x50000000);

        int panelWidth = Math.min(360, width - 40);
        int panelHeight = 160;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF018201E);
        context.outline(panelX, panelY, panelWidth, panelHeight, 0xFF65786C);
        context.centeredText(font, title, width / 2, panelY + 14, 0xFFE0E8E2);

        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2, panelY + 98, isError ? 0xFFFF6666 : 0xFF88FF88);
        }
    }

    @Override
    public void onClose() {
        ClientScreenNavigator.open(minecraft, parent);
    }
}
