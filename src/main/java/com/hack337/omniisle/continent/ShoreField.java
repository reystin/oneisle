package com.hack337.omniisle.continent;

/**
 * Explicit v3 shore weight field. The old v1/v2 ribbon was a constant-width
 * continentalness band; this field selects sand/stony/none from distance,
 * low/high frequency patch noise, bay geometry and slope.
 *
 * Pure and allocation-free: callers pass a precomputed signed distance and bayness.
 */
public final class ShoreField {
    public static final int NONE = 0;
    public static final int SAND = 1;
    public static final int STONY = 2;

    public static final double BAY_RADIUS = 60.0;
    public static final double INTERTIDAL_LIMIT = -6.0;
    public static final double MAX_INLAND = 34.0;

    private static final double[] RING_COS = new double[8];
    private static final double[] RING_SIN = new double[8];

    static {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 0.25;
            RING_COS[i] = Math.cos(angle);
            RING_SIN[i] = Math.sin(angle);
        }
    }

    private ShoreField() {}

    /**
     * Bayness in [0,1]: 1 for a concave coast (bay), 0 for a headland.
     * Samples the signed-distance field on an 8-point ring around (x,z).
     */
    public static double bayness(IslandTerrain terrain, double x, double z, double centerDistance) {
        if (terrain == null) return 0.5;
        double sum = 0.0;
        for (int i = 0; i < 8; i++) {
            sum += terrain.signedDistance(x + RING_COS[i] * BAY_RADIUS, z + RING_SIN[i] * BAY_RADIUS);
        }
        double mean = sum / 8.0;
        double k = (mean - centerDistance) / BAY_RADIUS;
        return smoothstep(-0.15, 0.25, k);
    }

    /**
     * Noise-varied water-side limit of the intertidal strip, in blocks of
     * signed distance. Always in [-18, 0]: the strip stays "short" but its
     * seaward edge is never a ruler-straight contour.
     */
    public static double intertidalLimit(double x, double z, long seed) {
        int salt = IslandClimate.saltedSeed(seed, 1601);
        double jitter = IslandClimate.fbm(x / 16.0, z / 16.0, salt);
        return Math.max(-18.0, Math.min(0.0, INTERTIDAL_LIMIT - 12.0 * jitter));
    }

    /**
     * Patch mask before the width cutoff (0 = no beach pocket, 1 = full pocket).
     * Used by acceptance tests and the shore debug map.
     */
    public static double patch(double x, double z, ShoreSettings settings, long seed) {
        if (settings == null || !settings.enabled()) return 0.0;
        int salt = IslandClimate.saltedSeed(seed, 1201);
        double low = IslandClimate.fbm(x / 380.0, z / 380.0, salt);
        double mid = IslandClimate.fbm(x / 130.0, z / 130.0, salt + 250);
        double high = IslandClimate.fbm(x / 55.0, z / 55.0, salt + 100);
        double jitter = IslandClimate.fbm(x / 17.0, z / 17.0, salt + 400);
        double n01 = clamp01((low * 0.30 + mid * 0.35 + high * 0.35) * 0.5 + 0.5 + 0.10 * jitter);
        double presence = 0.88 * settings.density();
        double threshold = 0.72 - 0.95 * presence;
        double gapNoise = IslandClimate.fbm(x / 70.0, z / 70.0, salt + 200);
        if (gapNoise < -0.34) return 0.0;
        double gap = smoothstep(-0.34, -0.21, gapNoise);
        return smoothstep(threshold, threshold + 0.12, n01) * gap;
    }

    /** Local inland width in blocks (3..26 scaled by patch strength). */
    public static double inlandWidth(double x, double z, ShoreSettings settings, long seed, double patchValue) {
        if (settings == null || !settings.enabled() || patchValue <= 0.0) return 0.0;
        int salt = IslandClimate.saltedSeed(seed, 1401);
        double frequency = 170.0 - 60.0 * settings.variety();
        double noise = clamp01(IslandClimate.fbm(x / frequency, z / frequency, salt) * 0.5 + 0.5);
        double coarse = 0.80 * IslandClimate.fbm(x / 20.0, z / 20.0, salt + 450);
        double fine = 0.50 * IslandClimate.fbm(x / 9.0, z / 9.0, salt + 470);
        double factor = Math.max(0.25, Math.min(1.15, 1.0 + coarse + fine));
        double edge = clamp01(IslandClimate.fbm(x / 27.0, z / 27.0, salt + 490) * 0.5 + 0.5);
        double notch = smoothstep(0.52, 0.88, edge);
        return (3.0 + 23.0 * noise) * patchValue * factor * (1.0 - 0.45 * notch);
    }

    /** Shore weights used by the outcome selector and by the debug map. */
    public static double beachWeight(double patch, double bay) {
        return patch * (0.35 + 0.65 * bay);
    }

    public static double stonyWeight(double patch, double bay, double slope) {
        double steep = smoothstep(0.22, 0.58, slope);
        return (1.0 - bay) * (0.45 + 0.55 * steep) * patch;
    }

    /**
     * Selects the shore outcome for a column.
     *
     * @param d signed distance in blocks (positive inland)
     * @param x world X
     * @param z world Z
     * @param slope height-gradient magnitude (continentalness units x 64)
     * @param bay bayness from {@link #bayness(IslandTerrain, double, double, double)}
     * @param settings v3 shore settings (null = legacy)
     * @param seed biome seed
     */
    public static int outcome(double d, double x, double z, double slope, double bay,
                              ShoreSettings settings, long seed) {
        if (settings == null || !settings.enabled()) return NONE;
        if (d < intertidalLimit(x, z, seed) || d > MAX_INLAND) return NONE;

        double patch = patch(x, z, settings, seed);
        if (patch <= 0.0) return NONE;
        double width = inlandWidth(x, z, settings, seed, patch);
        if (d > width) return NONE;
        if (d < 0.0 && patch < 0.55) return NONE;

        int salt = IslandClimate.saltedSeed(seed, 1501);
        double selector = clamp01(IslandClimate.fbm(x / 33.0, z / 33.0, salt) * 0.5 + 0.5);
        double beach = beachWeight(patch, bay);
        if (beach < 0.20) return NONE;
        double rockyFactor = settings.rocky() ? 1.0 : 0.0;
        double stonyChance = rockyFactor * (0.26 + 0.35 * stonyWeight(patch, bay, slope)
            + 0.30 * smoothstep(0.22, 0.58, slope));
        if (rockyFactor > 0.0 && bay < 0.78 && selector < stonyChance) {
            return STONY;
        }
        return SAND;
    }

    static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }
}
