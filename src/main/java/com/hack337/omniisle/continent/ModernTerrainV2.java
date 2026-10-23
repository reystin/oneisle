package com.hack337.omniisle.continent;

/**
 * Modern terrain generator (v2) for OmniIsle 0.6.0+.
 * Features:
 * - Exact Felzenszwalb-Huttenlocher EDT on native grid with sub-pixel distance convention.
 * - Flat float[] cached SDF grid with optional 3x3 Gaussian smoothing.
 * - Catmull-Rom bicubic interpolation on the continuous SDF field with 4x4 neighbourhood clamping.
 * - Domain warping on mask lookup only (frequency 1/520, amplitude 45; octave 2 at 1/190, amplitude 12).
 * - Near-coast micro-roughness centered at shoreline -0.08 with smoothstep falloff.
 * - Pure, thread-safe, allocation-free evaluation.
 */
public final class ModernTerrainV2 implements IslandTerrain {
    private static final double INF = 1.0e20;

    private final IslandShapeConfig config;
    private final IslandMask mask;
    private final int width;
    private final int height;
    private final double minX;
    private final double maxX;
    private final double minZ;
    private final double maxZ;
    private final double coastWidth;
    private final double oceanTransitionWidth;
    private final double blocksPerPixel;
    private final long seed;
    private final double coastWarp;

    private final float[] sdfGrid;

    public ModernTerrainV2(IslandShapeConfig config) {
        this.config = config;
        this.mask = config.mask != null ? config.mask : new IslandMask(192, 192, -2720, 2720, -2720, 2720, 192.0, 400.0, "");
        this.width = this.mask.width;
        this.height = this.mask.height;
        this.minX = this.mask.minX;
        this.maxX = this.mask.maxX;
        this.minZ = this.mask.minZ;
        this.maxZ = this.mask.maxZ;
        this.coastWidth = this.mask.coastWidth;
        this.oceanTransitionWidth = this.mask.oceanTransitionWidth;
        this.blocksPerPixel = 0.5 * ((maxX - minX) / width + (maxZ - minZ) / height);
        this.seed = config.biomeSeed != 0L ? config.biomeSeed : 0x5EED1234ABCDL;
        this.coastWarp = 45.0;

        this.sdfGrid = buildSdfGrid();
    }

    public IslandShapeConfig config() {
        return config;
    }

    public IslandMask mask() {
        return mask;
    }

    public float[] sdfGrid() {
        return sdfGrid;
    }

    @Override
    public double signedDistance(double worldX, double worldZ) {
        return computeSignedDistance(worldX, worldZ, true);
    }

    @Override
    public double continentalness(double worldX, double worldZ) {
        double worldDistance = computeSignedDistance(worldX, worldZ, true);

        double cBase = worldDistance >= 0.0
            ? -0.08 + worldDistance * 0.70 / coastWidth
            : -0.08 + worldDistance * 0.92 / oceanTransitionWidth;

        // Near-coast micro-roughness centered at shoreline -0.08
        double diff = Math.abs(cBase - (-0.08));
        if (diff < 0.22) {
            double t = 1.0 - diff / 0.22;
            double roughness = IslandNoise.fbm2(worldX / 72.0, worldZ / 72.0, seed + 911L, 3, 2.0, 0.5)
                * 0.045 * IslandNoise.smoothstep(t);
            cBase += roughness;
        }

        return Math.max(-1.0, Math.min(0.70, cBase));
    }

    @Override
    public double erosionBias(double x, double z) {
        return 0.0;
    }

