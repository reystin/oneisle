package com.hack337.omniisle.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.WorldPackBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.0.1 villages: five vanilla variants scoped to biome families, 54/12 spread
 * (~40% of the old 34/8 density), and untouched v1/v2 fixed placements.
 */
public class Phase14VillagePackTest {
    private static final Map<String, BiomePreview.Type> BY_ID = new HashMap<>();
    private static final Set<Integer> PLAINS_FAMILIES = Set.of(2, 3, 4, 5, 6);
    private static final Set<Integer> DESERT_FAMILIES = Set.of(12, 13);
    private static final Set<Integer> SAVANNA_FAMILIES = Set.of(11);
    private static final Set<Integer> SNOWY_FAMILIES = Set.of(0);
    private static final Set<Integer> TAIGA_FAMILIES = Set.of(1);

    static {
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            BY_ID.put(type.biomeId(), type);
        }
    }

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("14V.1 v3 emits five weighted village variants at 54/12 with family tags")
    void v3VillageVariants(@TempDir Path tempDir) throws IOException {
        IslandDraft draft = draft(IslandDraft.Preset.ROUND, 4000, 202L, IslandDraft.GenerationVersion.V3);
        Path export = Files.createDirectories(tempDir.resolve("export"));
        WorldPackBuilder.export(export, draft);

        JsonObject placement = placement(export, "villages");
        assertEquals("minecraft:random_spread", placement.get("type").getAsString());
        assertEquals(54, placement.get("spacing").getAsInt());
        assertEquals(12, placement.get("separation").getAsInt());
        assertEquals(10387312, placement.get("salt").getAsInt());

        JsonArray structures = parse(export, "data/minecraft/worldgen/structure_set/villages.json")
            .getAsJsonArray("structures");
        assertEquals(5, structures.size(), "v3 must list all five vanilla village types");
        Set<String> ids = new HashSet<>();
        for (JsonElement element : structures) {
            JsonObject entry = element.getAsJsonObject();
            ids.add(entry.get("structure").getAsString());
            assertEquals(1, entry.get("weight").getAsInt());
        }
        assertEquals(Set.of("minecraft:village_plains", "minecraft:village_desert",
            "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga"), ids);

        assertVariantTag(export, "village_plains", PLAINS_FAMILIES, Set.of(BiomePreview.Type.GROVE));
        assertVariantTag(export, "village_desert", DESERT_FAMILIES, Set.of());
        assertVariantTag(export, "village_savanna", SAVANNA_FAMILIES, Set.of());
        assertVariantTag(export, "village_snowy", SNOWY_FAMILIES, Set.of(BiomePreview.Type.ICE_SPIKES));
        assertVariantTag(export, "village_taiga", TAIGA_FAMILIES, Set.of());

        assertTrue(tagIds(export, "village_plains").contains("minecraft:plains"));
        assertTrue(tagIds(export, "village_desert").contains("minecraft:desert"));
        assertTrue(tagIds(export, "village_snowy").contains("minecraft:snowy_plains"));
        assertTrue(tagIds(export, "village_taiga").contains("minecraft:taiga"));
        assertTrue(tagIds(export, "village_savanna").contains("minecraft:savanna"));
    }

    @Test
    @DisplayName("14V.2 v3 village spread still scales with island size and density")
    void villageSpreadScaling(@TempDir Path tempDir) throws IOException {
        IslandDraft small = draft(IslandDraft.Preset.ROUND, 2500, 202L, IslandDraft.GenerationVersion.V3);
        WorldPackBuilder.export(Files.createDirectories(tempDir.resolve("small")), small);
        JsonObject smallPlacement = placement(tempDir.resolve("small"), "villages");
        assertEquals(34, smallPlacement.get("spacing").getAsInt());
        assertEquals(8, smallPlacement.get("separation").getAsInt());

        IslandDraft dense = draft(IslandDraft.Preset.ROUND, 4000, 202L, IslandDraft.GenerationVersion.V3);
        dense.setStructureDensity(2.0);
        WorldPackBuilder.export(Files.createDirectories(tempDir.resolve("dense")), dense);
        JsonObject densePlacement = placement(tempDir.resolve("dense"), "villages");
        assertEquals(38, densePlacement.get("spacing").getAsInt());
        assertEquals(8, densePlacement.get("separation").getAsInt());
    }

    @Test
    @DisplayName("14V.3 empty variants are omitted; no village family means a disabled set")
    void emptyVariantsAreOmitted(@TempDir Path tempDir) throws IOException {
        IslandDraft desertOnly = draft(IslandDraft.Preset.ROUND, 4000, 202L, IslandDraft.GenerationVersion.V3);
        desertOnly.deselectAllBiomes(BiomePreview.Category.LAND);
        desertOnly.toggleBiome(BiomePreview.Type.DESERT);
        desertOnly.toggleBiome(BiomePreview.Type.BADLANDS);
        Path desertExport = Files.createDirectories(tempDir.resolve("desert"));
        WorldPackBuilder.export(desertExport, desertOnly);
        JsonArray structures = parse(desertExport,
            "data/minecraft/worldgen/structure_set/villages.json").getAsJsonArray("structures");
        assertEquals(1, structures.size(), "only the desert variant survives");
        assertEquals("minecraft:village_desert",
            structures.get(0).getAsJsonObject().get("structure").getAsString());
        assertNotNull(tag(desertExport, "village_desert"));
        assertNull(tag(desertExport, "village_plains"), "empty plains variant must not emit a tag");
        assertNull(tag(desertExport, "village_snowy"), "empty snowy variant must not emit a tag");

        IslandDraft mushroomOnly = draft(IslandDraft.Preset.ROUND, 4000, 202L, IslandDraft.GenerationVersion.V3);
        mushroomOnly.deselectAllBiomes(BiomePreview.Category.LAND);
        mushroomOnly.toggleBiome(BiomePreview.Type.MUSHROOM_FIELDS);
        Path mushroomExport = Files.createDirectories(tempDir.resolve("mushroom"));
        WorldPackBuilder.export(mushroomExport, mushroomOnly);
        JsonObject placement = placement(mushroomExport, "villages");
        assertEquals("omniisle:fixed", placement.get("type").getAsString(),
            "no matching village family must disable the set");
        assertFalse(placement.get("enabled").getAsBoolean());
        assertNull(tag(mushroomExport, "village_plains"));
        assertNull(tag(mushroomExport, "village_desert"));
    }

    @Test
    @DisplayName("14V.4 v2 keeps the single fixed plains village")
    void v2KeepsFixedVillage(@TempDir Path tempDir) throws IOException {
        IslandDraft v2 = draft(IslandDraft.Preset.ROUND, 4000, 202L, IslandDraft.GenerationVersion.V2);
        Path export = Files.createDirectories(tempDir.resolve("v2"));
        WorldPackBuilder.export(export, v2);
        JsonObject placement = placement(export, "villages");
        assertEquals("omniisle:fixed", placement.get("type").getAsString(),
            "v2 villages stay fixed placements, never random spread");
        JsonArray structures = parse(export,
            "data/minecraft/worldgen/structure_set/villages.json").getAsJsonArray("structures");
        assertEquals(1, structures.size());
        assertEquals("minecraft:village_plains",
            structures.get(0).getAsJsonObject().get("structure").getAsString());
        assertNull(tag(export, "village_desert"), "v2 must not add the v3 village tags");
        assertNull(tag(export, "village_savanna"));
        assertNull(tag(export, "village_snowy"));
        assertNull(tag(export, "village_taiga"));
    }

    private static void assertVariantTag(Path export, String tag, Set<Integer> families,
                                         Set<BiomePreview.Type> excluded) throws IOException {
        JsonArray values = parse(export,
            "data/minecraft/tags/worldgen/biome/has_structure/" + tag + ".json")
            .getAsJsonArray("values");
        assertTrue(values.size() > 0, tag + " must list at least one biome");
        for (JsonElement element : values) {
            String id = element.getAsString();
            BiomePreview.Type type = BY_ID.get(id);
            assertNotNull(type, tag + " lists an unknown biome " + id);
            assertTrue(type.isLand(), tag + " lists a non-land biome " + id);
            assertTrue(families.contains(type.family),
                tag + " lists " + id + " from family " + type.family);
            assertFalse(excluded.contains(type), tag + " lists excluded " + id);
        }
    }

    private static JsonArray tag(Path export, String tag) throws IOException {
        JsonObject json = parse(export,
            "data/minecraft/tags/worldgen/biome/has_structure/" + tag + ".json");
        return json == null ? null : json.getAsJsonArray("values");
    }

    private static Set<String> tagIds(Path export, String tag) throws IOException {
        JsonArray values = tag(export, tag);
        Set<String> ids = new HashSet<>();
        if (values != null) {
            for (JsonElement element : values) ids.add(element.getAsString());
        }
        return ids;
    }

    private static JsonObject placement(Path export, String setName) throws IOException {
        return parse(export, "data/minecraft/worldgen/structure_set/" + setName + ".json")
            .getAsJsonObject("placement");
    }

    private static JsonObject parse(Path export, String entryName) throws IOException {
        String json = read(export, entryName);
        return json == null ? null : JsonParser.parseString(json).getAsJsonObject();
    }

    private static String read(Path export, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(export.resolve(WorldPackBuilder.FILE_NAME).toFile())) {
            var entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (var in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static IslandDraft draft(IslandDraft.Preset preset, int size, long seed,
                                     IslandDraft.GenerationVersion version) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setGenerationVersion(version);
        draft.setIsletDensity(0.25);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }
}
