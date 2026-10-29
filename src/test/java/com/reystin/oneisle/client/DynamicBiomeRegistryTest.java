package com.reystin.oneisle.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.7 dynamic biome registry: a datapack-provided custom biome loaded through
 * the real RegistryDataLoader must appear in the editor biome list under its category.
 */
public class DynamicBiomeRegistryTest {
    private static final boolean WRITE = Boolean.getBoolean("oneisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/dynamic_biome");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void datapackBiomesAppearInCategoryList(@TempDir Path tempDir) throws IOException {
        Path packRoot = tempDir.resolve("phase97_biome_pack");
        writePack(packRoot);
        assertTrue(PathPackResources.validatePath(packRoot));

        PackLocationInfo location = new PackLocationInfo("phase97_biome_pack",
            Component.literal("OneIsle Phase 9.7 biome pack"), PackSource.BUILT_IN, Optional.empty());
        DynamicBiomeRegistry.resetCache();
        try (PathPackResources pack = new PathPackResources(location, packRoot);
             MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
            RegistryDataLoader.RegistryData<Biome> biomeData = new RegistryDataLoader.RegistryData<>(
                Registries.BIOME, Biome.DIRECT_CODEC, RegistryValidator.none());
            CompletableFuture<RegistryAccess.Frozen> future = RegistryDataLoader.load(
                resources, List.of(), List.of(biomeData), Runnable::run);
            RegistryAccess.Frozen access = future.join();
            var biomeRegistry = access.lookupOrThrow(Registries.BIOME);
            assertEquals(2, biomeRegistry.size(), "custom pack should contribute exactly two biomes");

            DynamicBiomeRegistry.updateFromRegistry(biomeRegistry);
            List<DynamicBiomeRegistry.BiomeEntry> entries = DynamicBiomeRegistry.getAvailableBiomes();

            DynamicBiomeRegistry.BiomeEntry beach = find(entries, "oneisle_test:custom_beach");
            assertNotNull(beach, "custom beach biome missing from the editor biome list");
            assertEquals(BiomePreview.Category.SHORE, beach.category(),
                "custom_beach must be categorized as SHORE");

            DynamicBiomeRegistry.BiomeEntry caves = find(entries, "oneisle_test:custom_caves");
            assertNotNull(caves, "custom caves biome missing from the editor biome list");
            assertEquals(BiomePreview.Category.CAVE, caves.category(),
                "custom_caves must be categorized as CAVE");

            if (WRITE) {
                Files.createDirectories(EVIDENCE);
                StringBuilder report = new StringBuilder(
                    "pack,phase97_biome_pack\nregistry_biomes," + biomeRegistry.size() + "\n"
                        + "list_source,DynamicBiomeRegistry.updateFromRegistry(real RegistryDataLoader)\n");
                for (DynamicBiomeRegistry.BiomeEntry entry : entries) {
                    report.append("entry,").append(entry.id()).append(',').append(entry.category())
                        .append(",temperature=").append(entry.temperature()).append('\n');
                }
                report.append("custom_beach_category,").append(beach.category()).append('\n');
                report.append("custom_caves_category,").append(caves.category()).append('\n');
                Files.writeString(EVIDENCE.resolve("dynamic_biome_list.txt"), report.toString(),
                    StandardCharsets.UTF_8);
                Files.writeString(EVIDENCE.resolve("custom_beach.json"),
                    Files.readString(packRoot.resolve("data/oneisle_test/worldgen/biome/custom_beach.json")),
                    StandardCharsets.UTF_8);
                Files.writeString(EVIDENCE.resolve("pack.mcmeta"),
                    Files.readString(packRoot.resolve("pack.mcmeta")), StandardCharsets.UTF_8);
            }
        } finally {
            DynamicBiomeRegistry.resetCache();
        }
    }

    private static DynamicBiomeRegistry.BiomeEntry find(List<DynamicBiomeRegistry.BiomeEntry> entries, String id) {
        for (DynamicBiomeRegistry.BiomeEntry entry : entries) {
            if (entry.id().toString().equals(id)) return entry;
        }
        return null;
    }

    private static void writePack(Path root) throws IOException {
        write(root.resolve("pack.mcmeta"),
            "{\n  \"pack\": {\n    \"pack_format\": 121,\n"
                + "    \"description\": \"OneIsle Phase 9.7 dynamic biome fixture\"\n  }\n}\n");
        Path biomeDir = root.resolve("data/oneisle_test/worldgen/biome");
        Files.createDirectories(biomeDir);
        String biomeJson = "{\n"
            + "  \"attributes\": {},\n"
            + "  \"carvers\": [],\n"
            + "  \"downfall\": 0.4,\n"
            + "  \"effects\": {\"water_color\": \"#3f76e4\"},\n"
            + "  \"features\": [[], [], [], [], [], [], [], [], [], [], []],\n"
            + "  \"has_precipitation\": true,\n"
            + "  \"temperature\": 0.8\n"
            + "}\n";
        write(biomeDir.resolve("custom_beach.json"), biomeJson);
        write(biomeDir.resolve("custom_caves.json"), biomeJson);
    }

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }
}
