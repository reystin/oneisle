package com.reystin.oneisle.test;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.continent.ShoreField;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.8.4 shore coherence: rasterises the v3 sand band at 2-block resolution and
 * measures 8-connected sand components, so evidence reports actual arc lengths
 * instead of the old 1-3 block specks.
 */
public class Phase11ShoreTest {
    private static final boolean WRITE = Boolean.getBoolean("oneisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/phase11/after");
    private static final int STEP = 2;
    private static final double BAND_MIN = -20.0;
    private static final double BAND_MAX = 36.0;
    private static final List<IslandDraft.Preset> PRESETS = List.of(
        IslandDraft.Preset.MINESHIELD_4_MAP, IslandDraft.Preset.NATURAL,
        IslandDraft.Preset.ROUND, IslandDraft.Preset.ARCHIPELAGO,
        IslandDraft.Preset.CRESCENT);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("11R.1 sand bands form 25-90 block arcs; short specks are recorded")
    void sandRunsAreCoherent() throws IOException {
        StringBuilder csv = new StringBuilder("preset,components,cells,tiny_le3,under16,under25,"
            + "in_25_90,share_25_90,median_len,p25_len,p75_len,max_len,median_area,median_width\n");
        StringBuilder summary = new StringBuilder("target_tiny_components,0\n");
        int totalTiny = 0;
        for (IslandDraft.Preset preset : PRESETS) {
            IslandDraft draft = Phase11ClimateLayoutTest.draft(preset);
            IslandTerrain terrain = IslandTerrain.create(Phase11ClimateLayoutTest.config(draft));
            double halfSpan = draft.worldSpan() * 0.5;
            int half = (int) Math.ceil(halfSpan / STEP);
            int size = half * 2 + 1;
            byte[] sand = new byte[size * size];
            int cells = 0;
            for (int gz = 0; gz < size; gz++) {
                double wz = (gz - half) * (double) STEP;
                for (int gx = 0; gx < size; gx++) {
                    double wx = (gx - half) * (double) STEP;
                    double d = terrain.signedDistance(wx, wz);
                    if (d < BAND_MIN || d > BAND_MAX) continue;
                    double slope = slopeAt(terrain, wx, wz);
                    ShoreField.Ring ring = ShoreField.ring(terrain, wx, wz, d);
                    if (ShoreField.outcome(d, wx, wz, slope, terrain.heightTierAt(wx, wz),
                        terrain.continentalness(wx, wz), ring,
                        draft.shoreSettings(), draft.biomeSeed()) == ShoreField.SAND) {
                        sand[gz * size + gx] = 1;
                        cells++;
                    }
                }
            }
            List<Component> components = components(sand, size);
            double[] lengths = components.stream().mapToDouble(Component::length).sorted().toArray();
            double[] areas = components.stream().mapToDouble(Component::area).sorted().toArray();
            double[] widths = components.stream().mapToDouble(Component::width).sorted().toArray();
            int tiny = (int) Arrays.stream(lengths).filter(v -> v <= 3.0).count();
            int under16 = (int) Arrays.stream(lengths).filter(v -> v < 16.0).count();
            int under25 = (int) Arrays.stream(lengths).filter(v -> v < 25.0).count();
            int inRange = (int) Arrays.stream(lengths).filter(v -> v >= 25.0 && v <= 90.0).count();
            double share = components.isEmpty() ? 0.0 : inRange / (double) components.size();
            double medianLen = percentile(lengths, 0.50);
            double p25 = percentile(lengths, 0.25);
            double p75 = percentile(lengths, 0.75);
            double maxLen = lengths.length == 0 ? 0.0 : lengths[lengths.length - 1];
            double medianArea = percentile(areas, 0.50);
            double medianWidth = percentile(widths, 0.50);
            String line = preset.name() + "," + components.size() + "," + cells + "," + tiny + ","
                + under16 + "," + under25 + "," + inRange + "," + fmt(share) + ","
                + fmt(medianLen) + "," + fmt(p25) + "," + fmt(p75) + "," + fmt(maxLen) + ","
                + fmt(medianArea) + "," + fmt(medianWidth);
            System.out.println("11R.1 " + line);
            csv.append(line).append('\n');
            totalTiny += tiny;
            assertTrue(components.size() >= 8,
                preset + " produced only " + components.size() + " sand components");
            assertTrue(medianLen >= 25.0 && medianLen <= 90.0,
                preset + " median sand arc was " + fmt(medianLen) + " blocks");
            assertTrue(p75 <= 140.0,
                preset + " p75 sand arc was " + fmt(p75) + " blocks");
            assertTrue(share >= 0.35,
                preset + " only " + fmt(share * 100.0) + "% of arcs were 25-90 blocks");
            assertTrue(under25 <= components.size() * 0.40,
                preset + " had " + under25 + "/" + components.size() + " arcs under 25 blocks");
        }
        summary.append("achieved_tiny_components,").append(totalTiny).append('\n')
            .append("target_median_len,25-90\n")
            .append("note,short components are recorded in shore_runs.csv instead of claiming zero\n");
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_runs.csv"), csv.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("shore_runs_summary.txt"), summary.toString(),
                StandardCharsets.UTF_8);
        }
        assertTrue(totalTiny <= 15, "short sand components total was " + totalTiny);
    }

    private record Component(double length, double area, double width) {}

    private static List<Component> components(byte[] sand, int size) {
        List<Component> out = new ArrayList<>();
        boolean[] seen = new boolean[sand.length];
        int[] stack = new int[sand.length];
        for (int start = 0; start < sand.length; start++) {
            if (sand[start] == 0 || seen[start]) continue;
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int cells = 0;
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            while (top > 0) {
                int index = stack[--top];
                int x = index % size;
                int z = index / size;
                cells++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
                        int next = nz * size + nx;
                        if (sand[next] == 0 || seen[next]) continue;
                        seen[next] = true;
                        stack[top++] = next;
                    }
                }
            }
            double length = Math.max(maxX - minX + 1, maxZ - minZ + 1) * (double) STEP;
            double area = cells * (double) STEP * STEP;
            out.add(new Component(length, area, area / Math.max(1.0, length)));
        }
        return out;
    }

    private static double percentile(double[] sorted, double q) {
        if (sorted.length == 0) return 0.0;
        int index = (int) Math.round(q * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double slopeAt(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 4.0, z);
        double cW = terrain.continentalness(x - 4.0, z);
        double cS = terrain.continentalness(x, z + 4.0);
        double cN = terrain.continentalness(x, z - 4.0);
        return Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
