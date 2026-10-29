package com.hack337.omniisle.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Collapsible section header; open state is owned by the screen and survives tab switches. */
public class OmniSection extends OmniWidget {
    private String label;
    private boolean open;
    private Consumer<Boolean> onChange;

    public OmniSection(int x, int y, int width, int height, String label, boolean open, Consumer<Boolean> onChange) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.open = open;
        this.onChange = onChange;
    }

    public boolean open() { return open; }

    public OmniSection setOpen(boolean open) {
        this.open = open;
        return this;
    }

    public OmniSection setLabel(String label) {
        this.label = label;
        setMessage(Component.literal(label));
        return this;
    }

    public OmniSection setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        float alpha = active ? 1.0F : 0.5F;
        if (hover > 0.02F) {
            g.fill(x, y, x + w, y + h, OmniTheme.withAlpha(OmniTheme.RAISED, hover * 0.8F * alpha));
        }
        int centerY = y + h / 2;
        int color = OmniTheme.withAlpha(OmniTheme.MUTED, alpha);
        if (open) {
            g.fill(x + 3, centerY - 2, x + 4, centerY + 1, color);
            g.fill(x + 4, centerY - 1, x + 5, centerY, color);
            g.fill(x + 5, centerY - 2, x + 6, centerY + 1, color);
        } else {
            g.fill(x + 4, centerY - 2, x + 5, centerY + 2, color);
            g.fill(x + 5, centerY - 1, x + 6, centerY + 1, color);
            g.fill(x + 6, centerY, x + 7, centerY + 1, color);
        }
        OmniUi.textFit(g, font, label, x + 10, OmniUi.verticalCenter(font, y, h),
            w - 14, OmniTheme.withAlpha(OmniTheme.TEXT, alpha));
        if (focus > 0.02F) {
            g.outline(x, y, w, h, OmniTheme.withAlpha(OmniTheme.FOCUS, focus * alpha));
        }
    }

    private void toggle() {
        open = !open;
        if (onChange != null) onChange.accept(open);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) toggle();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isFocused() && active && activateKey(event)) {
            toggle();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(label));
        output.add(NarratedElementType.HINT,
            Component.translatable(open ? "omniisle.ui.expanded" : "omniisle.ui.collapsed"));
    }
}
