package com.reystin.oneisle.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reystin.oneisle.OneIsleIds;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.WorldPackBuilder;
import com.reystin.oneisle.worldgen.FixedMultiStructurePlacement;
import com.reystin.oneisle.worldgen.IslandDensityFunction;
import com.reystin.oneisle.worldgen.OneBiomeSource;
import com.reystin.oneisle.worldgen.RelaxedPyramids;
import com.reystin.oneisle.worldgen.SingleStructurePlacement;
import com.reystin.oneisle.worldgen.SpawnPlateauDensity;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 13: v3 highlands must reach the editor's advertised range without the circular
 * spawn plateau, while v2 packs keep their original density resources.
 */
public class Phase13HighlandWorldgenTest {
    private static final String PRESET_ENTRY = "data/oneisle/worldgen/world_preset/oneisle.json";
    private static final String DENSITY_DIR = "data/oneisle/worldgen/density_function/overworld/";

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registerModCodecs();
    }

    @Test
    void v3HighlandsReachAdvertisedHeightAndSpawnPlateauIsGone(@TempDir Path tempDir) throws Exception {
        IslandDraft draft = draft(IslandDraft.GenerationVersion.V3, true);
        Path exportDir = Files.createDirectories(tempDir.resolve("export"));
        WorldPackBuilder.export(exportDir, draft);
        Path packDir = tempDir.resolve("pack");
        extract(exportDir.resolve(WorldPackBuilder.FILE_NAME), packDir);

        Path densityDir = packDir.resolve(DENSITY_DIR);
        Path offsetPath = densityDir.resolve("offset.json");
        Path finalDensityPath = densityDir.resolve("final_density.json");
        Path preliminaryPath = densityDir.resolve("preliminary_surface_level.json");
        assertTrue(Files.exists(offsetPath), "v3 must generate the highland offset");
        assertTrue(Files.exists(finalDensityPath), "v3 must generate the raised final density");
        assertTrue(Files.exists(preliminaryPath), "v3 must generate the raised preliminary surface level");

        JsonObject offset = readJson(offsetPath);
        assertEquals("minecraft:add", offset.get("type").getAsString());
        assertEquals("minecraft:mul", offset.getAsJsonObject("left").get("type").getAsString());
        assertEquals(3, countSplines(offset),
            "offset must carry the static continents spline plus the scale and lift splines");
        assertTrue(hasSplineCoordinate(offset, "oneisle:overworld/continents"),
            "highland splines must be driven by continentalness");
        assertTrue(hasSplinePoint(offset, 0.95, 1.00), "lift spline must reach 1.0 at c=0.95");
        assertTrue(hasSplinePoint(offset, 0.95, 0.20), "scale spline must drop to 0.20 at c=0.95");
        JsonObject finalDensity = readJson(finalDensityPath);
        assertTrue(hasGradient(finalDensity, 300.0, 316.0), "final density fade must start at Y300");
        assertFalse(hasGradient(finalDensity, 240.0, 256.0), "v3 final density must not keep the Y240 fade");
        JsonObject preliminary = readJson(preliminaryPath);
        assertTrue(hasGradient(preliminary, 300.0, 316.0), "preliminary surface level fade must start at Y300");
        assertFalse(hasGradient(preliminary, 240.0, 256.0), "preliminary surface level must not keep the Y240 fade");

        try (var files = Files.walk(packDir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String raw = Files.readString(file);
                assertFalse(raw.contains("spawn_plateau"), "v3 pack must not reference the spawn plateau: " + file);
                assertFalse(raw.contains("\"top_y\""), "v3 pack must not emit plateau parameters: " + file);
            }
        }

        RegistryAccess.Frozen access = loadWorldgen(packDir);
        WorldPackBuilder.validatePreset(exportDir, access);
        WorldPreset preset = WorldPreset.DIRECT_CODEC
            .parse(RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, access), readJson(exportDir.resolve(
                WorldPackBuilder.FILE_NAME), PRESET_ENTRY))
            .getOrThrow(message -> new AssertionError("preset decode failed: " + message));
        LevelStem overworld = preset.overworld().orElseThrow();
        assertTrue(overworld.generator() instanceof NoiseBasedChunkGenerator);
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) overworld.generator();
        RandomState randomState = RandomState.create(
            access.lookupOrThrow(Registries.NOISE), 4242L, generator.generatorSettings().value());
        LevelHeightAccessor levels = LevelHeightAccessor.create(-64, 384);
        int[] spawnPx = draft.effectiveSpawnPx();
        int centerX = (int) Math.round(draft.worldX(spawnPx[0]));
        int centerZ = (int) Math.round(draft.worldZ(spawnPx[1]));

        List<Integer> heights = new ArrayList<>();
        for (int z = centerZ - 320; z <= centerZ + 320; z += 16) {
            for (int x = centerX - 320; x <= centerX + 320; x += 16) {
                heights.add(generator.getBaseHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG, levels, randomState));
            }
        }
        Collections.sort(heights);
        int min = heights.getFirst();
        int median = heights.get(heights.size() / 2);
        int p90 = heights.get((int) (heights.size() * 0.90));
        int max = heights.getLast();
        long above220 = heights.stream().filter(y -> y >= 220).count();
        System.out.printf("13.1 v3 highland surfaces: min=%d median=%d p90=%d max=%d above220=%d%n",
            min, median, p90, max, above220);

        assertTrue(max >= 240, "painted tier-3 highland must reach Y240+, got " + max);
        assertTrue(max <= 316, "terrain must stay below the raised density fade, got " + max);
        assertTrue(above220 >= 10, "expected a meaningful highland area above Y220, got " + above220);
        assertTrue(p90 >= 180, "highland p90 should be high, got " + p90);
    }

    @Test
    void v2PackKeepsLegacyDensityResources(@TempDir Path tempDir) throws Exception {
        IslandDraft draft = draft(IslandDraft.GenerationVersion.V2, false);
        Path exportDir = Files.createDirectories(tempDir.resolve("export"));
        WorldPackBuilder.export(exportDir, draft);
        try (ZipFile zip = new ZipFile(exportDir.resolve(WorldPackBuilder.FILE_NAME).toFile())) {
            assertFalse(zip.stream().anyMatch(entry -> entry.getName().equals(DENSITY_DIR + "offset.json")));
            assertFalse(zip.stream().anyMatch(entry -> entry.getName().equals(DENSITY_DIR + "final_density.json")));
            assertFalse(zip.stream().anyMatch(entry ->
                entry.getName().equals(DENSITY_DIR + "preliminary_surface_level.json")));
        }
    }

    private static IslandDraft draft(IslandDraft.GenerationVersion version, boolean paintHighland) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setRelief(IslandDraft.Relief.BALANCED);
        draft.setIslandSize(4000);
        draft.setShapeSeed(4242);
        draft.setBiomeSeed(4242);
        draft.setRiverDensity(0.0);
        draft.setIsletDensity(0.0);
        draft.setGenerationVersion(version);
        draft.rebuildShape();
        if (paintHighland) {
            int[] spawn = draft.effectiveSpawnPx();
            draft.paintHeight(spawn[0], spawn[1], 20, 3);
        }
        return draft;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject readJson(Path zipPath, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) throw new IOException("Missing zip entry " + entryName);
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static int countSplines(JsonElement element) {
        int count = 0;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && "minecraft:spline".equals(object.get("type").getAsString())) count++;
            for (var entry : object.entrySet()) count += countSplines(entry.getValue());
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) count += countSplines(child);
        }
        return count;
    }

    private static boolean hasSplineCoordinate(JsonElement element, String coordinate) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("coordinate") && coordinate.equals(object.get("coordinate").getAsString())) return true;
            for (var entry : object.entrySet()) {
                if (hasSplineCoordinate(entry.getValue(), coordinate)) return true;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (hasSplineCoordinate(child, coordinate)) return true;
            }
        }
        return false;
    }

    private static boolean hasSplinePoint(JsonElement element, double location, double value) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("location") && object.get("location").getAsDouble() == location
                && object.has("value") && object.get("value").isJsonPrimitive()
                && object.get("value").getAsDouble() == value) {
                return true;
            }
            for (var entry : object.entrySet()) {
                if (hasSplinePoint(entry.getValue(), location, value)) return true;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (hasSplinePoint(child, location, value)) return true;
            }
        }
        return false;
    }

    private static boolean hasGradient(JsonElement element, double from, double to) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && "minecraft:gradient".equals(object.get("type").getAsString())
                && object.has("from_coordinate") && object.get("from_coordinate").getAsDouble() == from
                && object.has("to_coordinate") && object.get("to_coordinate").getAsDouble() == to) {
                return true;
            }
            for (var entry : object.entrySet()) {
                if (hasGradient(entry.getValue(), from, to)) return true;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (hasGradient(child, from, to)) return true;
            }
        }
        return false;
    }

    private static RegistryAccess.Frozen loadWorldgen(Path generatedPackDir) {
        PackResources vanilla = ServerPacksSource.createVanillaPackSource().fullResources();
        PackLocationInfo modInfo = new PackLocationInfo("oneisle_mod",
            Component.literal("OneIsle mod resources"), PackSource.BUILT_IN, Optional.empty());
        PackLocationInfo generatedInfo = new PackLocationInfo("oneisle_generated",
            Component.literal("OneIsle generated pack"), PackSource.BUILT_IN, Optional.empty());
        Path modResources = Path.of("build", "resources", "main");
        try (PathPackResources generated = new PathPackResources(generatedInfo, generatedPackDir);
             PathPackResources mod = new PathPackResources(modInfo, modResources);
             // MultiPackResourceManager gives the last pack the highest priority, matching the
             // game where the world datapack overrides mod resources.
             MultiPackResourceManager resources = new MultiPackResourceManager(
                 PackType.SERVER_DATA, List.of(vanilla, mod, generated))) {
            RegistryAccess.Frozen builtinAccess =
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var pendingTags = TagLoader.loadTagsForExistingRegistries(resources, builtinAccess);
            var contexts = TagLoader.buildUpdatedLookups(builtinAccess, pendingTags);
            return RegistryDataLoader.load(resources, contexts, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
        }
    }

    private static void extract(Path zipPath, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) throw new IOException("zip entry escapes target: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static void registerModCodecs() {
        Registry<?>[] registries = {
            BuiltInRegistries.DENSITY_FUNCTION_TYPE, BuiltInRegistries.BIOME_SOURCE,
            BuiltInRegistries.STRUCTURE_TYPE, BuiltInRegistries.STRUCTURE_PLACEMENT
        };
        for (Registry<?> registry : registries) setFrozen(registry, false);
        try {
            if (BuiltInRegistries.DENSITY_FUNCTION_TYPE.getValue(OneIsleIds.id("island_continents")) == null) {
                IslandDensityFunction.registerCodec();
            }
            if (BuiltInRegistries.DENSITY_FUNCTION_TYPE.getValue(OneIsleIds.id("spawn_plateau")) == null) {
                SpawnPlateauDensity.registerCodec();
            }
            if (BuiltInRegistries.BIOME_SOURCE.getValue(OneIsleIds.id("island_biomes")) == null) {
                OneBiomeSource.registerCodec();
            }
            if (BuiltInRegistries.STRUCTURE_TYPE.getValue(OneIsleIds.id("relaxed_desert_pyramid")) == null) {
                RelaxedPyramids.register();
            }
            if (BuiltInRegistries.STRUCTURE_PLACEMENT.getValue(OneIsleIds.id("fixed")) == null) {
                SingleStructurePlacement.registerType();
            }
            if (BuiltInRegistries.STRUCTURE_PLACEMENT.getValue(OneIsleIds.id("fixed_multi")) == null) {
                FixedMultiStructurePlacement.registerType();
            }
        } finally {
            for (Registry<?> registry : registries) {
                resetBoundTags(registry);
                registry.freeze();
            }
        }
    }

    private static void resetBoundTags(Registry<?> registry) {
        try {
            Class<?> tagSetClass = Class.forName("net.minecraft.core.MappedRegistry$TagSet");
            var unbound = tagSetClass.getDeclaredMethod("unbound");
            unbound.setAccessible(true);
            Field allTags = MappedRegistry.class.getDeclaredField("allTags");
            allTags.setAccessible(true);
            allTags.set(registry, unbound.invoke(null));
            Field frozenTags = MappedRegistry.class.getDeclaredField("frozenTags");
            frozenTags.setAccessible(true);
            ((java.util.Map<?, ?>) frozenTags.get(registry)).clear();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not reset registry tags", exception);
        }
    }

    private static void setFrozen(Registry<?> registry, boolean frozen) {
        try {
            Field field = MappedRegistry.class.getDeclaredField("frozen");
            field.setAccessible(true);
            field.setBoolean(registry, frozen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access registry freeze flag", exception);
        }
    }
}
