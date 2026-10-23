package com.hack337.omniisle.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Advanced world generation options for OmniIsle islands. */
final class AdvancedSettingsScreen extends Screen {
    private final OmniIsleEditorScreen owner;
    private final IslandDraft state;

    AdvancedSettingsScreen(OmniIsleEditorScreen owner, IslandDraft state) {
        super(Component.translatable("omniisle.editor.advanced.title"));
        this.owner = owner;
        this.state = state;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(260, width - 40);
        int startX = (width - panelWidth) / 2;
        int y = Math.max(48, height / 2 - 50);

        CycleButton<IslandDraft.GenerationVersion> genButton = CycleButton
            .<IslandDraft.GenerationVersion>builder(val -> Component.translatable(val.translationKey), state.generationVersion())
            .withValues(List.of(IslandDraft.GenerationVersion.values()))
            .withTooltip(val -> Tooltip.create(Component.translatable("omniisle.editor.generation.tooltip")))
            .create(startX, y, panelWidth, 20, Component.translatable("omniisle.editor.generation"), (button, value) -> {
                state.setGenerationVersion(value);
            });
        addRenderableWidget(genButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 - 75, height - 32, 150, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x30000000);
        int panelWidth = Math.min(280, width - 24);
        int panelHeight = Math.min(140, height - 70);
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(36, height / 2 - 62);
        panel(context, panelX, panelY, panelWidth, panelHeight, 0xFF202B2C);
        context.centeredText(font, title, width / 2, panelY + 8, 0xFFF4E8C1);
    }

    @Override
    public void onClose() {
        ClientScreenNavigator.open(minecraft, owner);
    }

    private static void panel(GuiGraphicsExtractor context, int x, int y, int w, int h, int interior) {
        context.fill(x, y, x + w, y + h, 0xFF090B0A);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF788078);
        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF343A35);
        context.fill(x + 4, y + 4, x + w - 4, y + h - 4, interior);
    }
}
