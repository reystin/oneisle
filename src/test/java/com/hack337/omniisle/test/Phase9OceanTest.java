package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandClimate;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ModernTerrainV2;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class Phase9OceanTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/ocean");
    private static final double WO = 400.0;
    private static final double DEEP = IslandClimate.DEEP_OCEAN_THRESHOLD;
    private static final double BAND_START = 0.4 * WO;
    private static final double BAND_END = WO;
    private static final IslandDraft.Preset[] PRESETS = {
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.ROUND, IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT, IslandDraft.Preset.ARCHIPELAGO
    };
    private static final long[] SEEDS = {101L, 202L, 303L};

    @Test
    void v3DeepBandGuaranteedAcrossPresets() throws IOException {
        int configs = 0;
        int bandSamples = 0;
        int violations = 0;
        StringBuilder summary = new StringBuilder(
            "config,band_samples,violations,max_c_in_band,min_c_in_band\n");
        StringBuilder mismatches = new StringBuilder("config,ray_deg,offset,offshore_distance,continentalness\n");

        for (IslandDraft.Preset preset : PRESETS) {
            for (long seed : SEEDS) {
                IslandDraft draft = draft(preset, 4000, seed, 0.25);
                IslandTerrain terrain = terrain(draft, 0.25);
                ModernTerrainV2 surface = (ModernTerrainV2) terrain;
                String key = preset.name() + "_4000_s" + seed;
                int configBand = 0;
                int configViolations = 0;
                int validRays = 0;
                double maxBand = -2.0;
                double minBand = 2.0;

                for (int deg = 0; deg < 360; deg += 10) {
                    double rad = Math.toRadians(deg);
                    double dirX = Math.cos(rad);
                    double dirZ = Math.sin(rad);

                    double coast = coastRadius(surface, dirX, dirZ);
                    if (coast < 0.0) {
                        continue;
                    }
                    validRays++;

                    double cAtCoast = terrain.continentalness((coast + 1.0) * dirX, (coast + 1.0) * dirZ);
                    assertEquals(-0.08, cAtCoast, 0.06,
                        "c at s=0 must stay within the shared micro-roughness of -0.08, ray " + deg);

                    double prevC = terrain.continentalness((coast - 30.0) * dirX, (coast - 30.0) * dirZ);
                    for (double offset = -29.0; offset <= 500.0; offset += 1.0) {
                        double c = terrain.continentalness((coast + offset) * dirX, (coast + offset) * dirZ);
                        double delta = Math.abs(c - prevC);
                        assertTrue(delta < (offset <= 10.0 ? 0.02 : 0.05),
                            "Coast discontinuity at " + key + " ray " + deg + " offset " + offset + ": " + delta);
                        prevC = c;

                        if (offset >= 0.0) {
                            double s = -surface.computeSignedDistance((coast + offset) * dirX, (coast + offset) * dirZ, true);
                            if (s >= BAND_START && s <= BAND_END) {
                                configBand++;
                                bandSamples++;
                                maxBand = Math.max(maxBand, c);
                                minBand = Math.min(minBand, c);
                                if (c >= DEEP - 0.02) {
                                    configViolations++;
                                    violations++;
                                    mismatches.append(key).append(',').append(deg).append(',')
                                        .append(String.format(Locale.ROOT, "%.0f", offset)).append(',')
                                        .append(String.format(Locale.ROOT, "%.2f", s)).append(',')
                                        .append(String.format(Locale.ROOT, "%.6f", c)).append('\n');
                                }
                            }
                        }
                    }
                }

                assertTrue(validRays >= 18, key + " has too few rays that reach land: " + validRays);
                assertTrue(configBand >= 50, key + " produced too few guarantee-band samples: " + configBand);
                assertEquals(0, configViolations,
                    key + " has " + configViolations + " samples at or above the deep threshold inside the monument band");
                summary.append(key).append(',').append(configBand).append(',').append(configViolations).append(',')
                    .append(String.format(Locale.ROOT, "%.6f", maxBand)).append(',')
                    .append(String.format(Locale.ROOT, "%.6f", minBand)).append('\n');
                configs++;
            }
        }

        assertEquals(15, configs);
        assertEquals(0, violations);
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("ocean_band_summary.csv"), summary.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("ocean_band_mismatches.csv"), mismatches.toString(), StandardCharsets.UTF_8);
        }
        System.out.println("v3 deep band: configs=" + configs + " samples=" + bandSamples
            + " violations=" + violations);
    }

    @Test
    void v3SeabedHasReliefAndSeamountsStayOutsideBand() throws IOException {
        double globalMin = 1.0;
        double globalMax = -1.0;
        double bandMax = -2.0;
        double seamountMax = -2.0;
        StringBuilder csv = new StringBuilder(
            "preset,seed,island_size,ray_deg,offshore_distance,s_over_wo,continentalness,elevation,deep\n");
        int rows = 0;

        for (IslandDraft.Preset preset : new IslandDraft.Preset[]{IslandDraft.Preset.ROUND, IslandDraft.Preset.NATURAL}) {
            for (long seed : SEEDS) {
                IslandDraft draft = draft(preset, 4000, seed, 0.0);
                IslandTerrain terrain = terrain(draft, 0.0);
                ModernTerrainV2 surface = (ModernTerrainV2) terrain;
                for (int deg = 0; deg < 360; deg += 15) {
                    double rad = Math.toRadians(deg);
                    double dirX = Math.cos(rad);
                    double dirZ = Math.sin(rad);
                    double coast = coastRadius(surface, dirX, dirZ);
                    if (coast < 0.0) continue;

                    for (double offset = 0.0; offset <= 1600.0; offset += 10.0) {
                        double x = (coast + offset) * dirX;
                        double z = (coast + offset) * dirZ;
                        double c = terrain.continentalness(x, z);
                        double s = -surface.computeSignedDistance(x, z, true);
                        globalMin = Math.min(globalMin, c);
                        globalMax = Math.max(globalMax, c);
                        if (s >= BAND_START && s <= BAND_END) {
                            bandMax = Math.max(bandMax, c);
                        }
                        if (s > BAND_END) {
                            seamountMax = Math.max(seamountMax, c);
                        }
                        if (WRITE) {
                            csv.append(preset.name()).append(',').append(seed).append(",4000,")
                                .append(deg).append(',')
                                .append(String.format(Locale.ROOT, "%.0f", offset)).append(',')
                                .append(String.format(Locale.ROOT, "%.3f", s / WO)).append(',')
                                .append(String.format(Locale.ROOT, "%.6f", c)).append(',')
                                .append(String.format(Locale.ROOT, "%.1f", IslandTerrain.continentalnessToElevation(c)))
                                .append(',').append(c < DEEP).append('\n');
                            rows++;
                        }
                    }
                }
            }
        }

        assertTrue(globalMin >= -1.0 && globalMax <= 0.70,
            "v3 ocean must stay inside the global clamp: [" + globalMin + ", " + globalMax + "]");
        assertTrue(globalMax - globalMin > 0.10,
            "Seabed must have real relief, range was " + (globalMax - globalMin));
        assertTrue(bandMax < DEEP - 0.02,
            "No positive seabed excursion inside the band, max was " + bandMax);
        assertTrue(seamountMax > -0.78,
            "Seamounts should lift the seabed beyond 1.0*Wo above the soft floor, max was " + seamountMax);

        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("ocean_radial_profiles_v3.csv"), csv.toString(), StandardCharsets.UTF_8);
            StringBuilder summary = new StringBuilder("metric,value\n");
            summary.append("configs,6\n");
            summary.append("islet_density,0\n");
            summary.append("rows,").append(rows).append('\n');
            summary.append("c_min,").append(String.format(Locale.ROOT, "%.6f", globalMin)).append('\n');
            summary.append("c_max,").append(String.format(Locale.ROOT, "%.6f", globalMax)).append('\n');
            summary.append("c_max_in_band,").append(String.format(Locale.ROOT, "%.6f", bandMax)).append('\n');
            summary.append("c_max_beyond_wo,").append(String.format(Locale.ROOT, "%.6f", seamountMax)).append('\n');
            summary.append("deep_threshold,").append(String.format(Locale.ROOT, "%.2f", DEEP)).append('\n');
            summary.append("ocean_transition_width,").append(String.format(Locale.ROOT, "%.0f", WO)).append('\n');
            Files.writeString(EVIDENCE.resolve("ocean_profile_summary_v3.csv"), summary.toString(), StandardCharsets.UTF_8);
            writeHeightPng(EVIDENCE.resolve("ocean_height_6000_v3.png"), draft(IslandDraft.Preset.ROUND, 4000, 101L, 0.25));
        }
        System.out.println("v3 seabed: min=" + globalMin + " max=" + globalMax
            + " bandMax=" + bandMax + " seamountMax=" + seamountMax);
    }

    @Test
    void v3IsletsUseMaxStayOutsideBandAndHaveNoSpikes() throws IOException {
        int isletsSeen = 0;
        double peak = -2.0;
        double maxDelta = 0.0;
        double nearestIsletCoast = Double.POSITIVE_INFINITY;
        StringBuilder report = new StringBuilder("preset,seed,islet_land_samples,peak_c,nearest_land_offshore,max_delta\n");

        for (IslandDraft.Preset preset : new IslandDraft.Preset[]{IslandDraft.Preset.ROUND, IslandDraft.Preset.NATURAL}) {
            for (long seed : SEEDS) {
                IslandDraft draft = draft(preset, 2500, seed, 0.35);
                IslandTerrain terrain = terrain(draft, 0.35);
                int landSamples = 0;
                double configPeak = -2.0;
                double configNearest = Double.POSITIVE_INFINITY;
                double configDelta = 0.0;

                for (int deg = 0; deg < 360; deg += 5) {
                    double rad = Math.toRadians(deg);
                    double dirX = Math.cos(rad);
                    double dirZ = Math.sin(rad);
                    for (double r = 500.0; r <= 3000.0; r += 20.0) {
                        double x = r * dirX;
                        double z = r * dirZ;
                        double c = terrain.continentalness(x, z);
                        double mainlandSigned = ((ModernTerrainV2) terrain).computeSignedDistance(x, z, true);
                        if (c > -0.05 && mainlandSigned < -1.0 && terrain.signedDistance(x, z) > 0.0) {
                            landSamples++;
                            isletsSeen++;
                            configPeak = Math.max(configPeak, c);
                            peak = Math.max(peak, c);
                            double mainland = -mainlandSigned;
                            configNearest = Math.min(configNearest, mainland);
                            nearestIsletCoast = Math.min(nearestIsletCoast, mainland);
                            assertTrue(mainland > WO,
                                "Islet land must start beyond 1.0*Wo, found " + mainland + " at " + x + "," + z);
                            double delta = maxRayDelta(terrain, x, z, dirX, dirZ);
                            maxDelta = Math.max(maxDelta, delta);
                            configDelta = Math.max(configDelta, delta);
                        }
                    }
                }

                report.append(preset.name()).append(',').append(seed).append(',')
                    .append(landSamples).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", configPeak)).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", configNearest)).append(',')
                    .append(String.format(Locale.ROOT, "%.5f", configDelta)).append('\n');
            }
        }

        assertTrue(isletsSeen > 50, "Expected satellite islets at density 0.35, found " + isletsSeen + " land samples");
        assertTrue(peak <= 0.25 + 1e-9, "Islet peaks must not spike above the 0.25 design cap, was " + peak);
        assertTrue(maxDelta < 0.08,
            "Islet profiles must stay continuous smoothsteps without 1-block walls, max delta " + maxDelta);

        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("ocean_islets_v3.csv"), report.toString(), StandardCharsets.UTF_8);
        }
        System.out.println("v3 islets: samples=" + isletsSeen + " peak=" + peak
            + " nearest=" + nearestIsletCoast + " maxDelta=" + maxDelta);
    }

    private static double maxRayDelta(IslandTerrain terrain, double x, double z, double dirX, double dirZ) {
        double max = 0.0;
        double prev = terrain.continentalness(x - 60.0 * dirX, z - 60.0 * dirZ);
        for (double t = -59.0; t <= 60.0; t += 1.0) {
            double c = terrain.continentalness(x + t * dirX, z + t * dirZ);
            max = Math.max(max, Math.abs(c - prev));
            prev = c;
        }
        return max;
    }

    private static double coastRadius(ModernTerrainV2 terrain, double dirX, double dirZ) {
        for (double r = 5000.0; r >= 5.0; r -= 5.0) {
            if (terrain.computeSignedDistance(r * dirX, r * dirZ, true) > 0.0) {
                for (double out = r; out <= r + 5.0; out += 1.0) {
                    if (terrain.computeSignedDistance(out * dirX, out * dirZ, true) <= 0.0) {
                        return out;
                    }
                }
                return r + 5.0;
            }
        }
        return -1.0;
    }

    @Test
    void v3OceanMonumentTargetsDeepBand() throws IOException {
        StringBuilder csv = new StringBuilder(
            "config,chunk_x,chunk_z,center_x,center_z,c_center,c_max_surround,preview_biome\n");
        int configs = 0;
        for (IslandDraft.Preset preset : PRESETS) {
            for (long seed : SEEDS) {
                IslandDraft draft = draft(preset, 4000, seed, 0.25);
                Path tmp = Files.createTempDirectory("omniisle-monument-");
                try {
                    com.hack337.omniisle.client.WorldPackBuilder.export(tmp, draft);
                    String key = preset.name() + "_4000_s" + seed;
                    int chunkX;
                    int chunkZ;
                    try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(
                        tmp.resolve(com.hack337.omniisle.client.WorldPackBuilder.FILE_NAME).toFile())) {
                        String setJson = new String(zf.getInputStream(
                            zf.getEntry("data/minecraft/worldgen/structure_set/ocean_monuments.json")).readAllBytes(),
                            StandardCharsets.UTF_8);
                        var placement = com.google.gson.JsonParser.parseString(setJson).getAsJsonObject()
                            .getAsJsonObject("placement");
                        chunkX = placement.get("chunk_x").getAsInt();
                        chunkZ = placement.get("chunk_z").getAsInt();
                    }

                    IslandTerrain terrain = terrain(draft, 0.25);
                    double centerX = chunkX * 16 + 8;
                    double centerZ = chunkZ * 16 + 8;
                    double cCenter = terrain.continentalness(centerX, centerZ);
                    // Mirror the 29-block biome survey the monument runs at generation time.
                    double maxSurround = cCenter;
                    for (int dx = -29; dx <= 29; dx += 4) {
                        for (int dz = -29; dz <= 29; dz += 4) {
                            maxSurround = Math.max(maxSurround,
                                terrain.continentalness(centerX + dx, centerZ + dz));
                        }
                    }
                    double halfSpan = draft.worldSpan() * 0.5;
                    double slope = Math.hypot(
                        (terrain.continentalness(centerX + 8, centerZ) - terrain.continentalness(centerX - 8, centerZ)) / 16.0,
                        (terrain.continentalness(centerX, centerZ + 8) - terrain.continentalness(centerX, centerZ - 8)) / 16.0) * 64.0;
                    boolean shoreAllowed = !draft.enabledBiomes(com.hack337.omniisle.client.BiomePreview.Category.SHORE).isEmpty();
                    String biome = draft.selectBiome(
                        com.hack337.omniisle.client.BiomePreview.surfaceV3(cCenter, (int) centerX, (int) centerZ,
                            draft.biomeSeed(), draft.climateScale(), draft.gradientMode(), draft.gradientStrength(),
                            draft.theme(), slope, draft.generationVersionNumber(), halfSpan,
                            terrain.rivers(), terrain, draft.shoreSettings(), shoreAllowed),
                        (int) centerX, (int) centerZ).biomeId();

                    assertTrue(cCenter <= -0.62,
                        key + " monument center is not in the deep band: c=" + cCenter);
                    assertTrue(maxSurround < DEEP - 0.02,
                        key + " monument surroundings are not all deep water: max c=" + maxSurround);
                    assertTrue(biome.contains("ocean"),
                        key + " monument biome is not an ocean: " + biome);
                    csv.append(key).append(',').append(chunkX).append(',').append(chunkZ).append(',')
                        .append(String.format(Locale.ROOT, "%.0f", centerX)).append(',')
                        .append(String.format(Locale.ROOT, "%.0f", centerZ)).append(',')
                        .append(String.format(Locale.ROOT, "%.6f", cCenter)).append(',')
                        .append(String.format(Locale.ROOT, "%.6f", maxSurround)).append(',')
                        .append(biome).append('\n');
                    configs++;
                } finally {
                    try (var walk = Files.walk(tmp)) {
                        walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
                    }
                }
            }
        }
        assertEquals(15, configs, "every preset/seed config must place an ocean monument");
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("ocean_monument_band.csv"), csv.toString(), StandardCharsets.UTF_8);
        }
    }

    private static IslandDraft draft(IslandDraft.Preset preset, int size, long seed, double islets) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(size);
        draft.setShapeSeed(seed);
        draft.setBiomeSeed(seed);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(islets);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    private static IslandTerrain terrain(IslandDraft draft, double islets) {
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth, WO, draft.packedMask());
        IslandShapeConfig config = new IslandShapeConfig(3, islets, draft.riverDensity(),
            draft.isWorldBorderEnabled(), draft.worldBorderMargin(),
            Optional.empty(), Optional.empty(), List.of(), Optional.of(mask), draft.biomeSeed(),
            Optional.of(draft.shoreSettings()));
        return IslandTerrain.create(config);
    }

    private static void writeHeightPng(Path path, IslandDraft draft) throws IOException {
        IslandTerrain terrain = terrain(draft, 0.25);
        int span = 6000;
        int stride = 4;
        int dim = span / stride;
        BufferedImage image = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < dim; py++) {
            double z = -span / 2.0 + py * stride;
            for (int px = 0; px < dim; px++) {
                double x = -span / 2.0 + px * stride;
                double c = terrain.continentalness(x, z);
                image.setRGB(px, py, heightColor(IslandTerrain.continentalnessToElevation(c), c));
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
