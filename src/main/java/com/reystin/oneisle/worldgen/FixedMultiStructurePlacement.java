package com.reystin.oneisle.worldgen;

import com.reystin.oneisle.OneIsleIds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * An exact ordered list of start chunks for one structure set, used for the three
 * v3 strongholds. Like {@link SingleStructurePlacement} it must extend
 * {@link RandomSpreadStructurePlacement} so 26.3's locate machinery can see it;
 * spacing 1 makes every chunk a candidate cell and the overrides pin the accepted set.
 */
public final class FixedMultiStructurePlacement extends RandomSpreadStructurePlacement {
    public record FixedChunk(int x, int z) {
        public static final Codec<FixedChunk> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(-1_875_000, 1_875_000).fieldOf("x").forGetter(FixedChunk::x),
            Codec.intRange(-1_875_000, 1_875_000).fieldOf("z").forGetter(FixedChunk::z)
        ).apply(instance, FixedChunk::new));
    }

    public static final MapCodec<FixedMultiStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.list(FixedChunk.CODEC).fieldOf("chunks").forGetter(FixedMultiStructurePlacement::chunks),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(p -> p.enabled)
    ).apply(instance, FixedMultiStructurePlacement::new));

    private static final int LOCATE_SPACING = 1;

    private final List<FixedChunk> chunks;
    private final boolean enabled;
    private final LongSet chunkSet = new LongOpenHashSet();

    public FixedMultiStructurePlacement(List<FixedChunk> chunks, boolean enabled) {
        super(Vec3i.ZERO, RandomSpreadStructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, 0,
            Optional.empty(), LOCATE_SPACING, 0, RandomSpreadType.LINEAR);
        this.chunks = List.copyOf(chunks);
        this.enabled = enabled;
        this.chunks.forEach(chunk -> chunkSet.add(ChunkPos.pack(chunk.x(), chunk.z())));
    }

    public List<FixedChunk> chunks() {
        return chunks;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState calculator, int chunkX, int chunkZ) {
        return enabled && chunkSet.contains(ChunkPos.pack(chunkX, chunkZ));
    }

    /**
     * Locate trusts the random-spread cell lookup without re-checking
     * {@link #isPlacementChunk}; reject non-listed chunks here so the 100-chunk scan
     * stays cheap and only the real starts are biome-checked.
     */
    @Override
    public boolean applyAdditionalChunkRestrictions(int chunkX, int chunkZ, long seed) {
        return enabled && chunkSet.contains(ChunkPos.pack(chunkX, chunkZ));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<RandomSpreadStructurePlacement> codec() {
        return (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>) CODEC;
    }

    @SuppressWarnings("unchecked")
    public static void registerType() {
        Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, OneIsleIds.id("fixed_multi"),
            (MapCodec<? extends StructurePlacement>) (MapCodec<?>) CODEC);
    }
}
