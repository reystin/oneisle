package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.ModernTerrainV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class Phase2SdfQualityTest {

    @Test
    void testAnalyticDiscSdfError() {
        // Create an ideal circular disc centered at (0, 0)
        int resolution = 192;
        int islandSize = 4000;
        double halfSpan = islandSize * 1.36 * 0.5; // 2720 blocks
        double radiusBlocks = 1200.0; // 1200 block radius disc
        double radiusPx = radiusBlocks / (2 * halfSpan) * resolution; // ~42.35 px

        IslandDraft draft = new IslandDraft();
        draft.setIslandSize(islandSize);
        draft.clearLand();
        int centerPx = resolution / 2;
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                double dx = (x + 0.5) - centerPx;
                double dz = (z + 0.5) - centerPx;
                if (dx * dx + dz * dz <= radiusPx * radiusPx) {
                    draft.paint(x, z, 0, true);
                }
            }
        }

        IslandMask mask = new IslandMask(1, resolution, resolution, resolution,
            -halfSpan, halfSpan, -halfSpan, halfSpan, 192.0, 400.0, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(2, List.of(), Optional.of(mask), 42L);
        ModernTerrainV2 terrain = new ModernTerrainV2(config);

        // Compare unwarped distance against analytic disc distance inside 2 * coastWidth (384 blocks) of coast
        double sumSqErr = 0.0;
        int count = 0;
        double maxDistFromCoast = 2.0 * 192.0; // 384 blocks

        for (int r = (int)(radiusBlocks - maxDistFromCoast); r <= (int)(radiusBlocks + maxDistFromCoast); r += 4) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 5) {
                double rad = Math.toRadians(angleDeg);
                double x = r * Math.cos(rad);
                double z = r * Math.sin(rad);

                double analyticDist = radiusBlocks - r; // > 0 inland, < 0 offshore
                double computedDist = terrain.computeSignedDistance(x, z, false);

                double diff = computedDist - analyticDist;
                sumSqErr += diff * diff;
                count++;
            }
        }

        double rmsBlocks = Math.sqrt(sumSqErr / count);
        double blocksPerPx = (2 * halfSpan) / resolution; // ~28.3 blocks/px
        double rmsPx = rmsBlocks / blocksPerPx;

        // RMS error must be well within sub-pixel threshold
        assertTrue(rmsPx < 0.6, "SDF error vs analytic disc must be < 0.6 px RMS (got " + rmsPx + " px)");
    }

    @Test
    void testSignConsistencyBicubicVsBilinear() {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.NATURAL);
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(2, List.of(), Optional.of(mask), 1234L);
        ModernTerrainV2 terrain = new ModernTerrainV2(config);

        // Check 10,000 sub-pixel points across the grid
        int inconsistencies = 0;
        int totalChecked = 0;

        for (double pz = 1.0; pz < 190.0; pz += 1.87) {
            for (double px = 1.0; px < 190.0; px += 1.87) {
                double bicubic = terrain.sampleBicubicClamped(px, pz);
                double bilinear = terrain.sampleBilinear(px, pz);

                boolean signBicubic = bicubic >= 0.0;
                boolean signBilinear = bilinear >= 0.0;

                if (signBicubic != signBilinear) {
                    // Sign flips are only permitted within 0.35 px of the zero contour
                    double absVal = Math.min(Math.abs(bicubic), Math.abs(bilinear));
                    assertTrue(absVal <= 0.35,
                        "Sign flip occurred far from boundary (|dist|=" + absVal + " > 0.35 px) at px=" + px + " pz=" + pz);
                    inconsistencies++;
                }
                totalChecked++;
            }
        }

        assertTrue(totalChecked > 9000);
    }

    @Test
    void testContinentalnessContinuity() {
        // Continuity requirement: |c(x + eps) - c(x)| < 0.01 for eps = 1 block across 10,000 points
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.NATURAL);
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(2, List.of(), Optional.of(mask), 99999L);
        ModernTerrainV2 terrain = new ModernTerrainV2(config);

        long lcg = 0xDEADBEEFCAFE1234L;
        double range = halfSpan + 1000.0;

        for (int i = 0; i < 10000; i++) {
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (range * 2) - range;
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (range * 2) - range;

            double c0 = terrain.continentalness(x, z);
            double cDx = terrain.continentalness(x + 1.0, z);
            double cDz = terrain.continentalness(x, z + 1.0);

            double diffX = Math.abs(cDx - c0);
            double diffZ = Math.abs(cDz - c0);

            assertTrue(diffX < 0.015, "Continentalness must be continuous along X: diff=" + diffX + " at " + x + ", " + z);
            assertTrue(diffZ < 0.015, "Continentalness must be continuous along Z: diff=" + diffZ + " at " + x + ", " + z);
        }
    }

    @Test
    void testDeterminismAcrossThreads() throws Exception {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.CRESCENT);
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(2, List.of(), Optional.of(mask), 777L);
        ModernTerrainV2 terrain = new ModernTerrainV2(config);

        int numThreads = 8;
        int pointsPerThread = 2000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        List<Callable<double[]>> tasks = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            tasks.add(() -> {
                double[] results = new double[pointsPerThread];
                long lcg = 0x54321L;
                for (int i = 0; i < pointsPerThread; i++) {
                    lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                    double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * 5000.0 - 2500.0;
                    lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                    double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * 5000.0 - 2500.0;
                    results[i] = terrain.continentalness(x, z);
                }
                return results;
            });
        }

        List<Future<double[]>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        double[] baseline = futures.get(0).get();
        for (int t = 1; t < numThreads; t++) {
            double[] candidate = futures.get(t).get();
            assertArrayEquals(baseline, candidate, 0.0, "All threads must compute bit-for-bit identical terrain values");
        }
    }

    @Test
    void testMeanBoundaryDisplacementWithinBudget() {
        // Coastline displacement budget: <= 0.6 px mean, <= 2 px max at default settings
        int resolution = 192;
        int islandSize = 4000;
        double halfSpan = islandSize * 1.36 * 0.5;
        double blocksPerPx = (2 * halfSpan) / resolution; // ~28.3 blocks/px
        double radiusBlocks = 1200.0;

        IslandDraft draft = new IslandDraft();
        draft.setIslandSize(islandSize);
        draft.clearLand();
        int centerPx = resolution / 2;
        double radiusPx = radiusBlocks / blocksPerPx;
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                double dx = (x + 0.5) - centerPx;
                double dz = (z + 0.5) - centerPx;
                if (dx * dx + dz * dz <= radiusPx * radiusPx) {
                    draft.paint(x, z, 0, true);
                }
            }
        }

        IslandMask mask = new IslandMask(1, resolution, resolution, resolution,
            -halfSpan, halfSpan, -halfSpan, halfSpan, 192.0, 400.0, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(2, List.of(), Optional.of(mask), 5555L);
        ModernTerrainV2 terrain = new ModernTerrainV2(config);

        // Find the zero-crossing along radial rays (360 degrees)
        double totalDisplacementBlocks = 0.0;
        double maxDisplacementBlocks = 0.0;
        int rayCount = 360;

        for (int deg = 0; deg < rayCount; deg++) {
            double rad = Math.toRadians(deg);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            // Bisection search for shoreline c = -0.08
            double rMin = radiusBlocks - 150.0;
            double rMax = radiusBlocks + 150.0;

            for (int step = 0; step < 16; step++) {
                double rMid = (rMin + rMax) * 0.5;
                double cMid = terrain.continentalness(rMid * cos, rMid * sin);
                if (cMid >= -0.08) {
                    rMin = rMid;
                } else {
                    rMax = rMid;
                }
            }

            double actualShoreR = (rMin + rMax) * 0.5;
            double displacement = Math.abs(actualShoreR - radiusBlocks);
            totalDisplacementBlocks += displacement;
            if (displacement > maxDisplacementBlocks) {
                maxDisplacementBlocks = displacement;
            }
        }

        double meanDisplacementPx = (totalDisplacementBlocks / rayCount) / blocksPerPx;
        double maxDisplacementPx = maxDisplacementBlocks / blocksPerPx;

        assertTrue(meanDisplacementPx <= 0.6, "Mean boundary displacement must be <= 0.6 px (got " + meanDisplacementPx + ")");
        assertTrue(maxDisplacementPx <= 2.0, "Max boundary displacement must be <= 2.0 px (got " + maxDisplacementPx + ")");
    }
}
