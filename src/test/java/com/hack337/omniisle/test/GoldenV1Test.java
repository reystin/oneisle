package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GoldenV1Test {
    private static final String GOLDEN_RESOURCE_PATH = "/golden/v1/golden_v1.bin.gz";
    private static final String GOLDEN_FILE_PATH = "src/test/resources/golden/v1/golden_v1.bin.gz";
    private static final String SOURCE_GOLDEN_RESOURCE_PATH = "/golden/v1/golden_v1_source.txt";
    private static final String SOURCE_GOLDEN_FILE_PATH = "src/test/resources/golden/v1/golden_v1_source.txt";

    private static final long SHAPE_SEED = 1337L;
    private static final long BIOME_SEED = 4242L;
    private static final int NUM_RANDOM_POINTS = 10000;
    private static final int BIOME_GRID_SIZE = 200;
    private static final int NUM_Y_SAMPLES = 20;
    private static final int SOURCE_UNDERGROUND_SAMPLES = 100;
    private static final int[] SOURCE_Y_LEVELS = {-32, 0, 40, 120};

    private static final IslandDraft.Preset[] V1_PRESETS = {
        IslandDraft.Preset.NATURAL,
        IslandDraft.Preset.ROUND,
        IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT,
        IslandDraft.Preset.ARCHIPELAGO
    };

    @BeforeAll
    static void boot() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void testGoldenV1Regression() throws Exception {
        InputStream is = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH);
        assertNotNull(is, "Golden v1 file must exist in src/test/resources/golden/v1");
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(is)))) {
                int totalConfigs = dis.readInt();
                assertEquals(30, totalConfigs, "Must contain exactly 30 preset/relief/size configurations");

                for (IslandDraft.Preset preset : V1_PRESETS) {
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

    @Test
    void testGoldenV1BiomeSourceRegression() throws Exception {
        InputStream is = getClass().getResourceAsStream(SOURCE_GOLDEN_RESOURCE_PATH);
        assertNotNull(is, "Golden v1 biome-source snapshot missing; run -Domniisle.regenerateGolden=true");
        List<String> lines = new ArrayList<>();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) lines.add(line);
            }
        }
        assertEquals(30, lines.size(), "Expected 30 v1 biome-source golden rows");
        int index = 0;
        for (IslandDraft.Preset preset : V1_PRESETS) {
            for (IslandDraft.Relief relief : IslandDraft.Relief.values()) {
                for (int size : new int[]{2500, 8000}) {
                    String[] fields = lines.get(index++).split(",");
                    assertEquals(preset.name(), fields[0]);
                    assertEquals(relief.name(), fields[1]);
                    assertEquals(String.valueOf(size), fields[2]);
                    IslandDraft draft = goldenDraft(preset, relief, size);
                    long[] actual = sourceHashes(draft);
                    assertEquals(Long.parseLong(fields[3]), actual[0],
                        "Surface biome-source hash mismatch for " + preset + "/" + relief + "/" + size);
                    for (int yIdx = 0; yIdx < SOURCE_Y_LEVELS.length; yIdx++) {
                        assertEquals(Long.parseLong(fields[4 + yIdx]), actual[1 + yIdx],
                            "Underground biome-source hash mismatch at y=" + SOURCE_Y_LEVELS[yIdx]
                                + " for " + preset + "/" + relief + "/" + size);
                    }
                }
            }
        }
    }

    @Test
    void regenerateGoldenIfRequested() throws Exception {
        String mode = System.getProperty("omniisle.regenerateGolden");
        Assumptions.assumeTrue(mode != null && !mode.isBlank());
        File golden = new File(GOLDEN_FILE_PATH);
        golden.getParentFile().mkdirs();
        if (!"source".equals(mode)) generateGoldenData(golden);
        generateSourceGolden(new File(SOURCE_GOLDEN_FILE_PATH));
    }

    private static IslandDraft goldenDraft(IslandDraft.Preset preset, IslandDraft.Relief relief, int size) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setRelief(relief);
        draft.setIslandSize(size);
        draft.setShapeSeed(SHAPE_SEED);
        draft.setBiomeSeed(BIOME_SEED);
        draft.setRiverDensity(0.0);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V1);
        return draft;
    }

    private static long[] sourceHashes(IslandDraft draft) {
        IslandShapeConfig cfg = TestWorldgen.config(draft, 1, 0.0);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        Climate.Sampler sampler = TestWorldgen.sampler(terrain);
        OmniBiomeSource source = TestWorldgen.biomeSource(720, BIOME_SEED, 1, "north_south", 0.5, "standard");
        double halfSpan = draft.worldSpan() * 0.5;

        long surfaceHash = 0xCBF29CE484222325L;
        double step = (halfSpan * 2.0) / BIOME_GRID_SIZE;
        for (int gz = 0; gz < BIOME_GRID_SIZE; gz++) {
            for (int gx = 0; gx < BIOME_GRID_SIZE; gx++) {
                double x = -halfSpan + (gx + 0.5) * step;
                double z = -halfSpan + (gz + 0.5) * step;
                int quartX = (int) Math.floor(x) >> 2;
                int quartZ = (int) Math.floor(z) >> 2;
                String biome = source.getNoiseBiome(quartX, 16, quartZ, sampler).unwrapKey()
                    .map(k -> k.identifier().getPath()).orElse("");
                surfaceHash = fnv(surfaceHash, biome);
            }
        }

        long[] hashes = new long[1 + SOURCE_Y_LEVELS.length];
        hashes[0] = surfaceHash;
        for (int yIdx = 0; yIdx < SOURCE_Y_LEVELS.length; yIdx++) {
            int y = SOURCE_Y_LEVELS[yIdx];
            long lcg = 0x9E3779B97F4A7C15L ^ (y * 0x100000001B3L);
            long hash = 0xCBF29CE484222325L;
            for (int i = 0; i < SOURCE_UNDERGROUND_SAMPLES; i++) {
                lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
                lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
                int quartX = (int) Math.floor(x) >> 2;
                int quartZ = (int) Math.floor(z) >> 2;
                String biome = source.getNoiseBiome(quartX, y >> 2, quartZ, sampler).unwrapKey()
                    .map(k -> k.identifier().getPath()).orElse("");
                hash = fnv(hash, biome);
            }
            hashes[1 + yIdx] = hash;
        }
        return hashes;
    }

    private static long fnv(long hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash ^ 0x9E3779B97F4A7C15L;
    }

    private static void generateSourceGolden(File file) throws Exception {
        try (var writer = Files.newBufferedWriter(Path.of(file.getPath()), StandardCharsets.UTF_8)) {
            writer.write("# V1 OmniBiomeSource snapshot: 200x200 surface grid (y=64) + 4 underground Y levels,\n");
            writer.write("# 100 LCG points each. Regenerate with -Domniisle.regenerateGolden=true.\n");
            writer.write("# preset,relief,size,surface,y-32,y0,y40,y120\n");
            for (IslandDraft.Preset preset : V1_PRESETS) {
                for (IslandDraft.Relief relief : IslandDraft.Relief.values()) {
                    for (int size : new int[]{2500, 8000}) {
                        long[] hashes = sourceHashes(goldenDraft(preset, relief, size));
                        writer.write(preset.name() + "," + relief.name() + "," + size);
                        for (long hash : hashes) writer.write("," + hash);
                        writer.write("\n");
                    }
                }
            }
        }
    }

    private static void generateGoldenData(File file) throws Exception {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            dos.writeInt(30); // 5 presets * 3 reliefs * 2 sizes

            for (IslandDraft.Preset preset : V1_PRESETS) {
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
