package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class StructurePlacementHarness {

    public record StructureDef(String name, String optionName, Set<BiomePreview.Type> biomes, boolean deepDark) {}

    public static final List<StructureDef> TARGET_STRUCTURES = List.of(
        new StructureDef("stronghold", "STRONGHOLD", EnumSet.of(BiomePreview.Type.PLAINS), false),
        new StructureDef("ancient_city", "ANCIENT_CITY", EnumSet.of(BiomePreview.Type.STONY_PEAKS, BiomePreview.Type.JAGGED_PEAKS), true),
        new StructureDef("ocean_monument", "OCEAN_MONUMENT", EnumSet.of(BiomePreview.Type.DEEP_OCEAN), false),
        new StructureDef("village", "VILLAGE", EnumSet.of(BiomePreview.Type.PLAINS), false),
        new StructureDef("trial_chambers", "TRIAL_CHAMBERS", EnumSet.of(BiomePreview.Type.PLAINS), false),
        new StructureDef("mansion", "MANSION", EnumSet.of(BiomePreview.Type.DARK_FOREST), false),
        new StructureDef("shipwreck", "SHIPWRECK", EnumSet.of(BiomePreview.Type.OCEAN), false),
        new StructureDef("ocean_ruin", "OCEAN_RUINS", EnumSet.of(BiomePreview.Type.COLD_OCEAN), false),
        new StructureDef("abandoned_camp", "ABANDONED_CAMP", EnumSet.of(BiomePreview.Type.FOREST), false)
    );

    public record EvalResult(String configKey, String structure, boolean found, double distance) {}

    public static List<EvalResult> evaluateLegacy(IslandDraft.Preset preset, int islandSize, long seed) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(islandSize);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V1);
        draft.rebuildShape();

        double halfSpan = draft.worldSpan() * 0.5;
        double coastWidth = draft.relief().coastWidth;
        IslandMask mask = new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan,
            coastWidth, 400.0, draft.packedMask());

        int minChunk = (int) Math.floor(-halfSpan / 16.0);
        int maxChunk = (int) Math.ceil(halfSpan / 16.0);
        int width = maxChunk - minChunk + 1;

        record Sample(double continentalness, BiomePreview.Type biome, boolean deepDark) {}
        Sample[] samples = new Sample[width * width];
        for (int cz = minChunk; cz <= maxChunk; cz++) {
            for (int cx = minChunk; cx <= maxChunk; cx++) {
                int x = cx * 16 + 8;
                int z = cz * 16 + 8;
                double continentalness = mask.sampleTerrainValue(x, z);
                BiomePreview.Type biome = draft.selectBiome(
                    BiomePreview.surface(continentalness, x, z, draft.biomeSeed()), x, z);
                samples[(cz - minChunk) * width + cx - minChunk] =
                    new Sample(continentalness, biome, BiomePreview.isDeepDark(x, z, draft.biomeSeed()));
            }
        }

        double spawnX = 0.0;
        double spawnZ = 0.0;

        List<EvalResult> results = new ArrayList<>();
        List<int[]> used = new ArrayList<>();

        String configKey = preset.name() + "_" + islandSize + "_s" + seed;

        for (StructureDef def : TARGET_STRUCTURES) {
            Set<BiomePreview.Type> accepted = EnumSet.noneOf(BiomePreview.Type.class);
            def.biomes.forEach(type -> accepted.addAll(draft.compatibleChoices(type)));

            int bestCx = Integer.MIN_VALUE;
            int bestCz = Integer.MIN_VALUE;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int cz = minChunk; cz <= maxChunk; cz++) {
                for (int cx = minChunk; cx <= maxChunk; cx++) {
                    Sample sample = samples[(cz - minChunk) * width + cx - minChunk];
                    if (!accepted.contains(sample.biome)) continue;
                    if (def.deepDark && !sample.deepDark) continue;

                    boolean tooClose = false;
                    for (int[] other : used) {
                        int dx = cx - other[0];
                        int dz = cz - other[1];
                        if (dx * dx + dz * dz < 64) { tooClose = true; break; }
                    }
                    if (tooClose) continue;

                    boolean oceanOrShore = sample.biome.name().contains("OCEAN") || sample.biome == BiomePreview.Type.BEACH;
                    double score = oceanOrShore ? -Math.abs(sample.continentalness + 0.40) : sample.continentalness;
                    score -= (Math.abs(cx) + Math.abs(cz)) * 0.00001;

                    if (score > bestScore) {
                        bestScore = score;
                        bestCx = cx;
                        bestCz = cz;
                    }
                }
            }

            if (bestCx != Integer.MIN_VALUE) {
                used.add(new int[]{bestCx, bestCz});
                double dist = Math.hypot(bestCx * 16.0 + 8.0 - spawnX, bestCz * 16.0 + 8.0 - spawnZ);
                results.add(new EvalResult(configKey, def.name, true, dist));
            } else {
                results.add(new EvalResult(configKey, def.name, false, -1.0));
            }
        }

        return results;
    }

    public static List<EvalResult> evaluateModern(IslandDraft.Preset preset, int islandSize, long seed, int generationVersion) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(islandSize);
        draft.setGenerationVersion(generationVersion == 1 ? IslandDraft.GenerationVersion.V1 : IslandDraft.GenerationVersion.V2);
        draft.rebuildShape();

        double halfSpan = draft.worldSpan() * 0.5;
        double coastWidth = draft.relief().coastWidth;
        int resolution = IslandDraft.RESOLUTION;

        IslandShapeConfig cfg = new IslandShapeConfig(
            generationVersion,
            draft.isletDensity(),
            draft.hasCustomHeight()
                ? Optional.of(new IslandShapeConfig.HeightLayer(2, draft.packedHeight()))
                : Optional.empty(),
            Collections.emptyList(),
            Optional.of(new IslandMask(
                resolution, resolution,
                -halfSpan, halfSpan, -halfSpan, halfSpan,
                coastWidth, 400.0, draft.packedMask()
            )),
            draft.biomeSeed()
        );
        IslandTerrain terrain = IslandTerrain.create(cfg);

        int minChunk = (int) Math.floor(-halfSpan / 16.0);
        int maxChunk = (int) Math.ceil(halfSpan / 16.0);
        int width = maxChunk - minChunk + 1;

        record Sample(double continentalness, BiomePreview.Type biome, boolean deepDark) {}
        Sample[] samples = new Sample[width * width];
        for (int cz = minChunk; cz <= maxChunk; cz++) {
            for (int cx = minChunk; cx <= maxChunk; cx++) {
                int x = cx * 16 + 8;
                int z = cz * 16 + 8;
                double c = terrain.continentalness(x, z);
                BiomePreview.Type biome = draft.selectBiome(
                    BiomePreview.surface(c, x, z, draft.biomeSeed()), x, z);
                samples[(cz - minChunk) * width + cx - minChunk] =
                    new Sample(c, biome, BiomePreview.isDeepDark(x, z, draft.biomeSeed()));
            }
        }

        int[] sp = draft.effectiveSpawnPx();
        double spawnX = draft.worldX(sp[0]);
        double spawnZ = draft.worldZ(sp[1]);

        List<EvalResult> results = new ArrayList<>();
        List<int[]> used = new ArrayList<>();
        String configKey = preset.name() + "_" + islandSize + "_s" + seed + "_v" + generationVersion;

        for (StructureDef def : TARGET_STRUCTURES) {
            if ("stronghold".equals(def.name)) {
                if (generationVersion >= 2) {
                    int scaledDistance = Math.max(8, Math.min(32, (int) Math.round(32.0 * islandSize / 8000.0)));
                    double ringRadius = scaledDistance * 64.0;
                    double distFromSpawn = Math.abs(ringRadius - Math.hypot(spawnX, spawnZ));
                    boolean onIsland = ringRadius < halfSpan * 0.85;
                    results.add(new EvalResult(configKey, def.name, onIsland, distFromSpawn));
                } else {
                    double ringRadius = 32.0 * 64.0;
                    boolean onIsland = ringRadius < halfSpan * 0.85;
                    double distFromSpawn = Math.abs(ringRadius - Math.hypot(spawnX, spawnZ));
                    results.add(new EvalResult(configKey, def.name, onIsland, distFromSpawn));
                }
                continue;
            }

            Set<BiomePreview.Type> accepted = EnumSet.noneOf(BiomePreview.Type.class);
            def.biomes.forEach(type -> accepted.addAll(draft.compatibleChoices(type)));

            int bestCx = Integer.MIN_VALUE;
            int bestCz = Integer.MIN_VALUE;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int cz = minChunk; cz <= maxChunk; cz++) {
                for (int cx = minChunk; cx <= maxChunk; cx++) {
                    Sample sample = samples[(cz - minChunk) * width + cx - minChunk];
                    if (!accepted.contains(sample.biome)) continue;
                    if (def.deepDark && !sample.deepDark) continue;
                    if (("trial_chambers".equals(def.name) || "mineshafts".equals(def.name)) && sample.deepDark) continue;

                    boolean tooClose = false;
                    for (int[] other : used) {
                        int dx = cx - other[0];
                        int dz = cz - other[1];
                        if (dx * dx + dz * dz < 64) { tooClose = true; break; }
                    }
                    if (tooClose) continue;

                    boolean oceanOrShore = sample.biome.name().contains("OCEAN") || sample.biome == BiomePreview.Type.BEACH;
                    double score = oceanOrShore ? -Math.abs(sample.continentalness + 0.40) : sample.continentalness;
                    double distFromSpawn = Math.hypot(cx * 16.0 + 8.0 - spawnX, cz * 16.0 + 8.0 - spawnZ);
                    score -= distFromSpawn * 0.00005;

                    if (score > bestScore) {
                        bestScore = score;
                        bestCx = cx;
                        bestCz = cz;
                    }
                }
            }

            if (bestCx == Integer.MIN_VALUE) {
                Set<BiomePreview.Type> fallback = EnumSet.noneOf(BiomePreview.Type.class);
                BiomePreview.Category cat = def.biomes.iterator().next().category;
                fallback.addAll(draft.enabledBiomes(cat));

                for (int cz = minChunk; cz <= maxChunk; cz++) {
                    for (int cx = minChunk; cx <= maxChunk; cx++) {
                        Sample sample = samples[(cz - minChunk) * width + cx - minChunk];
                        if (!fallback.contains(sample.biome)) continue;
                        boolean tooClose = false;
                        for (int[] other : used) {
                            int dx = cx - other[0];
                            int dz = cz - other[1];
                            if (dx * dx + dz * dz < 64) { tooClose = true; break; }
                        }
                        if (tooClose) continue;
                        boolean oceanOrShore = sample.biome.name().contains("OCEAN") || sample.biome == BiomePreview.Type.BEACH;
                        double score = oceanOrShore ? -Math.abs(sample.continentalness + 0.40) : sample.continentalness;
                        double distFromSpawn = Math.hypot(cx * 16.0 + 8.0 - spawnX, cz * 16.0 + 8.0 - spawnZ);
                        score -= distFromSpawn * 0.00005;

                        if (score > bestScore) {
                            bestScore = score;
                            bestCx = cx;
                            bestCz = cz;
                        }
                    }
                }
            }

            if (bestCx != Integer.MIN_VALUE) {
                used.add(new int[]{bestCx, bestCz});
                double dist = Math.hypot(bestCx * 16.0 + 8.0 - spawnX, bestCz * 16.0 + 8.0 - spawnZ);
                results.add(new EvalResult(configKey, def.name, true, dist));
            } else {
                results.add(new EvalResult(configKey, def.name, false, -1.0));
            }
        }

        return results;
    }

    public static void generateReportBefore(String outputPath) throws IOException {
        IslandDraft.Preset[] presets = {
            IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
            IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
        };
        int[] sizes = {2500, 4000, 8000};
        long[] seeds = {101L, 202L, 303L};

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("preset,size,seed,structure,found,distance_from_spawn");

            for (IslandDraft.Preset preset : presets) {
                for (int size : sizes) {
                    for (long seed : seeds) {
                        List<EvalResult> results = evaluateLegacy(preset, size, seed);
                        for (EvalResult r : results) {
                            writer.printf(Locale.ROOT, "%s,%d,%d,%s,%b,%.1f%n",
                                preset.name(), size, seed, r.structure, r.found, r.distance);
                        }
                    }
                }
            }
        }
    }

    public static void generateReportAfter(String outputPath) throws IOException {
        IslandDraft.Preset[] presets = {
            IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
            IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
        };
        int[] sizes = {2500, 4000, 8000};
        long[] seeds = {101L, 202L, 303L};

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("preset,size,seed,structure,found,distance_from_spawn");

            for (IslandDraft.Preset preset : presets) {
                for (int size : sizes) {
                    for (long seed : seeds) {
                        List<EvalResult> results = evaluateModern(preset, size, seed, 2);
                        for (EvalResult r : results) {
                            writer.printf(Locale.ROOT, "%s,%d,%d,%s,%b,%.1f%n",
                                preset.name(), size, seed, r.structure, r.found, r.distance);
                        }
                    }
                }
            }
        }
    }
}
