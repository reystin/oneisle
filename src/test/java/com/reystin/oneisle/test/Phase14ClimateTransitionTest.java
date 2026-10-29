package com.reystin.oneisle.test;

import com.reystin.oneisle.client.BiomePreview;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.ClimateLayout;
import com.reystin.oneisle.continent.IslandClimate;
import com.reystin.oneisle.continent.IslandTerrain;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.0.1 continuous warm/cold transitions: the categorical province no longer
 * selects land families, so a warm core never borders a cold core. Only high
 * mountains are exempt, and their snow is allowed in every province.
 */
public class Phase14ClimateTransitionTest {
    private static final long SEED = 2025L;
    private static final int GRID = 192;
    private static final List<IslandDraft.Preset> PRESETS = List.of(
        IslandDraft.Preset.MINESHIELD_4_MAP, IslandDraft.Preset.NATURAL);
    private static final Set<Integer> WARM_CORE = Set.of(10, 11, 12, 13);
    private static final Set<Integer> COLD_CORE = Set.of(0, 1);
    private static final Set<Integer> BAND = Set.of(2, 4, 5, 11);
    private static final int NON_LAND = -2;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("14C.1 warm cores and cold cores are never adjacent")
    void warmColdCoresNeverTouch() {
        for (IslandDraft.Preset preset : PRESETS) {
            Scan scan = scan(preset);
            String message = preset + " warmCore=" + scan.warmCore + " coldCore=" + scan.coldCore
                + " mountains=" + scan.mountainCore;
            assertEquals(0, scan.coreAdjacent,
                message + " warm-core and cold-core columns touch outside mountains");
        }
        Scan natural = scan(IslandDraft.Preset.NATURAL);
        assertTrue(natural.warmCore > 0 && natural.coldCore > 0,
            "the transect scan needs both cores, saw warm=" + natural.warmCore
                + " cold=" + natural.coldCore);
    }

    @Test
    @DisplayName("14C.2 a band or a mountain always separates the cores")
    void bandsSeparateCores() {
        Scan natural = scan(IslandDraft.Preset.NATURAL);
        assertTrue(natural.coreSwitches > 0,
            "no warm->cold transect crossed the island: " + natural.families);
        assertEquals(0, natural.coreSwitchUnbanded,
            "warm and cold cores meet without a meadow/birch/plains/savanna or mountain gap");
        assertTrue(natural.bandedSwitches > 0,
            "no transect passed through an explicit band family: " + natural.bandColumns);
        for (IslandDraft.Preset preset : PRESETS) {
            Scan scan = scan(preset);
            assertEquals(0, scan.coreSwitchUnbanded,
                preset + " warm/cold cores meet without an intermediate family");
            if (scan.warmCore > 0 && scan.coldCore > 0) {
                assertTrue(scan.bandedSwitches > 0,
                    preset + " cores are not separated by a band family: " + scan.bandColumns);
            }
        }
    }

