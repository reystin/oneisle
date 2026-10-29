package com.hack337.omniisle.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.ClimateLayout;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.worldgen.RelaxedPyramids;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.8.4 strict v3 structure sites: province, surface ceiling and no-fallback behavior. */
public class Phase11StructureTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/phase11/after");
    private static final double OCEAN_TRANSITION = 400.0;
    private static final int SIZE = 2500;
    private static final long SEED = 2025L;

    private record Spec(Set<Integer> families, ClimateLayout.Province province,
                        double minC, double maxC, double maxSlope, double maxSurfaceY, String zone) {}

    private static final Map<IslandDraft.StructureOption, Spec> SPECS = Map.ofEntries(
        Map.entry(IslandDraft.StructureOption.DESERT_PYRAMID, new Spec(Set.of(12),
            ClimateLayout.Province.WARM, 0.00, 0.32, 0.22, 88.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.JUNGLE_TEMPLE, new Spec(Set.of(10),
            ClimateLayout.Province.WARM, -2.0, 0.36, 0.28, 96.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.SWAMP_HUT, new Spec(Set.of(8, 9),
            null, -2.0, 0.16, 0.10, 88.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.MANSION, new Spec(Set.of(7),
            null, -2.0, 0.36, 0.30, 96.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.IGLOO, new Spec(Set.of(0, 1),
            ClimateLayout.Province.COLD, -2.0, 0.30, 0.25, 92.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.OUTPOST, new Spec(Set.of(),
            null, -2.0, 0.40, 0.30, 100.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.TRAIL_RUINS, new Spec(Set.of(1),
            null, -2.0, 0.40, 0.30, 100.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.RUINED_PORTAL, new Spec(Set.of(),
            null, -2.0, 0.40, 0.30, 100.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.ABANDONED_CAMP, new Spec(Set.of(3),
            null, -2.0, 0.36, 0.28, 96.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.MINESHAFT, new Spec(Set.of(),
            null, -2.0, 2.0, 0.0, 0.0, "LAND")),
        Map.entry(IslandDraft.StructureOption.OCEAN_MONUMENT, new Spec(Set.of(),
            null, -2.0, -0.62, 0.0, 0.0, "OCEAN")),
        Map.entry(IslandDraft.StructureOption.OCEAN_RUINS, new Spec(Set.of(),
            null, -2.0, -0.30, 0.0, 0.0, "OCEAN")),
        Map.entry(IslandDraft.StructureOption.SHIPWRECK, new Spec(Set.of(),
            null, -2.0, -0.20, 0.0, 0.0, "OCEAN_OR_SHORE")),
        Map.entry(IslandDraft.StructureOption.BURIED_TREASURE, new Spec(Set.of(),
            null, -0.30, 0.02, 0.20, 0.0, "SHORE"))
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

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("11S.1 exported strict sites obey families, province, c/slope and surface ceiling")
    void strictSitesOnTwoPresets() throws IOException {
        StringBuilder csv = new StringBuilder(
            "fixture,set,enabled,chunk_x,chunk_z,continentalness,slope,top_y,family,province,zone\n");
        int enabledSites = 0;
        for (IslandDraft.Preset preset : List.of(
            IslandDraft.Preset.NATURAL, IslandDraft.Preset.MINESHIELD_4_MAP)) {
            IslandDraft draft = draft(preset);
            Path tmp = Files.createTempDirectory("omniisle-11-struct-");
            try {
                WorldPackBuilder.export(tmp, draft);
                IslandTerrain terrain = terrain(draft);
                ClimateLayout layout = terrain.climateLayout();
                double halfSpan = draft.worldSpan() * 0.5;
                boolean shoreAllowed = !draft.enabledBiomes(BiomePreview.Category.SHORE).isEmpty();
                for (Map.Entry<IslandDraft.StructureOption, String> entry : SET_NAMES.entrySet()) {
                    IslandDraft.StructureOption option = entry.getKey();
                    Spec spec = SPECS.get(option);
                    JsonObject placement = placement(tmp, entry.getValue());
                    boolean enabled = placement.get("enabled").getAsBoolean();
                    if (!enabled) {
                        csv.append(preset).append(',').append(entry.getValue())
                            .append(",false,,,,,,,,\n");
                        continue;
                    }
                    enabledSites++;
                    int x = placement.get("chunk_x").getAsInt() * 16 + 8;
                    int z = placement.get("chunk_z").getAsInt() * 16 + 8;
                    double c = terrain.continentalness(x, z);
                    double slope = slope(terrain, x, z);
                    double topY = terrain.surfaceHeight(x, z);
                    BiomePreview.Selection selection = BiomePreview.selectionV3(
                        c, x, z, draft.biomeSeed(), draft.climateScale(), draft.gradientMode(),
                        draft.gradientStrength(), draft.theme(), slope, 3, halfSpan,
                        terrain.rivers(), terrain, draft.shoreSettings(), shoreAllowed);
                    BiomePreview.Type point = draft.selectPreviewBiome(selection, x, z);
                    assertNotNull(point, entry.getValue() + " has no point biome");
                    ClimateLayout.Province province = layout.sample(x, z).province();
                    int family = selection.landFamily();
                    String message = entry.getValue() + " at " + x + "," + z + " c=" + c
                        + " slope=" + slope + " topY=" + topY + " family=" + family
                        + " province=" + province + " point=" + point;
                    assertTrue(c >= spec.minC() && c <= spec.maxC(), message);
                    if (spec.maxSlope() > 0.0) assertTrue(slope < spec.maxSlope(), message);
                    if (spec.maxSurfaceY() > 0.0) assertTrue(topY <= spec.maxSurfaceY(), message);
                    if (spec.province() != null) assertEquals(spec.province(), province, message);
                    assertTrue(zoneMatches(spec.zone(), point), message);
                    if (!spec.families().isEmpty()) assertTrue(spec.families().contains(family), message);
                    assertNarrowTag(tmp, draft, option, point, message);
                    csv.append(preset).append(',').append(entry.getValue()).append(",true,")
                        .append(placement.get("chunk_x").getAsInt()).append(',')
                        .append(placement.get("chunk_z").getAsInt()).append(',')
                        .append(String.format(Locale.ROOT, "%.4f", c)).append(',')
                        .append(String.format(Locale.ROOT, "%.4f", slope)).append(',')
                        .append(String.format(Locale.ROOT, "%.1f", topY)).append(',')
                        .append(family).append(',').append(province).append(',')
                        .append(spec.zone()).append('\n');
                }
            } finally {
                deleteTree(tmp);
            }
        }
        assertTrue(enabledSites >= 14, "too few strict sites placed: " + enabledSites);
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("structure_sites.csv"), csv.toString(),
                StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("11S.2 no-candidate families disable their sets without fallback or exception")
    void absentFamiliesDisableWithoutFallback() throws IOException {
        IslandDraft draft = draft(IslandDraft.Preset.NATURAL);
        draft.toggleBiome(BiomePreview.Type.DESERT);
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (type.isLand() && (type.family == 0 || type.family == 1)) {
                if (draft.isBiomeEnabled(type)) draft.toggleBiome(type);
            }
        }
        Path tmp = Files.createTempDirectory("omniisle-11-nofallback-");
        try {
            WorldPackBuilder.export(tmp, draft);
            for (String set : List.of("desert_pyramids", "igloos")) {
                JsonObject placement = placement(tmp, set);
                assertFalse(placement.get("enabled").getAsBoolean(),
                    set + " must stay disabled without a candidate");
                assertEquals(0, placement.get("chunk_x").getAsInt());
                assertEquals(0, placement.get("chunk_z").getAsInt());
            }
            assertNull(read(tmp, "data/minecraft/tags/worldgen/biome/has_structure/desert_pyramid.json"));
            assertNull(read(tmp, "data/minecraft/tags/worldgen/biome/has_structure/igloo.json"));
        } finally {
            deleteTree(tmp);
        }
    }

    @Test
    @DisplayName("11S.3 runtime guard accepts lowlands and rejects high, wrong-biome or wrong-sector sites")
    void runtimeGuard() {
        int topY = 70;
        assertTrue(RelaxedPyramids.allowsSurface("minecraft:desert", topY, 2.0,
            0.10, 0.05, ClimateLayout.Province.WARM,
            RelaxedPyramids.DESERT_POLICY), "valid warm lowland desert must be accepted");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:desert", topY, 2.0,
            0.10, 0.05, ClimateLayout.Province.COLD,
            RelaxedPyramids.DESERT_POLICY), "cold-sector desert must be rejected");
        assertTrue(RelaxedPyramids.allowsSurface("minecraft:desert", topY, 2.0,
            0.10, 0.05, null, RelaxedPyramids.DESERT_POLICY),
            "unknown province keeps the legacy surface check working");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:desert", 200, 2.0,
            0.10, 0.05, ClimateLayout.Province.WARM, RelaxedPyramids.DESERT_POLICY),
            "surface altitude above the policy ceiling must be rejected");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:desert", topY, 24.0,
            0.10, 0.05, ClimateLayout.Province.WARM, RelaxedPyramids.DESERT_POLICY),
            "high local relief must be rejected");
        assertFalse(RelaxedPyramids.allowsSurface("minecraft:plains", topY, 2.0,
            0.10, 0.05, ClimateLayout.Province.WARM, RelaxedPyramids.DESERT_POLICY),
            "wrong biome must be rejected");
    }

    private static boolean zoneMatches(String zone, BiomePreview.Type type) {
        return switch (zone) {
            case "LAND" -> type.isLand();
            case "OCEAN" -> type.category == BiomePreview.Category.OCEAN;
            case "SHORE" -> type.category == BiomePreview.Category.SHORE;
            case "OCEAN_OR_SHORE" -> type.category == BiomePreview.Category.OCEAN
                || type.category == BiomePreview.Category.SHORE;
            default -> false;
        };
    }

    private static void assertNarrowTag(Path dir, IslandDraft draft, IslandDraft.StructureOption option,
                                        BiomePreview.Type point, String message) throws IOException {
        String json = read(dir, "data/minecraft/tags/worldgen/biome/has_structure/"
            + tagName(option) + ".json");
        assertNotNull(json, "missing narrow tag for " + option);
        JsonArray values = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("values");
        Spec spec = SPECS.get(option);
        for (JsonElement value : values) {
            String id = value.getAsString();
            if (id.equals(point.biomeId())) continue;
            boolean accepted = false;
            for (BiomePreview.Category category : categories(spec.zone())) {
                for (BiomePreview.Type type : draft.enabledBiomes(category)) {
                    if (type.biomeId().equals(id)
                        && (spec.families().isEmpty() || spec.families().contains(type.family))) {
                        accepted = true;
                    }
                }
            }
            assertTrue(accepted, message + " tag leaked " + id);
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

    private static IslandDraft draft(IslandDraft.Preset preset) {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(preset);
        draft.setIslandSize(SIZE);
        draft.setShapeSeed(SEED);
        draft.setBiomeSeed(SEED);
        draft.applyTheme(IslandDraft.BiomeTheme.ALL);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setIsletDensity(0.25);
        draft.rebuildShape();
        draft.effectiveSpawnPx();
        return draft;
    }

    private static IslandTerrain terrain(IslandDraft state) {
        double halfSpan = state.worldSpan() * 0.5;
        IslandMask mask = new IslandMask(state.resolution(), state.resolution(),
            -halfSpan, halfSpan, -halfSpan, halfSpan, state.relief().coastWidth,
            OCEAN_TRANSITION, state.packedMask());
        int[] sp = state.effectiveSpawnPx();
        IslandShapeConfig config = new IslandShapeConfig(
            state.generationVersionNumber(), state.isletDensity(), state.riverDensity(),
            state.isWorldBorderEnabled(), state.worldBorderMargin(),
            state.hasCustomHeight()
                ? Optional.of(new IslandShapeConfig.HeightLayer(2, state.packedHeight()))
                : Optional.empty(),
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
