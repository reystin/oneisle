package com.hack337.omniisle.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Bottom-right toast queue with fade-out; entries allocate only when pushed. */
public final class OmniToast {
    public enum Kind { SUCCESS, ERROR, INFO }

    private static final long LIFE_MS = 2600L;
    private static final long FADE_MS = 220L;
    private static final int MAX = 3;
    private static final int WIDTH = 248;
    private static final int HEIGHT = 24;

    private static final class Entry {
        final String text;
        final Kind kind;
        final long start;
        String fitted = "";

        Entry(String text, Kind kind, long start) {
            this.text = text;
            this.kind = kind;
            this.start = start;
        }
    }

    private final List<Entry> entries = new ArrayList<>(MAX);

    public void push(String text, Kind kind, long nowMs) {
        if (text == null || text.isEmpty()) return;
        if (entries.size() >= MAX) entries.remove(0);
        entries.add(new Entry(text, kind, nowMs));
    }

    public int size() { return entries.size(); }

    public void clear() { entries.clear(); }

    public void render(GuiGraphicsExtractor g, Font font, int rightX, int bottomY, long nowMs) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (nowMs - entries.get(i).start >= LIFE_MS) entries.remove(i);
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            long age = nowMs - entry.start;
            float alpha = age <= LIFE_MS - FADE_MS ? 1.0F : Math.max(0.0F, (LIFE_MS - age) / (float) FADE_MS);
            if (alpha <= 0.0F) continue;
            int y = bottomY - (entries.size() - i) * (HEIGHT + 4);
            int x = rightX - WIDTH;
            int color = switch (entry.kind) {
                case SUCCESS -> OmniTheme.OK;
                case ERROR -> OmniTheme.DANGER;
                case INFO -> OmniTheme.MUTED;
            };
            g.fill(x + 2, y + 2, x + WIDTH + 2, y + HEIGHT + 2, OmniTheme.withAlpha(0x99000000, alpha));
            OmniUi.panel(g, x, y, WIDTH, HEIGHT, OmniTheme.withAlpha(OmniTheme.RAISED, alpha),
                OmniTheme.withAlpha(OmniTheme.BORDER, alpha));
            g.fill(x + 1, y + 2, x + 3, y + HEIGHT - 2, OmniTheme.withAlpha(color, alpha));
            OmniTheme.Status status = entry.kind == Kind.SUCCESS ? OmniTheme.Status.OK
                : entry.kind == Kind.ERROR ? OmniTheme.Status.ERROR : OmniTheme.Status.NEUTRAL;
            OmniUi.statusGlyph(g, status, x + 8, y + (HEIGHT - 10) / 2, 10, OmniTheme.withAlpha(color, alpha));
            if (entry.fitted.isEmpty()) entry.fitted = OmniMetrics.fit(entry.text, WIDTH - 30);
            OmniUi.text(g, font, entry.fitted, x + 24, OmniUi.verticalCenter(font, y, HEIGHT),
                OmniTheme.withAlpha(OmniTheme.TEXT, alpha));
        }
    }
}
