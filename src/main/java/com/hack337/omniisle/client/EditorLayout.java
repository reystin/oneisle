package com.hack337.omniisle.client;

/**
 * Pure layout math for the editor so fit at different GUI scales can be
 * asserted without booting the client.
 */
public final class EditorLayout {
    public static final int CONTROLS_WIDTH = 230;
    public static final int BIOME_LIST_WIDTH = 186;
    public static final int CONTROLS_ROWS = 8;

    public record Layout(
        boolean compact,
        int previewX, int previewY, int previewSize,
        int controlsX, int controlsY, int controlsWidth, int controlsHeight,
        int biomeListX, int biomeListY, int biomeListHeight,
        int rowStep, int rowH, int tabH, int tabStep,
        int contentY, int bottomY
    ) {
        public int controlsContentBottom() {
            return contentY + (CONTROLS_ROWS - 1) * rowStep + rowH;
        }

        public int controlsPanelBottom() {
            return controlsY - 6 + controlsHeight + 12;
        }

        public int doneButtonBottom() {
            return bottomY + 20;
        }
    }

    private EditorLayout() {}

    public static Layout compute(int width, int height) {
        boolean compact = width < 640 || height < 320;
        int controlsHeight = compact ? 184 : 224;
        int previewSize = compact
            ? Math.max(96, Math.min(140, Math.min(height - 130, width - CONTROLS_WIDTH - 36)))
            : Math.max(124, Math.min(286, Math.min(height - 134, width - 496)));
        int totalWidth = compact
            ? previewSize + 14 + CONTROLS_WIDTH
            : previewSize + 18 + CONTROLS_WIDTH + 18 + BIOME_LIST_WIDTH;
        int previewX = Math.max(10, (width - totalWidth) / 2);
        int previewY = compact ? 32 : Math.max(38, (height - Math.max(previewSize, controlsHeight) - 50) / 2);
        int controlsX = previewX + previewSize + (compact ? 14 : 18);
        int controlsY = previewY;
        int biomeListX = controlsX + CONTROLS_WIDTH + 18;
        int biomeListY = previewY;
        int biomeListHeight = Math.max(previewSize, controlsHeight);

        int rowStep = compact ? 18 : 22;
        int rowH = compact ? 16 : 20;
        int tabH = compact ? 16 : 18;
        int tabStep = tabH + 2;
        int contentY = controlsY + tabStep * 2 + 6;
        int bottomY = Math.min(height - 29,
            Math.max(previewY + previewSize, controlsY + controlsHeight) + 14);

        return new Layout(compact, previewX, previewY, previewSize,
            controlsX, controlsY, CONTROLS_WIDTH, controlsHeight,
            biomeListX, biomeListY, biomeListHeight,
            rowStep, rowH, tabH, tabStep, contentY, bottomY);
    }
}
