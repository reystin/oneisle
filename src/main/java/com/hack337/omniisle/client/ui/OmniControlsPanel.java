package com.hack337.omniisle.client.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Clipped, scrollable host for the per-tab control rows. Children keep absolute
 * (scrolled) positions so vanilla focus/hover dispatch works without coordinate
 * translation; only painting is clipped.
 */
public class OmniControlsPanel extends AbstractContainerWidget {
    private static final int INSET = 4;
    private static final int SCROLLBAR = 4;

    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final IdentityHashMap<AbstractWidget, Integer> baseY = new IdentityHashMap<>();
    private final OmniScrollArea scroll = new OmniScrollArea();
    private List<AbstractWidget> active = List.of();
    private int contentHeight;
    private int appliedScroll;
    private boolean paintBackground = true;
    private GuiEventListener pressedChild;

    public OmniControlsPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    /** Screens that draw the panel frame themselves can disable the background fill. */
    public OmniControlsPanel setPaintBackground(boolean paintBackground) {
        this.paintBackground = paintBackground;
        return this;
    }

    public void clearContent() {
        widgets.clear();
        baseY.clear();
        active = List.of();
        contentHeight = 0;
        appliedScroll = 0;
        scroll.setContentHeight(0);
        scroll.setViewHeight(0);
    }

    /** Registers a widget and remembers its unscrolled Y. */
    public void add(AbstractWidget widget) {
        widgets.add(widget);
        baseY.put(widget, widget.getY());
    }

    public void setActive(List<AbstractWidget> list) {
        active = list;
        int bottom = getY();
        for (AbstractWidget widget : active) {
            bottom = Math.max(bottom, baseY.getOrDefault(widget, widget.getY()) + widget.getHeight());
        }
        contentHeight = Math.max(0, bottom - (getY() + INSET) + INSET);
        scroll.setViewHeight(viewHeight());
        scroll.setContentHeight(contentHeight);
        scroll.setScroll(0);
        appliedScroll = 0;
        applyScroll();
    }

    public List<AbstractWidget> activeWidgets() {
        return active;
    }

    public boolean scrollable() {
        return contentHeight > viewHeight();
    }

    private int viewHeight() {
        return Math.max(1, getHeight() - INSET * 2);
    }

    private void applyScroll() {
        int delta = scroll.scroll();
        if (delta == appliedScroll) return;
        appliedScroll = delta;
        for (AbstractWidget widget : active) {
            Integer base = baseY.get(widget);
            if (base != null) widget.setY(base - delta);
        }
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return active;
    }

    @Override
    protected int contentHeight() {
        return contentHeight;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        if (paintBackground) {
            OmniUi.panel(g, getX(), getY(), getWidth(), getHeight(), OmniTheme.PANEL, OmniTheme.BORDER);
        }
        scroll.setViewHeight(viewHeight());
        applyScroll();
        int x = getX() + INSET;
        int y = getY() + INSET;
        int w = Math.max(1, getWidth() - INSET - SCROLLBAR - 2);
        int h = viewHeight();
        OmniUi.scissor(g, x, y, w, h);
        for (AbstractWidget widget : active) {
            if (!widget.visible) continue;
            if (widget.getY() + widget.getHeight() <= y || widget.getY() >= y + h) continue;
            widget.extractRenderState(g, mouseX, mouseY, partialTick);
        }
        OmniUi.unscissor(g);
        if (scroll.maxScroll() > 0) {
            OmniUi.scrollbar(g, getX() + getWidth() - SCROLLBAR - 2, y, 2, h,
                scroll.contentHeight(), scroll.viewHeight(), scroll.scroll());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;
        int trackX = getX() + getWidth() - SCROLLBAR - 3;
        if (event.x() >= trackX && scroll.maxScroll() > 0) {
            scroll.clickScrollbar(event.y(), getY() + INSET, viewHeight());
            applyScroll();
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (handled) {
            pressedChild = childAt(event.x(), event.y());
        }
        return handled;
    }

    private GuiEventListener childAt(double mouseX, double mouseY) {
        for (int i = active.size() - 1; i >= 0; i--) {
            AbstractWidget child = active.get(i);
            if (child.visible && child.isMouseOver(mouseX, mouseY)) return child;
        }
        return null;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scroll.dragging()) {
            scroll.dragTo(event.y(), getY() + INSET, viewHeight());
            applyScroll();
            return true;
        }
        if (pressedChild != null && pressedChild.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean was = scroll.dragging();
        scroll.release();
        boolean childHandled = pressedChild != null && pressedChild.mouseReleased(event);
        pressedChild = null;
        return childHandled || was || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0.0) return false;
        for (int i = active.size() - 1; i >= 0; i--) {
            GuiEventListener child = active.get(i);
            if (child.isMouseOver(mouseX, mouseY) && child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        if (!scroll.scrollBy(scrollY)) return false;
        applyScroll();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("omniisle.ui.controls"));
    }
}
