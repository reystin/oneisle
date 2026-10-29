package com.hack337.omniisle.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Programmatically generated 16x16 icon cells in an 8x3 atlas. */
public enum OmniIcons {
    BRUSH(0, 0),
    ERASER(1, 0),
    SMOOTH(2, 0),
    FILL(3, 0),
    LINE(4, 0),
    ELLIPSE(5, 0),
    RECTANGLE(6, 0),
    SPAWN(7, 0),
    SYMMETRY(0, 1),
    ZOOM(1, 1),
    GRID(2, 1),
    UNDO(3, 1),
    REDO(4, 1),
    DICE(5, 1),
    COPY(6, 1),
    IMPORT(7, 1),
    EXPORT(0, 2),
    PRESET(1, 2),
    SETTINGS(2, 2),
    CLOSE(3, 2);

    public static final int CELL = 16;
    public static final int COLUMNS = 8;
    public static final int ROWS = 3;
    public static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("omniisle", "textures/gui/icons.png");

    public final int column;
    public final int row;

    OmniIcons(int column, int row) {
        this.column = column;
        this.row = row;
    }

    public int u() { return column * CELL; }
    public int v() { return row * CELL; }

    private static Boolean available;

    /** True when the generated atlas is present; otherwise widgets fall back to text. */
    public static boolean available() {
        if (available != null) return available;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;
        try {
            available = !minecraft.getResourceManager().getResourceStack(TEXTURE).isEmpty();
        } catch (RuntimeException ignored) {
            available = Boolean.FALSE;
        }
        return available;
    }
}
