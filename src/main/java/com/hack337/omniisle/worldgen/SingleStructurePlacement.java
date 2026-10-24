package com.hack337.omniisle.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.hack337.omniisle.OmniIsleIds;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * A single exact start chunk for a structure set.
 *
 * <p>Must extend {@link RandomSpreadStructurePlacement}: 26.3's {@code /locate structure}
 * only walks random-spread and concentric-ring placements, silently ignoring any other
 * {@code StructurePlacement}. Spacing 1 / separation 0 makes every chunk a candidate cell,
 * so the locate scan can reach the fixed chunk while {@link #isPlacementChunk} still only
 * accepts that one exact chunk.</p>
 */
public final class SingleStructurePlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<SingleStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.intRange(-1_875_000, 1_875_000).fieldOf("chunk_x").forGetter(p -> p.chunkX),
        Codec.intRange(-1_875_000, 1_875_000).fieldOf("chunk_z").forGetter(p -> p.chunkZ),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(p -> p.enabled)
    ).apply(instance, SingleStructurePlacement::new));

    private static final int LOCATE_SPACING = 1;

    private final int chunkX;
    private final int chunkZ;
    private final boolean enabled;

    public SingleStructurePlacement(int chunkX, int chunkZ, boolean enabled) {
        super(Vec3i.ZERO, RandomSpreadStructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, 0,
            Optional.empty(), LOCATE_SPACING, 0, RandomSpreadType.LINEAR);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.enabled = enabled;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState calculator, int chunkX, int chunkZ) {
        return enabled && chunkX == this.chunkX && chunkZ == this.chunkZ;
    }

    /**
     * The locate scan trusts every chunk returned by the random-spread cell lookup and
     * does not re-check {@link #isPlacementChunk} before running the full biome survey
     * for the structure. Reject non-fixed candidates here so a 100-chunk scan stays cheap.
     */
    @Override
    public boolean applyAdditionalChunkRestrictions(int chunkX, int chunkZ, long seed) {
        return enabled && chunkX == this.chunkX && chunkZ == this.chunkZ;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<RandomSpreadStructurePlacement> codec() {
        return (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>) CODEC;
    }

    @SuppressWarnings("unchecked")
    public static void registerType() {
        Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, OmniIsleIds.id("fixed"), (MapCodec<? extends StructurePlacement>) (MapCodec<?>) CODEC);
    }
}
