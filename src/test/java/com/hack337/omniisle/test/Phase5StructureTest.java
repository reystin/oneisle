package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase5StructureTest {

    private static final IslandDraft.Preset[] BASE_PRESETS = {
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
    };

    @Test
    @DisplayName("5.1 Spawn is on land in 100% of 90 configs")
    void testSpawnOnLandForAllConfigs() {
        IslandDraft.Preset[] presets = BASE_PRESETS;
        int[] sizes = {2500, 4000, 8000};
        long[] seeds = {101L, 202L, 303L};
        int[] versions = {1, 2};

        int totalConfigs = 0;
        int onLandCount = 0;

        for (IslandDraft.Preset preset : presets) {
            for (int size : sizes) {
                for (long seed : seeds) {
                    for (int v : versions) {
                        totalConfigs++;
                        IslandDraft draft = new IslandDraft();
                        draft.setPreset(preset);
                        draft.setIslandSize(size);
                        draft.setGenerationVersion(v == 1 ? IslandDraft.GenerationVersion.V1 : IslandDraft.GenerationVersion.V2);
                        draft.rebuildShape();

                        int[] sp = draft.effectiveSpawnPx();
                        assertNotNull(sp, "Spawn pos should not be null");
                        if (draft.isLand(sp[0], sp[1])) {
                            onLandCount++;
                        } else {
                            fail("Spawn at (" + sp[0] + ", " + sp[1] + ") is NOT on land for "
                                + preset + " size=" + size + " seed=" + seed + " v=" + v);
                        }
                    }
                }
            }
        }

        assertEquals(90, totalConfigs, "Should test exactly 90 configs (5 presets * 3 sizes * 3 seeds * 2 versions)");
        assertEquals(90, onLandCount, "100% of spawn locations must be on land");
    }

    @Test
    @DisplayName("5.2 Stronghold guaranteed on size 2500 in v2")
    void testStrongholdFoundOnSize2500() {
        IslandDraft.Preset[] presets = BASE_PRESETS;
        long[] seeds = {101L, 202L, 303L};

        for (IslandDraft.Preset preset : presets) {
            for (long seed : seeds) {
                List<StructurePlacementHarness.EvalResult> results =
                    StructurePlacementHarness.evaluateModern(preset, 2500, seed, 2);
                StructurePlacementHarness.EvalResult stronghold = results.stream()
                    .filter(r -> "stronghold".equals(r.structure()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing stronghold evaluation"));

                assertTrue(stronghold.found(), "Stronghold must be found on land for size 2500 in v2: "
                    + preset + " seed=" + seed);
            }
        }
    }

    @Test
    @DisplayName("5.3 Non-ocean structures within 5000 blocks in >= 95% of configs (size >= 4000)")
    void testNonOceanStructuresWithinReach() {
        IslandDraft.Preset[] presets = BASE_PRESETS;
        int[] sizes = {4000, 8000};
        long[] seeds = {101L, 202L, 303L};

        int totalEvaluated = 0;
        int successful = 0;

        for (IslandDraft.Preset preset : presets) {
            for (int size : sizes) {
                for (long seed : seeds) {
                    List<StructurePlacementHarness.EvalResult> results =
                        StructurePlacementHarness.evaluateModern(preset, size, seed, 2);
                    for (StructurePlacementHarness.EvalResult r : results) {
                        // Skip ocean-only structures
                        if ("ocean_monument".equals(r.structure()) || "shipwreck".equals(r.structure()) || "ocean_ruin".equals(r.structure())) {
                            continue;
                        }
                        totalEvaluated++;
                        if (r.found() && r.distance() <= 5000.0) {
                            successful++;
                        }
                    }
                }
            }
        }

        double successRate = (double) successful / (double) totalEvaluated;
        assertTrue(successRate >= 0.95, "Success rate of non-ocean structures within 5000 blocks was "
            + (successRate * 100) + "%, expected >= 95%");
    }

    @Test
    @DisplayName("5.4 Generate STRUCTURE_REPORT_before.csv and STRUCTURE_REPORT_after.csv")
    void testGenerateReports() throws IOException {
        String beforeFilename = "STRUCTURE_REPORT_before.csv";
        StructurePlacementHarness.generateReportBefore(beforeFilename);
        File beforeFile = new File(beforeFilename);
        assertTrue(beforeFile.exists() && beforeFile.length() > 0);
        List<String> beforeLines = Files.readAllLines(Paths.get(beforeFilename));
        assertEquals(406, beforeLines.size(), "Before report should contain 406 rows");

        String afterFilename = "STRUCTURE_REPORT_after.csv";
        StructurePlacementHarness.generateReportAfter(afterFilename);
        File afterFile = new File(afterFilename);
        assertTrue(afterFile.exists() && afterFile.length() > 0);
        List<String> afterLines = Files.readAllLines(Paths.get(afterFilename));
        assertEquals(406, afterLines.size(), "After report should contain 406 rows");
    }
}
