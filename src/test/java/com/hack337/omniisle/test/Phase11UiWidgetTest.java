package com.hack337.omniisle.test;

import com.hack337.omniisle.client.ui.OmniButton;
import com.hack337.omniisle.client.ui.OmniDropdown;
import com.hack337.omniisle.client.ui.OmniIcons;
import com.hack337.omniisle.client.ui.OmniScrollArea;
import com.hack337.omniisle.client.ui.OmniSegmented;
import com.hack337.omniisle.client.ui.OmniSlider;
import com.hack337.omniisle.client.ui.OmniTabBar;
import com.hack337.omniisle.client.ui.OmniToggle;
import com.hack337.omniisle.client.ui.OmniWidget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.client.gui.narration.NarrationTrigger;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Core widget state machines: pointer, keyboard, wheel, focus and narration. */
public class Phase11UiWidgetTest {
    private static final FocusNavigationEvent.TabNavigation TAB =
        new FocusNavigationEvent.TabNavigation(true);

    @Test
    @DisplayName("11W.1 buttons run once per click and keyboard activation, disabled widgets are skipped")
    void buttons() {
        int[] runs = {0};
        OmniButton button = new OmniButton(10, 10, 80, 20, "Save", OmniButton.Style.PRIMARY,
            () -> runs[0]++).shortcut("Ctrl+S");
        assertTrue(button.mouseClicked(click(20, 15), false));
        assertEquals(1, runs[0]);
        assertFalse(button.mouseClicked(click(200, 200), false));
        assertEquals(1, runs[0]);

        button.active = false;
        assertFalse(button.mouseClicked(click(20, 15), false));
        button.active = true;

        assertFalse(button.keyPressed(key(InputConstants.KEY_RETURN, 0)), "unfocused button must ignore Enter");
        button.setFocused(true);
        assertTrue(button.keyPressed(key(InputConstants.KEY_RETURN, 0)));
        assertTrue(button.keyPressed(key(InputConstants.KEY_SPACE, 0)));
        assertEquals(3, runs[0]);
        button.active = false;
        assertFalse(button.keyPressed(key(InputConstants.KEY_RETURN, 0)));
        button.active = true;

        button.setLabel("Save world");
        assertEquals("Save world", button.label());
        button.setStyle(OmniButton.Style.DANGER);
        assertEquals(OmniButton.Style.DANGER, button.style());
        button.setFocused(false);
        assertTrue(button.nextFocusPath(TAB) != null);
        button.visible = false;
        assertNull(button.nextFocusPath(TAB));

        String narration = narration(button);
        assertTrue(narration.contains("Save world"), narration);
        assertTrue(narration.contains("Ctrl+S"), narration);

        OmniButton icon = OmniButton.icon(0, 0, 20, 20, OmniIcons.DICE, "Randomise",
            OmniButton.Style.GHOST, () -> runs[0]++);
        assertTrue(icon.iconOnly());
        assertEquals(OmniIcons.DICE, icon.icon());
        assertEquals(3, runs[0]);
        assertTrue(narration(icon).contains("Randomise"));
    }

    @Test
    @DisplayName("11W.2 toggles flip by pointer and Space without notifying setValue")
    void toggles() {
        List<Boolean> changes = new ArrayList<>();
        OmniToggle toggle = new OmniToggle(0, 0, 120, 20, "Grid", false, changes::add);
        assertFalse(toggle.value());
        assertTrue(toggle.mouseClicked(click(10, 10), false));
        assertTrue(toggle.value());
        assertEquals(List.of(true), changes);
        toggle.setFocused(true);
        assertTrue(toggle.keyPressed(key(InputConstants.KEY_SPACE, 0)));
        assertFalse(toggle.value());
        assertEquals(List.of(true, false), changes);
        toggle.setValue(true);
        assertTrue(toggle.value());
        assertEquals(2, changes.size(), "setValue is silent");
        toggle.active = false;
        assertFalse(toggle.mouseClicked(click(10, 10), false));
        assertTrue(narration(toggle).contains("Grid"));
    }

