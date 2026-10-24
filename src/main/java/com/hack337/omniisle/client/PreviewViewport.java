package com.hack337.omniisle.client;

/**
 * Normalized viewport for the island preview. Centers and spans are kept in
 * map coordinates (0..1) so they survive resolution changes.
 */
public final class PreviewViewport {
    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 8.0;

    private double zoom = MIN_ZOOM;
    private double centerX = 0.5;
    private double centerZ = 0.5;

    public double zoom() { return zoom; }
    public double centerX() { return centerX; }
    public double centerZ() { return centerZ; }
    public double span() { return 1.0 / zoom; }
    public double u0() { return centerX - span() * 0.5; }
    public double v0() { return centerZ - span() * 0.5; }

    public double sourceU(double screenFraction) {
        return centerX + (screenFraction - 0.5) / zoom;
    }

    public double sourceV(double screenFraction) {
        return centerZ + (screenFraction - 0.5) / zoom;
    }

    public double screenFractionU(double sourceU) {
        return 0.5 + (sourceU - centerX) * zoom;
    }

    public double screenFractionV(double sourceV) {
        return 0.5 + (sourceV - centerZ) * zoom;
    }

    /** Zooms while keeping the map point under the cursor anchored. */
    public void zoomAt(double screenFractionU, double screenFractionV, double factor) {
        if (!(factor > 0.0) || !Double.isFinite(factor)) return;
        double anchorU = clamp01(sourceU(screenFractionU));
        double anchorV = clamp01(sourceV(screenFractionV));
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        centerX = clampCenter(anchorU - (screenFractionU - 0.5) / zoom);
        centerZ = clampCenter(anchorV - (screenFractionV - 0.5) / zoom);
    }

    public void panByScreenFraction(double deltaU, double deltaV) {
        centerX = clampCenter(centerX - deltaU / zoom);
        centerZ = clampCenter(centerZ - deltaV / zoom);
    }

    public void reset() {
        zoom = MIN_ZOOM;
        centerX = 0.5;
        centerZ = 0.5;
    }

    private double clampCenter(double value) {
        double half = 0.5 / zoom;
        return Math.max(half, Math.min(1.0 - half, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
