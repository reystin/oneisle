package com.hack337.omniisle.test;

import com.hack337.omniisle.client.EditorLayout;
import com.hack337.omniisle.client.EditorLayout.Density;
import com.hack337.omniisle.client.EditorLayout.Layout;
import com.hack337.omniisle.client.EditorLayout.Rect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.0 UI geometry: density budgets, centered canvas, cells and tab slots at every tier. */
public class Phase11UiLayoutTest {
    @Test
    @DisplayName("11L.1 density tiers stay inside the planned budgets")
    void densityBudgets() {
        Layout full = EditorLayout.compute(1280, 720);
        assertEquals(Density.FULL, full.density());
        assertFalse(full.iconsOnly());
        assertTrue(full.listVisible());
        assertTrue(full.controls().width() >= 230 && full.controls().width() <= 280,
            "FULL controls " + full.controls().width());
        assertTrue(full.listPanel().width() >= 220 && full.listPanel().width() <= 300,
            "FULL list " + full.listPanel().width());
        assertTrue(full.toolColumn().width() >= 80, "FULL tool column must carry labels");

        Layout medium = EditorLayout.compute(800, 500);
        assertEquals(Density.MEDIUM, medium.density());
        assertTrue(medium.iconsOnly());
        assertFalse(medium.listVisible());
        assertTrue(medium.controls().width() >= 230 && medium.controls().width() <= 260,
            "MEDIUM controls " + medium.controls().width());
        assertEquals(Rect.EMPTY, medium.listPanel());

        Layout compact = EditorLayout.compute(500, 300);
        assertEquals(Density.COMPACT, compact.density());
        assertTrue(compact.iconsOnly());
        assertTrue(compact.controls().width() >= 200 && compact.controls().width() <= 220,
            "COMPACT controls " + compact.controls().width());
        assertTrue(compact.controlsScrollable(), "COMPACT controls must scroll");

        Layout mini = EditorLayout.compute(320, 150);
        assertEquals(Density.MINI, mini.density());
        assertTrue(mini.iconsOnly());
        assertTrue(mini.controls().width() >= 150 && mini.controls().width() <= 170,
            "MINI controls " + mini.controls().width());
        assertEquals(2, mini.toolColumns(), "MINI tools use two columns");
        assertEquals(6, mini.tabColumns(), "MINI tabs use one six-column row");
        assertEquals(1, mini.tabRows());
        assertTrue(mini.controlsScrollable());
        assertTrue(mini.canvas().width() >= 16 && mini.canvas().height() >= 16);
        assertTrue(EditorLayout.inside(mini.status(), mini.done()));
        assertTrue(EditorLayout.inside(mini.status(), mini.cancel()));

        Layout minimal = EditorLayout.compute(300, 140);
        assertEquals(Density.MINIMAL, minimal.density());
        assertTrue(minimal.minimal());
        assertEquals(Rect.EMPTY, minimal.topBar());
        assertEquals(Rect.EMPTY, minimal.canvas());
        assertEquals(Rect.EMPTY, minimal.controls());
        assertTrue(minimal.notice().width() > 0);
    }

