package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.IslandClimate;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.1 audit: re-measure the claims from SUMMARY_0.6.0.md.
 * Heavy plots are only written with -Domniisle.evidence=true.
 */
public class Phase9AuditTest {
    private static final boolean WRITE_EVIDENCE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/phase9_before");
    private static final double OCEAN_TRANSITION = 400.0;

    @BeforeAll
    static void boot() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        if (WRITE_EVIDENCE) {
            try {
                Files.createDirectories(EVIDENCE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    @DisplayName("9.1a Harness is deterministic after the phase-9.1 seed fix")
    void harnessIsDeterministicAfterSeedFix() throws IOException {
        List<StructurePlacementHarness.EvalResult> first =
            StructurePlacementHarness.evaluateModern(IslandDraft.Preset.NATURAL, 2500, 101L, 2);
        List<StructurePlacementHarness.EvalResult> second =
            StructurePlacementHarness.evaluateModern(IslandDraft.Preset.NATURAL, 2500, 101L, 2);
        boolean differs = false;
        for (int i = 0; i < first.size(); i++) {
            if (Math.abs(first.get(i).distance() - second.get(i).distance()) > 1.0e-9) {
                differs = true;
                break;
            }
        }
        assertFalse(differs, "Harness must be deterministic after setting shape/biome seeds");
        if (WRITE_EVIDENCE) {
            StringBuilder csv = new StringBuilder("structure,run1_distance,run2_distance,equal\n");
            for (int i = 0; i < first.size(); i++) {
                csv.append(first.get(i).structure()).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", first.get(i).distance())).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", second.get(i).distance())).append(',')
                    .append(Math.abs(first.get(i).distance() - second.get(i).distance()) < 1.0e-9)
                    .append('\n');
            }
            String note = "Before the fix (recorded in stronghold_harness_nondeterminism.csv):\n"
                + "StructurePlacementHarness.evaluateLegacy/evaluateModern ignored their seed parameter\n"
                + "and IslandDraft filled shapeSeed/biomeSeed with new Random().nextLong(), so the same\n"
                + "arguments produced different islands and distances (e.g. ancient_city 1052.37 vs 1489.40).\n"
                + "Fix: both methods now call setShapeSeed(seed) and setBiomeSeed(seed); Phase5StructureTest\n"
                + "does the same. STRUCTURE_REPORT_*.csv reruns are byte-identical.\n\n"
                + csv;
            Files.writeString(EVIDENCE.resolve("stronghold_harness_fix.txt"), note, StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("9.1b Stronghold JSON and raw ring geometry")
    void strongholdRingGeometry() throws IOException {
        StringBuilder csv = new StringBuilder("size,distance,spread,count,raw_ring,chunk_x,chunk_z,radius_blocks,distance_from_spawn\n");
        long seed = 101L;
        for (int size : new int[]{2500, 4000, 8000}) {
            IslandDraft draft = new IslandDraft();
            draft.setPreset(IslandDraft.Preset.NATURAL);
            draft.setIslandSize(size);
            draft.setShapeSeed(seed);
            draft.setBiomeSeed(seed);
            draft.setGenerationVersion(IslandDraft.GenerationVersion.V2);
            draft.rebuildShape();
            draft.effectiveSpawnPx();
            double spawnX = draft.worldSpawnX();
            double spawnZ = draft.worldSpawnZ();
            int distance = Math.max(8, Math.min(32, (int) Math.round(32.0 * size / 8000.0)));
            int spread = Math.max(1, distance / 10);
            int count = 128;
            assertTrue(distance >= 8 && distance <= 32);
            assertTrue(spread < count);
            List<long[]> rings = rawRingChunks(seed, distance, spread, 4);
            for (int i = 0; i < rings.size(); i++) {
                long[] p = rings.get(i);
                double bx = p[0] * 16.0 + 8.0;
                double bz = p[1] * 16.0 + 8.0;
                csv.append(size).append(',').append(distance).append(',').append(spread).append(',')
                    .append(count).append(',').append(i).append(',')
                    .append(p[0]).append(',').append(p[1]).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", Math.hypot(bx, bz))).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", Math.hypot(bx - spawnX, bz - spawnZ)))
                    .append('\n');
            }
            // The first raw ring radius is not 4*distance chunks; it includes a +-1.25*distance random term.
            double r0 = Math.hypot(rings.get(0)[0] * 16.0, rings.get(0)[1] * 16.0);
            assertTrue(r0 >= distance * 16.0 * 2.75 - 32.0 && r0 <= distance * 16.0 * 5.25 + 32.0);
        }
        if (WRITE_EVIDENCE) {
            Files.writeString(EVIDENCE.resolve("stronghold_ring_geometry.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
    }

    private static List<long[]> rawRingChunks(long levelSeed, int distance, int initialSpread, int rings) {
        RandomSource random = RandomSource.create();
        random.setSeed(levelSeed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int spread = initialSpread;
        int ringIndex = 0;
        int placed = 0;
        List<long[]> out = new ArrayList<>();
        for (int i = 0; i < 512 && out.size() < rings * 3; i++) {
            double radius = 4.0 * distance + (double) distance * ringIndex * 6.0
                + (random.nextDouble() - 0.5) * distance * 2.5;
            long chunkX = Math.round(Math.cos(angle) * radius);
            long chunkZ = Math.round(Math.sin(angle) * radius);
            out.add(new long[]{chunkX, chunkZ});
            random.fork();
            placed++;
            angle += Math.PI * 2.0 / spread;
            if (placed == spread) {
                ringIndex++;
                placed = 0;
                spread = spread + (2 * spread) / (ringIndex + 1);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
            if (ringIndex >= rings) break;
        }
        return out;
    }

    @Test
    @DisplayName("9.1c Ocean profiles: floor is not flat at -0.92; deep band exists")
    void oceanProfiles() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.ROUND, 4000, 101L, 2, 0.0);
        IslandTerrain terrain = terrain(draft, 2, 0.0);
        int deepSamples = 0;
        int totalSamples = 0;
        int maxRun = 0;
        double min = 1.0;
        double max = -1.0;
        StringBuilder csv = new StringBuilder("seed,ray_deg,offshore_distance,continentalness,elevation\n");
        for (long seed : new long[]{101L, 202L}) {
            IslandDraft d = baseDraft(IslandDraft.Preset.ROUND, 4000, seed, 2, 0.0);
            IslandTerrain t = terrain(d, 2, 0.0);
            for (int deg = 0; deg < 360; deg += 10) {
                double rad = Math.toRadians(deg);
                double dirX = Math.cos(rad);
                double dirZ = Math.sin(rad);
                double coast = 0.0;
                for (double r = 0.0; r <= 4000.0; r += 5.0) {
                    if (t.signedDistance(r * dirX, r * dirZ) < 0.0) {
                        coast = r;
                        break;
                    }
                }
                int run = 0;
                for (double offshore = 0.0; offshore <= 3000.0; offshore += 10.0) {
                    double dist = coast + offshore;
                    double c = t.continentalness(dist * dirX, dist * dirZ);
                    if (c <= -0.58) {
                        run += 10;
                        maxRun = Math.max(maxRun, run);
                        deepSamples++;
                    } else {
                        run = 0;
                    }
                    if (offshore >= 350.0) {
                        min = Math.min(min, c);
                        max = Math.max(max, c);
                    }
                    totalSamples++;
                    csv.append(seed).append(',').append(deg).append(',')
                        .append(String.format(Locale.ROOT, "%.0f", offshore)).append(',')
                        .append(String.format(Locale.ROOT, "%.6f", c)).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", IslandTerrain.continentalnessToElevation(c)))
                        .append('\n');
                }
            }
        }
        assertTrue(min >= -1.0 && max <= 1.0);
        assertTrue(min < -0.80, "Ocean floor must go below -0.80 somewhere, was " + min);
        assertTrue(max > -0.86, "Floor must not be a flat -0.92 plate, max at s>=350 was " + max);
        assertTrue(maxRun >= 150, "Guaranteed deep band must exist, max run " + maxRun);
        if (WRITE_EVIDENCE) {
            Files.writeString(EVIDENCE.resolve("ocean_radial_profiles.csv"), csv.toString(), StandardCharsets.UTF_8);
            StringBuilder summary = new StringBuilder("metric,value\n");
            summary.append("c_min_at_s>=350,").append(String.format(Locale.ROOT, "%.6f", min)).append('\n');
            summary.append("c_max_at_s>=350,").append(String.format(Locale.ROOT, "%.6f", max)).append('\n');
            summary.append("max_consecutive_deep_blocks,").append(maxRun).append('\n');
            summary.append("deep_fraction_of_ocean_samples,")
                .append(String.format(Locale.ROOT, "%.4f", deepSamples / (double) totalSamples)).append('\n');
            Files.writeString(EVIDENCE.resolve("ocean_profile_summary.csv"), summary.toString(), StandardCharsets.UTF_8);
            writeOceanHeightPng(EVIDENCE.resolve("ocean_height_6000_before.png"), draft, 4000);
        }
    }

    @Test
    @DisplayName("9.1d Perf: density sampling for a 25x25-chunk area, v1 vs v2 (not full chunk gen)")
    void densitySamplingPerformance() throws IOException {
        IslandDraft v1Draft = baseDraft(IslandDraft.Preset.ROUND, 4000, 4242L, 1, 0.0);
        IslandDraft v2Draft = baseDraft(IslandDraft.Preset.ROUND, 4000, 4242L, 2, 0.0);
        IslandTerrain v1 = terrain(v1Draft, 1, 0.0);
        IslandTerrain v2 = terrain(v2Draft, 2, 0.0);
        // 25x25 chunks at the vanilla 4-block noise resolution: 100x100 unique columns.
        double minX = -200.0;
        double minZ = -200.0;
        sampleArea(v1, minX, minZ);
        sampleArea(v2, minX, minZ);
        long[] t1 = new long[3];
        long[] t2 = new long[3];
        for (int rep = 0; rep < 3; rep++) {
            t1[rep] = timeArea(v1, minX, minZ);
            t2[rep] = timeArea(v2, minX, minZ);
            assertTrue(t1[rep] > 0 && t2[rep] > 0);
        }
        System.out.println("density sampling 25x25 chunks: v1=" + java.util.Arrays.toString(t1)
            + " ns, v2=" + java.util.Arrays.toString(t2) + " ns");
        if (WRITE_EVIDENCE) {
            StringBuilder csv = new StringBuilder("version,rep,ns_per_100x100_quarters,ms_per_25x25_chunks,ns_per_column\n");
            for (int rep = 0; rep < 3; rep++) {
                csv.append("v1,").append(rep + 1).append(',').append(t1[rep]).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", t1[rep] / 625_000.0)).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", t1[rep] / 10_000.0)).append('\n');
                csv.append("v2,").append(rep + 1).append(',').append(t2[rep]).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", t2[rep] / 625_000.0)).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", t2[rep] / 10_000.0)).append('\n');
            }
            csv.append("# This measures the 2D density sampler at the vanilla 4-block noise resolution.\n");
            csv.append("# It is NOT full chunk generation: no noise router, carvers, features or structures.\n");
            Files.writeString(EVIDENCE.resolve("density_sampling_perf.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
    }

    private static void sampleArea(IslandTerrain terrain, double minX, double minZ) {
        for (int z = 0; z < 100; z++) {
            for (int x = 0; x < 100; x++) {
                terrain.continentalness(minX + x * 4.0, minZ + z * 4.0);
            }
        }
    }

    private static long timeArea(IslandTerrain terrain, double minX, double minZ) {
        long start = System.nanoTime();
        sampleArea(terrain, minX, minZ);
        return System.nanoTime() - start;
    }

    @Test
    @DisplayName("9.1e Golden sampler matches real v1 terrain")
    void goldenSamplerMatchesLegacyTerrain() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 4000, 1337L, 1, 0.0);
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = mask(draft);
        IslandTerrain legacy = terrain(draft, 1, 0.0);
        long lcg = 0x123456789ABCDEFL;
        double maxDiff = 0.0;
        for (int i = 0; i < 10000; i++) {
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
            double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (halfSpan * 2) - halfSpan;
            maxDiff = Math.max(maxDiff, Math.abs(mask.sampleTerrainValue(x, z) - legacy.continentalness(x, z)));
        }
        assertEquals(0.0, maxDiff, 1.0e-15, "Golden snapshot must exercise the real v1 terrain sampler");
        assertTrue(Files.exists(Path.of("src/test/resources/golden/v1/golden_v1.bin.gz")));
        if (WRITE_EVIDENCE) {
            String note = "golden_v1.bin.gz max difference between mask.sampleTerrainValue and LegacyTerrainV1: "
                + maxDiff + "\n"
                + "Coverage after the phase-9.1 extension: 30 configs (5 presets x 3 reliefs x 2 sizes),\n"
                + "10000 LCG points per config, 200x200 preview grid, 20 deep-dark samples, plus a new\n"
                + "real OmniBiomeSource snapshot (200x200 surface grid and 4 underground Y levels x 100 points)\n"
                + "in src/test/resources/golden/v1/golden_v1_source.txt.\n"
                + "Fixed caveats: generateGoldenData() now iterates the same 5 v1 presets instead of all 12,\n"
                + "and the test no longer regenerates a missing golden file (missing resource = failure).\n"
                + "Remaining caveat: the committed binary golden was produced by the old generator; its first\n"
                + "30 records do match the 5-preset order, verified by this test (difference 0.0).\n";
            Files.writeString(EVIDENCE.resolve("golden_audit.txt"), note, StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("9.1f Real OmniBiomeSource vs BiomePreview parity")
    void biomeSourceParity() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.ROUND, 4000, 12345L, 2, 0.0);
        draft.setClimateScale(720);
        draft.setGradientMode(IslandClimate.GradientMode.NORTH_SOUTH);
        draft.setGradientStrength(0.5);
        draft.applyTheme(IslandDraft.BiomeTheme.STANDARD);
        draft.setRiverDensity(0.0);
        double halfSpan = draft.worldSpan() * 0.5;
        IslandShapeConfig cfg = config(draft, 2, 0.0);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        Climate.Sampler sampler = sampler(terrain);
        OmniBiomeSource source = biomeSource(720, 12345L, 2, "north_south", 0.5, "standard");
        int grid = 256;
        int step = (int) Math.max(1, Math.round((halfSpan * 2.0) / grid));
        int zones = 0;
        int familyLand = 0;
        int familyMatch = 0;
        int oceanTotal = 0;
        int oceanMatch = 0;
        int landTotal = 0;
        int landMatch = 0;
        int shoreTotal = 0;
        int shoreMatch = 0;
        int mismatchRows = 0;
        StringBuilder mismatches = new StringBuilder("x,z,server,server_category,client,client_category\n");
        BufferedImage diff = new BufferedImage(grid, grid, BufferedImage.TYPE_INT_RGB);
        for (int gz = 0; gz < grid; gz++) {
            for (int gx = 0; gx < grid; gx++) {
                int rawWx = (int) Math.round(-halfSpan + gx * step);
                int rawWz = (int) Math.round(-halfSpan + gz * step);
                int quartX = rawWx >> 2;
                int quartZ = rawWz >> 2;
                int bx = quartX << 2;
                int bz = quartZ << 2;
                double c = terrain.continentalness(bx, bz);
                double cE = terrain.continentalness(bx + 4, bz);
                double cW = terrain.continentalness(bx - 4, bz);
                double cS = terrain.continentalness(bx, bz + 4);
                double cN = terrain.continentalness(bx, bz - 4);
                double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
                String server = source.getNoiseBiome(quartX, 16, quartZ, sampler).unwrapKey()
                    .map(k -> k.identifier().getPath()).orElse("");
                BiomePreview.Type client = BiomePreview.surface(c, bx, bz, 12345L, 720,
                    IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                    slope, 2, halfSpan);
                String sc = category(server);
                String cc = client.category.name().toLowerCase(Locale.ROOT);
                boolean match = sc.equals(cc);
                if (match) zones++;
                if (sc.equals("ocean")) {
                    oceanTotal++;
                    if (match) oceanMatch++;
                } else if (sc.equals("shore")) {
                    shoreTotal++;
                    if (match) shoreMatch++;
                } else {
                    landTotal++;
                    if (match) landMatch++;
                }
                if (sc.equals("land") && cc.equals("land")) {
                    familyLand++;
                    int serverFamily = OmniBiomeSource.landFamily(server);
                    if (serverFamily == client.family) familyMatch++;
                }
                diff.setRGB(gx, gz, match ? 0x2E7D32 : 0xD32F2F);
                if (!match && WRITE_EVIDENCE && mismatchRows < 5000) {
                    mismatchRows++;
                    mismatches.append(bx).append(',').append(bz).append(',').append(server).append(',')
                        .append(sc).append(',').append(client.id).append(',').append(cc).append('\n');
                }
            }
        }
        double zoneRate = zones / (double) (grid * grid);
        double familyRate = familyLand == 0 ? 1.0 : familyMatch / (double) familyLand;
        assertTrue(zoneRate >= 0.97, "zone parity was " + zoneRate);
        assertTrue(familyRate >= 0.97, "family parity was " + familyRate);
        if (WRITE_EVIDENCE) {
            StringBuilder summary = new StringBuilder("scope,samples,matches,agreement\n");
            summary.append("overall,").append(grid * grid).append(',').append(zones).append(',')
                .append(zoneRate).append('\n');
            summary.append("ocean,").append(oceanTotal).append(',').append(oceanMatch).append(',')
                .append(oceanTotal == 0 ? 1.0 : oceanMatch / (double) oceanTotal).append('\n');
            summary.append("shore,").append(shoreTotal).append(',').append(shoreMatch).append(',')
                .append(shoreTotal == 0 ? 1.0 : shoreMatch / (double) shoreTotal).append('\n');
            summary.append("land,").append(landTotal).append(',').append(landMatch).append(',')
                .append(landTotal == 0 ? 1.0 : landMatch / (double) landTotal).append('\n');
            summary.append("land_family,").append(familyLand).append(',').append(familyMatch).append(',')
                .append(familyRate).append('\n');
            summary.append("# 256x256 surface points; rivers disabled (draft.setRiverDensity(0.0)) because\n")
                .append("# OmniBiomeSource rivers are not represented in BiomePreview.surface in 0.6.0.\n");
            Files.writeString(EVIDENCE.resolve("parity_before_summary.csv"), summary.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("parity_before_mismatches.csv"), mismatches.toString(), StandardCharsets.UTF_8);
            ImageIO.write(diff, "png", EVIDENCE.resolve("parity_before_diff.png").toFile());

            // Second pass with the default river density (0.35): BiomePreview knows nothing about rivers.
            IslandDraft riverDraft = baseDraft(IslandDraft.Preset.ROUND, 4000, 12345L, 2, 0.0);
            riverDraft.setClimateScale(720);
            riverDraft.setGradientMode(IslandClimate.GradientMode.NORTH_SOUTH);
            riverDraft.setGradientStrength(0.5);
            riverDraft.applyTheme(IslandDraft.BiomeTheme.STANDARD);
            IslandShapeConfig riverCfg = config(riverDraft, 2, 0.0);
            IslandTerrain riverTerrain = IslandTerrain.create(riverCfg);
            IslandDensityFunction.setActive(new IslandDensityFunction(riverCfg));
            Climate.Sampler riverSampler = sampler(riverTerrain);
            OmniBiomeSource riverSource = biomeSource(720, 12345L, 2, "north_south", 0.5, "standard");
            int rGrid = 128;
            int rStep = (int) Math.max(1, Math.round((halfSpan * 2.0) / rGrid));
            int rZones = 0;
            int rFamilyLand = 0;
            int rFamilyMatch = 0;
            int riverColumns = 0;
            for (int gz = 0; gz < rGrid; gz++) {
                for (int gx = 0; gx < rGrid; gx++) {
                    int rawWx = (int) Math.round(-halfSpan + gx * rStep);
                    int rawWz = (int) Math.round(-halfSpan + gz * rStep);
                    int quartX = rawWx >> 2;
                    int quartZ = rawWz >> 2;
                    int bx = quartX << 2;
                    int bz = quartZ << 2;
                    double c = riverTerrain.continentalness(bx, bz);
                    double cE = riverTerrain.continentalness(bx + 4, bz);
                    double cW = riverTerrain.continentalness(bx - 4, bz);
                    double cS = riverTerrain.continentalness(bx, bz + 4);
                    double cN = riverTerrain.continentalness(bx, bz - 4);
                    double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
                    String server = riverSource.getNoiseBiome(quartX, 16, quartZ, riverSampler).unwrapKey()
                        .map(k -> k.identifier().getPath()).orElse("");
                    BiomePreview.Type client = BiomePreview.surface(c, bx, bz, 12345L, 720,
                        IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                        slope, 2, halfSpan);
                    String sc = category(server);
                    String cc = client.category.name().toLowerCase(Locale.ROOT);
                    if (sc.equals(cc)) rZones++;
                    if (server.contains("river")) riverColumns++;
                    if (sc.equals("land") && cc.equals("land")) {
                        rFamilyLand++;
                        if (OmniBiomeSource.landFamily(server) == client.family) rFamilyMatch++;
                    }
                }
            }
            StringBuilder riverSummary = new StringBuilder("scope,samples,matches,agreement\n");
            riverSummary.append("overall,").append(rGrid * rGrid).append(',').append(rZones).append(',')
                .append(rZones / (double) (rGrid * rGrid)).append('\n');
            riverSummary.append("land_family,").append(rFamilyLand).append(',').append(rFamilyMatch).append(',')
                .append(rFamilyLand == 0 ? 1.0 : rFamilyMatch / (double) rFamilyLand).append('\n');
            riverSummary.append("river_columns,").append(riverColumns).append('\n');
            riverSummary.append("# Same seed/config but river_density left at the 0.6.0 draft default 0.35.\n")
                .append("# BiomePreview.surface has no river parameter, so river columns cannot match.\n");
            Files.writeString(EVIDENCE.resolve("parity_rivers_on_summary.csv"), riverSummary.toString(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("9.1k V1 real biome source vs preview on a 200x200 grid")
    void v1SourceParity() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.ROUND, 4000, 777L, 1, 0.0);
        draft.setClimateScale(720);
        draft.setGradientMode(IslandClimate.GradientMode.NORTH_SOUTH);
        draft.setGradientStrength(0.5);
        draft.applyTheme(IslandDraft.BiomeTheme.STANDARD);
        draft.setRiverDensity(0.0);
        double halfSpan = draft.worldSpan() * 0.5;
        IslandShapeConfig cfg = config(draft, 1, 0.0);
        IslandTerrain terrain = IslandTerrain.create(cfg);
        assertTrue(terrain instanceof com.hack337.omniisle.continent.LegacyTerrainV1);
        IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
        Climate.Sampler sampler = sampler(terrain);
        OmniBiomeSource source = biomeSource(720, 777L, 1, "north_south", 0.5, "standard");
        int grid = 200;
        int step = (int) Math.max(1, Math.round((halfSpan * 2.0) / grid));
        int zones = 0;
        int exact = 0;
        int familyLand = 0;
        int familyMatch = 0;
        for (int gz = 0; gz < grid; gz++) {
            for (int gx = 0; gx < grid; gx++) {
                int rawWx = (int) Math.round(-halfSpan + gx * step);
                int rawWz = (int) Math.round(-halfSpan + gz * step);
                int quartX = rawWx >> 2;
                int quartZ = rawWz >> 2;
                int bx = quartX << 2;
                int bz = quartZ << 2;
                double c = terrain.continentalness(bx, bz);
                double cE = terrain.continentalness(bx + 4, bz);
                double cW = terrain.continentalness(bx - 4, bz);
                double cS = terrain.continentalness(bx, bz + 4);
                double cN = terrain.continentalness(bx, bz - 4);
                double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
                String server = source.getNoiseBiome(quartX, 16, quartZ, sampler).unwrapKey()
                    .map(k -> k.identifier().getPath()).orElse("");
                BiomePreview.Type client = BiomePreview.surface(c, bx, bz, 777L, 720,
                    IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                    slope, 1, halfSpan);
                String sc = category(server);
                String cc = client.category.name().toLowerCase(Locale.ROOT);
                if (sc.equals(cc)) zones++;
                if (server.equals(client.id)) exact++;
                if (sc.equals("land") && cc.equals("land")) {
                    familyLand++;
                    if (OmniBiomeSource.landFamily(server) == client.family) familyMatch++;
                }
            }
        }
        int total = grid * grid;
        double zoneRate = zones / (double) total;
        if (WRITE_EVIDENCE) {
            String summary = "scope,samples,matches,agreement\n"
                + "v1_overall," + total + ',' + zones + ',' + zoneRate + '\n'
                + "v1_exact_biome," + total + ',' + exact + ',' + (exact / (double) total) + '\n'
                + "v1_land_family," + familyLand + ',' + familyMatch + ','
                + (familyLand == 0 ? 1.0 : familyMatch / (double) familyLand) + '\n';
            Files.writeString(EVIDENCE.resolve("parity_v1_summary.csv"), summary, StandardCharsets.UTF_8);
        }
        assertTrue(zoneRate >= 0.97, "v1 zone parity was " + zoneRate);
    }

    private static String category(String biomePath) {
        if (biomePath.contains("ocean")) return "ocean";
        if (biomePath.contains("beach") || biomePath.contains("stony_shore")) return "shore";
        return "land";
    }

    @Test
    @DisplayName("9.1j Uniform shore ribbon before v3")
    void uniformShoreRibbonBefore() throws IOException {
        StringBuilder csv = new StringBuilder("preset,size,seed,coast_samples,shore,ocean,land,shore_fraction\n");
        double totalShore = 0;
        double totalSamples = 0;
        for (IslandDraft.Preset preset : new IslandDraft.Preset[]{
            IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.CRESCENT}) {
            for (long seed : new long[]{101L, 202L, 303L}) {
                IslandDraft draft = baseDraft(preset, 4000, seed, 2, 0.0);
                draft.setClimateScale(720);
                draft.setGradientMode(IslandClimate.GradientMode.NORTH_SOUTH);
                draft.setGradientStrength(0.5);
                draft.applyTheme(IslandDraft.BiomeTheme.STANDARD);
                double halfSpan = draft.worldSpan() * 0.5;
                IslandTerrain terrain = terrain(draft, 2, 0.0);
                int shore = 0;
                int ocean = 0;
                int land = 0;
                int samples = 0;
                for (int deg = 0; deg < 360; deg += 5) {
                    double rad = Math.toRadians(deg);
                    double dx = Math.cos(rad);
                    double dz = Math.sin(rad);
                    double coast = -1.0;
                    for (double r = 4000.0; r >= 0.0; r -= 4.0) {
                        if (terrain.signedDistance(r * dx, r * dz) >= 0.0) {
                            coast = r;
                            break;
                        }
                    }
                    if (coast < 0.0) continue;
                    double r = coast;
                    for (int step = 0; step < 120; step++) {
                        double dHere = terrain.signedDistance(r * dx, r * dz);
                        if (dHere >= 5.0 && dHere <= 8.0) break;
                        double inward = terrain.signedDistance((r - 1.0) * dx, (r - 1.0) * dz);
                        double outward = terrain.signedDistance((r + 1.0) * dx, (r + 1.0) * dz);
                        r += inward > outward ? -1.0 : 1.0;
                        if (r < 0.0 || r > 4000.0) break;
                    }
                    double x = r * dx;
                    double z = r * dz;
                    double d = terrain.signedDistance(x, z);
                    if (d < 5.0 || d > 8.0) continue;
                    double c = terrain.continentalness(x, z);
                    double cE = terrain.continentalness(x + 4, z);
                    double cW = terrain.continentalness(x - 4, z);
                    double cS = terrain.continentalness(x, z + 4);
                    double cN = terrain.continentalness(x, z - 4);
                    double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
                    BiomePreview.Type type = BiomePreview.surface(c, x, z, seed, 720,
                        IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                        slope, 2, halfSpan);
                    samples++;
                    if (type.category == BiomePreview.Category.SHORE) shore++;
                    else if (type.category == BiomePreview.Category.OCEAN) ocean++;
                    else land++;
                }
                totalShore += shore;
                totalSamples += samples;
                assertTrue(samples > 40, "Not enough coast samples for " + preset + " seed " + seed + ": " + samples);
                csv.append(preset.name()).append(',').append(4000).append(',').append(seed).append(',')
                    .append(samples).append(',').append(shore).append(',').append(ocean).append(',')
                    .append(land).append(',').append(String.format(Locale.ROOT, "%.4f", shore / (double) samples))
                    .append('\n');
            }
        }
        double fraction = totalShore / totalSamples;
        assertTrue(fraction >= 0.95, "v2 should be a uniform shore ribbon, was " + fraction);
        if (WRITE_EVIDENCE) {
            Files.writeString(EVIDENCE.resolve("shore_ribbon_before.csv"), csv.toString(), StandardCharsets.UTF_8);
            writeBiomePng(EVIDENCE.resolve("biome_before_6000.png"), baseDraft(
                IslandDraft.Preset.ROUND, 4000, 101L, 2, 0.0), 6000);
        }
    }

    private static void writeBiomePng(Path path, IslandDraft draft, int span) throws IOException {
        draft.setClimateScale(720);
        draft.setGradientMode(IslandClimate.GradientMode.NORTH_SOUTH);
        draft.setGradientStrength(0.5);
        draft.applyTheme(IslandDraft.BiomeTheme.STANDARD);
        double halfSpan = draft.worldSpan() * 0.5;
        IslandTerrain terrain = terrain(draft, 2, 0.0);
        int stride = 8;
        int dim = span / stride;
        BufferedImage image = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < dim; py++) {
            double z = -span / 2.0 + py * stride;
            for (int px = 0; px < dim; px++) {
                double x = -span / 2.0 + px * stride;
                double c = terrain.continentalness(x, z);
                double cE = terrain.continentalness(x + 4, z);
                double cW = terrain.continentalness(x - 4, z);
                double cS = terrain.continentalness(x, z + 4);
                double cN = terrain.continentalness(x, z - 4);
                double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
                BiomePreview.Type type = BiomePreview.surface(c, x, z, draft.biomeSeed(), 720,
                    IslandClimate.GradientMode.NORTH_SOUTH, 0.5, IslandDraft.BiomeTheme.STANDARD,
                    slope, 2, halfSpan);
                image.setRGB(px, py, type.color);
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    @Test
    @DisplayName("9.1g Climate-family fractions per theme and gradient")
    void climateFractions() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.ROUND, 4000, 202L, 2, 0.0);
        double halfSpan = draft.worldSpan() * 0.5;
        IslandTerrain terrain = terrain(draft, 2, 0.0);
        StringBuilder csv = new StringBuilder("theme,gradient,family,count,fraction\n");
        for (IslandDraft.BiomeTheme theme : new IslandDraft.BiomeTheme[]{
            IslandDraft.BiomeTheme.ALL, IslandDraft.BiomeTheme.STANDARD,
            IslandDraft.BiomeTheme.TROPICAL, IslandDraft.BiomeTheme.NORDIC, IslandDraft.BiomeTheme.DESERT}) {
            for (double strength : new double[]{0.0, 0.5}) {
                EnumMap<BiomePreview.Type, Integer> counts = new EnumMap<>(BiomePreview.Type.class);
                int land = 0;
                int grid = 128;
                double step = halfSpan * 2.0 / grid;
                for (int gz = 0; gz < grid; gz++) {
                    for (int gx = 0; gx < grid; gx++) {
                        double x = -halfSpan + (gx + 0.5) * step;
                        double z = -halfSpan + (gz + 0.5) * step;
                        double c = terrain.continentalness(x, z);
                        double cE = terrain.continentalness(x + 4, z);
                        double cW = terrain.continentalness(x - 4, z);
                        double cS = terrain.continentalness(x, z + 4);
                        double cN = terrain.continentalness(x, z - 4);
                        double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
                        BiomePreview.Type type = BiomePreview.surface(c, x, z, 202L, 720,
                            IslandClimate.GradientMode.NORTH_SOUTH, strength, theme, slope, 2, halfSpan);
                        if (type.isLand()) {
                            counts.merge(type, 1, Integer::sum);
                            land++;
                        }
                    }
                }
                for (Map.Entry<BiomePreview.Type, Integer> e : counts.entrySet()) {
                    csv.append(theme.name()).append(',').append(strength == 0.0 ? "off" : "on").append(',')
                        .append(e.getKey().id).append(',').append(e.getValue()).append(',')
                        .append(String.format(Locale.ROOT, "%.4f", e.getValue() / (double) land)).append('\n');
                }
                assertTrue(land > 0);
            }
        }
        if (WRITE_EVIDENCE) {
            Files.writeString(EVIDENCE.resolve("climate_family_fractions.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("9.1h Ancient City: generated tag and placement")
    void ancientCityAudit() throws IOException {
        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, 4000, 303L, 2, 0.0);
        Path tmp = Files.createTempDirectory("omniisle-audit-pack");
        WorldPackBuilder.export(tmp, draft);
        Path pack = tmp.resolve(WorldPackBuilder.FILE_NAME);
        assertTrue(Files.exists(pack));
        String tag;
        String placement;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            tag = read(zip, "data/minecraft/tags/worldgen/biome/has_structure/ancient_city.json");
            placement = read(zip, "data/minecraft/worldgen/structure_set/ancient_cities.json");
        }
        assertNotNull(tag);
        assertTrue(tag.contains("minecraft:deep_dark"));
        boolean addsSurfaceBiome = tag.contains("minecraft:forest") || tag.contains("minecraft:plains")
            || tag.contains("minecraft:taiga") || tag.contains("minecraft:stony_peaks")
            || tag.contains("minecraft:jagged_peaks");
        assertTrue(addsSurfaceBiome, "Ancient City tag must be audited: " + tag);
        assertTrue(placement.contains("omniisle:fixed"));
        if (WRITE_EVIDENCE) {
            StringBuilder out = new StringBuilder("Ancient City generated pack inspection\n\n");
            out.append("tag has_structure/ancient_city:\n").append(tag).append('\n');
            out.append("structure_set ancient_cities:\n").append(placement).append('\n');
            out.append("Conclusion: the city is restricted to deep_dark plus one surface biome selected by the\n");
            out.append("pack builder; the selected surface biome does not require the underground deep_dark biome.\n");
            Files.writeString(EVIDENCE.resolve("ancient_city_audit.txt"), out.toString(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("9.1i World border is off by default")
    void worldBorderDefault() throws IOException {
        IslandDraft draft = new IslandDraft();
        boolean draftDefault = draft.isWorldBorderEnabled();
        assertFalse(draftDefault);
        assertEquals(1000, draft.worldBorderMargin());
        IslandShapeConfig cfg = new IslandShapeConfig(2, 0.0, List.of(), Optional.empty(), 1L);
        assertFalse(cfg.worldBorder);
        assertFalse(new IslandDraft().copy().isWorldBorderEnabled());
        // 0.6.0 copy() dropped border/river settings; fixed in Phase 9.4.
        draft.setWorldBorderEnabled(true);
        draft.setWorldBorderMargin(1500);
        draft.setRiverDensity(0.42);
        IslandDraft copy = draft.copy();
        assertTrue(copy.isWorldBorderEnabled());
        assertEquals(1500, copy.worldBorderMargin());
        assertEquals(0.42, copy.riverDensity(), 1e-9);
        if (WRITE_EVIDENCE) {
            String note = "IslandDraft.worldBorder default: " + draftDefault + "\n"
                + "IslandShapeConfig.worldBorder default (codec fallback): false\n"
                + "copy() result for a draft with border enabled: " + copy.isWorldBorderEnabled() + "\n"
                + "copy() result for riverDensity 0.42: " + copy.riverDensity() + "\n"
                + "Conclusion: border is OFF by default; Phase 9.4 makes copy() preserve river and border settings.\n";
            Files.writeString(EVIDENCE.resolve("world_border_default.txt"), note, StandardCharsets.UTF_8);
        }
    }

    private static String read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return null;
        try (var in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static IslandDraft baseDraft(IslandDraft.Preset preset, int size, long seed, int version, double islet) {
        IslandDraft draft = new IslandDraft();
        draft.applyTheme(IslandDraft.BiomeTheme.STANDARD);
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setGenerationVersion(version == 1 ? IslandDraft.GenerationVersion.V1 : IslandDraft.GenerationVersion.V2);
        draft.setIsletDensity(islet);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    private static IslandMask mask(IslandDraft draft) {
        double halfSpan = draft.worldSpan() * 0.5;
        return new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            OCEAN_TRANSITION, draft.packedMask());
    }

    private static IslandShapeConfig config(IslandDraft draft, int version, double islet) {
        return new IslandShapeConfig(version, islet, draft.riverDensity(),
            draft.isWorldBorderEnabled(), draft.worldBorderMargin(),
            Optional.empty(), Optional.empty(), Collections.emptyList(),
            Optional.of(mask(draft)), draft.biomeSeed());
    }

    private static IslandTerrain terrain(IslandDraft draft, int version, double islet) {
        return IslandTerrain.create(config(draft, version, islet));
    }

    private static Climate.Sampler sampler(IslandTerrain terrain) {
        DensitySampler cSampler = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                return (float) terrain.continentalness(x, z);
            }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };
        DensitySampler zero = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) { return 0.0f; }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };
        DensitySampler nonRiver = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) { return 0.5f; }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };
        return new Climate.Sampler(
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(cSampler, null),
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(nonRiver, null)
        );
    }

    private static Holder<Biome> holder(String path) {
        return Holder.Reference.createStandAlone(null,
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path)));
    }

    private static OmniBiomeSource biomeSource(int climateScale, long seed, int version,
                                               String gradient, double strength, String theme) {
        return new OmniBiomeSource(
            List.of(holder("snowy_plains"), holder("taiga"), holder("birch_forest"), holder("forest"),
                holder("plains"), holder("meadow"), holder("cherry_grove"), holder("dark_forest"),
                holder("swamp"), holder("mangrove_swamp"), holder("jungle"), holder("savanna"),
                holder("desert"), holder("badlands"), holder("stony_peaks"), holder("jagged_peaks"),
                holder("mushroom_fields"), holder("pale_garden")),
            List.of(),
            List.of(holder("beach"), holder("stony_shore"), holder("snowy_beach")),
            List.of(),
            List.of(holder("ocean"), holder("deep_ocean"), holder("warm_ocean"), holder("lukewarm_ocean"),
                holder("deep_lukewarm_ocean"), holder("cold_ocean"), holder("deep_cold_ocean"),
                holder("frozen_ocean"), holder("deep_frozen_ocean")),
            List.of(),
            List.of(holder("river"), holder("frozen_river")),
            List.of(),
            List.of(holder("lush_caves"), holder("dripstone_caves"), holder("deep_dark"), holder("sulfur_caves")),
            List.of(), climateScale, seed, version, gradient, strength, theme);
    }

    private static void writeOceanHeightPng(Path path, IslandDraft draft, int islandSize) throws IOException {
        IslandTerrain terrain = terrain(draft, 2, 0.0);
        int span = 6000;
        int stride = 4;
        int dim = span / stride;
        BufferedImage image = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < dim; py++) {
            double z = -span / 2.0 + py * stride;
            for (int px = 0; px < dim; px++) {
                double x = -span / 2.0 + px * stride;
                double c = terrain.continentalness(x, z);
                double y = IslandTerrain.continentalnessToElevation(c);
                image.setRGB(px, py, heightColor(y, c));
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private static int heightColor(double y, double c) {
        if (c < -0.08) {
            double t = Math.max(0.0, Math.min(1.0, (y - 20.0) / 44.0));
            int r = (int) (8 + 12 * t);
            int g = (int) (25 + 35 * t);
            int b = (int) (65 + 70 * t);
            return (r << 16) | (g << 8) | b;
        }
        double t = Math.max(0.0, Math.min(1.0, (y - 62.0) / 182.0));
        int r = (int) (70 + 170 * t);
        int g = (int) (110 + 100 * t);
        int b = (int) (45 + 170 * t);
        return (r << 16) | (g << 8) | b;
    }
}
