package com.hack337.omniisle.continent;

import java.util.Arrays;

/** Universal v3 climate sectors derived from the island mask and biome seed. */
public final class ClimateLayout {
    public enum Province { TEMPERATE, WARM, COLD }

    /**
     * Climate sample. {@code warmStrength}/{@code coldStrength} are the continuous smoothed
     * fields before the province dead zone, so v3 can blend families across band edges
     * instead of switching on the categorical province.
     */
    public record Sample(Province province, double strength,
                         double warmStrength, double coldStrength,
                         double paleStrength, double iceStrength, double cherryStrength) {
        /** Province-only sample: raw strengths follow the province and its strength. */
        public Sample(Province province, double strength,
                      double paleStrength, double iceStrength, double cherryStrength) {
            this(province, strength,
                province == Province.WARM ? strength : 0.0,
                province == Province.COLD ? strength : 0.0,
                paleStrength, iceStrength, cherryStrength);
        }
    }

    private static final Sample TEMPERATE = new Sample(Province.TEMPERATE, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private static final long DEFAULT_SEED = 0x5EED1234ABCDL;

    private final int width;
    private final int height;
    private final double minX;
    private final double maxX;
    private final double minZ;
    private final double maxZ;
    private final float[] warmStrength;
    private final float[] coldStrength;
    private final float[] boundaryNoise;
    private final float[] paleStrength;
    private final float[] iceStrength;
    private final float[] cherryStrength;
    private final int landCount;
    private final int warmLandCount;
    private final int coldLandCount;
    private final float warmThreshold;
    private final float coldThreshold;

    public ClimateLayout(IslandMask mask, long seed) {
        this.width = mask.width;
        this.height = mask.height;
        this.minX = mask.minX;
        this.maxX = mask.maxX;
        this.minZ = mask.minZ;
        this.maxZ = mask.maxZ;
        long saltSeed = seed != 0L ? seed : DEFAULT_SEED;
        int cells = width * height;

        boolean[] land = new boolean[cells];
        int landTotal = 0;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                land[z * width + x] = mask.isLand(x, z);
                if (land[z * width + x]) landTotal++;
            }
        }
        this.landCount = landTotal;
        if (landTotal == 0) {
            this.warmStrength = new float[cells];
            this.coldStrength = new float[cells];
            this.boundaryNoise = new float[cells];
            this.paleStrength = new float[cells];
            this.iceStrength = new float[cells];
            this.cherryStrength = new float[cells];
            this.warmLandCount = 0;
            this.coldLandCount = 0;
            this.warmThreshold = 1.0f;
            this.coldThreshold = 1.0f;
            return;
        }

        double[] warmScores = new double[cells];
        double[] coldScores = new double[cells];
        double[] landWarm = new double[landTotal];
        int wi = 0;
        for (int z = 0; z < height; z++) {
            double wz = worldZ(z);
            double nz = worldZ(z) / Math.max(1.0, Math.max(Math.abs(minZ), Math.abs(maxZ)));
            for (int x = 0; x < width; x++) {
                double wx = worldX(x);
                double nx = worldX(x) / Math.max(1.0, Math.max(Math.abs(minX), Math.abs(maxX)));
                double warm = -nx + 0.20 * (-nz)
                    + 0.10 * IslandNoise.fbm2(wx / 900.0, wz / 900.0, saltSeed + 1301L, 2, 2.0, 0.5);
                double cold = nx + 0.35 * (-nz)
                    + 0.10 * IslandNoise.fbm2(wx / 900.0, wz / 900.0, saltSeed + 1307L, 2, 2.0, 0.5);
                int index = z * width + x;
                warmScores[index] = warm;
                coldScores[index] = cold;
                if (land[index]) landWarm[wi++] = warm;
            }
        }

        int warmTarget = Math.max(1, (int) Math.round(landTotal * 0.16));
        Arrays.sort(landWarm);
        double warmThreshold = landWarm[Math.max(0, landTotal - warmTarget)];

        int nonWarmLand = 0;
        for (int i = 0; i < cells; i++) {
            if (land[i] && warmScores[i] < warmThreshold) nonWarmLand++;
        }
        int coldTarget = Math.min(nonWarmLand, Math.max(1, (int) Math.round(landTotal * 0.12)));
        double[] landCold = new double[Math.max(1, nonWarmLand)];
        int ci = 0;
        for (int i = 0; i < cells; i++) {
            if (land[i] && warmScores[i] < warmThreshold) landCold[ci++] = coldScores[i];
        }
        double coldThreshold = Double.POSITIVE_INFINITY;
        if (ci > 0) {
            Arrays.sort(landCold, 0, ci);
            coldThreshold = landCold[Math.max(0, ci - coldTarget)];
        }

