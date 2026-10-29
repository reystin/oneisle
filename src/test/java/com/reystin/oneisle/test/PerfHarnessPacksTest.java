package com.reystin.oneisle.test;

import com.reystin.oneisle.client.BiomePreview;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.WorldPackBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exports the four chunk-generation timing packs used by
 * {@code tools/perf/chunkgen_25x25.py}: v1, v2, v3 all-100 and v3 Cherry-45.
 * Disabled unless ONEISLE_PERF_DIR is set, so normal test runs stay fast.
 */
public class PerfHarnessPacksTest {
    private static final String PRESET_ENTRY = "data/oneisle/worldgen/world_preset/oneisle.json";

    @Test
    void exportPerfPacks() throws Exception {
        String dir = System.getenv("ONEISLE_PERF_DIR");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), "ONEISLE_PERF_DIR not set");
        Path root = Path.of(dir).resolve("packs");
        Files.createDirectories(root);
        StringBuilder manifest = new StringBuilder(
            "variant,generation_version,cherry_weight,island_size,shape_seed,biome_seed,preset,"
                + "river_density,islet_density,shore_density,shore_variety,shore_rocky,structure_density,sha256\n");

        exportVariant(root, "v1", IslandDraft.GenerationVersion.V1, 0, manifest);
        exportVariant(root, "v2", IslandDraft.GenerationVersion.V2, 0, manifest);
        exportVariant(root, "v3-neutral", IslandDraft.GenerationVersion.V3, 100, manifest);
        exportVariant(root, "v3-weighted", IslandDraft.GenerationVersion.V3, 45, manifest);

        Files.writeString(Path.of(dir).resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        System.out.println("Exported timing packs to " + root.toAbsolutePath());
    }

    private static void exportVariant(Path root, String name, IslandDraft.GenerationVersion version,
                                      int cherryWeight, StringBuilder manifest) throws Exception {
        IslandDraft draft = draft(version, cherryWeight);
        Path packDir = root.resolve(name);
        Files.createDirectories(packDir);
        WorldPackBuilder.export(packDir, draft);
        Path zip = packDir.resolve(WorldPackBuilder.FILE_NAME);
        boolean weighted = version == IslandDraft.GenerationVersion.V3 && cherryWeight != 100;
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry preset = file.getEntry(PRESET_ENTRY);
            assertTrue(preset != null, name + " is missing the world preset");
            String json;
            try (var input = file.getInputStream(preset)) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(json.contains("oneisle:island_biomes"), name + " has the wrong biome source namespace");
            if (weighted) {
                assertTrue(json.contains("\"biome_share\""), name + " has no biome_share");
            } else {
                assertFalse(json.contains("\"biome_share\""), name + " should stay neutral");
            }
        }
        manifest.append(name).append(',')
            .append(version.version).append(',')
            .append(version == IslandDraft.GenerationVersion.V3 ? cherryWeight : 0).append(',')
            .append(4000).append(',')
            .append(4242).append(',')
            .append(4242).append(',')
            .append(IslandDraft.Preset.ROUND.name()).append(',')
            .append("0.35,0.25,0.30,0.60,true,1.0,")
            .append(sha256(zip)).append('\n');
    }

    private static IslandDraft draft(IslandDraft.GenerationVersion version, int cherryWeight) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setIslandSize(4000);
        draft.setShapeSeed(4242);
        draft.setBiomeSeed(4242);
        draft.setGenerationVersion(version);
        draft.setIsletDensity(0.25);
        draft.setRiverDensity(0.35);
        draft.setStructureDensity(1.0);
        if (version == IslandDraft.GenerationVersion.V3 && cherryWeight != 100) {
            draft.setBiomeWeight(BiomePreview.Type.CHERRY_GROVE, cherryWeight);
        }
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
