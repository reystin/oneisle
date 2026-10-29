package com.reystin.oneisle.test;

import com.reystin.oneisle.client.EditorLayout;
import com.reystin.oneisle.client.EditorLayout.Density;
import com.reystin.oneisle.client.EditorLayout.Layout;
import com.reystin.oneisle.client.EditorLayout.Rect;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.0 editor layout: density tiers, panel geometry, tooltip placement and resolution row. */
public class Phase11EditorLayoutTest {
    /** Window sizes required by the 1.0 plan, at GUI scales 1-4. */
    private static final int[][] WINDOWS = {
        {640, 320}, {854, 480}, {1280, 720}, {1920, 1080}
    };

    /** Extra logical sizes kept from the 0.8.x layout test. */
    private static final int[][] EXTRA = {
        {1920, 1080}, {1280, 720}, {1366, 768}, {1024, 768}, {960, 540},
        {800, 600}, {768, 320}, {683, 384}, {640, 360}, {480, 270},
        {455, 256}, {426, 240}, {341, 192}
    };

    @Test
    @DisplayName("11E.1 resolution row cycles 192/256/384 with a full two-argument label")
    void resolutionRow() throws IOException {
        assertEquals(256, EditorLayout.nextResolution(192));
        assertEquals(384, EditorLayout.nextResolution(256));
        assertEquals(192, EditorLayout.nextResolution(384));
        assertEquals(192, EditorLayout.nextResolution(173));

        String ru = lang("ru_ru.json");
        String en = lang("en_us.json");
        String ruLabel = jsonValue(ru, "oneisle.editor.resolution");
        String enLabel = jsonValue(en, "oneisle.editor.resolution");
        assertEquals("Разрешение: %s×%s", ruLabel);
        assertEquals("Resolution: %s×%s", enLabel);
        assertEquals("Разрешение: 192×192", String.format(Locale.ROOT, ruLabel, 192, 192));
        assertEquals("Resolution: 256×256", String.format(Locale.ROOT, enLabel, 256, 256));
        assertFalse(String.format(Locale.ROOT, ruLabel, 192, 192).contains("x"),
            "resolution label must use × instead of x");
        assertTrue(jsonValue(ru, "oneisle.editor.resolution.tooltip").contains("192 -> 256 -> 384"));
        assertTrue(jsonValue(en, "oneisle.editor.resolution.tooltip").contains("192 -> 256 -> 384"));
    }

    @Test
    @DisplayName("11E.2 density thresholds cover FULL, MEDIUM, COMPACT, MINI and MINIMAL")
    void densityThresholds() {
        assertEquals(Density.FULL, EditorLayout.densityFor(840, 470));
        assertEquals(Density.MEDIUM, EditorLayout.densityFor(839, 470));
        assertEquals(Density.MEDIUM, EditorLayout.densityFor(700, 420));
        assertEquals(Density.COMPACT, EditorLayout.densityFor(699, 420));
        assertEquals(Density.COMPACT, EditorLayout.densityFor(400, 240));
        assertEquals(Density.MINI, EditorLayout.densityFor(399, 240));
        assertEquals(Density.MINI, EditorLayout.densityFor(320, 150));
        assertEquals(Density.MINIMAL, EditorLayout.densityFor(319, 150));

        assertEquals(Density.FULL, density(1280, 720, 1));
        assertEquals(Density.FULL, density(1920, 1080, 2));
        assertEquals(Density.COMPACT, density(640, 320, 1));
        assertEquals(Density.MINI, density(640, 320, 2));
        assertEquals(Density.MINIMAL, density(640, 320, 3));
        assertEquals(Density.MINIMAL, density(640, 320, 4));
        assertEquals(Density.COMPACT, density(854, 480, 2));
        assertEquals(Density.MINIMAL, density(854, 480, 3));
        assertEquals(Density.MINIMAL, density(854, 480, 4));
        assertEquals(Density.MINI, density(1280, 720, 4));
    }