        byte[] provinceGrid = new byte[cells];
        float[] warmRaw = new float[cells];
        float[] coldRaw = new float[cells];
        int warmLand = 0;
        int coldLand = 0;
        for (int i = 0; i < cells; i++) {
            if (land[i]) {
                if (warmScores[i] >= warmThreshold) {
                    provinceGrid[i] = 1;
                    warmRaw[i] = 1.0f;
                    warmLand++;
                } else if (coldScores[i] >= coldThreshold) {
                    provinceGrid[i] = 2;
                    coldRaw[i] = 1.0f;
                    coldLand++;
                }
            } else if (nearLand(land, i)) {
                double wm = warmScores[i] - warmThreshold;
                double cm = coldScores[i] - coldThreshold;
                if (wm >= -0.035 && wm >= cm) {
                    provinceGrid[i] = 1;
                    warmRaw[i] = 1.0f;
                } else if (cm >= -0.035) {
                    provinceGrid[i] = 2;
                    coldRaw[i] = 1.0f;
                }
            }
        }

        float[] paleRaw = palePockets(land, provinceGrid, tempNoise(saltSeed + 1601L, 420.0), 0.030, 0);
        float[] iceRaw = palePockets(land, provinceGrid, tempNoise(saltSeed + 1607L, 260.0), 0.006, 2);
        float[] cherryRaw = palePockets(land, provinceGrid, tempNoise(saltSeed + 1613L, 520.0), 0.36, -1);

