package com.reystin.oneisle.test;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.continent.ModernTerrainV2;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.7 coast fidelity: generated c = -0.08 shoreline vs the painted mask boundary.
 * Displacement is evaluated with the mask's own signed distance at every generated
 * contour point, in editor pixels (blocksPerPixel of the draft).
 */
public class Phase9CoastFidelityTest {
    private static final boolean WRITE = Boolean.getBoolean("oneisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/coast");
    private static final IslandDraft.Preset[] PRESETS = {
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
    };
    private static final long[] SEEDS = {101L, 202L, 303L};
    private static final int[] SIZES = {4000, 2500};
    private static final double GRID_STEP = 8.0;

    record Displacement(int points, double mean, double max, double p95, double maxX, double maxZ) {}

    @Test
    void generatedCoastFollowsPaintedMask() throws IOException {
        StringBuilder csv = new StringBuilder(
            "preset,size,seed,points,blocks_per_px,mean_px,max_px,p95_px,over_2px,threshold_applies,status\n");
        double worstGroupMean = 0.0;
        double worstGroupMax = 0.0;
        int failureCount = 0;
        double blocksPerPx4000 = 0.0;

        for (int size : SIZES) {
            for (IslandDraft.Preset preset : PRESETS) {
                for (long seed : SEEDS) {
                    IslandDraft draft = baseDraft(preset, size, seed);
                    IslandShapeConfig cfg = config(draft);
                    ModernTerrainV2 terrain = new ModernTerrainV2(cfg);
                    double blocksPerPx = blocksPerPx(draft);
                    if (size == 4000) blocksPerPx4000 = blocksPerPx;
                    Displacement d = measure(terrain, cfg.mask, draft, blocksPerPx);
                    boolean pass = d.mean() <= 0.6 && d.max() <= 2.0;
                    if (WRITE && d.max() > 1.5) {
                        Files.createDirectories(EVIDENCE);
                        Files.writeString(EVIDENCE.resolve("coast_max_" + preset.name() + "_" + size + "_" + seed + ".txt"),
                            "max_x," + fmt(d.maxX()) + "\nmax_z," + fmt(d.maxZ()) + "\nmax_px,"
                                + fmt(d.max()) + "\nblocks_per_px," + fmt(blocksPerPx) + "\n",
                            StandardCharsets.UTF_8);
                    }
                    worstGroupMean = Math.max(worstGroupMean, d.mean());
                    worstGroupMax = Math.max(worstGroupMax, d.max());
                    if (!pass) failureCount++;
                    csv.append(preset.name()).append(',').append(size).append(',').append(seed).append(',')
                        .append(d.points()).append(',').append(fmt(blocksPerPx)).append(',')
                        .append(fmt(d.mean())).append(',').append(fmt(d.max())).append(',')
                        .append(fmt(d.p95())).append(',').append(d.max() > 2.0 ? 1 : 0).append(',')
                        .append(true).append(',').append(pass ? "PASS" : "FAIL").append('\n');
                    if (WRITE && size == 4000) {
                        writeOverlay(draft, terrain,
                            EVIDENCE.resolve("coast_overlay_" + preset.name() + "_" + size + "_" + seed + ".png"));
                    }
                }
            }
        }

        String summary = "thresholds\nmean_px_max,0.600\nmax_px_max,2.000\n"
            + "criterion_applies_to_sizes,2500;4000\n"
            + "worst_mean_px," + fmt(worstGroupMean) + "\n"
            + "worst_max_px," + fmt(worstGroupMax) + "\n"
            + "failures," + failureCount + "\n"
            + "blocks_per_px_4000," + fmt(blocksPerPx4000) + "\n"
            + "method,generated c=-0.08 contour sampled on an 8-block grid; displacement = |mask.sampleSignedDistance| / blocksPerPixel\n";
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("coast_displacement.csv"), csv.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("coast_displacement_summary.txt"), summary, StandardCharsets.UTF_8);
        }
        assertEquals(0, failureCount,
            "coastline displacement failed on " + failureCount + " configs (worst mean "
                + worstGroupMean + " px, worst max " + worstGroupMax + " px)");
    }

    private static Displacement measure(ModernTerrainV2 terrain, IslandMask mask, IslandDraft draft, double blocksPerPx) {
        double halfSpan = draft.worldSpan() * 0.5;
        int n = (int) Math.ceil(2 * halfSpan / GRID_STEP);
        double[] grid = new double[(n + 1) * (n + 1)];
        for (int j = 0; j <= n; j++) {
            double z = -halfSpan + j * GRID_STEP;
            for (int i = 0; i <= n; i++) {
                double x = -halfSpan + i * GRID_STEP;
                grid[j * (n + 1) + i] = terrain.continentalness(x, z) + 0.08;
            }
        }
        List<Double> values = new ArrayList<>();
        double maxValue = -1.0;
        double maxX = 0.0;
        double maxZ = 0.0;
        for (int j = 0; j <= n; j++) {
            double z = -halfSpan + j * GRID_STEP;
            for (int i = 0; i < n; i++) {
                double f0 = grid[j * (n + 1) + i];
                double f1 = grid[j * (n + 1) + i + 1];
                if ((f0 >= 0.0) == (f1 >= 0.0)) continue;
                double t = f0 / (f0 - f1);
                double x = -halfSpan + (i + t) * GRID_STEP;
                double value = addDisplacement(values, mask, x, z, blocksPerPx);
                if (value > maxValue) {
                    maxValue = value;
                    maxX = x;
                    maxZ = z;
                }
            }
        }
        for (int i = 0; i <= n; i++) {
            double x = -halfSpan + i * GRID_STEP;
            for (int j = 0; j < n; j++) {
                double f0 = grid[j * (n + 1) + i];
                double f1 = grid[(j + 1) * (n + 1) + i];
                if ((f0 >= 0.0) == (f1 >= 0.0)) continue;
                double t = f0 / (f0 - f1);
                double z = -halfSpan + (j + t) * GRID_STEP;
                double value = addDisplacement(values, mask, x, z, blocksPerPx);
                if (value > maxValue) {
                    maxValue = value;
                    maxX = x;
                    maxZ = z;
                }
            }
        }
        if (values.isEmpty()) return new Displacement(0, Double.NaN, Double.NaN, Double.NaN, 0, 0);
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        double sum = 0.0;
        for (double v : sorted) sum += v;
        return new Displacement(sorted.length, sum / sorted.length,
            sorted[sorted.length - 1], sorted[(int) Math.floor(0.95 * (sorted.length - 1))], maxX, maxZ);
    }

    private static double addDisplacement(List<Double> values, IslandMask mask, double x, double z, double blocksPerPx) {
        double pixels = Math.abs(mask.sampleSignedDistance(x, z)) / blocksPerPx;
        values.add(pixels);
        return pixels;
    }

    private static double blocksPerPx(IslandDraft draft) {
        return draft.worldSpan() / draft.resolution();
    }

    private static IslandDraft baseDraft(IslandDraft.Preset preset, int size, long seed) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(0.0);
        draft.setRiverDensity(0.0);
        draft.setClimateScale(720);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    private static IslandShapeConfig config(IslandDraft draft) {
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(draft.resolution(), draft.resolution(),
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            TestWorldgen.OCEAN_TRANSITION, draft.packedMask());
        return new IslandShapeConfig(3, draft.isletDensity(), draft.riverDensity(),
            false, 1000, Optional.empty(), Optional.empty(), List.of(), Optional.of(mask),
            draft.biomeSeed(), Optional.of(draft.shoreSettings()));
    }

    private static IslandTerrain terrain(IslandDraft draft) {
        return IslandTerrain.create(config(draft));
    }

    private static void writeOverlay(IslandDraft draft, ModernTerrainV2 terrain, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        int resolution = 512;
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(draft.resolution(), draft.resolution(),
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            TestWorldgen.OCEAN_TRANSITION, draft.packedMask());
        BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
        double cell = 2 * halfSpan / resolution;
        for (int pz = 0; pz < resolution; pz++) {
            double z = -halfSpan + (pz + 0.5) * cell;
            for (int px = 0; px < resolution; px++) {
                double x = -halfSpan + (px + 0.5) * cell;
                double c = terrain.continentalness(x, z);
                int color = c < -0.08 ? 0x1B3A5C : 0x3F6B3A;
                double painted = Math.abs(mask.sampleSignedDistance(x, z));
                if (painted < cell * 0.6) color = 0x2D7FF9;
                if (Math.abs(c + 0.08) < 0.0015) color = 0xFF9F1C;
                image.setRGB(px, pz, color);
            }
        }
        // Emphasise generated contour with sub-pixel rasterisation of grid crossings.
        int n = (int) Math.ceil(2 * halfSpan / 4.0);
        double step = 2 * halfSpan / n;
        double[] grid = new double[(n + 1) * (n + 1)];
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                grid[j * (n + 1) + i] = terrain.continentalness(-halfSpan + i * step, -halfSpan + j * step) + 0.08;
            }
        }
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i < n; i++) {
                double f0 = grid[j * (n + 1) + i];
                double f1 = grid[j * (n + 1) + i + 1];
                if ((f0 >= 0.0) != (f1 >= 0.0)) {
                    double x = -halfSpan + (i + f0 / (f0 - f1)) * step;
                    double z = -halfSpan + j * step;
                    paint(image, x, z, halfSpan, resolution, 0xFFFFFF);
                }
            }
        }
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j < n; j++) {
                double f0 = grid[j * (n + 1) + i];
                double f1 = grid[(j + 1) * (n + 1) + i];
                if ((f0 >= 0.0) != (f1 >= 0.0)) {
                    double x = -halfSpan + i * step;
                    double z = -halfSpan + (j + f0 / (f0 - f1)) * step;
                    paint(image, x, z, halfSpan, resolution, 0xFFFFFF);
                }
            }
        }
        ImageIO.write(image, "PNG", path.toFile());
    }

    private static void paint(BufferedImage image, double x, double z, double halfSpan, int resolution, int color) {
        int px = (int) Math.round((x / halfSpan * 0.5 + 0.5) * (resolution - 1));
        int pz = (int) Math.round((z / halfSpan * 0.5 + 0.5) * (resolution - 1));
        if (px < 0 || px >= resolution || pz < 0 || pz >= resolution) return;
        image.setRGB(px, pz, color);
        if (px + 1 < resolution) image.setRGB(px + 1, pz, color);
        if (pz + 1 < resolution) image.setRGB(px, pz + 1, color);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
