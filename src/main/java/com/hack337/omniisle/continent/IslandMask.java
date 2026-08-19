package com.hack337.omniisle.continent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Base64;

/** A compact bitmap that both the editor and the server interpret identically. */
public final class IslandMask {
    private static final double INF = 1.0e20;

    public static final Codec<IslandMask> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(2, 4096).fieldOf("width").forGetter(m -> m.width),
        Codec.intRange(2, 4096).fieldOf("height").forGetter(m -> m.height),
        Codec.DOUBLE.fieldOf("min_x").forGetter(m -> m.minX),
        Codec.DOUBLE.fieldOf("max_x").forGetter(m -> m.maxX),
        Codec.DOUBLE.fieldOf("min_z").forGetter(m -> m.minZ),
        Codec.DOUBLE.fieldOf("max_z").forGetter(m -> m.maxZ),
        Codec.DOUBLE.optionalFieldOf("coast_width", 192.0).forGetter(m -> m.coastWidth),
        Codec.DOUBLE.optionalFieldOf("ocean_transition_width", 400.0).forGetter(m -> m.oceanTransitionWidth),
        Codec.STRING.fieldOf("data").forGetter(m -> m.data)
    ).apply(instance, IslandMask::new));

    public final int width;
    public final int height;
    public final double minX;
    public final double maxX;
    public final double minZ;
    public final double maxZ;
    public final double coastWidth;
    public final double oceanTransitionWidth;
    public final String data;

    private final boolean[] land;
    private final double[] signedDistance;

    public IslandMask(int width, int height, double minX, double maxX, double minZ, double maxZ,
                      double coastWidth, double oceanTransitionWidth, String data) {
        if (!(maxX > minX) || !(maxZ > minZ) || !(coastWidth > 0.0) || !(oceanTransitionWidth > 0.0)) {
            throw new IllegalArgumentException("Invalid raster mask bounds, coast_width, or ocean_transition_width");
        }
        this.width = width;
        this.height = height;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.coastWidth = coastWidth;
        this.oceanTransitionWidth = oceanTransitionWidth;
        this.data = data;

        byte[] packed = Base64.getDecoder().decode(data);
        int pixelCount = Math.multiplyExact(width, height);
        if (packed.length != (pixelCount + 7) / 8) {
            throw new IllegalArgumentException("Raster mask byte count does not match width and height");
        }
        this.land = new boolean[pixelCount];
        int landCount = 0;
        for (int i = 0; i < pixelCount; i++) {
            this.land[i] = (packed[i >>> 3] & (1 << (i & 7))) != 0;
            if (this.land[i]) landCount++;
        }
        if (landCount == 0) {
            throw new IllegalArgumentException("Raster mask contains no land pixels");
        }
        this.signedDistance = buildSignedDistance();
    }

    public double sampleTerrainValue(double worldX, double worldZ) {
        double sampledWorldX = Math.max(minX, Math.min(maxX, worldX));
        double sampledWorldZ = Math.max(minZ, Math.min(maxZ, worldZ));
        double px = (sampledWorldX - minX) / (maxX - minX) * width - 0.5;
        double pz = (sampledWorldZ - minZ) / (maxZ - minZ) * height - 0.5;
        px = Math.max(0.0, Math.min(width - 1.0, px));
        pz = Math.max(0.0, Math.min(height - 1.0, pz));
        int x0 = clamp((int)Math.floor(px), 0, width - 1);
        int z0 = clamp((int)Math.floor(pz), 0, height - 1);
        int x1 = Math.min(x0 + 1, width - 1);
        int z1 = Math.min(z0 + 1, height - 1);
        double tx = px - x0;
        double tz = pz - z0;
        double a = lerp(signedDistance[z0 * width + x0], signedDistance[z0 * width + x1], tx);
        double b = lerp(signedDistance[z1 * width + x0], signedDistance[z1 * width + x1], tx);
        double pixelDistance = lerp(a, b, tz);
        double blocksPerPixel = 0.5 * ((maxX - minX) / width + (maxZ - minZ) / height);
        double worldDistance = pixelDistance * blocksPerPixel;
        double outsideX = worldX - sampledWorldX;
        double outsideZ = worldZ - sampledWorldZ;
        if (outsideX != 0.0 || outsideZ != 0.0) {
            double existingOceanDistance = Math.max(0.0, -worldDistance);
            worldDistance = -Math.sqrt(existingOceanDistance * existingOceanDistance
                + outsideX * outsideX + outsideZ * outsideZ);
        }
        double continentalness = worldDistance >= 0.0
            ? -0.08 + worldDistance * 0.70 / coastWidth
            : -0.08 + worldDistance * 0.92 / oceanTransitionWidth;
        return Math.max(-1.0, Math.min(0.70, continentalness));
    }

    private double[] buildSignedDistance() {
        int paddedWidth = width + 2;
        int paddedHeight = height + 2;
        boolean[] padded = new boolean[paddedWidth * paddedHeight];
        for (int z = 0; z < height; z++) {
            System.arraycopy(land, z * width, padded, (z + 1) * paddedWidth + 1, width);
        }
        double[] toLand = distanceTransform(padded, paddedWidth, paddedHeight, true);
        double[] toOcean = distanceTransform(padded, paddedWidth, paddedHeight, false);
        double[] result = new double[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int source = (z + 1) * paddedWidth + x + 1;
                int target = z * width + x;
                double distance = Math.max(0.0, Math.sqrt(land[target] ? toOcean[source] : toLand[source]) - 0.5);
                result[target] = land[target] ? distance : -distance;
            }
        }
        return result;
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double a, double b, double delta) {
        return a + (b - a) * delta;
    }
}
