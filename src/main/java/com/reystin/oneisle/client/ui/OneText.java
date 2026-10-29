package com.reystin.oneisle.client.ui;

import java.util.Locale;

/** Pure text fitting so layout and truncation can be tested without a client font. */
public final class OneText {
    public static final String ELLIPSIS = "…";

    @FunctionalInterface
    public interface Metrics {
        int width(String text);
    }

    private OneText() {}

    /**
     * Returns the original text when it fits, otherwise the longest prefix that fits with an
     * ellipsis. The caller is responsible for caching the result; this method allocates only on
     * truncation.
     */
    public static String fit(Metrics metrics, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        if (metrics.width(text) <= maxWidth) return text;
        int ellipsisWidth = metrics.width(ELLIPSIS);
        if (ellipsisWidth > maxWidth) return "";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (metrics.width(text.substring(0, mid)) + ellipsisWidth <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        while (low > 0 && Character.isLowSurrogate(text.charAt(low))) {
            low--;
        }
        return text.substring(0, low).stripTrailing() + ELLIPSIS;
    }

    public static boolean fits(Metrics metrics, String text, int maxWidth) {
        return text != null && metrics.width(text) <= maxWidth;
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    public static String integer(int value) {
        return String.format(Locale.ROOT, "%d", value);
    }
}