    @Test
    @DisplayName("11W.3 segmented selectors pick cells and move with arrows")
    void segmented() {
        List<Integer> picks = new ArrayList<>();
        OmniSegmented segmented = new OmniSegmented(0, 0, 120, 20,
            new String[]{"Land", "Height", "Biomes"}, null, 0, picks::add);
        assertTrue(segmented.mouseClicked(click(100, 10), false));
        assertEquals(2, segmented.selected());
        assertEquals(List.of(2), picks);
        segmented.setFocused(true);
        assertTrue(segmented.keyPressed(key(InputConstants.KEY_LEFT, 0)));
        assertEquals(1, segmented.selected());
        assertTrue(segmented.keyPressed(key(InputConstants.KEY_HOME, 0)));
        assertEquals(0, segmented.selected());
        assertTrue(segmented.keyPressed(key(InputConstants.KEY_END, 0)));
        assertEquals(2, segmented.selected());
        assertTrue(segmented.keyPressed(key(InputConstants.KEY_RIGHT, 0)));
        assertEquals(2, segmented.selected(), "right at the end stays selected");
        segmented.setSelected(1);
        assertEquals(1, segmented.selected());
        segmented.setLabels(new String[]{"A", "B", "C"});
        assertEquals("B", segmented.labels()[1]);
        assertTrue(narration(segmented).contains("B"));
        segmented.active = false;
        assertNull(segmented.nextFocusPath(TAB));
    }

    @Test
    @DisplayName("11W.4 sliders support click, drag, wheel, arrows, Shift fine step and reset")
    void sliders() {
        List<Double> changes = new ArrayList<>();
        OmniSlider slider = new OmniSlider(10, 10, 108, 20, "Brush", 0.5, 0.1, 0.3, changes::add);
        slider.mouseClicked(click(84, 15), false);
        assertEquals(0.7, slider.value(), 1e-9);
        assertEquals(1, changes.size());
        assertTrue(slider.dragging());
        assertTrue(slider.mouseDragged(click(14, 15), 0, 0));
        assertEquals(0.0, slider.value(), 1e-9);
        slider.mouseReleased(click(14, 15));
        assertFalse(slider.dragging());

        slider.setValue(0.5);
        assertTrue(slider.mouseScrolled(20, 15, 0, -1));
        assertEquals(0.4, slider.value(), 1e-9);
        assertTrue(slider.mouseScrolled(20, 15, 0, 1));
        assertEquals(0.5, slider.value(), 1e-9);

        slider.setFocused(true);
        assertTrue(slider.keyPressed(key(InputConstants.KEY_RIGHT, 0)));
        assertEquals(0.6, slider.value(), 1e-9);
        assertTrue(slider.keyPressed(key(InputConstants.KEY_RIGHT, InputConstants.MOD_SHIFT)));
        assertEquals(0.61, slider.value(), 1e-6, "Shift must apply the fine step");
        assertTrue(slider.keyPressed(key(InputConstants.KEY_HOME, 0)));
        assertEquals(0.0, slider.value(), 1e-9);
        assertTrue(slider.keyPressed(key(InputConstants.KEY_END, 0)));
        assertEquals(1.0, slider.value(), 1e-9);

        slider.onClick(click(20, 15), true);
        assertEquals(0.3, slider.value(), 1e-9, "double-click resets");

        slider.active = false;
        double before = slider.value();
        assertFalse(slider.mouseScrolled(20, 15, 0, 1));
        assertFalse(slider.mouseDragged(click(80, 15), 0, 0));
        assertEquals(before, slider.value(), 1e-9);
        slider.active = true;
        slider.setValueText("7 px");
        assertTrue(narration(slider).contains("7 px"));
    }

    @Test
    @DisplayName("11W.5 scroll areas clamp, step by notches, and drag the thumb")
    void scrollAreas() {
        OmniScrollArea area = new OmniScrollArea();
        area.setViewHeight(40);
        area.setContentHeight(100);
        assertEquals(60, area.maxScroll());
        assertEquals(16, area.thumbHeight(40));
        assertTrue(area.scrollBy(-1));
        assertEquals(18, area.scroll());
        assertTrue(area.scrollBy(-10));
        assertEquals(60, area.scroll());
        assertFalse(area.scrollBy(-1));
        area.setScroll(-5);
        assertEquals(0, area.scroll());

        area.clickScrollbar(10, 0, 40);
        assertTrue(area.dragging());
        area.dragTo(30, 0, 40);
        assertTrue(area.scroll() > 0);
        area.release();
        assertFalse(area.dragging());
        area.setContentHeight(10);
        assertEquals(0, area.scroll());
        assertEquals(40, area.thumbHeight(40), "short content uses the full track");
    }