    /**
     * Computes signed distance in blocks.
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @param applyWarp whether to apply low-frequency domain warping
     * @return signed distance in blocks (> 0 inland, < 0 offshore)
     */
    public double computeSignedDistance(double worldX, double worldZ, boolean applyWarp) {
        double targetX = worldX;
        double targetZ = worldZ;

        if (applyWarp && coastWarp > 0.0) {
            double warpFactor = coastWarp / 45.0;
            double wx = (IslandNoise.fbm2(worldX / 520.0, worldZ / 520.0, seed + 101L, 3, 2.0, 0.5) * 45.0
                + IslandNoise.fbm2(worldX / 190.0, worldZ / 190.0, seed + 307L, 2, 2.0, 0.5) * 12.0) * warpFactor;
            double wz = (IslandNoise.fbm2(worldX / 520.0, worldZ / 520.0, seed + 211L, 3, 2.0, 0.5) * 45.0
                + IslandNoise.fbm2(worldX / 190.0, worldZ / 190.0, seed + 401L, 2, 2.0, 0.5) * 12.0) * warpFactor;
            targetX += wx;
            targetZ += wz;
        }

        double sampledX = Math.max(minX, Math.min(maxX, targetX));
        double sampledZ = Math.max(minZ, Math.min(maxZ, targetZ));

        double px = (sampledX - minX) / (maxX - minX) * width - 0.5;
        double pz = (sampledZ - minZ) / (maxZ - minZ) * height - 0.5;

        double pixelDistance = sampleBicubicClamped(px, pz);
        double worldDistance = pixelDistance * blocksPerPixel;

        double outsideX = targetX - sampledX;
        double outsideZ = targetZ - sampledZ;
        if (outsideX != 0.0 || outsideZ != 0.0) {
            double existingOceanDistance = Math.max(0.0, -worldDistance);
            worldDistance = -Math.sqrt(existingOceanDistance * existingOceanDistance
                + outsideX * outsideX + outsideZ * outsideZ);
        }

        return worldDistance;
    }

    /**
     * Catmull-Rom bicubic interpolation on the SDF grid with 4x4 neighbourhood clamping.
     */
    public double sampleBicubicClamped(double px, double pz) {
        px = Math.max(0.0, Math.min(width - 1.0, px));
        pz = Math.max(0.0, Math.min(height - 1.0, pz));

        int x1 = (int) Math.floor(px);
        int z1 = (int) Math.floor(pz);
        double tx = px - x1;
        double tz = pz - z1;

        int x0 = Math.max(0, x1 - 1);
        int x2 = Math.min(width - 1, x1 + 1);
        int x3 = Math.min(width - 1, x1 + 2);

        int z0 = Math.max(0, z1 - 1);
        int z2 = Math.min(height - 1, z1 + 1);
        int z3 = Math.min(height - 1, z1 + 2);

        float minVal = Float.POSITIVE_INFINITY;
        float maxVal = Float.NEGATIVE_INFINITY;

        int[] xs = {x0, x1, x2, x3};
        int[] zs = {z0, z1, z2, z3};

        for (int z : zs) {
            int row = z * width;
            for (int x : xs) {
                float v = sdfGrid[row + x];
                if (v < minVal) minVal = v;
                if (v > maxVal) maxVal = v;
            }
        }

        double r0 = catmullRom1D(sdfGrid[z0 * width + x0], sdfGrid[z0 * width + x1], sdfGrid[z0 * width + x2], sdfGrid[z0 * width + x3], tx);
        double r1 = catmullRom1D(sdfGrid[z1 * width + x0], sdfGrid[z1 * width + x1], sdfGrid[z1 * width + x2], sdfGrid[z1 * width + x3], tx);
        double r2 = catmullRom1D(sdfGrid[z2 * width + x0], sdfGrid[z2 * width + x1], sdfGrid[z2 * width + x2], sdfGrid[z2 * width + x3], tx);
        double r3 = catmullRom1D(sdfGrid[z3 * width + x0], sdfGrid[z3 * width + x1], sdfGrid[z3 * width + x2], sdfGrid[z3 * width + x3], tx);

        double val = catmullRom1D(r0, r1, r2, r3, tz);
        return Math.max(minVal, Math.min(maxVal, val));
    }

    /**
     * Bilinear interpolation on SDF grid (for parity / sign consistency tests).
     */
    public double sampleBilinear(double px, double pz) {
        px = Math.max(0.0, Math.min(width - 1.0, px));
        pz = Math.max(0.0, Math.min(height - 1.0, pz));
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        int x1 = Math.min(width - 1, x0 + 1);
        int z1 = Math.min(height - 1, z0 + 1);
        double tx = px - x0;
        double tz = pz - z0;
        double a = lerp(sdfGrid[z0 * width + x0], sdfGrid[z0 * width + x1], tx);
        double b = lerp(sdfGrid[z1 * width + x0], sdfGrid[z1 * width + x1], tx);
        return lerp(a, b, tz);
    }

