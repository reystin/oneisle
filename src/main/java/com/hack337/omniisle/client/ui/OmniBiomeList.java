package com.hack337.omniisle.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Shared biome/structure rows: swatch, name, toggle, weight stepper, actual share. */
public class OmniBiomeList extends OmniListView {
    public static final class Row {
        public final String id;
        public String name;
        public int color;
        public boolean enabled;
        public boolean weighted = true;
        public int weight = 100;
        public String actual = "";
        public OmniTheme.Status status = OmniTheme.Status.NEUTRAL;
        public String tooltip = "";
        public OmniIcons icon;
        public String editing;
        private int cachedWeight = Integer.MIN_VALUE;
        private String cachedWeightText = "";

        public Row(String id, String name, int color, boolean enabled) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.enabled = enabled;
        }

        /** Cached decimal weight so steady-state row rendering does not allocate. */
        public String weightText() {
            if (cachedWeight != weight) {
                cachedWeight = weight;
                cachedWeightText = Integer.toString(weight);
            }
            return cachedWeightText;
        }
    }

    public interface Listener {
        void onToggle(int index, boolean enabled);
        void onWeight(int index, int delta);
        void onWeightEdit(int index, int mouseX, int mouseY);
        void onActivate(int index);
    }

    private final List<Row> rows = new ArrayList<>();
    private Listener listener;
    private Row hoverRow;

    public OmniBiomeList(int x, int y, int width, int height, int rowHeight) {
        super(x, y, width, height, rowHeight);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        setRowCount(rows.size());
        hoverRow = null;
    }

    public List<Row> rows() { return rows; }

    public Row row(int index) {
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    public Row hoveredRow() { return hoverRow; }

    /** Tooltip text for the row under the cursor, empty when none. */
    public String tooltipAt(double mouseX, double mouseY) {
        int index = rowAt(mouseX, mouseY);
        if (index < 0) return "";
        Row row = rows.get(index);
        return row.tooltip == null ? "" : row.tooltip;
    }

    private int nameX() { return 44; }
    private int stepperWidth() { return 68; }
    private int actualWidth() { return 58; }

    private int stepperX(int width) { return width - actualWidth() - stepperWidth() - 4; }
    private int actualX(int width) { return width - actualWidth() - 2; }

    @Override
    protected void drawRow(GuiGraphicsExtractor g, Font font, int index, int x, int y, int width,
                           int height, boolean selected, boolean hovered, int mouseX, int mouseY) {
        Row row = rows.get(index);
        if (selected) {
            g.fill(x, y, x + width, y + height, OmniTheme.withAlpha(OmniTheme.ACCENT, 0.12F));
        } else if (hovered) {
            g.fill(x, y, x + width, y + height, OmniTheme.RAISED);
        } else if ((index & 1) == 1) {
            g.fill(x, y, x + width, y + height, 0x08000000);
        }
        int centerY = y + height / 2;
        int toggleW = 22;
        int toggleH = 12;
        int toggleX = x + 5;
        int toggleY = centerY - toggleH / 2;
        int trackColor = row.enabled ? OmniTheme.ACCENT : OmniTheme.BORDER;
        OmniUi.panel(g, toggleX, toggleY, toggleW, toggleH, trackColor, 0);
        int knobX = toggleX + (row.enabled ? toggleW - 9 : 3);
        g.fill(knobX, toggleY + 2, knobX + 6, toggleY + toggleH - 2,
            row.enabled ? OmniTheme.TEXT_DARK : OmniTheme.MUTED);
        if (row.icon != null && OmniIcons.available()) {
            OmniUi.icon(g, row.icon, x + 31, centerY - 6, 12, row.enabled ? OmniTheme.TEXT : OmniTheme.MUTED);
        } else {
            OmniUi.swatch(g, x + 31, centerY - 5, 10, 10, row.enabled ? row.color : OmniTheme.MUTED,
                OmniTheme.BORDER);
        }
        int rightReserved = 4 + (row.weighted ? actualWidth() + stepperWidth() + 4 : actualWidth());
        int nameWidth = width - nameX() - rightReserved - 4;
        OmniUi.textFit(g, font, row.name, x + nameX(), OmniUi.verticalCenter(font, y, height),
            nameWidth, row.enabled ? OmniTheme.TEXT : OmniTheme.MUTED);
        if (row.weighted) {
            int sx = stepperX(width);
            int valueX = sx + 14;
            int plusX = valueX + 32;
            boolean overMinus = mouseX >= x + sx && mouseX < x + sx + 14
                && mouseY >= y && mouseY < y + height;
            boolean overValue = mouseX >= x + valueX && mouseX < x + valueX + 32
                && mouseY >= y && mouseY < y + height;
            boolean overPlus = mouseX >= x + plusX && mouseX < x + plusX + 14
                && mouseY >= y && mouseY < y + height;
            drawStepperCell(g, font, x + sx, y + 2, 14, height - 4, "-", overMinus, row.enabled);
            drawStepperCell(g, font, x + valueX, y + 2, 32, height - 4,
                row.editing != null ? row.editing : row.weightText(), overValue, row.enabled);
            drawStepperCell(g, font, x + plusX, y + 2, 14, height - 4, "+", overPlus, row.enabled);
        }
        int ax = actualX(width);
        int actualColor = row.enabled ? OmniTheme.statusColor(row.status) : OmniTheme.MUTED;
        if (!row.actual.isEmpty()) {
            OmniUi.statusGlyph(g, row.enabled ? row.status : OmniTheme.Status.NEUTRAL,
                x + ax, centerY - 4, 8, actualColor);
            OmniUi.textFit(g, font, row.actual, x + ax + 11, OmniUi.verticalCenter(font, y, height),
                actualWidth() - 12, actualColor);
        }
        hoverRow = hovered ? row : hoverRow;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hoverRow = null;
        super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawStepperCell(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label,
                                 boolean hovered, boolean enabled) {
        int fill = hovered && enabled ? OmniTheme.BORDER : OmniTheme.PANEL;
        g.fill(x, y, x + w, y + h, fill);
        OmniUi.textCentered(g, font, label, x + w / 2, OmniUi.verticalCenter(font, y, h), w - 2,
            enabled ? OmniTheme.TEXT : OmniTheme.MUTED);
    }

    @Override
    protected void rowClicked(int index, int localX, int localY, MouseButtonEvent event) {
        Row row = rows.get(index);
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        int rowY = rowScreenY(index) - contentY();
        int localRowY = localY - rowY;
        int width = contentW();
        if (localX >= 3 && localX < 30 && localRowY >= 0 && localRowY < rowHeight) {
            if (listener != null) listener.onToggle(index, !row.enabled);
            return;
        }
        if (row.weighted) {
            int sx = stepperX(width);
            int valueX = sx + 14;
            int plusX = valueX + 32;
            if (localX >= sx && localX < sx + 14) {
                if (listener != null) listener.onWeight(index, -5);
                return;
            }
            if (localX >= valueX && localX < valueX + 32) {
                if (listener != null) listener.onWeightEdit(index, (int) event.x(), (int) event.y());
                return;
            }
            if (localX >= plusX && localX < plusX + 14) {
                if (listener != null) listener.onWeight(index, 5);
                return;
            }
        }
        selected = index;
        if (!row.weighted) {
            if (listener != null) listener.onActivate(index);
        }
    }

    @Override
    protected void rowActivated(int index) {
        Row row = rows.get(index);
        if (listener != null) listener.onToggle(index, !row.enabled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused() || !active) return super.keyPressed(event);
        int index = selected;
        if (index >= 0 && index < rows.size()) {
            Row row = rows.get(index);
            int key = event.key();
            if (key == InputConstants.KEY_LEFT && row.weighted) {
                if (listener != null) listener.onWeight(index, -5);
                return true;
            }
            if (key == InputConstants.KEY_RIGHT && row.weighted) {
                if (listener != null) listener.onWeight(index, 5);
                return true;
            }
            if (key == InputConstants.KEY_SPACE) {
                if (listener != null) listener.onToggle(index, !row.enabled);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        Row row = row(selected);
        if (row == null) {
            output.add(NarratedElementType.TITLE, Component.translatable("omniisle.ui.list.narration"));
            return;
        }
        output.add(NarratedElementType.TITLE, Component.literal(row.name));
        output.add(NarratedElementType.HINT,
            Component.translatable(row.enabled ? "options.on" : "options.off"));
        if (row.weighted) output.add(NarratedElementType.USAGE, Component.literal(Integer.toString(row.weight)));
        if (!row.actual.isEmpty()) output.add(NarratedElementType.USAGE, Component.literal(row.actual));
    }
}
