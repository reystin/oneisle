package com.hack337.omniisle.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Flat switch replacing checkboxes. */
public class OmniToggle extends OmniWidget {
    private boolean value;
    private Consumer<Boolean> onChange;
    private String label;
    private float knob;
    private long lastMs = -1L;

    public OmniToggle(int x, int y, int width, int height, String label, boolean value, Consumer<Boolean> onChange) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.value = value;
        this.onChange = onChange;
        this.knob = value ? 1.0F : 0.0F;
    }

    public boolean value() { return value; }

    public OmniToggle setValue(boolean value) {
        this.value = value;
        return this;
    }

    public OmniToggle setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public void toggle() {
        value = !value;
        if (onChange != null) onChange.accept(value);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        long now = Util.getMillis();
        if (lastMs < 0L) lastMs = now;
        long elapsed = Math.max(0L, Math.min(100L, now - lastMs));
        lastMs = now;
        float step = elapsed / (float) OmniTheme.TRANSITION_MS;
        knob = knob < (value ? 1.0F : 0.0F)
            ? Math.min(value ? 1.0F : 0.0F, knob + step)
            : Math.max(value ? 1.0F : 0.0F, knob - step);
        float alpha = active ? 1.0F : 0.5F;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int trackW = Math.min(26, Math.max(14, w / 3));
        int trackH = Math.min(h, 12);
        int trackX = x + w - trackW;
        int trackY = y + (h - trackH) / 2;
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        OmniUi.textFit(g, font, label, x + 2, OmniUi.verticalCenter(font, y, h),
            w - trackW - 8, OmniTheme.withAlpha(OmniTheme.TEXT, alpha));
        int fill = OmniTheme.lerp(OmniTheme.RAISED, OmniTheme.ACCENT, knob * (0.75F + 0.25F * hover));
        fill = OmniTheme.withAlpha(fill, alpha);
        OmniUi.panel(g, trackX, trackY, trackW, trackH, fill, OmniTheme.withAlpha(OmniTheme.BORDER, alpha));
        int knobSize = trackH - 4;
        int travel = trackW - knobSize - 6;
        int knobX = trackX + 3 + Math.round(travel * knob);
        int knobColor = OmniTheme.withAlpha(knob > 0.5F ? OmniTheme.TEXT_DARK : OmniTheme.MUTED, alpha);
        g.fill(knobX, trackY + 2, knobX + knobSize, trackY + 2 + knobSize, knobColor);
        if (focus > 0.02F) {
            g.outline(x - 1, trackY - 1, w + 2, trackH + 2, OmniTheme.withAlpha(OmniTheme.FOCUS, focus * alpha));
        }
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
            Component.translatable(value ? "options.on" : "options.off"));
    }
}
