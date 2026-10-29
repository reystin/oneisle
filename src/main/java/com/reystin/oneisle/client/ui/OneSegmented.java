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

/** Segmented selector for presets, relief, themes, tools and layers. */
public class OneSegmented extends OneWidget {
    private String[] labels;
    private OneIcons[] icons;
    private int selected;
    private IntConsumer onChange;
    private boolean iconOnly;

    public OneSegmented(int x, int y, int width, int height, String[] labels, int selected, IntConsumer onChange) {
        this(x, y, width, height, labels, null, selected, onChange);
    }

    public OneSegmented(int x, int y, int width, int height, String[] labels, OneIcons[] icons,
                         int selected, IntConsumer onChange) {
        super(x, y, width, height, Component.literal(labels.length == 0 ? "" : labels[Math.max(0, selected)]));
        this.labels = labels;
        this.icons = icons;
        this.selected = Math.max(0, Math.min(selected, labels.length - 1));
        this.onChange = onChange;
    }

    public OneSegmented iconOnly() {
        this.iconOnly = true;
        return this;
    }

    public int selected() { return selected; }
    public String[] labels() { return labels; }

    public OneSegmented setSelected(int selected) {
        this.selected = Math.max(0, Math.min(selected, labels.length - 1));
        updateMessage();
        return this;
    }

    public OneSegmented setLabels(String[] labels) {
        this.labels = labels;
        this.selected = Math.max(0, Math.min(selected, labels.length - 1));
        updateMessage();
        return this;
    }

    public OneSegmented setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    private void updateMessage() {
        if (labels.length > 0) setMessage(Component.literal(labels[selected]));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        float alpha = active ? 1.0F : 0.5F;
        OneUi.panel(g, x, y, w, h, OneTheme.withAlpha(OneTheme.RAISED, alpha),
            OneTheme.withAlpha(OneTheme.BORDER, alpha));
        int count = labels.length;
        if (count == 0) return;
        int cellWidth = w / count;
        int hovered = hoverIndex(mouseX, mouseY);
        for (int i = 0; i < count; i++) {
            int cx = x + i * cellWidth;
            int cw = i == count - 1 ? x + w - cx : cellWidth;
            boolean isSelected = i == selected;
            if (isSelected) {
                g.fill(cx + 1, y + 1, cx + cw - 1, y + h - 1,
                    OneTheme.withAlpha(OneTheme.lerp(OneTheme.RAISED, OneTheme.ACCENT, 0.16F), alpha));
                g.fill(cx + 1, y + h - 2, cx + cw - 1, y + h - 1, OneTheme.withAlpha(OneTheme.ACCENT, alpha));
            } else if (i == hovered && active) {
                g.fill(cx + 1, y + 1, cx + cw - 1, y + h - 1,
                    OneTheme.withAlpha(OneTheme.BORDER, 0.55F * hover));
            }
            int color = OneTheme.withAlpha(isSelected ? OneTheme.ACCENT : OneTheme.TEXT, alpha);
            int iconSize = Math.min(16, h - 4);
            boolean drawIcon = icons != null && i < icons.length && OneIcons.available();
            if (drawIcon) {
                int iconX = cx + (cw - iconSize) / 2;
                if (!iconOnly) iconX = cx + Math.max(4, (cw - (iconSize + 4 + labelWidth(i))) / 2);
                OneUi.icon(g, icons[i], iconX, y + (h - iconSize) / 2, iconSize, color);
                if (!iconOnly) {
                    OneUi.textFit(g, font, labels[i], iconX + iconSize + 4, OneUi.verticalCenter(font, y, h),
                        cw - (iconX - cx) - iconSize - 8, color);
                }
            } else {
                OneUi.textCentered(g, font, labels[i], cx + cw / 2, OneUi.verticalCenter(font, y, h),
                    cw - 6, color);
            }
        }
        if (focus > 0.02F) {
            g.outline(x, y, w, h, OneTheme.withAlpha(OneTheme.FOCUS, focus * alpha));
        }
    }

    private int labelWidth(int index) {
        return OneMetrics.width(labels[index]);
    }

    private int hoverIndex(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY) || labels.length == 0) return -1;
        int cellWidth = getWidth() / labels.length;
        if (cellWidth <= 0) return -1;
        return Math.max(0, Math.min(labels.length - 1, (int) ((mouseX - getX()) / cellWidth)));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return;
        int index = hoverIndex(event.x(), event.y());
        if (index < 0) return;
        select(index);
    }

    private void select(int index) {
        if (index == selected) return;
        selected = index;
        updateMessage();
        if (onChange != null) onChange.accept(index);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused() || !active) return super.keyPressed(event);
        int key = event.key();
        if (key == InputConstants.KEY_LEFT) {
            select(Math.max(0, selected - 1));
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            select(Math.min(labels.length - 1, selected + 1));
            return true;
        }
        if (key == InputConstants.KEY_HOME) {
            select(0);
            return true;
        }
        if (key == InputConstants.KEY_END) {
            select(labels.length - 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        if (labels.length > 0) {
            output.add(NarratedElementType.TITLE, Component.literal(labels[Math.max(0, Math.min(selected, labels.length - 1))]));
        }
    }
}
