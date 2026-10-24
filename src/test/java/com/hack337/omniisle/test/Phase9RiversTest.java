package com.hack337.omniisle.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.ShareCode;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandRivers;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ModernTerrainV2;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.7 river checks: legacy configs stay river-free and river mouths keep a
 * continuous, slope-bounded surface across 10 000 sampled points.
 */
public class Phase9RiversTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/rivers");
    private static final double VANILLA_MAX_SLOPE = 1.8;
    // A 0.14 carve over a 6-block minimum channel has a smoothstep bank gradient of at most
    // 0.14*1.5/6 = 0.035 c/block. Anything larger would be a step, not a bank.
    private static final double BANK_DELTA_BUDGET = 0.035;
    private static final double WATERLINE_DELTA_BUDGET = 0.035;
    private static final double PROBE_STEP = 0.25;

    @Test
    void legacyConfigsDefaultToNoRivers() throws IOException {
        IslandShapeConfig minimal = IslandShapeConfig.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"generation_version\":2}"))
            .getOrThrow(IllegalStateException::new);
        assertEquals(0.0, minimal.riverDensity, 1.0e-9, "legacy JSON without river_density must default to 0");
        ModernTerrainV2 terrain = new ModernTerrainV2(minimal);
        assertNotNull(terrain.rivers());
        assertFalse(terrain.rivers().isEnabled(), "legacy v2 config must not carve rivers");

        IslandShapeConfig modern = sampleConfig(0.0);
        JsonObject encoded = IslandShapeConfig.CODEC.encodeStart(JsonOps.INSTANCE, modern)
            .getOrThrow(IllegalStateException::new).getAsJsonObject();
        encoded.remove("river_density");
        IslandShapeConfig decoded = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE, encoded)
            .getOrThrow(IllegalStateException::new);
        assertEquals(0.0, decoded.riverDensity, 1.0e-9);
        assertFalse(new ModernTerrainV2(decoded).rivers().isEnabled());

        IslandDraft legacyDraft = ShareCode.decodeJson("{\"version\":2,\"generation_version\":3,\"island_size\":4000}");
        assertEquals(0.0, legacyDraft.riverDensity(), 1.0e-9,
            "share codes/configs without river_density must not silently enable rivers");

        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("river_legacy_defaults.txt"),
                "codec_absent_river_density,0.0\n"
                    + "legacy_rivers_enabled,false\n"
                    + "sharecode_absent_river_density," + legacyDraft.riverDensity() + "\n",
                StandardCharsets.UTF_8);
        }
    }

    @Test
    void riverMouthsKeepContinuityAndSlopeBudget() throws IOException {
        IslandDraft draft = baseDraft(0.45);
        ModernTerrainV2 terrain = (ModernTerrainV2) terrain(draft);
        IslandRivers rivers = terrain.rivers();
        assertTrue(rivers.isEnabled(), "river density 0.45 must enable rivers");

        List<double[]> mouths = new ArrayList<>();
        double halfSpan = draft.worldSpan() * 0.5;
        for (double z = -halfSpan; z <= halfSpan; z += 8.0) {
            for (double x = -halfSpan; x <= halfSpan; x += 8.0) {
                if (!rivers.isRiver(x, z)) continue;
                double d = terrain.signedDistance(x, z);
                if (d > 0.0 && d < 96.0) mouths.add(new double[]{x, z, d});
            }
        }
        assertTrue(mouths.size() >= 10, "expected river-mouth cells near the coast, found " + mouths.size());

        Random random = new Random(0xC0FFEE);
        StringBuilder csv = new StringBuilder("index,x,z,signed_distance,river,carve,elevation,delta_c_1block,slope_1block\n");
        int samples = 0;
        int belowSea = 0;
        int carved = 0;
        double maxDelta = 0.0;
        double maxSlope = 0.0;
        double slopeBudget = VANILLA_MAX_SLOPE * 1.5;
        while (samples < 10000) {
            double[] mouth = mouths.get(random.nextInt(mouths.size()));
            double radius = 28.0 * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Math.PI * 2.0;
            double x = mouth[0] + Math.cos(angle) * radius;
            double z = mouth[1] + Math.sin(angle) * radius;

            double c = terrain.continentalness(x, z);
            double cxp = terrain.continentalness(x + 1.0, z);
            double cxm = terrain.continentalness(x - 1.0, z);
            double czp = terrain.continentalness(x, z + 1.0);
            double czm = terrain.continentalness(x, z - 1.0);
            double delta = Math.max(Math.max(Math.abs(cxp - c), Math.abs(cxm - c)),
                Math.max(Math.abs(czp - c), Math.abs(czm - c)));
            double e = IslandTerrain.continentalnessToElevation(c);
            double slope = Math.max(
                Math.abs(IslandTerrain.continentalnessToElevation(cxp)
                    - IslandTerrain.continentalnessToElevation(cxm)) / 2.0,
                Math.abs(IslandTerrain.continentalnessToElevation(czp)
                    - IslandTerrain.continentalnessToElevation(czm)) / 2.0);
            double carve = rivers.riverCarveContinentalness(x, z);
            if (c < 0.0) belowSea++;
            if (carve > 0.001) carved++;
            maxDelta = Math.max(maxDelta, delta);
            maxSlope = Math.max(maxSlope, slope);
            csv.append(samples).append(',').append(fmt(x)).append(',').append(fmt(z)).append(',')
                .append(fmt(terrain.signedDistance(x, z))).append(',')
                .append(rivers.isRiver(x, z)).append(',').append(fmt(carve)).append(',')
                .append(fmt(e)).append(',').append(fmt(delta)).append(',').append(fmt(slope)).append('\n');
            samples++;
        }

        // Walk downhill from every mouth to the waterline and measure the c change across
        // the land/ocean branch switch. A carve that stops at d=0 shows up here as a step.
        int waterlineCrossings = 0;
        double maxWaterlineDelta = 0.0;
        double maxWaterlineCarve = 0.0;
        StringBuilder crossingsCsv = new StringBuilder("mouth_index,start_x,start_z,cross_x,cross_z,delta_c_per_block,carve_at_crossing\n");
        for (int mouthIndex = 0; mouthIndex < mouths.size(); mouthIndex++) {
            double[] mouth = mouths.get(mouthIndex);
            double x = mouth[0];
            double z = mouth[1];
            double prevC = terrain.continentalness(x, z);
            double prevCarve = rivers.riverCarveContinentalness(x, z);
            double prevSd = terrain.signedDistance(x, z);
            for (int step = 0; step < 640 && prevSd >= 0.0; step++) {
                double gx = (terrain.signedDistance(x + 1.0, z) - terrain.signedDistance(x - 1.0, z)) * 0.5;
                double gz = (terrain.signedDistance(x, z + 1.0) - terrain.signedDistance(x, z - 1.0)) * 0.5;
                double gradient = Math.hypot(gx, gz);
                if (gradient < 1.0e-6) break;
                double nx = x - gx / gradient * PROBE_STEP;
                double nz = z - gz / gradient * PROBE_STEP;
                double sd = terrain.signedDistance(nx, nz);
                double c = terrain.continentalness(nx, nz);
                double carve = rivers.riverCarveContinentalness(nx, nz);
                if (sd < 0.0) {
                    double delta = Math.abs(c - prevC) / PROBE_STEP;
                    maxWaterlineDelta = Math.max(maxWaterlineDelta, delta);
                    maxWaterlineCarve = Math.max(maxWaterlineCarve, Math.max(prevCarve, carve));
                    crossingsCsv.append(mouthIndex).append(',').append(fmt(mouth[0])).append(',')
                        .append(fmt(mouth[1])).append(',').append(fmt(nx)).append(',').append(fmt(nz))
                        .append(',').append(fmt(delta)).append(',').append(fmt(carve)).append('\n');
                    waterlineCrossings++;
                    break;
                }
                x = nx;
                z = nz;
                prevC = c;
                prevCarve = carve;
                prevSd = sd;
            }
        }

        String summary = "river_mouth_cells," + mouths.size() + "\n"
            + "samples," + samples + "\n"
            + "points_below_sea_level," + belowSea + "\n"
            + "points_with_carve," + carved + "\n"
            + "max_delta_c_1block," + fmt(maxDelta) + "\n"
            + "bank_delta_budget," + fmt(BANK_DELTA_BUDGET) + "\n"
            + "max_slope_1block," + fmt(maxSlope) + "\n"
            + "slope_budget," + fmt(slopeBudget) + "\n"
            + "waterline_crossings," + waterlineCrossings + "\n"
            + "max_waterline_delta_per_block," + fmt(maxWaterlineDelta) + "\n"
            + "max_waterline_carve," + fmt(maxWaterlineCarve) + "\n"
            + "waterline_delta_budget," + fmt(WATERLINE_DELTA_BUDGET) + "\n";
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("river_mouth_samples.csv"), csv.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("river_waterline_crossings.csv"), crossingsCsv.toString(), StandardCharsets.UTF_8);
            Files.writeString(EVIDENCE.resolve("river_mouth_summary.txt"), summary, StandardCharsets.UTF_8);
        }

        assertTrue(carved > 500, "too few sampled points actually inside carved channels: " + carved);
        assertTrue(belowSea > 100, "too few below-sea river mouth samples: " + belowSea);
        assertTrue(maxDelta <= BANK_DELTA_BUDGET,
            "river-mouth bank delta " + maxDelta + " exceeded the smoothstep bank budget " + BANK_DELTA_BUDGET);
        assertTrue(maxSlope <= slopeBudget,
            "river-mouth surface slope " + maxSlope + " exceeded vanilla*1.5 (" + slopeBudget + ")");
        assertTrue(waterlineCrossings >= mouths.size(),
            "expected a waterline probe for every mouth, found " + waterlineCrossings + "/" + mouths.size());
        assertTrue(maxWaterlineDelta <= WATERLINE_DELTA_BUDGET,
            "river-mouth waterline step " + maxWaterlineDelta + " exceeded " + WATERLINE_DELTA_BUDGET);
    }

    private static IslandShapeConfig sampleConfig(double riverDensity) {
        IslandMask mask = new IslandMask(192, 192, -2720, 2720, -2720, 2720, 192.0,
            TestWorldgen.OCEAN_TRANSITION, "");
        return new IslandShapeConfig(2, 0.0, riverDensity, false, 1000,
            Optional.empty(), Optional.empty(), List.of(), Optional.of(mask), 101L);
    }

    private static IslandDraft baseDraft(double riverDensity) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.NATURAL);
        draft.setIslandSize(4000);
        draft.setShapeSeed(101L);
        draft.setBiomeSeed(101L);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(0.0);
        draft.setRiverDensity(riverDensity);
        draft.setClimateScale(720);
        draft.rebuildShape();
        return draft;
    }

    private static IslandShapeConfig config(IslandDraft draft) {
        double halfSpan = draft.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(draft.resolution(), draft.resolution(),
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            TestWorldgen.OCEAN_TRANSITION, draft.packedMask());
        return new IslandShapeConfig(3, draft.isletDensity(), draft.riverDensity(),
            false, 1000, Optional.empty(), Optional.empty(), List.of(), Optional.of(mask),
            draft.biomeSeed(), Optional.of(draft.shoreSettings()));
    }

    private static IslandTerrain terrain(IslandDraft draft) {
        return IslandTerrain.create(config(draft));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
