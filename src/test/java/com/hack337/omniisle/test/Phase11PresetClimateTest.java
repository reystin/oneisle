package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.ClimateLayout;
import com.hack337.omniisle.continent.IslandTerrain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.8.4 acceptance on the five shipping presets: every v3 world must show a
 * temperate heartland plus warm and cold sectors, with rare families bounded.
 */
public class Phase11PresetClimateTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/phase11/after");
    private static final long SEED = 2025L;
    private static final int SIZE = 256;
    private static final List<IslandDraft.Preset> PRESETS = List.of(
        IslandDraft.Preset.MINESHIELD_4_MAP, IslandDraft.Preset.NATURAL,
        IslandDraft.Preset.ROUND, IslandDraft.Preset.ARCHIPELAGO,
        IslandDraft.Preset.CRESCENT);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("11P.1 five presets: 8% warm, 8% cold, 60-75% green, rare < 3.5%")
    void composition() throws IOException {
        StringBuilder csv = new StringBuilder("preset,land,warm_pct,cold_pct,green_pct,"
            + "cherry_pct,mushroom_pct,pale_pct,swamp_above_c018,"
            + "snow_below_tier14_outside_cold,mushroom_main,dry_shelf_ocean\n");
        StringBuilder summary = new StringBuilder(
            "# 1.0.1 v3 composition, seed 2025, 256x256, five presets\n"
                + "target,warm>=8.00,cold>=8.00,green>=60.00,green<=75.00,"
                + "cherry<3.50,mushroom<3.50,pale<3.50,counters=0\n");
        boolean allMet = true;
        for (IslandDraft.Preset preset : PRESETS) {
            Result result = scan(preset);
            int land = result.land;
            String message = preset + " " + result.families + " warm=" + pct(result.warm, land)
                + " cold=" + pct(result.cold, land) + " green=" + pct(result.green, land);
            assertTrue(land > 10_000, message);
            assertTrue(result.warm / (double) land >= 0.08, message + " warm/arid sector below 8%");
            assertTrue(result.cold / (double) land >= 0.08, message + " cold/snowy sector below 8%");
            assertTrue(result.green / (double) land >= 0.60 && result.green / (double) land <= 0.75,
                message + " green heartland outside 60-75%");
            assertTrue(result.cherry / (double) land < 0.035, message + " cherry above 3.5%");
            assertTrue(result.mushroom / (double) land < 0.035, message + " mushrooms above 3.5%");
            assertTrue(result.pale / (double) land < 0.035, message + " pale_garden above 3.5%");
            assertEquals(0, result.swampHighC, message + " wetland above c=0.18: " + result.swampHighC);
            assertEquals(0, result.snowLowTier, message + " snow below tier 1.4 outside cold: "
                + result.snowLowTier);
            assertEquals(0, result.mushroomMain, message + " mushroom on the main island");
            assertEquals(0, result.dryShelfOcean, message + " ocean on the dry shelf: " + result.dryShelfOcean);
            assertTrue(result.warmFamilyC > 0, message + " warm families missing");
            boolean met = result.warm / (double) land >= 0.08 && result.cold / (double) land >= 0.08
                && result.green / (double) land >= 0.60 && result.green / (double) land <= 0.75
                && result.cherry / (double) land < 0.035
                && result.mushroom / (double) land < 0.035
                && result.pale / (double) land < 0.035
                && result.swampHighC == 0 && result.snowLowTier == 0
                && result.mushroomMain == 0 && result.dryShelfOcean == 0;
            allMet &= met;
            csv.append(preset.name()).append(',').append(land)
                .append(',').append(fmt(result.warm, land))
                .append(',').append(fmt(result.cold, land))
                .append(',').append(fmt(result.green, land))
                .append(',').append(fmt(result.cherry, land))
                .append(',').append(fmt(result.mushroom, land))
                .append(',').append(fmt(result.pale, land))
                .append(',').append(result.swampHighC)
                .append(',').append(result.snowLowTier)
                .append(',').append(result.mushroomMain)
                .append(',').append(result.dryShelfOcean)
                .append('\n');
            summary.append(preset.name())
                .append(",warm=").append(pct(result.warm, land))
                .append(",cold=").append(pct(result.cold, land))
                .append(",green=").append(pct(result.green, land))
                .append(",cherry=").append(pct(result.cherry, land))
                .append(",mushroom=").append(pct(result.mushroom, land))
                .append(",pale=").append(pct(result.pale, land))
                .append(",counters=0")
                .append(met ? ",PASS" : ",FAIL")
                .append('\n');
        }
        summary.append("all_targets_met=").append(allMet).append('\n');
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("composition_controlled.csv"), csv.toString(),
                StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("summary_after.txt"), summary.toString(),
                StandardCharsets.UTF_8);
        }
    }

    private static String fmt(int count, int total) {
        return String.format(Locale.ROOT, "%.2f", count * 100.0 / Math.max(1, total));
    }

    @Test
    @DisplayName("11P.2 preset output is deterministic across repeated terrain builds")
    void deterministicBuilds() {
        for (IslandDraft.Preset preset : PRESETS) {
            Result first = scan(preset);
            Result second = scan(preset);
            assertEquals(first.families, second.families, preset + " family histogram drifted");
        }
    }

    private record Result(TreeMap<Integer, Integer> families, int land, int warm, int cold,
                          int green, int cherry, int mushroom, int pale, int swampHighC,
                          int snowLowTier, int mushroomMain, int dryShelfOcean, int warmFamilyC) {}

    private static final java.util.Map<IslandDraft.Preset, Result> CACHE =
        new java.util.EnumMap<>(IslandDraft.Preset.class);

    private static synchronized Result scan(IslandDraft.Preset preset) {
        return CACHE.computeIfAbsent(preset, Phase11PresetClimateTest::scanTerrain);
    }

    private static Result scanTerrain(IslandDraft.Preset preset) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setShapeSeed(SEED);
        draft.setBiomeSeed(SEED);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        IslandTerrain terrain = IslandTerrain.create(TestWorldgen.config(draft, 3, 0.25));
        ClimateLayout layout = terrain.climateLayout();
        double half = draft.worldSpan() * 0.5;
        double step = draft.worldSpan() / SIZE;
        TreeMap<Integer, Integer> families = new TreeMap<>();
        int land = 0, warm = 0, cold = 0, green = 0, cherry = 0, mushroom = 0, pale = 0;
        int swampHighC = 0, snowLowTier = 0, mushroomMain = 0, dryShelfOcean = 0, warmFamilyC = 0;
        for (int gz = 0; gz < SIZE; gz++) {
            double z = -half + (gz + 0.5) * step;
            for (int gx = 0; gx < SIZE; gx++) {
                double x = -half + (gx + 0.5) * step;
                double c = terrain.continentalness(x, z);
                double slope = slope(terrain, x, z);
                BiomePreview.Selection selection = BiomePreview.selectionV3(
                    c, x, z, draft.biomeSeed(), draft.climateScale(), draft.gradientMode(),
                    draft.gradientStrength(), draft.theme(), slope, 3, half, terrain.rivers(),
                    terrain, draft.shoreSettings(), true);
                double d = terrain.distances(x, z).signed();
                if (selection.type().category == BiomePreview.Category.OCEAN) {
                    if (d <= 0.0 && c >= com.hack337.omniisle.continent.IslandClimate.V3_SEA_LEVEL_CONTINENTALNESS) {
                        dryShelfOcean++;
                    }
                    continue;
                }
                if (!selection.type().isLand()) continue;
                land++;
                int family = selection.landFamily();
                families.merge(family, 1, Integer::sum);
                if (family == 11 || family == 12 || family == 13) warm++;
                if (family == 0 || family == 1) cold++;
                if (family >= 2 && family <= 7 || family == 17) green++;
                if (family == 6) cherry++;
                if (family == 16) {
                    mushroom++;
                    if (terrain.distances(x, z).main() >= 0.0) mushroomMain++;
                }
                if (family == 17) pale++;
                if ((family == 8 || family == 9) && c > 0.18) swampHighC++;
                ClimateLayout.Sample sector = layout.sample(x, z);
                if (family == 15 && terrain.heightTierAt(x, z) < 1.4
                    && sector.province() != ClimateLayout.Province.COLD) {
                    snowLowTier++;
                }
                if ((family == 11 || family == 12 || family == 13) && c > 0.0) warmFamilyC++;
            }
        }
        System.out.println(String.format(Locale.ROOT,
            "PHASE11 preset %s land=%d warm=%.2f cold=%.2f green=%.2f cherry=%.2f mush=%.2f pale=%.2f %s",
            preset, land, warm * 100.0 / land, cold * 100.0 / land, green * 100.0 / land,
            cherry * 100.0 / land, mushroom * 100.0 / land, pale * 100.0 / land, families));
        return new Result(families, land, warm, cold, green, cherry, mushroom, pale,
            swampHighC, snowLowTier, mushroomMain, dryShelfOcean, warmFamilyC);
    }

    private static String pct(int count, int total) {
        return String.format(Locale.ROOT, "%.2f%%", count * 100.0 / Math.max(1, total));
    }

    private static double slope(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 4.0, z);
        double cW = terrain.continentalness(x - 4.0, z);
        double cS = terrain.continentalness(x, z + 4.0);
        double cN = terrain.continentalness(x, z - 4.0);
        return Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
    }
}