    @Test
    @DisplayName("11W.6 tab bars hit-test slots and cycle with arrows")
    void tabBars() {
        String[] labels = {"Shape", "Terrain", "Climate", "Biomes", "Structures", "Tools"};
        OmniIcons[] icons = {OmniIcons.PRESET, OmniIcons.SMOOTH, OmniIcons.SYMMETRY,
            OmniIcons.BRUSH, OmniIcons.SPAWN, OmniIcons.SETTINGS};
        int[] picks = {-1};
        OmniTabBar tabs = new OmniTabBar(0, 0, 180, 40, labels, icons, 0, index -> picks[0] = index);
        for (int i = 0; i < 6; i++) {
            tabs.setSlot(i, (i % 3) * 60, (i / 3) * 20, 60, 20);
        }
        assertEquals(4, tabs.tabAt(70, 25));
        assertTrue(tabs.mouseClicked(click(70, 25), false));
        assertEquals(4, tabs.selected());
        assertEquals(4, picks[0]);
        tabs.setFocused(true);
        assertTrue(tabs.keyPressed(key(InputConstants.KEY_RIGHT, 0)));
        assertEquals(5, tabs.selected());
        assertTrue(tabs.keyPressed(key(InputConstants.KEY_RIGHT, 0)));
        assertEquals(0, tabs.selected());
        assertTrue(tabs.keyPressed(key(InputConstants.KEY_END, 0)));
        assertEquals(5, tabs.selected());
        assertTrue(narration(tabs).contains("Tools"));
    }

    @Test
    @DisplayName("11W.7 dropdowns navigate, scroll, pick and close")
    void dropdowns() {
        String[] items = new String[12];
        for (int i = 0; i < items.length; i++) items[i] = "Item " + i;
        int[] picks = {-1};
        int[] closes = {0};
        OmniDropdown dropdown = new OmniDropdown(0, 0, 140, "Presets", items, 1,
            index -> picks[0] = index, () -> closes[0]++);
        assertEquals(1, dropdown.cursor());
        assertTrue(dropdown.keyPressed(key(InputConstants.KEY_DOWN, 0)));
        assertEquals(2, dropdown.cursor());
        for (int i = 0; i < 8; i++) dropdown.keyPressed(key(InputConstants.KEY_DOWN, 0));
        assertEquals(10, dropdown.cursor());
        assertTrue(dropdown.keyPressed(key(InputConstants.KEY_RETURN, 0)));
        assertEquals(10, picks[0]);
        assertTrue(dropdown.keyPressed(key(InputConstants.KEY_ESCAPE, 0)));
        assertEquals(1, closes[0]);
        assertTrue(dropdown.mouseScrolled(20, 20, 0, -1));
        assertTrue(dropdown.nextFocusPath(TAB) != null);
    }

    @Test
    @DisplayName("11W.8 all widget types expose a unique, non-empty narration title")
    void narrationTitles() {
        List<OmniWidget> widgets = List.of(
            new OmniButton(0, 0, 60, 18, "Undo", OmniButton.Style.GHOST, () -> {}),
            new OmniToggle(0, 0, 60, 18, "Grid", true, value -> {}),
            new OmniSegmented(0, 0, 90, 18, new String[]{"On", "Off"}, 0, index -> {}),
            new OmniSlider(0, 0, 90, 18, "Zoom", 0.5, 0.1, 0.5, value -> {}),
            new OmniTabBar(0, 0, 60, 18, new String[]{"Shape", "Tools"},
                new OmniIcons[]{OmniIcons.PRESET, OmniIcons.SETTINGS}, 0, index -> {}),
            new OmniDropdown(0, 0, 120, "Menu", new String[]{"A", "B"}, 0, index -> {}, () -> {})
        );
        List<String> titles = new ArrayList<>();
        for (OmniWidget widget : widgets) {
            widget.setFocused(false);
            assertNotNull(widget.nextFocusPath(TAB), widget.getClass().getSimpleName() + " must be focusable");
            String text = narration(widget);
            assertFalse(text.isBlank(), widget.getClass().getSimpleName() + " narration empty");
            titles.add(widget.getMessage().getString());
            widget.active = false;
            assertNull(widget.nextFocusPath(TAB), "disabled " + widget.getClass().getSimpleName());
            widget.active = true;
        }
        assertEquals(titles.size(), titles.stream().distinct().count(), "narration titles must be unique");
    }

    private static MouseButtonEvent click(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
    }

    private static KeyEvent key(int key, int modifiers) {
        return new KeyEvent(key, 0, modifiers);
    }

    private static String narration(AbstractWidget widget) {
        RecordingNarration recorder = new RecordingNarration();
        widget.updateNarration(recorder);
        return recorder.text();
    }

    private static final class RecordingNarration implements NarrationElementOutput {
        private final List<NarrationThunk<?>> thunks = new ArrayList<>();

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> thunk) {
            thunks.add(thunk);
        }

        @Override
        public NarrationElementOutput nest() {
            return this;
        }

        @Override
        public NarrationTrigger narrationTrigger() {
            return NarrationTrigger.SYSTEM;
        }

        String text() {
            StringBuilder builder = new StringBuilder();
            for (NarrationThunk<?> thunk : thunks) {
                thunk.getText(value -> builder.append(value).append(' '));
            }
            return builder.toString().trim();
        }
    }
}
