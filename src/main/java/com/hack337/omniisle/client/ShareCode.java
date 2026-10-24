package com.hack337.omniisle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes and decodes OmniIsle draft designs into compressed share code strings:
 * OMNI2-<base64url(deflate(payload))>-<crc16>
 *
 * Enforces strict decompression safety (capped at 256 KiB) and CRC verification.
 */
public final class ShareCode {
    public static final String PREFIX = "OMNI2-";
    public static final int MAX_DECOMPRESSED_BYTES = 256 * 1024; // 256 KiB cap
    private static final Gson GSON = new GsonBuilder().create();

    private ShareCode() {}

    /** Standard CRC-16 (CCITT) implementation. */
    public static int crc16(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc = ((crc >>> 8) | (crc << 8)) & 0xFFFF;
            crc ^= (b & 0xFF);
            crc ^= (crc & 0xFF) >> 4;
            crc ^= (crc << 12) & 0xFFFF;
            crc ^= ((crc & 0xFF) << 5) & 0xFFFF;
        }
        return crc & 0xFFFF;
    }

    /** Serializes a draft to the JSON payload used by share codes and config/omniisle.json. */
    public static String toJson(IslandDraft draft) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.addProperty("resolution", draft.resolution());
        root.addProperty("island_size", draft.islandSize());
        root.addProperty("shape_seed", draft.shapeSeed());
        root.addProperty("biome_seed", draft.biomeSeed());
        root.addProperty("preset", draft.preset().name());
        root.addProperty("relief", draft.relief().name());
        root.addProperty("theme", draft.theme().name());
        root.addProperty("generation_version", draft.generationVersionNumber());
        root.addProperty("islet_density", draft.isletDensity());
        root.addProperty("structure_density", draft.structureDensity());
        root.addProperty("climate_scale", draft.climateScale());
        root.addProperty("gradient_mode", draft.gradientMode().name());
        root.addProperty("gradient_strength", draft.gradientStrength());
        root.addProperty("river_density", draft.riverDensity());
        root.addProperty("world_border", draft.isWorldBorderEnabled());
        root.addProperty("world_border_margin", draft.worldBorderMargin());
        root.addProperty("shore_density", draft.shoreDensity());
        root.addProperty("shore_variety", draft.shoreVariety());
        root.addProperty("shore_rocky", draft.shoreRocky());
        if (draft.hasExplicitSpawn()) {
            root.addProperty("spawn_px", draft.explicitSpawnPx());
            root.addProperty("spawn_pz", draft.explicitSpawnPz());
        }
        root.addProperty("mask", draft.packedMask());
        if (draft.hasCustomHeight()) {
            root.addProperty("height", draft.packedHeight());
        }

        JsonArray biomes = new JsonArray();
        for (BiomePreview.Type b : draft.enabledBiomes().values()) {
            biomes.add(b.id);
        }
        root.add("biomes", biomes);

        JsonArray structs = new JsonArray();
        for (IslandDraft.StructureOption s : draft.enabledStructures()) {
            structs.add(s.name());
        }
        root.add("structures", structs);

        return GSON.toJson(root);
    }

    /** Encodes an IslandDraft into a share code string. */
    public static String encode(IslandDraft draft) {
        byte[] payload = toJson(draft).getBytes(StandardCharsets.UTF_8);

        // Deflate
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(payload);
        deflater.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            baos.write(buf, 0, count);
        }
        deflater.end();
        byte[] compressed = baos.toByteArray();

        int crc = crc16(compressed);
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
        String crcHex = String.format(Locale.ROOT, "%04X", crc);

        return PREFIX + b64 + "-" + crcHex;
    }

    /** Decodes a share code string into an IslandDraft. Throws IllegalArgumentException on failure. */
    public static IslandDraft decode(String code) {
        if (code == null || !code.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid share code: must start with " + PREFIX);
        }
        String body = code.substring(PREFIX.length()).trim();
        int dashIdx = body.lastIndexOf('-');
        if (dashIdx <= 0 || dashIdx == body.length() - 1) {
            throw new IllegalArgumentException("Invalid share code format: missing CRC checksum");
        }

        String b64 = body.substring(0, dashIdx);
        String expectedCrcHex = body.substring(dashIdx + 1).toUpperCase(Locale.ROOT);
        int expectedCrc;
        try {
            expectedCrc = Integer.parseInt(expectedCrcHex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid share code: malformed CRC checksum '" + expectedCrcHex + "'");
        }

        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid share code: malformed Base64 data", e);
        }

        int actualCrc = crc16(compressed);
        if (actualCrc != expectedCrc) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                "Checksum mismatch: share code corrupted (expected %04X, got %04X)", expectedCrc, actualCrc));
        }

        // Inflate with size capping
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int totalDecompressed = 0;
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                totalDecompressed += count;
                if (totalDecompressed > MAX_DECOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("Share code payload exceeded maximum safe size of 256 KiB");
                }
                baos.write(buf, 0, count);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decompress share code payload: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }

        String json = baos.toString(StandardCharsets.UTF_8);
        return decodeJson(json);
    }

    public static IslandDraft decodeJson(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse share code JSON: " + e.getMessage(), e);
        }
        return decodeJsonObject(root);
    }

    public static IslandDraft decodeJsonObject(JsonObject root) {
        int res = root.has("resolution") ? root.get("resolution").getAsInt() : IslandDraft.RESOLUTION;
        int islandSize = root.has("island_size") ? root.get("island_size").getAsInt() : IslandDraft.DEFAULT_ISLAND_SIZE;
        long shapeSeed = root.has("shape_seed") ? root.get("shape_seed").getAsLong() : 0L;
        long biomeSeed = root.has("biome_seed") ? root.get("biome_seed").getAsLong() : 0L;

        IslandDraft.Preset preset = IslandDraft.Preset.NATURAL;
        if (root.has("preset")) {
            try {
                preset = IslandDraft.Preset.valueOf(root.get("preset").getAsString().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {}
        }

        IslandDraft draft = new IslandDraft(res);
        draft.setIslandSize(islandSize);
        draft.setShapeSeed(shapeSeed);
        draft.setBiomeSeed(biomeSeed);
        draft.setPreset(preset);

        if (root.has("relief")) {
            try {
                draft.setRelief(IslandDraft.Relief.valueOf(root.get("relief").getAsString().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {}
        }
        if (root.has("theme")) {
            try {
                draft.applyTheme(IslandDraft.BiomeTheme.valueOf(root.get("theme").getAsString().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {}
        }
        if (root.has("generation_version")) {
            draft.setGenerationVersion(IslandDraft.GenerationVersion.fromInt(root.get("generation_version").getAsInt()));
        }
        if (root.has("islet_density")) {
            draft.setIsletDensity(root.get("islet_density").getAsDouble());
        }
        if (root.has("structure_density")) {
            draft.setStructureDensity(root.get("structure_density").getAsDouble());
        }
        if (root.has("climate_scale")) {
            draft.setClimateScale(root.get("climate_scale").getAsInt());
        }
        if (root.has("gradient_mode")) {
            draft.setGradientMode(com.hack337.omniisle.continent.IslandClimate.GradientMode.fromString(root.get("gradient_mode").getAsString()));
        }
        if (root.has("gradient_strength")) {
            draft.setGradientStrength(root.get("gradient_strength").getAsDouble());
        }
        if (root.has("river_density")) {
            draft.setRiverDensity(root.get("river_density").getAsDouble());
        }
        if (root.has("world_border")) {
            draft.setWorldBorderEnabled(root.get("world_border").getAsBoolean());
        }
        if (root.has("world_border_margin")) {
            draft.setWorldBorderMargin(root.get("world_border_margin").getAsInt());
        }
        if (root.has("shore_density")) {
            draft.setShoreDensity(root.get("shore_density").getAsDouble());
        }
        if (root.has("shore_variety")) {
            draft.setShoreVariety(root.get("shore_variety").getAsDouble());
        }
        if (root.has("shore_rocky")) {
            draft.setShoreRocky(root.get("shore_rocky").getAsBoolean());
        }
        if (root.has("mask")) {
            draft.unpackMask(root.get("mask").getAsString());
        }
        if (root.has("height")) {
            draft.unpackHeight(root.get("height").getAsString());
        }

        if (root.has("spawn_px") && root.has("spawn_pz")) {
            draft.setSpawn(root.get("spawn_px").getAsInt(), root.get("spawn_pz").getAsInt());
        } else {
            draft.clearSpawn();
        }

        if (root.has("biomes") && root.get("biomes").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("biomes");
            List<String> enabledIds = new ArrayList<>();
            for (JsonElement el : arr) enabledIds.add(el.getAsString());
            draft.restoreBiomesFromIds(enabledIds);
        }
        if (root.has("structures") && root.get("structures").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("structures");
            List<String> enabledNames = new ArrayList<>();
            for (JsonElement el : arr) enabledNames.add(el.getAsString());
            draft.restoreStructuresFromNames(enabledNames);
        }

        return draft;
    }
}
