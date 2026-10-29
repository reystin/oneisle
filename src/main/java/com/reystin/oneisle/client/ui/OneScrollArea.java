package com.reystin.oneisle.client.ui;

/** Pure scroll state shared by controls and list views. */
public final class OneScrollArea {
    private int contentHeight;
    private int viewHeight;
    private int scroll;
    private boolean dragging;
    private int dragOffset;

    public void setContentHeight(int contentHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        scroll = OneUi.clampScroll(scroll, this.contentHeight, viewHeight);
    }

    public void setViewHeight(int viewHeight) {
        this.viewHeight = Math.max(0, viewHeight);
        scroll = OneUi.clampScroll(scroll, contentHeight, this.viewHeight);
    }

    public int contentHeight() { return contentHeight; }
    public int viewHeight() { return viewHeight; }
    public int scroll() { return scroll; }
    public boolean dragging() { return dragging; }

    public void setScroll(int scroll) {
        this.scroll = OneUi.clampScroll(scroll, contentHeight, viewHeight);
    }

    public int maxScroll() {
        return Math.max(0, contentHeight - viewHeight);
    }

    public boolean scrollBy(double notches) {
        int before = scroll;
        scroll = OneUi.clampScroll(scroll - (int) Math.round(notches * 18.0), contentHeight, viewHeight);
        return scroll != before;
    }

    public boolean clickScrollbar(double mouseY, int trackY, int trackHeight) {
        int thumb = thumbHeight(trackHeight);
        int offset = thumbOffset(trackHeight);
        if (mouseY >= trackY + offset && mouseY < trackY + offset + thumb) {
            dragging = true;
            dragOffset = (int) mouseY - (trackY + offset);
            return true;
        }
        dragging = true;
        dragOffset = thumb / 2;
        dragTo(mouseY, trackY, trackHeight);
        return true;
    }

    public boolean dragTo(double mouseY, int trackY, int trackHeight) {
        if (!dragging) return false;
        int travel = trackHeight - thumbHeight(trackHeight);
        if (travel <= 0 || maxScroll() == 0) return false;
        int position = (int) mouseY - trackY - dragOffset;
        int next = Math.round(position * (float) maxScroll() / travel);
        int before = scroll;
        scroll = OneUi.clampScroll(next, contentHeight, viewHeight);
        return scroll != before;
    }

    public void release() {
        dragging = false;
    }

    public int thumbHeight(int trackHeight) {
        if (contentHeight <= viewHeight || contentHeight <= 0) return trackHeight;
        return Math.max(12, trackHeight * viewHeight / contentHeight);
    }

    public int thumbOffset(int trackHeight) {
        int thumb = thumbHeight(trackHeight);
        int travel = trackHeight - thumb;
        if (travel <= 0 || maxScroll() == 0) return 0;
        return travel * scroll / maxScroll();
    }
}
