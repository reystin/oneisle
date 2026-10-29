package com.reystin.oneisle.test;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.worldgen.OneBiomeSource;
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

/** Shared deterministic worldgen fixtures for tests. */
final class TestWorldgen {
    static final double OCEAN_TRANSITION = 400.0;

    private TestWorldgen() {}

    static IslandMask mask(IslandDraft draft) {
        double halfSpan = draft.worldSpan() * 0.5;
        return new IslandMask(IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
            -halfSpan, halfSpan, -halfSpan, halfSpan, draft.relief().coastWidth,
            OCEAN_TRANSITION, draft.packedMask());
    }

    static IslandShapeConfig config(IslandDraft draft, int version, double islet) {
        return new IslandShapeConfig(version, islet, draft.riverDensity(),
            draft.isWorldBorderEnabled(), draft.worldBorderMargin(),
            Optional.empty(), Optional.empty(), Collections.emptyList(),
            Optional.of(mask(draft)), draft.biomeSeed());
    }

    static Climate.Sampler sampler(IslandTerrain terrain) {
        DensitySampler cSampler = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                return (float) terrain.continentalness(x, z);
            }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };
        DensitySampler zero = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) { return 0.0f; }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };
        DensitySampler nonRiver = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) { return 0.5f; }
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {}
        };
        return new Climate.Sampler(
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(cSampler, null),
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(zero, null),
            new DensitySampler.Bound(nonRiver, null)
        );
    }

    static Holder<Biome> holder(String path) {
        return Holder.Reference.createStandAlone(null,
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path)));
    }

    static OneBiomeSource biomeSource(int climateScale, long seed, int version,
                                       String gradient, double strength, String theme) {
        return biomeSource(climateScale, seed, version, gradient, strength, theme, null, false);
    }

    static OneBiomeSource biomeSource(int climateScale, long seed, int version,
                                       String gradient, double strength, String theme,
                                       com.reystin.oneisle.continent.ShoreSettings shore,
                                       boolean enableShore) {
        List<Holder<Biome>> shoreList = List.of(holder("beach"), holder("stony_shore"), holder("snowy_beach"));
        return new OneBiomeSource(
            List.of(holder("snowy_plains"), holder("taiga"), holder("birch_forest"), holder("forest"),
                holder("plains"), holder("meadow"), holder("cherry_grove"), holder("dark_forest"),
                holder("swamp"), holder("mangrove_swamp"), holder("jungle"), holder("savanna"),
                holder("desert"), holder("badlands"), holder("stony_peaks"), holder("jagged_peaks"),
                holder("mushroom_fields"), holder("pale_garden")),
            List.of(),
            shoreList,
            enableShore ? shoreList : List.of(),
            List.of(holder("ocean"), holder("deep_ocean"), holder("warm_ocean"), holder("lukewarm_ocean"),
                holder("deep_lukewarm_ocean"), holder("cold_ocean"), holder("deep_cold_ocean"),
                holder("frozen_ocean"), holder("deep_frozen_ocean")),
            List.of(),
            List.of(holder("river"), holder("frozen_river")),
            List.of(),
            List.of(holder("lush_caves"), holder("dripstone_caves"), holder("deep_dark"), holder("sulfur_caves")),
            List.of(), climateScale, seed, version, gradient, strength, theme,
            java.util.Optional.ofNullable(shore));
    }
}
