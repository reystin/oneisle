package com.reystin.oneisle.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Collapsible section header; open state is owned by the screen and survives tab switches. */
public class OneSection extends OneWidget {
    private String label;
    private boolean open;
    private Consumer<Boolean> onChange;

    public OneSection(int x, int y, int width, int height, String label, boolean open, Consumer<Boolean> onChange) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.open = open;
        this.onChange = onChange;
    }

    public boolean open() { return open; }

    public OneSection setOpen(boolean open) {
        this.open = open;
        return this;
    }

    public OneSection setLabel(String label) {
        this.label = label;
        setMessage(Component.literal(label));
        return this;
    }

    public OneSection setOnChange(Consumer<Boolean> onChange) {
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
            g.fill(x, y, x + w, y + h, OneTheme.withAlpha(OneTheme.RAISED, hover * 0.8F * alpha));
        }
        int centerY = y + h / 2;
        int color = OneTheme.withAlpha(OneTheme.MUTED, alpha);
        if (open) {
            g.fill(x + 3, centerY - 2, x + 4, centerY + 1, color);
            g.fill(x + 4, centerY - 1, x + 5, centerY, color);
            g.fill(x + 5, centerY - 2, x + 6, centerY + 1, color);
        } else {
            g.fill(x + 4, centerY - 2, x + 5, centerY + 2, color);
            g.fill(x + 5, centerY - 1, x + 6, centerY + 1, color);
            g.fill(x + 6, centerY, x + 7, centerY + 1, color);
        }
        OneUi.textFit(g, font, label, x + 10, OneUi.verticalCenter(font, y, h),
            w - 14, OneTheme.withAlpha(OneTheme.TEXT, alpha));
        if (focus > 0.02F) {
            g.outline(x, y, w, h, OneTheme.withAlpha(OneTheme.FOCUS, focus * alpha));
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
            Component.translatable(open ? "oneisle.ui.expanded" : "oneisle.ui.collapsed"));
    }
}
