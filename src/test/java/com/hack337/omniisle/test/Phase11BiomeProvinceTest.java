package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.ClimateLayout;
import com.hack337.omniisle.continent.IslandClimate;
import com.hack337.omniisle.continent.IslandTerrain;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.8.4 v3 family tree: elevation, province and rare-pocket gates on real terrain. */
public class Phase11BiomeProvinceTest {
    private static final long SEED = 2025L;
    private static final int SIZE = 256;
    private static final List<IslandDraft.Preset> PRESETS = List.of(
        IslandDraft.Preset.MINESHIELD_4_MAP, IslandDraft.Preset.NATURAL);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("11B.1 snow, wetlands, cherry, desert and pale follow their physical gates")
    void familyGates() {
        for (IslandDraft.Preset preset : PRESETS) {
            Scan scan = scan(preset);
            String message = preset + " " + scan.families;
            assertTrue(scan.snowBadTier == 0, message + " snow below the mountain tier: " + scan.snowBadTier);
            assertTrue(scan.coldCoreBad == 0,
                message + " non-snow/taiga family in a cold core: " + scan.coldCoreBad);
            assertTrue(scan.coldBandBad == 0,
                message + " family outside meadow/birch in a cold band: " + scan.coldBandBad);
            assertTrue(scan.warmBandBad == 0,
                message + " family outside savanna/plains/meadow in a warm band: " + scan.warmBandBad);
            assertTrue(scan.warmCoreBad == 0,
                message + " family outside the warm core set: " + scan.warmCoreBad);
            assertTrue(scan.wetland > 0, message + " wetlands never appear on coastal lowlands");
            assertTrue(scan.wetlandBad == 0, message + " wetland outside c/slope/tier gates: " + scan.wetlandBad);
            assertTrue(scan.cherryBad == 0, message + " cherry outside its foothill band: " + scan.cherryBad);
            assertTrue(scan.desertBad == 0, message + " desert outside the warm core or c>0.36: " + scan.desertBad);
            assertTrue(scan.badlandsBad == 0, message + " badlands outside the warm core: " + scan.badlandsBad);
            assertTrue(scan.paleBad == 0, message + " pale_garden outside its pocket: " + scan.paleBad);
            assertTrue(scan.iceHintBad == 0, message + " ice_spikes hint outside a cold ice pocket: " + scan.iceHintBad);
            assertTrue(scan.mushroomBad == 0, message + " mushroom on the main island: " + scan.mushroomBad);
            assertTrue(scan.desertNextToCold == 0,
                message + " desert touching a snowy/taiga column: " + scan.desertNextToCold);
        }
    }

    @Test
    @DisplayName("11B.2 mushrooms are offshore-only and appear where satellite land exists")
    void mushroomsStayOffshore() {
        Scan mineshield = scan(IslandDraft.Preset.MINESHIELD_4_MAP);
        assertTrue(mineshield.mushroomMain == 0, "main-island mushroom columns: " + mineshield.mushroomMain);
        assertTrue(mineshield.mushroomIslet > 0,
            "the Mineshield bay islet must host a mushroom patch: " + mineshield.families);
        for (IslandDraft.Preset preset : PRESETS) {
            Scan scan = scan(preset);
            assertEquals(0, scan.mushroomMain, preset + " mushroom on main island");
        }
    }

