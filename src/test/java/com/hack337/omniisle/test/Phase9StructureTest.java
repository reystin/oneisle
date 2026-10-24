package com.hack337.omniisle.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ModernTerrainV2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class Phase9StructureTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/structures");
    private static final double WO = 400.0;
    private static final IslandDraft.Preset[] PRESETS = {
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
    };
    private static final int[] SIZES = {2500, 4000, 8000};
    private static final long SEED = 101L;

    @Test
    void v3StrongholdsAreThreeReachableLandStarts() throws IOException {
        StringBuilder csv = new StringBuilder(
            "config,goal_distance,spawn_x,spawn_z,x1,z1,d1,x2,z2,d2,x3,z3,d3,pair12,pair13,pair23,land_depth1,land_depth2,land_depth3\n");
        int configs = 0;
        for (IslandDraft.Preset preset : PRESETS) {
            for (int size : SIZES) {
                IslandDraft draft = draft(preset, size, SEED, 0.25, IslandDraft.GenerationVersion.V3);
                Path tmp = Files.createTempDirectory("omniisle-stronghold-");
                try {
                    WorldPackBuilder.export(tmp, draft);
                    JsonObject placement = placement(tmp, "strongholds");
                    assertEquals("omniisle:fixed_multi", placement.get("type").getAsString());
                    assertTrue(placement.get("enabled").getAsBoolean());
                    var chunks = placement.getAsJsonArray("chunks");
                    assertEquals(3, chunks.size(), "v3 must place exactly three strongholds");

                    IslandTerrain terrain = terrain(draft, 0.25);
                    ModernTerrainV2 surface = (ModernTerrainV2) terrain;
                    double spawnX = draft.worldSpawnX();
                    double spawnZ = draft.worldSpawnZ();
                    double minDistance = size >= 4000 ? 300.0 : 150.0;
                    double[] xs = new double[3];
                    double[] zs = new double[3];
                    double[] distances = new double[3];
                    double[] landDepth = new double[3];
                    for (int i = 0; i < 3; i++) {
                        JsonObject chunk = chunks.get(i).getAsJsonObject();
                        int chunkX = chunk.get("x").getAsInt();
                        int chunkZ = chunk.get("z").getAsInt();
                        xs[i] = chunkX * 16 + 8;
                        zs[i] = chunkZ * 16 + 8;
                        distances[i] = Math.hypot(xs[i] - spawnX, zs[i] - spawnZ);
                        assertTrue(distances[i] >= minDistance,
                            "stronghold " + i + " only " + distances[i] + " blocks from spawn");
                        assertTrue(distances[i] <= 100 * 16 * Math.sqrt(2.0),
                            "stronghold " + i + " is outside the 100-chunk locate scan: " + distances[i]);
                        landDepth[i] = surface.computeSignedDistance(xs[i], zs[i], true);
                        assertTrue(landDepth[i] > 0.0,
                            "stronghold " + i + " is not on the main island: depth " + landDepth[i]);
                        assertTrue(surface.signedDistance(xs[i], zs[i]) > 0.0,
                            "stronghold " + i + " is not valid terrain");
                    }
                    for (int a = 0; a < 3; a++) {
                        for (int b = a + 1; b < 3; b++) {
                            double pair = Math.hypot(xs[a] - xs[b], zs[a] - zs[b]);
                            assertTrue(pair >= 250.0, "strongholds " + a + "/" + b + " only " + pair + " apart");
                        }
                    }
                    String tag = read(tmp, "data/minecraft/tags/worldgen/biome/has_structure/stronghold.json");
                    assertNotNull(tag);
                    assertTrue(tag.contains("values"), "stronghold biome tag is empty: " + tag);

                    String key = preset.name() + "_" + size + "_s" + SEED;
                    csv.append(key).append(',').append(String.format(Locale.ROOT, "%.0f", minDistance)).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", spawnX)).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", spawnZ)).append(',')
                        .append(String.format(Locale.ROOT, "%.0f,%.0f,%.1f", xs[0], zs[0], distances[0])).append(',')
                        .append(String.format(Locale.ROOT, "%.0f,%.0f,%.1f", xs[1], zs[1], distances[1])).append(',')
                        .append(String.format(Locale.ROOT, "%.0f,%.0f,%.1f", xs[2], zs[2], distances[2])).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", Math.hypot(xs[0] - xs[1], zs[0] - zs[1]))).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", Math.hypot(xs[0] - xs[2], zs[0] - zs[2]))).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", Math.hypot(xs[1] - xs[2], zs[1] - zs[2]))).append(',')
                        .append(String.format(Locale.ROOT, "%.1f,%.1f,%.1f", landDepth[0], landDepth[1], landDepth[2]))
                        .append('\n');
                    configs++;
                } finally {
                    deleteTree(tmp);
                }
            }
        }
        assertEquals(15, configs);
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("stronghold_placement.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v3VillageAndTrialChambersUseScaledRandomSpread() throws IOException {
        StringBuilder csv = new StringBuilder("config,structure,island_size,density,spacing,separation,expected_spacing,expected_separation\n");
        int rows = 0;
        for (int size : SIZES) {
            for (double density : new double[]{0.5, 1.0, 2.0}) {
                IslandDraft draft = draft(IslandDraft.Preset.ROUND, size, 202L, 0.25, IslandDraft.GenerationVersion.V3);
                draft.setStructureDensity(density);
                Path tmp = Files.createTempDirectory("omniisle-spread-");
                try {
                    WorldPackBuilder.export(tmp, draft);
                    for (String set : List.of("villages", "trial_chambers")) {
                        JsonObject placement = placement(tmp, set);
                        assertEquals("minecraft:random_spread", placement.get("type").getAsString(),
                            set + " must use the real random-spread machinery");
                        int spacing = placement.get("spacing").getAsInt();
                        int separation = placement.get("separation").getAsInt();
                        int[] expected = expectedSpread(set.equals("trial_chambers"), size, density);
                        assertEquals(expected[0], spacing, set + " spacing");
                        assertEquals(expected[1], separation, set + " separation");
                        assertTrue(separation < spacing, set + " must keep separation < spacing");
                        String tag = read(tmp, "data/minecraft/tags/worldgen/biome/has_structure/"
                            + (set.equals("villages") ? "village_plains" : "trial_chambers") + ".json");
                        assertNotNull(tag);
                        assertTrue(tag.contains("minecraft:"), set + " biome tag must list island biomes: " + tag);
                        csv.append("ROUND_").append(size).append("_d").append(density).append(',')
                            .append(set).append(',').append(size).append(',')
                            .append(String.format(Locale.ROOT, "%.1f", density)).append(',')
                            .append(spacing).append(',').append(separation).append(',')
                            .append(expected[0]).append(',').append(expected[1]).append('\n');
                        rows++;
                    }
                } finally {
                    deleteTree(tmp);
                }
            }
        }
        assertEquals(18, rows);
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("structure_scaling.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v3RelaxesOnlyThePyramidSeaLevelGate() throws IOException {
        IslandDraft v3 = draft(IslandDraft.Preset.ROUND, 4000, 404L, 0.25, IslandDraft.GenerationVersion.V3);
        Path tmp = Files.createTempDirectory("omniisle-relaxed-");
        try {
            WorldPackBuilder.export(tmp, v3);
            String desert = read(tmp, "data/minecraft/worldgen/structure/desert_pyramid.json");
            assertNotNull(desert, "v3 must override the desert pyramid structure type");
            assertTrue(desert.contains("omniisle:relaxed_desert_pyramid"), desert);
            assertTrue(desert.contains("#minecraft:has_structure/desert_pyramid"), desert);
            String jungle = read(tmp, "data/minecraft/worldgen/structure/jungle_pyramid.json");
            assertNotNull(jungle, "v3 must override the jungle pyramid structure type");
            assertTrue(jungle.contains("omniisle:relaxed_jungle_temple"), jungle);
            assertTrue(jungle.contains("#minecraft:has_structure/jungle_temple"), jungle);
        } finally {
            deleteTree(tmp);
        }

        IslandDraft v2 = draft(IslandDraft.Preset.ROUND, 4000, 404L, 0.25, IslandDraft.GenerationVersion.V2);
        Path legacy = Files.createTempDirectory("omniisle-legacy-pyramid-");
        try {
            WorldPackBuilder.export(legacy, v2);
            assertNull(read(legacy, "data/minecraft/worldgen/structure/desert_pyramid.json"),
                "v2 packs must not override vanilla pyramid structures");
            assertNull(read(legacy, "data/minecraft/worldgen/structure/jungle_pyramid.json"),
                "v2 packs must not override vanilla pyramid structures");
        } finally {
            deleteTree(legacy);
        }
    }

    @Test
    void legacyV2KeepsConcentricStrongholdsAndFixedVillages() throws IOException {
        IslandDraft draft = draft(IslandDraft.Preset.ROUND, 4000, 303L, 0.25, IslandDraft.GenerationVersion.V2);
        Path tmp = Files.createTempDirectory("omniisle-legacy-");
        try {
            WorldPackBuilder.export(tmp, draft);
            JsonObject strongholds = placement(tmp, "strongholds");
            assertEquals("minecraft:concentric_rings", strongholds.get("type").getAsString());
            JsonObject villages = placement(tmp, "villages");
            assertEquals("omniisle:fixed", villages.get("type").getAsString());
        } finally {
            deleteTree(tmp);
        }
    }

    @Test
    void legacyV1AndV2KeepSingleBiomeFixedStructureTags() throws IOException {
        for (IslandDraft.GenerationVersion version : List.of(
                IslandDraft.GenerationVersion.V1, IslandDraft.GenerationVersion.V2)) {
            IslandDraft draft = draft(IslandDraft.Preset.ROUND, 4000, 303L, 0.25, version);
            Path tmp = Files.createTempDirectory("omniisle-legacy-tags-");
            try {
                WorldPackBuilder.export(tmp, draft);
                var pyramid = fixedTag(tmp, "desert_pyramid");
                assertEquals(1, pyramid.size(),
                    version + " must keep the 0.6.0 single-preview-biome pyramid tag, got " + pyramid);
                var ancientCity = fixedTag(tmp, "ancient_city");
                assertEquals(2, ancientCity.size(),
                    version + " ancient city tag must stay deep_dark + one preview biome, got " + ancientCity);
                assertEquals("minecraft:deep_dark", ancientCity.get(0).getAsString());
            } finally {
                deleteTree(tmp);
            }
        }
    }

    private static com.google.gson.JsonArray fixedTag(Path dir, String tag) throws IOException {
        String json = read(dir, "data/minecraft/tags/worldgen/biome/has_structure/" + tag + ".json");
        assertNotNull(json, "missing has_structure/" + tag + " tag");
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("values");
    }

    private static int[] expectedSpread(boolean trial, int size, double density) {
        int baseSpacing = 34;
        int baseSeparation = trial ? 12 : 8;
        double islandScale = Math.min(1.0, size / 4000.0);
        double densityScale = 1.0 / Math.sqrt(density);
        int spacing = Math.max(2, (int) Math.round(baseSpacing * islandScale * densityScale));
        int separation = Math.max(1, (int) Math.round(baseSeparation * islandScale * densityScale));
        return new int[]{spacing, Math.min(separation, spacing - 1)};
    }

    private static JsonObject placement(Path dir, String setName) throws IOException {
        String json = read(dir, "data/minecraft/worldgen/structure_set/" + setName + ".json");
        assertNotNull(json, "missing structure set " + setName);
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("placement");
    }

    private static String read(Path dir, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(dir.resolve(WorldPackBuilder.FILE_NAME).toFile())) {
            var entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (var in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static IslandDraft draft(IslandDraft.Preset preset, int size, long seed,
                                     double islets, IslandDraft.GenerationVersion version) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setGenerationVersion(version);
        draft.setIsletDensity(islets);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    private static IslandTerrain terrain(IslandDraft draft, double islets) {
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth, WO, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(3, islets, draft.riverDensity(),
            draft.isWorldBorderEnabled(), draft.worldBorderMargin(),
            Optional.empty(), Optional.empty(), List.of(), Optional.of(mask), draft.biomeSeed(),
            Optional.of(draft.shoreSettings()));
        return IslandTerrain.create(config);
    }

    private static void deleteTree(Path tmp) throws IOException {
        try (var walk = Files.walk(tmp)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
