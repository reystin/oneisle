package com.hack337.omniisle.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biomes;

/** Adds visible fish populations to OmniIsle river biomes. */
public final class RiverLife {
    private RiverLife() {}

    public static void installSpawns() {
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(Biomes.RIVER),
            MobCategory.WATER_AMBIENT, entity("salmon"), 24, 2, 5
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(Biomes.RIVER),
            MobCategory.WATER_AMBIENT, entity("cod"), 10, 2, 4
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(Biomes.FROZEN_RIVER),
            MobCategory.WATER_AMBIENT, entity("salmon"), 18, 1, 4
        );
    }

    private static net.minecraft.world.entity.EntityType<?> entity(String path) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(path));
    }
}
