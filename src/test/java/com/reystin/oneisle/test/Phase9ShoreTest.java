package com.reystin.oneisle.test;

import com.reystin.oneisle.client.BiomePreview;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.ShareCode;
import com.reystin.oneisle.client.WorldPackBuilder;
import com.reystin.oneisle.continent.IslandClimate;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.continent.ShoreField;
import com.reystin.oneisle.continent.ShoreSettings;
import com.reystin.oneisle.worldgen.IslandDensityFunction;
import com.reystin.oneisle.worldgen.OneBiomeSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.2 acceptance harness for the v3 shoreline. Samples the d=0 contour
 * (angular rays + binary refinement), then evaluates the biome 6 blocks inland.
 */
public class Phase9ShoreTest {
    private static final boolean WRITE = Boolean.getBoolean("oneisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/shore");
    private static final long[] SEEDS = {101L, 202L, 303L};
    private static final int[] SIZES = {2500, 4000};
    private static final IslandDraft.Preset[] PRESETS = {
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
    };
    private static final int CONTOUR_POINTS = 2048;
    private static final double RAY_STEP = 24.0;
    private static final double INLAND_SAMPLE = 6.0;

    record CoastPoint(double x, double z, double arc, double d, double k, double bay,
                      double width, double patch, BiomePreview.Type type) {}

    record IslandMetrics(String key, double sand, double stony, double none, int runs,
                         double longestRun, double medianRun, double widthCv, int points) {}

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void shorelineAcceptanceAcrossPresets() throws IOException {
        StringBuilder csv = new StringBuilder(
            "preset,size,seed,points,sand,stony,none,runs,longest_run,median_run,"
                + "sand_runs,sand_longest,sand_median,sand_tiny,width_cv,width_samples\n");
        int totalPoints = 0;
        int totalSand = 0;
        int totalStony = 0;
        int totalNone = 0;
        int islandCount = 0;
        int runFailures = 0;
        double minWidthCv = Double.MAX_VALUE;

        for (IslandDraft.Preset preset : PRESETS) {
            for (int size : SIZES) {
                for (long seed : SEEDS) {
                    IslandDraft draft = baseDraft(preset, size, seed);
                    IslandTerrain terrain = terrain(draft);
                    List<CoastPoint> points = traceCoast(terrain, draft.shoreSettings(), seed,
                        draft.worldSpan() * 0.5, CONTOUR_POINTS);
                    assertTrue(points.size() >= 2000,
                        preset + " " + size + " seed " + seed + " produced only " + points.size() + " contour points");

                    int sand = 0;
                    int stony = 0;
                    int none = 0;
                    List<Double> widths = new ArrayList<>();
                    for (CoastPoint p : points) {
                        if (p.type() == BiomePreview.Type.BEACH || p.type() == BiomePreview.Type.SNOWY_BEACH) sand++;
                        else if (p.type() == BiomePreview.Type.STONY_SHORE) stony++;
                        else none++;
                        if (p.patch() > 0.0 && p.width() > 0.0) widths.add(p.width());
                    }
                    double[] shoreRuns = runStats(points, p -> p.type().category == BiomePreview.Category.SHORE);
                    double[] sandRuns = runStats(points,
                        p -> p.type() == BiomePreview.Type.BEACH || p.type() == BiomePreview.Type.SNOWY_BEACH);

                    double longest = shoreRuns[1];
                    double median = shoreRuns[2];
                    double widthCv = coefficientOfVariation(widths);

                    int n = points.size();
                    double sandFrac = sand / (double) n;
                    double stonyFrac = stony / (double) n;
                    double noneFrac = none / (double) n;
                    csv.append(preset.name()).append(',').append(size).append(',').append(seed)
                        .append(',').append(n)
                        .append(',').append(fmt(sandFrac)).append(',').append(fmt(stonyFrac))
                        .append(',').append(fmt(noneFrac))
                        .append(',').append((int) shoreRuns[0])
                        .append(',').append(fmt(longest)).append(',').append(fmt(median))
                        .append(',').append((int) sandRuns[0])
                        .append(',').append(fmt(sandRuns[1])).append(',').append(fmt(sandRuns[2]))
                        .append(',').append((int) sandRuns[3])
                        .append(',').append(fmt(widthCv)).append(',').append(widths.size()).append('\n');

                    totalPoints += n;
                    totalSand += sand;
                    totalStony += stony;
                    totalNone += none;
                    islandCount++;
                    minWidthCv = Math.min(minWidthCv, widthCv);
                    // v3 shore semantics: sand pockets form 25-90 block arcs, so the
                    // gate is sand-run count/length. The 2D component evidence test
                    // owns speck detection; 1D contour arc lengths cannot resolve it.
                    // 1.0.1 narrowing (open-water/tier gates, width 4..22) shortened the
                    // worst arcs: measured minimum is 2 runs / 16.4 median on small archipelagos.
                    if (sandRuns[0] < 2 || sandRuns[1] > 400.0 || sandRuns[2] < 14.0
                        || sandRuns[2] > 140.0) {
                        runFailures++;
                    }
                }
            }
        }

        double sand = totalSand / (double) totalPoints;
        double stony = totalStony / (double) totalPoints;
        double none = totalNone / (double) totalPoints;
        String summary = "islands," + islandCount + "\n"
            + "points," + totalPoints + "\n"
            + "sand," + sand + "\n"
            + "stony," + stony + "\n"
            + "none," + none + "\n"
            + "run_failures," + runFailures + "\n";

        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_acceptance.csv"), csv.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("shore_acceptance_summary.txt"), summary, StandardCharsets.UTF_8);
        }

