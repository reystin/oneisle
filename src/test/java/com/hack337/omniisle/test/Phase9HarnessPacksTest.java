package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.WorldPackBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Exports the 45 structure-report packs used by the real-server harness.
 * Disabled unless OMNIISLE_HARNESS_DIR is set, so normal test runs do not write
 * temporary worlds.
 */
public class Phase9HarnessPacksTest {
    private static final IslandDraft.Preset[] PRESETS = {
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
    };
    private static final int[] SIZES = {2500, 4000, 8000};

    @Test
    void exportHarnessPacks() throws IOException {
        String dir = System.getenv("OMNIISLE_HARNESS_DIR");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), "OMNIISLE_HARNESS_DIR not set");
        Path root = Path.of(dir);
        Files.createDirectories(root.resolve("packs"));
        StringBuilder configs = new StringBuilder("config,kind,preset,size,seed,marker_x,marker_z\n");

        for (IslandDraft.Preset preset : PRESETS) {
            for (int size : SIZES) {
                for (long seed : new long[]{101L, 202L, 303L}) {
                    IslandDraft draft = draft(preset, size, seed);
                    String key = preset.name() + "_" + size + "_" + seed;
                    export(root, key, draft);
                    configs.append(key).append(",structure,").append(preset.name()).append(',')
                        .append(size).append(',').append(seed).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", draft.worldSpawnX())).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", draft.worldSpawnZ())).append('\n');
                }
            }
        }
        Files.writeString(root.resolve("configs.csv"), configs.toString(), StandardCharsets.UTF_8);
        System.out.println("Exported harness packs to " + root.toAbsolutePath());
    }

    private static void export(Path root, String key, IslandDraft draft) throws IOException {
        Path packDir = root.resolve("packs").resolve(key);
        Files.createDirectories(packDir);
        WorldPackBuilder.export(packDir, draft);
    }

    private static IslandDraft draft(IslandDraft.Preset preset, int size, long seed) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(0.25);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }
}