    @Test
    @DisplayName("11B.3 mountain snow is province-independent; bands blend cold/warm edges")
    void syntheticSnowGate() {
        IslandClimate.ClimateValues values = new IslandClimate.ClimateValues(0.35, 0.50, 0.90, 0.0, 0.0);
        ClimateLayout.Sample warm = new ClimateLayout.Sample(
            ClimateLayout.Province.WARM, 1.0, 0.0, 0.0, 0.0);
        ClimateLayout.Sample cold = new ClimateLayout.Sample(
            ClimateLayout.Province.COLD, 1.0, 0.0, 0.0, 0.0);
        assertEquals(15, IslandClimate.selectLandFamilyV3(values, 0.60, 0, 0, SEED,
            100.0, 100.0, 1.6, 0.10, ClimateLayout.temperate(), true),
            "high temperate peak with cold effective temperature is a snowy peak");
        assertEquals(14, IslandClimate.selectLandFamilyV3(
            new IslandClimate.ClimateValues(0.98, 0.30, 0.90, 0.0, 0.0), 0.60, 0, 0, SEED,
            100.0, 100.0, 1.6, 0.10, ClimateLayout.temperate(), true),
            "high temperate peak with warm effective temperature is stony peaks");
        assertEquals(15, IslandClimate.selectLandFamilyV3(values, 0.60, 0, 0, SEED,
            100.0, 100.0, 1.6, 0.10, cold, true),
            "snow peaks are allowed in the cold sector: mountains are exempt from bands");
        assertEquals(15, IslandClimate.selectLandFamilyV3(values, 0.60, 0, 0, SEED,
            100.0, 100.0, 1.6, 0.10, warm, true),
            "snow peaks are allowed in the warm sector: mountains are exempt from bands");
        assertEquals(1, IslandClimate.selectLandFamilyV3(values, 0.60, 0, 0, SEED,
            100.0, 100.0, 1.4, 0.10, ClimateLayout.temperate(), true),
            "below the snow tier a cold foothill is taiga, not a snow peak");

        ClimateLayout.Sample coldBand = new ClimateLayout.Sample(
            ClimateLayout.Province.TEMPERATE, 0.0, 0.30, 0.50, 0.0, 0.0, 0.0);
        assertEquals(5, IslandClimate.selectLandFamilyV3(
            new IslandClimate.ClimateValues(0.45, 0.70, 0.20, 0.0, 0.0), 0.10, 0, 0, SEED,
            100.0, 100.0, 0.5, 0.05, coldBand, true),
            "humid cold band is meadow");
        assertEquals(2, IslandClimate.selectLandFamilyV3(
            new IslandClimate.ClimateValues(0.45, 0.30, 0.20, 0.0, 0.0), 0.10, 0, 0, SEED,
            100.0, 100.0, 0.5, 0.05, coldBand, true),
            "dry cold band is birch");
        ClimateLayout.Sample warmBand = new ClimateLayout.Sample(
            ClimateLayout.Province.TEMPERATE, 0.0, 0.50, 0.30, 0.0, 0.0, 0.0);
        assertEquals(4, IslandClimate.selectLandFamilyV3(
            new IslandClimate.ClimateValues(0.62, 0.45, 0.20, 0.0, 0.0), 0.10, 0, 0, SEED,
            100.0, 100.0, 0.5, 0.05, warmBand, true),
            "warm band keeps plains between temperate and warm cores");
        assertEquals(0, IslandClimate.selectLandFamilyV3(values, 0.60, 0, 0, SEED,
            100.0, 100.0, 0.5, 0.05, cold, true),
            "dry cold core lowland is snowy plains, never a peak");
        assertEquals(1, IslandClimate.selectLandFamilyV3(
            new IslandClimate.ClimateValues(0.35, 0.70, 0.20, 0.0, 0.0), 0.60, 0, 0, SEED,
            100.0, 100.0, 0.5, 0.05, cold, true),
            "humid cold core lowland is taiga");
    }

    @Test
    @DisplayName("11B.4 effective climate reports the chosen family centroid for weighting")
    void effectiveClimateIsFamilyCentroid() {
        Scan scan = scan(IslandDraft.Preset.NATURAL);
        assertTrue(scan.centroidBad == 0,
            "selection temperature is not the family centroid on " + scan.centroidBad + " columns");
    }

    private record Scan(TreeMap<Integer, Integer> families, int snow, int snowBadTier,
                        int coldCoreBad, int coldBandBad, int warmBandBad, int warmCoreBad,
                        int wetland, int wetlandBad, int cherryBad,
                        int desertBad, int badlandsBad, int paleBad,
                        int iceHintBad, int mushroomBad, int mushroomMain, int mushroomIslet,
                        int desertNextToCold, int centroidBad) {}

