package com.hack337.omniisle.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Six editor tabs laid out by {@code EditorLayout}; icons plus labels when they fit. */
public class OmniTabBar extends OmniWidget {
    private final int[] slotX = new int[6];
    private final int[] slotY = new int[6];
    private final int[] slotW = new int[6];
    private final int[] slotH = new int[6];
    private final String[] labels;
    private final OmniIcons[] icons;
    private int selected;
    private IntConsumer onChange;

    public OmniTabBar(int x, int y, int width, int height, String[] labels, OmniIcons[] icons,
                      int selected, IntConsumer onChange) {
        super(x, y, width, height, Component.literal(labels.length == 0 ? "" : labels[Math.max(0, selected)]));
        this.labels = labels.clone();
        this.icons = icons.clone();
        this.selected = Math.max(0, Math.min(selected, labels.length - 1));
        this.onChange = onChange;
    }

    public void setSlot(int index, int x, int y, int width, int height) {
        if (index < 0 || index >= slotX.length) return;
        slotX[index] = x;
        slotY[index] = y;
        slotW[index] = width;
        slotH[index] = height;
    }

    public int selected() { return selected; }

    public OmniTabBar setSelected(int selected) {
        this.selected = Math.max(0, Math.min(selected, labels.length - 1));
        setMessage(Component.literal(labels[this.selected]));
        return this;
    }

    public OmniTabBar setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    public OmniTabBar setLabels(String[] labels) {
        if (labels.length == this.labels.length) System.arraycopy(labels, 0, this.labels, 0, labels.length);
        setMessage(Component.literal(this.labels[Math.max(0, Math.min(selected, this.labels.length - 1))]));
        return this;
    }

    public String label(int index) {
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int hovered = tabAt(mouseX, mouseY);
        float alpha = active ? 1.0F : 0.5F;
        for (int i = 0; i < labels.length; i++) {
            if (slotW[i] <= 0) continue;
            int x = slotX[i];
            int y = slotY[i];
            int w = slotW[i];
            int h = slotH[i];
            boolean isSelected = i == selected;
            if (isSelected) {
                g.fill(x, y, x + w, y + h, OmniTheme.withAlpha(OmniTheme.RAISED, alpha));
                g.fill(x, y + h - 2, x + w, y + h, OmniTheme.withAlpha(OmniTheme.ACCENT, alpha));
            } else if (i == hovered && active) {
                g.fill(x, y, x + w, y + h, OmniTheme.withAlpha(OmniTheme.BORDER, 0.55F * hover));
            }
            int color = OmniTheme.withAlpha(isSelected ? OmniTheme.TEXT : OmniTheme.MUTED, alpha);
            int iconSize = Math.min(16, h - 4);
            boolean iconsDrawn = icons != null && i < icons.length && OmniIcons.available();
            String text = labels[i];
            int textWidth = OmniMetrics.width(text);
            boolean showLabel = x + w >= 8 && w >= iconSize + textWidth + 12 && h >= 18;
            int contentWidth = (iconsDrawn ? iconSize + (showLabel ? 4 : 0) : 0) + (showLabel ? textWidth : 0);
            int contentX = x + Math.max(2, (w - contentWidth) / 2);
            if (iconsDrawn) {
                OmniUi.icon(g, icons[i], contentX, y + (h - iconSize) / 2, iconSize, color);
                contentX += iconSize + (showLabel ? 4 : 0);
            }
            if (showLabel) {
                OmniUi.text(g, font, text, contentX, OmniUi.verticalCenter(font, y, h - 2), color);
            } else if (!iconsDrawn) {
                OmniUi.textCentered(g, font, text, x + w / 2, OmniUi.verticalCenter(font, y, h - 2), w - 4, color);
            }
        }
        if (focus > 0.02F && selected >= 0 && selected < labels.length && slotW[selected] > 0) {
            g.outline(slotX[selected], slotY[selected], slotW[selected], slotH[selected],
                OmniTheme.withAlpha(OmniTheme.FOCUS, focus * alpha));
        }
    }

    public int tabAt(double mouseX, double mouseY) {
        for (int i = 0; i < labels.length; i++) {
            if (mouseX >= slotX[i] && mouseX < slotX[i] + slotW[i]
                && mouseY >= slotY[i] && mouseY < slotY[i] + slotH[i]) return i;
        }
        return -1;
    }

    private void select(int index) {
        if (index < 0 || index >= labels.length || index == selected) return;
        selected = index;
        setMessage(Component.literal(labels[selected]));
        if (onChange != null) onChange.accept(selected);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        int index = tabAt(event.x(), event.y());
        if (index >= 0) select(index);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused() || !active) return super.keyPressed(event);
        int key = event.key();
        if (key == InputConstants.KEY_LEFT) {
            select((selected + labels.length - 1) % labels.length);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            select((selected + 1) % labels.length);
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
        if (selected >= 0 && selected < labels.length) {
            output.add(NarratedElementType.TITLE, Component.literal(labels[selected]));
        }
    }
}
