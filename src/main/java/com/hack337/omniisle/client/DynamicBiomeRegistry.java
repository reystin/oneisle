package com.hack337.omniisle.client;

import com.hack337.omniisle.continent.IslandClimate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * Registry-driven biome provider that discovers available biomes and their categories,
 * climate families, colors, and structure associations from the world creation context,
 * falling back to the 56 hardcoded biomes when the registry is unavailable.
 */
public final class DynamicBiomeRegistry {
    public record BiomeEntry(
        Identifier id,
        String name,
        BiomePreview.Category category,
        int familyIndex,
        int color,
        float temperature,
        List<String> structures
    ) {
        public String translationKey() {
            return "biome." + id.getNamespace() + "." + id.getPath();
        }
    }

    private static volatile List<BiomeEntry> activeBiomes = null;

    private DynamicBiomeRegistry() {}

    public static List<BiomeEntry> getAvailableBiomes() {
        if (activeBiomes != null && !activeBiomes.isEmpty()) {
            return activeBiomes;
        }
        return defaultBiomes();
    }

    public static void updateFromRegistry(Registry<Biome> registry) {
        List<BiomeEntry> list = buildEntries(registry);
        if (!list.isEmpty()) {
            activeBiomes = Collections.unmodifiableList(list);
        }
    }

    static List<BiomeEntry> buildEntries(Registry<Biome> registry) {
        if (registry == null) return List.of();
        List<BiomeEntry> list = new ArrayList<>();
        for (Holder.Reference<Biome> holder : registry.listElements().toList()) {
            Identifier id = holder.key().identifier();
            Biome biome = holder.value();
            BiomePreview.Category category;
            int familyIndex = -1;

            if (holder.is(BiomeTags.IS_DEEP_OCEAN) || holder.is(BiomeTags.IS_OCEAN)) {
                category = BiomePreview.Category.OCEAN;
            } else if (holder.is(BiomeTags.IS_RIVER)) {
                category = BiomePreview.Category.RIVER;
            } else if (holder.is(BiomeTags.IS_BEACH) || id.getPath().contains("shore") || id.getPath().contains("beach")) {
                category = BiomePreview.Category.SHORE;
            } else if (id.getPath().contains("caves") || id.getPath().equals("deep_dark")) {
                category = BiomePreview.Category.CAVE;
            } else {
                category = BiomePreview.Category.LAND;
                float temp = biome.getBaseTemperature();
                if (holder.is(BiomeTags.IS_MOUNTAIN)) {
                    familyIndex = temp < 0.35f ? 15 : 14;
                } else if (holder.is(BiomeTags.IS_BADLANDS)) {
                    familyIndex = 13;
                } else if (holder.is(BiomeTags.IS_JUNGLE)) {
                    familyIndex = 10;
                } else if (holder.is(BiomeTags.IS_SAVANNA)) {
                    familyIndex = 11;
                } else if (holder.is(BiomeTags.IS_TAIGA)) {
                    familyIndex = 1;
                } else {
                    familyIndex = IslandClimate.familyFor(temp, 0.5);
                }
            }

            int color = 0xFF000000 | (biome.getFoliageColor() != 0 ? biome.getFoliageColor() : 0x8DB84D);
            if (category == BiomePreview.Category.OCEAN) color = 0xFF1565C0;
            else if (category == BiomePreview.Category.RIVER) color = 0xFF2879C7;
            else if (category == BiomePreview.Category.SHORE) color = 0xFFF0E6A0;

            List<String> structures = new ArrayList<>();
            if (holder.is(BiomeTags.HAS_VILLAGE_PLAINS) || holder.is(BiomeTags.HAS_VILLAGE_DESERT)
                || holder.is(BiomeTags.HAS_VILLAGE_SAVANNA) || holder.is(BiomeTags.HAS_VILLAGE_SNOWY)
                || holder.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
                structures.add("village");
            }
            if (holder.is(BiomeTags.HAS_STRONGHOLD)) structures.add("stronghold");
            if (holder.is(BiomeTags.HAS_ANCIENT_CITY)) structures.add("ancient_city");
            if (holder.is(BiomeTags.HAS_OCEAN_MONUMENT)) structures.add("ocean_monument");
            if (holder.is(BiomeTags.HAS_TRIAL_CHAMBERS)) structures.add("trial_chambers");
            if (holder.is(BiomeTags.HAS_WOODLAND_MANSION)) structures.add("mansion");

            list.add(new BiomeEntry(id, id.getPath(), category, familyIndex, color, biome.getBaseTemperature(), structures));
        }
        return list;
    }

    static void resetCache() {
        activeBiomes = null;
    }

    public static List<BiomeEntry> defaultBiomes() {
        List<BiomeEntry> list = new ArrayList<>();
        for (BiomePreview.Type t : BiomePreview.Type.values()) {
            list.add(new BiomeEntry(
                Identifier.withDefaultNamespace(t.id),
                t.id,
                t.category,
                t.family,
                t.color,
                0.5f,
                List.of()
            ));
        }
        return list;
    }
}
