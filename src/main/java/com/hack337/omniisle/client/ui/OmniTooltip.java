package com.hack337.omniisle.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** 400 ms delayed tooltip with cached wrapping. The caller supplies the clamped position. */
public final class OmniTooltip {
    private static final int MAX_WIDTH = 220;
    private static final int PAD = 8;

    private Object owner;
    private String title = "";
    private String description = "";
    private String shortcut = "";
    private long startMs;
    private Font font;
    private int width;
    private int height;
    private final List<FormattedCharSequence> lines = new ArrayList<>();

    public void hover(Object owner, String title, String description, String shortcut, long nowMs, Font font) {
        String safeTitle = title == null ? "" : title;
        String safeDescription = description == null ? "" : description;
        String safeShortcut = shortcut == null ? "" : shortcut;
        boolean changed = owner != this.owner
            || !safeTitle.equals(this.title)
            || !safeDescription.equals(this.description)
            || !safeShortcut.equals(this.shortcut)
            || this.font != font;
        if (owner != this.owner) this.startMs = nowMs;
        if (!changed) return;
        this.owner = owner;
        this.title = safeTitle;
        this.description = safeDescription;
        this.shortcut = safeShortcut;
        this.font = font;
        rebuild();
    }

    public void clear() {
        owner = null;
        startMs = 0L;
        title = "";
        description = "";
        shortcut = "";
        lines.clear();
        width = 0;
        height = 0;
    }

    public boolean visible(long nowMs) {
        return owner != null && nowMs - startMs >= OmniTheme.TOOLTIP_DELAY_MS && height > 0;
    }

    public boolean hasOwner() { return owner != null; }
    public Object owner() { return owner; }
    public int width() { return width; }
    public int height() { return height; }
    public long startMs() { return startMs; }

    private void rebuild() {
        lines.clear();
        if (font == null) {
            width = 0;
            height = 0;
            return;
        }
        int inner = MAX_WIDTH - PAD * 2;
        int titleWidth = title.isEmpty() ? 0 : Math.min(inner, font.width(title));
        int descWidth = 0;
        if (!description.isEmpty()) {
            lines.addAll(font.split(Component.literal(description), inner));
            for (FormattedCharSequence line : lines) {
                descWidth = Math.max(descWidth, font.width(line));
            }
        }
        int shortcutWidth = shortcut.isEmpty() ? 0 : font.width(shortcut) + 10;
        width = Math.min(MAX_WIDTH, Math.max(64, Math.max(titleWidth, Math.max(descWidth, shortcutWidth)) + PAD * 2));
        int lineHeight = font.lineHeight;
        height = PAD * 2 + lineHeight;
        if (!lines.isEmpty()) height += 3 + lines.size() * (lineHeight + 1);
        if (!shortcut.isEmpty()) height += 3 + lineHeight + 2;
    }

    public void render(GuiGraphicsExtractor g, Font font, int x, int y) {
        if (width <= 0 || height <= 0) return;
        g.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x77000000);
        OmniUi.raised(g, x, y, width, height, OmniTheme.RAISED, OmniTheme.BORDER);
        g.fill(x + 1, y + 2, x + 3, y + height - 2, OmniTheme.ACCENT);
        int textX = x + PAD;
        int cursorY = y + PAD;
        if (!title.isEmpty()) {
            OmniUi.textFit(g, font, title, textX, cursorY, width - PAD * 2, OmniTheme.TEXT);
            cursorY += font.lineHeight;
        }
        if (!lines.isEmpty()) {
            cursorY += 3;
            for (FormattedCharSequence line : lines) {
                g.text(font, line, textX, cursorY, OmniTheme.MUTED);
                cursorY += font.lineHeight + 1;
            }
        }
        if (!shortcut.isEmpty()) {
            cursorY += 3;
            g.fill(textX, cursorY + 1, textX + OmniMetrics.width(shortcut) + 6, cursorY + font.lineHeight,
                OmniTheme.withAlpha(OmniTheme.ACCENT, 0.16F));
            OmniUi.text(g, font, shortcut, textX + 3, cursorY, OmniTheme.ACCENT);
        }
    }
}
