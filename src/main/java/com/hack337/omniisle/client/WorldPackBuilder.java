package com.hack337.omniisle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hack337.omniisle.continent.BiomeShare;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.platform.TargetPlatform;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/** Builds the temporary datapack that CreateWorldScreen later copies into the new world. */
public final class WorldPackBuilder {
    public static final String FILE_NAME = "omniisle-generated.zip";
    private static final String PRESET_ENTRY = "data/omniisle/worldgen/world_preset/omniisle.json";
    private static final double COAST_TRANSITION_WIDTH = 192.0;
    private static final double OCEAN_TRANSITION_WIDTH = 400.0;
    /** The v3 base reaches -0.62 at 0.4*Wo, the shallow edge of the monument band. */
    private static final double DEEP_MONUMENT_MAX_CONTINENTALNESS = -0.62;
    private static final int VILLAGE_SPACING = 34;
    private static final int VILLAGE_SEPARATION = 8;
    private static final int TRIAL_CHAMBERS_SPACING = 34;
    private static final int TRIAL_CHAMBERS_SEPARATION = 12;
    private static final int VILLAGE_SALT = 10387312;
    private static final int TRIAL_CHAMBERS_SALT = 94251327;
    private static final double STRONGHOLD_MIN_PAIR_DISTANCE = 250.0;
    private static final double STRONGHOLD_MAX_REACH = 1500.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record StructureDefinition(IslandDraft.StructureOption option, String setName, String structure, String biomeTag,
                                       Set<BiomePreview.Type> biomes, boolean deepDark) {}
    private record Point(int chunkX, int chunkZ) {}
    private record Sample(double continentalness, BiomePreview.Type biome, boolean deepDark) {}

