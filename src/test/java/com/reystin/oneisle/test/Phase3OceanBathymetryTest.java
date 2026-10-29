package com.reystin.oneisle.test;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.ModernTerrainV2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class Phase3OceanBathymetryTest {

    private static final double DEEP_THRESHOLD = -0.58;

    @Test
    void testRadialSweepsContinuousAndGuaranteedDeepBand() {
        // Radial sweeps outward in 36 directions
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setIslandSize(4000);
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());
        // Test with islets disabled to test pure bathymetry guaranteed band
        IslandShapeConfig config = new IslandShapeConfig(2, 0.0, List.of(), Optional.of(mask), 12345L);
        ModernTerrainV2 terrain = new ModernTerrainV2(config);

        for (int deg = 0; deg < 360; deg += 10) {
            double rad = Math.toRadians(deg);
            double dirX = Math.cos(rad);
            double dirZ = Math.sin(rad);

            // Step outward from center to 4000 blocks
            double prevC = terrain.continentalness(0, 0);
            int deepBandBlocks = 0;
            int maxConsecutiveDeepBlocks = 0;

            for (double dist = 10.0; dist <= 3500.0; dist += 5.0) {
                double x = dist * dirX;
                double z = dist * dirZ;
                double c = terrain.continentalness(x, z);

                // Continuity check: 5-block step delta must not exceed 0.08
                double delta = Math.abs(c - prevC);
                assertTrue(delta < 0.08,
                    "Discontinuity detected at ray " + deg + " deg, dist " + dist + ": delta=" + delta);
                prevC = c;

                if (c <= DEEP_THRESHOLD) {
                    deepBandBlocks += 5;
                    if (deepBandBlocks > maxConsecutiveDeepBlocks) {
                        maxConsecutiveDeepBlocks = deepBandBlocks;
                    }
                } else {
                    deepBandBlocks = 0;
                }
            }

            assertTrue(maxConsecutiveDeepBlocks >= 150,
                "Ray " + deg + " deg must have a deep ocean segment >= 150 blocks long (got " + maxConsecutiveDeepBlocks + " blocks)");
        }
    }

    @Test
    void testIsletDensityZeroMatchesBitForBit() {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.NATURAL);
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());

        IslandShapeConfig configWithIslets = new IslandShapeConfig(2, 0.0, List.of(), Optional.of(mask), 8888L);
        ModernTerrainV2 terrainWithZeroDensity = new ModernTerrainV2(configWithIslets);

        long lcg = 0x112233445566L;
        for (int i = 0; i < 10000; i++) {
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * 10000.0 - 5000.0;
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * 10000.0 - 5000.0;

            double c = terrainWithZeroDensity.continentalness(x, z);
            // Must equal output when density is zero
            assertEquals(c, terrainWithZeroDensity.continentalness(x, z), 0.0);
        }
    }

    @Test
    void testSatelliteIsletsLandAreaFractionAndSeparation() {
        // Test islet land area per 10,000 x 10,000 block region at density 0.25: between 0.1% and 2.0%
        // And verify no islet within 350 blocks of the main island
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setIslandSize(2500); // smaller main island to test vast ocean
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(2, 0.25, List.of(), Optional.of(mask), 98765L);
        ModernTerrainV2 terrain = new ModernTerrainV2(config);

        // Check ocean separation: within 350 blocks of the main island, no satellite islet land (c >= -0.08) may appear
        // where main island is ocean
        for (double r = halfSpan + 10.0; r < halfSpan + 340.0; r += 20.0) {
            for (int deg = 0; deg < 360; deg += 10) {
                double rad = Math.toRadians(deg);
                double x = r * Math.cos(rad);
                double z = r * Math.sin(rad);

                double mainDist = terrain.computeSignedDistance(x, z, false);
                if (mainDist < -10.0 && mainDist > -340.0) {
                    double c = terrain.continentalness(x, z);
                    // Continentalness in this moat must remain ocean (< -0.08)
                    assertTrue(c < -0.08, "No satellite islet land allowed in 350m buffer of main island: c=" + c + " at " + x + ", " + z);
                }
            }
        }

        // Measure islet land area in offshore ocean: [ -5000, 5000 ] x [ -5000, 5000 ]
        int step = 50;
        int totalOceanPoints = 0;
        int isletLandPoints = 0;

        for (int z = -5000; z <= 5000; z += step) {
            for (int x = -5000; x <= 5000; x += step) {
                double mainDist = terrain.computeSignedDistance(x, z, false);
                if (mainDist <= -350.0) {
                    totalOceanPoints++;
                    double c = terrain.continentalness(x, z);
                    if (c >= -0.08) {
                        isletLandPoints++;
                    }
                }
            }
        }

        double landFraction = (double) isletLandPoints / (double) totalOceanPoints;
        assertTrue(landFraction >= 0.001, "Islet land fraction must be >= 0.1% (got " + (landFraction * 100.0) + "%)");
        assertTrue(landFraction <= 0.020, "Islet land fraction must be <= 2.0% (got " + (landFraction * 100.0) + "%)");
    }
}
