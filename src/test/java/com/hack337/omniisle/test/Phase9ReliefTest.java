package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ModernTerrainV2;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.7 relief acceptance: real surface-height relief on round islands, flat-plateau
 * limits and painted tier-3 slope/boost budgets. Replaces the old variance-only check.
 */
public class Phase9ReliefTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/relief");
    private static final double VANILLA_MAX_SLOPE = 1.8;
    private static final double SLOPE_BUDGET = VANILLA_MAX_SLOPE * 1.5;

    @Test
    void roundIslandsHaveDistinctPeaksAndNoWidePlateaus() throws IOException {
        StringBuilder csv = new StringBuilder(
            "preset,size,seed,samples,distinct_peaks,max_elevation,max_plateau_span_blocks,plateau_area_diameter_blocks,plateau_component_span_blocks\n");
        int seedsWithPeaks = 0;
        double worstPlateau = 0.0;
        double maxSlopeAll = 0.0;

        for (long seed = 1; seed <= 5; seed++) {
            IslandDraft draft = baseDraft(IslandDraft.Preset.ROUND, 4000, seed, IslandDraft.Relief.BALANCED);
            ModernTerrainV2 terrain = (ModernTerrainV2) terrain(draft);
            int step = 24;
            int bound = 1200;
            int count = (2 * bound) / step + 1;
            double[][] elevation = new double[count][count];
            double[][] continental = new double[count][count];
            double maxElevation = -1.0e9;
            for (int zi = 0; zi < count; zi++) {
                double z = -bound + zi * step;
                for (int xi = 0; xi < count; xi++) {
                    double x = -bound + xi * step;
                    double c = terrain.continentalness(x, z);
                    continental[zi][xi] = c;
                    elevation[zi][xi] = IslandTerrain.continentalnessToElevation(c);
                    maxElevation = Math.max(maxElevation, elevation[zi][xi]);
                }
            }

            List<int[]> peaks = new ArrayList<>();
            for (int zi = 2; zi < count - 2; zi++) {
                for (int xi = 2; xi < count - 2; xi++) {
                    if (continental[zi][xi] < 0.40) continue;
                    boolean localMax = true;
                    for (int dz = -2; dz <= 2 && localMax; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (dx == 0 && dz == 0) continue;
                            if (continental[zi + dz][xi + dx] >= continental[zi][xi]) {
                                localMax = false;
                                break;
                            }
                        }
                    }
                    if (localMax) peaks.add(new int[]{xi, zi});
                }
            }
            List<int[]> distinct = new ArrayList<>();
            for (int[] p : peaks) {
                boolean separate = true;
                for (int[] q : distinct) {
                    double d = Math.hypot((p[0] - q[0]) * step, (p[1] - q[1]) * step);
                    if (d < 150.0) {
                        separate = false;
                        break;
                    }
                }
                if (separate) distinct.add(p);
            }
            if (distinct.size() >= 2) seedsWithPeaks++;

            int maxRowSpan = 0;
            for (int zi = 0; zi < count; zi++) {
                int rowSpan = 0;
                for (int xi = 0; xi < count; xi++) {
                    if (Math.abs(elevation[zi][xi] - maxElevation) <= 1.0) {
                        rowSpan += step;
                        maxRowSpan = Math.max(maxRowSpan, rowSpan);
                    } else {
                        rowSpan = 0;
                    }
                }
            }
            // Only cells connected to each other form a plateau; scattered summits at the
            // same altitude are not a flat plateau.
            boolean[] near = new boolean[count * count];
            for (int i = 0; i < near.length; i++) {
                near[i] = Math.abs(elevation[i / count][i % count] - maxElevation) <= 1.0;
            }
            boolean[] visited = new boolean[near.length];
            int largestCells = 0;
            int plateauSpan = 0;
            int[] stack = new int[near.length];
            for (int start = 0; start < near.length; start++) {
                if (!near[start] || visited[start]) continue;
                int head = 0;
                stack[head++] = start;
                visited[start] = true;
                int cells = 0;
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxZ = Integer.MIN_VALUE;
                while (head > 0) {
                    int cell = stack[--head];
                    int cx = cell % count;
                    int cz = cell / count;
                    cells++;
                    minX = Math.min(minX, cx);
                    maxX = Math.max(maxX, cx);
                    minZ = Math.min(minZ, cz);
                    maxZ = Math.max(maxZ, cz);
                    int[][] neighbours = {{cx - 1, cz}, {cx + 1, cz}, {cx, cz - 1}, {cx, cz + 1}};
                    for (int[] nb : neighbours) {
                        if (nb[0] < 0 || nb[0] >= count || nb[1] < 0 || nb[1] >= count) continue;
                        int idx = nb[1] * count + nb[0];
                        if (near[idx] && !visited[idx]) {
                            visited[idx] = true;
                            stack[head++] = idx;
                        }
                    }
                }
                largestCells = Math.max(largestCells, cells);
                plateauSpan = Math.max(plateauSpan, Math.max(maxX - minX, maxZ - minZ) * step);
            }
            double areaDiameter = 2.0 * Math.sqrt(largestCells * (double) step * step / Math.PI);
            worstPlateau = Math.max(worstPlateau, maxRowSpan);
            maxSlopeAll = Math.max(maxSlopeAll, maxSurfaceSlope(terrain, 4, 400));

            csv.append("ROUND,4000,").append(seed).append(',')
                .append(count * count).append(',')
                .append(distinct.size()).append(',')
                .append(fmt(maxElevation)).append(',')
                .append(maxRowSpan).append(',')
                .append(fmt(areaDiameter)).append(',')
                .append(plateauSpan).append('\n');

            assertTrue(maxRowSpan < 150,
                "flat plateau at max height must stay below 150 blocks; seed " + seed + " had " + maxRowSpan);
            assertTrue(plateauSpan < 150,
                "connected plateau span must stay below 150 blocks; seed " + seed + " had " + plateauSpan);
            assertTrue(areaDiameter < 150.0,
                "plateau area diameter must stay below 150 blocks; seed " + seed + " had " + areaDiameter);
        }

        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("relief_round_islands.csv"), csv.toString(), StandardCharsets.UTF_8);
            writeHeightMap(baseDraft(IslandDraft.Preset.ROUND, 4000, 1L, IslandDraft.Relief.BALANCED),
                EVIDENCE.resolve("relief_round_4000_seed1.png"));
        }

        assertTrue(seedsWithPeaks >= 3,
            "at least 3 of 5 round-island seeds need 2+ distinct maxima; got " + seedsWithPeaks);
        assertTrue(maxSlopeAll <= SLOPE_BUDGET,
            "natural round-island slope exceeded vanilla*1.5: " + maxSlopeAll);
    }

    @Test
    void tier3PaintingRaisesPeaksAndKeepsSlopesBounded() throws IOException {
        StringBuilder csv = new StringBuilder("seed,base_peak,tier3_peak,boost_blocks,max_slope_4block\n");
        double minBoost = Double.MAX_VALUE;
        double maxSlope = 0.0;
        for (long seed = 1; seed <= 5; seed++) {
            IslandDraft plain = baseDraft(IslandDraft.Preset.ROUND, 4000, seed, IslandDraft.Relief.FLAT);
            IslandDraft painted = baseDraft(IslandDraft.Preset.ROUND, 4000, seed, IslandDraft.Relief.FLAT);
            int cx = painted.resolution() / 2;
            painted.paintHeight(cx, cx, 14, 3);
            ModernTerrainV2 baseTerrain = (ModernTerrainV2) terrain(plain);
            ModernTerrainV2 paintedTerrain = (ModernTerrainV2) terrain(painted);
            double basePeak = maxElevation(baseTerrain, 8, 900);
            double paintedPeak = maxElevation(paintedTerrain, 8, 900);
            double boost = paintedPeak - basePeak;
            double slope = maxSurfaceSlope(paintedTerrain, 4, 400);
            minBoost = Math.min(minBoost, boost);
            maxSlope = Math.max(maxSlope, slope);
            csv.append(seed).append(',').append(fmt(basePeak)).append(',')
                .append(fmt(paintedPeak)).append(',').append(fmt(boost)).append(',')
                .append(fmt(slope)).append('\n');
            assertTrue(boost >= 40.0, "tier-3 peak boost was only " + boost + " blocks on seed " + seed);
            assertTrue(slope <= SLOPE_BUDGET,
                "tier-3 painted slope " + slope + " exceeded vanilla*1.5 on seed " + seed);
        }
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("relief_tier3_boost.csv"), csv.toString(), StandardCharsets.UTF_8);
            IslandDraft painted = baseDraft(IslandDraft.Preset.ROUND, 4000, 1L, IslandDraft.Relief.FLAT);
            int cx = painted.resolution() / 2;
            painted.paintHeight(cx, cx, 14, 3);
            writeHeightMap(painted, EVIDENCE.resolve("relief_tier3_4000_seed1.png"));
        }
        assertTrue(minBoost >= 40.0);
        assertTrue(maxSlope <= SLOPE_BUDGET);
    }

    private static IslandDraft baseDraft(IslandDraft.Preset preset, int size, long seed, IslandDraft.Relief relief) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setRelief(relief);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(0.0);
        draft.setRiverDensity(0.0);
        draft.setClimateScale(720);
        draft.rebuildShape();
        return draft;
    }

    private static IslandShapeConfig config(IslandDraft draft) {
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(draft.resolution(), draft.resolution(),
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            TestWorldgen.OCEAN_TRANSITION, draft.packedMask());
        Optional<IslandShapeConfig.HeightLayer> height = draft.hasCustomHeight()
            ? Optional.of(new IslandShapeConfig.HeightLayer(2, draft.packedHeight()))
            : Optional.empty();
        return new IslandShapeConfig(3, draft.isletDensity(), draft.riverDensity(),
            false, 1000, height, Optional.empty(), List.of(), Optional.of(mask),
            draft.biomeSeed(), Optional.of(draft.shoreSettings()));
    }

    private static IslandTerrain terrain(IslandDraft draft) {
        return IslandTerrain.create(config(draft));
    }

    private static double maxElevation(ModernTerrainV2 terrain, int step, int bound) {
        double max = -1.0e9;
        for (int z = -bound; z <= bound; z += step) {
            for (int x = -bound; x <= bound; x += step) {
                max = Math.max(max, IslandTerrain.continentalnessToElevation(terrain.continentalness(x, z)));
            }
        }
        return max;
    }

    private static double maxSurfaceSlope(ModernTerrainV2 terrain, int step, int bound) {
        double max = 0.0;
        for (int z = -bound; z <= bound; z += step) {
            for (int x = -bound; x <= bound; x += step) {
                double e = IslandTerrain.continentalnessToElevation(terrain.continentalness(x, z));
                double ex = IslandTerrain.continentalnessToElevation(terrain.continentalness(x + step, z));
                double ez = IslandTerrain.continentalnessToElevation(terrain.continentalness(x, z + step));
                max = Math.max(max, Math.max(Math.abs(ex - e), Math.abs(ez - e)) / step);
            }
        }
        return max;
    }

    private static void writeHeightMap(IslandDraft draft, Path path) throws IOException {
        ModernTerrainV2 terrain = (ModernTerrainV2) terrain(draft);
        int resolution = 512;
        double halfSpan = draft.worldSpan() * 0.5;
        BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < resolution; pz++) {
            double z = -halfSpan + (pz + 0.5) * (2 * halfSpan / resolution);
            for (int px = 0; px < resolution; px++) {
                double x = -halfSpan + (px + 0.5) * (2 * halfSpan / resolution);
                double c = terrain.continentalness(x, z);
                double e = IslandTerrain.continentalnessToElevation(c);
                int color;
                if (c < -0.08) {
                    int depth = (int) Math.max(0, Math.min(120, -e * 1.2));
                    color = (0x20 + depth / 3) << 16 | (0x50 + depth / 2) << 8 | (0x90 + depth);
                } else {
                    int v = (int) Math.max(0, Math.min(255, (e - 60.0) * 1.3));
                    color = (0x40 + v * 3 / 4) << 16 | (0x70 + v * 2 / 3) << 8 | (0x30 + v / 4);
                }
                image.setRGB(px, pz, color);
            }
        }
        ImageIO.write(image, "PNG", path.toFile());
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