    private static final List<StructureDefinition> STRUCTURES = List.of(
        structure(IslandDraft.StructureOption.VILLAGE, "villages", "minecraft:village_plains", "village_plains", BiomePreview.Type.PLAINS),
        structure(IslandDraft.StructureOption.OUTPOST, "pillager_outposts", "minecraft:pillager_outpost", "pillager_outpost", BiomePreview.Type.PLAINS),
        structure(IslandDraft.StructureOption.MANSION, "woodland_mansions", "minecraft:mansion", "woodland_mansion", BiomePreview.Type.DARK_FOREST),
        structure(IslandDraft.StructureOption.DESERT_PYRAMID, "desert_pyramids", "minecraft:desert_pyramid", "desert_pyramid", BiomePreview.Type.DESERT),
        structure(IslandDraft.StructureOption.IGLOO, "igloos", "minecraft:igloo", "igloo", BiomePreview.Type.SNOWY_PLAINS),
        structure(IslandDraft.StructureOption.JUNGLE_TEMPLE, "jungle_temples", "minecraft:jungle_pyramid", "jungle_temple", BiomePreview.Type.JUNGLE),
        structure(IslandDraft.StructureOption.SWAMP_HUT, "swamp_huts", "minecraft:swamp_hut", "swamp_hut", BiomePreview.Type.SWAMP),
        structure(IslandDraft.StructureOption.TRAIL_RUINS, "trail_ruins", "minecraft:trail_ruins", "trail_ruins", BiomePreview.Type.TAIGA),
        structure(IslandDraft.StructureOption.TRIAL_CHAMBERS, "trial_chambers", "minecraft:trial_chambers", "trial_chambers", BiomePreview.Type.PLAINS),
        new StructureDefinition(IslandDraft.StructureOption.ANCIENT_CITY, "ancient_cities", "minecraft:ancient_city", "ancient_city",
            EnumSet.of(BiomePreview.Type.PLAINS, BiomePreview.Type.FOREST, BiomePreview.Type.TAIGA, BiomePreview.Type.STONY_PEAKS, BiomePreview.Type.JAGGED_PEAKS), true),
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
        if (state.hasNonNeutralWeights()) {
            state.applyCalibration(BiomeShareCalibrator.calibrate(state));
        }
        Files.createDirectories(dataPackDirectory);
        Path target = dataPackDirectory.resolve(FILE_NAME);
        Path temporary = dataPackDirectory.resolve(FILE_NAME + ".tmp");
        double halfSpan = state.worldSpan() * 0.5;
        double coastWidth = state.relief().coastWidth;
        int res = state.resolution();
        IslandMask mask = new IslandMask(res, res,
            -halfSpan, halfSpan, -halfSpan, halfSpan,
            coastWidth, OCEAN_TRANSITION_WIDTH, state.packedMask());

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
            putEntry(zip, "pack.mcmeta", packMetadata());
            putEntry(zip, "data/omniisle/worldgen/density_function/overworld/continents.json",
                createDensityJson(state, halfSpan));
            if (state.generationVersionNumber() >= 3) {
                putEntry(zip, "data/omniisle/worldgen/density_function/overworld/final_density.json",
                    createSpawnPlateauJson(state));
                putEntry(zip, "data/minecraft/worldgen/structure/desert_pyramid.json",
                    relaxedPyramidJson("omniisle:relaxed_desert_pyramid", "desert_pyramid"));
                putEntry(zip, "data/minecraft/worldgen/structure/jungle_pyramid.json",
                    relaxedPyramidJson("omniisle:relaxed_jungle_temple", "jungle_temple"));
            }
            putEntry(zip, PRESET_ENTRY, createPresetJson(state));
            exportStructureSets(zip, state, mask, halfSpan);
        } catch (Throwable throwable) {
            Files.deleteIfExists(temporary);
            if (throwable instanceof IOException io) throw io;
            throw new IOException("Could not build OmniIsle datapack", throwable);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Preset preflight failure that the editor reports as a status message. */
    public static final class PresetValidationException extends IOException {
        public PresetValidationException(String message) { super(message); }
        public PresetValidationException(String message, Throwable cause) { super(message, cause); }
    }

    /**
     * Decodes the freshly exported preset with the same worldgen registries the create-world
     * screen will use. A failure here becomes an editor status message instead of a crash on
     * world entry.
     */
    public static void validatePreset(Path dataPackDirectory, HolderLookup.Provider registries) throws IOException {
        Path target = dataPackDirectory.resolve(FILE_NAME);
        JsonElement preset;
        try (ZipFile zip = new ZipFile(target.toFile())) {
            ZipEntry entry = zip.getEntry(PRESET_ENTRY);
            if (entry == null) throw new PresetValidationException("Generated datapack is missing the world preset");
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                preset = JsonParser.parseReader(reader);
            }
        } catch (IOException exception) {
            if (exception instanceof PresetValidationException validation) throw validation;
            throw new PresetValidationException("Could not read the generated datapack: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new PresetValidationException("Generated world preset is not valid JSON", exception);
        }
        try {
            WorldPreset decoded = WorldPreset.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, registries), preset)
                .getOrThrow(message -> new PresetValidationException("Generated world preset is invalid: " + message));
            if (decoded.overworld().isEmpty()) {
                throw new PresetValidationException("Generated world preset has no overworld");
            }
        } catch (IOException exception) {
            if (exception instanceof PresetValidationException validation) throw validation;
            throw new PresetValidationException("Generated world preset could not be decoded: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new PresetValidationException("Generated world preset could not be decoded", exception);
        }
    }

    private static String packMetadata() {
        // 26.3 requires min_format/max_format for pack formats above 81; a bare
        // pack_format is reported as incompatible even though it still loads.
        return "{\n  \"pack\": {\n    \"min_format\": " + TargetPlatform.PACK_FORMAT
            + ",\n    \"max_format\": " + TargetPlatform.PACK_FORMAT
            + ",\n    \"description\": \"OmniIsle generated island\"\n  }\n}\n";
    }

    private static String createDensityJson(IslandDraft state, double halfSpan) {
        JsonObject mask = new JsonObject();
        mask.addProperty("version", 1);
        mask.addProperty("resolution", state.resolution());
        mask.addProperty("width", state.resolution());
        mask.addProperty("height", state.resolution());
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
        config.addProperty("river_density", state.riverDensity());
        config.addProperty("world_border", state.isWorldBorderEnabled());
        config.addProperty("world_border_margin", state.worldBorderMargin());
        if (state.hasCustomHeight()) {
            JsonObject heightObj = new JsonObject();
            heightObj.addProperty("bits_per_pixel", 2);
            heightObj.addProperty("data", state.packedHeight());
            config.add("height_layer", heightObj);
        }
        int[] sp = state.effectiveSpawnPx();
        JsonObject spawnObj = new JsonObject();
        spawnObj.addProperty("x", (int) Math.round(state.worldX(sp[0])));
        spawnObj.addProperty("z", (int) Math.round(state.worldZ(sp[1])));
        config.add("spawn", spawnObj);
        config.add("entries", new JsonArray());
        config.add("mask", mask);
        config.addProperty("biome_seed", state.biomeSeed());
        if (state.generationVersionNumber() >= 3) {
            config.add("shore", shoreJson(state));
        }
        JsonObject input = new JsonObject();
        input.addProperty("type", "omniisle:island_continents");
        input.add("config", config);
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:cache");
        root.add("input", input);
        return GSON.toJson(root) + "\n";
    }

