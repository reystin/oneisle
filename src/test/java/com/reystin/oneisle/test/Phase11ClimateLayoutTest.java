package com.reystin.oneisle.test;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.ClimateLayout;
import com.reystin.oneisle.continent.IslandClimate;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.continent.ShoreField;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.8.4 universal v3 provinces: quantiles, pockets, continuity, ocean adjacency. */
public class Phase11ClimateLayoutTest {
    private static final long SEED = 2025L;
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
    @DisplayName("11L.1 every preset has disjoint warm/cold sectors around a temperate heartland")
    void universalSectors() {
        for (IslandDraft.Preset preset : PRESETS) {
            IslandDraft draft = draft(preset);
            ClimateLayout layout = layout(draft);
            IslandMask mask = mask(draft);
            int land = 0;
            int warm = 0;
            int cold = 0;
            double warmX = 0.0;
            double coldX = 0.0;
            double coldZ = 0.0;
            double halfX = Math.max(1.0, Math.max(Math.abs(mask.minX), Math.abs(mask.maxX)));
            double halfZ = Math.max(1.0, Math.max(Math.abs(mask.minZ), Math.abs(mask.maxZ)));
            for (int z = 0; z < mask.height; z++) {
                double wz = mask.minZ + (z + 0.5) * (mask.maxZ - mask.minZ) / mask.height;
                for (int x = 0; x < mask.width; x++) {
                    if (!mask.isLand(x, z)) continue;
                    double wx = mask.minX + (x + 0.5) * (mask.maxX - mask.minX) / mask.width;
                    ClimateLayout.Sample sample = layout.sample(wx, wz);
                    land++;
                    if (sample.province() == ClimateLayout.Province.WARM) {
                        warm++;
                        warmX += wx / halfX;
                    } else if (sample.province() == ClimateLayout.Province.COLD) {
                        cold++;
                        coldX += wx / halfX;
                        coldZ += wz / halfZ;
                    }
                }
            }
            double total = Math.max(1, land);
            String message = preset + " land=" + land + " warm=" + pct(warm, land)
                + " cold=" + pct(cold, land);
            assertTrue(land > 0, message);
            assertTrue(warm / total >= 0.10 && warm / total <= 0.22, message);
            assertTrue(cold / total >= 0.08 && cold / total <= 0.18, message);
            assertTrue(warm + cold < land, message + " provinces overlap or leave no heartland");
            assertTrue(warmX / Math.max(1, warm) < 0.0, message + " warm centroid must be west");
            assertTrue(coldX / Math.max(1, cold) > warmX / Math.max(1, warm),
                message + " cold sector must sit east of the warm sector");
            assertTrue(coldZ / Math.max(1, cold) < 0.15, message + " cold arc must lean north");
        }
    }

    @Test
    @DisplayName("11L.2 pockets are rare, province-scoped and stable for a fixed seed")
    void pocketsAreRareAndDeterministic() {
        for (IslandDraft.Preset preset : PRESETS) {
            IslandDraft draft = draft(preset);
            ClimateLayout first = layout(draft);
            ClimateLayout second = layout(draft(preset));
            IslandMask mask = mask(draft);
            int land = 0;
            int coldLand = 0;
            int pale = 0;
            int ice = 0;
            int iceOutsideCold = 0;
            int cherry = 0;
            for (int z = 0; z < mask.height; z++) {
                double wz = worldZ(mask, z);
                for (int x = 0; x < mask.width; x++) {
                    if (!mask.isLand(x, z)) continue;
                    double wx = worldX(mask, x);
                    ClimateLayout.Sample a = first.sample(wx, wz);
                    ClimateLayout.Sample b = second.sample(wx, wz);
                    assertEquals(a, b, preset + " layout is not deterministic at " + wx + "," + wz);
                    land++;
                    if (a.province() == ClimateLayout.Province.COLD) coldLand++;
                    if (a.paleStrength() > 0.0) pale++;
                    if (a.iceStrength() > 0.0) {
                        ice++;
                        if (a.province() != ClimateLayout.Province.COLD) iceOutsideCold++;
                    }
                    if (a.cherryStrength() > 0.0) cherry++;
                }
            }
            String message = preset + " pale=" + pct(pale, land) + " ice=" + pct(ice, land)
                + " cherry=" + pct(cherry, land);
            assertTrue(pale / (double) land >= 0.01 && pale / (double) land <= 0.05, message);
            assertTrue(cherry / (double) land >= 0.25 && cherry / (double) land <= 0.45, message);
            assertEquals(0, iceOutsideCold, message + " ice pocket leaked outside the cold sector");
            if (coldLand > 0) {
                assertTrue(ice / (double) coldLand >= 0.002 && ice / (double) coldLand <= 0.02, message);
            }
        }
    }