    private static double catmullRom1D(double p0, double p1, double p2, double p3, double t) {
        double c0 = p1;
        double c1 = 0.5 * (p2 - p0);
        double c2 = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3;
        double c3 = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3;
        return ((c3 * t + c2) * t + c1) * t + c0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private float[] buildSdfGrid() {
        int paddedWidth = width + 2;
        int paddedHeight = height + 2;
        boolean[] padded = new boolean[paddedWidth * paddedHeight];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                if (mask.isLand(x, z)) {
                    padded[(z + 1) * paddedWidth + (x + 1)] = true;
                }
            }
        }

        double[] toLand = distanceTransform(padded, paddedWidth, paddedHeight, true);
        double[] toOcean = distanceTransform(padded, paddedWidth, paddedHeight, false);

        float[] rawSdf = new float[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int source = (z + 1) * paddedWidth + x + 1;
                int target = z * width + x;
                boolean isLand = mask.isLand(x, z);
                if (isLand) {
                    double dOut = Math.sqrt(toOcean[source]);
                    rawSdf[target] = (float) Math.max(0.0, dOut - 0.5);
                } else {
                    double dIn = Math.sqrt(toLand[source]);
                    rawSdf[target] = (float) -Math.max(0.0, dIn - 0.5);
                }
            }
        }

        // 3x3 Gaussian smoothing pass (separable [0.25, 0.5, 0.25])
        float[] temp = new float[width * height];
        for (int z = 0; z < height; z++) {
            int row = z * width;
            for (int x = 0; x < width; x++) {
                int xPrev = Math.max(0, x - 1);
                int xNext = Math.min(width - 1, x + 1);
                temp[row + x] = 0.25f * rawSdf[row + xPrev] + 0.5f * rawSdf[row + x] + 0.25f * rawSdf[row + xNext];
            }
        }

        float[] smoothed = new float[width * height];
        for (int z = 0; z < height; z++) {
            int zPrev = Math.max(0, z - 1) * width;
            int zCur = z * width;
            int zNext = Math.min(height - 1, z + 1) * width;
            for (int x = 0; x < width; x++) {
                smoothed[zCur + x] = 0.25f * temp[zPrev + x] + 0.5f * temp[zCur + x] + 0.25f * temp[zNext + x];
            }
        }

        return smoothed;
    }

    private static double[] distanceTransform(boolean[] mask, int width, int height, boolean targetValue) {
        double[] first = new double[width * height];
        double[] output = new double[width * height];
        double[] input = new double[Math.max(width, height)];
        double[] transformed = new double[Math.max(width, height)];

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) input[x] = mask[z * width + x] == targetValue ? 0.0 : INF;
            edt1d(input, transformed, width);
            System.arraycopy(transformed, 0, first, z * width, width);
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) input[z] = first[z * width + x];
            edt1d(input, transformed, height);
            for (int z = 0; z < height; z++) output[z * width + x] = transformed[z];
        }
        return output;
    }

    private static void edt1d(double[] input, double[] output, int size) {
        int firstFinite = -1;
        for (int i = 0; i < size; i++) {
            if (input[i] < INF) { firstFinite = i; break; }
        }
        if (firstFinite < 0) {
            for (int i = 0; i < size; i++) output[i] = INF;
            return;
        }
        int[] v = new int[size];
        double[] z = new double[size + 1];
        int k = 0;
        v[0] = firstFinite;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = firstFinite + 1; q < size; q++) {
            if (input[q] >= INF) continue;
            double intersection;
            do {
                int vk = v[k];
                intersection = ((input[q] + q * (double)q) - (input[vk] + vk * (double)vk)) / (2.0 * (q - vk));
                if (intersection <= z[k]) k--;
            } while (intersection <= z[k]);
            k++;
            v[k] = q;
            z[k] = intersection;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < size; q++) {
            while (z[k + 1] < q) k++;
            double delta = q - v[k];
            output[q] = delta * delta + input[v[k]];
        }
    }
}