        this.warmStrength = smooth(smooth(warmRaw));
        this.coldStrength = smooth(smooth(coldRaw));
        this.warmThreshold = strengthThreshold(this.warmStrength, land, null, 0.0f, 0.16);
        this.coldThreshold = strengthThreshold(this.coldStrength, land, this.warmStrength,
            this.warmThreshold, 0.12);
        this.boundaryNoise = tempNoise(saltSeed + 1621L, 160.0);
        this.paleStrength = paleRaw;
        this.iceStrength = iceRaw;
        this.cherryStrength = cherryRaw;
        this.warmLandCount = warmLand;
        this.coldLandCount = coldLand;
    }
    public static Sample temperate() {
        return TEMPERATE;
    }

    public Sample sample(double worldX, double worldZ) {
        if (landCount == 0) return TEMPERATE;
        double px = (clamp(worldX, minX, maxX) - minX) / (maxX - minX) * width - 0.5;
        double pz = (clamp(worldZ, minZ, maxZ) - minZ) / (maxZ - minZ) * height - 0.5;
        px = clamp(px, 0.0, width - 1.0);
        pz = clamp(pz, 0.0, height - 1.0);
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        int x1 = Math.min(width - 1, x0 + 1);
        int z1 = Math.min(height - 1, z0 + 1);
        double tx = px - x0;
        double tz = pz - z0;
        double warm = bilinear(warmStrength, x0, z0, x1, z1, tx, tz);
        double cold = bilinear(coldStrength, x0, z0, x1, z1, tx, tz);
        double boundary = bilinear(boundaryNoise, x0, z0, x1, z1, tx, tz);
        double pale = bilinear(paleStrength, x0, z0, x1, z1, tx, tz);
        double ice = bilinear(iceStrength, x0, z0, x1, z1, tx, tz);
        double cherry = bilinear(cherryStrength, x0, z0, x1, z1, tx, tz);

        // The province must follow the smoothed strength field, not the nearest mask
        // cell: a nearest-cell lookup reproduces the editor grid as 20-block stairs.
        // A low-frequency jitter on the threshold keeps the contour organic and the
        // bilinear field keeps it continuous between cells.
        double warmEdge = warm + boundary * 0.22;
        double coldEdge = cold + boundary * 0.22;
        Province province = Province.TEMPERATE;
        double strength = 0.0;
        if (warmEdge >= warmThreshold && warmEdge >= coldEdge) {
            province = Province.WARM;
            strength = warm;
        } else if (coldEdge >= coldThreshold) {
            province = Province.COLD;
            strength = cold;
        }
        if (province != Province.TEMPERATE && strength < 0.16) {
            province = Province.TEMPERATE;
            strength = 0.0;
        }
        return new Sample(province, clamp01(strength), clamp01(warm), clamp01(cold),
            clamp01(pale), clamp01(ice), clamp01(cherry));
    }

    public int landCount() { return landCount; }
    public int warmLandCount() { return warmLandCount; }
    public int coldLandCount() { return coldLandCount; }

    private float[] tempNoise(long seed, double scale) {
        float[] values = new float[width * height];
        for (int z = 0; z < height; z++) {
            double wz = worldZ(z);
            for (int x = 0; x < width; x++) {
                values[z * width + x] = (float) IslandNoise.fbm2(worldX(x) / scale, wz / scale, seed, 2, 2.0, 0.5);
            }
        }
        return values;
    }

    private float[] palePockets(boolean[] land, byte[] provinceGrid, float[] values,
                                double fraction, int requiredProvince) {
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (!land[i]) continue;
            if (requiredProvince >= 0 && provinceGrid[i] != requiredProvince) continue;
            count++;
        }
        float[] result = new float[values.length];
        if (count == 0) return result;
        float[] sorted = new float[count];
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (!land[i]) continue;
            if (requiredProvince >= 0 && provinceGrid[i] != requiredProvince) continue;
            sorted[index++] = values[i];
        }
        Arrays.sort(sorted);
        int take = Math.max(1, (int) Math.round(count * fraction));
        float threshold = sorted[Math.max(0, count - take)];
        for (int i = 0; i < values.length; i++) {
            if (!land[i]) continue;
            if (requiredProvince >= 0 && provinceGrid[i] != requiredProvince) continue;
            if (values[i] >= threshold) result[i] = 1.0f;
        }
        return result;
    }

    private boolean nearLand(boolean[] land, int index) {
        int x = index % width;
        int z = index / width;
        int radius = 10;
        for (int dz = -radius; dz <= radius; dz++) {
            int nz = z + dz;
            if (nz < 0 || nz >= height) continue;
            int row = nz * width;
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x + dx;
                if (nx < 0 || nx >= width) continue;
                if (land[row + nx]) return true;
            }
        }
        return false;
    }

    private float[] smooth(float[] source) {
        int w = width;
        int h = height;
        float[] temp = new float[source.length];
        for (int z = 0; z < h; z++) {
            int row = z * w;
            for (int x = 0; x < w; x++) {
                temp[row + x] = (float) ((source[row + Math.max(0, x - 2)] * 1.0
                    + source[row + Math.max(0, x - 1)] * 4.0
                    + source[row + x] * 6.0
                    + source[row + Math.min(w - 1, x + 1)] * 4.0
                    + source[row + Math.min(w - 1, x + 2)] * 1.0) / 16.0);
            }
        }
        float[] result = new float[source.length];
        for (int z = 0; z < h; z++) {
            int zPrev2 = Math.max(0, z - 2) * w;
            int zPrev1 = Math.max(0, z - 1) * w;
            int zCur = z * w;
            int zNext1 = Math.min(h - 1, z + 1) * w;
            int zNext2 = Math.min(h - 1, z + 2) * w;
            for (int x = 0; x < w; x++) {
                result[zCur + x] = (float) ((temp[zPrev2 + x] * 1.0
                    + temp[zPrev1 + x] * 4.0
                    + temp[zCur + x] * 6.0
                    + temp[zNext1 + x] * 4.0
                    + temp[zNext2 + x] * 1.0) / 16.0);
            }
        }
        return result;
    }

    /**
     * Threshold that keeps the same land fraction after smoothing, so a smoothed
     * 16 % / 12 % binary sector does not grow or shrink its sampled area.
     */
    private float strengthThreshold(float[] values, boolean[] land, float[] warmValues,
                                    float warmCut, double fraction) {
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (!land[i]) continue;
            if (warmValues != null && warmValues[i] >= warmCut) continue;
            count++;
        }
        if (count == 0) return 1.0f;
        float[] sorted = new float[count];
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (!land[i]) continue;
            if (warmValues != null && warmValues[i] >= warmCut) continue;
            sorted[index++] = values[i];
        }
        Arrays.sort(sorted);
        int take = Math.min(count, Math.max(1, (int) Math.round(landCount * fraction)));
        return sorted[count - take];
    }

    private double bilinear(float[] values, int x0, int z0, int x1, int z1,
                            double tx, double tz) {
        int w = width;
        double a = values[z0 * w + x0] + (values[z0 * w + x1] - values[z0 * w + x0]) * tx;
        double b = values[z1 * w + x0] + (values[z1 * w + x1] - values[z1 * w + x0]) * tx;
        return a + (b - a) * tz;
    }

    private double worldX(int x) {
        return minX + ((x + 0.5) / width) * (maxX - minX);
    }

    private double worldZ(int z) {
        return minZ + ((z + 0.5) / height) * (maxZ - minZ);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