    @Test
    @DisplayName("11L.2 control cells split rows without gaps or overlaps")
    void controlCells() {
        for (int width : new int[]{320, 400, 640, 800, 1280, 1920}) {
            for (int height : new int[]{150, 240, 320, 480, 720, 1080}) {
                Layout layout = EditorLayout.compute(width, height);
                if (layout.minimal()) continue;
                String at = width + "x" + height;
                for (int rowIndex = 0; rowIndex < 10; rowIndex++) {
                    Rect row = layout.controlRow(rowIndex);
                    for (int parts = 1; parts <= 4; parts++) {
                        Rect[] cells = layout.controlCells(rowIndex, parts);
                        assertEquals(parts, cells.length);
                        int previousRight = row.x();
                        int total = 0;
                        for (int i = 0; i < parts; i++) {
                            Rect cell = cells[i];
                            assertTrue(EditorLayout.inside(row, cell), at + " cell escapes row");
                            assertEquals(row.y(), cell.y(), at + " cell y");
                            assertEquals(row.height(), cell.height(), at + " cell height");
                            if (i > 0) {
                                assertEquals(previousRight + 4, cell.x(),
                                    at + " expected a 4-px gap between cells");
                            }
                            previousRight = cell.right();
                            total += cell.width();
                        }
                        assertEquals(row.right(), previousRight, at + " cells must fill the row");
                        assertEquals(row.width() - 4 * (parts - 1), total,
                            at + " cells + gaps must cover the row");
                        assertTrue(layout.controlCells(rowIndex, 0).length == 0);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("11L.3 tab and tool slots tile their panels exactly")
    void slotsTilePanels() {
        for (int[] size : new int[][]{{1920, 1080}, {1280, 720}, {854, 480}, {640, 320}, {400, 240}, {320, 150}}) {
            Layout layout = EditorLayout.compute(size[0], size[1]);
            if (layout.minimal()) continue;
            String at = size[0] + "x" + size[1];
            Rect previous = null;
            for (int i = 0; i < EditorLayout.TAB_COUNT; i++) {
                Rect tab = layout.tab(i);
                assertTrue(EditorLayout.inside(layout.tabs(), tab), at + " tab " + i);
                assertTrue(tab.y() >= layout.content().bottom() || tab.bottom() <= layout.content().y(),
                    at + " tab " + i + " overlaps content");
                if (previous != null) {
                    assertFalse(EditorLayout.overlaps(previous, tab), at + " tabs overlap");
                    if (tab.y() == previous.y()) {
                        assertEquals(previous.right() + layout.tabGap(), tab.x(),
                            at + " tab gap " + i);
                    }
                }
                previous = tab;
            }
            assertEquals(layout.tabs().x(), layout.tab(0).x(), at + " first tab must start at the panel");
            assertEquals(layout.tabs().right(), layout.tab(EditorLayout.TAB_COUNT - 1).right(),
                at + " last tab must end at the panel");

            previous = null;
            for (int i = 0; i < EditorLayout.TOOL_COUNT; i++) {
                Rect slot = layout.toolSlot(i);
                assertTrue(EditorLayout.inside(layout.toolColumn(), slot), at + " tool " + i);
                assertTrue(slot.width() > 0 && slot.height() > 0, at + " empty tool slot");
                if (previous != null) {
                    assertFalse(EditorLayout.overlaps(previous, slot), at + " tool slots overlap");
                }
                previous = slot;
            }
            Rect last = layout.toolSlot(EditorLayout.TOOL_COUNT - 1);
            assertTrue(last.bottom() <= layout.toolColumn().bottom(), at + " last tool leaves the column");
        }
    }

    @Test
    @DisplayName("11L.4 canvas is centered in its frame and never covers surrounding panels")
    void centeredCanvas() {
        for (int[] size : new int[][]{{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {320, 180}}) {
            Layout layout = EditorLayout.compute(size[0], size[1]);
            String at = size[0] + "x" + size[1];
            Rect frame = layout.canvasFrame();
            Rect canvas = layout.canvas();
            assertTrue(EditorLayout.inside(frame, canvas), at + " canvas escapes frame");
            int left = canvas.x() - frame.x();
            int right = frame.right() - canvas.right();
            assertTrue(Math.abs(left - right) <= 1, at + " canvas not horizontally centered");
            assertTrue(canvas.y() >= layout.canvasModes().bottom(), at + " canvas covers mode bar");
            assertFalse(EditorLayout.overlaps(canvas, layout.controls()), at + " canvas/controls");
            assertFalse(EditorLayout.overlaps(canvas, layout.toolColumn()), at + " canvas/tools");
            assertFalse(EditorLayout.overlaps(canvas, layout.status()), at + " canvas/status");
            if (layout.listVisible()) {
                assertFalse(EditorLayout.overlaps(canvas, layout.listPanel()), at + " canvas/list");
            }
        }
    }

    @Test
    @DisplayName("11L.5 tooltip placement has an allocation-free twin")
    void tooltipAllocationFree() {
        Rect bounds = new Rect(10, 20, 300, 220);
        int[] out = new int[4];
        for (int x = 10; x <= 310; x += 37) {
            for (int y = 20; y <= 240; y += 29) {
                EditorLayout.placeTooltip(bounds, x, y, 120, 54, out);
                Rect expected = EditorLayout.placeTooltip(bounds, x, y, 120, 54);
                assertEquals(expected.x(), out[0]);
                assertEquals(expected.y(), out[1]);
                assertEquals(expected.width(), out[2]);
                assertEquals(expected.height(), out[3]);
                assertTrue(EditorLayout.inside(bounds, expected));
                assertTrue(out[0] >= bounds.x() && out[0] + out[2] <= bounds.right());
                assertTrue(out[1] >= bounds.y() && out[1] + out[3] <= bounds.bottom());
            }
        }
    }
}
