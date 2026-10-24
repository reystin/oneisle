package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandMask;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GoldenV1Test {
    private static final String GOLDEN_RESOURCE_PATH = "/golden/v1/golden_v1.bin.gz";
    private static final String GOLDEN_FILE_PATH = "src/test/resources/golden/v1/golden_v1.bin.gz";

    private static final long SHAPE_SEED = 1337L;
    private static final long BIOME_SEED = 4242L;
    private static final int NUM_RANDOM_POINTS = 10000;
    private static final int BIOME_GRID_SIZE = 200;
    private static final int NUM_Y_SAMPLES = 20;

    @Test
    void testGoldenV1Regression() throws Exception {
        File goldenFile = new File(GOLDEN_FILE_PATH);
        if (!goldenFile.exists()) {
            goldenFile.getParentFile().mkdirs();
            generateGoldenData(goldenFile);
        }

        InputStream is = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH);
        if (is == null && goldenFile.exists()) {
            is = new FileInputStream(goldenFile);
        }
        assertNotNull(is, "Golden v1 file must exist");
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(is)))) {
                int totalConfigs = dis.readInt();
                assertEquals(30, totalConfigs, "Must contain exactly 30 preset/relief/size configurations");

                IslandDraft.Preset[] v1Presets = {
                    IslandDraft.Preset.NATURAL,
                    IslandDraft.Preset.ROUND,
                    IslandDraft.Preset.LONG,
                    IslandDraft.Preset.CRESCENT,
                    IslandDraft.Preset.ARCHIPELAGO
                };

                for (IslandDraft.Preset preset : v1Presets) {
                    for (IslandDraft.Relief relief : IslandDraft.Relief.values()) {
                        for (int size : new int[]{2500, 8000}) {
                            int savedPresetOrd = dis.readInt();
                            int savedReliefOrd = dis.readInt();
                            int savedSize = dis.readInt();
                            assertEquals(preset.ordinal(), savedPresetOrd);
                            assertEquals(relief.ordinal(), savedReliefOrd);
                            assertEquals(size, savedSize);

                            IslandDraft draft = new IslandDraft();
                            draft.setPreset(preset);
                            draft.setRelief(relief);
                            draft.setIslandSize(size);
                            draft.setShapeSeed(SHAPE_SEED);
                            draft.setBiomeSeed(BIOME_SEED);

                            double halfSpan = draft.worldSpan() * 0.5;
                            IslandMask mask = new IslandMask(
                                IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
                                -halfSpan, halfSpan, -halfSpan, halfSpan,
                                draft.relief().coastWidth, 400.0, draft.packedMask());

                            long lcg = 0x123456789ABCDEFL;
                            double maxRange = halfSpan + 1500.0;
                            for (int i = 0; i < NUM_RANDOM_POINTS; i++) {
                                lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                                double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (maxRange * 2) - maxRange;
                                lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                                double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (maxRange * 2) - maxRange;

                                double expected = dis.readDouble();
                                double actual = mask.sampleTerrainValue(x, z);
                                assertEquals(expected, actual, 1e-15,
                                    "Continentalness mismatch at sample " + i + " for preset=" + preset + " relief=" + relief + " size=" + size);
                            }

                            double step = (halfSpan * 2) / (double) BIOME_GRID_SIZE;
                            for (int gz = 0; gz < BIOME_GRID_SIZE; gz++) {
                                double z = -halfSpan + (gz + 0.5) * step;
                                for (int gx = 0; gx < BIOME_GRID_SIZE; gx++) {
                                    double x = -halfSpan + (gx + 0.5) * step;
                                    double c = mask.sampleTerrainValue(x, z);
                                    BiomePreview.Type surfaceBiome = draft.selectBiome(
                                        BiomePreview.surface(c, x, z, BIOME_SEED), x, z);
                                    byte expected = dis.readByte();
                                    assertEquals(expected, (byte) surfaceBiome.ordinal(),
                                        "Biome mismatch at grid (" + gx + ", " + gz + ")");
                                }
                            }

                            for (int yIdx = 0; yIdx < NUM_Y_SAMPLES; yIdx++) {
                                lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                                double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
                                lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                                double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
                                boolean expected = dis.readBoolean();
                                boolean actual = BiomePreview.isDeepDark(x, z, BIOME_SEED);
                                assertEquals(expected, actual, "DeepDark mismatch at sample " + yIdx);
                            }
                        }
                    }
                }
        }
    }

    private static void generateGoldenData(File file) throws Exception {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            dos.writeInt(30); // 5 presets * 3 reliefs * 2 sizes

            for (IslandDraft.Preset preset : IslandDraft.Preset.values()) {
                for (IslandDraft.Relief relief : IslandDraft.Relief.values()) {
                    for (int size : new int[]{2500, 8000}) {
                        dos.writeInt(preset.ordinal());
                        dos.writeInt(relief.ordinal());
                        dos.writeInt(size);

                        IslandDraft draft = new IslandDraft();
                        draft.setPreset(preset);
                        draft.setRelief(relief);
                        draft.setIslandSize(size);
                        draft.setShapeSeed(SHAPE_SEED);
                        draft.setBiomeSeed(BIOME_SEED);

                        double halfSpan = draft.worldSpan() * 0.5;
                        IslandMask mask = new IslandMask(
                            IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
                            -halfSpan, halfSpan, -halfSpan, halfSpan,
                            draft.relief().coastWidth, 400.0, draft.packedMask());

                        long lcg = 0x123456789ABCDEFL;
                        double maxRange = halfSpan + 1500.0;
                        for (int i = 0; i < NUM_RANDOM_POINTS; i++) {
                            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                            double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (maxRange * 2) - maxRange;
                            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                            double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (maxRange * 2) - maxRange;

                            double c = mask.sampleTerrainValue(x, z);
                            dos.writeDouble(c);
                        }

                        double step = (halfSpan * 2) / (double) BIOME_GRID_SIZE;
                        for (int gz = 0; gz < BIOME_GRID_SIZE; gz++) {
                            double z = -halfSpan + (gz + 0.5) * step;
                            for (int gx = 0; gx < BIOME_GRID_SIZE; gx++) {
                                double x = -halfSpan + (gx + 0.5) * step;
                                double c = mask.sampleTerrainValue(x, z);
                                BiomePreview.Type surfaceBiome = draft.selectBiome(
                                    BiomePreview.surface(c, x, z, BIOME_SEED), x, z);
                                dos.writeByte((byte) surfaceBiome.ordinal());
                            }
                        }

                        for (int yIdx = 0; yIdx < NUM_Y_SAMPLES; yIdx++) {
                            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                            double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
                            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                            double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
                            boolean deepDark = BiomePreview.isDeepDark(x, z, BIOME_SEED);
                            dos.writeBoolean(deepDark);
                        }
                    }
                }
            }
        }
    }
}