    private static final java.util.Map<IslandDraft.Preset, Scan> CACHE =
        new java.util.EnumMap<>(IslandDraft.Preset.class);

    private static synchronized Scan scan(IslandDraft.Preset preset) {
        return CACHE.computeIfAbsent(preset, Phase11BiomeProvinceTest::scanTerrain);
    }

    private static Scan scanTerrain(IslandDraft.Preset preset) {
        IslandDraft draft = Phase11ClimateLayoutTest.draft(preset);
        IslandTerrain terrain = IslandTerrain.create(Phase11ClimateLayoutTest.config(draft));
        ClimateLayout layout = terrain.climateLayout();
        double half = draft.worldSpan() * 0.5;
        double step = draft.worldSpan() / SIZE;
        TreeMap<Integer, Integer> families = new TreeMap<>();
        int[] fams = new int[SIZE * SIZE];
        int snow = 0, snowBadTier = 0;
        int coldCoreBad = 0, coldBandBad = 0, warmBandBad = 0, warmCoreBad = 0;
        int wetland = 0, wetlandBad = 0, cherryBad = 0;
        int desertBad = 0, badlandsBad = 0, paleBad = 0, iceHintBad = 0;
        int mushroomBad = 0, mushroomMain = 0, mushroomIslet = 0, centroidBad = 0;
        for (int gz = 0; gz < SIZE; gz++) {
            double z = -half + (gz + 0.5) * step;
            for (int gx = 0; gx < SIZE; gx++) {
                double x = -half + (gx + 0.5) * step;
                double c = terrain.continentalness(x, z);
                double slope = slope(terrain, x, z);
                BiomePreview.Selection selection = BiomePreview.selectionV3(
                    c, x, z, draft.biomeSeed(), 720, draft.gradientMode(), draft.gradientStrength(),
                    draft.theme(), slope, 3, half, terrain.rivers(), terrain,
                    draft.shoreSettings(), true);
                int index = gz * SIZE + gx;
                if (!selection.type().isLand()) {
                    fams[index] = -1;
                    continue;
                }
                int family = selection.landFamily();
                double qc = (double) (long) ((float) c * 10000.0f) / 10000.0;
                fams[index] = family;
                families.merge(family, 1, Integer::sum);
                double tier = terrain.heightTierAt(x, z);
                ClimateLayout.Sample sector = layout.sample(x, z);
                IslandClimate.ClimateValues values = IslandClimate.climateAt(x, z, 720,
                    draft.biomeSeed(), draft.gradientMode(), draft.gradientStrength(),
                    draft.theme().name(), half, 3);
                double mountains = values.mountains();
                double diff = sector.warmStrength() - sector.coldStrength();
                double effTemp = clamp01(clamp01(values.temperature()
                    - Math.max(0.0, qc - 0.28) * 0.62)
                    + 0.14 * sector.warmStrength() - 0.24 * sector.coldStrength());
                if (family == 15) {
                    snow++;
                    if (!(tier >= 1.5 && qc > 0.50)) snowBadTier++;
                }
                boolean mountainExempt = (tier >= 1.5 && qc > 0.50)
                    || (tier >= 1.25 && qc > 0.30 && mountains > 0.42);
                if (!mountainExempt) {
                    if (diff <= -IslandClimate.V3_CLIMATE_CORE_DIFF) {
                        if (family != 0 && family != 1 && family != 16) coldCoreBad++;
                    } else if (diff <= -IslandClimate.V3_CLIMATE_BAND_DIFF) {
                        if (family != 2 && family != 5 && family != 16) coldBandBad++;
                    } else if (diff >= IslandClimate.V3_CLIMATE_CORE_DIFF) {
                        if (family != 9 && family != 10 && family != 11
                            && family != 12 && family != 13 && family != 16) {
                            warmCoreBad++;
                        }
                    } else if (diff >= IslandClimate.V3_CLIMATE_BAND_DIFF) {
                        if (family != 4 && family != 5 && family != 9
                            && family != 11 && family != 16) {
                            warmBandBad++;
                        }
                    }
                }
                if (family == 8 || family == 9) {
                    wetland++;
                    if (!(qc < 0.15 && slope < 0.12 && tier <= 1.0 && mountains < 0.35)) wetlandBad++;
                    if (family == 9 && diff < IslandClimate.V3_CLIMATE_BAND_DIFF) wetlandBad++;
                }
                if (family == 6 && !(qc > 0.34 && qc <= 0.48
                    && diff > -IslandClimate.V3_CLIMATE_BAND_DIFF
                    && diff < IslandClimate.V3_CLIMATE_BAND_DIFF)) {
                    cherryBad++;
                }
                if (family == 12 && !(diff >= IslandClimate.V3_CLIMATE_CORE_DIFF && qc <= 0.36)) desertBad++;
                if (family == 13 && diff < IslandClimate.V3_CLIMATE_CORE_DIFF) badlandsBad++;
                double paleHum = clamp01(values.humidity() + 0.65 * sector.paleStrength());
                if (family == 17 && !(sector.paleStrength() > 0.50 && paleHum > 0.88
                    && effTemp >= 0.33 && effTemp <= 0.82
                    && diff > -IslandClimate.V3_CLIMATE_BAND_DIFF
                    && diff < IslandClimate.V3_CLIMATE_BAND_DIFF)) {
                    paleBad++;
                }
                if ("ice_spikes".equals(selection.landHint())
                    && !(family == 0 && sector.province() == ClimateLayout.Province.COLD
                        && sector.iceStrength() > 0.5)) {
                    iceHintBad++;
                }
                if (family == 16) {
                    if (!(c < 0.05) || terrain.distances(x, z).main() >= 0.0) {
                        mushroomBad++;
                        mushroomMain++;
                    } else {
                        mushroomIslet++;
                    }
                }
                // The effective climate reported for weighting is the chosen family's centroid.
                double centroidTemp = com.hack337.omniisle.continent.BiomeShare.familyCenterTemperature(family);
                if (Math.abs(selection.temperature() - centroidTemp) > 1.0e-9) centroidBad++;
            }
        }
        int desertNextToCold = 0;
        for (int gz = 1; gz < SIZE - 1; gz++) {
            for (int gx = 1; gx < SIZE - 1; gx++) {
                int index = gz * SIZE + gx;
                if (fams[index] != 12) continue;
                boolean adjacent = false;
                for (int dz = -1; dz <= 1 && !adjacent; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int neighbourFamily = fams[index + dz * SIZE + dx];
                        if (neighbourFamily == 0 || neighbourFamily == 1) {
                            adjacent = true;
                            break;
                        }
                    }
                }
                if (adjacent) desertNextToCold++;
            }
        }
        System.out.println(String.format(Locale.ROOT,
            "PHASE11 biome %s families=%s snow=%d wetland=%d mushrooms=%d/%d desert=%d pale=%d",
            preset, families, snow, wetland, mushroomMain, mushroomIslet,
            families.getOrDefault(12, 0), families.getOrDefault(17, 0)));
        return new Scan(families, snow, snowBadTier, coldCoreBad, coldBandBad, warmBandBad,
            warmCoreBad, wetland, wetlandBad, cherryBad, desertBad, badlandsBad, paleBad,
            iceHintBad, mushroomBad, mushroomMain, mushroomIslet, desertNextToCold, centroidBad);
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }

    private static double slope(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 4.0, z);
        double cW = terrain.continentalness(x - 4.0, z);
        double cS = terrain.continentalness(x, z + 4.0);
        double cN = terrain.continentalness(x, z - 4.0);
        return Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
    }
}
