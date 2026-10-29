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
import net.minecraft.util.FormattedCharSequence;

/** Inline destructive confirmation with two flat buttons. */
public class OmniPopover extends OmniWidget {
    private static final int PAD = 10;
    private static final int BUTTON_H = 20;

    private final String title;
    private final String message;
    private final String confirmLabel;
    private final String cancelLabel;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final List<FormattedCharSequence> lines = new ArrayList<>();

    public OmniPopover(int x, int y, int width, Font font, String title, String message,
                       String confirmLabel, String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        super(x, y, width, 0, Component.literal(title));
        this.title = title;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        lines.addAll(font.split(Component.literal(message), width - PAD * 2));
        setHeight(PAD * 2 + font.lineHeight + 4 + lines.size() * (font.lineHeight + 1) + 10 + BUTTON_H);
    }

    public int confirmY() {
        return getY() + getHeight() - PAD - BUTTON_H;
    }

    public int confirmX() {
        return getX() + getWidth() - PAD - confirmWidth();
    }

    public int cancelX() {
        return confirmX() - 6 - cancelWidth();
    }

    public int confirmWidth() {
        return Math.min(120, OmniMetrics.width(confirmLabel) + 20);
    }

    public int cancelWidth() {
        return Math.min(100, OmniMetrics.width(cancelLabel) + 20);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x99000000);
        OmniUi.raised(g, x, y, w, h, OmniTheme.PANEL, OmniTheme.BORDER);
        g.fill(x + 1, y + 2, x + 3, y + h - 2, OmniTheme.DANGER);
        OmniUi.textFit(g, font, title, x + PAD, y + PAD, w - PAD * 2, OmniTheme.TEXT);
        int cursorY = y + PAD + font.lineHeight + 4;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x + PAD, cursorY, OmniTheme.MUTED);
            cursorY += font.lineHeight + 1;
        }
        int confirmY = confirmY();
        drawButton(g, font, confirmX(), confirmY, confirmWidth(), confirmLabel, true, mouseX, mouseY);
        drawButton(g, font, cancelX(), confirmY, cancelWidth(), cancelLabel, false, mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label,
                            boolean destructive, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON_H;
        int fill = destructive
            ? OmniTheme.lerp(OmniTheme.RAISED, 0xFF55302F, hovered ? 1.0F : 0.35F)
            : OmniTheme.lerp(OmniTheme.RAISED, 0xFF2A303B, hovered ? 1.0F : 0.0F);
        int color = destructive ? OmniTheme.DANGER : OmniTheme.TEXT;
        OmniUi.panel(g, x, y, w, BUTTON_H, fill, destructive ? OmniTheme.DANGER : OmniTheme.BORDER);
        OmniUi.textCentered(g, font, label, x + w / 2, OmniUi.verticalCenter(font, y, BUTTON_H), w - 8, color);
    }

    private boolean inConfirm(double mouseX, double mouseY) {
        return mouseX >= confirmX() && mouseX < confirmX() + confirmWidth()
            && mouseY >= confirmY() && mouseY < confirmY() + BUTTON_H;
    }

    private boolean inCancel(double mouseX, double mouseY) {
        return mouseX >= cancelX() && mouseX < cancelX() + cancelWidth()
            && mouseY >= confirmY() && mouseY < confirmY() + BUTTON_H;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        if (inConfirm(event.x(), event.y())) {
            if (onConfirm != null) onConfirm.run();
        } else if (inCancel(event.x(), event.y())) {
            if (onCancel != null) onCancel.run();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (onCancel != null) onCancel.run();
            return true;
        }
        if (key == InputConstants.KEY_RETURN) {
            if (onConfirm != null) onConfirm.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(title));
        output.add(NarratedElementType.HINT, Component.literal(message));
    }
}
