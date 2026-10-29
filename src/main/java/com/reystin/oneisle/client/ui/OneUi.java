package com.reystin.oneisle.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/** Flat drawing primitives. Every method paints with fills so no vanilla textures leak in. */
public final class OneUi {
    public static final int ICON = 16;

    private OneUi() {}

    /** Panel with 1-px clipped corners: the four corner pixels stay transparent. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        if (w <= 0 || h <= 0) return;
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, fill);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + 1, border == 0 ? fill : border);
        g.fill(x, y + 1, x + w, y + h - 1, fill);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, border == 0 ? fill : border);
        if (border != 0) {
            g.fill(x, y + 1, x + 1, y + h - 1, border);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        }
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        panel(g, x, y, w, h, fill, 0);
    }

    /** Raised surface: darker 1-px shadow under the bottom edge. */
    public static void raised(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        panel(g, x, y, w, h, fill, border);
        if (w > 2 && h > 0) {
            g.fill(x + 1, y + h, x + w - 1, y + h + 1, OneTheme.SHADOW);
        }
    }

    public static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
        if (w > 0) g.fill(x, y, x + w, y + 1, OneTheme.BORDER);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    public static void textFit(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        String fitted = OneMetrics.fit(text, maxWidth);
        if (!fitted.isEmpty()) g.text(font, fitted, x, y, color, false);
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String text, int centerX, int y, int maxWidth, int color) {
        String fitted = OneMetrics.fit(text, maxWidth);
        if (fitted.isEmpty()) return;
        int width = OneMetrics.width(fitted);
        g.text(font, fitted, centerX - width / 2, y, color, false);
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, String text, int rightX, int y, int maxWidth, int color) {
        String fitted = OneMetrics.fit(text, maxWidth);
        if (fitted.isEmpty()) return;
        g.text(font, fitted, rightX - OneMetrics.width(fitted), y, color, false);
    }

    public static void textShadowed(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, true);
    }

    /** Word-wrapped description; returns the used height. */
    public static int wrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
        return g.textWithWordWrap(font, text, x, y, width, color);
    }

    public static void icon(GuiGraphicsExtractor g, OneIcons icon, int x, int y, int size, int tint) {
        if (!OneIcons.available()) return;
        g.blit(RenderPipelines.GUI_TEXTURED, OneIcons.TEXTURE, x, y,
            icon.u(), icon.v(), size, size, OneIcons.CELL, OneIcons.CELL,
            OneIcons.CELL * OneIcons.COLUMNS, OneIcons.CELL * OneIcons.ROWS, tint);
    }

    /** Colour-plus-glyph status marks, drawn as pixels so the default font cannot show tofu. */
    public static void statusGlyph(GuiGraphicsExtractor g, OneTheme.Status status, int x, int y, int size, int color) {
        switch (status) {
            case OK -> check(g, x, y, size, color);
            case WARN -> alert(g, x, y, size, color);
            case ERROR -> cross(g, x, y, size, color);
            case NEUTRAL -> dot(g, x, y, size, color);
        }
    }

    public static void check(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int unit = Math.max(1, size / 7);
        int ox = x + Math.max(0, (size - unit * 7) / 2);
        int oy = y + Math.max(0, (size - unit * 7) / 2);
        for (int i = 0; i < 4; i++) {
            g.fill(ox + i * unit, oy + (3 + i) * unit, ox + (i + 1) * unit, oy + (4 + i) * unit, color);
        }
        for (int i = 0; i < 5; i++) {
            g.fill(ox + (3 + i) * unit, oy + (6 - i) * unit, ox + (4 + i) * unit, oy + (7 - i) * unit, color);
        }
    }

    public static void alert(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int unit = Math.max(1, size / 7);
        int ox = x + Math.max(0, (size - unit * 7) / 2);
        int oy = y + Math.max(0, (size - unit * 7) / 2);
        g.fill(ox + 3 * unit, oy + unit, ox + 4 * unit, oy + 5 * unit, color);
        g.fill(ox + 3 * unit, oy + 6 * unit, ox + 4 * unit, oy + 7 * unit, color);
    }

    public static void cross(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int unit = Math.max(1, size / 7);
        int ox = x + Math.max(0, (size - unit * 7) / 2);
        int oy = y + Math.max(0, (size - unit * 7) / 2);
        for (int i = 0; i < 7; i++) {
            g.fill(ox + i * unit, oy + i * unit, ox + (i + 1) * unit, oy + (i + 1) * unit, color);
            g.fill(ox + (6 - i) * unit, oy + i * unit, ox + (7 - i) * unit, oy + (i + 1) * unit, color);
        }
    }

    public static void dot(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int unit = Math.max(1, size / 7);
        int ox = x + Math.max(0, (size - unit * 7) / 2);
        int oy = y + Math.max(0, (size - unit * 7) / 2);
        g.fill(ox + 3 * unit, oy + 3 * unit, ox + 4 * unit, oy + 4 * unit, color);
    }

    public static void swatch(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, int border) {
        g.fill(x, y, x + w, y + h, border);
        if (w > 2 && h > 2) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    public static void scissor(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public static void unscissor(GuiGraphicsExtractor g) {
        g.disableScissor();
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int w, int h, int contentHeight, int viewHeight, int scroll) {
        if (contentHeight <= viewHeight || h <= 0 || w <= 0) return;
        g.fill(x, y, x + w, y + h, OneTheme.RAISED);
        int thumb = Math.max(12, h * viewHeight / contentHeight);
        int maxScroll = contentHeight - viewHeight;
        int travel = h - thumb;
        int offset = maxScroll <= 0 ? 0 : travel * scroll / maxScroll;
        g.fill(x, y + offset, x + w, y + offset + thumb, OneTheme.MUTED);
    }

    public static int clampScroll(int scroll, int contentHeight, int viewHeight) {
        int max = Math.max(0, contentHeight - viewHeight);
        return Math.max(0, Math.min(scroll, max));
    }

    public static int verticalCenter(Font font, int y, int h) {
        return y + (h - font.lineHeight) / 2 + 1;
    }
}
