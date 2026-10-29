package com.hack337.omniisle.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Flat frame around a vanilla {@link EditBox}. The box itself stays vanilla so IME,
 * selection, narration and accessibility keep the tested 26.3 behaviour; only the
 * border and colours are replaced. Register {@link #box()} as a renderable widget
 * before this frame so text paints on top.
 */
public class OmniTextField extends OmniWidget {
    private final EditBox box;
    private String hint;

    public OmniTextField(Font font, int x, int y, int width, int height, String hint, String initial,
                         Consumer<String> responder) {
        super(x, y, width, height, Component.literal(hint));
        this.hint = hint == null ? "" : hint;
        this.box = new EditBox(font, x + 4, y + 3, width - 8, height - 6, Component.literal(this.hint));
        box.setBordered(false);
        box.setTextShadow(false);
        box.setTextColor(OmniTheme.TEXT);
        box.setTextColorUneditable(OmniTheme.MUTED);
        box.setHint(Component.literal(this.hint));
        box.setValue(initial == null ? "" : initial);
        if (responder != null) box.setResponder(responder);
    }

    public EditBox box() { return box; }

    public String text() { return box.getValue(); }

    public OmniTextField setText(String text) {
        box.setValue(text == null ? "" : text);
        return this;
    }

    public OmniTextField setHint(String hint) {
        this.hint = hint == null ? "" : hint;
        box.setHint(Component.literal(this.hint));
        setMessage(Component.literal(this.hint));
        return this;
    }

    public OmniTextField setMaxLength(int length) {
        box.setMaxLength(length);
        return this;
    }

    public OmniTextField setEditable(boolean editable) {
        box.setEditable(editable);
        return this;
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        if (box != null) box.setX(x + 4);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        if (box != null) box.setY(y + 3);
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        if (box != null) box.setWidth(Math.max(1, width - 8));
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        if (box != null) box.setHeight(Math.max(1, height - 6));
    }

    @Override
    public net.minecraft.client.gui.ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    /** The frame is decoration only: clicks must reach the EditBox registered above it. */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean focused = box.isFocused();
        OmniUi.panel(g, getX(), getY(), getWidth(), getHeight(), OmniTheme.RAISED,
            focused ? OmniTheme.ACCENT : OmniTheme.BORDER);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        box.updateWidgetNarration(output);
    }
}
