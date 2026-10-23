package com.hack337.omniisle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.platform.TargetPlatform;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the temporary datapack that CreateWorldScreen later copies into the new world. */
public final class WorldPackBuilder {
    public static final String FILE_NAME = "omniisle-generated.zip";
    private static final double COAST_TRANSITION_WIDTH = 192.0;
    private static final double OCEAN_TRANSITION_WIDTH = 400.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record StructureDefinition(IslandDraft.StructureOption option, String setName, String structure, String biomeTag,
                                       Set<BiomePreview.Type> biomes, boolean deepDark) {}
    private record Point(int chunkX, int chunkZ) {}
    private record Sample(double continentalness, BiomePreview.Type biome, boolean deepDark) {}

    private static final List<StructureDefinition> STRUCTURES = List.of(
        structure(IslandDraft.StructureOption.VILLAGE, "villages", "minecraft:village_plains", "village_plains", BiomePreview.Type.PLAINS),
        structure(IslandDraft.StructureOption.OUTPOST, "pillager_outposts", "minecraft:pillager_outpost", "pillager_outpost", BiomePreview.Type.PLAINS),
        structure(IslandDraft.StructureOption.MANSION, "woodland_mansions", "minecraft:mansion", "woodland_mansion", BiomePreview.Type.DARK_FOREST),
        structure(IslandDraft.StructureOption.DESERT_PYRAMID, "desert_pyramids", "minecraft:desert_pyramid", "desert_pyramid", BiomePreview.Type.BADLANDS),
        structure(IslandDraft.StructureOption.IGLOO, "igloos", "minecraft:igloo", "igloo", BiomePreview.Type.SNOWY_PLAINS),
        structure(IslandDraft.StructureOption.JUNGLE_TEMPLE, "jungle_temples", "minecraft:jungle_pyramid", "jungle_temple", BiomePreview.Type.JUNGLE),
        structure(IslandDraft.StructureOption.SWAMP_HUT, "swamp_huts", "minecraft:swamp_hut", "swamp_hut", BiomePreview.Type.SWAMP),
        structure(IslandDraft.StructureOption.TRAIL_RUINS, "trail_ruins", "minecraft:trail_ruins", "trail_ruins", BiomePreview.Type.TAIGA),
        structure(IslandDraft.StructureOption.TRIAL_CHAMBERS, "trial_chambers", "minecraft:trial_chambers", "trial_chambers", BiomePreview.Type.PLAINS),
        new StructureDefinition(IslandDraft.StructureOption.ANCIENT_CITY, "ancient_cities", "minecraft:ancient_city", "ancient_city",
            EnumSet.of(BiomePreview.Type.STONY_PEAKS, BiomePreview.Type.JAGGED_PEAKS), true),
        structure(IslandDraft.StructureOption.STRONGHOLD, "strongholds", "minecraft:stronghold", "stronghold", BiomePreview.Type.PLAINS),
        structure(IslandDraft.StructureOption.RUINED_PORTAL, "ruined_portals", "minecraft:ruined_portal", "ruined_portal_standard", BiomePreview.Type.PLAINS),
        structure(IslandDraft.StructureOption.MINESHAFT, "mineshafts", "minecraft:mineshaft", "mineshaft", BiomePreview.Type.PLAINS),
        structure(IslandDraft.StructureOption.OCEAN_MONUMENT, "ocean_monuments", "minecraft:monument", "ocean_monument", BiomePreview.Type.DEEP_OCEAN),
        structure(IslandDraft.StructureOption.OCEAN_RUINS, "ocean_ruins", "minecraft:ocean_ruin_cold", "ocean_ruin_cold", BiomePreview.Type.COLD_OCEAN),
        structure(IslandDraft.StructureOption.SHIPWRECK, "shipwrecks", "minecraft:shipwreck", "shipwreck", BiomePreview.Type.OCEAN),
        structure(IslandDraft.StructureOption.BURIED_TREASURE, "buried_treasures", "minecraft:buried_treasure", "buried_treasure", BiomePreview.Type.BEACH),
        structure(IslandDraft.StructureOption.ABANDONED_CAMP, "abandoned_camp", "minecraft:abandoned_camp_forest", "abandoned_camp_forest", BiomePreview.Type.FOREST)
    );

    private WorldPackBuilder() {}

