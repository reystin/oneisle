package com.hack337.omniisle.client;

import com.hack337.omniisle.continent.IslandTerrain;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Handles importing and exporting OmniIsle masks, height layers, biome maps,
 * and hillshade relief images as PNG files.
 */
public final class ImageImportExport {
    public record ImportedDraftData(boolean[] land, byte[] height) {}

    private ImageImportExport() {}

    /**
     * Imports a mask image into a boolean array scaled to targetRes x targetRes.
     * Preserves aspect ratio with letterboxing.
     */
    public static boolean[] importMask(BufferedImage src, int targetRes, int threshold, boolean invert) {
        BufferedImage scaled = scaleLetterbox(src, targetRes);
        boolean[] land = new boolean[targetRes * targetRes];

        for (int z = 0; z < targetRes; z++) {
            for (int x = 0; x < targetRes; x++) {
                int rgb = scaled.getRGB(x, z);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int lum = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);

                boolean isLand = invert ? (lum <= threshold) : (lum >= threshold);
                land[z * targetRes + x] = isLand;
            }
        }
        return land;
    }

    /**
     * Imports a heightmap image into a land mask (threshold) and four height
     * tiers split by the 25/50/75 percentiles of the land luminosity.
     */
    public static ImportedDraftData importHeightmap(BufferedImage src, int targetRes, int threshold, boolean invert) {
        BufferedImage scaled = scaleLetterbox(src, targetRes);
        int total = targetRes * targetRes;
        boolean[] land = new boolean[total];
        int[] values = new int[total];

        for (int z = 0; z < targetRes; z++) {
            for (int x = 0; x < targetRes; x++) {
                int rgb = scaled.getRGB(x, z);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int lum = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                int index = z * targetRes + x;
                land[index] = invert ? (lum <= threshold) : (lum >= threshold);
                values[index] = invert ? 255 - lum : lum;
            }
        }

        int[] landValues = new int[total];
        int count = 0;
        int min = 255;
        int max = 0;
        for (int i = 0; i < total; i++) {
            if (!land[i]) continue;
            int value = values[i];
            landValues[count++] = value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        byte[] height = new byte[total];
        if (count == 0 || min == max) {
            java.util.Arrays.fill(height, (byte) 1);
            return new ImportedDraftData(land, height);
        }

        int[] sorted = java.util.Arrays.copyOf(landValues, count);
        java.util.Arrays.sort(sorted);
        int q25 = sorted[Math.max(0, count / 4 - 1)];
        int q50 = sorted[Math.max(0, count / 2 - 1)];
        int q75 = sorted[Math.max(0, count * 3 / 4 - 1)];

        for (int i = 0; i < total; i++) {
            if (!land[i]) {
                height[i] = (byte) 1;
                continue;
            }
            int value = values[i];
            int tier = value <= q25 ? 0 : value <= q50 ? 1 : value <= q75 ? 2 : 3;
            height[i] = (byte) tier;
        }
        return new ImportedDraftData(land, height);
    }

    /** Letterboxes and scales an image to fit inside targetRes x targetRes, keeping black margins. */
    public static BufferedImage scaleLetterbox(BufferedImage src, int targetRes) {
        BufferedImage dest = new BufferedImage(targetRes, targetRes, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, targetRes, targetRes);

        int sw = src.getWidth();
        int sh = src.getHeight();
        double scale = Math.min((double) targetRes / sw, (double) targetRes / sh);
        int dw = (int) Math.round(sw * scale);
        int dh = (int) Math.round(sh * scale);
        int dx = (targetRes - dw) / 2;
        int dy = (targetRes - dh) / 2;

        g.drawImage(src, dx, dy, dw, dh, null);
        g.dispose();
        return dest;
    }

    /** Exports the land mask as a black and white PNG. */
    public static void exportMask(IslandDraft draft, Path path) throws IOException {
        int res = draft.resolution();
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                int color = draft.isLand(x, z) ? 0xFFFFFF : 0x000000;
                img.setRGB(x, z, color);
            }
        }
        ImageIO.write(img, "PNG", path.toFile());
    }

    /** Exports the height layer as a 4-shade grayscale PNG. */
    public static void exportHeight(IslandDraft draft, Path path) throws IOException {
        int res = draft.resolution();
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                int tier = draft.tier(x, z) & 3;
                int gray = tier * 85;
                int color = (gray << 16) | (gray << 8) | gray;
                img.setRGB(x, z, color);
            }
        }
        ImageIO.write(img, "PNG", path.toFile());
    }

    /** Exports the surface biome map as a full color PNG. */
    public static void exportBiomes(IslandDraft draft, IslandTerrain terrain, Path path) throws IOException {
        int res = draft.resolution();
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        double halfSpan = draft.worldSpan() / 2.0;
        boolean shoreAllowed = draft.generationVersionNumber() >= 3
            && !draft.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();
        com.hack337.omniisle.continent.ShoreSettings shore = draft.shoreSettings();

        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                double wx = draft.worldX(x);
                double wz = draft.worldZ(z);
                double c = terrain != null ? terrain.continentalness(wx, wz) : (draft.isLand(x, z) ? 0.3 : -0.5);
                double slope = 0.0;
                if (terrain != null) {
                    double cE = terrain.continentalness(wx + 8, wz);
                    double cW = terrain.continentalness(wx - 8, wz);
                    double cS = terrain.continentalness(wx, wz + 8);
                    double cN = terrain.continentalness(wx, wz - 8);
                    slope = Math.hypot((cE - cW) / 16.0, (cS - cN) / 16.0) * 64.0;
                }
                BiomePreview.Type preferred = BiomePreview.surfaceV3(
                    c, wx, wz, draft.biomeSeed(),
                    draft.climateScale(), draft.gradientMode(), draft.gradientStrength(),
                    draft.theme(), slope, draft.generationVersionNumber(), halfSpan,
                    terrain != null ? terrain.rivers() : null, terrain, shore, shoreAllowed
                );
                int color = draft.selectBiome(preferred, wx, wz).color & 0x00FFFFFF;
                img.setRGB(x, z, color);
            }
        }
        ImageIO.write(img, "PNG", path.toFile());
    }

    /** Exports hillshade lighting as a grayscale PNG. */
    public static void exportHillshade(IslandDraft draft, IslandTerrain terrain, Path path) throws IOException {
        int res = draft.resolution();
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        if (terrain == null) return;

        float[] elevations = new float[res * res];
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                double wx = draft.worldX(x);
                double wz = draft.worldZ(z);
                double c = terrain.continentalness(wx, wz);
                elevations[z * res + x] = (float) IslandTerrain.continentalnessToElevation(c);
            }
        }
        double blockSize = draft.worldSpan() / (double) res;

        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                int xPrev = Math.max(0, x - 1);
                int xNext = Math.min(res - 1, x + 1);
                int zPrev = Math.max(0, z - 1);
                int zNext = Math.min(res - 1, z + 1);
                double dx = (elevations[z * res + xNext] - elevations[z * res + xPrev]) / ((xNext - xPrev) * blockSize);
                double dz = (elevations[zNext * res + x] - elevations[zPrev * res + x]) / ((zNext - zPrev) * blockSize);

                double norm = Math.sqrt(dx * dx + dz * dz + 1.0);
                double dot = (0.5 * dx + 0.70710678 + 0.5 * dz) / norm;
                double diff = (dot - 0.70710678) / 0.70710678;
                double factor = 0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, diff * 1.8));
                int gray = Math.max(0, Math.min(255, (int) Math.round(factor * 255.0)));
                int color = (gray << 16) | (gray << 8) | gray;
                img.setRGB(x, z, color);
            }
        }
        ImageIO.write(img, "PNG", path.toFile());
    }

    /** Exports shore outcomes: black none, yellow sand, grey stony, dark grey land. */
    public static void exportShore(IslandDraft draft, IslandTerrain terrain, Path path) throws IOException {
        int res = draft.resolution();
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        boolean shoreAllowed = terrain != null
            && draft.generationVersionNumber() >= 3
            && !draft.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();
        com.hack337.omniisle.continent.ShoreSettings settings = draft.shoreSettings();

        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                int color = draft.isLand(x, z) ? 0x333333 : 0x000000;
                if (shoreAllowed && settings != null && settings.enabled()) {
                    double wx = draft.worldX(x);
                    double wz = draft.worldZ(z);
                    double distance = terrain.signedDistance(wx, wz);
                    if (distance >= com.hack337.omniisle.continent.ShoreField.intertidalLimit(wx, wz, draft.biomeSeed())
                        && distance <= com.hack337.omniisle.continent.ShoreField.MAX_INLAND) {
                        double cE = terrain.continentalness(wx + 8, wz);
                        double cW = terrain.continentalness(wx - 8, wz);
                        double cS = terrain.continentalness(wx, wz + 8);
                        double cN = terrain.continentalness(wx, wz - 8);
                        double slope = Math.hypot((cE - cW) / 16.0, (cS - cN) / 16.0) * 64.0;
                        double bay = com.hack337.omniisle.continent.ShoreField.bayness(terrain, wx, wz, distance);
                        int outcome = com.hack337.omniisle.continent.ShoreField.outcome(
                            distance, wx, wz, slope, bay, settings, draft.biomeSeed());
                        if (outcome == com.hack337.omniisle.continent.ShoreField.SAND) color = 0xF0E6A0;
                        else if (outcome == com.hack337.omniisle.continent.ShoreField.STONY) color = 0x90989B;
                    }
                }
                img.setRGB(x, z, color);
            }
        }
        ImageIO.write(img, "PNG", path.toFile());
    }

    /** Exports all 5 maps into the given export folder. */
    public static Path exportAll(IslandDraft draft, IslandTerrain terrain, Path gameDir) throws IOException {
        Path exportsDir = gameDir.resolve("omniisle").resolve("exports");
        Files.createDirectories(exportsDir);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).format(LocalDateTime.now());
        String prefix = draft.preset().name().toLowerCase(Locale.ROOT) + "_" + timestamp;

        Path maskPath = exportsDir.resolve(prefix + "_mask.png");
        Path heightPath = exportsDir.resolve(prefix + "_height.png");
        Path biomesPath = exportsDir.resolve(prefix + "_biomes.png");
        Path hillshadePath = exportsDir.resolve(prefix + "_hillshade.png");
        Path shorePath = exportsDir.resolve(prefix + "_shore.png");

        exportMask(draft, maskPath);
        exportHeight(draft, heightPath);
        if (terrain != null) {
            exportBiomes(draft, terrain, biomesPath);
            exportHillshade(draft, terrain, hillshadePath);
            exportShore(draft, terrain, shorePath);
        }
        return exportsDir;
    }
}
