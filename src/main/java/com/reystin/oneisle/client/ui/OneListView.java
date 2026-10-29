package com.reystin.oneisle.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Virtualised scrollable rows with wheel, drag, keyboard navigation and a thin scrollbar. */
public abstract class OneListView extends OneWidget {
    protected static final int SCROLLBAR = 5;

    protected final OneScrollArea scroll = new OneScrollArea();
    protected int rowHeight;
    protected int rowCount;
    protected int hovered = -1;
    protected int selected = -1;
    private boolean scrollbarDrag;

    protected OneListView(int x, int y, int width, int height, int rowHeight) {
        super(x, y, width, height, Component.empty());
        this.rowHeight = Math.max(12, rowHeight);
        this.scroll.setViewHeight(height);
        this.scroll.setContentHeight(0);
    }

    public void setRowCount(int count) {
        this.rowCount = Math.max(0, count);
        if (selected >= rowCount) selected = rowCount - 1;
        scroll.setContentHeight(rowCount * rowHeight);
    }

    public int rowCount() { return rowCount; }
    public int rowHeight() { return rowHeight; }
    public int selected() { return selected; }
    public int hoveredIndex() { return hovered; }
    public OneScrollArea scrollArea() { return scroll; }

    public void setSelected(int index) {
        selected = Math.max(-1, Math.min(index, rowCount - 1));
        ensureVisible(selected);
    }

    public void setRowHeight(int rowHeight) {
        this.rowHeight = Math.max(12, rowHeight);
        scroll.setContentHeight(rowCount * this.rowHeight);
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        scroll.setViewHeight(Math.max(0, height));
    }

    protected int contentX() { return getX() + 2; }
    protected int contentY() { return getY() + 2; }
    protected int contentW() { return Math.max(1, getWidth() - 4 - SCROLLBAR); }
    protected int contentH() { return Math.max(1, getHeight() - 4); }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        OneUi.panel(g, getX(), getY(), getWidth(), getHeight(), OneTheme.PANEL, OneTheme.BORDER);
        hovered = rowAt(mouseX, mouseY);
        int x = contentX();
        int y = contentY();
        int w = contentW();
        int h = contentH();
        OneUi.scissor(g, x, y, w, h);
        int first = Math.max(0, scroll.scroll() / rowHeight);
        int last = Math.min(rowCount, first + h / rowHeight + 2);
        for (int i = first; i < last; i++) {
            int rowY = y + i * rowHeight - scroll.scroll();
            if (rowY + rowHeight <= y || rowY >= y + h) continue;
            boolean rowSelected = i == selected;
            boolean rowHover = i == hovered;
            drawRow(g, font, i, x, rowY, w, rowHeight, rowSelected, rowHover, mouseX, mouseY);
        }
        OneUi.unscissor(g);
        int trackX = getX() + getWidth() - SCROLLBAR - 1;
        OneUi.scrollbar(g, trackX, y, 2, h, scroll.contentHeight(), scroll.viewHeight(), scroll.scroll());
    }

    protected abstract void drawRow(GuiGraphicsExtractor g, Font font, int index, int x, int y, int width,
                                    int height, boolean selected, boolean hovered, int mouseX, int mouseY);

    protected void rowClicked(int index, int localX, int localY, MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) selected = index;
    }

    protected void rowActivated(int index) {
    }

    public int rowAt(double mouseX, double mouseY) {
        int x = contentX();
        int y = contentY();
        int w = contentW();
        int h = contentH();
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return -1;
        int index = (int) ((mouseY - y + scroll.scroll()) / rowHeight);
        return index >= 0 && index < rowCount ? index : -1;
    }

    public int rowScreenY(int index) {
        return contentY() + index * rowHeight - scroll.scroll();
    }

    protected boolean inScrollbar(double mouseX, double mouseY) {
        int trackX = getX() + getWidth() - SCROLLBAR - 1;
        return mouseX >= trackX - 2 && mouseX < trackX + 4
            && mouseY >= contentY() && mouseY < contentY() + contentH();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        if (inScrollbar(event.x(), event.y())) {
            scrollbarDrag = true;
            scroll.clickScrollbar(event.y(), contentY(), contentH());
            return;
        }
        int index = rowAt(event.x(), event.y());
        if (index < 0) return;
        rowClicked(index, (int) event.x() - contentX(), (int) event.y() - contentY(), event);
        if (doubleClick) rowActivated(index);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scrollbarDrag) {
            scroll.dragTo(event.y(), contentY(), contentH());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean was = scrollbarDrag;
        scrollbarDrag = false;
        scroll.release();
        super.mouseReleased(event);
        return was;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0.0) return false;
        return scroll.scrollBy(scrollY);
    }

    private void ensureVisible(int index) {
        if (index < 0) return;
        int top = index * rowHeight;
        if (top < scroll.scroll()) scroll.setScroll(top);
        int bottom = top + rowHeight;
        if (bottom > scroll.scroll() + contentH()) scroll.setScroll(bottom - contentH());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused() || !active) return super.keyPressed(event);
        int key = event.key();
        if (key == InputConstants.KEY_UP) {
            setSelected(Math.max(0, selected - 1));
            return true;
        }
        if (key == InputConstants.KEY_DOWN) {
            setSelected(Math.min(rowCount - 1, selected + 1));
            return true;
        }
        if (key == InputConstants.KEY_HOME) {
            setSelected(0);
            return true;
        }
        if (key == InputConstants.KEY_END) {
            setSelected(rowCount - 1);
            return true;
        }
        if (key == InputConstants.KEY_RETURN && selected >= 0) {
            rowActivated(selected);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("oneisle.ui.list.narration"));
        if (selected >= 0) {
            output.add(NarratedElementType.HINT,
                Component.translatable("oneisle.ui.list.row", selected + 1));
        }
    }
}
