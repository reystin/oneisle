package com.reystin.oneisle.test;

import com.reystin.oneisle.client.ui.OneInteraction;
import com.reystin.oneisle.client.ui.OneTheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flat theme tokens, WCAG contrast and time-based easing. */
public class Phase11UiThemeTest {
    @Test
    @DisplayName("11U.1 tokens match the design specification")
    void tokens() {
        assertEquals(0xFF121418, OneTheme.BG);
        assertEquals(0xFF1A1D23, OneTheme.PANEL);
        assertEquals(0xFF222731, OneTheme.RAISED);
        assertEquals(0xFF2C313A, OneTheme.BORDER);
        assertEquals(0xFFE6E8EB, OneTheme.TEXT);
        assertEquals(0xFF8A919C, OneTheme.MUTED);
        assertEquals(0xFF3DDCB0, OneTheme.ACCENT);
        assertEquals(0xFF6BEBC8, OneTheme.ACCENT_HOVER);
        assertEquals(0xFFFF6B6B, OneTheme.DANGER);
        assertEquals(0xFFFFC857, OneTheme.WARN);
        assertEquals(0xFF5BD98B, OneTheme.OK);
        assertEquals(4, OneTheme.GRID);
        assertEquals(8, OneTheme.PADDING);
        assertEquals(18, OneTheme.ROW_HEIGHT);
        assertEquals(80L, OneTheme.TRANSITION_MS);
        assertEquals(400L, OneTheme.TOOLTIP_DELAY_MS);
    }

    @Test
    @DisplayName("11U.2 body text pairs reach WCAG 4.5:1")
    void contrast() {
        assertAtLeast(4.5, OneTheme.TEXT, OneTheme.BG);
        assertAtLeast(4.5, OneTheme.TEXT, OneTheme.PANEL);
        assertAtLeast(4.5, OneTheme.TEXT, OneTheme.RAISED);
        assertAtLeast(4.5, OneTheme.MUTED, OneTheme.PANEL);
        assertAtLeast(4.5, OneTheme.MUTED, OneTheme.RAISED);
        assertAtLeast(4.5, OneTheme.ACCENT, OneTheme.PANEL);
        assertAtLeast(4.5, OneTheme.DANGER, OneTheme.PANEL);
        assertAtLeast(4.5, OneTheme.WARN, OneTheme.PANEL);
        assertAtLeast(4.5, OneTheme.OK, OneTheme.PANEL);
        assertAtLeast(4.5, OneTheme.TEXT_DARK, OneTheme.ACCENT);
    }

    private static void assertAtLeast(double minimum, int foreground, int background) {
        double ratio = OneTheme.contrastRatio(foreground, background);
        assertTrue(ratio >= minimum,
            "contrast " + String.format(java.util.Locale.ROOT, "%.2f", ratio) + " < " + minimum);
    }

    @Test
    @DisplayName("11U.3 easing is linear over 80 ms and monotonic")
    void easing() {
        assertEquals(0.0F, OneInteraction.ramp(0L));
        assertEquals(0.5F, OneInteraction.ramp(40L));
        assertEquals(1.0F, OneInteraction.ramp(80L));
        assertEquals(1.0F, OneInteraction.ramp(500L));
        assertEquals(0.0F, OneInteraction.ramp(-5L));
        float previous = -1.0F;
        for (long ms = 0; ms <= 100; ms += 10) {
            float value = OneInteraction.ramp(ms);
            assertTrue(value >= previous);
            previous = value;
        }

        OneInteraction interaction = new OneInteraction();
        interaction.tick(true, false, false, 1000L);
        float first = interaction.hover();
        interaction.tick(true, false, false, 1040L);
        float second = interaction.hover();
        interaction.tick(true, false, false, 1080L);
        float third = interaction.hover();
        assertTrue(first < second && second < third);
        assertEquals(1.0F, third);
    }

    @Test
    @DisplayName("11U.4 disabled state is 50 percent alpha and resets animation")
    void disabled() {
        int half = OneTheme.withAlpha(OneTheme.TEXT, 0.5F);
        int alpha = (half >>> 24) & 0xFF;
        assertTrue(alpha == 127 || alpha == 128, "alpha was " + alpha);
        assertEquals(OneTheme.TEXT & 0x00FFFFFF, half & 0x00FFFFFF);

        OneInteraction interaction = new OneInteraction();
        interaction.tick(true, true, true, 0L);
        interaction.setEnabled(false);
        assertEquals(0.0F, interaction.hover());
        assertEquals(0.0F, interaction.focus());
        assertEquals(0.0F, interaction.press());
    }
}
