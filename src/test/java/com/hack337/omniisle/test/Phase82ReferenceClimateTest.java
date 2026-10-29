package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.ClimateLayout;
import com.hack337.omniisle.continent.IslandClimate;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandNoise;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ModernTerrainV2;
import com.hack337.omniisle.continent.ShoreField;
import com.hack337.omniisle.continent.ShoreSettings;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.8.2 v3 reference continent: shoreline, family tree, ridge sync and composition. */
public class Phase82ReferenceClimateTest {
    private static final int RES = 192;
    private static final int SIZE = 3500;
    private static final long SEED = 2025L;
    private static final double HALF = SIZE * 1.36 / 2.0;
    private static final int STEP = 16;
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/phase82");

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void referenceCompositionMatchesDesign() throws IOException {
        ModernTerrainV2 terrain = referenceTerrain();
        TreeMap<Integer, Integer> counts = new TreeMap<>();
        int land = 0;
        int offshoreLand = 0;
        int offshoreMushroom = 0;
        int inlandMushroom = 0;
        int swampViolations = 0;
        int peakColumns = 0;
        int peakWetland = 0;
        int peakMushroom = 0;
        int lowTierSnowOutsideCold = 0;
        TreeMap<Integer, Integer> peakFamilies = new TreeMap<>();
        int foothillColumns = 0;
        int foothillFamilies = 0;
        int dryShelfOcean = 0;
        int dryShelfLand = 0;
        int warmColumns = 0;
        double warmMaxC = -2.0;
        int warmNearCoast = 0;
        int warmWithin400 = 0;
        double maxTemp = -1.0, minTemp = 2.0, maxHum = -1.0, minHum = 2.0;
        double maxTempX = 0, maxTempZ = 0;

        int gridWidth = 0;
        for (double g = -HALF; g <= HALF; g += STEP) gridWidth++;
        boolean[][] warmGrid = new boolean[gridWidth][gridWidth];
        int[][] warmIndex = new int[gridWidth][gridWidth];
        for (int[] row : warmIndex) java.util.Arrays.fill(row, -1);

        double ridgeSum = 0.0, mountainsSum = 0.0, ridgeSq = 0.0, mountainsSq = 0.0, cross = 0.0;
        int correlationN = 0;

        StringBuilder composition = new StringBuilder(
            "family,count,fraction,core_temperate,warm_arid,green_total\n");
        StringBuilder tempTable = new StringBuilder(
            "sample,target_temperature,achieved_temperature,delta\n");
        StringBuilder ridgeCsv = new StringBuilder("x,z,physical_ridge,climate_mountains,family\n");
        StringBuilder mushroomCsv = new StringBuilder("x,z,signed_distance,main_distance,family,zone\n");

        int gzIndex = 0;
        for (double z = -HALF; z <= HALF; z += STEP, gzIndex++) {
            int gxIndex = 0;
            for (double x = -HALF; x <= HALF; x += STEP, gxIndex++) {
                double c = terrain.continentalness(x, z);
                IslandTerrain.Distances distances = terrain.distances(x, z);
                double d = distances.signed();
                double main = distances.main();
                double slope = slope(terrain, x, z);
                double tier = terrain.heightTierAt(x, z);
                ClimateLayout.Sample layout = terrain.climateLayout().sample(x, z);
                IslandClimate.SampleResult res = IslandClimate.sampleV3(c, x, z, slope, 720.0,
                    SEED, IslandClimate.GradientMode.NORTH_SOUTH, 1.0, "all", HALF, 3,
                    d, main, tier, layout,
                    true, ShoreField.NONE);

                double physical = Math.max(0.0, 1.0 - 2.0 * Math.abs(IslandNoise.fbm2(
                    x / 750.0, z / 750.0, SEED + 801L, 3, 2.0, 0.5)));
                ridgeSum += physical;
                mountainsSum += res.mountains();
                ridgeSq += physical * physical;
                mountainsSq += res.mountains() * res.mountains();
                cross += physical * res.mountains();
                correlationN++;
                if (correlationN % 97 == 0) {
                    ridgeCsv.append(String.format(Locale.ROOT, "%.0f,%.0f,%.4f,%.4f,%d%n",
                        x, z, physical, res.mountains(), res.landFamily()));
                }

                if (d > -42.0 && d <= 0.0 && c >= IslandClimate.V3_SEA_LEVEL_CONTINENTALNESS) {
                    if (res.zone() == IslandClimate.SurfaceZone.OCEAN) dryShelfOcean++;
                    else dryShelfLand++;
                }

                if (res.zone() != IslandClimate.SurfaceZone.LAND) continue;
                if (main <= 0.0) {
                    offshoreLand++;
                    if (res.landFamily() == 16) {
                        offshoreMushroom++;
                        mushroomCsv.append(String.format(Locale.ROOT, "%.0f,%.0f,%.1f,%.1f,%d,%s%n",
                            x, z, d, main, res.landFamily(), res.zone()));
                    }
                    continue;
                }

                counts.merge(res.landFamily(), 1, Integer::sum);
                land++;
                if (res.temperature() > maxTemp) { maxTemp = res.temperature(); maxTempX = x; maxTempZ = z; }
                minTemp = Math.min(minTemp, res.temperature());
                maxHum = Math.max(maxHum, res.humidity());
                minHum = Math.min(minHum, res.humidity());
                if (res.landFamily() == 16) inlandMushroom++;
                if ((res.landFamily() == 8 || res.landFamily() == 9)
                    && (c > 0.15 || res.mountains() > 0.42)) {
                    swampViolations++;
                }
                boolean core = isCoreTemperate(res.landFamily());
                boolean warm = isWarmArid(res.landFamily());
                if (c > 0.45) {
                    peakColumns++;
                    peakFamilies.merge(res.landFamily(), 1, Integer::sum);
                    if (res.landFamily() == 8 || res.landFamily() == 9) peakWetland++;
                    if (res.landFamily() == 16) peakMushroom++;
                }
                if (res.landFamily() == 15 && tier < 1.5
                    && layout.province() != ClimateLayout.Province.COLD) {
                    lowTierSnowOutsideCold++;
                }
                if (c > 0.30 && c <= 0.45) {
                    foothillColumns++;
                    if (res.landFamily() == 1 || res.landFamily() == 3 || res.landFamily() == 5
                        || res.landFamily() == 6) {
                        foothillFamilies++;
                    }
                }
                if (warm) {
                    warmColumns++;
                    warmMaxC = Math.max(warmMaxC, c);
                    warmGrid[gzIndex][gxIndex] = true;
                    warmIndex[gzIndex][gxIndex] = warmColumns;
                    if (d <= 400.0) warmWithin400++;
                }
                if (res.temperature() >= 0.72) {
                    tempTable.append(String.format(Locale.ROOT, "%.0f,%.3f,%.3f,%.3f%n",
                        (double) (correlationN), 0.72, res.temperature(),
                        res.temperature() - 0.72));
                }
            }
        }

        int largestWarm = largestComponent(warmGrid);
        double coreFraction = fraction(counts, land, f -> isCoreTemperate(f));
        double greenFraction = fraction(counts, land, f -> isGreen(f));
        double mushroomFraction = land > 0 ? (counts.getOrDefault(16, 0)) / (double) land : 0.0;
        double rMean = ridgeSum / correlationN;
        double mMean = mountainsSum / correlationN;
        double cov = cross / correlationN - rMean * mMean;
        double rStd = Math.sqrt(Math.max(1e-12, ridgeSq / correlationN - rMean * rMean));
        double mStd = Math.sqrt(Math.max(1e-12, mountainsSq / correlationN - mMean * mMean));
        double corr = cov / (rStd * mStd);

        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            composition.append(e.getKey()).append(',').append(e.getValue()).append(',')
                .append(String.format(Locale.ROOT, "%.4f", e.getValue() / (double) land)).append(',')
                .append(isCoreTemperate(e.getKey()) ? 1 : 0).append(',')
                .append(isWarmArid(e.getKey()) ? 1 : 0).append(',')
                .append(isGreen(e.getKey()) ? 1 : 0).append('\n');
        }

