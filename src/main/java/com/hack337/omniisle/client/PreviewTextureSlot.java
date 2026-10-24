package com.hack337.omniisle.client;

/**
 * Tracks one preview texture registration so repeated editor open/close or
 * resolution changes release exactly the texture they registered.
 */
final class PreviewTextureSlot {
    private int resolution = -1;
    private boolean active;

    void ensure(int res, Runnable onRelease, Runnable onCreate) {
        if (active && resolution == res) return;
        if (active) onRelease.run();
        onCreate.run();
        active = true;
        resolution = res;
    }

    void release(Runnable onRelease) {
        if (!active) return;
        onRelease.run();
        active = false;
        resolution = -1;
    }

    boolean active() { return active; }

    int resolution() { return resolution; }
}
