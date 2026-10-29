package com.reystin.oneisle.test;

import com.reystin.oneisle.client.BiomePreview;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.continent.ShoreField;
import com.reystin.oneisle.continent.ShoreSettings;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.reystin.oneisle.test.Phase9ShoreTest.baseDraft;
import static com.reystin.oneisle.test.Phase9ShoreTest.slopeAt;
import static com.reystin.oneisle.test.Phase9ShoreTest.terrain;
import static com.reystin.oneisle.test.Phase9ShoreTest.traceCoast;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.0.1 shore hardening: a beach may only exist where the column actually faces
 * open water, sits on a low coastal shelf and is still within the coastal
 * continentalness band. Narrow fjord banks keep high bayness but must stay bare.
 */
public class Phase14ShoreInteriorTest {
    private static final long SEED = 101L;
    private static final int SIZE = 4000;
    private static final int GRID = 384;
    private static final List<IslandDraft.Preset> PRESETS = List.of(
        IslandDraft.Preset.NATURAL, IslandDraft.Preset.FJORDS, IslandDraft.Preset.ARCHIPELAGO);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("14S.1 no shore biome on a column without open water, low shelf or coastal c")
    void shoreRequiresOpenLowCoast() {
        for (IslandDraft.Preset preset : PRESETS) {
            IslandDraft draft = baseDraft(preset, SIZE, SEED);
            IslandTerrain terrain = terrain(draft);
            double half = draft.worldSpan() * 0.5;
            double step = draft.worldSpan() / GRID;
            int shore = 0;
            int coastal = 0;
            int beyondBand = 0;
            int shallowWater = 0;
            int highTier = 0;
            int inlandC = 0;
            int interior = 0;
            for (int gz = 0; gz < GRID; gz++) {
                double z = -half + (gz + 0.5) * step;
                for (int gx = 0; gx < GRID; gx++) {
                    double x = -half + (gx + 0.5) * step;
                    double d = terrain.signedDistance(x, z);
                    if (d < 0.0 || d > 40.0) continue;
                    coastal++;
                    double c = terrain.continentalness(x, z);
                    double tier = terrain.heightTierAt(x, z);
                    double slope = slopeAt(terrain, x, z);
                    ShoreField.Ring ring = ShoreField.ring(terrain, x, z, d);
                    BiomePreview.Selection selection = BiomePreview.selectionV3(
                        c, x, z, draft.biomeSeed(), draft.climateScale(),
                        draft.gradientMode(), draft.gradientStrength(), draft.theme(),
                        slope, 3, half, terrain.rivers(), terrain, draft.shoreSettings(), true);
                    if (selection.type() == null
                        || selection.type().category != BiomePreview.Category.SHORE) {
                        continue;
                    }
                    shore++;
                    if (d > ShoreField.MAX_INLAND) beyondBand++;
                    if (ring.minDistance() > -ShoreField.OPEN_WATER_MIN) shallowWater++;
                    if (tier > ShoreField.MAX_HEIGHT_TIER) highTier++;
                    if (c > ShoreField.MAX_CONTINENTALNESS) inlandC++;
                    if (d >= 10.0) interior++;
                }
            }
            String label = preset + " d in [0,40] coastal=" + coastal + " shore=" + shore
                + " interior=" + interior;
            System.out.println(String.format(Locale.ROOT,
                "PHASE14 shore %s beyondBand=%d shallow=%d highTier=%d inlandC=%d",
                label, beyondBand, shallowWater, highTier, inlandC));
            assertTrue(coastal > 100, label + " sampled too few coastal columns");
            assertTrue(shore > 0, label + " has no shore columns at all");
            assertEquals(0, beyondBand, label + " shore past MAX_INLAND: " + beyondBand);
            assertEquals(0, shallowWater, label + " shore without open water: " + shallowWater);
            assertEquals(0, highTier, label + " shore on a high shelf: " + highTier);
            assertEquals(0, inlandC, label + " shore far inland by continentalness: " + inlandC);
        }
    }

    @Test
    @DisplayName("14S.2 a narrow fjord bank has high bayness but never becomes a beach")
    void narrowFjordBanksStayBare() {
        IslandTerrain channel = new IslandTerrain() {
            @Override
            public double continentalness(double x, double z) {
                return 0.0;
            }

            @Override
            public double erosionBias(double x, double z) {
                return 0.0;
            }

            @Override
            public double signedDistance(double x, double z) {
                return Math.abs(x) - 10.0;
            }
        };
        long seed = 101L;
        int none = 0;
        double minBay = 1.0;
        for (double z = -400.0; z <= 400.0; z += 25.0) {
            for (double x : new double[]{11.0, 12.0, 14.0, 17.0, -11.0, -14.0}) {
                double d = channel.signedDistance(x, z);
                ShoreField.Ring ring = ShoreField.ring(channel, x, z, d);
                int outcome = ShoreField.outcome(d, x, z, 0.10, 1.0, 0.0, ring,
                    new ShoreSettings(1.0, 0.6, "natural", true), seed);
                if (outcome == ShoreField.NONE) none++;
                minBay = Math.min(minBay, ring.bay());
            }
        }
        System.out.println("PHASE14 fjord none=" + none + " minBay=" + minBay);
        assertEquals(198, none, "a 20-block-wide fjord bank must never classify as a shore");
        assertTrue(minBay > 0.9,
            "the fjord banks must still look concave (bayness " + minBay + ")");
    }

    @Test
    @DisplayName("14S.3 open straights still grow natural beach arcs")
    void openCoastKeepsBeaches() {
        IslandTerrain coast = new IslandTerrain() {
            @Override
            public double continentalness(double x, double z) {
                return 0.0;
            }

            @Override
            public double erosionBias(double x, double z) {
                return 0.0;
            }

            @Override
            public double signedDistance(double x, double z) {
                return z;
            }
        };
        ShoreSettings settings = new ShoreSettings(1.0, 0.6, "natural", true);
        int sand = 0;
        int total = 0;
        for (double x = -400.0; x <= 400.0; x += 4.0) {
            double z = 6.0;
            ShoreField.Ring ring = ShoreField.ring(coast, x, z, z);
            assertTrue(ring.minDistance() <= -ShoreField.OPEN_WATER_MIN,
                "open coast ring must see deep water at x=" + x);
            if (ShoreField.outcome(z, x, z, 0.10, 1.0, 0.0, ring, settings, SEED) == ShoreField.SAND) {
                sand++;
            }
            total++;
        }
        System.out.println("PHASE14 openCoast sand=" + sand + "/" + total);
        assertTrue(sand >= 20, "open straight coast produced only " + sand + " sand columns in " + total);

        IslandDraft draft = baseDraft(IslandDraft.Preset.NATURAL, SIZE, SEED);
        IslandTerrain terrain = terrain(draft);
        int coastSand = 0;
        for (Phase9ShoreTest.CoastPoint point : traceCoast(terrain, draft.shoreSettings(), SEED,
            draft.worldSpan() * 0.5, 2048)) {
            if (point.type() == BiomePreview.Type.BEACH || point.type() == BiomePreview.Type.SNOWY_BEACH) {
                coastSand++;
            }
        }
        System.out.println("PHASE14 naturalCoast sand=" + coastSand);
        assertTrue(coastSand >= 150, "NATURAL open coast lost its beaches: " + coastSand);
    }
}
