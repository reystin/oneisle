package com.hack337.omniisle.client.ui;

/** Flat dark editor tokens. Colours are ARGB; contrast is WCAG 2.x relative luminance. */
public final class OmniTheme {
    public static final int BG = 0xFF121418;
    public static final int PANEL = 0xFF1A1D23;
    public static final int RAISED = 0xFF222731;
    public static final int BORDER = 0xFF2C313A;
    public static final int TEXT = 0xFFE6E8EB;
    public static final int MUTED = 0xFF8A919C;
    public static final int ACCENT = 0xFF3DDCB0;
    public static final int ACCENT_HOVER = 0xFF6BEBC8;
    public static final int DANGER = 0xFFFF6B6B;
    public static final int WARN = 0xFFFFC857;
    public static final int OK = 0xFF5BD98B;
    public static final int TEXT_DARK = 0xFF121418;
    public static final int SHADOW = 0x66000000;
    public static final int OVERLAY = 0xCC090B0E;
    public static final int FOCUS = ACCENT;

    public static final int GRID = 4;
    public static final int PADDING = 8;
    public static final int ROW_HEIGHT = 18;
    public static final long TRANSITION_MS = 80L;
    public static final long TOOLTIP_DELAY_MS = 400L;

    public enum Status { OK, WARN, ERROR, NEUTRAL }

    private OmniTheme() {}

    public static int statusColor(Status status) {
        return switch (status) {
            case OK -> OK;
            case WARN -> WARN;
            case ERROR -> DANGER;
            case NEUTRAL -> MUTED;
        };
    }

    /** Multiplies the alpha channel by {@code factor} (0..1). */
    public static int withAlpha(int color, float factor) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        int a = Math.max(0, Math.min(255, Math.round(alpha * factor * 255.0F)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int lerp(int from, int to, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int a = lerpChannel(from >>> 24, to >>> 24, clamped);
        int r = lerpChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, clamped);
        int g = lerpChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, clamped);
        int b = lerpChannel(from & 0xFF, to & 0xFF, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * t)));
    }

    /** WCAG contrast ratio of two opaque colours; returns 1 for equal/invalid input. */
    public static double contrastRatio(int first, int second) {
        double a = relativeLuminance(first);
        double b = relativeLuminance(second);
        double high = Math.max(a, b);
        double low = Math.min(a, b);
        return (high + 0.05) / (low + 0.05);
    }

    public static double relativeLuminance(int rgb) {
        double r = linear(((rgb >>> 16) & 0xFF) / 255.0);
        double g = linear(((rgb >>> 8) & 0xFF) / 255.0);
        double b = linear((rgb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
