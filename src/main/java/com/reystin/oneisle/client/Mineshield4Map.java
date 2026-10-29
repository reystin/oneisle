package com.reystin.oneisle.client;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/** Land mask and height tiers traced from the bundled Mineshield 4 reference map. */
final class Mineshield4Map {
    private static final String RESOURCE = "/assets/oneisle/presets/mineshield_4_map.jpg";
    private static final Map<Integer, Data> CACHE = new ConcurrentHashMap<>();
    private static volatile BufferedImage source;

    record Data(boolean[] land, byte[] height) {}

    private Mineshield4Map() {}

    static Data sample(int resolution) {
        Data data = CACHE.computeIfAbsent(resolution, Mineshield4Map::build);
        return new Data(data.land().clone(), data.height().clone());
    }

    private static Data build(int resolution) {
        BufferedImage scaled = ImageImportExport.scaleLetterbox(source(), resolution);
        boolean[] land = new boolean[resolution * resolution];
        byte[] height = new byte[resolution * resolution];
        Arrays.fill(height, (byte) 1);
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                int rgb = scaled.getRGB(x, z);
                int i = z * resolution + x;
                boolean water = isWater(rgb);
                land[i] = !water
                    && x > 0 && z > 0 && x < resolution - 1 && z < resolution - 1;
                if (!water) height[i] = (byte) heightTier(rgb);
            }
        }
        cleanTiers(land, height, resolution);
        return new Data(land, height);
    }

    /**
     * Median-filters the raw tier map, then keeps a single curved central-north
     * snow range (bridging its lobes) and the western mesa/foothill band. All
     * other detached tier-2/tier-3 specks fall back to lowland.
     */
    private static void cleanTiers(boolean[] land, byte[] height, int resolution) {
        byte[] base = new byte[height.length];
        int[] window = new int[9];
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                int i = z * resolution + x;
                if (!land[i]) {
                    base[i] = 1;
                    continue;
                }
                int n = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    int nz = Math.max(0, Math.min(resolution - 1, z + dz));
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = Math.max(0, Math.min(resolution - 1, x + dx));
                        int ni = nz * resolution + nx;
                        window[n++] = land[ni] ? height[ni] : 1;
                    }
                }
                Arrays.sort(window);
                base[i] = (byte) window[4];
            }
        }

        boolean[] snow = new boolean[base.length];
        List<Component> snowComponents = components(base, land, resolution, 3);
        Component mainSnow = null;
        for (Component c : snowComponents) {
            if (mainSnow == null || c.area > mainSnow.area) mainSnow = c;
        }
        if (mainSnow != null) {
            for (int[] cell : mainSnow.cells) snow[cell[1] * resolution + cell[0]] = true;
            double minArea = 0.0009 * resolution * resolution;
            for (Component c : snowComponents) {
                if (c == mainSnow) continue;
                if (c.area < minArea) continue;
                double cx = c.centroidX / resolution;
                double cz = c.centroidZ / resolution;
                if (cx < 0.33 || cx > 0.75 || cz < 0.15 || cz > 0.65) continue;
                bridge(snow, mainSnow, c, land, resolution);
            }
        }

        boolean[] highland = new boolean[base.length];
        double minHighland = 0.00065 * resolution * resolution;
        for (Component c : components(base, land, resolution, 2)) {
            if (c.area < minHighland) continue;
            if (c.centroidX >= 0.45 * resolution) continue;
            for (int[] cell : c.cells) {
                int i = cell[1] * resolution + cell[0];
                if (!snow[i]) highland[i] = true;
            }
        }

        for (int i = 0; i < height.length; i++) {
            if (!land[i]) height[i] = 1;
            else height[i] = (byte) (snow[i] ? 3 : highland[i] ? 2 : 1);
        }
    }

    private static void bridge(boolean[] snow, Component main, Component other,
                               boolean[] land, int resolution) {
        int[] bestA = null;
        int[] bestB = null;
        long best = Long.MAX_VALUE;
        for (int[] a : main.cells) {
            for (int[] b : other.cells) {
                long dx = a[0] - b[0];
                long dz = a[1] - b[1];
                long distance = dx * dx + dz * dz;
                if (distance < best) {
                    best = distance;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        if (bestA == null) return;
        int x0 = bestA[0];
        int z0 = bestA[1];
        int x1 = bestB[0];
        int z1 = bestB[1];
        for (int[] cell : other.cells) snow[cell[1] * resolution + cell[0]] = true;
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int x = x0;
        int z = z0;
        while (true) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int ox = -1; ox <= 1; ox++) {
                    int nx = x + ox;
                    int nz = z + oz;
                    if (nx < 0 || nz < 0 || nx >= resolution || nz >= resolution) continue;
                    int i = nz * resolution + nx;
                    if (land[i]) snow[i] = true;
                }
            }
            if (x == x1 && z == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    private record Component(int area, int centroidX, int centroidZ, List<int[]> cells) {}

    private static List<Component> components(byte[] tiers, boolean[] land, int resolution, int tier) {
        boolean[] seen = new boolean[tiers.length];
        List<Component> result = new ArrayList<>();
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                int idx = z * resolution + x;
                if (seen[idx] || !land[idx] || tiers[idx] != tier) continue;
                List<int[]> cells = new ArrayList<>();
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, z});
                seen[idx] = true;
                long sumX = 0;
                long sumZ = 0;
                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    cells.add(p);
                    sumX += p[0];
                    sumZ += p[1];
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) continue;
                            int nx = p[0] + dx;
                            int nz = p[1] + dz;
                            if (nx < 0 || nz < 0 || nx >= resolution || nz >= resolution) continue;
                            int ni = nz * resolution + nx;
                            if (seen[ni] || !land[ni] || tiers[ni] != tier) continue;
                            seen[ni] = true;
                            queue.add(new int[]{nx, nz});
                        }
                    }
                }
                int area = cells.size();
                result.add(new Component(area, (int) (sumX / area), (int) (sumZ / area), cells));
            }
        }
        return result;
    }

    static boolean isWater(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (r + g + b <= 24) return true;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0] * 360.0f;
        return hue >= 150.0f && hue <= 265.0f && hsb[1] > 0.28f && hsb[2] > 0.08f
            && b > r + 8 && b >= g * 0.92f;
    }

    static int heightTier(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0] * 360.0f;
        if (hsb[2] > 0.85f && hsb[1] < 0.12f && hue >= 160.0f && hue <= 260.0f) return 3;
        if (hue <= 60.0f && hsb[1] >= 0.30f) return 2;
        return 1;
    }

    private static BufferedImage source() {
        BufferedImage result = source;
        if (result == null) {
            synchronized (Mineshield4Map.class) {
                result = source;
                if (result == null) {
                    try (InputStream in = Mineshield4Map.class.getResourceAsStream(RESOURCE)) {
                        if (in == null) throw new IllegalStateException("Missing preset resource " + RESOURCE);
                        result = ImageIO.read(in);
                        if (result == null) throw new IllegalStateException("Unreadable preset resource " + RESOURCE);
                        source = result;
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to load " + RESOURCE, e);
                    }
                }
            }
        }
        return result;
    }
}
