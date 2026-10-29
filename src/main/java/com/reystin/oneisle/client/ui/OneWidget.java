package com.reystin.oneisle.client.ui;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Shared flat-widget state: time-based animation, press tracking and focus skipping. */
public abstract class OneWidget extends AbstractWidget {
    protected final OneInteraction fx = new OneInteraction();
    protected boolean pointerDown;
    protected float hover;
    protected float press;
    protected float focus;

    protected OneWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected void animate(int mouseX, int mouseY) {
        if (!active || !visible) {
            fx.setEnabled(false);
            hover = 0.0F;
            press = 0.0F;
            focus = 0.0F;
            return;
        }
        fx.setEnabled(true);
        fx.tick(isMouseOver(mouseX, mouseY), isFocused(), pointerDown, Util.getMillis());
        hover = fx.hover();
        press = fx.press();
        focus = fx.focus();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!visible || !active || event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
            || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        pointerDown = true;
        onClick(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        pointerDown = false;
        return super.mouseReleased(event);
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!visible || !active) return null;
        return super.nextFocusPath(event);
    }

    protected boolean activateKey(KeyEvent event) {
        int key = event.key();
        return key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
            || key == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE;
    }

    public float hoverAmount() { return hover; }
    public float pressAmount() { return press; }
    public float focusAmount() { return focus; }
}
