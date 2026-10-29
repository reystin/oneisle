package com.reystin.oneisle.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reystin.oneisle.client.BiomePreview;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.WorldPackBuilder;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.worldgen.RelaxedPyramids;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.8.2 v3 fixed structures: strict category/family/climate/slope placement, no fallback. */
public class Phase82StructureTest {
    private static final boolean WRITE = Boolean.getBoolean("oneisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/phase82");
    private static final double OCEAN_TRANSITION = 400.0;
    private static final int SIZE = 2500;
    private static final long SEED = 2025L;

    private record PolicySpec(Set<Integer> families, double minC, double maxC,
                              double maxSlope, String zone) {}

    private static final Map<IslandDraft.StructureOption, PolicySpec> SPECS = Map.ofEntries(
        Map.entry(IslandDraft.StructureOption.DESERT_PYRAMID,
            new PolicySpec(Set.of(12), 0.00, 0.32, 0.22, "LAND")),
        Map.entry(IslandDraft.StructureOption.JUNGLE_TEMPLE,
            new PolicySpec(Set.of(10), -2.0, 0.36, 0.28, "LAND")),
        Map.entry(IslandDraft.StructureOption.SWAMP_HUT,
            new PolicySpec(Set.of(8, 9), -2.0, 0.16, 0.10, "LAND")),
        Map.entry(IslandDraft.StructureOption.MANSION,
            new PolicySpec(Set.of(7), -2.0, 0.36, 0.30, "LAND")),
        Map.entry(IslandDraft.StructureOption.IGLOO,
            new PolicySpec(Set.of(0, 1), -2.0, 0.30, 0.25, "LAND")),
        Map.entry(IslandDraft.StructureOption.OUTPOST,
            new PolicySpec(Set.of(), -2.0, 0.40, 0.30, "LAND")),
        Map.entry(IslandDraft.StructureOption.TRAIL_RUINS,
            new PolicySpec(Set.of(1), -2.0, 0.40, 0.30, "LAND")),
        Map.entry(IslandDraft.StructureOption.RUINED_PORTAL,
            new PolicySpec(Set.of(), -2.0, 0.40, 0.30, "LAND")),
        Map.entry(IslandDraft.StructureOption.ABANDONED_CAMP,
            new PolicySpec(Set.of(3), -2.0, 0.36, 0.28, "LAND")),
        Map.entry(IslandDraft.StructureOption.MINESHAFT,
            new PolicySpec(Set.of(), -2.0, 2.0, 0.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.OCEAN_MONUMENT,
            new PolicySpec(Set.of(), -2.0, -0.62, 0.0, "OCEAN")),
        Map.entry(IslandDraft.StructureOption.OCEAN_RUINS,
            new PolicySpec(Set.of(), -2.0, -0.30, 0.0, "OCEAN")),
        Map.entry(IslandDraft.StructureOption.SHIPWRECK,
            new PolicySpec(Set.of(), -2.0, -0.20, 0.0, "OCEAN_OR_SHORE")),
        Map.entry(IslandDraft.StructureOption.BURIED_TREASURE,
            new PolicySpec(Set.of(), -0.30, 0.02, 0.20, "SHORE"))
    );

    private static final Map<IslandDraft.StructureOption, String> SET_NAMES = Map.ofEntries(
        Map.entry(IslandDraft.StructureOption.DESERT_PYRAMID, "desert_pyramids"),
        Map.entry(IslandDraft.StructureOption.JUNGLE_TEMPLE, "jungle_temples"),
        Map.entry(IslandDraft.StructureOption.SWAMP_HUT, "swamp_huts"),
        Map.entry(IslandDraft.StructureOption.MANSION, "woodland_mansions"),
        Map.entry(IslandDraft.StructureOption.IGLOO, "igloos"),
        Map.entry(IslandDraft.StructureOption.OUTPOST, "pillager_outposts"),
        Map.entry(IslandDraft.StructureOption.TRAIL_RUINS, "trail_ruins"),
        Map.entry(IslandDraft.StructureOption.RUINED_PORTAL, "ruined_portals"),
        Map.entry(IslandDraft.StructureOption.ABANDONED_CAMP, "abandoned_camp"),
        Map.entry(IslandDraft.StructureOption.MINESHAFT, "mineshafts"),
        Map.entry(IslandDraft.StructureOption.OCEAN_MONUMENT, "ocean_monuments"),
        Map.entry(IslandDraft.StructureOption.OCEAN_RUINS, "ocean_ruins"),
        Map.entry(IslandDraft.StructureOption.SHIPWRECK, "shipwrecks"),
        Map.entry(IslandDraft.StructureOption.BURIED_TREASURE, "buried_treasures")
    );

    private record Fixture(IslandDraft.Preset preset, IslandDraft.BiomeTheme theme, long seed) {}

    private static final List<Fixture> FIXTURES = List.of(
        new Fixture(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.DESERT, 2025L),
        new Fixture(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.DESERT, 1337L),
        new Fixture(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.TROPICAL, 2025L),
        new Fixture(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.TROPICAL, 1337L),
        new Fixture(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.ALL, 2025L),
        new Fixture(IslandDraft.Preset.NATURAL, IslandDraft.BiomeTheme.ALL, 1337L)
    );

    @Test
    void strictPoliciesPlaceOnlyValidSites() throws IOException {
        StringBuilder csv = new StringBuilder(
            "fixture,set,enabled,chunk_x,chunk_z,continentalness,slope,family,biome,zone\n");
        Map<IslandDraft.StructureOption, Integer> placed = new LinkedHashMap<>();
        for (Fixture fixture : FIXTURES) {
            IslandDraft draft = draft(fixture);
            Path tmp = Files.createTempDirectory("oneisle-82-struct-");
            try {
                WorldPackBuilder.export(tmp, draft);
                IslandTerrain terrain = terrain(draft);
                double halfSpan = draft.worldSpan() * 0.5;
                boolean shoreAllowed = !draft.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();
                for (Map.Entry<IslandDraft.StructureOption, String> entry : SET_NAMES.entrySet()) {
                    IslandDraft.StructureOption option = entry.getKey();
                    PolicySpec spec = SPECS.get(option);
                    JsonObject placement = placement(tmp, entry.getValue());
                    assertEquals("oneisle:fixed", placement.get("type").getAsString(),
                        entry.getValue() + " must be a fixed v3 placement");
                    boolean enabled = placement.get("enabled").getAsBoolean();
                    if (!enabled) {
                        csv.append(fixture.preset()).append('_').append(fixture.theme()).append(',')
                            .append(entry.getValue()).append(",false,,,0,0,-1,, \n");
                        continue;
                    }
                    int cx = placement.get("chunk_x").getAsInt();
                    int cz = placement.get("chunk_z").getAsInt();
                    int x = cx * 16 + 8;
                    int z = cz * 16 + 8;
                    double c = terrain.continentalness(x, z);
                    double slope = slope(terrain, x, z);
                    BiomePreview.Selection selection = BiomePreview.selectionV3(
                        c, x, z, draft.biomeSeed(), draft.climateScale(), draft.gradientMode(),
                        draft.gradientStrength(), draft.theme(), slope, 3, halfSpan,
                        terrain.rivers(), terrain, draft.shoreSettings(), shoreAllowed);
                    BiomePreview.Type pointBiome = draft.selectPreviewBiome(selection, x, z);
                    assertNotNull(pointBiome, "no resolved point biome");
                    int family = selection.landFamily();
                    boolean land = pointBiome.isLand();
                    boolean ocean = pointBiome.category == BiomePreview.Category.OCEAN;
                    boolean shore = pointBiome.category == BiomePreview.Category.SHORE;
                    String message = entry.getValue() + " at " + cx + "," + cz + " c=" + c
                        + " slope=" + slope + " family=" + family + " type=" + pointBiome;
                    assertTrue(c >= spec.minC() && c <= spec.maxC(), message);
                    if (spec.maxSlope() > 0.0) {
                        assertTrue(slope < spec.maxSlope(), message);
                    }
                    switch (spec.zone()) {
                        case "LAND" -> assertTrue(land, message);
                        case "OCEAN" -> assertTrue(ocean, message);
                        case "SHORE" -> assertTrue(shore, message);
                        case "OCEAN_OR_SHORE" -> assertTrue(ocean || shore, message);
                        default -> throw new IllegalStateException(spec.zone());
                    }
                    if (!spec.families().isEmpty()) {
                        assertTrue(spec.families().contains(family), message);
                    }
                    assertNarrowTag(tmp, draft, option, pointBiome, fixture);
                    placed.merge(option, 1, Integer::sum);
                    csv.append(fixture.preset()).append('_').append(fixture.theme()).append(',')
                        .append(entry.getValue()).append(",true,").append(cx).append(',').append(cz)
                        .append(',').append(String.format(Locale.ROOT, "%.4f", c)).append(',')
                        .append(String.format(Locale.ROOT, "%.4f", slope)).append(',')
                        .append(family).append(',').append(pointBiome).append(',')
                        .append(land ? "LAND" : shore ? "SHORE" : ocean ? "OCEAN" : "?").append('\n');
                }
            } finally {
                deleteTree(tmp);
            }
        }
        assertTrue(placed.getOrDefault(IslandDraft.StructureOption.DESERT_PYRAMID, 0) > 0,
            "no fixture placed a desert pyramid: " + placed);
        assertTrue(placed.getOrDefault(IslandDraft.StructureOption.JUNGLE_TEMPLE, 0) > 0,
            "no fixture placed a jungle temple: " + placed);
        assertTrue(placed.getOrDefault(IslandDraft.StructureOption.SWAMP_HUT, 0) > 0,
            "no fixture placed a swamp hut: " + placed);
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("structure_placement_after.csv"), csv.toString(),
                StandardCharsets.UTF_8);
        }
    }

    @Test
    void absentFamilyDisablesSetWithoutFallback() throws IOException {
        IslandDraft draft = draft(new Fixture(IslandDraft.Preset.ROUND, IslandDraft.BiomeTheme.ALL, SEED));
        draft.toggleBiome(BiomePreview.Type.DESERT);
        draft.toggleBiome(BiomePreview.Type.DARK_FOREST);
        draft.toggleBiome(BiomePreview.Type.SWAMP);
        draft.toggleBiome(BiomePreview.Type.MANGROVE_SWAMP);
        assertFalse(draft.isBiomeEnabled(BiomePreview.Type.DESERT));
        Path tmp = Files.createTempDirectory("oneisle-82-nofallback-");
        try {
            WorldPackBuilder.export(tmp, draft);
            for (String set : List.of("desert_pyramids", "woodland_mansions", "swamp_huts")) {
                JsonObject placement = placement(tmp, set);
                assertFalse(placement.get("enabled").getAsBoolean(),
                    set + " must stay disabled when its family is absent");
                assertEquals(0, placement.get("chunk_x").getAsInt());
                assertEquals(0, placement.get("chunk_z").getAsInt());
            }
            assertNull(read(tmp, "data/minecraft/tags/worldgen/biome/has_structure/desert_pyramid.json"),
                "disabled desert pyramid must not write a placement tag");
            assertNull(read(tmp, "data/minecraft/tags/worldgen/biome/has_structure/woodland_mansion.json"),
                "disabled mansion must not write a placement tag");
            assertNull(read(tmp, "data/minecraft/tags/worldgen/biome/has_structure/swamp_hut.json"),
                "disabled swamp hut must not write a placement tag");
        } finally {
            deleteTree(tmp);
        }
    }

    @Test
    void relaxedPyramidsValidateSurface() {
        assertTrue(RelaxedPyramids.allowsSurface("minecraft:desert", 70, 3.0, 0.10, 0.08,
            RelaxedPyramids.DESERT_POLICY), "lowland desert must remain valid");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:desert", 130, 3.0, 0.10, 0.08,
            RelaxedPyramids.DESERT_POLICY), "high mountain desert must be rejected");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:plains", 70, 3.0, 0.10, 0.08,
            RelaxedPyramids.DESERT_POLICY), "wrong biome must be rejected");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:desert", 70, 12.0, 0.10, 0.08,
            RelaxedPyramids.DESERT_POLICY), "rough terrain must be rejected");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:desert", 70, 3.0, 0.50, 0.08,
            RelaxedPyramids.DESERT_POLICY), "high continentalness must be rejected");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:desert", 70, 3.0, 0.10, 0.30,
            RelaxedPyramids.DESERT_POLICY), "steep desert must be rejected");

        assertTrue(RelaxedPyramids.allowsSurface("minecraft:jungle", 75, 4.0, 0.20, 0.10,
            RelaxedPyramids.JUNGLE_POLICY), "lowland jungle must remain valid");
        assertTrue(RelaxedPyramids.allowsSurface("minecraft:bamboo_jungle", 75, 4.0, 0.20, 0.10,
            RelaxedPyramids.JUNGLE_POLICY), "bamboo jungle is a valid pyramid biome");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:jungle", 75, 4.0, 0.45, 0.10,
            RelaxedPyramids.JUNGLE_POLICY), "high jungle must be rejected");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:forest", 75, 4.0, 0.20, 0.10,
            RelaxedPyramids.JUNGLE_POLICY), "non-jungle must be rejected");
    }

    private static void assertNarrowTag(Path dir, IslandDraft draft,
                                        IslandDraft.StructureOption option,
                                        BiomePreview.Type pointBiome, Fixture fixture) throws IOException {
        String tagPath = "data/minecraft/tags/worldgen/biome/has_structure/"
            + tagName(option) + ".json";
        String json = read(dir, tagPath);
        assertNotNull(json, "missing narrow tag for " + option);
        JsonArray values = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonArray("values");
        Set<String> expected = new LinkedHashSet<>();
        PolicySpec spec = SPECS.get(option);
        for (BiomePreview.Category category : categories(spec.zone())) {
            for (BiomePreview.Type type : draft.enabledBiomes(category)) {
                if (spec.families().isEmpty() || spec.families().contains(type.family)) {
                    expected.add(type.biomeId());
                }
            }
        }
        expected.add(pointBiome.biomeId());
        Set<String> actual = new LinkedHashSet<>();
        for (JsonElement value : values) actual.add(value.getAsString());
        assertEquals(expected, actual, option + " tag must contain only accepted families "
            + "plus the point biome [fixture=" + fixture + " point=" + pointBiome
            + " enabled=" + draft.enabledBiomes() + "]");
        for (String id : actual) {
            boolean accepted = pointBiome.biomeId().equals(id);
            for (BiomePreview.Category category : categories(spec.zone())) {
                for (BiomePreview.Type type : draft.enabledBiomes(category)) {
                    if (type.biomeId().equals(id)
                        && (spec.families().isEmpty() || spec.families().contains(type.family))) {
                        accepted = true;
                    }
                }
            }
            assertTrue(accepted, option + " tag leaked disallowed biome " + id);
        }
    }

    private static List<BiomePreview.Category> categories(String zone) {
        return switch (zone) {
            case "LAND" -> List.of(BiomePreview.Category.LAND);
            case "OCEAN" -> List.of(BiomePreview.Category.OCEAN);
            case "SHORE" -> List.of(BiomePreview.Category.SHORE);
            case "OCEAN_OR_SHORE" ->
                List.of(BiomePreview.Category.OCEAN, BiomePreview.Category.SHORE);
            default -> throw new IllegalStateException(zone);
        };
    }

    private static String tagName(IslandDraft.StructureOption option) {
        return switch (option) {
            case DESERT_PYRAMID -> "desert_pyramid";
            case JUNGLE_TEMPLE -> "jungle_temple";
            case SWAMP_HUT -> "swamp_hut";
            case MANSION -> "woodland_mansion";
            case IGLOO -> "igloo";
            case OUTPOST -> "pillager_outpost";
            case TRAIL_RUINS -> "trail_ruins";
            case RUINED_PORTAL -> "ruined_portal_standard";
            case ABANDONED_CAMP -> "abandoned_camp_forest";
            case MINESHAFT -> "mineshaft";
            case OCEAN_MONUMENT -> "ocean_monument";
            case OCEAN_RUINS -> "ocean_ruin_cold";
            case SHIPWRECK -> "shipwreck";
            case BURIED_TREASURE -> "buried_treasure";
            default -> throw new IllegalStateException(option.name());
        };
    }

    private static IslandDraft draft(Fixture fixture) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(fixture.preset());
        draft.setIslandSize(SIZE);
        draft.setShapeSeed(fixture.seed());
        draft.setBiomeSeed(fixture.seed());
        draft.applyTheme(fixture.theme());
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(0.25);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    /** Mirrors the exact terrain config used by {@link WorldPackBuilder#export}. */
    private static IslandTerrain terrain(IslandDraft state) {
        double halfSpan = state.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(state.resolution(), state.resolution(),
            -halfSpan, halfSpan, -halfSpan, halfSpan, state.relief().coastWidth,
            OCEAN_TRANSITION, state.packedMask());
        int[] sp = state.effectiveSpawnPx();
        IslandShapeConfig config = new IslandShapeConfig(
            state.generationVersionNumber(), state.isletDensity(), state.riverDensity(),
            state.isWorldBorderEnabled(), state.worldBorderMargin(),
            Optional.empty(),
            Optional.of(new IslandShapeConfig.SpawnPos(
                (int) Math.round(state.worldX(sp[0])), (int) Math.round(state.worldZ(sp[1])))),
            List.of(), Optional.of(mask), state.biomeSeed(),
            Optional.of(state.shoreSettings()));
        return IslandTerrain.create(config);
    }

    private static double slope(IslandTerrain terrain, double x, double z) {
        double e = terrain.continentalness(x + 8, z);
        double w = terrain.continentalness(x - 8, z);
        double s = terrain.continentalness(x, z + 8);
        double n = terrain.continentalness(x, z - 8);
        return Math.hypot((e - w) / 16.0, (s - n) / 16.0) * 64.0;
    }

    private static JsonObject placement(Path dir, String setName) throws IOException {
        String json = read(dir, "data/minecraft/worldgen/structure_set/" + setName + ".json");
        assertNotNull(json, "missing structure set " + setName);
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("placement");
    }

    private static String read(Path dir, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(dir.resolve(WorldPackBuilder.FILE_NAME).toFile())) {
            var entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (var in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static void deleteTree(Path tmp) throws IOException {
        try (var walk = Files.walk(tmp)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