    @Test
    @DisplayName("14C.3 snow is a mountain feature in any province, never a lowland feature")
    void snowOnlyOnMountains() {
        ClimateLayout.Sample warm = new ClimateLayout.Sample(
            ClimateLayout.Province.WARM, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        ClimateLayout.Sample cold = new ClimateLayout.Sample(
            ClimateLayout.Province.COLD, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        IslandClimate.ClimateValues coldPeak =
            new IslandClimate.ClimateValues(0.30, 0.50, 0.90, 0.0, 0.0);
        IslandClimate.ClimateValues hotPeak =
            new IslandClimate.ClimateValues(0.98, 0.30, 0.90, 0.0, 0.0);

        assertEquals(15, select(coldPeak, 0.60, 1.6, warm, true),
            "a cold high peak in a warm province must be snowy peaks");
        assertEquals(14, select(hotPeak, 0.60, 1.6, warm, true),
            "a hot high peak in a warm province must be stony peaks");
        assertEquals(15, select(coldPeak, 0.60, 1.6, cold, true),
            "snow peaks are province-independent");
        assertEquals(5, select(new IslandClimate.ClimateValues(0.75, 0.50, 0.90, 0.0, 0.0),
            0.60, 1.6, ClimateLayout.temperate(), true),
            "a temperate high peak is a meadow");

        int warmFoothill = select(coldPeak, 0.60, 1.40, warm, false);
        assertTrue(warmFoothill != 0 && warmFoothill != 1 && warmFoothill != 15,
            "a warm lowland/foothill must stay warm next to a desert, got " + warmFoothill);
        assertEquals(0, select(new IslandClimate.ClimateValues(0.95, 0.50, 0.10, 0.0, 0.0),
            0.60, 0.5, cold, false),
            "a cold lowland stays snowy plains even when the physical temperature is hot");
        int warmLowland = select(new IslandClimate.ClimateValues(0.95, 0.30, 0.10, 0.0, 0.0),
            0.60, 0.5, warm, false);
        assertTrue(warmLowland == 11 || warmLowland == 12 || warmLowland == 13
            || warmLowland == 10, "a warm lowland must keep its warm family, got " + warmLowland);
    }

    private static int select(IslandClimate.ClimateValues values, double continentalness,
                              double tier, ClimateLayout.Sample layout, boolean provinceBias) {
        return IslandClimate.selectLandFamilyV3(values, continentalness, 0, 0, SEED,
            100.0, 100.0, tier, 0.10, layout, provinceBias);
    }

    private record Scan(int warmCore, int coldCore, int mountainCore, int coreAdjacent,
                        int coreSwitches, int coreSwitchUnbanded, int bandedSwitches,
                        java.util.TreeMap<Integer, Integer> bandColumns,
                        java.util.TreeMap<Integer, Integer> families) {}

    private static final java.util.Map<IslandDraft.Preset, Scan> CACHE =
        new EnumMap<>(IslandDraft.Preset.class);

    private static synchronized Scan scan(IslandDraft.Preset preset) {
        return CACHE.computeIfAbsent(preset, Phase14ClimateTransitionTest::scanTerrain);
    }

    private static Scan scanTerrain(IslandDraft.Preset preset) {
        IslandDraft draft = Phase11ClimateLayoutTest.draft(preset);
        IslandTerrain terrain = IslandTerrain.create(Phase11ClimateLayoutTest.config(draft));
        double half = draft.worldSpan() * 0.5;
        double step = draft.worldSpan() / GRID;
        int[] fams = new int[GRID * GRID];
        double[] tiers = new double[GRID * GRID];
        double[] cs = new double[GRID * GRID];
        Arrays.fill(fams, NON_LAND);
        java.util.TreeMap<Integer, Integer> families = new java.util.TreeMap<>();
        int warmCore = 0;
        int coldCore = 0;
        int mountainCore = 0;
        for (int gz = 0; gz < GRID; gz++) {
            double z = -half + (gz + 0.5) * step;
            for (int gx = 0; gx < GRID; gx++) {
                double x = -half + (gx + 0.5) * step;
                double c = terrain.continentalness(x, z);
                double slope = slope(terrain, x, z);
                BiomePreview.Selection selection = BiomePreview.selectionV3(
                    c, x, z, draft.biomeSeed(), draft.climateScale(), draft.gradientMode(),
                    draft.gradientStrength(), draft.theme(), slope, 3, half,
                    terrain.rivers(), terrain, draft.shoreSettings(), true);
                int index = gz * GRID + gx;
                cs[index] = c;
                tiers[index] = terrain.heightTierAt(x, z);
                if (selection.type() == null || !selection.type().isLand()) continue;
                int family = selection.landFamily();
                fams[index] = family;
                families.merge(family, 1, Integer::sum);
                boolean mountain = tiers[index] >= 1.5 && c > 0.50;
                if (WARM_CORE.contains(family)) {
                    warmCore++;
                    if (mountain) mountainCore++;
                } else if (COLD_CORE.contains(family)) {
                    coldCore++;
                    if (mountain) mountainCore++;
                }
            }
        }

        int coreAdjacent = 0;
        int[][] offsets = {{1, 0}, {0, 1}, {1, 1}, {-1, 1}};
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int index = gz * GRID + gx;
                int a = fams[index];
                if (a == NON_LAND) continue;
                for (int[] offset : offsets) {
                    int nx = gx + offset[0];
                    int nz = gz + offset[1];
                    if (nx < 0 || nz < 0 || nx >= GRID || nz >= GRID) continue;
                    int other = nz * GRID + nx;
                    int b = fams[other];
                    if (b == NON_LAND) continue;
                    boolean warmCold = WARM_CORE.contains(a) && COLD_CORE.contains(b)
                        || COLD_CORE.contains(a) && WARM_CORE.contains(b);
                    if (!warmCold) continue;
                    if (mountain(tiers, cs, index) || mountain(tiers, cs, other)) continue;
                    coreAdjacent++;
                }
            }
        }

        int[] state = new int[4];
        java.util.TreeMap<Integer, Integer> bandColumns = new java.util.TreeMap<>();
        int[] path = new int[GRID];
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) path[gx] = gz * GRID + gx;
            walk(fams, tiers, cs, path, state, bandColumns);
        }
        for (int gx = 0; gx < GRID; gx++) {
            for (int gz = 0; gz < GRID; gz++) path[gz] = gz * GRID + gx;
            walk(fams, tiers, cs, path, state, bandColumns);
        }
        System.out.println(String.format(Locale.ROOT,
            "PHASE14 climate %s families=%s warmCore=%d coldCore=%d mountainCore=%d adjacent=%d"
                + " switches=%d unbanded=%d banded=%d bands=%s",
            preset, families, warmCore, coldCore, mountainCore, coreAdjacent,
            state[0], state[1], state[2], bandColumns));
        return new Scan(warmCore, coldCore, mountainCore, coreAdjacent,
            state[0], state[1], state[2], bandColumns, families);
    }

    /** Walks one row/column: every warm<->cold core switch must have a band or mountain gap. */
    private static void walk(int[] fams, double[] tiers, double[] cs, int[] path,
                             int[] state, java.util.TreeMap<Integer, Integer> bandColumns) {
        int i = 0;
        while (i < path.length) {
            int start = i;
            while (i < path.length && fams[path[i]] != NON_LAND) i++;
            int end = i;
            if (end - start >= 2) walkRun(fams, tiers, cs, path, start, end, state, bandColumns);
            i++;
        }
    }

    private static void walkRun(int[] fams, double[] tiers, double[] cs, int[] path,
                                int start, int end, int[] state,
                                java.util.TreeMap<Integer, Integer> bandColumns) {
        int lastKind = 0;
        int lastPos = -1;
        for (int p = start; p < end; p++) {
            int family = fams[path[p]];
            int kind = WARM_CORE.contains(family) ? 1 : COLD_CORE.contains(family) ? -1 : 0;
            if (kind == 0) continue;
            if (lastKind != 0 && kind != lastKind) {
                state[0]++;
                boolean separated = false;
                for (int q = lastPos + 1; q < p; q++) {
                    int gapFamily = fams[path[q]];
                    if (BAND.contains(gapFamily)) {
                        separated = true;
                        bandColumns.merge(gapFamily, 1, Integer::sum);
                    }
                    if (mountain(tiers, cs, path[q])) separated = true;
                }
                if (separated) {
                    state[2]++;
                } else {
                    state[1]++;
                }
            }
            lastKind = kind;
            lastPos = p;
        }
    }

    private static boolean mountain(double[] tiers, double[] cs, int index) {
        return tiers[index] >= 1.5 && cs[index] > 0.50;
    }

    private static double slope(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 4.0, z);
        double cW = terrain.continentalness(x - 4.0, z);
        double cS = terrain.continentalness(x, z + 4.0);
        double cN = terrain.continentalness(x, z - 4.0);
        return Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
    }
}
