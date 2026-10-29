package com.reystin.oneisle.test;

import com.reystin.oneisle.OneIsleIds;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.ShareCode;
import com.reystin.oneisle.client.WorldPackBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneIsleIdentityTest {
    private static final String OLD_NAMESPACE = "omniisle";

    @Test
    @DisplayName("15.1 Identity constants expose the OneIsle runtime identity")
    void identityConstants() {
        assertEquals("oneisle", OneIsleIds.MOD_ID);
        assertEquals("OneIsle — All Biomes & Structures on One Island", OneIsleIds.DISPLAY_NAME);
        assertEquals("reystin", OneIsleIds.AUTHOR);
        assertEquals("https://github.com/reystin/oneisle", OneIsleIds.SOURCES);
        assertEquals("oneisle", OneIsleIds.id("spawn").getNamespace());
        assertEquals("oneisle:spawn", OneIsleIds.id("spawn").toString());
    }

    @Test
    @DisplayName("15.2 Runtime resources contain no OmniIsle identifiers")
    void runtimeResourcesUseOneIsleNamespace() throws IOException {
        Path resources = Path.of("src/main/resources");
        assertTrue(Files.isDirectory(resources), "runtime resource directory must exist");
        try (Stream<Path> paths = Files.walk(resources)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String relative = resources.relativize(file).toString().replace('\\', '/');
                assertFalse(relative.toLowerCase().contains(OLD_NAMESPACE),
                        "old namespace in resource path: " + relative);
                if (relative.endsWith(".json") || relative.endsWith(".txt") || relative.endsWith(".md")) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    assertFalse(text.contains(OLD_NAMESPACE),
                            "old namespace in resource content: " + relative);
                }
            }
        }
    }

    @Test
    @DisplayName("15.3 Generated datapack is namespaced under oneisle")
    void generatedPackUsesOneIsleNamespace(@TempDir Path tempDir) throws IOException {
        IslandDraft draft = new IslandDraft(192);
        draft.setIslandSize(4000);
        WorldPackBuilder.export(tempDir, draft);
        Path packZip = tempDir.resolve(WorldPackBuilder.FILE_NAME);
        assertTrue(Files.exists(packZip), "datapack ZIP must be generated");

        try (ZipFile zip = new ZipFile(packZip.toFile())) {
            assertNotNull(zip.getEntry("data/oneisle/worldgen/world_preset/oneisle.json"),
                    "world preset must live under data/oneisle");
            String preset = read(zip, "data/oneisle/worldgen/world_preset/oneisle.json");
            assertTrue(preset.contains("\"oneisle:island_biomes\""), "biome source must use oneisle");
            assertTrue(preset.contains("\"oneisle:one_terrain\""), "noise settings must use oneisle");
            String continents = read(zip, "data/oneisle/worldgen/density_function/overworld/continents.json");
            assertTrue(continents.contains("\"oneisle:island_continents\""),
                    "density function must use oneisle");

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                assertFalse(entry.getName().contains(OLD_NAMESPACE),
                        "old namespace in pack entry: " + entry.getName());
                if (entry.getName().endsWith(".json")) {
                    assertFalse(read(zip, entry.getName()).contains(OLD_NAMESPACE),
                            "old namespace in pack content: " + entry.getName());
                }
            }
        }
    }

    @Test
    @DisplayName("15.4 Share codes use the ONE2 prefix and reject legacy codes")
    void shareCodePrefixIsOneTwo() {
        assertEquals("ONE2-", ShareCode.PREFIX);
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("OMNI2-AAAA-0000"));
    }

    private static String read(ZipFile zip, String name) throws IOException {
        try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