    @Test
    @DisplayName("11L.3 strength is continuous inside a sector and clamps to [0,1]")
    void strengthIsContinuous() {
        IslandDraft draft = draft(IslandDraft.Preset.NATURAL);
        ClimateLayout layout = layout(draft);
        IslandMask mask = mask(draft);
        double worst = 0.0;
        int pairs = 0;
        for (int z = 2; z < mask.height - 2; z += 3) {
            double wz = worldZ(mask, z);
            for (int x = 2; x < mask.width - 2; x += 3) {
                if (!mask.isLand(x, z) || !mask.isLand(x + 1, z)) continue;
                ClimateLayout.Sample a = layout.sample(worldX(mask, x), wz);
                ClimateLayout.Sample b = layout.sample(worldX(mask, x + 1), wz);
                assertTrue(a.strength() >= 0.0 && a.strength() <= 1.0);
                if (a.province() != b.province()) continue;
                worst = Math.max(worst, Math.abs(a.strength() - b.strength()));
                pairs++;
            }
        }
        assertTrue(pairs > 100, "not enough same-province pairs: " + pairs);
        assertTrue(worst <= 0.30, "strength jumps by " + worst + " between adjacent columns");
    }

    @Test
    @DisplayName("11L.4 water next to a warm/cold coast inherits the sector")
    void oceanAdjacency() {
        IslandDraft draft = draft(IslandDraft.Preset.NATURAL);
        ClimateLayout layout = layout(draft);
        IslandMask mask = mask(draft);
        int warmWater = 0;
        int warmMatches = 0;
        int coldWater = 0;
        int coldMatches = 0;
        for (int z = 1; z < mask.height - 1; z++) {
            double wz = worldZ(mask, z);
            for (int x = 1; x < mask.width - 1; x++) {
                if (mask.isLand(x, z)) continue;
                ClimateLayout.Province neighbour = null;
                for (int dz = -1; dz <= 1 && neighbour == null; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= mask.width || nz >= mask.height) continue;
                        if (!mask.isLand(nx, nz)) continue;
                        ClimateLayout.Province p = layout.sample(worldX(mask, nx), worldZ(mask, nz)).province();
                        if (p != ClimateLayout.Province.TEMPERATE) {
                            neighbour = p;
                            break;
                        }
                    }
                }
                if (neighbour == null) continue;
                ClimateLayout.Province sampled = layout.sample(worldX(mask, x), wz).province();
                if (neighbour == ClimateLayout.Province.WARM) {
                    warmWater++;
                    if (sampled == ClimateLayout.Province.WARM) warmMatches++;
                } else if (neighbour == ClimateLayout.Province.COLD) {
                    coldWater++;
                    if (sampled == ClimateLayout.Province.COLD) coldMatches++;
                }
            }
        }
        assertTrue(warmWater > 20, "no warm coastal water sampled");
        assertTrue(coldWater > 20, "no cold coastal water sampled");
        assertTrue(warmMatches / (double) warmWater >= 0.80,
            "warm coastal water matches " + warmMatches + "/" + warmWater);
        assertTrue(coldMatches / (double) coldWater >= 0.80,
            "cold coastal water matches " + coldMatches + "/" + coldWater);
    }

    @Test
    @DisplayName("11L.5 degenerate masks never throw and never invent a province")
    void degenerateMasksAreSafe() {
        byte[] packed = new byte[1];
        packed[0] = 0b0000_0010;
        IslandMask tiny = new IslandMask(2, 2, -200, 200, -200, 200, 60, 400,
            Base64.getEncoder().encodeToString(packed));
        ClimateLayout single = new ClimateLayout(tiny, SEED);
        assertEquals(1, single.landCount());
        ClimateLayout.Sample sample = single.sample(0.0, 0.0);
        assertNotNull(sample);
        assertTrue(sample.strength() >= 0.0 && sample.strength() <= 1.0);
        assertTrue(sample.paleStrength() >= 0.0 && sample.paleStrength() <= 1.0);
        for (double v : new double[]{-10_000, 0, 10_000}) {
            ClimateLayout.Sample clamped = single.sample(v, -v);
            assertNotNull(clamped);
            assertTrue(clamped.strength() >= 0.0 && clamped.strength() <= 1.0);
        }
    }

    @Test
    @DisplayName("11L.6 province boundary is bilinear, fractional and free of grid stairs")
    void provinceBoundaryIsSmooth() {
        IslandDraft draft = draft(IslandDraft.Preset.NATURAL);
        ClimateLayout layout = layout(draft);
        IslandMask mask = mask(draft);
        double halfSpan = draft.worldSpan() * 0.5;
        int crossings = 0;
        int fractional = 0;
        int blended = 0;
        for (int z = 4; z < mask.height - 4; z += 7) {
            double rowZ = worldZ(mask, z);
            ClimateLayout.Province previous = null;
            double previousStrength = -1.0;
            for (double wx = -halfSpan; wx <= halfSpan; wx += 2.0) {
                int mx = (int) Math.floor((wx - mask.minX) / (mask.maxX - mask.minX) * mask.width);
                int mz = (int) Math.floor((rowZ - mask.minZ) / (mask.maxZ - mask.minZ) * mask.height);
                if (mx < 0 || mz < 0 || mx >= mask.width || mz >= mask.height
                    || !mask.isLand(mx, mz)) {
                    previous = null;
                    previousStrength = -1.0;
                    continue;
                }
                ClimateLayout.Sample sample = layout.sample(wx, rowZ);
                boolean wasWarm = previous == ClimateLayout.Province.WARM;
                boolean isWarm = sample.province() == ClimateLayout.Province.WARM;
                if (previous != null && wasWarm != isWarm) {
                    crossings++;
                    double warmStrength = isWarm ? sample.strength() : previousStrength;
                    if (warmStrength < 0.95) fractional++;
                    if (warmStrength < 0.80) blended++;
                }
                previous = sample.province();
                previousStrength = sample.strength();
            }
        }
        assertTrue(crossings > 10, "not enough warm boundary crossings: " + crossings);
        assertTrue(fractional > crossings / 2, "province decided by a binary cell, not the "
            + "smoothed field (" + fractional + "/" + crossings + " crossings were fractional)");
        assertTrue(blended > 0, "no crossing sampled the province transition blend");
    }

    @Test
    @DisplayName("11L.7 warm/cold ocean rims use lukewarm/cold transition water")
    void oceanTransitionBands() {
        double halfSpan = 1750.0;
        double warmX = 0.0;
        double warmZ = 0.0;
        double warmTemp = -1.0;
        double coldX = 0.0;
        double coldZ = 0.0;
        double coldTemp = 2.0;
        for (int z = -1200; z <= 1200; z += 80) {
            for (int x = -1200; x <= 1200; x += 80) {
                IslandClimate.ClimateValues values = IslandClimate.climateAt(x, z, 720, SEED,
                    IslandClimate.GradientMode.NORTH_SOUTH, 0.5, "standard", halfSpan, 3);
                if (values.temperature() > warmTemp) {
                    warmTemp = values.temperature();
                    warmX = x;
                    warmZ = z;
                }
                if (values.temperature() < coldTemp) {
                    coldTemp = values.temperature();
                    coldX = x;
                    coldZ = z;
                }
            }
        }
        assertTrue(warmTemp > 0.66, "no warm reference column found: " + warmTemp);
        assertTrue(coldTemp < 0.34, "no cold reference column found: " + coldTemp);

        ClimateLayout.Sample warmRim = new ClimateLayout.Sample(
            ClimateLayout.Province.WARM, 0.35, 0.0, 0.0, 0.0);
        ClimateLayout.Sample warmCore = new ClimateLayout.Sample(
            ClimateLayout.Province.WARM, 0.90, 0.0, 0.0, 0.0);
        ClimateLayout.Sample coldRim = new ClimateLayout.Sample(
            ClimateLayout.Province.COLD, 0.35, 0.0, 0.0, 0.0);
        ClimateLayout.Sample coldCore = new ClimateLayout.Sample(
            ClimateLayout.Province.COLD, 0.90, 0.0, 0.0, 0.0);

        assertEquals("lukewarm_ocean", ocean(warmX, warmZ, halfSpan, warmRim),
            "warm province rim must not jump straight to warm_ocean");
        assertEquals("warm_ocean", ocean(warmX, warmZ, halfSpan, warmCore));
        assertEquals("cold_ocean", ocean(coldX, coldZ, halfSpan, coldRim),
            "cold province rim must not jump straight to frozen_ocean");
        assertEquals("frozen_ocean", ocean(coldX, coldZ, halfSpan, coldCore));
    }

    private static String ocean(double x, double z, double halfSpan, ClimateLayout.Sample layout) {
        IslandClimate.SampleResult result = IslandClimate.sampleV3(-0.30, x, z, 0.0, 720, SEED,
            IslandClimate.GradientMode.NORTH_SOUTH, 0.5, "standard", halfSpan, 3,
            -45.0, -45.0, 1.0, layout, false, ShoreField.NONE);
        assertEquals(IslandClimate.SurfaceZone.OCEAN, result.zone());
        return result.subType();
    }

    private static double worldX(IslandMask mask, int x) {
        return mask.minX + (x + 0.5) * (mask.maxX - mask.minX) / mask.width;
    }

    private static double worldZ(IslandMask mask, int z) {
        return mask.minZ + (z + 0.5) * (mask.maxZ - mask.minZ) / mask.height;
    }

    private static String pct(int count, int total) {
        return String.format(Locale.ROOT, "%.2f%%", count * 100.0 / Math.max(1, total));
    }

    private static ClimateLayout layout(IslandDraft draft) {
        ClimateLayout layout = IslandTerrain.create(config(draft)).climateLayout();
        assertNotNull(layout, "v3 terrain must expose a climate layout");
        return layout;
    }

    static IslandDraft draft(IslandDraft.Preset preset) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setShapeSeed(SEED);
        draft.setBiomeSeed(SEED);
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

    static IslandMask mask(IslandDraft draft) {
        double halfSpan = draft.worldSpan() * 0.5;
        return new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            TestWorldgen.OCEAN_TRANSITION, draft.packedMask());
    }

    static IslandShapeConfig config(IslandDraft draft) {
        return new IslandShapeConfig(3, draft.isletDensity(), draft.riverDensity(),
            false, 1000, Optional.empty(), Optional.empty(), List.of(), Optional.of(mask(draft)),
            draft.biomeSeed(), Optional.of(draft.shoreSettings()));
    }
}
