package com.hack337.omniisle.worldgen;

import com.hack337.omniisle.OmniIsleIds;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
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
 */
public final class RelaxedPyramids {
    public static final StructureType<Desert> DESERT_TYPE = () -> Desert.CODEC;
    public static final StructureType<JungleTemple> JUNGLE_TYPE = () -> JungleTemple.CODEC;

    private RelaxedPyramids() {}

    public static void register() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            OmniIsleIds.id("relaxed_desert_pyramid"), DESERT_TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            OmniIsleIds.id("relaxed_jungle_temple"), JUNGLE_TYPE);
    }

    public static final class Desert extends DesertPyramidStructure {
        public static final MapCodec<Desert> CODEC = Structure.simpleCodec(Desert::new);

        public Desert(Structure.StructureSettings settings) {
            super(settings);
        }

        @Override
        public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
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
