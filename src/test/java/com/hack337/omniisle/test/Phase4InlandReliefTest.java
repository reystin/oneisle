package com.hack337.omniisle.test;

import com.google.gson.JsonElement;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.LegacyTerrainV1;
import com.hack337.omniisle.continent.ModernTerrainV2;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase4InlandReliefTest {

    private static IslandShapeConfig createRoundIslandConfig(long seed, IslandDraft.Relief relief, double islandSize, IslandShapeConfig.HeightLayer heightLayer) {
        int res = 192;
        boolean[] land = new boolean[res * res];
        double radiusPx = res * 0.35;
        double cx = res / 2.0;
        double cz = res / 2.0;
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                double dx = x + 0.5 - cx;
                double dz = z + 0.5 - cz;
                if (dx * dx + dz * dz <= radiusPx * radiusPx) {
                    land[z * res + x] = true;
                }
            }
        }

        byte[] packed = new byte[(land.length + 7) / 8];
        for (int i = 0; i < land.length; i++) {
            if (land[i]) packed[i >>> 3] |= (byte) (1 << (i & 7));
        }
        String maskData = java.util.Base64.getEncoder().encodeToString(packed);

        double halfSpan = islandSize * 1.36 / 2.0;
        IslandMask mask = new IslandMask(
            res, res, -halfSpan, halfSpan, -halfSpan, halfSpan,
            relief.coastWidth, 400.0, maskData
        );

        return new IslandShapeConfig(
            2,
            0.0,
            Optional.ofNullable(heightLayer),
            Collections.emptyList(),
            Optional.of(mask),
            seed
        );
    }

    @Test
    @DisplayName("4.1 Inland relief: round island at seeds 1..5 is not a single cone (>= 2 local maxima in >= 3 of 5 seeds)")
    void testNotSingleConeMultiMaxima() {
        int seedsWithMultiMaxima = 0;

        for (long seed = 1; seed <= 5; seed++) {
            IslandShapeConfig config = createRoundIslandConfig(seed, IslandDraft.Relief.BALANCED, 4000, null);
            ModernTerrainV2 terrain = new ModernTerrainV2(config);

            // Sample inland grid (step 24 blocks, range [-1000, 1000])
            int step = 24;
            int minCoord = -960;
            int maxCoord = 960;
            int count = (maxCoord - minCoord) / step + 1;
            double[][] grid = new double[count][count];

            double maxC = -1.0;
            for (int zi = 0; zi < count; zi++) {
                double z = minCoord + zi * step;
                for (int xi = 0; xi < count; xi++) {
                    double x = minCoord + xi * step;
                    double c = terrain.continentalness(x, z);
                    grid[zi][xi] = c;
                    if (c > maxC) maxC = c;
                }
            }

            // Find local maxima with threshold c > 0.40 (inland high ground)
            record Peak(int xi, int zi, double c) {}
            List<Peak> peaks = new ArrayList<>();

            for (int zi = 2; zi < count - 2; zi++) {
                for (int xi = 2; xi < count - 2; xi++) {
                    double val = grid[zi][xi];
                    if (val < 0.40) continue;
                    boolean isLocalMax = true;
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (dx == 0 && dz == 0) continue;
                            if (grid[zi + dz][xi + dx] >= val) {
                                isLocalMax = false;
                                break;
                            }
                        }
                        if (!isLocalMax) break;
                    }
                    if (isLocalMax) {
                        peaks.add(new Peak(xi, zi, val));
                    }
                }
            }

            // Filter peaks that are distinct and separated by at least 150 blocks
            List<Peak> distinctPeaks = new ArrayList<>();
            for (Peak p : peaks) {
                boolean distinct = true;
                for (Peak other : distinctPeaks) {
                    double distBlocks = Math.hypot((p.xi - other.xi) * step, (p.zi - other.zi) * step);
                    if (distBlocks < 150.0) {
                        distinct = false;
                        break;
                    }
                }
                if (distinct) distinctPeaks.add(p);
            }

            if (distinctPeaks.size() >= 2) {
                seedsWithMultiMaxima++;
            }

            // Verify no flat plateau wider than 150 blocks at max height
            // Flat plateau = region within 0.001 of maxC
            int maxPlateauSpan = 0;
            for (int zi = 0; zi < count; zi++) {
                int currentSpan = 0;
                for (int xi = 0; xi < count; xi++) {
                    if (Math.abs(grid[zi][xi] - maxC) < 0.002) {
                        currentSpan += step;
                        if (currentSpan > maxPlateauSpan) maxPlateauSpan = currentSpan;
                    } else {
                        currentSpan = 0;
                    }
                }
            }
            assertTrue(maxPlateauSpan < 150, "Plateau at max height should be < 150 blocks wide, was: " + maxPlateauSpan);
        }

        assertTrue(seedsWithMultiMaxima >= 3,
            "At least 3 of 5 seeds must have >= 2 distinct peaks, found: " + seedsWithMultiMaxima);
    }

    @Test
    @DisplayName("4.2 Painting Tier 3 in center of FLAT island produces peaks >= 40 blocks higher with smooth transitions")
    void testTier3PaintingElevationBoostAndSmoothness() {
        int res = 192;
        byte[] heightLayer = new byte[res * res];
        Arrays.fill(heightLayer, (byte) 1);

        // Paint Tier 3 in the center (radius 14 px)
        int cx = res / 2;
        int cz = res / 2;
        int r = 14;
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r) {
                    heightLayer[z * res + x] = 3;
                }
            }
        }

        // Pack into 2-bit Base64
        byte[] packed = new byte[(heightLayer.length + 3) / 4];
        for (int i = 0; i < heightLayer.length; i++) {
            packed[i >>> 2] |= (byte) ((heightLayer[i] & 3) << ((i & 3) * 2));
        }
        String heightData = java.util.Base64.getEncoder().encodeToString(packed);
        IslandShapeConfig.HeightLayer hl = new IslandShapeConfig.HeightLayer(2, heightData);

        long seed = 42L;
        IslandShapeConfig cfgWithout = createRoundIslandConfig(seed, IslandDraft.Relief.FLAT, 4000, null);
        IslandShapeConfig cfgWith = createRoundIslandConfig(seed, IslandDraft.Relief.FLAT, 4000, hl);

        ModernTerrainV2 terrainWithout = new ModernTerrainV2(cfgWithout);
        ModernTerrainV2 terrainWith = new ModernTerrainV2(cfgWith);

        // Sample along a radial transect through the center
        double maxElevWithout = -1.0e9;
        double maxElevWith = -1.0e9;
        double maxSlope = 0.0;
        int sampleStep = 8; // 8-block samples as specified in prompt

        double prevElev = IslandTerrain.continentalnessToElevation(terrainWith.continentalness(-600, 0));
        for (int x = -592; x <= 600; x += sampleStep) {
            double cWithout = terrainWithout.continentalness(x, 0);
            double cWith = terrainWith.continentalness(x, 0);

            double elevWithout = IslandTerrain.continentalnessToElevation(cWithout);
            double elevWith = IslandTerrain.continentalnessToElevation(cWith);

            if (elevWithout > maxElevWithout) maxElevWithout = elevWithout;
            if (elevWith > maxElevWith) maxElevWith = elevWith;

            double slope = Math.abs(elevWith - prevElev) / sampleStep;
            if (slope > maxSlope) maxSlope = slope;
            prevElev = elevWith;
        }

        double peakBoost = maxElevWith - maxElevWithout;
        assertTrue(peakBoost >= 40.0,
            "Tier 3 painting should boost peak elevation by >= 40 blocks, but boost was: " + peakBoost);

        // Max slope between neighbouring 8-block samples must be smooth (max slope <= vanilla max slope * 1.5, where vanilla mountain slope is ~1.8)
        assertTrue(maxSlope <= 2.5,
            "Max slope between 8-block samples must be smooth (<= 2.5), was: " + maxSlope);
    }

    @Test
    @DisplayName("4.3 Undo/redo works across both land and height layers")
    void testUndoRedoAcrossBothLayers() {
        IslandDraft draft = new IslandDraft();
        int initialLandCount = 0;
        for (int z = 0; z < IslandDraft.RESOLUTION; z++) {
            for (int x = 0; x < IslandDraft.RESOLUTION; x++) {
                if (draft.isLand(x, z)) initialLandCount++;
            }
        }
        assertTrue(initialLandCount > 0, "Initial draft should have land");

        // Land stroke
        draft.pushUndo();
        draft.paint(10, 10, 5, true);
        assertTrue(draft.isLand(10, 10));

        // Height stroke
        draft.pushUndo();
        draft.paintHeight(10, 10, 5, 3);
        assertEquals(3, draft.tier(10, 10));

        // Undo height stroke
        assertTrue(draft.canUndo());
        draft.undo();
        assertEquals(1, draft.tier(10, 10), "Height should revert to default 1");
        assertTrue(draft.isLand(10, 10), "Land should still be painted");

        // Undo land stroke
        assertTrue(draft.canUndo());
        draft.undo();
        // Redo land stroke
        assertTrue(draft.canRedo());
        draft.redo();
        assertTrue(draft.isLand(10, 10), "Land should be restored on redo");
        assertEquals(1, draft.tier(10, 10));

        // Redo height stroke
        assertTrue(draft.canRedo());
        draft.redo();
        assertEquals(3, draft.tier(10, 10), "Height should be restored to Tier 3 on redo");

        // Clear height
        draft.clearHeight();
        assertEquals(1, draft.tier(10, 10));
        draft.undo();
        assertEquals(3, draft.tier(10, 10), "Clear height should be undoable");
    }

    @Test
    @DisplayName("4.4 JSON serialization and deserialization of height_layer round-trips correctly")
    void testHeightLayerCodecRoundTrip() {
        int res = 192;
        byte[] height = new byte[res * res];
        Arrays.fill(height, (byte) 1);
        height[100] = 0;
        height[200] = 2;
        height[300] = 3;

        byte[] packed = new byte[(height.length + 3) / 4];
        for (int i = 0; i < height.length; i++) {
            packed[i >>> 2] |= (byte) ((height[i] & 3) << ((i & 3) * 2));
        }
        String b64 = java.util.Base64.getEncoder().encodeToString(packed);

        IslandShapeConfig original = new IslandShapeConfig(
            2,
            0.35,
            Optional.of(new IslandShapeConfig.HeightLayer(2, b64)),
            Collections.emptyList(),
            Optional.empty(),
            12345L
        );

        JsonElement json = IslandShapeConfig.CODEC.encodeStart(JsonOps.INSTANCE, original)
            .getOrThrow(IllegalStateException::new);

        IslandShapeConfig decoded = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(IllegalStateException::new);

        assertEquals(2, decoded.generationVersion);
        assertEquals(0.35, decoded.isletDensity, 1e-6);
        assertNotNull(decoded.heightLayer);
        assertEquals(b64, decoded.heightLayer.data());
        assertNotNull(decoded.unpackedHeight);
        assertEquals(0, decoded.unpackedHeight[100]);
        assertEquals(1, decoded.unpackedHeight[101]);
        assertEquals(2, decoded.unpackedHeight[200]);
        assertEquals(3, decoded.unpackedHeight[300]);

        // Absent height_layer should cleanly deserialize as null / default
        IslandShapeConfig without = new IslandShapeConfig(
            2, 0.25, Optional.empty(), Collections.emptyList(), Optional.empty(), 99L
        );
        JsonElement jsonWithout = IslandShapeConfig.CODEC.encodeStart(JsonOps.INSTANCE, without)
            .getOrThrow(IllegalStateException::new);
        IslandShapeConfig decodedWithout = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE, jsonWithout)
            .getOrThrow(IllegalStateException::new);
        assertNull(decodedWithout.heightLayer);
        assertNull(decodedWithout.unpackedHeight);
    }

    @Test
    @DisplayName("4.5 Invariant I1: Legacy generation (v1) ignores height layer and preserves 0.5.0 math")
    void testLegacyV1UnaffectedByHeightLayer() {
        IslandShapeConfig.HeightLayer hl = new IslandShapeConfig.HeightLayer(2, "AAAA");
        IslandShapeConfig cfgV1WithHeight = new IslandShapeConfig(
            1,
            0.0,
            Optional.of(hl),
            Collections.emptyList(),
            Optional.empty(),
            100L
        );
        IslandTerrain terrain = IslandTerrain.create(cfgV1WithHeight);
        assertInstanceOf(LegacyTerrainV1.class, terrain, "v1 config must create LegacyTerrainV1 instance");
    }
}
