package com.hack337.omniisle.worldgen;

import com.hack337.omniisle.OmniIsleIds;
import com.hack337.omniisle.continent.ClimateLayout;
import com.hack337.omniisle.continent.IslandTerrain;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.structures.JungleTemplePiece;
import net.minecraft.world.level.levelgen.structure.structures.JungleTempleStructure;

/**
 * v3 variants of the two vanilla single-piece pyramids that use the same pieces and
 * biome tags but skip {@code SinglePieceStructure}'s hard {@code lowestY < seaLevel}
 * rejection. OmniIsle's lowland islands routinely put the surface within a few blocks
 * of sea level, which made the fixed pyramids vanish whenever a footprint corner dipped
 * into a vanilla noise valley.
 *
 * <p>0.8.2 restores the removed checks: the chunk-centre biome must match the structure's
 * intended biome set, and the sampled surface must stay below the elevation, relief and
 * slope limits. A rejected site returns {@link Optional#empty()}; nothing is placed.
 */
public final class RelaxedPyramids {
    public static final StructureType<Desert> DESERT_TYPE = () -> Desert.CODEC;
    public static final StructureType<JungleTemple> JUNGLE_TYPE = () -> JungleTemple.CODEC;

    public record Policy(Set<String> biomes, ClimateLayout.Province province, double maxY,
                         double maxRelief, double minC, double maxC, double maxSlope) {}

    public static final Policy DESERT_POLICY = new Policy(
        Set.of("minecraft:desert"), ClimateLayout.Province.WARM, 88.0, 8.0, 0.00, 0.32, 0.22);
    public static final Policy JUNGLE_POLICY = new Policy(
        Set.of("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"),
        ClimateLayout.Province.WARM, 96.0, 8.0, -2.0, 0.36, 0.28);

    private RelaxedPyramids() {}

    public static void register() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            OmniIsleIds.id("relaxed_desert_pyramid"), DESERT_TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            OmniIsleIds.id("relaxed_jungle_temple"), JUNGLE_TYPE);
    }

    /** Testable site predicate: biome, ceiling, local relief, continentalness and slope. */
    public static boolean allowsSurface(String biomePath, int topY, double relief,
                                        double continentalness, double slope, Policy policy) {
        return allowsSurface(biomePath, topY, relief, continentalness, slope, null, policy);
    }

    /**
     * Full v3 predicate. A null province means "unknown" (legacy caller); the export
     * already gates every fixed site by province, so only a known mismatching province
     * rejects a site here.
     */
    public static boolean allowsSurface(String biomePath, int topY, double relief,
                                        double continentalness, double slope,
                                        ClimateLayout.Province province, Policy policy) {
        if (policy == null) return true;
        if (biomePath == null || !policy.biomes().contains(biomePath)) return false;
        if (topY > policy.maxY()) return false;
        if (relief > policy.maxRelief()) return false;
        if (continentalness < policy.minC() || continentalness > policy.maxC()) return false;
        if (policy.province() != null && province != null && province != policy.province()) return false;
        return !(policy.maxSlope() > 0.0 && slope >= policy.maxSlope());
    }

    private static boolean siteAllows(Structure.GenerationContext context, Policy policy) {
        ChunkPos pos = context.chunkPos();
        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();
        Heightmap.Types heightmap = Heightmap.Types.WORLD_SURFACE_WG;
        int topY = context.chunkGenerator().getBaseHeight(
            x, z, heightmap, context.heightAccessor(), context.randomState());

        BiomeResolver resolver = context.biomeSource().createResolver(context.climateSampler());
        Holder<Biome> biome = resolver.getNoiseBiome(
            QuartPos.fromBlock(x), QuartPos.fromBlock(topY), QuartPos.fromBlock(z));
        if (!context.validBiome().test(biome)) return false;
        String path = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("");

        int lowest = topY;
        int highest = topY;
        int[][] offsets = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}};
        for (int[] offset : offsets) {
            int height = context.chunkGenerator().getBaseHeight(
                x + offset[0], z + offset[1], heightmap, context.heightAccessor(), context.randomState());
            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
        }

        double continentalness = 0.0;
        double slope = 0.0;
        ClimateLayout.Province province = null;
        IslandDensityFunction active = IslandDensityFunction.getActive();
        if (active != null && active.terrain() != null) {
            IslandTerrain terrain = active.terrain();
            continentalness = terrain.continentalness(x, z);
            double cE = terrain.continentalness(x + 8, z);
            double cW = terrain.continentalness(x - 8, z);
            double cS = terrain.continentalness(x, z + 8);
            double cN = terrain.continentalness(x, z - 8);
            slope = Math.hypot((cE - cW) / 16.0, (cS - cN) / 16.0) * 64.0;
            ClimateLayout layout = terrain.climateLayout();
            if (layout != null) province = layout.sample(x, z).province();
        }
        return allowsSurface(path, topY, highest - lowest, continentalness, slope, province, policy);
    }

    public static final class Desert extends DesertPyramidStructure {
        public static final MapCodec<Desert> CODEC = Structure.simpleCodec(Desert::new);

        public Desert(Structure.StructureSettings settings) {
            super(settings);
        }

        @Override
        public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
            if (!siteAllows(context, DESERT_POLICY)) return Optional.empty();
            return onTopOfChunkCenterWithoutBiomeCheck(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
                ChunkPos pos = context.chunkPos();
                builder.addPiece(new DesertPyramidPiece(context.random(), pos.getMinBlockX(), pos.getMinBlockZ()));
            });
        }

        @Override
        public StructureType<?> type() {
            return DESERT_TYPE;
        }
    }

    public static final class JungleTemple extends JungleTempleStructure {
        public static final MapCodec<JungleTemple> CODEC = Structure.simpleCodec(JungleTemple::new);

        public JungleTemple(Structure.StructureSettings settings) {
            super(settings);
        }

        @Override
        public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
            if (!siteAllows(context, JUNGLE_POLICY)) return Optional.empty();
            return onTopOfChunkCenterWithoutBiomeCheck(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
                ChunkPos pos = context.chunkPos();
                builder.addPiece(new JungleTemplePiece(context.random(), pos.getMinBlockX(), pos.getMinBlockZ()));
            });
        }

        @Override
        public StructureType<?> type() {
            return JUNGLE_TYPE;
        }
    }
}
