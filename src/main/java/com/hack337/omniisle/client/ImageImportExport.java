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
     * Imports a heightmap image into both a land mask (by threshold)
     * and a 4-tier height layer (0..3) scaled to targetRes x targetRes.
     */
    public static ImportedDraftData importHeightmap(BufferedImage src, int targetRes, int threshold, boolean invert) {
        BufferedImage scaled = scaleLetterbox(src, targetRes);
        boolean[] land = new boolean[targetRes * targetRes];
        byte[] height = new byte[targetRes * targetRes];

        for (int z = 0; z < targetRes; z++) {
            for (int x = 0; x < targetRes; x++) {
                int rgb = scaled.getRGB(x, z);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int lum = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);

                boolean isLand = invert ? (lum <= threshold) : (lum >= threshold);
                land[z * targetRes + x] = isLand;

                if (isLand) {
                    double norm;
                    if (!invert) {
                        norm = threshold < 255 ? (lum - threshold) / (255.0 - threshold) : 1.0;
                    } else {
                        norm = threshold > 0 ? (threshold - lum) / (double) threshold : 1.0;
                    }
                    int tier = Math.max(0, Math.min(3, (int) Math.round(norm * 3.0)));
                    height[z * targetRes + x] = (byte) tier;
                } else {
                    height[z * targetRes + x] = (byte) 1;
                }
            }
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
                BiomePreview.Type preferred = BiomePreview.surface(
                    c, wx, wz, draft.biomeSeed(),
                    draft.climateScale(), draft.gradientMode(), draft.gradientStrength(),
                    draft.theme(), slope, draft.generationVersionNumber(), halfSpan
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

    /** Exports all 4 maps into the given export folder. */
    public static Path exportAll(IslandDraft draft, IslandTerrain terrain, Path gameDir) throws IOException {
        Path exportsDir = gameDir.resolve("omniisle").resolve("exports");
        Files.createDirectories(exportsDir);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).format(LocalDateTime.now());
        String prefix = draft.preset().name().toLowerCase(Locale.ROOT) + "_" + timestamp;

        Path maskPath = exportsDir.resolve(prefix + "_mask.png");
        Path heightPath = exportsDir.resolve(prefix + "_height.png");
        Path biomesPath = exportsDir.resolve(prefix + "_biomes.png");
        Path hillshadePath = exportsDir.resolve(prefix + "_hillshade.png");

        exportMask(draft, maskPath);
        exportHeight(draft, heightPath);
        if (terrain != null) {
            exportBiomes(draft, terrain, biomesPath);
            exportHillshade(draft, terrain, hillshadePath);
        }
        return exportsDir;
    }
}
