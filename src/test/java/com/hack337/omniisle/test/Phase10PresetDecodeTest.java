package com.hack337.omniisle.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hack337.omniisle.OmniIsleIds;
import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.BiomeShareCalibrator;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.BiomeShare;
import com.hack337.omniisle.worldgen.FixedMultiStructurePlacement;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.hack337.omniisle.worldgen.RelaxedPyramids;
import com.hack337.omniisle.worldgen.SingleStructurePlacement;
import com.hack337.omniisle.worldgen.SpawnPlateauDensity;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Phase10PresetDecodeTest {
    private static final String PRESET_ENTRY = "data/omniisle/worldgen/world_preset/omniisle.json";

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registerModCodecs();
    }

    /**
     * The real fabric initializer registers these codecs before the built-in registries
     * freeze; a unit test boots vanilla first, so thaw only the type registries involved.
     */
    private static void registerModCodecs() {
        Registry<?>[] registries = {
            BuiltInRegistries.DENSITY_FUNCTION_TYPE, BuiltInRegistries.BIOME_SOURCE,
            BuiltInRegistries.STRUCTURE_TYPE, BuiltInRegistries.STRUCTURE_PLACEMENT
        };
        for (Registry<?> registry : registries) setFrozen(registry, false);
        try {
            if (BuiltInRegistries.DENSITY_FUNCTION_TYPE.getValue(OmniIsleIds.id("island_continents")) == null) {
                IslandDensityFunction.registerCodec();
            }
            if (BuiltInRegistries.DENSITY_FUNCTION_TYPE.getValue(OmniIsleIds.id("spawn_plateau")) == null) {
                SpawnPlateauDensity.registerCodec();
            }
            if (BuiltInRegistries.BIOME_SOURCE.getValue(OmniIsleIds.id("island_biomes")) == null) {
                OmniBiomeSource.registerCodec();
            }
            if (BuiltInRegistries.STRUCTURE_TYPE.getValue(OmniIsleIds.id("relaxed_desert_pyramid")) == null) {
                RelaxedPyramids.register();
            }
            if (BuiltInRegistries.STRUCTURE_PLACEMENT.getValue(OmniIsleIds.id("fixed")) == null) {
                SingleStructurePlacement.registerType();
            }
            if (BuiltInRegistries.STRUCTURE_PLACEMENT.getValue(OmniIsleIds.id("fixed_multi")) == null) {
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

    @Test
    @DisplayName("10.11 generated preset decodes through the real worldgen registries")
    void presetDecodesWithRealRegistry(@TempDir Path tempDir) throws Exception {
        IslandDraft draft = new IslandDraft();
        draft.setShapeSeed(0x306d956984a4bf25L);
        draft.setBiomeSeed(0x306d956984a4bf25L);
        draft.setPreset(IslandDraft.Preset.NATURAL);
        draft.applyTheme(IslandDraft.BiomeTheme.ALL);
        draft.setBiomeWeight(BiomePreview.Type.CHERRY_GROVE, 45);
        draft.applyCalibration(BiomeShareCalibrator.calibrate(draft));

        Path exportDir = Files.createDirectories(tempDir.resolve("export"));
        WorldPackBuilder.export(exportDir, draft);
        Path generatedZip = exportDir.resolve(WorldPackBuilder.FILE_NAME);
        Path packDir = tempDir.resolve("pack");
        extract(generatedZip, packDir);

        RegistryAccess.Frozen access = loadWorldgen(packDir); {
            WorldPackBuilder.validatePreset(exportDir, access);

            JsonElement presetJson;
            try (ZipFile zip = new ZipFile(generatedZip.toFile());
                 InputStreamReader reader = new InputStreamReader(
                     zip.getInputStream(zip.getEntry(PRESET_ENTRY)), StandardCharsets.UTF_8)) {
                presetJson = JsonParser.parseReader(reader);
            }
            WorldPreset preset = WorldPreset.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, access), presetJson)
                .getOrThrow(message -> new AssertionError("preset decode failed: " + message));
            LevelStem overworld = preset.overworld()
                .orElseThrow(() -> new AssertionError("preset has no overworld"));
            assertTrue(overworld.generator().getBiomeSource() instanceof OmniBiomeSource,
                "overworld generator should use the island biome source");
            OmniBiomeSource source = (OmniBiomeSource) overworld.generator().getBiomeSource();
            BiomeShare.Settings settings = source.biomeShareSettings();
            assertEquals(45, settings.weight("minecraft:cherry_grove"),
                "cherry weight must survive the codec");
            assertFalse(settings.bias().isEmpty(), "calibrated bias must be encoded");
            assertTrue(settings.hasNonNeutralWeight());

            var worldPresetRegistry = access.lookupOrThrow(Registries.WORLD_PRESET);
            assertTrue(worldPresetRegistry.listElements().anyMatch(holder ->
                    holder.unwrapKey().orElseThrow().identifier().getPath().equals("omniisle")),
                "the loader should have decoded the generated world preset from the pack");
        }
    }

    @Test
    @DisplayName("10.12 corrupt preset fails validation without crashing")
    void corruptPresetFailsValidation(@TempDir Path tempDir) throws Exception {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.NATURAL);
        draft.applyTheme(IslandDraft.BiomeTheme.ALL);
        Path exportDir = Files.createDirectories(tempDir.resolve("export"));
        WorldPackBuilder.export(exportDir, draft);
        Path packDir = tempDir.resolve("pack");
        extract(exportDir.resolve(WorldPackBuilder.FILE_NAME), packDir);

        RegistryAccess.Frozen access = loadWorldgen(packDir);

        rewritePreset(exportDir.resolve(WorldPackBuilder.FILE_NAME), "{\"dimensions\": ");
        WorldPackBuilder.PresetValidationException failure = assertThrows(
            WorldPackBuilder.PresetValidationException.class,
            () -> WorldPackBuilder.validatePreset(exportDir, access));
        assertNotNull(failure.getMessage());
        System.out.println("10.12 corrupt preset rejected: " + failure.getMessage());

        rewritePreset(exportDir.resolve(WorldPackBuilder.FILE_NAME),
            "{\"dimensions\":{\"minecraft:overworld\":{\"generator\":"
                + "{\"type\":\"minecraft:noise\",\"biome_source\":"
                + "{\"type\":\"omniisle:island_biomes\",\"biome_seed\":1}},"
                + "\"type\":\"minecraft:overworld\"}}}");
        assertThrows(WorldPackBuilder.PresetValidationException.class,
            () -> WorldPackBuilder.validatePreset(exportDir, access));
    }

    private static RegistryAccess.Frozen loadWorldgen(Path generatedPackDir) {
        PackResources vanilla = ServerPacksSource.createVanillaPackSource().fullResources();
        PackLocationInfo modInfo = new PackLocationInfo("omniisle_mod",
            Component.literal("OmniIsle mod resources"), PackSource.BUILT_IN, Optional.empty());
        PackLocationInfo generatedInfo = new PackLocationInfo("omniisle_generated",
            Component.literal("OmniIsle generated pack"), PackSource.BUILT_IN, Optional.empty());
        Path modResources = Path.of("build", "resources", "main");
        try (PathPackResources generated = new PathPackResources(generatedInfo, generatedPackDir);
             PathPackResources mod = new PathPackResources(modInfo, modResources);
             // MultiPackResourceManager gives the last pack the highest priority, matching the
             // game where the world datapack overrides mod resources.
             MultiPackResourceManager resources = new MultiPackResourceManager(
                 PackType.SERVER_DATA, List.of(vanilla, mod, generated))) {
            RegistryAccess.Frozen builtinAccess =
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            List<Registry.PendingTags<?>> pendingTags =
                TagLoader.loadTagsForExistingRegistries(resources, builtinAccess);
            List<HolderLookup.RegistryLookup<?>> contexts =
                TagLoader.buildUpdatedLookups(builtinAccess, pendingTags);
            return RegistryDataLoader.load(resources, contexts, testRegistries(), Runnable::run).join();
        }
    }

    private static List<RegistryDataLoader.RegistryData<?>> testRegistries() {
        return RegistryDataLoader.WORLD_REGISTRIES;
    }

    private static void rewritePreset(Path zipPath, String presetJson) throws IOException {
        Path temporary = zipPath.resolveSibling(zipPath.getFileName() + ".tmp");
        try (ZipFile source = new ZipFile(zipPath.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals(PRESET_ENTRY)) {
                    out.write(presetJson.getBytes(StandardCharsets.UTF_8));
                } else {
                    try (InputStream in = source.getInputStream(entry)) {
                        in.transferTo(out);
                    }
                }
                out.closeEntry();
            }
        }
        Files.move(temporary, zipPath, StandardCopyOption.REPLACE_EXISTING);
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
}
