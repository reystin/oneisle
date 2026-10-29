package com.hack337.omniisle.test;

import com.hack337.omniisle.client.ui.OmniInteraction;
import com.hack337.omniisle.client.ui.OmniTheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flat theme tokens, WCAG contrast and time-based easing. */
public class Phase11UiThemeTest {
    @Test
    @DisplayName("11U.1 tokens match the design specification")
    void tokens() {
        assertEquals(0xFF121418, OmniTheme.BG);
        assertEquals(0xFF1A1D23, OmniTheme.PANEL);
        assertEquals(0xFF222731, OmniTheme.RAISED);
        assertEquals(0xFF2C313A, OmniTheme.BORDER);
        assertEquals(0xFFE6E8EB, OmniTheme.TEXT);
        assertEquals(0xFF8A919C, OmniTheme.MUTED);
        assertEquals(0xFF3DDCB0, OmniTheme.ACCENT);
        assertEquals(0xFF6BEBC8, OmniTheme.ACCENT_HOVER);
        assertEquals(0xFFFF6B6B, OmniTheme.DANGER);
        assertEquals(0xFFFFC857, OmniTheme.WARN);
        assertEquals(0xFF5BD98B, OmniTheme.OK);
        assertEquals(4, OmniTheme.GRID);
        assertEquals(8, OmniTheme.PADDING);
        assertEquals(18, OmniTheme.ROW_HEIGHT);
        assertEquals(80L, OmniTheme.TRANSITION_MS);
        assertEquals(400L, OmniTheme.TOOLTIP_DELAY_MS);
    }

    @Test
    @DisplayName("11U.2 body text pairs reach WCAG 4.5:1")
    void contrast() {
        assertAtLeast(4.5, OmniTheme.TEXT, OmniTheme.BG);
        assertAtLeast(4.5, OmniTheme.TEXT, OmniTheme.PANEL);
        assertAtLeast(4.5, OmniTheme.TEXT, OmniTheme.RAISED);
        assertAtLeast(4.5, OmniTheme.MUTED, OmniTheme.PANEL);
        assertAtLeast(4.5, OmniTheme.MUTED, OmniTheme.RAISED);
        assertAtLeast(4.5, OmniTheme.ACCENT, OmniTheme.PANEL);
        assertAtLeast(4.5, OmniTheme.DANGER, OmniTheme.PANEL);
        assertAtLeast(4.5, OmniTheme.WARN, OmniTheme.PANEL);
        assertAtLeast(4.5, OmniTheme.OK, OmniTheme.PANEL);
        assertAtLeast(4.5, OmniTheme.TEXT_DARK, OmniTheme.ACCENT);
    }

    private static void assertAtLeast(double minimum, int foreground, int background) {
        double ratio = OmniTheme.contrastRatio(foreground, background);
        assertTrue(ratio >= minimum,
            "contrast " + String.format(java.util.Locale.ROOT, "%.2f", ratio) + " < " + minimum);
    }

    @Test
    @DisplayName("11U.3 easing is linear over 80 ms and monotonic")
    void easing() {
        assertEquals(0.0F, OmniInteraction.ramp(0L));
        assertEquals(0.5F, OmniInteraction.ramp(40L));
        assertEquals(1.0F, OmniInteraction.ramp(80L));
        assertEquals(1.0F, OmniInteraction.ramp(500L));
        assertEquals(0.0F, OmniInteraction.ramp(-5L));
        float previous = -1.0F;
        for (long ms = 0; ms <= 100; ms += 10) {
            float value = OmniInteraction.ramp(ms);
            assertTrue(value >= previous);
            previous = value;
        }

        OmniInteraction interaction = new OmniInteraction();
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
        int half = OmniTheme.withAlpha(OmniTheme.TEXT, 0.5F);
        int alpha = (half >>> 24) & 0xFF;
        assertTrue(alpha == 127 || alpha == 128, "alpha was " + alpha);
        assertEquals(OmniTheme.TEXT & 0x00FFFFFF, half & 0x00FFFFFF);

        OmniInteraction interaction = new OmniInteraction();
        interaction.tick(true, true, true, 0L);
        interaction.setEnabled(false);
        assertEquals(0.0F, interaction.hover());
        assertEquals(0.0F, interaction.focus());
        assertEquals(0.0F, interaction.press());
    }
}