    private static String createSpawnPlateauJson(IslandDraft state) {
        int[] sp = state.effectiveSpawnPx();
        JsonObject plateau = new JsonObject();
        plateau.addProperty("type", "omniisle:spawn_plateau");
        plateau.addProperty("input", "omniisle:overworld/final_density_base");
        plateau.addProperty("spawn_x", (int) Math.round(state.worldX(sp[0])));
        plateau.addProperty("spawn_z", (int) Math.round(state.worldZ(sp[1])));
        plateau.addProperty("radius", 80.0);
        plateau.addProperty("top_y", 78.0);
        return GSON.toJson(plateau) + "\n";
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
                biomeSource.addProperty("climate_scale", state.climateScale());
                biomeSource.addProperty("generation_version", state.generationVersionNumber());
                biomeSource.addProperty("gradient_mode", state.gradientMode().name().toLowerCase(java.util.Locale.ROOT));
                biomeSource.addProperty("gradient_strength", state.gradientStrength());
                biomeSource.addProperty("biome_theme", state.theme().name().toLowerCase(java.util.Locale.ROOT));
                if (state.generationVersionNumber() >= 3) {
                    biomeSource.add("shore", shoreJson(state));
                }
                addEnabledBiomes(biomeSource, "enabled_land_biomes", state, BiomePreview.Category.LAND);
                addEnabledBiomes(biomeSource, "enabled_shore_biomes", state, BiomePreview.Category.SHORE);
                addEnabledBiomes(biomeSource, "enabled_ocean_biomes", state, BiomePreview.Category.OCEAN);
                addEnabledBiomes(biomeSource, "enabled_river_biomes", state, BiomePreview.Category.RIVER);
                addEnabledBiomes(biomeSource, "enabled_cave_biomes", state, BiomePreview.Category.CAVE);
                addBiomeShare(biomeSource, state);
                return GSON.toJson(root) + "\n";
            }
        }
    }

    private static void addBiomeShare(JsonObject biomeSource, IslandDraft state) {
        if (!state.hasNonNeutralWeights()) return;
        BiomeShare.Settings settings = state.biomeShareSettings();
        JsonObject share = new JsonObject();
        JsonObject weights = new JsonObject();
        for (Map.Entry<String, Integer> entry : state.draftLandWeightMap().entrySet()) {
            weights.addProperty(fullBiomeId(entry.getKey()), entry.getValue());
        }
        share.add("weights", weights);
        JsonObject bias = new JsonObject();
        for (Map.Entry<String, Double> entry : settings.bias().entrySet()) {
            bias.addProperty(fullBiomeId(entry.getKey()), entry.getValue());
        }
        share.add("bias", bias);
        JsonObject achieved = new JsonObject();
        for (Map.Entry<String, Double> entry : state.biomeShareAchieved().entrySet()) {
            achieved.addProperty(fullBiomeId(entry.getKey()), entry.getValue());
        }
        share.add("achieved", achieved);
        JsonObject target = new JsonObject();
        for (Map.Entry<String, Double> entry : state.biomeShareTarget().entrySet()) {
            target.addProperty(fullBiomeId(entry.getKey()), entry.getValue());
        }
        share.add("target", target);
        share.addProperty("feasible", state.sharesFeasible());
        biomeSource.add("biome_share", share);
    }

    private static String fullBiomeId(String id) {
        return id.startsWith("minecraft:") ? id : "minecraft:" + BiomeShare.pathOf(id);
    }

    private static JsonObject shoreJson(IslandDraft state) {
        JsonObject shore = new JsonObject();
        shore.addProperty("density", state.shoreDensity());
        shore.addProperty("variety", state.shoreVariety());
        shore.addProperty("style", "natural");
        shore.addProperty("rocky", state.shoreRocky());
        return shore;
    }

    private static void addEnabledBiomes(JsonObject biomeSource, String key, IslandDraft state,
                                         BiomePreview.Category category) {
        JsonArray enabled = new JsonArray();
        state.enabledBiomes(category).forEach(type -> enabled.add(type.biomeId()));
        biomeSource.add(key, enabled);
    }

    private static void exportStructureSets(ZipOutputStream zip, IslandDraft state,
                                           IslandMask mask, double halfSpan) throws IOException {
        int[] sp = state.effectiveSpawnPx();
        com.hack337.omniisle.continent.IslandShapeConfig terrainConfig = new com.hack337.omniisle.continent.IslandShapeConfig(
            state.generationVersionNumber(), state.isletDensity(), state.riverDensity(),
            state.isWorldBorderEnabled(), state.worldBorderMargin(),
            state.hasCustomHeight() ? java.util.Optional.of(new com.hack337.omniisle.continent.IslandShapeConfig.HeightLayer(2, state.packedHeight())) : java.util.Optional.empty(),
            java.util.Optional.of(new com.hack337.omniisle.continent.IslandShapeConfig.SpawnPos((int)Math.round(state.worldX(sp[0])), (int)Math.round(state.worldZ(sp[1])))),
            List.of(), java.util.Optional.of(mask), state.biomeSeed(),
            state.generationVersionNumber() >= 3
                ? java.util.Optional.of(state.shoreSettings())
                : java.util.Optional.empty()
        );
        com.hack337.omniisle.continent.IslandTerrain terrain = com.hack337.omniisle.continent.IslandTerrain.create(terrainConfig);
        boolean shoreAllowed = state.generationVersionNumber() >= 3
            && !state.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();

        int minChunk = (int)Math.floor(-halfSpan / 16.0);
        int maxChunk = (int)Math.ceil(halfSpan / 16.0);
        int width = maxChunk - minChunk + 1;
        Sample[] samples = new Sample[width * width];
        for (int cz = minChunk; cz <= maxChunk; cz++) {
            for (int cx = minChunk; cx <= maxChunk; cx++) {
                int x = cx * 16 + 8;
                int z = cz * 16 + 8;
                double continentalness = terrain.continentalness(x, z);
                BiomePreview.Type biome;
                if (state.generationVersionNumber() >= 3) {
                    double cpE = terrain.continentalness(x + 8, z);
                    double cpW = terrain.continentalness(x - 8, z);
                    double cpS = terrain.continentalness(x, z + 8);
                    double cpN = terrain.continentalness(x, z - 8);
                    double slope = Math.hypot((cpE - cpW) / 16.0, (cpS - cpN) / 16.0) * 64.0;
                    BiomePreview.Selection selection = BiomePreview.selectionV3(
                        continentalness, x, z, state.biomeSeed(),
                        state.climateScale(), state.gradientMode(), state.gradientStrength(),
                        state.theme(), slope, state.generationVersionNumber(), halfSpan,
                        terrain.rivers(), terrain, state.shoreSettings(), shoreAllowed);
                    biome = state.selectPreviewBiome(selection, x, z);
                } else if (state.hasNonNeutralWeights()) {
                    BiomePreview.Selection selection = BiomePreview.selection(
                        continentalness, x, z, state.biomeSeed(), state.climateScale(),
                        state.gradientMode(), state.gradientStrength(), state.theme(), 0.0,
                        state.generationVersionNumber(), halfSpan, terrain.rivers());
                    biome = state.selectPreviewBiome(selection, x, z);
                } else {
                    biome = state.selectBiome(
                        BiomePreview.surface(continentalness, x, z, state.biomeSeed()), x, z);
                }
                samples[(cz - minChunk) * width + cx - minChunk] =
                    new Sample(continentalness, biome, BiomePreview.isDeepDark(x, z, state.biomeSeed()));
            }
        }

        int version = state.generationVersionNumber();
        boolean isV2 = version >= 2;
        boolean isV3 = version >= 3;
        List<Point> used = new ArrayList<>();
        for (StructureDefinition definition : STRUCTURES) {
            if (!TargetPlatform.HAS_TRIAL_CHAMBERS && definition.option == IslandDraft.StructureOption.TRIAL_CHAMBERS) continue;
            if (!TargetPlatform.HAS_ABANDONED_CAMP && definition.option == IslandDraft.StructureOption.ABANDONED_CAMP) continue;
            boolean enabled = state.isStructureEnabled(definition.option);
            if (isV3 && definition.option == IslandDraft.StructureOption.ANCIENT_CITY
                && !state.isBiomeEnabled(BiomePreview.Type.DEEP_DARK)) {
                enabled = false;
            }

            if (isV3 && definition.option == IslandDraft.StructureOption.STRONGHOLD) {
                List<Point> points = enabled
                    ? placeStrongholds(state, terrain, samples, minChunk, maxChunk, width, used)
                    : List.of();
                used.addAll(points);
                putEntry(zip, "data/minecraft/worldgen/structure_set/" + definition.setName + ".json",
                    fixedMultiSetJson(points, enabled));
                if (enabled) {
                    Set<String> biomeIds = allEnabledBiomeIds(state);
                    for (Point point : points) {
                        biomeIds.add(samples[(point.chunkZ - minChunk) * width + point.chunkX - minChunk].biome.biomeId());
                    }
                    putEntry(zip, "data/minecraft/tags/worldgen/biome/has_structure/" + definition.biomeTag + ".json",
                        biomeTagJson(biomeIds));
                }
                continue;
            }

            if (isV2 && !isV3 && definition.option == IslandDraft.StructureOption.STRONGHOLD) {
                // Legacy v1/v2 worlds keep the 0.6.0 ring scaling untouched (invariant I1).
                int scaledDistance = Math.max(8, Math.min(32, (int) Math.round(32.0 * state.islandSize() / 8000.0)));
                int scaledSpread = Math.max(1, scaledDistance / 10);
                String json = "{\n  \"structures\": [{\n    \"structure\": \"" + definition.structure +
                    "\",\n    \"weight\": 1\n  }],\n  \"placement\": {\n    \"type\": \"minecraft:concentric_rings\",\n" +
                    "    \"count\": 128,\n    \"distance\": " + scaledDistance + ",\n    \"spread\": " + scaledSpread + ",\n" +
                    "    \"preferred_biomes\": \"#minecraft:stronghold_biased_to\",\n    \"salt\": 0\n  }\n}\n";
                putEntry(zip, "data/minecraft/worldgen/structure_set/" + definition.setName + ".json", json);
                continue;
            }

            if (isV3 && (definition.option == IslandDraft.StructureOption.VILLAGE
                    || definition.option == IslandDraft.StructureOption.TRIAL_CHAMBERS)) {
                if (enabled) {
                    int[] spread = scaledSpread(definition.option, state);
                    int salt = definition.option == IslandDraft.StructureOption.VILLAGE
                        ? VILLAGE_SALT : TRIAL_CHAMBERS_SALT;
                    putEntry(zip, "data/minecraft/worldgen/structure_set/" + definition.setName + ".json",
                        randomSpreadSetJson(definition, spread[0], spread[1], salt));
                    putEntry(zip, "data/minecraft/tags/worldgen/biome/has_structure/" + definition.biomeTag + ".json",
                        biomeTagJson(enabledLandBiomeIds(state)));
                } else {
                    putEntry(zip, "data/minecraft/worldgen/structure_set/" + definition.setName + ".json",
                        fixedSetJson(definition, new Point(0, 0), false));
                }
                continue;
            }

            Point point = enabled ? locateStructure(definition, state, samples, minChunk, maxChunk, width, used) : new Point(0, 0);
            if (enabled) used.add(point);
            putEntry(zip, "data/minecraft/worldgen/structure_set/" + definition.setName + ".json",
                fixedSetJson(definition, point, enabled));
            if (enabled) {
                Sample pointSample = samples[(point.chunkZ - minChunk) * width + point.chunkX - minChunk];
                String tagPath = "data/minecraft/tags/worldgen/biome/has_structure/" + definition.biomeTag + ".json";
                if (isV3) {
                    Set<String> biomeIds = allEnabledBiomeIds(state);
                    biomeIds.add(pointSample.biome.biomeId());
                    if (definition.option == IslandDraft.StructureOption.ANCIENT_CITY) {
                        biomeIds.add("minecraft:deep_dark");
                    }
                    putEntry(zip, tagPath, biomeTagJson(biomeIds));
                } else {
                    String biomeId = pointSample.biome.biomeId();
                    if (definition.option == IslandDraft.StructureOption.ANCIENT_CITY) {
                        putEntry(zip, tagPath, "{\n  \"replace\": false,\n  \"values\": [\n    \"minecraft:deep_dark\",\n    \"" + biomeId + "\"\n  ]\n}\n");
                    } else {
                        putEntry(zip, tagPath, "{\n  \"replace\": false,\n  \"values\": [\n    \"" + biomeId + "\"\n  ]\n}\n");
                    }
                }
            }
        }
    }

    private static String fixedSetJson(StructureDefinition definition, Point point, boolean enabled) {
        return "{\n  \"structures\": [{\n    \"structure\": \"" + definition.structure +
            "\",\n    \"weight\": 1\n  }],\n  \"placement\": {\n    \"type\": \"omniisle:fixed\",\n" +
            "    \"chunk_x\": " + point.chunkX + ",\n    \"chunk_z\": " + point.chunkZ +
            ",\n    \"enabled\": " + enabled + "\n  }\n}\n";
    }

    private static String fixedMultiSetJson(List<Point> points, boolean enabled) {
        StringBuilder chunks = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) chunks.append(", ");
            chunks.append("{\"x\": ").append(points.get(i).chunkX)
                .append(", \"z\": ").append(points.get(i).chunkZ).append('}');
        }
        chunks.append(']');
        return "{\n  \"structures\": [{\n    \"structure\": \"minecraft:stronghold\",\n    \"weight\": 1\n"
            + "  }],\n  \"placement\": {\n    \"type\": \"omniisle:fixed_multi\",\n"
            + "    \"chunks\": " + chunks + ",\n    \"enabled\": " + enabled + "\n  }\n}\n";
    }

    private static String randomSpreadSetJson(StructureDefinition definition, int spacing, int separation, int salt) {
        return "{\n  \"structures\": [{\n    \"structure\": \"" + definition.structure +
            "\",\n    \"weight\": 1\n  }],\n  \"placement\": {\n    \"type\": \"minecraft:random_spread\",\n" +
            "    \"salt\": " + salt + ",\n    \"spacing\": " + spacing + ",\n    \"separation\": " + separation +
            "\n  }\n}\n";
    }

    private static String relaxedPyramidJson(String type, String biomeTag) {
        return "{\n  \"type\": \"" + type + "\",\n  \"biomes\": \"#minecraft:has_structure/" + biomeTag
            + "\",\n  \"spawn_overrides\": {},\n  \"step\": \"surface_structures\"\n}\n";
    }

    private static String biomeTagJson(Set<String> biomeIds) {
        StringBuilder values = new StringBuilder();
        for (String biomeId : biomeIds) {
            if (!values.isEmpty()) values.append(",\n    ");
            values.append('"').append(biomeId).append('"');
        }
        return "{\n  \"replace\": false,\n  \"values\": [\n    " + values + "\n  ]\n}\n";
    }

    private static Set<String> enabledLandBiomeIds(IslandDraft state) {
        Set<String> ids = new LinkedHashSet<>();
        state.enabledBiomes(BiomePreview.Category.LAND).forEach(type -> ids.add(type.biomeId()));
        if (ids.isEmpty()) ids.add(BiomePreview.Type.PLAINS.biomeId());
        return ids;
    }

    /**
     * v3 fixed placements have exactly one candidate chunk, so the biome tag is a
     * presence gate, not a distribution control. The editor preview can disagree
     * with the runtime biome source by one family; listing every enabled biome
     * keeps the chosen chunk valid without allowing any other placement. v1/v2 keep
     * the 0.6.0 single-biome tag.
     */
    private static Set<String> allEnabledBiomeIds(IslandDraft state) {
        Set<String> ids = new LinkedHashSet<>();
        for (BiomePreview.Category category : BiomePreview.Category.values()) {
            state.enabledBiomes(category).forEach(type -> ids.add(type.biomeId()));
        }
        if (ids.isEmpty()) ids.add(BiomePreview.Type.PLAINS.biomeId());
        return ids;
    }

    private static int[] scaledSpread(IslandDraft.StructureOption option, IslandDraft state) {
        boolean trial = option == IslandDraft.StructureOption.TRIAL_CHAMBERS;
        int baseSpacing = trial ? TRIAL_CHAMBERS_SPACING : VILLAGE_SPACING;
        int baseSeparation = trial ? TRIAL_CHAMBERS_SEPARATION : VILLAGE_SEPARATION;
        double islandScale = Math.min(1.0, state.islandSize() / 4000.0);
        double density = Math.max(0.5, Math.min(2.0, state.structureDensity()));
        double densityScale = 1.0 / Math.sqrt(density);
        int spacing = Math.max(2, (int) Math.round(baseSpacing * islandScale * densityScale));
        int separation = Math.max(1, (int) Math.round(baseSeparation * islandScale * densityScale));
        separation = Math.min(separation, spacing - 1);
        return new int[]{spacing, separation};
    }

    private record StrongholdCandidate(Point point, double distance, double angle, double score) {}

    /**
     * Places the three v3 strongholds on real land in three roughly even sectors around
     * the spawn. Unlike vanilla concentric rings, this guarantees the spawn distance,
     * pairwise spacing and land requirements for every preset and island size, and the
     * fixed chunks are still visible to /locate and Eyes of Ender.
     */
    private static List<Point> placeStrongholds(IslandDraft state, com.hack337.omniisle.continent.IslandTerrain terrain,
                                                Sample[] samples, int minChunk, int maxChunk, int width,
                                                List<Point> used) throws IOException {
        double spawnX = state.worldSpawnX();
        double spawnZ = state.worldSpawnZ();
        double minSpawnDistance = state.islandSize() >= 4000 ? 300.0 : 150.0;
        double targetDistance = Math.min(1200.0, Math.max(320.0, state.islandSize() * 0.22));
        double maxReach = Math.min(STRONGHOLD_MAX_REACH, Math.max(targetDistance + 300.0, 600.0));
        double sectorRotation = ((state.biomeSeed() >>> 11) % 3600) / 3600.0 * Math.PI * 2.0;

        List<StrongholdCandidate> candidates = new ArrayList<>();
        List<StrongholdCandidate> fallback = new ArrayList<>();
        for (int cz = minChunk; cz <= maxChunk; cz++) {
            for (int cx = minChunk; cx <= maxChunk; cx++) {
                Sample sample = samples[(cz - minChunk) * width + cx - minChunk];
                double x = cx * 16 + 8;
                double z = cz * 16 + 8;
                double dx = x - spawnX;
                double dz = z - spawnZ;
                double distance = Math.hypot(dx, dz);
                if (distance < minSpawnDistance || distance > maxReach) continue;
                boolean tooClose = false;
                for (Point other : used) {
                    int ox = cx - other.chunkX;
                    int oz = cz - other.chunkZ;
                    if (ox * ox + oz * oz < 64) { tooClose = true; break; }
                }
                if (tooClose) continue;
                double score = Math.abs(distance - targetDistance) - sample.continentalness * 40.0;
                StrongholdCandidate candidate = new StrongholdCandidate(new Point(cx, cz), distance,
                    Math.atan2(dz, dx), score);
                if (sample.biome.isLand() && mainIslandDistance(terrain, x, z) > 0.0) {
                    candidates.add(candidate);
                } else if (sample.biome.category == BiomePreview.Category.SHORE) {
                    fallback.add(candidate);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(StrongholdCandidate::score));
        fallback.sort(Comparator.comparingDouble(StrongholdCandidate::score));

        List<Point> chosen = new ArrayList<>();
        for (int sector = 0; sector < 3; sector++) {
            double center = sectorRotation + sector * (Math.PI * 2.0 / 3.0);
            StrongholdCandidate best = null;
            for (StrongholdCandidate candidate : candidates) {
                if (Math.abs(angleDifference(candidate.angle, center)) > Math.PI / 3.0) continue;
                if (!farEnough(candidate.point, chosen)) continue;
                best = candidate;
                break;
            }
            if (best != null) chosen.add(best.point);
        }
        if (chosen.size() < 3) {
            for (StrongholdCandidate candidate : candidates) {
                if (chosen.size() >= 3) break;
                if (farEnough(candidate.point, chosen)) chosen.add(candidate.point);
            }
        }
        if (chosen.size() < 3) {
            for (StrongholdCandidate candidate : fallback) {
                if (chosen.size() >= 3) break;
                if (farEnough(candidate.point, chosen)) chosen.add(candidate.point);
            }
        }
        if (chosen.size() < 3) {
            throw new IOException("Could not place three strongholds: needs " + minSpawnDistance
                + " blocks from spawn and " + STRONGHOLD_MIN_PAIR_DISTANCE + " blocks apart, found "
                + chosen.size() + " land candidates");
        }
        chosen.sort(Comparator.comparingDouble(point ->
            Math.hypot(point.chunkX * 16 + 8 - spawnX, point.chunkZ * 16 + 8 - spawnZ)));
        return chosen;
    }

    private static boolean farEnough(Point candidate, List<Point> chosen) {
        for (Point other : chosen) {
            double dx = (candidate.chunkX - other.chunkX) * 16.0;
            double dz = (candidate.chunkZ - other.chunkZ) * 16.0;
            if (Math.hypot(dx, dz) < STRONGHOLD_MIN_PAIR_DISTANCE) return false;
        }
        return true;
    }

    private static double mainIslandDistance(com.hack337.omniisle.continent.IslandTerrain terrain, double x, double z) {
        if (terrain instanceof com.hack337.omniisle.continent.ModernTerrainV2 modern) {
            return modern.computeSignedDistance(x, z, true);
        }
        return terrain.signedDistance(x, z);
    }

    private static double angleDifference(double a, double b) {
        double difference = Math.abs(a - b) % (Math.PI * 2.0);
        return difference > Math.PI ? Math.PI * 2.0 - difference : difference;
    }

    /**
     * The monument's own generation check surveys every biome within 29 blocks and requires
     * all of them to be ocean. Requiring the 5x5 chunk centres around the candidate to stay
     * below the deep threshold keeps that survey on deep water and rejects islet skirts,
     * which can dip below -0.62 right next to shallow land.
     */
    private static boolean deepSurveyClean(Sample[] samples, int minChunk, int maxChunk, int width,
                                           int chunkX, int chunkZ) {
        for (int oz = -2; oz <= 2; oz++) {
            int sz = chunkZ + oz;
            if (sz < minChunk || sz > maxChunk) return false;
            for (int ox = -2; ox <= 2; ox++) {
                int sx = chunkX + ox;
                if (sx < minChunk || sx > maxChunk) return false;
                if (samples[(sz - minChunk) * width + sx - minChunk].continentalness
                    > DEEP_MONUMENT_MAX_CONTINENTALNESS) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Point locateStructure(StructureDefinition definition, IslandDraft state, Sample[] samples,
                                     int minChunk, int maxChunk, int width, List<Point> used) throws IOException {
        Set<BiomePreview.Type> accepted = EnumSet.noneOf(BiomePreview.Type.class);
        definition.biomes.forEach(type -> accepted.addAll(state.compatibleChoices(type)));
        Point best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double spawnX = state.worldSpawnX();
        double spawnZ = state.worldSpawnZ();
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
                boolean deepMonument = definition.option == IslandDraft.StructureOption.OCEAN_MONUMENT;
                if (deepMonument && !deepSurveyClean(samples, minChunk, maxChunk, width, cx, cz)) continue;
                boolean oceanOrShore = sample.biome.name().contains("OCEAN") || sample.biome == BiomePreview.Type.BEACH;
                double score;
                if (deepMonument) {
                    // Keep the monument safely inside the v3 deep band [0.4*Wo, 1.0*Wo],
                    // whose shallow edge is about -0.62, instead of the old +-0.40 target.
                    score = -Math.abs(sample.continentalness + 0.72);
                } else {
                    score = oceanOrShore ? -Math.abs(sample.continentalness + 0.40) : sample.continentalness;
                }
                double distFromSpawn = Math.hypot((cx * 16 + 8) - spawnX, (cz * 16 + 8) - spawnZ);
                score -= distFromSpawn * 0.00005;
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