        StringBuilder summary = new StringBuilder();
        summary.append("land=").append(land).append('\n');
        summary.append("core_temperate_fraction=").append(fmt(coreFraction)).append('\n');
        summary.append("green_total_fraction=").append(fmt(greenFraction)).append('\n');
        summary.append("dry_shelf_ocean_columns=").append(dryShelfOcean).append('\n');
        summary.append("dry_shelf_land_columns=").append(dryShelfLand).append('\n');
        summary.append("swamp_violations=").append(swampViolations).append('\n');
        summary.append("inland_mushroom=").append(inlandMushroom).append('\n');
        summary.append("mushroom_fraction=").append(fmt(mushroomFraction)).append('\n');
        summary.append("offshore_land=").append(offshoreLand)
            .append(" offshore_mushroom=").append(offshoreMushroom).append('\n');
        summary.append("peak_columns=").append(peakColumns)
            .append(" peak_wetland=").append(peakWetland)
            .append(" peak_mushroom=").append(peakMushroom).append('\n');
        summary.append("peak_families=").append(peakFamilies).append('\n');
        summary.append("low_tier_snow_outside_cold=").append(lowTierSnowOutsideCold).append('\n');
        summary.append("foothill_columns=").append(foothillColumns)
            .append(" foothill_green=").append(foothillFamilies).append('\n');
        summary.append("warm_columns=").append(warmColumns)
            .append(" warm_max_c=").append(fmt(warmMaxC)).append('\n');
        summary.append("warm_largest_component=").append(largestWarm)
            .append(" fraction=").append(fmt(warmColumns == 0 ? 0.0 : largestWarm / (double) warmColumns))
            .append('\n');
        summary.append("warm_within_400_of_coast=").append(warmWithin400)
            .append(" fraction=").append(fmt(warmColumns == 0 ? 0.0 : warmWithin400 / (double) warmColumns))
            .append('\n');
        summary.append("ridge_mountain_correlation=").append(fmt(corr)).append('\n');
        summary.append("temperature_min=").append(fmt(minTemp)).append(" max=").append(fmt(maxTemp))
            .append(" at ").append(maxTempX).append(',').append(maxTempZ).append('\n');
        summary.append("humidity_min=").append(fmt(minHum)).append(" max=").append(fmt(maxHum)).append('\n');
        System.out.println("PHASE82\n" + summary);
        System.out.println("PHASE82 families " + counts);

        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("reference_composition_after.csv"),
                composition.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("phase82_summary_after.txt"),
                summary.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("temp_achieved_vs_target.csv"),
                tempTable.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("mountain_sync.csv"),
                ridgeCsv.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("mushroom_offshore.csv"),
                mushroomCsv.toString(), StandardCharsets.UTF_8);
        }

        // Diagnostic bounds: tightened once the reference composition is confirmed.
        org.junit.jupiter.api.Assertions.assertEquals(0, dryShelfOcean, "dry shelf must not be ocean");
        org.junit.jupiter.api.Assertions.assertEquals(0, inlandMushroom, "mushrooms must be offshore");
        org.junit.jupiter.api.Assertions.assertEquals(0, swampViolations, "no swamps on ridges");
        org.junit.jupiter.api.Assertions.assertTrue(peakColumns > 0, "central peak band missing");
        org.junit.jupiter.api.Assertions.assertEquals(0, peakWetland,
            "no wetlands on the high central band, peaks=" + peakFamilies);
        org.junit.jupiter.api.Assertions.assertEquals(0, peakMushroom,
            "no mushrooms on the high central band");
        org.junit.jupiter.api.Assertions.assertEquals(0, lowTierSnowOutsideCold,
            "snow family 15 only above the mountain tier outside the cold province");
        org.junit.jupiter.api.Assertions.assertTrue(corr > 0.9, "ridge correlation " + corr);
        org.junit.jupiter.api.Assertions.assertTrue(coreFraction >= 0.45 && coreFraction <= 0.75,
            "core temperate " + coreFraction);
        org.junit.jupiter.api.Assertions.assertTrue(greenFraction >= 0.60 && greenFraction <= 0.75,
            "green total " + greenFraction);
        org.junit.jupiter.api.Assertions.assertTrue(foothillColumns > 0 && foothillFamilies > 0,
            "foothill transitions missing");
        org.junit.jupiter.api.Assertions.assertTrue(warmColumns > 0, "warm/arid region missing");
        org.junit.jupiter.api.Assertions.assertTrue(warmMaxC <= 0.36, "warm columns above c 0.36");
        org.junit.jupiter.api.Assertions.assertTrue(warmColumns == 0
                || largestWarm / (double) warmColumns >= 0.75,
            "warm region not cohesive: " + largestWarm + "/" + warmColumns);
        org.junit.jupiter.api.Assertions.assertTrue(mushroomFraction < 0.03,
            "mushrooms above 3% of land: " + mushroomFraction);
    }

    @Test
    void referenceMapEvidencePng() throws IOException {
        if (!WRITE) return;
        Files.createDirectories(EVIDENCE);
        ModernTerrainV2 terrain = referenceTerrain();
        int overviewPixels = (int) Math.round(HALF * 2.0 / 4.0);
        BufferedImage overview = new BufferedImage(overviewPixels, overviewPixels,
            BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < overviewPixels; py++) {
            double z = -HALF + (py + 0.5) * (HALF * 2.0 / overviewPixels);
            for (int px = 0; px < overviewPixels; px++) {
                double x = -HALF + (px + 0.5) * (HALF * 2.0 / overviewPixels);
                overview.setRGB(px, py, sampleRgb(terrain, x, z));
            }
        }
        ImageIO.write(overview, "png", EVIDENCE.resolve("reference_map_after.png").toFile());

        double coastX = HALF;
        for (double x = 0.0; x <= HALF; x += 4.0) {
            if (terrain.signedDistance(x, 0.0) <= 0.0) {
                coastX = x;
                break;
            }
        }
        BufferedImage closeup = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < 512; py++) {
            double z = -256.0 + py + 0.5;
            for (int px = 0; px < 512; px++) {
                closeup.setRGB(px, py, sampleRgb(terrain, coastX - 320.0 + px + 0.5, z));
            }
        }
        ImageIO.write(closeup, "png", EVIDENCE.resolve("preview_closeup_1block.png").toFile());

        BufferedImage transect = new BufferedImage(1024, 128, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < 128; py++) {
            double z = -64.0 + py + 0.5;
            for (int px = 0; px < 1024; px++) {
                transect.setRGB(px, py, sampleRgb(terrain, coastX - 512.0 + px + 0.5, z));
            }
        }
        ImageIO.write(transect, "png", EVIDENCE.resolve("shoreline_transect.png").toFile());
        System.out.println("PHASE82 evidence pngs written, coastX=" + coastX);
    }

    private static int sampleRgb(ModernTerrainV2 terrain, double x, double z) {
        double c = terrain.continentalness(x, z);
        IslandTerrain.Distances d = terrain.distances(x, z);
        IslandClimate.SampleResult res = IslandClimate.sampleV3(c, x, z, slope(terrain, x, z), 720.0,
            SEED, IslandClimate.GradientMode.NORTH_SOUTH, 1.0, "all", HALF, 3,
            d.signed(), d.main(), terrain.heightTierAt(x, z), terrain.climateLayout().sample(x, z),
            true, ShoreField.NONE);
        if (res.zone() == IslandClimate.SurfaceZone.OCEAN) return 0x0B3D91;
        if (res.zone() == IslandClimate.SurfaceZone.SHORE) return 0xE8DCA0;
        return BiomePreview.landTypeFromFamily(res.landFamily()).color;
    }

    @Test
    void dappledAndPaleAppearNaturally() {
        long paleSeed = 13579L;
        ModernTerrainV2 paleTerrain = sweepTerrain(paleSeed);
        int pale = 0;
        int paleLand = 0;
        for (double z = -HALF; z <= HALF; z += 8) {
            for (double x = -HALF; x <= HALF; x += 8) {
                double c = paleTerrain.continentalness(x, z);
                IslandTerrain.Distances d = paleTerrain.distances(x, z);
                if (d.main() <= 0.0) continue;
                IslandClimate.SampleResult res = IslandClimate.sampleV3(c, x, z, 0.0, 720.0,
                    paleSeed, IslandClimate.GradientMode.NORTH_SOUTH, 1.0, "all", HALF, 3,
                    d.signed(), d.main(), paleTerrain.heightTierAt(x, z),
                    paleTerrain.climateLayout().sample(x, z),
                    true, ShoreField.NONE);
                if (res.zone() != IslandClimate.SurfaceZone.LAND) continue;
                paleLand++;
                if (res.landFamily() == 17) pale++;
            }
        }
        assertTrue(pale > 0, "pale_garden must appear naturally on the pale fixture");
        assertTrue(pale / (double) paleLand >= 0.003 && pale / (double) paleLand <= 0.015,
            "pale_garden must stay in the 0.5-1.5% target band: " + pale + "/" + paleLand);
        System.out.println("PHASE82 pale pale=" + pale + " land=" + paleLand);

        IslandDraft draft = draftForParity();
        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        com.hack337.omniisle.worldgen.IslandDensityFunction previous =
            com.hack337.omniisle.worldgen.IslandDensityFunction.getActive();
        com.hack337.omniisle.worldgen.IslandDensityFunction.setActive(
            new com.hack337.omniisle.worldgen.IslandDensityFunction(cfg));
        try {
            net.minecraft.world.level.biome.Climate.Sampler sampler = TestWorldgen.sampler(terrain);
            com.hack337.omniisle.worldgen.OmniBiomeSource source = catalogSource(draft, 3);
            double halfSpan = draft.worldSpan() * 0.5;
            int family3 = 0;
            int dappled = 0;
            for (double z = -halfSpan; z <= halfSpan; z += 48) {
                for (double x = -halfSpan; x <= halfSpan; x += 48) {
                    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder =
                        source.getNoiseBiome((int) Math.floor(x) >> 2, 16,
                            (int) Math.floor(z) >> 2, sampler);
                    String path = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                    int family = com.hack337.omniisle.worldgen.OmniBiomeSource.landFamily(path);
                    if (family == 3) {
                        family3++;
                        if (path.equals("dappled_forest")) dappled++;
                    }
                }
            }
            assertTrue(family3 > 0, "no forest-family land in the reference scan");
            assertTrue(dappled > 0, "dappled_forest must appear in mid-temperate forest transitions");
            System.out.println("PHASE82 dappled=" + dappled + " family3=" + family3
                + " fraction=" + String.format(Locale.ROOT, "%.3f", dappled / (double) family3));
        } finally {
            com.hack337.omniisle.worldgen.IslandDensityFunction.setActive(previous);
        }
    }

    @Test
    void sulfurCavesInWarmShallowUndergroundV3() throws IOException {
        IslandDraft draft = draftForParity();
        draft.setGradientStrength(1.0);
        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        com.hack337.omniisle.worldgen.IslandDensityFunction previous =
            com.hack337.omniisle.worldgen.IslandDensityFunction.getActive();
        com.hack337.omniisle.worldgen.IslandDensityFunction.setActive(
            new com.hack337.omniisle.worldgen.IslandDensityFunction(cfg));
        try {
            net.minecraft.world.level.biome.Climate.Sampler sampler = TestWorldgen.sampler(terrain);
            com.hack337.omniisle.worldgen.OmniBiomeSource v3 = catalogSource(draft, 3);
            com.hack337.omniisle.worldgen.OmniBiomeSource v2 = catalogSource(draft, 2);
            double halfSpan = draft.worldSpan() * 0.5;
            int warm = 0;
            int warmDeepDarkBefore = 0;
            int warmSulfurAfter = 0;
            int coldChecked = 0;
            for (double z = -halfSpan; z <= halfSpan; z += 32) {
                for (double x = -halfSpan; x <= halfSpan; x += 32) {
                    IslandTerrain.Distances distances = terrain.distances(x, z);
                    if (distances.main() <= 0.0) continue;
                    if (distances.signed() <= ShoreField.MAX_INLAND + 8.0) continue;
                    double c = terrain.continentalness(x, z);
                    IslandClimate.SampleResult res = IslandClimate.sampleV3(c, x, z,
                        slope(terrain, x, z), 720.0, SEED,
                        IslandClimate.GradientMode.NORTH_SOUTH, 1.0, "all", HALF, 3,
                        distances.signed(), distances.main(), terrain.heightTierAt(x, z),
                        terrain.climateLayout().sample(x, z), true, ShoreField.NONE);
                    if (res.zone() != IslandClimate.SurfaceZone.LAND) continue;
                    int bx = (int) Math.floor(x) >> 2;
                    int bz = (int) Math.floor(z) >> 2;
                    boolean cavePatch = IslandClimate.fbm(x / 360.0, z / 360.0,
                        IslandClimate.saltedSeed(SEED, 901)) > -0.28;
                    if (!cavePatch) continue;
                    boolean deepDark = IslandClimate.fbm(x / 520.0, z / 520.0,
                        IslandClimate.saltedSeed(SEED, 977)) > 0.02;
                    if (res.temperature() > 0.70) {
                        warm++;
                        String path3 = path(v3.getNoiseBiome(bx, -4, bz, sampler));
                        if (deepDark) {
                            String path2 = path(v2.getNoiseBiome(bx, -4, bz, sampler));
                            if (path2.equals("deep_dark")) warmDeepDarkBefore++;
                        }
                        assertEquals("sulfur_caves", path3,
                            "v3 warm shallow cave must prefer sulfur at " + x + "," + z);
                        warmSulfurAfter++;
                    } else if (res.temperature() < 0.60 && coldChecked < 8) {
                        String path3 = path(v3.getNoiseBiome(bx, -4, bz, sampler));
                        assertFalse(path3.equals("sulfur_caves"),
                            "cold shallow cave must not be sulfur at " + x + "," + z);
                        coldChecked++;
                    }
                }
            }
            assertTrue(warm > 0 && warmSulfurAfter == warm,
                "warm shallow caves: " + warmSulfurAfter + "/" + warm);
            assertTrue(warmDeepDarkBefore > 0,
                "the fixture must include warm columns that were deep_dark before this change");
            if (WRITE) {
                Files.createDirectories(EVIDENCE);
                int beforeSulfur = warm - warmDeepDarkBefore;
                Files.writeString(EVIDENCE.resolve("cave_sulfur.csv"),
                    "variant,warm_shallow_columns,before_deep_dark,before_sulfur,after_sulfur,cold_checked\n"
                        + "v3_0.8.1," + warm + "," + warmDeepDarkBefore + "," + beforeSulfur + ","
                        + beforeSulfur + ",0\n"
                        + "v3_0.8.2," + warm + "," + warmDeepDarkBefore + "," + beforeSulfur + ","
                        + warmSulfurAfter + "," + coldChecked + "\n",
                    StandardCharsets.UTF_8);
            }
            System.out.println("PHASE82 sulfur warm=" + warmSulfurAfter + "/" + warm
                + " beforeDeepDark=" + warmDeepDarkBefore + " coldChecked=" + coldChecked);
        } finally {
            com.hack337.omniisle.worldgen.IslandDensityFunction.setActive(previous);
        }
    }

    private static com.hack337.omniisle.worldgen.OmniBiomeSource catalogSource(IslandDraft draft,
                                                                               int version) {
        List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> land =
            new java.util.ArrayList<>();
        for (String id : com.hack337.omniisle.continent.BiomeShare.allLandIds()) {
            land.add(TestWorldgen.holder(id));
        }
        List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> caves =
            List.of(TestWorldgen.holder("lush_caves"), TestWorldgen.holder("dripstone_caves"),
                TestWorldgen.holder("deep_dark"), TestWorldgen.holder("sulfur_caves"));
        List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> shore =
            List.of(TestWorldgen.holder("beach"), TestWorldgen.holder("stony_shore"),
                TestWorldgen.holder("snowy_beach"));
        List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> ocean =
            List.of(TestWorldgen.holder("ocean"), TestWorldgen.holder("deep_ocean"),
                TestWorldgen.holder("warm_ocean"), TestWorldgen.holder("lukewarm_ocean"),
                TestWorldgen.holder("deep_lukewarm_ocean"), TestWorldgen.holder("cold_ocean"),
                TestWorldgen.holder("deep_cold_ocean"), TestWorldgen.holder("frozen_ocean"),
                TestWorldgen.holder("deep_frozen_ocean"));
        return new com.hack337.omniisle.worldgen.OmniBiomeSource(
            land, land, shore, shore, ocean, ocean,
            List.of(), List.of(), caves, caves,
            draft.climateScale(), draft.biomeSeed(), version,
            draft.gradientMode().name().toLowerCase(Locale.ROOT), draft.gradientStrength(),
            draft.theme().name().toLowerCase(Locale.ROOT),
            Optional.of(draft.shoreSettings()), Optional.empty());
    }

    private static String path(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder) {
        return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
    }

    private static ModernTerrainV2 sweepTerrain(long seed) {
        IslandMask mask = referenceMask();
        IslandShapeConfig cfg = new IslandShapeConfig(3, 0.25, 0.35, false, 1000,
            Optional.of(referenceHeight()), Optional.empty(), List.of(), Optional.of(mask), seed,
            Optional.of(ShoreSettings.DEFAULT));
        return new ModernTerrainV2(cfg);
    }

    @Test
    void dryShelfIsLandNotOcean() {
        ModernTerrainV2 terrain = referenceTerrain();
        int shelfLand = 0;
        int shelfOcean = 0;
        StringBuilder probe = new StringBuilder("ray,d=0_c,d=-42_c,zone_at_0\n");
        for (int ray = 0; ray < 24; ray++) {
            double angle = ray * Math.PI * 2.0 / 24.0;
            double x0 = rayDistance(terrain, angle, 0.0);
            double x42 = rayDistance(terrain, angle, -42.0);
            double c0 = terrain.continentalness(x0, rayZ(terrain, angle, 0.0));
            double c42 = terrain.continentalness(x42, rayZ(terrain, angle, -42.0));
            IslandClimate.SampleResult res = IslandClimate.sampleV3(c0, x0, rayZ(terrain, angle, 0.0),
                0.0, 720.0, SEED, IslandClimate.GradientMode.NORTH_SOUTH, 1.0, "all", HALF, 3,
                0.0, 1.0, terrain.heightTierAt(x0, rayZ(terrain, angle, 0.0)),
                terrain.climateLayout().sample(x0, rayZ(terrain, angle, 0.0)),
                true, ShoreField.NONE);
            probe.append(String.format(Locale.ROOT, "%d,%.4f,%.4f,%s%n", ray, c0, c42, res.zone()));
            org.junit.jupiter.api.Assertions.assertNotEquals(IslandClimate.SurfaceZone.OCEAN, res.zone(),
                "dry shelf at signedDistance 0 must not be ocean (ray " + ray + ")");
            org.junit.jupiter.api.Assertions.assertTrue(c0 >= IslandClimate.V3_SEA_LEVEL_CONTINENTALNESS,
                "c at the synthetic waterline " + c0);
            if (res.zone() == IslandClimate.SurfaceZone.LAND) shelfLand++;
            else if (res.zone() == IslandClimate.SurfaceZone.OCEAN) shelfOcean++;
        }
        for (int z = -(int) HALF; z <= HALF; z += 64) {
            for (int x = -(int) HALF; x <= HALF; x += 64) {
                double d = terrain.signedDistance(x, z);
                if (d > -42.0 && d <= 0.0) {
                    double c = terrain.continentalness(x, z);
                    if (c < IslandClimate.V3_SEA_LEVEL_CONTINENTALNESS) continue;
                    IslandClimate.SampleResult res = IslandClimate.sampleV3(c, x, z, 0.0, 720.0,
                        SEED, IslandClimate.GradientMode.NORTH_SOUTH, 1.0, "all", HALF, 3,
                        d, terrain.distances(x, z).main(), terrain.heightTierAt(x, z),
                        terrain.climateLayout().sample(x, z), true, ShoreField.NONE);
                    if (res.zone() == IslandClimate.SurfaceZone.OCEAN) shelfOcean++;
                    else shelfLand++;
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, shelfOcean, "dry shelf ocean columns");
        org.junit.jupiter.api.Assertions.assertTrue(shelfLand > 0, "dry shelf land columns");
        if (WRITE) {
            write("shoreline_probe.csv", probe.toString());
        }
    }

    @Test
    void mountainBandsMatchPhysicalRidge() {
        ModernTerrainV2 terrain = referenceTerrain();
        double ridgeSum = 0.0, mSum = 0.0, ridgeSq = 0.0, mSq = 0.0, cross = 0.0;
        int n = 0;
        int swampOnRidge = 0;
        for (int z = -(int) HALF; z <= HALF; z += 32) {
            for (int x = -(int) HALF; x <= HALF; x += 32) {
                double c = terrain.continentalness(x, z);
                IslandClimate.SampleResult res = IslandClimate.sampleV3(c, x, z,
                    slope(terrain, x, z), 720.0, SEED,
                    IslandClimate.GradientMode.NORTH_SOUTH, 1.0, "all", HALF, 3,
                    terrain.distances(x, z).signed(), terrain.distances(x, z).main(),
                    terrain.heightTierAt(x, z), terrain.climateLayout().sample(x, z), true,
                    ShoreField.NONE);
                double ridge = Math.max(0.0, 1.0 - 2.0 * Math.abs(IslandNoise.fbm2(
                    x / 750.0, z / 750.0, SEED + 801L, 3, 2.0, 0.5)));
                ridgeSum += ridge;
                mSum += res.mountains();
                ridgeSq += ridge * ridge;
                mSq += res.mountains() * res.mountains();
                cross += ridge * res.mountains();
                n++;
                if ((res.landFamily() == 8 || res.landFamily() == 9) && res.mountains() > 0.42) {
                    swampOnRidge++;
                }
            }
        }
        double rMean = ridgeSum / n;
        double mMean = mSum / n;
        double corr = (cross / n - rMean * mMean)
            / (Math.sqrt(ridgeSq / n - rMean * rMean) * Math.sqrt(mSq / n - mMean * mMean));
        System.out.println("PHASE82 ridge correlation=" + corr + " swampsOnRidge=" + swampOnRidge);
        org.junit.jupiter.api.Assertions.assertTrue(corr > 0.9, "ridge correlation " + corr);
        org.junit.jupiter.api.Assertions.assertEquals(0, swampOnRidge);
    }

    @Test
    void previewSourceZoneFamilyParityV3() {
        IslandDraft draft = draftForParity();
        IslandShapeConfig cfg = TestWorldgen.config(draft, 3, 0.25);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        net.minecraft.world.level.biome.Climate.Sampler sampler = TestWorldgen.sampler(terrain);
        com.hack337.omniisle.worldgen.OmniBiomeSource source = TestWorldgen.biomeSource(
            720, draft.biomeSeed(), 3, "north_south", 0.5, "standard",
            draft.shoreSettings(), true);
        com.hack337.omniisle.worldgen.IslandDensityFunction previous =
            com.hack337.omniisle.worldgen.IslandDensityFunction.getActive();
        com.hack337.omniisle.worldgen.IslandDensityFunction.setActive(
            new com.hack337.omniisle.worldgen.IslandDensityFunction(cfg));
        try {
            int compared = 0;
            int matches = 0;
            double halfSpan = draft.worldSpan() * 0.5;
            for (int gz = -40; gz <= 40; gz++) {
                for (int gx = -40; gx <= 40; gx++) {
                    double x = gx * halfSpan / 40.0;
                    double z = gz * halfSpan / 40.0;
                    double c = terrain.continentalness(x, z);
                    BiomePreview.Selection preview = BiomePreview.selectionV3(
                        c, x, z, draft.biomeSeed(), draft.climateScale(),
                        draft.gradientMode(), draft.gradientStrength(), draft.theme(),
                        0.0, 3, halfSpan, terrain.rivers(), terrain, draft.shoreSettings(), true);
                    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder =
                        source.getNoiseBiome((int) Math.floor(x) >> 2, 16,
                            (int) Math.floor(z) >> 2, sampler);
                    String path = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                    int realFamily = com.hack337.omniisle.worldgen.OmniBiomeSource.landFamily(path);
                    boolean realLand = realFamily >= 0;
                    boolean previewLand = preview.type() != null && preview.type().isLand();
                    compared++;
                    if (realLand == previewLand && (!realLand || realFamily == preview.landFamily())) {
                        matches++;
                    }
                }
            }
            double parity = matches / (double) compared;
            System.out.println("PHASE82 preview/source parity=" + parity);
            org.junit.jupiter.api.Assertions.assertTrue(parity >= 0.97, "parity " + parity);
        } finally {
            com.hack337.omniisle.worldgen.IslandDensityFunction.setActive(previous);
        }
    }

    private static IslandDraft draftForParity() {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setIslandSize(SIZE);
        draft.setShapeSeed(SEED);
        draft.setBiomeSeed(SEED);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        return draft;
    }

    private static void write(String name, String contents) {
        try {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve(name), contents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static double rayDistance(ModernTerrainV2 terrain, double angle, double target) {
        double t = 0.0;
        while (t < 3200.0) {
            if (terrain.signedDistance(Math.cos(angle) * t, Math.sin(angle) * t) <= target) break;
            t += 1.0;
        }
        return Math.cos(angle) * t;
    }

    private static double rayZ(ModernTerrainV2 terrain, double angle, double target) {
        double t = 0.0;
        while (t < 3200.0) {
            if (terrain.signedDistance(Math.cos(angle) * t, Math.sin(angle) * t) <= target) break;
            t += 1.0;
        }
        return Math.sin(angle) * t;
    }

    private static double fraction(TreeMap<Integer, Integer> counts, int land,
                                   java.util.function.IntPredicate predicate) {
        int sum = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (predicate.test(e.getKey())) sum += e.getValue();
        }
        return land == 0 ? 0.0 : sum / (double) land;
    }

    private static int largestComponent(boolean[][] grid) {
        int rows = grid.length;
        int cols = rows == 0 ? 0 : grid[0].length;
        boolean[][] seen = new boolean[rows][cols];
        int best = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!grid[r][c] || seen[r][c]) continue;
                int size = 0;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{r, c});
                seen[r][c] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    size++;
                    int[][] neighbours = {{cell[0] + 1, cell[1]}, {cell[0] - 1, cell[1]},
                        {cell[0], cell[1] + 1}, {cell[0], cell[1] - 1}};
                    for (int[] nb : neighbours) {
                        if (nb[0] < 0 || nb[0] >= rows || nb[1] < 0 || nb[1] >= cols) continue;
                        if (grid[nb[0]][nb[1]] && !seen[nb[0]][nb[1]]) {
                            seen[nb[0]][nb[1]] = true;
                            queue.add(nb);
                        }
                    }
                }
                best = Math.max(best, size);
            }
        }
        return best;
    }

    private static boolean isCoreTemperate(int family) {
        return family == 2 || family == 3 || family == 4 || family == 7 || family == 17;
    }

    private static boolean isWarmArid(int family) {
        return family == 11 || family == 12 || family == 13;
    }

    private static boolean isGreen(int family) {
        return family == 1 || family == 2 || family == 3 || family == 4 || family == 5
            || family == 6 || family == 7 || family == 17;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static ModernTerrainV2 referenceTerrain() {
        IslandMask mask = referenceMask();
        IslandShapeConfig cfg = new IslandShapeConfig(3, 0.25, 0.35, false, 1000,
            Optional.of(referenceHeight()), Optional.empty(), List.of(), Optional.of(mask), SEED,
            Optional.of(ShoreSettings.DEFAULT));
        return new ModernTerrainV2(cfg);
    }

    private static double slope(IslandTerrain terrain, double x, double z) {
        double e = terrain.continentalness(x + 8, z);
        double w = terrain.continentalness(x - 8, z);
        double s = terrain.continentalness(x, z + 8);
        double n = terrain.continentalness(x, z - 8);
        return Math.hypot((e - w) / 16.0, (s - n) / 16.0) * 64.0;
    }

    private static IslandMask referenceMask() {
        boolean[] land = new boolean[RES * RES];
        double cx = RES / 2.0;
        double cz = RES / 2.0;
        double radius = RES * 0.35;
        for (int z = 0; z < RES; z++) {
            for (int x = 0; x < RES; x++) {
                double dx = x + 0.5 - cx;
                double dz = z + 0.5 - cz;
                land[z * RES + x] = dx * dx + dz * dz <= radius * radius;
            }
        }
        byte[] packed = new byte[(land.length + 7) / 8];
        for (int i = 0; i < land.length; i++) {
            if (land[i]) packed[i >>> 3] |= (byte) (1 << (i & 7));
        }
        return new IslandMask(RES, RES, -HALF, HALF, -HALF, HALF, 192.0, 400.0,
            Base64.getEncoder().encodeToString(packed));
    }

    private static IslandShapeConfig.HeightLayer referenceHeight() {
        byte[] h = new byte[RES * RES];
        int cx = RES / 2;
        int cz = RES / 2;
        for (int z = cz - 52; z <= cz + 16; z++) {
            if (z < 0 || z >= RES) continue;
            double spine = cx + Math.sin((z - cz) * 0.16) * 9.0;
            for (int x = 0; x < RES; x++) {
                double dist = Math.abs(x - spine);
                if (dist <= 7.0) h[z * RES + x] = 3;
                else if (dist <= 15.0) h[z * RES + x] = 2;
            }
        }
        byte[] packed = new byte[(h.length + 3) / 4];
        for (int i = 0; i < h.length; i++) {
            packed[i >>> 2] |= (byte) ((h[i] & 3) << ((i & 3) * 2));
        }
        return new IslandShapeConfig.HeightLayer(2, Base64.getEncoder().encodeToString(packed));
    }
}
