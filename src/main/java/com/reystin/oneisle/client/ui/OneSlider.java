package com.reystin.oneisle.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Thin flat slider with accent fill, value label, wheel/arrow/fine-step/reset support. */
public class OneSlider extends OneWidget {
    private String label;
    private String valueText = "";
    private double value;
    private double step;
    private double resetValue;
    private DoubleConsumer onChange;
    private boolean dragging;

    public OneSlider(int x, int y, int width, int height, String label, double value, double step,
                      double resetValue, DoubleConsumer onChange) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.value = value;
        this.step = step;
        this.resetValue = resetValue;
        this.onChange = onChange;
    }

    public double value() { return value; }
    public boolean dragging() { return dragging; }

    public OneSlider setValue(double value) {
        this.value = clamp(value);
        return this;
    }

    public OneSlider setValueText(String valueText) {
        this.valueText = valueText == null ? "" : valueText;
        return this;
    }

    public OneSlider setLabel(String label) {
        this.label = label;
        setMessage(Component.literal(label));
        return this;
    }

    public OneSlider setOnChange(DoubleConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void apply(double next, boolean notify) {
        apply(next, notify, step);
    }

    private void apply(double next, boolean notify, double quantum) {
        double clamped = clamp(next);
        if (quantum > 0.0) {
            clamped = clamp(Math.round(clamped / quantum) * quantum);
        }
        if (Math.abs(clamped - value) < 1.0E-9) return;
        value = clamped;
        if (notify && onChange != null) onChange.accept(value);
    }

    private void setFromMouse(double mouseX) {
        int x = getX() + 4;
        int w = getWidth() - 8;
        if (w <= 0) return;
        apply((mouseX - x) / (double) w, true);
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
        int textY = y + 2;
        OneUi.textFit(g, font, label, x + 2, textY, w - 8 - OneMetrics.width(valueText) - 8,
            OneTheme.withAlpha(OneTheme.TEXT, alpha));
        OneUi.textRight(g, font, valueText, x + w - 2, textY, w / 2, OneTheme.withAlpha(OneTheme.MUTED, alpha));
        int trackY = y + h - 6;
        int trackX = x + 4;
        int trackW = w - 8;
        int filled = (int) Math.round(trackW * value);
        g.fill(trackX, trackY, trackX + trackW, trackY + 3, OneTheme.withAlpha(OneTheme.RAISED, alpha));
        g.fill(trackX, trackY, trackX + filled, trackY + 3, OneTheme.withAlpha(OneTheme.ACCENT, alpha));
        int thumbX = trackX + filled - 3;
        int thumbColor = OneTheme.withAlpha(active && (hover > 0.05F || dragging) ? OneTheme.ACCENT_HOVER : OneTheme.TEXT, alpha);
        g.fill(thumbX, trackY - 2, thumbX + 6, trackY + 5, thumbColor);
        if (focus > 0.02F) {
            g.outline(x, y, w, h, OneTheme.withAlpha(OneTheme.FOCUS, focus * alpha));
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        if (doubleClick) {
            apply(resetValue, true);
            return;
        }
        dragging = true;
        setFromMouse(event.x());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging || !active) return false;
        setFromMouse(event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = dragging;
        dragging = false;
        super.mouseReleased(event);
        return wasDragging;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !isMouseOver(mouseX, mouseY) || scrollY == 0.0) return false;
        double delta = Math.max(0.0025, step) * scrollY;
        apply(value + delta, true);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused() || !active) return super.keyPressed(event);
        int key = event.key();
        double fine = Math.max(0.0025, step * 0.1);
        boolean shift = (event.modifiers() & InputConstants.MOD_SHIFT) != 0;
        if (key == InputConstants.KEY_LEFT) {
            apply(value - (shift ? fine : Math.max(0.0025, step)), true, shift ? fine : step);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            apply(value + (shift ? fine : Math.max(0.0025, step)), true, shift ? fine : step);
            return true;
        }
        if (key == InputConstants.KEY_HOME) {
            apply(0.0, true);
            return true;
        }
        if (key == InputConstants.KEY_END) {
            apply(1.0, true);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(label));
        if (!valueText.isEmpty()) output.add(NarratedElementType.HINT, Component.literal(valueText));
    }
}
