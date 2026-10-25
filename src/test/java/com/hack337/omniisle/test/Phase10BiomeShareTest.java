package com.hack337.omniisle.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.BiomeShareCalibrator;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.ShareCode;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.BiomeShare;
import com.hack337.omniisle.continent.ShoreField;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.mojang.serialization.JsonOps;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Phase10BiomeShareTest {
    private static final long SHAPE_SEED = 1337L;
    private static final long BIOME_SEED = 4242L;
    private static final List<IslandDraft.Preset> PRESETS = List.of(
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.CRESCENT,
        IslandDraft.Preset.ARCHIPELAGO, IslandDraft.Preset.FJORDS);
    private static final List<IslandDraft.BiomeTheme> THEMES = List.of(
        IslandDraft.BiomeTheme.ALL, IslandDraft.BiomeTheme.TROPICAL, IslandDraft.BiomeTheme.NORDIC);
    private static final Path EVIDENCE_DIR = Path.of("build", "reports", "phase10");
    private static final Path DOCS_EVIDENCE_DIR = Path.of("docs", "evidence", "phase10");

    @BeforeAll
    static void boot() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("10.1 all-100 is bit-identical to the absent field")
    void allHundredIsBitIdentical() throws Exception {
        IslandDraft draft = draft(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.ALL);
        OmniBiomeSource baseSource = sourceFor(draft, null);
        Map<String, Integer> allHundred = new LinkedHashMap<>();
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (type.isLand()) allHundred.put(type.id, 100);
        }
        BiomeShare.Settings explicit = BiomeShare.Settings.fromMaps(
            allHundred, Map.of(), Map.of(), Map.of(), true).sanitized();
        OmniBiomeSource explicitSource = sourceFor(draft, explicit);
        assertFalse(baseSource.hasWeightedShares());
        assertFalse(explicitSource.hasWeightedShares());

        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        Climate.Sampler sampler = TestWorldgen.sampler(terrain);
        IslandDensityFunction previousActive = IslandDensityFunction.getActive();
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        try {
            long baseHash = sourceHash(baseSource, sampler, draft, 64);
            long explicitHash = sourceHash(explicitSource, sampler, draft, 64);
            System.out.println("10.1 hash absent=" + baseHash + " explicit100=" + explicitHash);
            assertEquals(baseHash, explicitHash, "all-100 biome_share must not change worldgen output");
        } finally {
            IslandDensityFunction.setActive(previousActive);
        }

        Path tmp = Files.createTempDirectory("omniisle-share-all100-");
        WorldPackBuilder.export(tmp, draft);
        assertFalse(presetBiomeSource(tmp).has("biome_share"),
            "neutral weights must not write the optional field");
    }

    @Test
    @DisplayName("10.2 calibration: 5 presets x 3 themes x 3 feasible vectors within 3 pp")
    void calibrationConverges() throws Exception {
        Files.createDirectories(EVIDENCE_DIR);
        StringBuilder csv = new StringBuilder(
            "preset,theme,vector,samples,iterations,max_error_pp,feasible,weights\n");
        StringBuilder examples = new StringBuilder(
            "preset,theme,biome,weight,target_pct,achieved_pct,error_pp\n");
        for (IslandDraft.Preset preset : PRESETS) {
            for (IslandDraft.BiomeTheme theme : THEMES) {
                IslandDraft probe = draft(preset, theme);
                Map<String, Double> baseline = BiomeShareCalibrator.calibrate(probe).target();
                List<BiomePreview.Type> eligible = eligibleBiomes(probe, baseline);
                assertTrue(eligible.size() >= 3, preset + "/" + theme + " has too few reachable biomes");
                List<Map<BiomePreview.Type, Integer>> vectors = feasibleVectors(eligible, seed(preset, theme));
                for (int v = 0; v < vectors.size(); v++) {
                    IslandDraft calibrated = draft(preset, theme);
                    vectors.get(v).forEach(calibrated::setBiomeWeight);
                    BiomeShareCalibrator.Result result = BiomeShareCalibrator.calibrate(calibrated);
                    assertTrue(result.feasible(), preset + "/" + theme + "/v" + v
                        + " should be feasible, maxErr=" + result.maxErrorPp());
                    assertTrue(result.maxErrorPp() <= 3.0, preset + "/" + theme + "/v" + v
                        + " max error " + result.maxErrorPp() + " pp");
                    csv.append(preset).append(',').append(theme).append(',').append(v).append(',')
                        .append(result.samples()).append(',').append(result.iterations()).append(',')
                        .append(String.format(Locale.ROOT, "%.3f", result.maxErrorPp())).append(',')
                        .append(result.feasible()).append(',')
                        .append(vectors.get(v)).append('\n');
                    if (v == 0 && isExample(preset, theme)) {
                        for (Map.Entry<String, Double> target : result.target().entrySet()) {
                            double achieved = result.achieved().getOrDefault(target.getKey(), 0.0);
                            int weight = vectors.get(v).entrySet().stream()
                                .filter(e -> e.getKey().id.equals(target.getKey()))
                                .map(Map.Entry::getValue).findFirst().orElse(100);
                            examples.append(preset).append(',').append(theme).append(',')
                                .append(target.getKey()).append(',').append(weight).append(',')
                                .append(String.format(Locale.ROOT, "%.3f", target.getValue())).append(',')
                                .append(String.format(Locale.ROOT, "%.3f", achieved)).append(',')
                                .append(String.format(Locale.ROOT, "%.3f",
                                    Math.abs(achieved - target.getValue()))).append('\n');
                        }
                    }
                }
            }
        }
        Files.writeString(EVIDENCE_DIR.resolve("calibration_examples.csv"), examples.toString(),
            StandardCharsets.UTF_8);

        IslandDraft unreachable = draft(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.NORDIC);
        unreachable.setBiomeWeight(BiomePreview.Type.DESERT, 200);
        BiomeShareCalibrator.Result impossible = BiomeShareCalibrator.calibrate(unreachable);
        assertFalse(impossible.feasible(), "out-of-theme biome must be flagged unreachable");
        assertTrue(impossible.achieved().values().stream().allMatch(v -> v >= 0.0 && v <= 100.0));
        unreachable.applyCalibration(impossible);
        assertNotNull(unreachable.selectPreviewBiome(sampleSelection(unreachable, 0.0, 0.0, 0.0), 0, 0));

        Files.writeString(EVIDENCE_DIR.resolve("calibration.csv"), csv.toString(), StandardCharsets.UTF_8);
        Files.createDirectories(DOCS_EVIDENCE_DIR);
        Files.writeString(DOCS_EVIDENCE_DIR.resolve("calibration_examples.csv"), examples.toString(),
            StandardCharsets.UTF_8);
        Files.writeString(DOCS_EVIDENCE_DIR.resolve("calibration.csv"), csv.toString(),
            StandardCharsets.UTF_8);
    }

    private static boolean isExample(IslandDraft.Preset preset, IslandDraft.BiomeTheme theme) {
        return (preset == IslandDraft.Preset.NATURAL && theme == IslandDraft.BiomeTheme.ALL)
            || (preset == IslandDraft.Preset.ROUND && theme == IslandDraft.BiomeTheme.NORDIC)
            || (preset == IslandDraft.Preset.ARCHIPELAGO && theme == IslandDraft.BiomeTheme.TROPICAL);
    }

    @Test
    @DisplayName("10.3 monotonic: raising one weight never lowers its achieved share")
    void monotonicWeights() {
        IslandDraft probe = draft(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.ALL);
        Map<String, Double> baseline = BiomeShareCalibrator.calibrate(probe).target();
        BiomePreview.Type target = eligibleBiomes(probe, baseline).stream()
            .max(Comparator.comparingDouble(type -> baseline.getOrDefault(type.id, 0.0)))
            .orElseThrow();
        double previous = -1.0;
        StringBuilder trace = new StringBuilder(target.id + ":");
        for (int weight : new int[] {0, 50, 100, 200, 400}) {
            IslandDraft calibrated = draft(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.ALL);
            calibrated.setBiomeWeight(target, weight);
            BiomeShareCalibrator.Result result = BiomeShareCalibrator.calibrate(calibrated);
            double achieved = weight == 0 ? 0.0 : result.achieved().getOrDefault(target.id, 0.0);
            trace.append(' ').append(weight).append("->")
                .append(String.format(Locale.ROOT, "%.2f", achieved));
            assertTrue(achieved >= previous - 1.0e-9,
                "weight " + weight + " lowered " + target.id + " from " + previous + " to " + achieved);
            previous = achieved;
        }
        System.out.println("10.3 monotonic " + trace);
    }

    @Test
    @DisplayName("10.4 weight 0 is equivalent to unticking; re-enabling restores 100")
    void zeroEqualsDisabled() {
        IslandDraft zero = draft(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.ALL);
        zero.setBiomeWeight(BiomePreview.Type.PLAINS, 0);
        IslandDraft disabled = draft(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.ALL);
        disabled.toggleBiome(BiomePreview.Type.PLAINS);
        assertFalse(zero.isBiomeEnabled(BiomePreview.Type.PLAINS));
        assertFalse(disabled.isBiomeEnabled(BiomePreview.Type.PLAINS));
        assertEquals(disabled.enabledBiomes().values(), zero.enabledBiomes().values());
        assertEquals(previewHash(zero), previewHash(disabled), "zero weight must match disabled output");
        assertEquals(BiomeShareCalibrator.calibrate(zero).bias(), BiomeShareCalibrator.calibrate(disabled).bias());

        zero.setBiomeWeight(BiomePreview.Type.PLAINS, 100);
        IslandDraft fresh = draft(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.ALL);
        assertTrue(zero.isBiomeEnabled(BiomePreview.Type.PLAINS));
        assertEquals(100, zero.biomeWeight(BiomePreview.Type.PLAINS));
        assertEquals(previewHash(fresh), previewHash(zero), "re-enabled biome must return to the 100 path");
    }

    @Test
    @DisplayName("10.5 determinism: identical bias and output across 8 threads")
    void deterministicAcrossThreads() throws Exception {
        IslandDraft probe = draft(IslandDraft.Preset.CRESCENT, IslandDraft.BiomeTheme.ALL);
        Map<String, Double> baseline = BiomeShareCalibrator.calibrate(probe).target();
        List<BiomePreview.Type> eligible = eligibleBiomes(probe, baseline);
        IslandDraft weighted = draft(IslandDraft.Preset.CRESCENT, IslandDraft.BiomeTheme.ALL);
        Map<BiomePreview.Type, Integer> vector = feasibleVectors(eligible, 99L).get(0);
        vector.forEach(weighted::setBiomeWeight);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<BiomeShareCalibrator.Result>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                IslandDraft copy = weighted.copy();
                futures.add(pool.submit(() -> BiomeShareCalibrator.calibrate(copy)));
            }
            Set<String> fingerprints = new HashSet<>();
            for (Future<BiomeShareCalibrator.Result> future : futures) {
                BiomeShareCalibrator.Result result = future.get();
                fingerprints.add(result.bias() + "|" + result.achieved() + "|" + result.target()
                    + "|" + result.maxErrorPp() + "|" + result.iterations());
            }
            assertEquals(1, fingerprints.size(), "8 concurrent calibrations diverged");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("10.6 climate coherence: 20,000 extreme-weight samples stay inside R_max")
    void climateCoherence() {
        int[] ids = new int[18];
        double[] factors = new double[18];
        int count = 0;
        for (int family = 0; family < 18; family++) {
            if (BiomeShare.isTerrainDriven(family)) continue;
            int index = -1;
            for (int i = 0; i < BiomeShare.CATALOG_SIZE; i++) {
                BiomeShare.Variant variant = BiomeShare.variant(i);
                if (variant != null && variant.family() == family && !variant.terrainDriven()) {
                    index = i;
                    break;
                }
            }
            if (index < 0) continue;
            ids[count] = index;
            factors[count] = family == 12 ? Math.exp(BiomeShare.MAX_BIAS) : Math.exp(BiomeShare.MIN_BIAS);
            count++;
        }
        BiomeShare.SelectorScratch scratch = new BiomeShare.SelectorScratch();
        Random rng = new Random(0x5EED10L);
        double maxDistance = 0.0;
        int beyond = 0;
        int noAlternative = 0;
        for (int i = 0; i < 20_000; i++) {
            int preferred = rng.nextInt(17);
            if (BiomeShare.isTerrainDriven(preferred)) preferred = 4;
            double temperature = rng.nextDouble();
            double humidity = rng.nextDouble();
            double ticket = BiomeShare.weightedTicketFor(BIOME_SEED, i, -i);
            double legacy = BiomeShare.ticketFor(preferred, BIOME_SEED, i, -i);
            int selected = BiomeShare.select(preferred, temperature, humidity,
                ids, factors, count, ticket, legacy, scratch);
            assertTrue(selected >= 0, "selector returned no biome at sample " + i);
            int family = BiomeShare.familyOf(selected);
            double distance = Math.sqrt(BiomeShare.distanceSquared(family, temperature, humidity));
            maxDistance = Math.max(maxDistance, distance);
            if (distance > BiomeShare.DEFAULT_R_MAX + 1.0e-9) {
                beyond++;
                boolean alternative = false;
                for (int candidate = 0; candidate < count; candidate++) {
                    int candidateFamily = BiomeShare.familyOf(ids[candidate]);
                    if (Math.sqrt(BiomeShare.distanceSquared(candidateFamily, temperature, humidity))
                        <= BiomeShare.DEFAULT_R_MAX) {
                        alternative = true;
                        break;
                    }
                }
                if (!alternative) noAlternative++;
            }
        }
        assertEquals(beyond, noAlternative, "a selection exceeded R_max while a closer alternative existed");
        System.out.println("10.6 coherence maxDistance=" + maxDistance
            + " beyond=" + beyond + " noAlternative=" + noAlternative);
    }

    @Test
    @DisplayName("10.7 codec, share code, preset export, undo/redo and bad data")
    void codecRoundTripsAndBadData() throws Exception {
        IslandDraft draft = draft(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.STANDARD);
        draft.setBiomeWeight(BiomePreview.Type.PLAINS, 150);
        draft.setBiomeWeight(BiomePreview.Type.FOREST, 50);
        draft.applyCalibration(BiomeShareCalibrator.calibrate(draft));
        assertTrue(draft.hasNonNeutralWeights());

        BiomeShare.Settings settings = draft.biomeShareSettings();
        var encoded = BiomeShare.Settings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow(IllegalStateException::new);
        BiomeShare.Settings decoded = BiomeShare.Settings.CODEC
            .parse(JsonOps.INSTANCE, encoded).getOrThrow(IllegalStateException::new).sanitized();
        assertEquals(settings.weights(), decoded.weights());
        assertEquals(settings.bias(), decoded.bias());
        assertEquals(settings.achieved(), decoded.achieved());
        assertEquals(settings.target(), decoded.target());
        assertEquals(settings.feasible(), decoded.feasible());

        IslandDraft shareDecoded = ShareCode.decode(ShareCode.encode(draft));
        assertEquals(draft.biomeWeight(BiomePreview.Type.PLAINS), shareDecoded.biomeWeight(BiomePreview.Type.PLAINS));
        assertEquals(draft.biomeWeight(BiomePreview.Type.FOREST), shareDecoded.biomeWeight(BiomePreview.Type.FOREST));
        assertEquals(draft.biomeShareBias(), shareDecoded.biomeShareBias());
        assertEquals(draft.biomeShareAchieved(), shareDecoded.biomeShareAchieved());
        assertEquals(draft.biomeShareTarget(), shareDecoded.biomeShareTarget());
        assertEquals(draft.sharesFeasible(), shareDecoded.sharesFeasible());
        assertEquals(previewHash(draft), previewHash(shareDecoded));

        Path tmp = Files.createTempDirectory("omniisle-share-preset-");
        WorldPackBuilder.export(tmp, draft);
        JsonObject share = presetBiomeSource(tmp).getAsJsonObject("biome_share");
        BiomeShare.Settings exported = BiomeShare.Settings.CODEC
            .parse(JsonOps.INSTANCE, share).getOrThrow(IllegalStateException::new).sanitized();
        assertEquals(draft.biomeWeight(BiomePreview.Type.PLAINS), exported.weight("minecraft:plains"));
        assertEquals(draft.biomeWeight(BiomePreview.Type.FOREST), exported.weight("minecraft:forest"));
        assertEquals(draft.biomeShareBias().size(), exported.bias().size());
        for (Map.Entry<String, Double> entry : draft.biomeShareBias().entrySet()) {
            assertEquals(entry.getValue(), exported.bias().get(entry.getKey()), 1.0e-12);
        }
        assertTrue(share.has("target"));
        assertTrue(share.has("achieved"));

        IslandDraft undoDraft = draft(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.STANDARD);
        assertEquals(100, undoDraft.biomeWeight(BiomePreview.Type.PLAINS));
        assertTrue(undoDraft.applyBiomeWeightText(BiomePreview.Type.PLAINS, "150"));
        assertEquals(150, undoDraft.biomeWeight(BiomePreview.Type.PLAINS));
        assertTrue(undoDraft.undo());
        assertEquals(100, undoDraft.biomeWeight(BiomePreview.Type.PLAINS));
        assertTrue(undoDraft.redo());
        assertEquals(150, undoDraft.biomeWeight(BiomePreview.Type.PLAINS));

        JsonObject root = JsonParser.parseString(ShareCode.toJson(undoDraft)).getAsJsonObject();
        JsonObject bad = new JsonObject();
        JsonObject weights = new JsonObject();
        weights.addProperty("minecraft:plains", -50);
        weights.addProperty("minecraft:forest", 999);
        weights.addProperty("minecraft:not_a_biome", 100);
        bad.add("weights", weights);
        JsonObject bias = new JsonObject();
        bias.addProperty("minecraft:plains", "NaN");
        bias.addProperty("minecraft:forest", 9999.0);
        bad.add("bias", bias);
        root.add("biome_share", bad);
        IslandDraft sanitized = ShareCode.decodeJson(root.toString());
        assertFalse(sanitized.isBiomeEnabled(BiomePreview.Type.PLAINS), "negative weight disables");
        assertEquals(BiomeShare.MAX_WEIGHT, sanitized.biomeWeight(BiomePreview.Type.FOREST));
        assertEquals(0.0, sanitized.biomeBias(BiomePreview.Type.PLAINS));
        assertEquals(BiomeShare.MAX_BIAS, sanitized.biomeBias(BiomePreview.Type.FOREST));

        BiomeShare.Settings dirty = BiomeShare.Settings.fromMaps(
            Map.of("minecraft:plains", -7, "minecraft:not_a_biome", 100, "minecraft:desert", 5000),
            Map.of("minecraft:plains", Double.NaN, "minecraft:desert", 99.0),
            Map.of(), Map.of(), true).sanitized();
        assertEquals(0, dirty.weight("minecraft:plains"));
        assertEquals(BiomeShare.MAX_WEIGHT, dirty.weight("minecraft:desert"));
        assertEquals(0.0, dirty.bias("minecraft:plains"));
        assertEquals(BiomeShare.MAX_BIAS, dirty.bias("minecraft:desert"));
    }

    @Test
    @DisplayName("10.8 preview-vs-real land-family parity >= 97% with weights")
    void previewMatchesRealSource() {
        IslandDraft draft = draft(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.ALL);
        Map<String, Double> baseline = BiomeShareCalibrator.calibrate(draft).target();
        List<BiomePreview.Type> eligible = eligibleBiomes(draft, baseline);
        Map<BiomePreview.Type, Integer> vector = feasibleVectors(eligible, 2026L).get(0);
        vector.forEach(draft::setBiomeWeight);
        BiomeShareCalibrator.Result calibration = BiomeShareCalibrator.calibrate(draft);
        draft.applyCalibration(calibration);
        assertTrue(draft.hasNonNeutralWeights());
        assertFalse(calibration.bias().isEmpty());

        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        Climate.Sampler sampler = TestWorldgen.sampler(terrain);
        OmniBiomeSource source = sourceFor(draft, draft.biomeShareSettings());
        double halfSpan = draft.worldSpan() * 0.5;
        int minChunk = (int) Math.floor(-halfSpan / 16.0);
        int maxChunk = (int) Math.ceil(halfSpan / 16.0);

        int compared = 0;
        int matches = 0;
        for (int cz = minChunk; cz <= maxChunk; cz += 2) {
            for (int cx = minChunk; cx <= maxChunk; cx += 2) {
                int x = cx * 16 + 8;
                int z = cz * 16 + 8;
                int biomeX = x >> 2;
                int biomeZ = z >> 2;
                Holder<Biome> real = source.getNoiseBiome(biomeX, 16, biomeZ, sampler);
                String path = real.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                int realFamily = OmniBiomeSource.landFamily(path);
                if (realFamily < 0) continue;

                double c = Climate.unquantizeCoord(sampler.sample(biomeX, 16, biomeZ).continentalness());
                double cE = Climate.unquantizeCoord(sampler.sample(biomeX + 1, 16, biomeZ).continentalness());
                double cW = Climate.unquantizeCoord(sampler.sample(biomeX - 1, 16, biomeZ).continentalness());
                double cS = Climate.unquantizeCoord(sampler.sample(biomeX, 16, biomeZ + 1).continentalness());
                double cN = Climate.unquantizeCoord(sampler.sample(biomeX, 16, biomeZ - 1).continentalness());
                double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
                BiomePreview.Selection selection = BiomePreview.selectionV3(c, x, z,
                    draft.biomeSeed(), draft.climateScale(), draft.gradientMode(), draft.gradientStrength(),
                    draft.theme(), slope, 3, halfSpan, terrain.rivers(), terrain,
                    draft.shoreSettings(), !draft.enabledBiomes(BiomePreview.Category.SHORE).isEmpty());
                BiomePreview.Type preview = draft.selectPreviewBiome(selection, x, z);
                if (preview == null || !preview.isLand()) continue;
                compared++;
                if (preview.family == realFamily) matches++;
            }
        }
        assertTrue(compared > 500, "not enough land samples for parity: " + compared);
        double parity = matches / (double) compared;
        System.out.println("10.8 parity " + matches + "/" + compared
            + String.format(Locale.ROOT, " = %.2f%%", parity * 100.0));
        assertTrue(parity >= 0.97, "land-family parity " + parity * 100.0 + "% < 97%");
    }

    @Test
    @DisplayName("10.9 weighted biome lookup overhead <= +3%")
    void weightedLookupOverhead() {
        IslandDraft draft = draft(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.ALL);
        Map<String, Double> baseline = BiomeShareCalibrator.calibrate(draft).target();
        Map<BiomePreview.Type, Integer> vector = feasibleVectors(eligibleBiomes(draft, baseline), 7L).get(0);
        vector.forEach(draft::setBiomeWeight);
        draft.applyCalibration(BiomeShareCalibrator.calibrate(draft));

        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        Climate.Sampler sampler = TestWorldgen.sampler(terrain);
        OmniBiomeSource weighted = sourceFor(draft, draft.biomeShareSettings());
        OmniBiomeSource neutral = sourceFor(draft, null);
        int[] coords = sampleCoordinates(draft, 48);

        for (int i = 0; i < 8; i++) {
            runLookups(neutral, sampler, coords);
            runLookups(weighted, sampler, coords);
        }
        double[] neutralTimes = new double[7];
        double[] weightedTimes = new double[7];
        for (int i = 0; i < neutralTimes.length; i++) {
            neutralTimes[i] = runLookups(neutral, sampler, coords);
            weightedTimes[i] = runLookups(weighted, sampler, coords);
        }
        java.util.Arrays.sort(neutralTimes);
        java.util.Arrays.sort(weightedTimes);
        double neutralMedian = neutralTimes[3];
        double weightedMedian = weightedTimes[3];
        double overhead = (weightedMedian / neutralMedian - 1.0) * 100.0;
        System.out.println(String.format(Locale.ROOT,
            "10.9 lookup neutral=%.3fms weighted=%.3fms overhead=%+.2f%%",
            neutralMedian / 1.0e6, weightedMedian / 1.0e6, overhead));
        assertTrue(overhead <= 3.0, "weighted lookup overhead " + overhead + "% exceeds +3%");
    }

    @Test
    @DisplayName("10.10 UI logic: defaults, typing, +/-/reset, zero untick, terrain read-only")
    void uiLogic() {
        IslandDraft draft = new IslandDraft();
        assertEquals(IslandDraft.OVERWORLD_BIOMES.size(), draft.enabledBiomes().values().size());
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (type.isLand()) assertEquals(100, draft.biomeWeight(type), type.id);
        }
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            boolean terrain = type.isLand() && BiomeShare.isTerrainDriven(type.family);
            assertEquals(terrain, !IslandDraft.isBiomeWeightEditable(type) && type.isLand(), type.id);
            if (terrain) {
                assertFalse(draft.applyBiomeWeightText(type, "250"), type.id + " must be read-only");
                assertEquals(100, draft.biomeWeight(type));
            }
        }

        BiomePreview.Type plains = BiomePreview.Type.PLAINS;
        assertTrue(draft.applyBiomeWeightText(plains, "150"));
        assertEquals(150, draft.biomeWeight(plains));
        assertTrue(draft.applyBiomeWeightText(plains, "0"));
        assertFalse(draft.isBiomeEnabled(plains), "typing 0 must untick the biome");
        assertTrue(draft.applyBiomeWeightText(plains, "100"));
        assertTrue(draft.isBiomeEnabled(plains), "typing a positive value must tick the biome");
        assertEquals(100, draft.biomeWeight(plains));
        assertFalse(draft.applyBiomeWeightText(plains, "not-a-number"));
        assertEquals(100, draft.biomeWeight(plains));
        assertTrue(draft.applyBiomeWeightText(plains, "999"));
        assertEquals(BiomeShare.MAX_WEIGHT, draft.biomeWeight(plains));
        assertTrue(draft.applyBiomeWeightText(plains, "-50"));
        assertFalse(draft.isBiomeEnabled(plains));

        draft.applyBiomeWeightText(plains, "100");
        assertTrue(draft.applyBiomeWeightText(plains, "105"));
        assertEquals(105, draft.biomeWeight(plains));
        assertTrue(draft.applyBiomeWeightText(plains, "100"));
        assertEquals(100, draft.biomeWeight(plains));

        draft.setBiomeWeight(plains, 200);
        draft.setBiomeWeight(BiomePreview.Type.FOREST, 50);
        draft.resetBiomeWeight(plains);
        assertEquals(100, draft.biomeWeight(plains));
        assertEquals(50, draft.biomeWeight(BiomePreview.Type.FOREST));
        draft.resetAllBiomeWeights();
        assertEquals(100, draft.biomeWeight(BiomePreview.Type.FOREST));
        assertFalse(draft.hasNonNeutralWeights());

        draft.setBiomeWeight(plains, 0);
        draft.setBiomeWeight(BiomePreview.Type.FOREST, 0);
        assertFalse(draft.isBiomeEnabled(plains));
        assertFalse(draft.isBiomeEnabled(BiomePreview.Type.FOREST));
        draft.selectAllBiomes(BiomePreview.Category.LAND);
        assertTrue(draft.isBiomeEnabled(plains));
        assertEquals(100, draft.biomeWeight(plains), "+ All restores freshly enabled land to 100");
    }

    @Test
    @DisplayName("10.13 held-out real-source shares match targets (Cherry 45)")
    void heldOutSharesMatchTarget() throws Exception {
        IslandDraft draft = cherry45Draft();
        BiomeShareCalibrator.Result calibration = BiomeShareCalibrator.calibrate(draft);
        assertTrue(calibration.feasible(), "Cherry 45 calibration should be feasible");
        draft.applyCalibration(calibration);
        double cherryTarget = calibration.target()
            .getOrDefault(BiomePreview.Type.CHERRY_GROVE.id, 0.0);
        double cherryAchieved = calibration.achieved()
            .getOrDefault(BiomePreview.Type.CHERRY_GROVE.id, 0.0);
        assertTrue(cherryTarget > 16.0 && cherryTarget < 18.5,
            "Cherry 45 should reproduce the reported 17.4% target: " + cherryTarget);
        assertTrue(Math.abs(cherryAchieved - cherryTarget) <= 3.0,
            "Cherry 45 fit error " + (cherryAchieved - cherryTarget) + " pp");

        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        IslandDensityFunction previousActive = IslandDensityFunction.getActive();
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        try {
            Climate.Sampler sampler = TestWorldgen.sampler(terrain);
            OmniBiomeSource source = sourceFor(draft, draft.biomeShareSettings());
            int resolution = draft.resolution();
            int grid = 79;
            Map<String, Integer> counts = new LinkedHashMap<>();
            int land = 0;
            for (int gz = 0; gz < grid; gz++) {
                int pz = Math.min(resolution - 1,
                    (int) Math.floor((gz + 0.31) * resolution / grid));
                for (int gx = 0; gx < grid; gx++) {
                    int px = Math.min(resolution - 1,
                        (int) Math.floor((gx + 0.31) * resolution / grid));
                    if (!draft.isLand(px, pz)) continue;
                    double wx = draft.worldX(px);
                    double wz = draft.worldZ(pz);
                    if (terrain.signedDistance(wx, wz) <= ShoreField.MAX_INLAND) continue;
                    if (terrain.rivers() != null && terrain.rivers().isEnabled()
                        && terrain.rivers().isRiver(wx, wz)) continue;
                    int biomeX = (int) Math.floor(wx) >> 2;
                    int biomeZ = (int) Math.floor(wz) >> 2;
                    Holder<Biome> holder = source.getNoiseBiome(biomeX, 16, biomeZ, sampler);
                    String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                    int family = OmniBiomeSource.landFamily(path);
                    if (family < 0 || BiomeShare.isTerrainDriven(family)) continue;
                    counts.merge(path, 1, Integer::sum);
                    land++;
                }
            }
            assertTrue(land > 1000, "held-out sample too small: " + land);

            StringBuilder csv = new StringBuilder(
                "biome,target_pct,heldout_pct,error_pp,samples,total_land\n");
            double maxError = 0.0;
            double maxErrorAboveHalfPp = 0.0;
            String worst = "";
            for (Map.Entry<String, Double> entry : calibration.target().entrySet()) {
                String path = BiomeShare.pathOf(entry.getKey());
                int samples = counts.getOrDefault(path, 0);
                double heldOut = 100.0 * samples / land;
                double error = Math.abs(heldOut - entry.getValue());
                maxError = Math.max(maxError, error);
                if (entry.getValue() >= 0.5 && error > maxErrorAboveHalfPp) {
                    maxErrorAboveHalfPp = error;
                    worst = path;
                }
                csv.append(entry.getKey()).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", entry.getValue())).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", heldOut)).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", error)).append(',')
                    .append(samples).append(',').append(land).append('\n');
            }
            Files.createDirectories(EVIDENCE_DIR);
            Files.writeString(EVIDENCE_DIR.resolve("heldout_shares.csv"), csv.toString(),
                StandardCharsets.UTF_8);
            Files.createDirectories(DOCS_EVIDENCE_DIR);
            Files.writeString(DOCS_EVIDENCE_DIR.resolve("heldout_shares.csv"), csv.toString(),
                StandardCharsets.UTF_8);
            System.out.println(String.format(Locale.ROOT,
                "10.13 held-out land=%d cherryTarget=%.2f%% fitAchieved=%.2f%% "
                    + "maxError=%.2f pp (targets>=0.5%%: %.2f pp worst=%s)",
                land, cherryTarget, cherryAchieved, maxError, maxErrorAboveHalfPp, worst));
            assertTrue(maxErrorAboveHalfPp <= 3.0,
                "held-out shares deviate by " + maxErrorAboveHalfPp + " pp (" + worst + ")");
        } finally {
            IslandDensityFunction.setActive(previousActive);
        }
    }

    @Test
    @DisplayName("10.14 Cherry-45 one-block preview is coherent before/after")
    void previewHasNoSpeckleAtCherry45() throws Exception {
        IslandDraft draft = cherry45Draft();
        BiomeShareCalibrator.Result calibration = BiomeShareCalibrator.calibrate(draft);
        draft.applyCalibration(calibration);
        double cherryTarget = calibration.target()
            .getOrDefault(BiomePreview.Type.CHERRY_GROVE.id, 0.0);
        double cherryAchieved = calibration.achieved()
            .getOrDefault(BiomePreview.Type.CHERRY_GROVE.id, 0.0);

        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        IslandDensityFunction previousActive = IslandDensityFunction.getActive();
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        try {
            int[] ids = new int[BiomeShare.CATALOG_SIZE];
            double[] factors = new double[BiomeShare.CATALOG_SIZE];
            int count = draft.fillLandCandidates(ids, factors);
            BiomeShare.SelectorScratch scratch = new BiomeShare.SelectorScratch();
            double halfSpan = draft.worldSpan() * 0.5;
            boolean shoreAllowed = !draft.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();

            int overviewSize = draft.resolution();
            Rendered overview = render(draft, terrain, ids, factors, count, scratch, shoreAllowed,
                halfSpan, draft.worldX(0), draft.worldZ(0),
                draft.worldSpan() / overviewSize, overviewSize, overviewSize);

            int width = 192;
            int height = 128;
            int[] center = cherryRichCenter(draft, terrain, shoreAllowed, halfSpan, width, height,
                cherryTarget / 100.0);
            int minX = center[0] - width / 2;
            int minZ = center[1] - height / 2;
            Rendered window = render(draft, terrain, ids, factors, count, scratch, shoreAllowed,
                halfSpan, minX, minZ, 1.0, width, height);

            Speckle oldWindow = measureSpeckle(window.before());
            Speckle newWindow = measureSpeckle(window.after());
            Speckle oldOverview = measureSpeckle(overview.before());
            Speckle newOverview = measureSpeckle(overview.after());
            double oldWindowCherry = cherryShare(window.beforeCherry(), oldWindow);
            double newWindowCherry = cherryShare(window.afterCherry(), newWindow);
            double oldOverviewCherry = cherryShare(overview.beforeCherry(), oldOverview);
            double newOverviewCherry = cherryShare(overview.afterCherry(), newOverview);
            System.out.println(String.format(Locale.ROOT,
                "10.14 window=[%d,%d]+%dx%d old agreement=%.3f isolated=%.3f cherry=%.2f%% | "
                    + "new agreement=%.3f isolated=%.3f cherry=%.2f%% land=%d | "
                    + "overview old agreement=%.3f isolated=%.3f cherry=%.2f%% | "
                    + "new agreement=%.3f isolated=%.3f cherry=%.2f%% land=%d",
                minX, minZ, width, height, oldWindow.agreement(), oldWindow.isolatedRate(),
                oldWindowCherry, newWindow.agreement(), newWindow.isolatedRate(), newWindowCherry,
                newWindow.landPixels(), oldOverview.agreement(), oldOverview.isolatedRate(),
                oldOverviewCherry, newOverview.agreement(), newOverview.isolatedRate(),
                newOverviewCherry, newOverview.landPixels()));

            Files.createDirectories(DOCS_EVIDENCE_DIR);
            StringBuilder csv = new StringBuilder(
                "variant,cherry_weight,cherry_target_pct,cherry_achieved_pct,view,cherry_pct,"
                    + "land_pixels,neighbor_agreement,isolated_rate,area\n");
            appendMetricRow(csv, "white_0.8.0", cherryTarget, cherryAchieved, "island_192",
                oldOverviewCherry, oldOverview, overviewSize + "x" + overviewSize);
            appendMetricRow(csv, "coherent_0.8.1", cherryTarget, cherryAchieved, "island_192",
                newOverviewCherry, newOverview, overviewSize + "x" + overviewSize);
            String windowArea = width + "x" + height + "@" + minX + "+" + minZ;
            appendMetricRow(csv, "white_0.8.0", cherryTarget, cherryAchieved, "window_1block",
                oldWindowCherry, oldWindow, windowArea);
            appendMetricRow(csv, "coherent_0.8.1", cherryTarget, cherryAchieved, "window_1block",
                newWindowCherry, newWindow, windowArea);
            Files.writeString(DOCS_EVIDENCE_DIR.resolve("cherry45_metrics.csv"), csv.toString(),
                StandardCharsets.UTF_8);
            writeEvidenceImage(DOCS_EVIDENCE_DIR.resolve("cherry45_before_after.png"),
                overview, overviewSize, overviewSize, window, width, height);

            assertTrue(newWindow.agreement() >= 0.85,
                "coherent preview neighbour agreement " + newWindow.agreement() + " < 0.85");
            assertTrue(newWindow.isolatedRate() <= 0.02,
                "coherent preview isolated rate " + newWindow.isolatedRate() + " > 0.02");
            assertTrue(oldWindow.agreement() < newWindow.agreement(),
                "the 0.8.0 white ticket should be measurably more speckled");
            assertTrue(oldWindow.isolatedRate() > newWindow.isolatedRate(),
                "the 0.8.0 white ticket should isolate more pixels");
            assertTrue(newOverview.agreement() > oldOverview.agreement(),
                "coherent island preview should agree more than the white ticket");
            assertTrue(newOverview.isolatedRate() < oldOverview.isolatedRate(),
                "coherent island preview should isolate fewer pixels than the white ticket");
            assertTrue(newWindowCherry > 1.0,
                "the representative window should show the weighted Cherry biome");
        } finally {
            IslandDensityFunction.setActive(previousActive);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static IslandDraft draft(IslandDraft.Preset preset, IslandDraft.BiomeTheme theme) {
        IslandDraft draft = new IslandDraft();
        draft.setShapeSeed(SHAPE_SEED);
        draft.setBiomeSeed(BIOME_SEED);
        draft.setPreset(preset);
        draft.applyTheme(theme);
        return draft;
    }

    private static long seed(IslandDraft.Preset preset, IslandDraft.BiomeTheme theme) {
        return 31L * preset.ordinal() + 7L * theme.ordinal() + 0x51ED;
    }

    private static IslandDraft cherry45Draft() {
        long shapeSeed = 0x306d956984a4bf25L;
        IslandDraft draft = draft(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.ALL);
        draft.setShapeSeed(shapeSeed);
        // The report hides the biome seed; +5 reproduces the screenshot's 17.4% Cherry target.
        draft.setBiomeSeed(shapeSeed + 5);
        draft.setIslandSize(4000);
        draft.setBiomeWeight(BiomePreview.Type.CHERRY_GROVE, 45);
        return draft;
    }

    /** Exact 0.8.0 white per-block ticket, kept only to measure the regression. */
    private static double whiteTicket(int preferredFamily, long biomeSeed, int x, int z) {
        long value = biomeSeed
            ^ (x * 0x9E3779B97F4A7C15L)
            ^ (z * 0xC2B2AE3D27D4EB4FL)
            ^ (preferredFamily * 0x165667B19E3779F9L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value = value ^ (value >>> 31);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static int[] cherryRichCenter(IslandDraft draft, IslandTerrain terrain,
                                          boolean shoreAllowed, double halfSpan,
                                          int width, int height, double targetShare) {
        int step = 24;
        int cols = width / step;
        int rows = (height + step - 1) / step;
        int reach = (int) (halfSpan * 0.8);
        int cells = 2 * reach / step + 1;
        boolean[][] cherry = new boolean[cells][cells];
        boolean[][] land = new boolean[cells][cells];
        for (int j = 0; j < cells; j++) {
            double wz = -reach + j * step;
            for (int i = 0; i < cells; i++) {
                double wx = -reach + i * step;
                BiomePreview.Selection selection = selectionAt(draft, terrain, shoreAllowed,
                    halfSpan, wx, wz);
                BiomePreview.Type type = draft.selectPreviewBiome(selection, wx, wz);
                land[j][i] = type != null && type.isLand();
                cherry[j][i] = type == BiomePreview.Type.CHERRY_GROVE;
            }
        }
        int[][] cherryPrefix = prefix(cherry);
        int[][] landPrefix = prefix(land);
        double bestScore = Double.POSITIVE_INFINITY;
        int bestLand = 0;
        int bestI = -1;
        int bestJ = 0;
        for (int j = 0; j + rows <= cells; j++) {
            for (int i = 0; i + cols <= cells; i++) {
                int windowLand = sum(landPrefix, i, j, cols, rows);
                if (windowLand * 2 < cols * rows) continue;
                int windowCherry = sum(cherryPrefix, i, j, cols, rows);
                if (windowCherry < 2) continue;
                double score = Math.abs(windowCherry / (double) (cols * rows) - targetShare);
                if (score < bestScore - 1e-9
                    || (Math.abs(score - bestScore) <= 1e-9 && windowLand > bestLand)) {
                    bestScore = score;
                    bestLand = windowLand;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        if (bestI < 0) return new int[]{0, 0};
        return new int[]{
            (int) Math.round(-reach + (bestI + cols / 2.0) * step),
            (int) Math.round(-reach + (bestJ + rows / 2.0) * step)};
    }

    private static int[][] prefix(boolean[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int[][] prefix = new int[rows + 1][cols + 1];
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                prefix[j + 1][i + 1] = (grid[j][i] ? 1 : 0)
                    + prefix[j][i + 1] + prefix[j + 1][i] - prefix[j][i];
            }
        }
        return prefix;
    }

    private static int sum(int[][] prefix, int x, int y, int width, int height) {
        return prefix[y + height][x + width] - prefix[y][x + width]
            - prefix[y + height][x] + prefix[y][x];
    }

    private static BiomePreview.Selection selectionAt(IslandDraft draft, IslandTerrain terrain,
                                                      boolean shoreAllowed, double halfSpan,
                                                      double wx, double wz) {
        return BiomePreview.selectionV3(
            terrain.continentalness(wx, wz), wx, wz, draft.biomeSeed(), draft.climateScale(),
            draft.gradientMode(), draft.gradientStrength(), draft.theme(),
            previewSlope(terrain, wx, wz), draft.generationVersionNumber(), halfSpan,
            terrain.rivers(), terrain, draft.shoreSettings(), shoreAllowed);
    }

    private record Rendered(BiomePreview.Type[][] before, BiomePreview.Type[][] after,
                            int[] beforeColors, int[] afterColors,
                            int beforeCherry, int afterCherry) {}

    private static Rendered render(IslandDraft draft, IslandTerrain terrain, int[] ids,
                                   double[] factors, int count, BiomeShare.SelectorScratch scratch,
                                   boolean shoreAllowed, double halfSpan,
                                   double originX, double originZ, double step,
                                   int width, int height) {
        BiomePreview.Type[][] before = new BiomePreview.Type[height][width];
        BiomePreview.Type[][] after = new BiomePreview.Type[height][width];
        int[] beforeColors = new int[width * height];
        int[] afterColors = new int[width * height];
        int beforeCherry = 0;
        int afterCherry = 0;
        for (int z = 0; z < height; z++) {
            double wz = originZ + z * step;
            for (int x = 0; x < width; x++) {
                double wx = originX + x * step;
                BiomePreview.Selection selection = selectionAt(draft, terrain, shoreAllowed,
                    halfSpan, wx, wz);
                BiomePreview.Type afterType = draft.selectPreviewBiome(selection, wx, wz);
                BiomePreview.Type beforeType = afterType;
                if (selection != null && selection.isLand() && count > 0
                    && !BiomeShare.isTerrainDriven(selection.landFamily())) {
                    int selected = BiomeShare.select(selection.landFamily(),
                        selection.temperature(), selection.humidity(), ids, factors, count,
                        whiteTicket(selection.landFamily(), draft.biomeSeed(),
                            (int) Math.floor(wx), (int) Math.floor(wz)),
                        0.0, scratch);
                    BiomePreview.Type chosen = selected >= 0
                        ? BiomePreview.landTypeForCatalog(selected) : null;
                    if (chosen != null) beforeType = chosen;
                }
                before[z][x] = beforeType;
                after[z][x] = afterType;
                beforeColors[z * width + x] = colorOf(beforeType);
                afterColors[z * width + x] = colorOf(afterType);
                if (beforeType == BiomePreview.Type.CHERRY_GROVE) beforeCherry++;
                if (afterType == BiomePreview.Type.CHERRY_GROVE) afterCherry++;
            }
        }
        return new Rendered(before, after, beforeColors, afterColors, beforeCherry, afterCherry);
    }

    private static double cherryShare(int cherryPixels, Speckle metric) {
        return 100.0 * cherryPixels / Math.max(1, metric.landPixels());
    }

    private static void appendMetricRow(StringBuilder csv, String variant, double target,
                                        double achieved, String view, double cherryPct,
                                        Speckle metric, String area) {
        csv.append(variant).append(",45,")
            .append(String.format(Locale.ROOT, "%.3f,%.3f,", target, achieved))
            .append(view).append(',')
            .append(String.format(Locale.ROOT, "%.3f,%d,%.4f,%.4f,",
                cherryPct, metric.landPixels(), metric.agreement(), metric.isolatedRate()))
            .append(area).append('\n');
    }

    private static double previewSlope(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 8, z);
        double cW = terrain.continentalness(x - 8, z);
        double cS = terrain.continentalness(x, z + 8);
        double cN = terrain.continentalness(x, z - 8);
        return Math.hypot((cE - cW) / 16.0, (cS - cN) / 16.0) * 64.0;
    }

    private record Speckle(int landPixels, int comparisons, int matches, int isolated, int interior) {
        double agreement() { return comparisons == 0 ? 0.0 : matches / (double) comparisons; }
        double isolatedRate() { return interior == 0 ? 1.0 : isolated / (double) interior; }
    }

    private static Speckle measureSpeckle(BiomePreview.Type[][] types) {
        int height = types.length;
        int width = types[0].length;
        int landPixels = 0;
        int comparisons = 0;
        int matches = 0;
        int isolated = 0;
        int interior = 0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                BiomePreview.Type type = types[z][x];
                if (type == null || !type.isLand()) continue;
                landPixels++;
                int landNeighbors = 0;
                int equalNeighbors = 0;
                for (int[] offset : offsets) {
                    int nx = x + offset[0];
                    int nz = z + offset[1];
                    if (nx < 0 || nx >= width || nz < 0 || nz >= height) continue;
                    BiomePreview.Type neighbor = types[nz][nx];
                    if (neighbor == null || !neighbor.isLand()) continue;
                    landNeighbors++;
                    comparisons++;
                    if (neighbor == type) {
                        matches++;
                        equalNeighbors++;
                    }
                }
                if (landNeighbors == 4) {
                    interior++;
                    if (equalNeighbors == 0) isolated++;
                }
            }
        }
        return new Speckle(landPixels, comparisons, matches, isolated, interior);
    }

    private static int colorOf(BiomePreview.Type type) {
        return type != null ? type.color | 0xFF000000 : 0xFF0B0E12;
    }

    private static void writeEvidenceImage(Path path, Rendered overview, int overviewWidth,
                                           int overviewHeight, Rendered window, int windowWidth,
                                           int windowHeight) throws IOException {
        int scale = 2;
        int gap = 8;
        int label = 22;
        int panelWidth = overviewWidth * scale;
        int overviewPanelHeight = overviewHeight * scale;
        int windowPanelHeight = windowHeight * scale;
        int totalWidth = panelWidth * 2 + gap;
        int totalHeight = label + overviewPanelHeight + label + windowPanelHeight + label;
        BufferedImage image = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x101418));
        g.fillRect(0, 0, totalWidth, totalHeight);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.setColor(new Color(0xE8EDF2));
        int leftX = 10;
        int rightX = panelWidth + gap + 10;
        g.drawString("0.8.0 white ticket (before)", leftX, 16);
        g.drawString("0.8.1 coherent ticket (after)", rightX, 16);
        int y = label;
        drawScaled(g, overview.beforeColors(), overviewWidth, leftX, y, scale);
        drawScaled(g, overview.afterColors(), overviewWidth, rightX, y, scale);
        y += overviewPanelHeight + 16;
        g.setColor(new Color(0xE8EDF2));
        g.drawString("island preview " + overviewWidth + "x" + overviewHeight, leftX, y);
        g.drawString("island preview " + overviewWidth + "x" + overviewHeight, rightX, y);
        y += 6;
        drawScaled(g, window.beforeColors(), windowWidth, leftX, y, scale);
        drawScaled(g, window.afterColors(), windowWidth, rightX, y, scale);
        y += windowPanelHeight + 16;
        g.setColor(new Color(0xE8EDF2));
        g.drawString("1-block window " + windowWidth + "x" + windowHeight, leftX, y);
        g.drawString("1-block window " + windowWidth + "x" + windowHeight, rightX, y);
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void drawScaled(Graphics2D g, int[] colors, int width, int x0, int y0,
                                   int scale) {
        int height = colors.length / width;
        BufferedImage panel = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        panel.setRGB(0, 0, width, height, colors, 0, width);
        g.drawImage(panel, x0, y0, width * scale, height * scale, null);
    }

    private static List<BiomePreview.Type> eligibleBiomes(IslandDraft draft, Map<String, Double> baseline) {
        List<BiomePreview.Type> eligible = new ArrayList<>();
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (!type.isLand() || !draft.isBiomeEnabled(type)) continue;
            if (BiomeShare.isTerrainDriven(type.family)) continue;
            if (baseline.getOrDefault(type.id, 0.0) >= 2.0) eligible.add(type);
        }
        return eligible;
    }

    private static List<Map<BiomePreview.Type, Integer>> feasibleVectors(
        List<BiomePreview.Type> eligible, long seed) {
        Random rng = new Random(seed);
        int[] choices = {50, 50, 150, 200, 300};
        List<Map<BiomePreview.Type, Integer>> vectors = new ArrayList<>();
        for (int v = 0; v < 3; v++) {
            List<BiomePreview.Type> pool = new ArrayList<>(eligible);
            Map<BiomePreview.Type, Integer> vector = new LinkedHashMap<>();
            int picks = Math.min(3 + rng.nextInt(2), pool.size());
            for (int i = 0; i < picks && !pool.isEmpty(); i++) {
                BiomePreview.Type type = pool.remove(rng.nextInt(pool.size()));
                vector.put(type, choices[rng.nextInt(choices.length)]);
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private static BiomePreview.Selection sampleSelection(IslandDraft draft, double x, double z, double c) {
        return BiomePreview.selectionV3(c, x, z, draft.biomeSeed(), draft.climateScale(),
            draft.gradientMode(), draft.gradientStrength(), draft.theme(), 0.0, 3,
            draft.worldSpan() * 0.5, null, null, draft.shoreSettings(), false);
    }

    private static long previewHash(IslandDraft draft) {
        IslandTerrain terrain = draft.createTerrain();
        double halfSpan = draft.worldSpan() * 0.5;
        boolean shoreAllowed = !draft.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();
        long hash = 0xCBF29CE484222325L;
        long lcg = 0x6A09E667F3BCC909L;
        for (int i = 0; i < 4096; i++) {
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
            double c = terrain.continentalness(x, z);
            BiomePreview.Selection selection = BiomePreview.selectionV3(c, x, z, draft.biomeSeed(),
                draft.climateScale(), draft.gradientMode(), draft.gradientStrength(), draft.theme(),
                0.0, draft.generationVersionNumber(), halfSpan, terrain.rivers(), terrain,
                draft.shoreSettings(), shoreAllowed);
            BiomePreview.Type type = draft.selectPreviewBiome(selection, x, z);
            hash ^= type != null ? type.ordinal() : -1;
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private static OmniBiomeSource sourceFor(IslandDraft draft, BiomeShare.Settings settings) {
        List<Holder<Biome>> allLand = new ArrayList<>();
        for (int i = 0; i < BiomeShare.CATALOG_SIZE; i++) {
            allLand.add(TestWorldgen.holder(BiomeShare.variant(i).id()));
        }
        List<Holder<Biome>> enabledLand = landHolders(draft, BiomePreview.Category.LAND);
        List<Holder<Biome>> shore = List.of(TestWorldgen.holder("beach"),
            TestWorldgen.holder("stony_shore"), TestWorldgen.holder("snowy_beach"));
        List<Holder<Biome>> ocean = List.of(TestWorldgen.holder("ocean"),
            TestWorldgen.holder("deep_ocean"), TestWorldgen.holder("warm_ocean"),
            TestWorldgen.holder("lukewarm_ocean"), TestWorldgen.holder("deep_lukewarm_ocean"),
            TestWorldgen.holder("cold_ocean"), TestWorldgen.holder("deep_cold_ocean"),
            TestWorldgen.holder("frozen_ocean"), TestWorldgen.holder("deep_frozen_ocean"));
        List<Holder<Biome>> caves = List.of(TestWorldgen.holder("lush_caves"),
            TestWorldgen.holder("dripstone_caves"), TestWorldgen.holder("deep_dark"),
            TestWorldgen.holder("sulfur_caves"));
        return new OmniBiomeSource(allLand, enabledLand, shore,
            holdersFor(draft, BiomePreview.Category.SHORE, shore), ocean,
            holdersFor(draft, BiomePreview.Category.OCEAN, ocean), List.of(), List.of(), caves,
            holdersFor(draft, BiomePreview.Category.CAVE, caves), draft.climateScale(), draft.biomeSeed(),
            draft.generationVersionNumber(), draft.gradientMode().name().toLowerCase(Locale.ROOT),
            draft.gradientStrength(), draft.theme().name().toLowerCase(Locale.ROOT),
            java.util.Optional.of(draft.shoreSettings()), java.util.Optional.ofNullable(settings));
    }

    private static List<Holder<Biome>> landHolders(IslandDraft draft, BiomePreview.Category category) {
        List<Holder<Biome>> holders = new ArrayList<>();
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (type.category == category && draft.isBiomeEnabled(type)) {
                holders.add(TestWorldgen.holder(type.id));
            }
        }
        return holders;
    }

    private static List<Holder<Biome>> holdersFor(IslandDraft draft, BiomePreview.Category category,
                                                  List<Holder<Biome>> available) {
        Set<String> enabled = new HashSet<>();
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (type.category == category && draft.isBiomeEnabled(type)) enabled.add(type.id);
        }
        List<Holder<Biome>> holders = new ArrayList<>();
        for (Holder<Biome> holder : available) {
            String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
            if (enabled.contains(path)) holders.add(holder);
        }
        return holders;
    }

    private static long sourceHash(OmniBiomeSource source, Climate.Sampler sampler,
                                   IslandDraft draft, int chunks) {
        double halfSpan = draft.worldSpan() * 0.5;
        int minChunk = (int) Math.floor(-halfSpan / 16.0);
        long hash = 0xCBF29CE484222325L;
        for (int cz = 0; cz < chunks; cz++) {
            for (int cx = 0; cx < chunks; cx++) {
                for (int y : new int[] {16, -4}) {
                    Holder<Biome> holder = source.getNoiseBiome(minChunk + cx, y, minChunk + cz, sampler);
                    String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                    for (int i = 0; i < path.length(); i++) {
                        hash ^= path.charAt(i);
                        hash *= 0x100000001B3L;
                    }
                    hash ^= 0x9E3779B97F4A7C15L;
                }
            }
        }
        return hash;
    }

    private static int[] sampleCoordinates(IslandDraft draft, int chunks) {
        int[] coords = new int[chunks * chunks * 2];
        double halfSpan = draft.worldSpan() * 0.5;
        int minChunk = (int) Math.floor(-halfSpan / 16.0);
        int index = 0;
        for (int cz = 0; cz < chunks; cz++) {
            for (int cx = 0; cx < chunks; cx++) {
                coords[index++] = minChunk + cx;
                coords[index++] = minChunk + cz;
            }
        }
        return coords;
    }

    private static double runLookups(OmniBiomeSource source, Climate.Sampler sampler, int[] coords) {
        java.lang.management.ThreadMXBean bean =
            java.lang.management.ManagementFactory.getThreadMXBean();
        long start = bean.getCurrentThreadCpuTime();
        long sink = 0;
        for (int i = 0; i < coords.length; i += 2) {
            sink += source.getNoiseBiome(coords[i], 16, coords[i + 1], sampler).hashCode();
        }
        long elapsed = bean.getCurrentThreadCpuTime() - start;
        if (sink == 42L) System.out.print("");
        return elapsed;
    }

    private static JsonObject readPresetJson(Path dir) throws Exception {
        Path pack = dir.resolve(WorldPackBuilder.FILE_NAME);
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            String json = new String(zip.getInputStream(
                zip.getEntry("data/omniisle/worldgen/world_preset/omniisle.json")).readAllBytes(),
                StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        }
    }

    private static JsonObject presetBiomeSource(Path dir) throws Exception {
        return readPresetJson(dir)
            .getAsJsonObject("dimensions")
            .getAsJsonObject("minecraft:overworld")
            .getAsJsonObject("generator")
            .getAsJsonObject("biome_source");
    }
}
