package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.ImageImportExport;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.ShareCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase7EditorTest {

    @Test
    @DisplayName("7.1 Share Code Property Test: 200 random drafts encode/decode round-trip")
    void testShareCodeRoundTrip200Drafts() {
        Random rng = new Random(987654321L);
        IslandDraft.Preset[] presets = IslandDraft.Preset.values();
        IslandDraft.Relief[] reliefs = IslandDraft.Relief.values();
        IslandDraft.BiomeTheme[] themes = IslandDraft.BiomeTheme.values();
        int[] resolutions = {192, 256, 384};

        for (int i = 0; i < 200; i++) {
            int res = resolutions[rng.nextInt(resolutions.length)];
            IslandDraft draft = new IslandDraft(res);
            draft.setIslandSize(2500 + rng.nextInt(56) * 100);
            draft.setShapeSeed(rng.nextLong());
            draft.setBiomeSeed(rng.nextLong());
            draft.setPreset(presets[rng.nextInt(presets.length)]);
            draft.setRelief(reliefs[rng.nextInt(reliefs.length)]);
            draft.applyTheme(themes[rng.nextInt(themes.length)]);
            draft.setGenerationVersion(rng.nextBoolean() ? IslandDraft.GenerationVersion.V2 : IslandDraft.GenerationVersion.V1);
            draft.setIsletDensity(rng.nextDouble());
            draft.setStructureDensity(0.5 + rng.nextInt(4) * 0.5);
            draft.setClimateScale(384 + rng.nextInt(25) * 48);
            draft.setGradientStrength(rng.nextDouble());

            // Add some strokes or custom paint
            for (int k = 0; k < 5; k++) {
                int px = rng.nextInt(res);
                int pz = rng.nextInt(res);
                draft.paint(px, pz, 2 + rng.nextInt(6), rng.nextBoolean());
                draft.paintHeight(px, pz, 2 + rng.nextInt(6), rng.nextInt(4));
            }

            if (rng.nextBoolean()) {
                int[] autoSp = draft.effectiveSpawnPx();
                draft.setSpawn(autoSp[0], autoSp[1]);
            } else {
                draft.clearSpawn();
            }

            // Encode
            String code = ShareCode.encode(draft);
            assertNotNull(code);
            assertTrue(code.startsWith(ShareCode.PREFIX));

            // Decode
            IslandDraft decoded = ShareCode.decode(code);
            assertNotNull(decoded);

            // Assert parity
            assertEquals(draft.resolution(), decoded.resolution(), "Resolution mismatch at trial " + i);
            assertEquals(draft.islandSize(), decoded.islandSize(), "IslandSize mismatch at trial " + i);
            assertEquals(draft.shapeSeed(), decoded.shapeSeed(), "ShapeSeed mismatch at trial " + i);
            assertEquals(draft.biomeSeed(), decoded.biomeSeed(), "BiomeSeed mismatch at trial " + i);
            assertEquals(draft.preset(), decoded.preset(), "Preset mismatch at trial " + i);
            assertEquals(draft.relief(), decoded.relief(), "Relief mismatch at trial " + i);
            assertEquals(draft.theme(), decoded.theme(), "Theme mismatch at trial " + i);
            assertEquals(draft.generationVersionNumber(), decoded.generationVersionNumber(), "GenVersion mismatch at trial " + i);
            assertEquals(draft.isletDensity(), decoded.isletDensity(), 0.0001, "IsletDensity mismatch at trial " + i);
            assertEquals(draft.structureDensity(), decoded.structureDensity(), 0.0001, "StructureDensity mismatch at trial " + i);
            assertEquals(draft.climateScale(), decoded.climateScale(), "ClimateScale mismatch at trial " + i);
            assertEquals(draft.gradientMode(), decoded.gradientMode(), "GradientMode mismatch at trial " + i);
            assertEquals(draft.gradientStrength(), decoded.gradientStrength(), 0.0001, "GradientStrength mismatch at trial " + i);
            assertEquals(draft.explicitSpawnPx(), decoded.explicitSpawnPx(), "SpawnPx mismatch at trial " + i);
            assertEquals(draft.explicitSpawnPz(), decoded.explicitSpawnPz(), "SpawnPz mismatch at trial " + i);

            assertEquals(draft.packedMask(), decoded.packedMask(), "Mask data mismatch at trial " + i);
            assertEquals(draft.packedHeight(), decoded.packedHeight(), "Height data mismatch at trial " + i);

            assertEquals(draft.enabledBiomes().values(), decoded.enabledBiomes().values(), "Biomes mismatch at trial " + i);
            assertEquals(draft.enabledStructures(), decoded.enabledStructures(), "Structures mismatch at trial " + i);
        }
    }

    @Test
    @DisplayName("7.2 Share Code Tamper Resistance & Security Validation")
    void testShareCodeTamperResistance() {
        IslandDraft draft = new IslandDraft();
        String validCode = ShareCode.encode(draft);

        // Corrupted checksum
        String corruptedCrc = validCode.substring(0, validCode.length() - 2) + "00";
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(corruptedCrc));

        // Corrupted payload
        int middle = validCode.indexOf('-') + 5;
        char altered = validCode.charAt(middle) == 'A' ? 'B' : 'A';
        String corruptedPayload = validCode.substring(0, middle) + altered + validCode.substring(middle + 1);
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(corruptedPayload));

        // Missing prefix
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("INVALID-CODE-1234"));

        // Null / empty
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(null));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(""));
    }

    @Test
    @DisplayName("7.3 PNG Mask & Heightmap Import/Export Round-Trip Test")
    void testPngImportExportRoundTrip() throws Exception {
        IslandDraft draft = new IslandDraft(192);
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.rebuildShape();

        // Paint tiers
        draft.paintHeight(96, 96, 25, 3);
        draft.paintHeight(96, 96, 50, 2);

        Path tempDir = Files.createTempDirectory("omniisle_png_test");
        Path maskPath = tempDir.resolve("mask.png");
        Path heightPath = tempDir.resolve("height.png");

        // Export mask
        ImageImportExport.exportMask(draft, maskPath);
        assertTrue(Files.exists(maskPath));

        // Import mask
        BufferedImage maskImg = ImageIO.read(maskPath.toFile());
        boolean[] importedLand = ImageImportExport.importMask(maskImg, 192, 128, false);

        for (int z = 0; z < 192; z++) {
            for (int x = 0; x < 192; x++) {
                assertEquals(draft.isLand(x, z), importedLand[z * 192 + x],
                    "Mask bit mismatch at (" + x + ", " + z + ")");
            }
        }

        // Export height and verify the documented 4-shade grayscale encoding
        ImageImportExport.exportHeight(draft, heightPath);
        assertTrue(Files.exists(heightPath));

        BufferedImage heightImg = ImageIO.read(heightPath.toFile());
        for (int z = 0; z < 192; z++) {
            for (int x = 0; x < 192; x++) {
                int expectedGray = (draft.tier(x, z) & 3) * 85;
                assertEquals(expectedGray, heightImg.getRGB(x, z) & 0xFF,
                    "Height shade mismatch at (" + x + ", " + z + ")");
            }
        }
    }

    @Test
    @DisplayName("7.4 Shape Presets Validation: 13 presets x 20 seeds, land fraction 12-55%, component >= 70%, 0 border pixels")
    void testAllShapePresets() {
        IslandDraft.Preset[] presets = IslandDraft.Preset.values();

        for (IslandDraft.Preset preset : presets) {
            for (long seed = 1L; seed <= 20L; seed++) {
                IslandDraft draft = new IslandDraft(192);
                draft.setPreset(preset);
                draft.setShapeSeed(seed);
                draft.rebuildShape();

                int res = draft.resolution();
                int totalPixels = res * res;
                int landCount = 0;

                // 1. Check border pixels are 0
                for (int x = 0; x < res; x++) {
                    assertFalse(draft.isLand(x, 0), preset + " seed " + seed + " has land on top border at " + x);
                    assertFalse(draft.isLand(x, res - 1), preset + " seed " + seed + " has land on bottom border at " + x);
                }
                for (int z = 0; z < res; z++) {
                    assertFalse(draft.isLand(0, z), preset + " seed " + seed + " has land on left border at " + z);
                    assertFalse(draft.isLand(res - 1, z), preset + " seed " + seed + " has land on right border at " + z);
                }

                // 2. Check land fraction
                for (int z = 0; z < res; z++) {
                    for (int x = 0; x < res; x++) {
                        if (draft.isLand(x, z)) landCount++;
                    }
                }
                double landFraction = (double) landCount / totalPixels;
                assertTrue(landFraction >= 0.12, preset + " seed " + seed + " land fraction too small: " + (landFraction * 100) + "%");
                assertTrue(landFraction <= 0.55, preset + " seed " + seed + " land fraction too large: " + (landFraction * 100) + "%");

                // 3. Check single largest component >= 70% (except ARCHIPELAGO and TWIN_ISLANDS)
                if (preset != IslandDraft.Preset.ARCHIPELAGO && preset != IslandDraft.Preset.TWIN_ISLANDS) {
                    int largestComp = largestConnectedLandComponent(draft);
                    double compRatio = (double) largestComp / landCount;
                    assertTrue(compRatio >= 0.70,
                        preset + " seed " + seed + " largest component only " + (compRatio * 100) + "% of land (" + largestComp + "/" + landCount + ")");
                }
            }
        }
    }

    private int largestConnectedLandComponent(IslandDraft draft) {
        int res = draft.resolution();
        boolean[] visited = new boolean[res * res];
        int maxComp = 0;

        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                if (!draft.isLand(x, z) || visited[z * res + x]) continue;
                int compSize = 0;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, z});
                visited[z * res + x] = true;

                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    compSize++;
                    int cx = p[0];
                    int cz = p[1];

                    int[][] neighbors = {{cx + 1, cz}, {cx - 1, cz}, {cx, cz + 1}, {cx, cz - 1}};
                    for (int[] n : neighbors) {
                        int nx = n[0];
                        int nz = n[1];
                        if (nx >= 0 && nx < res && nz >= 0 && nz < res && draft.isLand(nx, nz) && !visited[nz * res + nx]) {
                            visited[nz * res + nx] = true;
                            queue.add(new int[]{nx, nz});
                        }
                    }
                }
                if (compSize > maxComp) maxComp = compSize;
            }
        }
        return maxComp;
    }

    @Test
    @DisplayName("7.5 Symmetry Tools: Mirror X, Mirror Z, 4-Way exact mathematical symmetry")
    void testSymmetryTools() {
        int res = 192;
        IslandDraft draft = new IslandDraft(res);
        draft.clearLand();

        // 1. Mirror Z
        draft.paint(50, 40, 5, true, IslandDraft.Symmetry.MIRROR_Z);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                assertEquals(draft.isLand(x, z), draft.isLand(x, res - 1 - z),
                    "Mirror Z asymmetry at (" + x + ", " + z + ")");
            }
        }

        // 2. Mirror X
        draft.clearLand();
        draft.paint(40, 50, 5, true, IslandDraft.Symmetry.MIRROR_X);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                assertEquals(draft.isLand(x, z), draft.isLand(res - 1 - x, z),
                    "Mirror X asymmetry at (" + x + ", " + z + ")");
            }
        }

        // 3. Four-Way
        draft.clearLand();
        draft.paint(30, 45, 6, true, IslandDraft.Symmetry.FOUR_WAY);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                boolean val = draft.isLand(x, z);
                assertEquals(val, draft.isLand(res - 1 - x, z), "4-Way X asymmetry");
                assertEquals(val, draft.isLand(x, res - 1 - z), "4-Way Z asymmetry");
                assertEquals(val, draft.isLand(res - 1 - x, res - 1 - z), "4-Way XZ asymmetry");
            }
        }
    }

    @Test
    @DisplayName("7.6 Multi-Resolution Support: 192, 256, 384 resizing and packedMask scaling")
    void testMultiResolutionDraft() {
        IslandDraft draft = new IslandDraft(192);
        assertEquals(192, draft.resolution());
        assertEquals(192 * 192 / 8, (draft.resolution() * draft.resolution() + 7) / 8);

        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.rebuildShape();
        int land192 = 0;
        for (int z = 0; z < 192; z++) {
            for (int x = 0; x < 192; x++) {
                if (draft.isLand(x, z)) land192++;
            }
        }
        double frac192 = (double) land192 / (192 * 192);

        // Resample to 256
        draft.setResolution(256);
        assertEquals(256, draft.resolution());
        int land256 = 0;
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                if (draft.isLand(x, z)) land256++;
            }
        }
        double frac256 = (double) land256 / (256 * 256);
        assertEquals(frac192, frac256, 0.03, "Land fraction preserved across resample to 256");

        // Resample to 384
        draft.setResolution(384);
        assertEquals(384, draft.resolution());
        int land384 = 0;
        for (int z = 0; z < 384; z++) {
            for (int x = 0; x < 384; x++) {
                if (draft.isLand(x, z)) land384++;
            }
        }
        double frac384 = (double) land384 / (384 * 384);
        assertEquals(frac192, frac384, 0.03, "Land fraction preserved across resample to 384");

        // Packed mask byte length check
        byte[] decoded384 = java.util.Base64.getDecoder().decode(draft.packedMask());
        assertEquals((384 * 384 + 7) / 8, decoded384.length);
    }
}
