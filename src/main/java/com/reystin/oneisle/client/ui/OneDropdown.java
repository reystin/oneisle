package com.reystin.oneisle.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Overlay menu used by the presets button. Add last so it paints above the editor. */
public class OneDropdown extends OneWidget {
    private static final int PAD = 4;
    private static final int MAX_ROWS = 9;

    private final String[] items;
    private final int itemHeight;
    private final IntConsumer onPick;
    private final Runnable onClose;
    private final String title;
    private final OneScrollArea scroll = new OneScrollArea();
    private int cursor;
    private int hoverIndex = -1;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public OneDropdown(int x, int y, int width, String title, String[] items, int selected,
                        IntConsumer onPick, Runnable onClose) {
        super(x, y, width, heightFor(items.length), Component.literal(title));
        this.title = title;
        this.items = items.clone();
        this.itemHeight = 20;
        this.onPick = onPick;
        this.onClose = onClose;
        this.cursor = Math.max(0, Math.min(selected, items.length - 1));
        int rows = Math.min(MAX_ROWS, items.length);
        this.listH = rows * itemHeight;
        this.scroll.setViewHeight(listH);
        this.scroll.setContentHeight(items.length * itemHeight);
        this.scroll.setScroll(cursor * itemHeight - listH / 2);
        this.listX = x + PAD;
        this.listY = y + PAD;
        this.listW = width - PAD * 2 - (items.length > MAX_ROWS ? 6 : 0);
    }

    private static int heightFor(int count) {
        return Math.min(MAX_ROWS, Math.max(0, count)) * 20 + PAD * 2;
    }

    public int cursor() { return cursor; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x88000000);
        OneUi.raised(g, x, y, w, h, OneTheme.PANEL, OneTheme.BORDER);
        hoverIndex = rowAt(mouseX, mouseY);
        OneUi.scissor(g, listX, listY, listW, listH);
        int first = scroll.scroll() / itemHeight;
        int last = Math.min(items.length, first + MAX_ROWS + 1);
        for (int i = first; i < last; i++) {
            int rowY = listY + i * itemHeight - scroll.scroll();
            boolean selected = i == cursor;
            if (selected) {
                g.fill(listX, rowY, listX + listW, rowY + itemHeight,
                    OneTheme.withAlpha(OneTheme.ACCENT, 0.16F));
            } else if (i == hoverIndex) {
                g.fill(listX, rowY, listX + listW, rowY + itemHeight, OneTheme.RAISED);
            }
            OneUi.textFit(g, font, items[i], listX + 8, OneUi.verticalCenter(font, rowY, itemHeight),
                listW - 16, selected ? OneTheme.ACCENT : OneTheme.TEXT);
        }
        OneUi.unscissor(g);
        if (items.length > MAX_ROWS) {
            OneUi.scrollbar(g, x + w - PAD - 4, listY, 3, listH, scroll.contentHeight(), listH, scroll.scroll());
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY || mouseY >= listY + listH) return -1;
        int index = (int) ((mouseY - listY + scroll.scroll()) / itemHeight);
        return index >= 0 && index < items.length ? index : -1;
    }

    private void pick(int index) {
        if (index < 0 || index >= items.length) return;
        cursor = index;
        if (onPick != null) onPick.accept(index);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return;
        int row = rowAt(event.x(), event.y());
        if (row >= 0) {
            pick(row);
        } else if (isMouseOver(event.x(), event.y())) {
            cursor = Math.max(0, Math.min(items.length - 1, (int) ((event.y() - listY + scroll.scroll()) / itemHeight)));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        return scroll.scrollBy(scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (onClose != null) onClose.run();
            return true;
        }
        if (key == InputConstants.KEY_UP) {
            cursor = Math.max(0, cursor - 1);
            ensureVisible(cursor);
            return true;
        }
        if (key == InputConstants.KEY_DOWN) {
            cursor = Math.min(items.length - 1, cursor + 1);
            ensureVisible(cursor);
            return true;
        }
        if (key == InputConstants.KEY_RETURN) {
            pick(cursor);
            return true;
        }
        return super.keyPressed(event);
    }

    private void ensureVisible(int index) {
        int top = index * itemHeight;
        if (top < scroll.scroll()) scroll.setScroll(top);
        int bottom = top + itemHeight;
        if (bottom > scroll.scroll() + listH) scroll.setScroll(bottom - listH);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(title));
        if (cursor >= 0 && cursor < items.length) output.add(NarratedElementType.HINT, Component.literal(items[cursor]));
    }
}