    @Test
    @DisplayName("11E.3 every rect stays inside the screen and its panel at all sizes and scales")
    void layoutMatrix() {
        for (int[] size : logicalSizes()) {
            checkLayout(size[0], size[1]);
        }
    }

    @Test
    @DisplayName("11E.4 inspector tooltip is clamped inside the canvas and flips at edges")
    void tooltipStaysInsideCanvas() {
        for (int[] size : logicalSizes()) {
            Layout layout = EditorLayout.compute(size[0], size[1]);
            if (layout.minimal()) continue;
            String at = size[0] + "x" + size[1];
            Rect canvas = layout.canvas();
            int[][] cursors = {
                {canvas.x(), canvas.y()},
                {canvas.right() - 1, canvas.y()},
                {canvas.x(), canvas.bottom() - 1},
                {canvas.right() - 1, canvas.bottom() - 1},
                {canvas.x() + canvas.width() / 2, canvas.y() + canvas.height() / 2}
            };
            for (int[] cursor : cursors) {
                Rect tip = EditorLayout.placeTooltip(canvas, cursor[0], cursor[1], 150, 56);
                assertTrue(EditorLayout.inside(canvas, tip), at + " tooltip escapes canvas: " + tip);
            }
            Rect flip = EditorLayout.placeTooltip(canvas, canvas.right() - 2, canvas.bottom() - 2, 100, 40);
            if (100 < canvas.width()) {
                assertTrue(flip.right() <= canvas.right() - 2, at + " tooltip did not flip left");
            } else {
                assertEquals(canvas.width(), flip.width(), at + " oversized tooltip must fill canvas");
                assertEquals(canvas.x(), flip.x(), at + " oversized tooltip must clamp to canvas");
            }
            if (40 < canvas.height()) {
                assertTrue(flip.bottom() <= canvas.bottom() - 2, at + " tooltip did not flip up");
            }
            Rect huge = EditorLayout.placeTooltip(canvas, canvas.x() + 2, canvas.y() + 2,
                canvas.width() + 80, canvas.height() + 80);
            assertEquals(canvas.width(), huge.width(), at + " oversized tooltip must shrink to canvas");
            assertEquals(canvas.height(), huge.height(), at + " oversized tooltip must shrink to canvas");

            int[] out = new int[4];
            EditorLayout.placeTooltip(canvas, cursors[4][0], cursors[4][1], 150, 56, out);
            Rect centred = EditorLayout.placeTooltip(canvas, cursors[4][0], cursors[4][1], 150, 56);
            assertEquals(centred.x(), out[0]);
            assertEquals(centred.y(), out[1]);
            assertEquals(centred.width(), out[2]);
            assertEquals(centred.height(), out[3]);
        }
    }

