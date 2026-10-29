package com.hack337.omniisle.client.ui;

/** Time-based hover/focus/press easing so transitions do not depend on frame rate. */
public final class OmniInteraction {
    private float hover;
    private float focus;
    private float press;
    private long lastMs = -1L;
    private boolean pointerDown;
    private boolean enabled = true;

    public void tick(boolean hovered, boolean focused, boolean down, long nowMs) {
        if (lastMs < 0L) lastMs = nowMs;
        long elapsed = Math.max(0L, Math.min(100L, nowMs - lastMs));
        lastMs = nowMs;
        pointerDown = down;
        float step = elapsed / (float) OmniTheme.TRANSITION_MS;
        hover = approach(hover, enabled && hovered ? 1.0F : 0.0F, step);
        focus = approach(focus, enabled && focused ? 1.0F : 0.0F, step);
        press = approach(press, enabled && down ? 1.0F : 0.0F, step);
    }

    public void reset(long nowMs) {
        lastMs = nowMs;
        hover = 0.0F;
        focus = 0.0F;
        press = 0.0F;
        pointerDown = false;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            hover = 0.0F;
            focus = 0.0F;
            press = 0.0F;
            pointerDown = false;
        }
    }

    public boolean enabled() { return enabled; }
    public float hover() { return hover; }
    public float focus() { return focus; }
    public float press() { return press; }
    public boolean pointerDown() { return pointerDown; }

    private static float approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    /** Deterministic 0..1 ramp used by tests: linear over {@link OmniTheme#TRANSITION_MS}. */
    public static float ramp(long elapsedMs) {
        return Math.max(0.0F, Math.min(1.0F, elapsedMs / (float) OmniTheme.TRANSITION_MS));
    }
}
