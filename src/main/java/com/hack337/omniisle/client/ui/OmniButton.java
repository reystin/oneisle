package com.hack337.omniisle.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Flat button: primary/secondary/ghost/danger, optional leading icon and shortcut chip. */
public class OmniButton extends OmniWidget {
    public enum Style { PRIMARY, SECONDARY, GHOST, DANGER }

    private Style style;
    private String label;
    private OmniIcons icon;
    private String shortcut = "";
    private Runnable action;
    private boolean iconOnly;
    private String cachedFit = "";
    private int cachedFitMax = -1;
    private int cachedFitWidth = -1;

    public OmniButton(int x, int y, int width, int height, String label, Style style, Runnable action) {
        this(x, y, width, height, label, style, action, null, false);
    }

    public OmniButton(int x, int y, int width, int height, String label, Style style, Runnable action,
                      OmniIcons icon, boolean iconOnly) {
        super(x, y, width, height, Component.literal(label == null ? "" : label));
        this.label = label == null ? "" : label;
        this.style = style;
        this.action = action;
        this.icon = icon;
        this.iconOnly = iconOnly;
    }

    public static OmniButton icon(int x, int y, int width, int height, OmniIcons icon, String label,
                                  Style style, Runnable action) {
        return new OmniButton(x, y, width, height, label, style, action, icon, true);
    }

    public OmniButton shortcut(String shortcut) {
        this.shortcut = shortcut == null ? "" : shortcut;
        return this;
    }

    public OmniButton setOnPress(Runnable action) {
        this.action = action;
        return this;
    }

    public OmniButton setLabel(String label) {
        this.label = label == null ? "" : label;
        setMessage(Component.literal(this.label));
        cachedFitMax = -1;
        return this;
    }

    public OmniButton setStyle(Style style) {
        this.style = style;
        return this;
    }

    public OmniButton setIcon(OmniIcons icon) {
        this.icon = icon;
        return this;
    }

    public String label() { return label; }
    public Style style() { return style; }
    public OmniIcons icon() { return icon; }
    public boolean iconOnly() { return iconOnly; }
    public String shortcutText() { return shortcut; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        float alpha = active ? 1.0F : 0.5F;
        int fill;
        int border;
        int text;
        switch (style) {
            case PRIMARY -> {
                fill = OmniTheme.lerp(OmniTheme.ACCENT, OmniTheme.ACCENT_HOVER, hover);
                fill = OmniTheme.lerp(fill, 0xFF0B0D10, press * 0.16F);
                border = 0;
                text = OmniTheme.TEXT_DARK;
            }
            case DANGER -> {
                fill = OmniTheme.lerp(OmniTheme.RAISED, 0xFF55302F, hover);
                border = OmniTheme.DANGER;
                text = OmniTheme.DANGER;
            }
            case GHOST -> {
                fill = OmniTheme.withAlpha(OmniTheme.RAISED, hover);
                border = 0;
                text = OmniTheme.TEXT;
            }
            default -> {
                fill = OmniTheme.lerp(OmniTheme.RAISED, 0xFF2A303B, hover);
                border = OmniTheme.BORDER;
                text = OmniTheme.TEXT;
            }
        }
        fill = OmniTheme.withAlpha(fill, alpha * (0.86F + 0.14F * (1.0F - press)));
        border = OmniTheme.withAlpha(border, alpha);
        text = OmniTheme.withAlpha(text, alpha);
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        OmniUi.raised(g, x, y, w, h, fill, border);
        if (focus > 0.02F) {
            g.outline(x, y, w, h, OmniTheme.withAlpha(OmniTheme.FOCUS, focus * alpha));
        }
        int iconSize = Math.min(16, h - 4);
        int textY = OmniUi.verticalCenter(font, y, h);
        int inset = 8;
        int textLeft = x + inset;
        if (icon != null && OmniIcons.available()) {
            int iconX = iconOnly ? x + (w - iconSize) / 2 : x + inset;
            int iconY = y + (h - iconSize) / 2;
            OmniUi.icon(g, icon, iconX, iconY, iconSize, text);
            if (!iconOnly) textLeft = iconX + iconSize + 6;
        }
        String shown = label;
        if (iconOnly && icon != null && OmniIcons.available()) shown = "";
        if (!shown.isEmpty()) {
            int shortcutWidth = shortcut.isEmpty() ? 0 : OmniMetrics.width(shortcut) + 10;
            int maxText = x + w - inset - shortcutWidth - textLeft;
            shown = fitLabel(shown, maxText);
            if (iconOnly && OmniIcons.available()) {
                shown = "";
            } else if (!shown.isEmpty()) {
                OmniUi.text(g, font, shown, textLeft, textY, text);
            }
        }
        if (!shortcut.isEmpty()) {
            int sw = OmniMetrics.width(shortcut);
            if (sw + 8 < w - 4) {
                OmniUi.text(g, font, shortcut, x + w - inset - sw, textY, OmniTheme.withAlpha(OmniTheme.MUTED, alpha));
            }
        }
    }

    private String fitLabel(String text, int maxWidth) {
        if (maxWidth == cachedFitMax && OmniMetrics.width(text) == cachedFitWidth) return cachedFit;
        cachedFit = OmniMetrics.fit(text, maxWidth);
        cachedFitMax = maxWidth;
        cachedFitWidth = OmniMetrics.width(text);
        return cachedFit;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || action == null) return;
        action.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isFocused() && active && action != null && activateKey(event)) {
            action.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (!shortcut.isEmpty()) output.add(NarratedElementType.HINT, Component.literal(shortcut));
    }
}
