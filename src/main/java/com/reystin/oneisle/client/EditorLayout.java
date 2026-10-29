package com.reystin.oneisle.client;

/**
 * Pure layout math for the 1.0 editor. {@code Screen.init()} is the only caller;
 * widgets are repositioned from these rectangles on resize and GUI-scale changes.
 *
 * <pre>
 * top bar:  brand | undo redo | presets | share | import/export | settings
 * body:     tool column | canvas + view modes | six tabs + controls | right list
 * status:   X,Z · biome · zoom % · tool · symmetry            [Cancel] [Done]
 * </pre>
 */
public final class EditorLayout {
    public static final int[] RESOLUTIONS = {192, 256, 384};
    public static final int TAB_COUNT = 6;
    public static final int TOOL_COUNT = 8;

    public enum Density { FULL, MEDIUM, COMPACT, MINI, MINIMAL }

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public int centerX() { return x + width / 2; }
        public int centerY() { return y + height / 2; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);
    }

    public record Layout(
        Density density,
        int screenWidth, int screenHeight,
        Rect topBar, Rect brand, Rect undo, Rect redo, Rect presets, Rect share, Rect importExport, Rect settings,
        Rect toolColumn, Rect canvasFrame, Rect canvasModes, Rect canvas,
        Rect tabs, Rect controls, Rect content, Rect listPanel, Rect listHeader, Rect listContent,
        Rect status, Rect statusText, Rect cancel, Rect done, Rect notice,
        int tabColumns, int tabRows, int tabWidth, int tabHeight, int tabGap,
        int rowHeight, int rowStep, int toolColumns, int toolSlotWidth, int toolSlotHeight, int toolStep,
        int contentHeight
    ) {
        public boolean compact() {
            return density == Density.COMPACT || density == Density.MINI || density == Density.MINIMAL;
        }

        public boolean minimal() {
            return density == Density.MINIMAL;
        }

        public boolean listVisible() {
            return density == Density.FULL;
        }

        public boolean iconsOnly() {
            return density != Density.FULL;
        }

        public boolean controlsScrollable() {
            return contentHeight > content.height();
        }

        public Rect previewRect() { return canvas; }
        public Rect controlsPanelRect() { return controls; }
        public Rect biomeListPanelRect() { return listPanel; }

        public Rect toolSlot(int index) {
            if (index < 0 || index >= TOOL_COUNT) return Rect.EMPTY;
            int col = index % toolColumns;
            int row = index / toolColumns;
            int x = toolColumn.x() + 4 + col * (toolSlotWidth + 2);
            int y = toolColumn.y() + 4 + row * toolStep;
            return new Rect(x, y, toolSlotWidth, toolSlotHeight);
        }

        public Rect tab(int index) {
            if (index < 0 || index >= TAB_COUNT) return Rect.EMPTY;
            int col = index % tabColumns;
            int row = index / tabColumns;
            int x = tabs.x() + col * (tabWidth + tabGap);
            int y = tabs.y() + row * (tabHeight + tabGap);
            int w = col == tabColumns - 1 ? tabs.right() - x : tabWidth;
            return new Rect(x, y, w, tabHeight);
        }

        public Rect controlRow(int index) {
            int y = content.y() + index * rowStep;
            return new Rect(content.x(), y, content.width(), rowHeight);
        }

        /** Splits a control row into equal cells with 4-px gaps. */
        public Rect[] controlCells(int index, int parts) {
            Rect row = controlRow(index);
            if (parts <= 0) return new Rect[0];
            int gap = 4;
            int cellWidth = (row.width() - gap * (parts - 1)) / parts;
            Rect[] cells = new Rect[parts];
            for (int i = 0; i < parts; i++) {
                int x = row.x() + i * (cellWidth + gap);
                int w = i == parts - 1 ? row.right() - x : cellWidth;
                cells[i] = new Rect(x, row.y(), w, row.height());
            }
            return cells;
        }
    }

    private EditorLayout() {}

    public static int nextResolution(int current) {
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            if (RESOLUTIONS[i] == current) return RESOLUTIONS[(i + 1) % RESOLUTIONS.length];
        }
        return RESOLUTIONS[0];
    }

    public static boolean overlaps(Rect a, Rect b) {
        if (a.width() <= 0 || a.height() <= 0 || b.width() <= 0 || b.height() <= 0) return false;
        return a.x() < b.right() && b.x() < a.right()
            && a.y() < b.bottom() && b.y() < a.bottom();
    }

    public static boolean inside(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
            && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    /**
     * Places a tooltip next to the cursor inside {@code bounds}. Preference is
     * below-right, below-left, above-right, above-left; the result never leaves
     * the bounds and oversize boxes shrink to them.
     */
    public static Rect placeTooltip(Rect bounds, int mouseX, int mouseY, int width, int height) {
        int[] out = new int[4];
        placeTooltip(bounds, mouseX, mouseY, width, height, out);
        return new Rect(out[0], out[1], out[2], out[3]);
    }

    /** Allocation-free variant for per-frame callers; writes x, y, width, height. */
    public static void placeTooltip(Rect bounds, int mouseX, int mouseY, int width, int height, int[] out) {
        int w = Math.min(Math.max(1, width), bounds.width());
        int h = Math.min(Math.max(1, height), bounds.height());
        int gap = 10;
        int x = mouseX + gap;
        if (x + w > bounds.right()) x = mouseX - gap - w;
        int y = mouseY + gap;
        if (y + h > bounds.bottom()) y = mouseY - gap - h;
        x = Math.max(bounds.x(), Math.min(x, bounds.right() - w));
        y = Math.max(bounds.y(), Math.min(y, bounds.bottom() - h));
        out[0] = x;
        out[1] = y;
        out[2] = w;
        out[3] = h;
    }

    public static Density densityFor(int width, int height) {
        if (width >= 840 && height >= 470) return Density.FULL;
        if (width >= 700 && height >= 420) return Density.MEDIUM;
        if (width >= 400 && height >= 240) return Density.COMPACT;
        if (width >= 320 && height >= 150) return Density.MINI;
        return Density.MINIMAL;
    }

    public static Layout compute(int width, int height) {
        Density density = densityFor(width, height);
        if (density == Density.MINIMAL) return minimal(width, height);

        int margin = density == Density.MINI ? 4 : 8;
        int topBarH = density == Density.MINI ? 24 : 36;
        int statusH = switch (density) {
            case FULL, MEDIUM -> 32;
            case COMPACT -> 26;
            default -> 22;
        };
        int gap = density == Density.MINI ? 4 : 8;

        Rect topBar = new Rect(0, 0, width, topBarH);
        int button = density == Density.MINI ? 18 : 24;
        int barY = (topBarH - button) / 2;
        int brandW = switch (density) {
            case FULL -> 148;
            case MEDIUM -> 112;
            case COMPACT -> 24;
            default -> 16;
        };
        Rect brand = new Rect(margin, barY, brandW, button);
        int left = brand.right() + (density == Density.MINI ? 4 : 8);
        Rect undo = new Rect(left, barY, button, button);
        Rect redo = new Rect(undo.right() + 2, barY, button, button);
        int right = width - margin;
        Rect settings = new Rect(right - button, barY, button, button);
        right = settings.x() - 4;
        int shareW = density == Density.FULL ? 108 : button;
        Rect importExport = new Rect(right - button, barY, button, button);
        right = importExport.x() - 4;
        Rect share = new Rect(right - shareW, barY, shareW, button);
        right = share.x() - (density == Density.MINI ? 3 : 6);
        int presetW = switch (density) {
            case FULL -> 112;
            case MEDIUM -> 96;
            case COMPACT -> 72;
            default -> 44;
        };
        Rect presets = new Rect(right - presetW, barY, presetW, button);

        Rect status = new Rect(0, height - statusH, width, statusH);
        int doneW = switch (density) {
            case FULL, MEDIUM -> 88;
            case COMPACT -> 64;
            default -> 56;
        };
        int actionH = switch (density) {
            case FULL, MEDIUM -> 22;
            case COMPACT -> 20;
            default -> 18;
        };
        int actionY = status.y() + (statusH - actionH) / 2;
        int statusMargin = density == Density.MINI ? 4 : 12;
        Rect done = new Rect(width - statusMargin - doneW, actionY, doneW, actionH);
        Rect cancel = new Rect(done.x() - (density == Density.MINI ? 4 : 8) - doneW, actionY, doneW, actionH);
        Rect statusText = new Rect(statusMargin, status.y(), Math.max(0, cancel.x() - statusMargin - 8), statusH);

        int bodyTop = topBarH + gap;
        int bodyBottom = status.y() - gap;
        int bodyH = Math.max(24, bodyBottom - bodyTop);

        int toolColumns = density == Density.MINI ? 2 : 1;
        int toolW = switch (density) {
            case FULL -> 104;
            case MEDIUM -> 40;
            case COMPACT -> 30;
            default -> 40;
        };
        int toolSlotW = toolColumns == 2 ? (toolW - 10) / 2 : toolW - 8;
        int toolSlotH;
        if (density == Density.FULL || density == Density.MEDIUM) {
            toolSlotH = 30;
        } else if (density == Density.COMPACT) {
            toolSlotH = Math.max(14, Math.min(24, (bodyH - 4 - 7 * 4) / 8));
        } else {
            toolSlotH = 16;
        }
        int toolStep = toolSlotH + (density == Density.MINI ? 2 : 4);
        Rect toolColumn = new Rect(margin, bodyTop, toolW, bodyH);

        int controlsW = switch (density) {
            case FULL -> 260;
            case MEDIUM -> 240;
            case COMPACT -> 208;
            default -> 160;
        };
        int listW = density == Density.FULL ? 260 : 0;
        Rect listPanel = listW > 0
            ? new Rect(width - margin - listW, bodyTop, listW, bodyH)
            : Rect.EMPTY;
        Rect listHeader = listW > 0
            ? new Rect(listPanel.x() + 4, listPanel.y() + 4, listPanel.width() - 8, 26)
            : Rect.EMPTY;
        Rect listContent = listW > 0
            ? new Rect(listPanel.x() + 4, listHeader.bottom() + 4, listPanel.width() - 8,
                listPanel.bottom() - listHeader.bottom() - 8)
            : Rect.EMPTY;
        int controlsX = listW > 0
            ? listPanel.x() - gap - controlsW
            : width - margin - controlsW;
        Rect controls = new Rect(controlsX, bodyTop, controlsW, bodyH);

        int tabColumns = density == Density.MINI ? 6 : 2;
        int tabRows = density == Density.MINI ? 1 : 3;
        int tabH = switch (density) {
            case FULL, MEDIUM -> 22;
            case COMPACT -> 20;
            default -> 18;
        };
        int tabGap = 2;
        Rect tabs = new Rect(controls.x() + 4, controls.y() + 4, controls.width() - 8,
            tabRows * tabH + (tabRows - 1) * tabGap);
        int tabW = (tabs.width() - (tabColumns - 1) * tabGap) / tabColumns;
        Rect content = new Rect(controls.x() + 6, tabs.bottom() + 6, controls.width() - 12,
            Math.max(0, controls.bottom() - tabs.bottom() - 10));

        int rowHeight = switch (density) {
            case FULL, MEDIUM -> 24;
            case COMPACT -> 20;
            default -> 18;
        };
        int rowStep = rowHeight + 4;

        int canvasX = toolColumn.right() + gap;
        int canvasW = Math.max(16, controls.x() - gap - canvasX);
        Rect canvasFrame = new Rect(canvasX, bodyTop, canvasW, bodyH);
        int modeBarH = canvasW < 160 ? 34 : 18;
        int modeRows = modeBarH > 20 ? 2 : 1;
        Rect canvasModes = new Rect(canvasFrame.x(), canvasFrame.y(), canvasFrame.width(), modeBarH);
        int availableW = Math.max(16, canvasFrame.width() - 8);
        int availableH = Math.max(16, canvasFrame.height() - modeBarH - 8);
        int canvasSize = Math.max(16, Math.min(768, Math.min(availableW, availableH)));
        int canvasXCentered = canvasFrame.x() + (canvasFrame.width() - canvasSize) / 2;
        int canvasY = canvasModes.bottom() + 4 + Math.max(0, (availableH - canvasSize) / 2);
        Rect canvas = new Rect(canvasXCentered, canvasY, canvasSize, canvasSize);

        int contentHeight = 0;
        int rowsForContent = switch (density) {
            case FULL, MEDIUM, COMPACT -> 10;
            default -> 8;
        };
        contentHeight = rowsForContent * rowStep;
        // Layout record only knows the nominal content height; screens may use more rows.

        return new Layout(density, width, height,
            topBar, brand, undo, redo, presets, share, importExport, settings,
            toolColumn, canvasFrame, canvasModes, canvas,
            tabs, controls, content, listPanel, listHeader, listContent,
            status, statusText, cancel, done, Rect.EMPTY,
            tabColumns, tabRows, tabW, tabH, tabGap,
            rowHeight, rowStep, toolColumns, toolSlotW, toolSlotH, toolStep,
            contentHeight);
    }

    private static Layout minimal(int width, int height) {
        int noticeW = Math.min(300, Math.max(120, width - 16));
        int noticeH = Math.min(120, Math.max(80, height - 16));
        Rect notice = new Rect((width - noticeW) / 2, (height - noticeH) / 2, noticeW, noticeH);
        int buttonW = Math.min(64, (noticeW - 24) / 2);
        int buttonH = 18;
        Rect done = new Rect(notice.right() - 8 - buttonW, notice.bottom() - 8 - buttonH, buttonW, buttonH);
        Rect cancel = new Rect(done.x() - 6 - buttonW, done.y(), buttonW, buttonH);
        return new Layout(Density.MINIMAL, width, height,
            Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY,
            Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY,
            Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY,
            Rect.EMPTY, Rect.EMPTY, cancel, done, notice,
            1, 1, 0, 0, 0, 18, 22, 1, 0, 0, 0, 0);
    }
}