    public static void export(Path dataPackDirectory, IslandDraft source) throws IOException {
        IslandDraft state = source.copy();
        if (!state.hasLand()) throw new IOException("The island contains no land");
        Files.createDirectories(dataPackDirectory);
        Path target = dataPackDirectory.resolve(FILE_NAME);
        Path temporary = dataPackDirectory.resolve(FILE_NAME + ".tmp");
        double halfSpan = state.worldSpan() * 0.5;
        double coastWidth = state.relief().coastWidth;
        IslandMask mask = new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan,
            coastWidth, OCEAN_TRANSITION_WIDTH, state.packedMask());

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
            putEntry(zip, "pack.mcmeta", packMetadata());
            putEntry(zip, "data/omniisle/worldgen/density_function/overworld/continents.json",
                createDensityJson(state, halfSpan));
            putEntry(zip, "data/omniisle/worldgen/world_preset/omniisle.json", createPresetJson(state));
            exportStructureSets(zip, state, mask, halfSpan);
        } catch (Throwable throwable) {
            Files.deleteIfExists(temporary);
            if (throwable instanceof IOException io) throw io;
            throw new IOException("Could not build OmniIsle datapack", throwable);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String packMetadata() {
        return "{\n  \"pack\": {\n    \"pack_format\": " + TargetPlatform.PACK_FORMAT
            + ",\n    \"description\": \"OmniIsle generated island\"\n  }\n}\n";
    }

    private static String createDensityJson(IslandDraft state, double halfSpan) {
        JsonObject mask = new JsonObject();
        mask.addProperty("version", 1);
        mask.addProperty("resolution", IslandDraft.RESOLUTION);
        mask.addProperty("width", IslandDraft.RESOLUTION);
        mask.addProperty("height", IslandDraft.RESOLUTION);
        mask.addProperty("min_x", -halfSpan);
        mask.addProperty("max_x", halfSpan);
        mask.addProperty("min_z", -halfSpan);
        mask.addProperty("max_z", halfSpan);
        mask.addProperty("coast_width", state.relief().coastWidth);
        mask.addProperty("ocean_transition_width", OCEAN_TRANSITION_WIDTH);
        mask.addProperty("data", state.packedMask());
        JsonObject config = new JsonObject();
        config.addProperty("generation_version", state.generationVersionNumber());
        config.addProperty("islet_density", state.isletDensity());
        config.add("entries", new JsonArray());
        config.add("mask", mask);
        config.addProperty("biome_seed", state.biomeSeed());
        JsonObject input = new JsonObject();
        input.addProperty("type", "omniisle:island_continents");
        input.add("config", config);
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:cache");
        root.add("input", input);
        return GSON.toJson(root) + "\n";
    }

    private static String createPresetJson(IslandDraft state) throws IOException {
        try (var input = WorldPackBuilder.class.getResourceAsStream(
            "/data/omniisle/worldgen/world_preset/omniisle.json")) {
            if (input == null) throw new IOException("Missing OmniIsle world preset template");
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject biomeSource = root.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
                    .getAsJsonObject("generator").getAsJsonObject("biome_source");
                biomeSource.addProperty("biome_seed", state.biomeSeed());
                addEnabledBiomes(biomeSource, "enabled_land_biomes", state, BiomePreview.Category.LAND);
                addEnabledBiomes(biomeSource, "enabled_shore_biomes", state, BiomePreview.Category.SHORE);
                addEnabledBiomes(biomeSource, "enabled_ocean_biomes", state, BiomePreview.Category.OCEAN);
                addEnabledBiomes(biomeSource, "enabled_river_biomes", state, BiomePreview.Category.RIVER);
                addEnabledBiomes(biomeSource, "enabled_cave_biomes", state, BiomePreview.Category.CAVE);
                return GSON.toJson(root) + "\n";
            }
        }
    }

    private static void addEnabledBiomes(JsonObject biomeSource, String key, IslandDraft state,
                                         BiomePreview.Category category) {
        JsonArray enabled = new JsonArray();
        state.enabledBiomes(category).forEach(type -> enabled.add(type.biomeId()));
        biomeSource.add(key, enabled);
    }

    private static void exportStructureSets(ZipOutputStream zip, IslandDraft state,
                                           IslandMask mask, double halfSpan) throws IOException {
        int minChunk = (int)Math.floor(-halfSpan / 16.0);
        int maxChunk = (int)Math.ceil(halfSpan / 16.0);
        int width = maxChunk - minChunk + 1;
        Sample[] samples = new Sample[width * width];
        for (int cz = minChunk; cz <= maxChunk; cz++) {
            for (int cx = minChunk; cx <= maxChunk; cx++) {
                int x = cx * 16 + 8;
                int z = cz * 16 + 8;
                double continentalness = mask.sampleTerrainValue(x, z);
                BiomePreview.Type biome = state.selectBiome(
                    BiomePreview.surface(continentalness, x, z, state.biomeSeed()), x, z);
                samples[(cz - minChunk) * width + cx - minChunk] =
                    new Sample(continentalness, biome, BiomePreview.isDeepDark(x, z, state.biomeSeed()));
            }
        }

        List<Point> used = new ArrayList<>();
        for (StructureDefinition definition : STRUCTURES) {
            if (!TargetPlatform.HAS_TRIAL_CHAMBERS && definition.option == IslandDraft.StructureOption.TRIAL_CHAMBERS) continue;
            if (!TargetPlatform.HAS_ABANDONED_CAMP && definition.option == IslandDraft.StructureOption.ABANDONED_CAMP) continue;
            boolean enabled = state.isStructureEnabled(definition.option);
            Point point = enabled ? locateStructure(definition, state, samples, minChunk, maxChunk, width, used) : new Point(0, 0);
            if (enabled) used.add(point);
            String json = "{\n  \"structures\": [{\n    \"structure\": \"" + definition.structure +
                "\",\n    \"weight\": 1\n  }],\n  \"placement\": {\n    \"type\": \"omniisle:fixed\",\n" +
                "    \"chunk_x\": " + point.chunkX + ",\n    \"chunk_z\": " + point.chunkZ +
                ",\n    \"enabled\": " + enabled + "\n  }\n}\n";
            putEntry(zip, "data/minecraft/worldgen/structure_set/" + definition.setName + ".json", json);
            if (enabled) {
                Sample pointSample = samples[(point.chunkZ - minChunk) * width + point.chunkX - minChunk];
                putEntry(zip, "data/minecraft/tags/worldgen/biome/has_structure/" + definition.biomeTag + ".json",
                    "{\n  \"replace\": false,\n  \"values\": [\n    \"" + pointSample.biome.biomeId() + "\"\n  ]\n}\n");
            }
        }
    }

    private static Point locateStructure(StructureDefinition definition, IslandDraft state, Sample[] samples,
                                     int minChunk, int maxChunk, int width, List<Point> used) throws IOException {
        Set<BiomePreview.Type> accepted = EnumSet.noneOf(BiomePreview.Type.class);
        definition.biomes.forEach(type -> accepted.addAll(state.compatibleChoices(type)));
        Point best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int cz = minChunk; cz <= maxChunk; cz++) {
            for (int cx = minChunk; cx <= maxChunk; cx++) {
                Sample sample = samples[(cz - minChunk) * width + cx - minChunk];
                if (!accepted.contains(sample.biome)) continue;
                if (definition.deepDark && !sample.deepDark) continue;
                if ((definition.setName.equals("trial_chambers") || definition.setName.equals("mineshafts")) && sample.deepDark) continue;
                boolean tooClose = false;
                for (Point other : used) {
                    int dx = cx - other.chunkX;
                    int dz = cz - other.chunkZ;
                    if (dx * dx + dz * dz < 64) { tooClose = true; break; }
                }
                if (tooClose) continue;
                boolean oceanOrShore = sample.biome.name().contains("OCEAN") || sample.biome == BiomePreview.Type.BEACH;
                double score = oceanOrShore ? -Math.abs(sample.continentalness + 0.40) : sample.continentalness;
                score -= (Math.abs(cx) + Math.abs(cz)) * 0.00001;
                if (score > bestScore) { bestScore = score; best = new Point(cx, cz); }
            }
        }
        if (best == null) {
            Set<BiomePreview.Type> fallback = fallbackBiomes(definition.biomes, state);
            if (!fallback.equals(accepted) || definition.deepDark) {
                return locateStructure(new StructureDefinition(definition.option, definition.setName, definition.structure,
                    definition.biomeTag, fallback, false), state, samples, minChunk, maxChunk, width, used);
            }
            throw new IOException("No suitable biome for " + definition.setName);
        }
        return best;
    }

    private static Set<BiomePreview.Type> fallbackBiomes(Set<BiomePreview.Type> requested,
                                                                IslandDraft state) {
        EnumSet<BiomePreview.Type> result = EnumSet.noneOf(BiomePreview.Type.class);
        BiomePreview.Category category = requested.iterator().next().category;
        result.addAll(state.enabledBiomes(category));
        return result;
    }

    private static StructureDefinition structure(IslandDraft.StructureOption option, String name, String structure, String tag, BiomePreview.Type biome) {
        return new StructureDefinition(option, name, structure, tag, EnumSet.of(biome), false);
    }

    private static void putEntry(ZipOutputStream zip, String name, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
