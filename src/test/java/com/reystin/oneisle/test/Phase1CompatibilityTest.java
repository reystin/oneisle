package com.reystin.oneisle.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.continent.LegacyTerrainV1;
import com.reystin.oneisle.continent.ShoreSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class Phase1CompatibilityTest {

    @Test
    void testLegacy050JsonParsesWithDefaults() {
        // Sample legacy 0.5.0 JSON: no generation_version, mask has no version and no resolution
        IslandDraft draft = new IslandDraft();
        String packedMask = draft.packedMask();

        String legacyJson = """
        {
            "entries": [],
            "mask": {
                "width": 192,
                "height": 192,
                "min_x": -2720.0,
                "max_x": 2720.0,
                "min_z": -2720.0,
                "max_z": 2720.0,
                "coast_width": 192.0,
                "ocean_transition_width": 400.0,
                "data": "%s"
            },
            "biome_seed": 123456789
        }
        """.formatted(packedMask);

        JsonElement element = JsonParser.parseString(legacyJson);
        var result = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE, element);
        assertTrue(result.result().isPresent(), "Legacy 0.5.0 JSON must parse successfully");

        IslandShapeConfig config = result.result().get();
        assertEquals(1, config.generationVersion, "Absent generation_version must default to 1 (legacy)");
        assertNotNull(config.mask, "Mask must be present");
        assertEquals(1, config.mask.version, "Absent mask.version must default to 1");
        assertEquals(192, config.mask.resolution, "Absent mask.resolution must default to 192");
        assertEquals(123456789L, config.biomeSeed);
    }

    @Test
    void testModernJsonCodecRoundTrip() {
        IslandDraft draft = new IslandDraft();
        draft.setIslandSize(4000);
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(1, 192, 192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());

        IslandShapeConfig original = new IslandShapeConfig(2, java.util.List.of(), Optional.of(mask), 987654321L);

        var encoded = IslandShapeConfig.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "Encoding must succeed");

        JsonElement json = encoded.result().get();
        assertTrue(json.isJsonObject());
        JsonObject obj = json.getAsJsonObject();
        assertEquals(2, obj.get("generation_version").getAsInt());

        var decoded = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(decoded.result().isPresent(), "Decoding must succeed");

        IslandShapeConfig roundTripped = decoded.result().get();
        assertEquals(2, roundTripped.generationVersion);
        assertEquals(987654321L, roundTripped.biomeSeed);
        assertNotNull(roundTripped.mask);
        assertEquals(1, roundTripped.mask.version);
        assertEquals(192, roundTripped.mask.resolution);
        assertEquals(192, roundTripped.mask.width);
        assertEquals(192, roundTripped.mask.height);
        assertEquals(-halfSpan, roundTripped.mask.minX, 1e-6);
        assertEquals(halfSpan, roundTripped.mask.maxX, 1e-6);
    }

    @Test
    void testUnknownFutureGenerationVersionHandledGracefully() {
        IslandShapeConfig.unknownGenerationVersionDetected = false;

        String futureJson = """
        {
            "generation_version": 99,
            "entries": [],
            "biome_seed": 42
        }
        """;

        JsonElement element = JsonParser.parseString(futureJson);
        var result = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE, element);
        assertTrue(result.result().isPresent(), "Future version must parse without throwing exception");

        IslandShapeConfig config = result.result().get();
        assertEquals(IslandShapeConfig.MAX_SUPPORTED_GENERATION_VERSION, config.generationVersion,
            "Future version must be capped to MAX_SUPPORTED_GENERATION_VERSION");
        assertTrue(IslandShapeConfig.unknownGenerationVersionDetected,
            "Warning flag must be set for world join notification");
        assertEquals(99, IslandShapeConfig.lastUnknownGenerationVersion);
    }

    @Test
    void testV3ShoreSettingsCodecRoundTrip() {
        IslandShapeConfig original = new IslandShapeConfig(3, 0.25, 0.35, false, 1000,
            Optional.empty(), Optional.empty(), List.of(), Optional.empty(), 42L,
            Optional.of(new ShoreSettings(0.55, 0.8, "natural", false)));

        var encoded = IslandShapeConfig.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "V3 encoding must succeed");
        JsonObject obj = encoded.result().get().getAsJsonObject();
        assertEquals(3, obj.get("generation_version").getAsInt());
        assertTrue(obj.getAsJsonObject("shore").get("density").getAsDouble() > 0.54);

        var decoded = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE, obj);
        assertTrue(decoded.result().isPresent(), "V3 decoding must succeed");
        IslandShapeConfig roundTripped = decoded.result().get();
        assertNotNull(roundTripped.shore, "V3 must retain explicit shore settings");
        assertEquals(0.55, roundTripped.shore.density(), 1e-9);
        assertEquals(0.8, roundTripped.shore.variety(), 1e-9);
        assertFalse(roundTripped.shore.rocky());
    }

    @Test
    void testV2WithoutShoreKeepsLegacyRibbon() {
        var result = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"generation_version\":2,\"entries\":[],\"biome_seed\":42}"));
        assertTrue(result.result().isPresent());
        assertNull(result.result().get().shore, "Absent shore on v2 must stay null (legacy ribbon)");
    }

    @Test
    void testV3WithoutShoreDefaultsToNaturalBeaches() {
        var result = IslandShapeConfig.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"generation_version\":3,\"entries\":[],\"biome_seed\":42}"));
        assertTrue(result.result().isPresent());
        assertEquals(ShoreSettings.DEFAULT, result.result().get().shore,
            "Absent shore on v3 must default to the natural beach field");
    }

    @Test
    void testCorruptedMaskDataFallsBackGracefully() {
        // I5 Invariant: Truncated or invalid mask data must not crash the game or throw exceptions
        assertDoesNotThrow(() -> {
            IslandMask mask = new IslandMask(192, 192, -2000.0, 2000.0, -2000.0, 2000.0, 192.0, 400.0, "invalid_base64!!!");
            double sample = mask.sampleTerrainValue(0, 0);
            assertTrue(sample > -1.0 && sample <= 0.70, "Fallback mask must produce valid continentalness");
        });

        assertDoesNotThrow(() -> {
            // Truncated data: only 4 bytes instead of 4608
            IslandMask mask = new IslandMask(192, 192, -2000.0, 2000.0, -2000.0, 2000.0, 192.0, 400.0, "AAAA");
            double sample = mask.sampleTerrainValue(0, 0);
            assertTrue(sample > -1.0 && sample <= 0.70);
        });

        assertDoesNotThrow(() -> {
            // Empty data
            IslandMask mask = new IslandMask(192, 192, -2000.0, 2000.0, -2000.0, 2000.0, 192.0, 400.0, "");
            double sample = mask.sampleTerrainValue(0, 0);
            assertTrue(sample > -1.0 && sample <= 0.70);
        });
    }

    @Test
    void testLegacyTerrainV1FactoryAndSignedDistance() {
        IslandDraft draft = new IslandDraft();
        draft.setIslandSize(4000);
        double halfSpan = draft.worldSpan() * 0.5;

        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            192.0, 400.0, draft.packedMask());
        IslandShapeConfig cfg = new IslandShapeConfig(1, java.util.List.of(), Optional.of(mask), 0L);

        IslandTerrain terrain = IslandTerrain.create(cfg);
        assertInstanceOf(LegacyTerrainV1.class, terrain, "Version 1 must create LegacyTerrainV1");

        assertEquals(0.0, terrain.erosionBias(0, 0), "V1 erosion bias must be 0.0");

        // Verify terrain continentalness matches mask.sampleTerrainValue bit-for-bit
        double cDirect = mask.sampleTerrainValue(100.0, -250.0);
        double cTerrain = terrain.continentalness(100.0, -250.0);
        assertEquals(cDirect, cTerrain, 1e-15, "LegacyTerrainV1 must match IslandMask.sampleTerrainValue exactly");

        // Verify signed distance
        double dist = terrain.signedDistance(100.0, -250.0);
        double expectedDist = mask.sampleSignedDistance(100.0, -250.0);
        assertEquals(expectedDist, dist, 1e-15, "Signed distance must match mask signed distance");
    }
}
