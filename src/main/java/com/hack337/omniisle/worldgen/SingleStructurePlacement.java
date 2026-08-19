package com.hack337.omniisle.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.hack337.omniisle.OmniIsleIds;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** A single exact start chunk for a structure set. */
public final class SingleStructurePlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<SingleStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.intRange(-1_875_000, 1_875_000).fieldOf("chunk_x").forGetter(p -> p.chunkX),
        Codec.intRange(-1_875_000, 1_875_000).fieldOf("chunk_z").forGetter(p -> p.chunkZ),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(p -> p.enabled)
    ).apply(instance, SingleStructurePlacement::new));

    public static final StructurePlacementType<SingleStructurePlacement> TYPE = () -> CODEC;

    private final int chunkX;
    private final int chunkZ;
    private final boolean enabled;

    public SingleStructurePlacement(int chunkX, int chunkZ, boolean enabled) {
        super(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, 0,
            Optional.empty(), 4096, 0, RandomSpreadType.LINEAR);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.enabled = enabled;
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        return new ChunkPos(this.chunkX, this.chunkZ);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState calculator, int chunkX, int chunkZ) {
        return enabled && chunkX == this.chunkX && chunkZ == this.chunkZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return TYPE;
    }

    public static void registerType() {
        Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, OmniIsleIds.id("fixed"), TYPE);
    }
}
