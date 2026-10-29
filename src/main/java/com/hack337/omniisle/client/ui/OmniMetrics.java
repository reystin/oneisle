package com.hack337.omniisle.client.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.LanguageManager;

/**
 * Font-backed measurements with a cache invalidated on language or GUI resize.
 * Steady-state rendering must not allocate: every label and fit result is cached.
 */
public final class OmniMetrics {
    private static Font font;
    private static String language = "";
    private static final Map<String, Integer> widths = new HashMap<>();
    private static final Map<String, Map<Integer, String>> fitted = new HashMap<>();

    private OmniMetrics() {}

    public static void tick(Font current) {
        Minecraft minecraft = Minecraft.getInstance();
        LanguageManager manager = minecraft == null ? null : minecraft.getLanguageManager();
        String selected = manager == null ? "" : manager.getSelected();
        if (font != current || !language.equals(selected)) {
            font = current;
            language = selected;
            invalidate();
        }
    }

    public static void invalidate() {
        widths.clear();
        fitted.clear();
    }

    public static int width(String text) {
        if (text == null || text.isEmpty()) return 0;
        Integer cached = widths.get(text);
        if (cached != null) return cached;
        if (font == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return 0;
            font = minecraft.font;
        }
        int width = font.width(text);
        widths.put(text, width);
        return width;
    }

    public static String fit(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        Map<Integer, String> byWidth = fitted.get(text);
        if (byWidth == null) {
            byWidth = new HashMap<>(2);
            fitted.put(text, byWidth);
        }
        String cached = byWidth.get(maxWidth);
        if (cached != null) return cached;
        String result = OmniText.fit(OmniMetrics::width, text, maxWidth);
        byWidth.put(maxWidth, result);
        return result;
    }

    public static boolean fits(String text, int maxWidth) {
        return width(text) <= maxWidth;
    }

    /** Cached worst-case width for layout hints (no font access). */
    public static int estimate(String text, int charWidth) {
        return text == null ? 0 : text.codePointCount(0, text.length()) * charWidth;
    }
}