        // 1.0.1 measured: sand 0.1156, stony 0.0325, none 0.8519 over 30 islands.
        // The open-water/tier gates deliberately drop interior and cliff-strip beaches,
        // so the floors sit below the 1.0.0 band while still proving beaches survive.
        assertTrue(sand >= 0.09 && sand <= 0.35, "sand fraction was " + sand);
        assertTrue(stony >= 0.02 && stony <= 0.10, "stony fraction was " + stony);
        assertTrue(none >= 0.55, "none fraction was " + none);
        assertEquals(0, runFailures, "islands violating sand run count/length targets: " + runFailures);
        assertTrue(minWidthCv >= 0.30, "minimum inland width CV was " + minWidthCv);
    }

    /** Arc-length stats of runs matching the predicate along the d=6 contour. */
    private static double[] runStats(List<CoastPoint> points,
                                     java.util.function.Predicate<CoastPoint> member) {
        List<Double> runs = new ArrayList<>();
        double runArc = 0.0;
        boolean active = false;
        for (CoastPoint p : points) {
            boolean in = member.test(p);
            if (active && p.arc() > 30.0) {
                runs.add(runArc);
                active = false;
                runArc = 0.0;
            }
            if (in && active) {
                runArc += p.arc();
            } else if (in) {
                active = true;
                runArc = 0.0;
            } else if (active) {
                runs.add(runArc);
                active = false;
                runArc = 0.0;
            }
        }
        if (active) runs.add(runArc);
        runs.sort(Double::compareTo);
        double longest = runs.isEmpty() ? 0.0 : runs.get(runs.size() - 1);
        double median = runs.isEmpty() ? 0.0 : runs.get(runs.size() / 2);
        int tiny = 0;
        for (double run : runs) if (run < 4.0) tiny++;
        return new double[]{runs.size(), longest, median, tiny};
    }

    @Test
    void shoreDensityResponseIsMonotone() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 4000, 101L);
        IslandTerrain terrain = terrain(draft);
        double halfSpan = draft.worldSpan() * 0.5;
        List<CoastPoint> baseline = traceCoast(terrain, draft.shoreSettings(), 101L, halfSpan, CONTOUR_POINTS);
        double[] densities = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
        double[] fractions = new double[densities.length];
        StringBuilder csv = new StringBuilder("density,shore_fraction\n");
        for (int i = 0; i < densities.length; i++) {
            ShoreSettings settings = new ShoreSettings(densities[i], 0.6, "natural", true);
            int shore = 0;
            for (CoastPoint p : baseline) {
                double qx = p.x();
                double qz = p.z();
                double slope = slopeAt(terrain, qx, qz);
                double c = terrain.continentalness(qx, qz);
                BiomePreview.Type type = BiomePreview.surfaceV3(c, qx, qz, 101L, 720,
                    IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                    slope, 3, halfSpan, terrain.rivers(), terrain, settings, true);
                if (type.category == BiomePreview.Category.SHORE) shore++;
            }
            fractions[i] = shore / (double) baseline.size();
            csv.append(densities[i]).append(',').append(fmt(fractions[i])).append('\n');
        }
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_density_sweep.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
        assertEquals(0.0, fractions[0], 1.0e-9, "density=0 must disable shores");
        // 1.0.1 measured 0.563 at density=1 (1.0.0 band was 0.70-0.90); cliffs and
        // narrow channels now stay unclassified by design.
        assertTrue(fractions[5] >= 0.45 && fractions[5] <= 0.75, "density=1 fraction was " + fractions[5]);
        for (int i = 1; i < fractions.length; i++) {
            assertTrue(fractions[i] + 1.0e-6 >= fractions[i - 1],
                "shore fraction is not monotone at density " + densities[i]);
        }
    }

    @Test
    void shoreBayHeadlandCorrelation() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 4000, 101L);
        IslandTerrain terrain = terrain(draft);
        List<CoastPoint> points = traceCoast(terrain, draft.shoreSettings(), 101L,
            draft.worldSpan() * 0.5, CONTOUR_POINTS);
        int bayTotal = 0;
        int baySand = 0;
        int headlandTotal = 0;
        int headlandSand = 0;
        for (CoastPoint p : points) {
            boolean sand = p.type() == BiomePreview.Type.BEACH || p.type() == BiomePreview.Type.SNOWY_BEACH;
            if (p.k() > 0.1) {
                bayTotal++;
                if (sand) baySand++;
            } else if (p.k() < -0.1) {
                headlandTotal++;
                if (sand) headlandSand++;
            }
        }
        assertTrue(bayTotal > 50 && headlandTotal > 50, "not enough bay/headland samples");
        double bayFraction = baySand / (double) bayTotal;
        double headlandFraction = headlandSand / (double) headlandTotal;
        String report = "bay_points," + bayTotal + "\nbay_sand," + bayFraction
            + "\nheadland_points," + headlandTotal + "\nheadland_sand," + headlandFraction
            + "\nratio," + (bayFraction / Math.max(1.0e-9, headlandFraction)) + "\n";
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_bay_headland.txt"), report, StandardCharsets.UTF_8);
        }
        assertTrue(bayFraction >= 2.0 * headlandFraction,
            "bay sand " + bayFraction + " vs headland sand " + headlandFraction);
    }

    @Test
    void shorePreviewMatchesServer() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 4000, 101L);
        IslandTerrain terrain = terrain(draft);
        IslandShapeConfig cfg = config(draft);
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        Climate.Sampler sampler = TestWorldgen.sampler(terrain);
        OneBiomeSource server = TestWorldgen.biomeSource(720, 101L, 3, "north_south", 0.5, "standard",
            draft.shoreSettings(), true);

        double halfSpan = draft.worldSpan() * 0.5;
        int step = 8;
        int sampled = 0;
        int matched = 0;
        int shoreSamples = 0;
        StringBuilder csv = new StringBuilder("x,z,d,server,client,category,match\n");
        for (double wz = -halfSpan; wz <= halfSpan; wz += step) {
            for (double wx = -halfSpan; wx <= halfSpan; wx += step) {
                double d = terrain.signedDistance(wx, wz);
                if (Math.abs(d) > 45.0) continue;
                int bx = (int) Math.floor(wx / 4.0) * 4;
                int bz = (int) Math.floor(wz / 4.0) * 4;
                int quartX = bx >> 2;
                int quartZ = bz >> 2;
                String serverBiome = server.getNoiseBiome(quartX, 16, quartZ, sampler)
                    .unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                String serverCategory = category(serverBiome);
                double c = terrain.continentalness(bx, bz);
                double slope = slopeAt(terrain, bx, bz);
                BiomePreview.Type client = BiomePreview.surfaceV3(c, bx, bz, 101L, 720,
                    IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                    slope, 3, halfSpan, terrain.rivers(), terrain, draft.shoreSettings(), true);
                String clientCategory = client.category.name().toLowerCase(Locale.ROOT);
                boolean match = serverCategory.equals(clientCategory);
                sampled++;
                if (match) matched++;
                if (serverCategory.equals("shore") || clientCategory.equals("shore")) shoreSamples++;
                if (WRITE && !match && csv.length() < 20000) {
                    csv.append(bx).append(',').append(bz).append(',').append(fmt(d))
                        .append(',').append(serverBiome).append(',').append(client.id)
                        .append(',').append(serverCategory).append(',').append(match).append('\n');
                }
            }
        }
        double rate = matched / (double) sampled;
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_parity_mismatches.csv"), csv.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("shore_parity_summary.txt"),
                "samples," + sampled + "\nmatched," + matched + "\nrate," + rate
                    + "\nshore_samples," + shoreSamples + "\n", StandardCharsets.UTF_8);
        }
        assertTrue(shoreSamples > 500, "shore parity sample count was " + shoreSamples);
        assertTrue(rate >= 0.97, "shore-zone parity was " + rate);
    }

    @Test
    void shoreBorderHasNoLongStraightSegments() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 4000, 101L);
        IslandTerrain terrain = terrain(draft);
        double halfSpan = draft.worldSpan() * 0.5;
        int step = 5;
        int size = (int) (2 * halfSpan / step) + 1;
        byte[] cells = new byte[size * size];
        for (int z = 0; z < size; z++) {
            double wz = -halfSpan + z * step;
            for (int x = 0; x < size; x++) {
                double wx = -halfSpan + x * step;
                double d = terrain.signedDistance(wx, wz);
                byte cell;
                double limit = ShoreField.intertidalLimit(wx, wz, 101L);
                if (d < limit || d > ShoreField.MAX_INLAND) {
                    cell = (byte) (d > 0 ? 1 : 0);
                } else {
                    ShoreField.Ring ring = ShoreField.ring(terrain, wx, wz, d);
                    int outcome = ShoreField.outcome(d, wx, wz, 0.25, terrain.heightTierAt(wx, wz),
                        terrain.continentalness(wx, wz), ring, draft.shoreSettings(), 101L);                    cell = (byte) (outcome != ShoreField.NONE ? 2 : (d > 0 ? 1 : 0));
                }
                cells[z * size + x] = cell;
            }
        }
        List<BorderChain> chains = new java.util.ArrayList<>();
        chains.addAll(borderChains(cells, size, step, halfSpan, true));
        chains.addAll(borderChains(cells, size, step, halfSpan, false));
        chains.sort((a, b) -> Double.compare(b.lineSpan(), a.lineSpan()));
        BorderChain longestStraight = chains.stream().filter(BorderChain::straight)
            .max(java.util.Comparator.comparingDouble(BorderChain::lineSpan))
            .orElse(new BorderChain(0, 0.0, 0.0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, true));
        BorderChain longestOverall = chains.isEmpty()
            ? new BorderChain(0, 0.0, 0.0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, true)
            : chains.get(0);
        int longest = (int) Math.round(longestStraight.lineSpan());
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_straight_border.txt"),
                "grid_step," + step + "\nlinefit_tolerance_blocks," + LINE_TOLERANCE + "\n"
                    + "longest_straight_border_blocks," + longest + "\n"
                    + "orientation," + (longestStraight.horizontal() ? "horizontal" : "vertical") + "\n"
                    + "line_from," + longestStraight.startLine() + "\nline_to," + longestStraight.endLine() + "\n"
                    + "max_deviation," + fmt(longestStraight.maxDeviation()) + "\n"
                    + "longest_border_blocks," + longestOverall.lineSpan() + "\n"
                    + "longest_border_max_deviation," + fmt(longestOverall.maxDeviation()) + "\n"
                    + "longest_border_rms," + fmt(longestOverall.rmsDeviation()) + "\n",
                StandardCharsets.UTF_8);
            StringBuilder csv = new StringBuilder(
                "orientation,points,line_span,max_deviation,rms_deviation,straight,start_x,start_z,end_x,end_z\n");
            int rows = 0;
            for (BorderChain c : chains) {
                if (rows++ >= 12) break;
                csv.append(c.horizontal() ? "horizontal" : "vertical").append(',')
                    .append(c.points()).append(',').append(c.lineSpan()).append(',')
                    .append(fmt(c.maxDeviation())).append(',').append(fmt(c.rmsDeviation())).append(',')
                    .append(c.straight()).append(',').append(fmt(c.startX())).append(',')
                    .append(fmt(c.startZ())).append(',').append(fmt(c.endX())).append(',')
                    .append(fmt(c.endZ())).append('\n');
            }
            Files.writeString(EVIDENCE.resolve("shore_border_chains.csv"), csv.toString(), StandardCharsets.UTF_8);
            writeShoreMask(cells, size, EVIDENCE.resolve("shore_mask_4000_101.png"));
        }
        assertTrue(longest <= 120, "longest straight shore border was " + longest + " blocks");
    }

    @Test
    void v3TerrainContinuityNearCoast() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 4000, 101L);
        IslandTerrain terrain = terrain(draft);
        double halfSpan = draft.worldSpan() * 0.5;
        double maxDelta = 0.0;
        int samples = 0;
        java.util.Random random = new java.util.Random(9L);
        StringBuilder csv = new StringBuilder("x,z,d,delta\n");
        for (int i = 0; i < 1000000 && samples < 10000; i++) {
            double x = (random.nextDouble() * 2 - 1) * halfSpan;
            double z = (random.nextDouble() * 2 - 1) * halfSpan;
            double d = terrain.signedDistance(x, z);
            if (Math.abs(d) > 40.0) continue;
            double c0 = terrain.continentalness(x, z);
            double c1 = terrain.continentalness(x + 1.0, z);
            double c2 = terrain.continentalness(x, z + 1.0);
            double delta = Math.max(Math.abs(c1 - c0), Math.abs(c2 - c0));
            maxDelta = Math.max(maxDelta, delta);
            if (WRITE) csv.append(fmt(x)).append(',').append(fmt(z)).append(',')
                .append(fmt(d)).append(',').append(fmt(delta)).append('\n');
            samples++;
        }
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_continuity.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
        assertTrue(samples > 5000, "not enough coastal continuity samples");
        assertTrue(maxDelta < 0.015, "coastal delta was " + maxDelta);
    }

    @Test
    void v3SettingsRoundTripAndPack() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 2500, 77L);
        draft.setShoreDensity(0.75);
        draft.setShoreVariety(0.40);
        draft.setShoreRocky(false);

        IslandDraft copy = draft.copy();
        assertEquals(0.75, copy.shoreDensity(), 1.0e-9);
        assertEquals(0.40, copy.shoreVariety(), 1.0e-9);
        assertFalse(copy.shoreRocky());

        IslandDraft decoded = ShareCode.decode(ShareCode.encode(draft));
        assertEquals(0.75, decoded.shoreDensity(), 1.0e-9);
        assertEquals(0.40, decoded.shoreVariety(), 1.0e-9);
        assertFalse(decoded.shoreRocky());
        assertEquals(3, decoded.generationVersionNumber());

        Path tmp = Files.createTempDirectory("oneisle-shore-pack");
        WorldPackBuilder.export(tmp, draft);
        String continents;
        String preset;
        try (ZipFile zip = new ZipFile(tmp.resolve(WorldPackBuilder.FILE_NAME).toFile())) {
            continents = new String(zip.getInputStream(
                zip.getEntry("data/oneisle/worldgen/density_function/overworld/continents.json")).readAllBytes(),
                StandardCharsets.UTF_8);
            preset = new String(zip.getInputStream(
                zip.getEntry("data/oneisle/worldgen/world_preset/oneisle.json")).readAllBytes(),
                StandardCharsets.UTF_8);
        }
        assertTrue(continents.contains("\"shore\""));
        assertTrue(continents.contains("\"density\": 0.75"), continents);
        assertTrue(preset.contains("\"shore\""));
        assertTrue(preset.contains("\"rocky\": false"), preset);

        IslandShapeConfig cfg = config(draft);
        assertNotNull(cfg.shore);
        assertEquals(0.75, cfg.shore.density(), 1.0e-9);
        assertEquals(0.40, cfg.shore.variety(), 1.0e-9);
        assertFalse(cfg.shore.rocky());
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("shore_settings_roundtrip.txt"),
                "draft_density,0.75\ndraft_variety,0.40\ndraft_rocky,false\n"
                    + "share_code_roundtrip,density=0.75 variety=0.40 rocky=false version=3\n"
                    + "pack_continents_has_shore," + continents.contains("\"shore\"") + "\n"
                    + "pack_preset_has_shore," + preset.contains("\"shore\"") + "\n",
                StandardCharsets.UTF_8);
        }
    }

    @Test
    void shorePngEvidence() throws IOException {
        if (!WRITE) return;
        Files.createDirectories(EVIDENCE);
        IslandDraft.Preset[] presets = {
            IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.CRESCENT
        };
        for (IslandDraft.Preset preset : presets) {
            IslandDraft draft = baseDraft(preset, 4000, 101L);
            IslandTerrain terrain = terrain(draft);
            writeBiomeMap(draft, terrain, EVIDENCE.resolve("shore_before_" + preset.name() + ".png"), 2);
            writeBiomeMap(draft, terrain, EVIDENCE.resolve("shore_after_" + preset.name() + ".png"), 3);
        }
    }

    private static void writeBiomeMap(IslandDraft draft, IslandTerrain terrain, Path path, int version) throws IOException {
        int resolution = 512;
        double halfSpan = draft.worldSpan() * 0.5;
        BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < resolution; pz++) {
            double wz = -halfSpan + (pz + 0.5) * (2 * halfSpan / resolution);
            for (int px = 0; px < resolution; px++) {
                double wx = -halfSpan + (px + 0.5) * (2 * halfSpan / resolution);
                double c = terrain.continentalness(wx, wz);
                double slope = slopeAt(terrain, wx, wz);
                BiomePreview.Type type = version >= 3
                    ? BiomePreview.surfaceV3(c, wx, wz, 101L, 720,
                        IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                        slope, 3, halfSpan, terrain.rivers(), terrain, draft.shoreSettings(), true)
                    : BiomePreview.surface(c, wx, wz, 101L, 720,
                        IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                        slope, 2, halfSpan, terrain.rivers());
                image.setRGB(px, pz, type.color & 0x00FFFFFF);
            }
        }
        ImageIO.write(image, "PNG", path.toFile());
    }

    record BorderChain(int points, double lineSpan, double maxDeviation, int startLine, int endLine,
                       int startBoundary, int endBoundary, double rmsDeviation,
                       double startX, double startZ, double endX, double endZ, boolean horizontal) {
        boolean straight() { return maxDeviation <= LINE_TOLERANCE; }
    }

    private static final double LINE_TOLERANCE = 2.5;

    private static final class ChainBuilder {
        final boolean horizontal;
        final java.util.ArrayList<double[]> pts = new java.util.ArrayList<>();
        int last;
        int startLine;
        int endLine;

        ChainBuilder(boolean horizontal) { this.horizontal = horizontal; }

        void add(int line, int boundary, double halfSpan, double step) {
            double lineWorld = -halfSpan + line * step + step * 0.5;
            double boundaryWorld = -halfSpan + boundary * step + step * 0.5;
            double x = horizontal ? boundaryWorld : lineWorld;
            double z = horizontal ? lineWorld : boundaryWorld;
            pts.add(new double[]{x, z});
            last = boundary;
            endLine = line;
        }
    }

    private static List<BorderChain> borderChains(byte[] cells, int size, int step, double halfSpan,
                                                  boolean horizontal) {
        List<ChainBuilder> active = new java.util.ArrayList<>();
        List<BorderChain> done = new java.util.ArrayList<>();
        for (int line = 0; line < size; line++) {
            int[] current = boundariesOf(cells, size, line, horizontal);
            boolean[] usedPrev = new boolean[active.size()];
            boolean[] matchedCur = new boolean[current.length];
            for (int i = 0; i < current.length; i++) {
                int best = -1;
                int bestDelta = 2;
                for (int j = 0; j < active.size(); j++) {
                    if (usedPrev[j]) continue;
                    int delta = Math.abs(active.get(j).last - current[i]);
                    if (delta <= 1 && delta < bestDelta) {
                        bestDelta = delta;
                        best = j;
                    }
                }
                if (best >= 0) {
                    usedPrev[best] = true;
                    matchedCur[i] = true;
                    active.get(best).add(line, current[i], halfSpan, step);
                }
            }
            for (int j = active.size() - 1; j >= 0; j--) {
                if (!usedPrev[j]) {
                    ChainBuilder finished = active.remove(j);
                    BorderChain chain = finishChain(finished, step);
                    if (chain != null) done.add(chain);
                }
            }
            for (int i = 0; i < current.length; i++) {
                if (matchedCur[i]) continue;
                ChainBuilder fresh = new ChainBuilder(horizontal);
                fresh.startLine = line;
                fresh.add(line, current[i], halfSpan, step);
                active.add(fresh);
            }
        }
        for (ChainBuilder finished : active) {
            BorderChain chain = finishChain(finished, step);
            if (chain != null) done.add(chain);
        }
        return done;
    }

    private static BorderChain finishChain(ChainBuilder builder, int step) {
        int n = builder.pts.size();
        if (n < 2) return null;
        double mx = 0.0;
        double mz = 0.0;
        for (double[] p : builder.pts) {
            mx += p[0];
            mz += p[1];
        }
        mx /= n;
        mz /= n;
        double sxx = 0.0;
        double sxz = 0.0;
        double szz = 0.0;
        for (double[] p : builder.pts) {
            double dx = p[0] - mx;
            double dz = p[1] - mz;
            sxx += dx * dx;
            sxz += dx * dz;
            szz += dz * dz;
        }
        double theta = 0.5 * Math.atan2(2.0 * sxz, sxx - szz);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double minAlong = Double.MAX_VALUE;
        double maxAlong = -Double.MAX_VALUE;
        double maxDev = 0.0;
        double sumSq = 0.0;
        for (double[] p : builder.pts) {
            double dx = p[0] - mx;
            double dz = p[1] - mz;
            double along = dx * cos + dz * sin;
            double across = -dx * sin + dz * cos;
            minAlong = Math.min(minAlong, along);
            maxAlong = Math.max(maxAlong, along);
            maxDev = Math.max(maxDev, Math.abs(across));
            sumSq += across * across;
        }
        double[] first = builder.pts.get(0);
        double[] last = builder.pts.get(n - 1);
        return new BorderChain(n, maxAlong - minAlong, maxDev, builder.startLine, builder.endLine,
            builder.pts.size(), builder.pts.size(), Math.sqrt(sumSq / n),
            first[0], first[1], last[0], last[1], builder.horizontal);
    }

    private static void writeShoreMask(byte[] cells, int size, Path path) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int color = switch (cells[z * size + x]) {
                    case 2 -> 0xE8D9A0;
                    case 1 -> 0x5C8A4E;
                    default -> 0x2F5E9E;
                };
                image.setRGB(x, z, color);
            }
        }
        ImageIO.write(image, "PNG", path.toFile());
    }

    private static int[] boundariesOf(byte[] cells, int size, int line, boolean horizontal) {
        int[] result = new int[size];
        int count = 0;
        for (int i = 0; i + 1 < size; i++) {
            byte a = horizontal ? cells[line * size + i] : cells[i * size + line];
            byte b = horizontal ? cells[line * size + i + 1] : cells[(i + 1) * size + line];
            if ((a == 2) != (b == 2)) result[count++] = i;
        }
        int[] trimmed = new int[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }

    static IslandDraft baseDraft(IslandDraft.Preset preset, int size, long seed) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(0.0);
        draft.setRiverDensity(0.0);
        draft.setClimateScale(720);
        draft.setGradientMode(IslandClimate.GradientMode.NORTH_SOUTH);
        draft.setGradientStrength(0.5);
        draft.applyTheme(IslandDraft.BiomeTheme.STANDARD);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    private static IslandShapeConfig config(IslandDraft draft) {
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            TestWorldgen.OCEAN_TRANSITION, draft.packedMask());
        return new IslandShapeConfig(3, draft.isletDensity(), draft.riverDensity(),
            false, 1000, Optional.empty(), Optional.empty(), List.of(), Optional.of(mask),
            draft.biomeSeed(), Optional.of(draft.shoreSettings()));
    }

    static IslandTerrain terrain(IslandDraft draft) {
        return IslandTerrain.create(config(draft));
    }

    static List<CoastPoint> traceCoast(IslandTerrain terrain, ShoreSettings shore,
                                               long seed, double halfSpan, int points) {
        double cx = 0.0;
        double cz = 0.0;
        double best = -1.0e9;
        for (int gz = -12; gz <= 12; gz++) {
            for (int gx = -12; gx <= 12; gx++) {
                double x = gx * halfSpan * 0.9 / 12.0;
                double z = gz * halfSpan * 0.9 / 12.0;
                double d = terrain.signedDistance(x, z);
                if (d > best) {
                    best = d;
                    cx = x;
                    cz = z;
                }
            }
        }
        if (best <= 0.0) return List.of();

        List<CoastPoint> out = new ArrayList<>(points);
        double maxR = halfSpan * 2.0;
        double prevX = 0.0;
        double prevZ = 0.0;
        boolean havePrev = false;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double hit = -1.0;
            double lastIn = 0.0;
            for (double r = RAY_STEP; r <= maxR; r += RAY_STEP) {
                if (terrain.signedDistance(cx + cos * r, cz + sin * r) <= 0.0) {
                    hit = r;
                    break;
                }
                lastIn = r;
            }
            if (hit < 0.0) continue;
            double lo = lastIn;
            double hi = hit;
            for (int it = 0; it < 16; it++) {
                double mid = 0.5 * (lo + hi);
                if (terrain.signedDistance(cx + cos * mid, cz + sin * mid) > 0.0) lo = mid;
                else hi = mid;
            }
            double r = 0.5 * (lo + hi);
            double px = cx + cos * r;
            double pz = cz + sin * r;

            double eps = 2.0;
            double gx = (terrain.signedDistance(px + eps, pz) - terrain.signedDistance(px - eps, pz)) / (2 * eps);
            double gz = (terrain.signedDistance(px, pz + eps) - terrain.signedDistance(px, pz - eps)) / (2 * eps);
            double glen = Math.hypot(gx, gz);
            double nx;
            double nz;
            if (glen > 1.0e-6) {
                nx = gx / glen;
                nz = gz / glen;
            } else {
                nx = -cos;
                nz = -sin;
            }
            double qx = px + nx * INLAND_SAMPLE;
            double qz = pz + nz * INLAND_SAMPLE;
            double qd = terrain.signedDistance(qx, qz);
            double k = ringK(terrain, qx, qz, qd, ShoreField.BAY_RADIUS);
            double bay = smoothstep(-0.15, 0.25, k);
            double slope = slopeAt(terrain, qx, qz);
            double c = terrain.continentalness(qx, qz);
            double patch = ShoreField.patch(qx, qz, shore, seed);
            double width = ShoreField.inlandWidth(qx, qz, shore, seed, patch);
            BiomePreview.Type type = BiomePreview.surfaceV3(c, qx, qz, seed, 720,
                IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                slope, 3, halfSpan, terrain.rivers(), terrain, shore, true);
            double arc = havePrev ? Math.hypot(px - prevX, pz - prevZ) : 0.0;
            out.add(new CoastPoint(qx, qz, arc, qd, k, bay, width, patch, type));
            prevX = px;
            prevZ = pz;
            havePrev = true;
        }
        return out;
    }

    private static double ringK(IslandTerrain terrain, double x, double z, double distance, double radius) {
        double sum = 0.0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 0.25;
            sum += terrain.signedDistance(x + Math.cos(angle) * radius, z + Math.sin(angle) * radius);
        }
        return ((sum / 8.0) - distance) / radius;
    }

    static double slopeAt(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 4.0, z);
        double cW = terrain.continentalness(x - 4.0, z);
        double cS = terrain.continentalness(x, z + 4.0);
        double cN = terrain.continentalness(x, z - 4.0);
        return Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
    }

    private static double coefficientOfVariation(List<Double> values) {
        if (values.size() < 2) return 0.0;
        double mean = 0.0;
        for (double value : values) mean += value;
        mean /= values.size();
        if (mean <= 0.0) return 0.0;
        double variance = 0.0;
        for (double value : values) {
            double delta = value - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / values.size()) / mean;
    }

    private static String category(String biome) {
        if (biome.contains("ocean")) return "ocean";
        if (biome.contains("beach") || biome.contains("shore")) return "shore";
        return "land";
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