    private static void checkLayout(int width, int height) {
        String at = width + "x" + height;
        Layout layout = EditorLayout.compute(width, height);
        Rect screen = new Rect(0, 0, width, height);
        assertEquals(EditorLayout.densityFor(width, height), layout.density(), at + " tier");

        if (layout.minimal()) {
            assertMinimal(layout, screen, at);
            return;
        }

        Rect topBar = layout.topBar();
        Rect tools = layout.toolColumn();
        Rect canvasFrame = layout.canvasFrame();
        Rect controls = layout.controls();
        Rect list = layout.listPanel();
        Rect status = layout.status();

        for (Rect rect : new Rect[]{topBar, tools, canvasFrame, controls, status,
            layout.canvasModes(), layout.canvas(), layout.tabs(), layout.content(),
            layout.statusText(), layout.cancel(), layout.done()}) {
            assertInside(rect, screen, at);
        }
        assertTrue(layout.canvasModes().height() > 0, at + " canvas modes missing");
        assertTrue(layout.canvas().width() >= 16 && layout.canvas().height() >= 16,
            at + " canvas too small");
        assertTrue(EditorLayout.inside(canvasFrame, layout.canvasModes()), at + " modes escape frame");
        assertTrue(EditorLayout.inside(canvasFrame, layout.canvas()), at + " canvas escapes frame");
        assertFalse(EditorLayout.overlaps(canvasFrame, tools), at + " canvas/tool overlap");
        assertFalse(EditorLayout.overlaps(canvasFrame, controls), at + " canvas/controls overlap");
        assertFalse(EditorLayout.overlaps(canvasFrame, status), at + " canvas/status overlap");
        assertFalse(EditorLayout.overlaps(canvasFrame, topBar), at + " canvas/top bar overlap");

        assertFalse(EditorLayout.overlaps(topBar, tools), at + " top bar/tools overlap");
        assertFalse(EditorLayout.overlaps(topBar, controls), at + " top bar/controls overlap");
        assertFalse(EditorLayout.overlaps(topBar, status), at + " top bar/status overlap");
        assertFalse(EditorLayout.overlaps(tools, controls), at + " tools/controls overlap");
        assertFalse(EditorLayout.overlaps(tools, status), at + " tools/status overlap");
        assertFalse(EditorLayout.overlaps(controls, status), at + " controls/status overlap");

        if (layout.listVisible()) {
            assertTrue(EditorLayout.inside(screen, list), at + " list escapes screen");
            assertTrue(EditorLayout.inside(list, layout.listHeader()), at + " list header escapes");
            assertTrue(EditorLayout.inside(list, layout.listContent()), at + " list content escapes");
            assertTrue(layout.listContent().y() >= layout.listHeader().bottom(),
                at + " list content over header");
            assertFalse(EditorLayout.overlaps(list, controls), at + " list/controls overlap");
            assertFalse(EditorLayout.overlaps(list, canvasFrame), at + " list/canvas overlap");
            assertFalse(EditorLayout.overlaps(list, tools), at + " list/tools overlap");
            assertFalse(EditorLayout.overlaps(list, status), at + " list/status overlap");
            assertFalse(EditorLayout.overlaps(list, topBar), at + " list/top bar overlap");
        } else {
            assertEquals(Rect.EMPTY, list, at + " hidden list must be empty");
            assertEquals(Rect.EMPTY, layout.listHeader(), at + " hidden header must be empty");
            assertEquals(Rect.EMPTY, layout.listContent(), at + " hidden content must be empty");
        }
        assertTrue(layout.listVisible() == (layout.density() == Density.FULL), at + " list visibility");

        for (EditorLayout.Rect rect : new EditorLayout.Rect[]{
            layout.brand(), layout.undo(), layout.redo(), layout.presets(),
            layout.share(), layout.importExport(), layout.settings()}) {
            assertInside(rect, topBar, at + " top bar widget");
        }
        assertNoOverlap(at + " top bar",
            layout.brand(), layout.undo(), layout.redo(), layout.presets(),
            layout.share(), layout.importExport(), layout.settings());

        assertInside(layout.cancel(), status, at + " cancel");
        assertInside(layout.done(), status, at + " done");
        assertInside(layout.statusText(), status, at + " status text");
        assertFalse(EditorLayout.overlaps(layout.cancel(), layout.done()), at + " cancel/done overlap");
        assertTrue(layout.statusText().right() <= layout.cancel().x(), at + " status text covers cancel");
        assertTrue(layout.cancel().width() > 0 && layout.done().width() > 0, at + " actions unusable");

        Rect previousTab = null;
        for (int tab = 0; tab < EditorLayout.TAB_COUNT; tab++) {
            Rect slot = layout.tab(tab);
            assertInside(slot, layout.tabs(), at + " tab " + tab);
            assertTrue(slot.y() >= layout.content().bottom() || slot.bottom() <= layout.content().y(),
                at + " tab " + tab + " overlaps content");
            if (previousTab != null) assertFalse(EditorLayout.overlaps(slot, previousTab),
                at + " tabs overlap");
            previousTab = slot;
        }

        Rect previousTool = null;
        for (int tool = 0; tool < EditorLayout.TOOL_COUNT; tool++) {
            Rect slot = layout.toolSlot(tool);
            assertInside(slot, tools, at + " tool " + tool);
            assertTrue(slot.width() > 0 && slot.height() > 0, at + " tool " + tool + " unusable");
            if (previousTool != null) assertFalse(EditorLayout.overlaps(slot, previousTool),
                at + " tools overlap");
            previousTool = slot;
        }

        for (int row = 0; row < 10; row++) {
            Rect rect = layout.controlRow(row);
            assertEquals(layout.content().x(), rect.x(), at + " row " + row + " x");
            assertEquals(layout.content().width(), rect.width(), at + " row " + row + " width");
            if (!layout.controlsScrollable()) {
                assertTrue(EditorLayout.inside(layout.content(), rect), at + " row " + row + " escapes");
            }
        }
        assertTrue(layout.controls().width() >= 150, at + " controls too narrow");
        assertTrue(layout.iconsOnly() == (layout.density() != Density.FULL), at + " icon-only tier");
        assertEquals(Rect.EMPTY, layout.notice(), at + " notice only belongs to MINIMAL");
    }

