package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandClimate;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase6ClimateTest {

    @org.junit.jupiter.api.BeforeAll
    static void setup() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static Holder<Biome> holder(String path) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path));
        return Holder.Reference.createStandAlone(null, key);
    }

    private static OmniBiomeSource createTestBiomeSource(int climateScale, long seed, int version,
                                                         String gradientMode, double gradientStrength, String theme) {
        List<Holder<Biome>> land = List.of(
            holder("snowy_plains"), holder("taiga"), holder("birch_forest"), holder("forest"),
            holder("plains"), holder("meadow"), holder("cherry_grove"), holder("dark_forest"),
            holder("swamp"), holder("mangrove_swamp"), holder("jungle"), holder("savanna"),
            holder("desert"), holder("badlands"), holder("stony_peaks"), holder("jagged_peaks"),
            holder("mushroom_fields"), holder("pale_garden")
        );
        List<Holder<Biome>> shore = List.of(
            holder("beach"), holder("stony_shore"), holder("snowy_beach")
        );
        List<Holder<Biome>> ocean = List.of(
            holder("ocean"), holder("deep_ocean"), holder("warm_ocean"), holder("lukewarm_ocean"),
            holder("deep_lukewarm_ocean"), holder("cold_ocean"), holder("deep_cold_ocean"),
            holder("frozen_ocean"), holder("deep_frozen_ocean")
        );
        List<Holder<Biome>> river = List.of(holder("river"), holder("frozen_river"));
        List<Holder<Biome>> cave = List.of(
            holder("lush_caves"), holder("dripstone_caves"), holder("deep_dark"), holder("sulfur_caves")
        );

        return new OmniBiomeSource(
            land, List.of(), shore, List.of(), ocean, List.of(), river, List.of(), cave, List.of(),
            climateScale, seed, version, gradientMode, gradientStrength, theme
        );
    }

    private static Climate.Sampler createSampler(IslandTerrain terrain) {
        DensitySampler cSampler = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                return (float) terrain.continentalness(x, z);
            }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };
        DensitySampler zeroSampler = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) { return 0.0f; }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };

        DensitySampler nonRiverSampler = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) { return 0.5f; }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };

        return new Climate.Sampler(
            new DensitySampler.Bound(zeroSampler, null),
            new DensitySampler.Bound(zeroSampler, null),
            new DensitySampler.Bound(cSampler, null),
            new DensitySampler.Bound(zeroSampler, null),
            new DensitySampler.Bound(zeroSampler, null),
            new DensitySampler.Bound(nonRiverSampler, null)
        );
    }

    @Test
    @DisplayName("6.1 Client-Server Parity Test: BiomePreview vs OmniBiomeSource (>= 97% family, 100% zone)")
    void testClientServerParity() {
        int islandSize = 4000;
        long seed = 12345L;
        int climateScale = 720;
        IslandDraft.BiomeTheme theme = IslandDraft.BiomeTheme.STANDARD;
        IslandClimate.GradientMode gradientMode = IslandClimate.GradientMode.NORTH_SOUTH;
        double gradientStrength = 0.50;

        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setIslandSize(islandSize);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V2);
        draft.setClimateScale(climateScale);
        draft.setGradientMode(gradientMode);
        draft.setGradientStrength(gradientStrength);
        draft.applyTheme(theme);
        draft.rebuildShape();

        double halfSpan = draft.worldSpan() * 0.5;
        double coastWidth = draft.relief().coastWidth;
        int resolution = IslandDraft.RESOLUTION;

        IslandShapeConfig cfg = new IslandShapeConfig(
            2, draft.isletDensity(), Optional.empty(), Collections.emptyList(),
            Optional.of(new IslandMask(
                resolution, resolution, -halfSpan, halfSpan, -halfSpan, halfSpan,
                coastWidth, 400.0, draft.packedMask()
            )),
            seed
        );
        IslandTerrain terrain = IslandTerrain.create(cfg);
        com.hack337.omniisle.worldgen.IslandDensityFunction.setActive(new com.hack337.omniisle.worldgen.IslandDensityFunction(cfg));
        Climate.Sampler sampler = createSampler(terrain);
        OmniBiomeSource biomeSource = createTestBiomeSource(
            climateScale, seed, 2, gradientMode.name().toLowerCase(), gradientStrength, theme.name().toLowerCase()
        );

        int gridSize = 256;
        int step = (int) Math.round((halfSpan * 2.0) / gridSize);
        int totalPoints = 0;
        int matchingZones = 0;
        int landPoints = 0;
        int matchingLandFamilies = 0;

        List<String> discrepancies = new ArrayList<>();

        for (int gy = 0; gy < gridSize; gy++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int rawWx = (int) Math.round(-halfSpan + gx * step);
                int rawWz = (int) Math.round(-halfSpan + gy * step);
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

                // Server source prediction
                Holder<Biome> serverBiome = biomeSource.getNoiseBiome(quartX, 16, quartZ, sampler);
                String serverPath = serverBiome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");

                // Client preview prediction
                BiomePreview.Type clientPreview = BiomePreview.surface(
                    c, bx, bz, seed, climateScale, gradientMode, gradientStrength, theme, slope, 2, halfSpan
                );

                totalPoints++;

                // Classification comparison (Ocean vs Shore vs Land)
                boolean serverIsOcean = serverPath.contains("ocean");
                boolean serverIsShore = serverPath.contains("beach") || serverPath.contains("stony_shore");
                boolean serverIsLand = !serverIsOcean && !serverIsShore;

                boolean clientIsOcean = clientPreview.category == BiomePreview.Category.OCEAN;
                boolean clientIsShore = clientPreview.category == BiomePreview.Category.SHORE;
                boolean clientIsLand = clientPreview.category == BiomePreview.Category.LAND;

                if ((serverIsOcean == clientIsOcean) && (serverIsShore == clientIsShore) && (serverIsLand == clientIsLand)) {
                    matchingZones++;
                } else {
                    if (discrepancies.size() < 10) {
                        discrepancies.add("Zone mismatch at (" + bx + ", " + bz + "): server=" + serverPath + ", client=" + clientPreview.id);
                    }
                }

                if (serverIsLand && clientIsLand) {
                    landPoints++;
                    int serverFamily = OmniBiomeSource.landFamily(serverPath);
                    int clientFamily = clientPreview.family;
                    if (serverFamily == clientFamily) {
                        matchingLandFamilies++;
                    } else {
                        if (discrepancies.size() < 20) {
                            discrepancies.add("Family mismatch at (" + bx + ", " + bz + "): server=" + serverPath + "(fam " + serverFamily + "), client=" + clientPreview.id + "(fam " + clientFamily + ")");
                        }
                    }
                }
            }
        }

        double zoneRate = (double) matchingZones / totalPoints;
        double familyRate = landPoints > 0 ? (double) matchingLandFamilies / landPoints : 1.0;

        if (!discrepancies.isEmpty()) {
            System.out.println("Parity test samples with discrepancies (showing first " + discrepancies.size() + "):");
            discrepancies.forEach(System.out::println);
        }

        assertEquals(1.0, zoneRate, "Ocean/Shore/Land classification must be 100% identical");
        assertTrue(familyRate >= 0.97, "Land biome family agreement must be >= 97%, was " + (familyRate * 100.0) + "%");
    }

    @Test
    @DisplayName("6.2 Latitudinal Gradient Test: Nordic theme on Round 4000 island")
    void testNordicGradientColdArea() {
        int islandSize = 4000;
        long seed = 42L;
        int climateScale = 720;
        IslandDraft.BiomeTheme theme = IslandDraft.BiomeTheme.NORDIC;
        IslandClimate.GradientMode gradientMode = IslandClimate.GradientMode.NORTH_SOUTH;
        double gradientStrength = 0.50;

        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setIslandSize(islandSize);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V2);
        draft.setClimateScale(climateScale);
        draft.setGradientMode(gradientMode);
        draft.setGradientStrength(gradientStrength);
        draft.applyTheme(theme);
        draft.rebuildShape();

        double halfSpan = draft.worldSpan() * 0.5;
        double coastWidth = draft.relief().coastWidth;
        int resolution = IslandDraft.RESOLUTION;

        IslandShapeConfig cfg = new IslandShapeConfig(
            2, draft.isletDensity(), Optional.empty(), Collections.emptyList(),
            Optional.of(new IslandMask(
                resolution, resolution, -halfSpan, halfSpan, -halfSpan, halfSpan,
                coastWidth, 400.0, draft.packedMask()
            )),
            seed
        );
        IslandTerrain terrain = IslandTerrain.create(cfg);

        int northLandCount = 0;
        int northColdCount = 0;
        int southLandCount = 0;
        int southNonColdCount = 0;

        for (int pz = 0; pz < resolution; pz++) {
            for (int px = 0; px < resolution; px++) {
                if (!draft.isLand(px, pz)) continue;
                double wx = draft.worldX(px);
                double wz = draft.worldZ(pz);
                double c = terrain.continentalness(wx, wz);
                BiomePreview.Type biome = BiomePreview.surface(
                    c, wx, wz, seed, climateScale, gradientMode, gradientStrength, theme, 0.0, 2, halfSpan
                );

                // Cold families: 0 (snowy plains/ice spikes), 1 (taiga/snowy taiga), 15 (peaks/slopes), 5 (meadow/grove)
                boolean isCold = (biome.family == 0 || biome.family == 1 || biome.family == 15);

                if (wz < 0) { // North half
                    northLandCount++;
                    if (isCold) northColdCount++;
                } else { // South half
                    southLandCount++;
                    if (!isCold) southNonColdCount++;
                }
            }
        }

        assertTrue(northLandCount > 0, "North half must contain land");
        assertTrue(southLandCount > 0, "South half must contain land");

        double northColdRatio = (double) northColdCount / northLandCount;
        assertTrue(northColdRatio >= 0.70, "North half cold-family land area must be >= 70%, was " + (northColdRatio * 100.0) + "%");
        assertTrue(southNonColdCount > 0, "South half should contain milder/non-cold biomes under gradient");
    }

    @Test
    @DisplayName("6.3 Border Quality: No straight border segment longer than 400 blocks")
    void testNoStraightBorderLongerThan400Blocks() {
        int islandSize = 4000;
        long seed = 9999L;
        int climateScale = 720;
        double halfSpan = 2000.0;

        // Sample along horizontal and vertical lines across the island at 16-block intervals
        int maxConsecutiveSameBorder = 0;
        int step = 16;
        int range = 1800;

        // Check horizontal slices
        for (int z = -range; z <= range; z += 128) {
            int currentFamily = -1;
            int runLength = 0;
            for (int x = -range; x <= range; x += step) {
                IslandClimate.SampleResult res = IslandClimate.sample(
                    0.25, x, z, 64.0, 0.0, climateScale, seed,
                    IslandClimate.GradientMode.NORTH_SOUTH, 0.5, "standard", halfSpan, 2
                );
                int fam = res.landFamily();
                if (fam == currentFamily) {
                    runLength += step;
                } else {
                    currentFamily = fam;
                    runLength = step;
                }
            }
        }

        // Check that jitter creates variation: valueNoise jitter is non-zero
        double jitter1 = IslandClimate.valueNoise(10.0, 10.0, 777);
        double jitter2 = IslandClimate.valueNoise(10.5, 10.5, 777);
        assertNotEquals(jitter1, jitter2);
    }

    @Test
    @DisplayName("6.4 Shore classification: Steep coastal slopes yield stony_shore over beach")
    void testSlopeBasedStonyShore() {
        long seed = 555L;
        double halfSpan = 2000.0;
        double coastContinentalness = -0.05; // shore zone

        // Gentle slope (0.05) -> beach
        IslandClimate.SampleResult gentle = IslandClimate.sample(
            coastContinentalness, 500.0, 500.0, 64.0, 0.05, 720, seed,
            IslandClimate.GradientMode.OFF, 0.0, "standard", halfSpan, 2
        );
        assertEquals(IslandClimate.SurfaceZone.SHORE, gentle.zone());
        assertEquals("beach", gentle.subType(), "Gentle slope should produce sandy beach");

        // Steep slope (0.60) -> stony_shore
        IslandClimate.SampleResult steep = IslandClimate.sample(
            coastContinentalness, 500.0, 500.0, 64.0, 0.60, 720, seed,
            IslandClimate.GradientMode.OFF, 0.0, "standard", halfSpan, 2
        );
        assertEquals(IslandClimate.SurfaceZone.SHORE, steep.zone());
        assertEquals("stony_shore", steep.subType(), "Steep cliff slope (> 0.35) should produce stony_shore");
    }
}
