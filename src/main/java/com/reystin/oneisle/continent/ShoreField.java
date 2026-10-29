package com.reystin.oneisle.continent;

/**
 * Explicit v3 shore weight field. The old v1/v2 ribbon was a constant-width
 * continentalness band; this field selects sand/stony/none from distance,
 * low/high frequency patch noise, open-water geometry, altitude and slope.
 *
 * Pure: callers pass a precomputed signed distance and {@link Ring} scan.
 */
public final class ShoreField {
    public static final int NONE = 0;
    public static final int SAND = 1;
    public static final int STONY = 2;

    public static final double BAY_RADIUS = 96.0;
    public static final double INTERTIDAL_LIMIT = -6.0;
    public static final double MAX_INLAND = 28.0;
    public static final double MIN_WIDTH = 8.0;
    /** Ring depth that counts as an open water body (blocks of signed distance). */
    public static final double OPEN_WATER_MIN = 55.0;
    /** Beaches stop on slopes that belong to hills, not coastal shelves. */
    public static final double MAX_HEIGHT_TIER = 1.10;
    /** Beaches stop once continentalness marks the column as inland relief. */
    public static final double MAX_CONTINENTALNESS = 0.20;
    public static final double MIN_BAY = 0.20;

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

    /** Ring scan result: bayness plus the deepest water found on the same 8-point ring. */
    public record Ring(double bay, double minDistance) {}

    /**
     * Single 8-point scan at {@link #BAY_RADIUS} around (x,z). {@code bay} is 1 for a
     * concave coast (bay) and 0 for a headland; {@code minDistance} is the most negative
     * signed distance on the ring, i.e. how much open water the column actually faces.
     * A narrow fjord keeps every ring sample close to the banks, so its minDistance stays
     * shallow even when the local bayness is high.
     */
    public static Ring ring(IslandTerrain terrain, double x, double z, double centerDistance) {
        if (terrain == null) return new Ring(0.5, 0.0);
        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            double d = terrain.signedDistance(
                x + RING_COS[i] * BAY_RADIUS, z + RING_SIN[i] * BAY_RADIUS);
            sum += d;
            if (d < min) min = d;
        }
        double k = (sum / 8.0 - centerDistance) / BAY_RADIUS;
        return new Ring(smoothstep(-0.22, 0.38, k), min);
    }

    /**
     * Noise-varied water-side limit of the intertidal strip, in blocks of
     * signed distance. Always in [-18, 0]: the strip stays "short" but its
     * seaward edge is never a ruler-straight contour.
     */
    public static double intertidalLimit(double x, double z, long seed) {
        int salt = IslandClimate.saltedSeed(seed, 1601);
        double jitter = IslandClimate.fbm(x / 64.0, z / 64.0, salt, 1);
        return Math.max(-18.0, Math.min(0.0, INTERTIDAL_LIMIT - 8.0 * jitter));
    }

    /**
     * Patch mask before the width cutoff (0 = no beach pocket, 1 = full pocket).
     * Used by acceptance tests and the shore debug map. Only single-octave scales
     * >= 95 blocks feed the mask so sand forms contiguous 25-90 block arcs
     * instead of short specks.
     */
    public static double patch(double x, double z, ShoreSettings settings, long seed) {
        if (settings == null || !settings.enabled()) return 0.0;
        int salt = IslandClimate.saltedSeed(seed, 1201);
        double low = IslandClimate.fbm(x / 380.0, z / 380.0, salt, 1);
        double mid = IslandClimate.fbm(x / 145.0, z / 145.0, salt + 250, 1);
        double n01 = clamp01((low * 0.45 + mid * 0.55) * 0.5 + 0.5);
        double presence = 0.88 * settings.density();
        double threshold = 0.72 - 0.95 * presence;
        double pocketNoise = IslandClimate.fbm(x / 95.0, z / 95.0, salt + 700, 1);
        double pocketCut = -0.10 - 0.90 * settings.density();
        double pocket = smoothstep(pocketCut - 0.12, pocketCut + 0.12, pocketNoise);
        if (pocket <= 0.0) return 0.0;
        double gapNoise = IslandClimate.fbm(x / 110.0, z / 110.0, salt + 200, 1);
        double gap = 1.0 - (1.0 - settings.density())
            * (1.0 - smoothstep(-0.34, -0.21, gapNoise));
        return smoothstep(threshold, threshold + 0.12, n01) * gap * pocket;
    }

    /** Local inland width in blocks (4..22 scaled by patch strength). */
    public static double inlandWidth(double x, double z, ShoreSettings settings, long seed, double patchValue) {
        if (settings == null || !settings.enabled() || patchValue <= 0.0) return 0.0;
        int salt = IslandClimate.saltedSeed(seed, 1401);
        double frequency = 170.0 - 60.0 * settings.variety();
        double noise = clamp01(IslandClimate.fbm(x / frequency, z / frequency, salt, 1) * 0.5 + 0.5);
        double coarse = IslandClimate.fbm(x / 190.0, z / 190.0, salt + 450, 1);
        double factor = Math.max(0.50, Math.min(1.0, 1.0 + 0.22 * coarse));
        double edge = clamp01(IslandClimate.fbm(x / 160.0, z / 160.0, salt + 490, 1) * 0.5 + 0.5);
        double notch = smoothstep(0.62, 0.92, edge);
        return (4.0 + 18.0 * noise) * (0.42 + 0.58 * patchValue) * factor * (1.0 - 0.35 * notch);
    }

    /** Shore weights used by the outcome selector and by the debug map. */
    public static double beachWeight(double patch, double bay) {
        return patch * (0.26 + 0.74 * smoothstep(-0.05, 0.30, bay));
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
     * @param heightTier terrain height tier at the column
     * @param continentalness terrain continentalness at the column
     * @param ring open-water scan from {@link #ring(IslandTerrain, double, double, double)}
     * @param settings v3 shore settings (null = legacy)
     * @param seed biome seed
     */
    public static int outcome(double d, double x, double z, double slope, double heightTier,
                              double continentalness, Ring ring, ShoreSettings settings, long seed) {
        if (settings == null || !settings.enabled()) return NONE;
        if (d < intertidalLimit(x, z, seed) || d > MAX_INLAND) return NONE;
        if (heightTier > MAX_HEIGHT_TIER || continentalness > MAX_CONTINENTALNESS) return NONE;
        if (ring == null || ring.minDistance() > -OPEN_WATER_MIN) return NONE;
        double bay = ring.bay();
        if (bay < MIN_BAY) return NONE;

        double patch = patch(x, z, settings, seed);
        if (patch <= 0.0) return NONE;
        double beach = beachWeight(patch, bay);
        if (beach < 0.20) return NONE;
        double width = inlandWidth(x, z, settings, seed, patch)
            * (0.80 + 0.20 * smoothstep(0.22, 0.40, beach));
        if (width < MIN_WIDTH) return NONE;
        if (d > width - 2.0) return NONE;
        if (d < 0.0 && patch < 0.55) return NONE;

        int salt = IslandClimate.saltedSeed(seed, 1501);
        double selector = clamp01(IslandClimate.fbm(x / 150.0, z / 150.0, salt, 1) * 0.5 + 0.5);
        double rockyFactor = settings.rocky() ? 1.0 : 0.0;
        double headland = 1.0 - smoothstep(0.25, 0.55, bay);
        double steep = smoothstep(0.22, 0.58, slope);
        double stonyChance = Math.min(0.75, rockyFactor * (0.48 * headland + 0.30 * steep)
            * (0.55 + 0.45 * patch));
        if (rockyFactor > 0.0 && selector < stonyChance) return STONY;
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
