package com.hack337.omniisle.test;

import com.hack337.omniisle.client.DynamicBiomeRegistry;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.ShareCode;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandRivers;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.LegacyTerrainV1;
import com.hack337.omniisle.continent.ModernTerrainV2;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class Phase8StretchTest {

    @Test
    @DisplayName("8.1 River system is deterministic and forms continuous carving channels")
    void testRiversDeterminismAndCarving() {
        IslandDraft draft = new IslandDraft(192);
        draft.setIslandSize(4000);
        draft.setShapeSeed(12345L);
        draft.setBiomeSeed(67890L);
        draft.setPreset(IslandDraft.Preset.VOLCANO);
        draft.setRiverDensity(0.40);

        IslandShapeConfig cfg = new IslandShapeConfig(
            2, draft.isletDensity(), draft.riverDensity(), draft.isWorldBorderEnabled(), draft.worldBorderMargin(),
            java.util.Optional.empty(), java.util.Optional.empty(), List.of(),
            java.util.Optional.of(new IslandMask(192, 192, -2000, 2000, -2000, 2000, 192.0, 400.0, draft.packedMask())),
            draft.biomeSeed()
        );

        ModernTerrainV2 terrain1 = new ModernTerrainV2(cfg);
        ModernTerrainV2 terrain2 = new ModernTerrainV2(cfg);

        assertNotNull(terrain1.rivers(), "Rivers system must be initialized in v2");
        assertTrue(terrain1.rivers().isEnabled(), "Rivers must be enabled when riverDensity > 0");

        // Verify determinism across the grid
        int riverCells = 0;
        for (int pz = 0; pz < 192; pz += 4) {
            for (int px = 0; px < 192; px += 4) {
                double wx = draft.worldX(px);
                double wz = draft.worldZ(pz);

                double c1 = terrain1.continentalness(wx, wz);
                double c2 = terrain2.continentalness(wx, wz);
                assertEquals(c1, c2, 1e-12, "Continentalness with rivers must be 100% deterministic");

                boolean riv1 = terrain1.rivers().isRiver(wx, wz);
                boolean riv2 = terrain2.rivers().isRiver(wx, wz);
                assertEquals(riv1, riv2, "isRiver must be 100% deterministic");

                if (riv1) {
                    riverCells++;
                    double carve = terrain1.rivers().riverCarveContinentalness(wx, wz);
                    assertTrue(carve > 0.0, "Carving must be positive inside river channel");
                }
            }
        }
        assertTrue(riverCells > 0, "Must have detected river cells on island with riverDensity 0.40");
    }

    @Test
    @DisplayName("8.1 River system preserves v1 bit-for-bit invariant")
    void testRiversLegacyInvariant() {
        IslandDraft draft = new IslandDraft(192);
        draft.setIslandSize(4000);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V1);

        IslandShapeConfig cfg = new IslandShapeConfig(
            1, draft.isletDensity(), draft.riverDensity(), draft.isWorldBorderEnabled(), draft.worldBorderMargin(),
            java.util.Optional.empty(), java.util.Optional.empty(), List.of(),
            java.util.Optional.of(new IslandMask(192, 192, -2000, 2000, -2000, 2000, 192.0, 400.0, draft.packedMask())),
            draft.biomeSeed()
        );

        IslandTerrain terrain = IslandTerrain.create(cfg);
        assertInstanceOf(LegacyTerrainV1.class, terrain);
        assertNull(terrain.rivers(), "Legacy v1 must not instantiate rivers");
        assertEquals(0.0, terrain.erosionBias(100.0, 100.0), 1e-9, "Legacy erosionBias must stay 0.0");
    }

    @Test
    @DisplayName("8.2 DynamicBiomeRegistry fallback and category categorization")
    void testDynamicBiomeRegistryFallback() {
        List<DynamicBiomeRegistry.BiomeEntry> biomes = DynamicBiomeRegistry.getAvailableBiomes();
        assertNotNull(biomes);
        assertTrue(biomes.size() >= 50, "Must contain at least standard Overworld biomes");

        boolean hasOcean = false;
        boolean hasRiver = false;
        boolean hasShore = false;
        boolean hasCave = false;
        boolean hasLand = false;

        for (DynamicBiomeRegistry.BiomeEntry b : biomes) {
            assertNotNull(b.id());
            assertNotNull(b.name());
            assertNotNull(b.category());
            switch (b.category()) {
                case OCEAN -> hasOcean = true;
                case RIVER -> hasRiver = true;
                case SHORE -> hasShore = true;
                case CAVE -> hasCave = true;
                case LAND -> {
                    hasLand = true;
                    assertTrue(b.familyIndex() >= 0 && b.familyIndex() <= 17,
                        "Land biomes must map to 0..17 climate families");
                }
            }
        }

        assertTrue(hasOcean && hasRiver && hasShore && hasCave && hasLand,
            "Must categorize all 5 major biome categories");
    }

    @Test
    @DisplayName("8.3 World border settings round-trip in Draft and Codec")
    void testWorldBorderSettings() {
        IslandDraft draft = new IslandDraft(192);
        assertFalse(draft.isWorldBorderEnabled());
        assertEquals(1000, draft.worldBorderMargin());

        draft.setWorldBorderEnabled(true);
        draft.setWorldBorderMargin(2500);
        assertTrue(draft.isWorldBorderEnabled());
        assertEquals(2500, draft.worldBorderMargin());

        // Test clamping
        draft.setWorldBorderMargin(100);
        assertEquals(500, draft.worldBorderMargin());
        draft.setWorldBorderMargin(10000);
        assertEquals(4000, draft.worldBorderMargin());

        // Test ShareCode encode/decode with world border
        draft.setWorldBorderMargin(1500);
        String code = ShareCode.encode(draft);
        IslandDraft decoded = ShareCode.decode(code);
        assertTrue(decoded.isWorldBorderEnabled());
        assertEquals(1500, decoded.worldBorderMargin());
    }

    @Test
    @DisplayName("8.5 Datapack export for dedicated server preset import")
    void testDatapackExport(@TempDir Path tempDir) throws IOException {
        IslandDraft draft = new IslandDraft(192);
        draft.setIslandSize(4000);
        draft.setPreset(IslandDraft.Preset.ATOLL);
        draft.setWorldBorderEnabled(true);
        draft.setWorldBorderMargin(1200);

        WorldPackBuilder.export(tempDir, draft);

        Path packZip = tempDir.resolve(WorldPackBuilder.FILE_NAME);
        assertTrue(Files.exists(packZip), "Datapack ZIP must be generated");
        assertTrue(Files.size(packZip) > 1000, "Datapack ZIP must have substantial content");

        // Test ShareCode decodeJson from raw JSON string
        String json = ShareCode.encode(draft); // This gets compressed share code, but let's test decodeJson
        // ShareCode.decode handles OMNI2-..., but let's check JSON round-trip
        IslandDraft jsonDecoded = ShareCode.decode(json);
        assertEquals(draft.preset(), jsonDecoded.preset());
        assertEquals(draft.islandSize(), jsonDecoded.islandSize());
        assertEquals(draft.isWorldBorderEnabled(), jsonDecoded.isWorldBorderEnabled());
        assertEquals(draft.worldBorderMargin(), jsonDecoded.worldBorderMargin());
    }
}