    private static void assertMinimal(Layout layout, Rect screen, String at) {
        Rect notice = layout.notice();
        assertTrue(notice.width() > 0 && notice.height() > 0, at + " notice missing");
        assertInside(notice, screen, at + " notice");
        for (Rect rect : new Rect[]{layout.topBar(), layout.toolColumn(), layout.canvasFrame(),
            layout.canvas(), layout.controls(), layout.listPanel(), layout.tabs(), layout.content(),
            layout.status(), layout.statusText()}) {
            assertEquals(Rect.EMPTY, rect, at + " MINIMAL must not expose " + rect);
        }
        assertInside(layout.cancel(), screen, at + " cancel");
        assertInside(layout.done(), screen, at + " done");
        assertInside(layout.cancel(), notice, at + " cancel outside notice");
        assertInside(layout.done(), notice, at + " done outside notice");
        assertFalse(EditorLayout.overlaps(layout.cancel(), layout.done()), at + " cancel/done overlap");
        assertTrue(layout.cancel().width() > 0 && layout.done().width() > 0, at + " actions unusable");
        assertTrue(layout.minimal(), at + " minimal flag");
    }

    private static void assertNoOverlap(String at, Rect... rects) {
        for (int i = 0; i < rects.length; i++) {
            for (int j = i + 1; j < rects.length; j++) {
                assertFalse(EditorLayout.overlaps(rects[i], rects[j]),
                    at + " widgets " + i + "/" + j + " overlap: " + rects[i] + " " + rects[j]);
            }
        }
    }

    /** Window size divided by GUI scale, rounded up like {@code Window.setGuiScale}. */
    private static int[] logical(int width, int height, int scale) {
        return new int[]{(width + scale - 1) / scale, (height + scale - 1) / scale};
    }

    private static Density density(int width, int height, int scale) {
        int[] size = logical(width, height, scale);
        return EditorLayout.densityFor(size[0], size[1]);
    }

    private static int[][] logicalSizes() {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.List<int[]> sizes = new java.util.ArrayList<>();
        for (int[] window : WINDOWS) {
            for (int scale = 1; scale <= 4; scale++) {
                int[] size = logical(window[0], window[1], scale);
                if (seen.add(size[0] + "x" + size[1])) sizes.add(size);
            }
        }
        for (int[] size : EXTRA) {
            if (seen.add(size[0] + "x" + size[1])) sizes.add(size);
        }
        return sizes.toArray(new int[0][]);
    }

    private static void assertInside(Rect inner, Rect outer, String what) {
        assertTrue(EditorLayout.inside(outer, inner),
            what + " escapes " + outer + ": " + inner);
    }

    private static String lang(String file) throws IOException {
        try (InputStream in = Phase11EditorLayoutTest.class.getClassLoader()
            .getResourceAsStream("assets/oneisle/lang/" + file)) {
            assertNotNull(in, "missing lang resource " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String jsonValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
            .matcher(json);
        assertTrue(matcher.find(), "missing lang key " + key);
        return matcher.group(1);
    }
}
