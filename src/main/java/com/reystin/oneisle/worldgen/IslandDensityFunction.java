package com.reystin.oneisle.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.OneIsleIds;

public class IslandDensityFunction implements DensityFunction {
    public static final MapCodec<IslandDensityFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            IslandShapeConfig.CODEC.fieldOf("config").forGetter(c -> c.config)
        ).apply(instance, IslandDensityFunction::new)
    );

    private static volatile IslandDensityFunction activeInstance;

    private final IslandShapeConfig config;
    private final IslandTerrain terrain;

    public IslandDensityFunction(IslandShapeConfig config) {
        this.config = config;
        this.terrain = IslandTerrain.create(config);
        activeInstance = this;
    }

    public static IslandDensityFunction getActive() {
        return activeInstance;
    }

    public static void setActive(IslandDensityFunction instance) {
        activeInstance = instance;
    }

    public IslandShapeConfig shapeConfig() {
        return this.config;
    }

    public IslandTerrain terrain() {
        return this.terrain;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        activeInstance = this;
        return new Sampler(this.terrain);
    }

    @Override
    public Interval range() {
        return Interval.of(-1.0f, 1.0f);
    }

    @Override
    public int domainAxes() {
        return AXIS_X | AXIS_Z;
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public MapCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    @SuppressWarnings("unchecked")
    public static void registerCodec() {
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, OneIsleIds.id("island_continents"), (MapCodec<? extends DensityFunction>) (MapCodec<?>) CODEC);
    }

    private record Sampler(IslandTerrain terrain) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
            for (int zIdx = 0; zIdx < volume.sizeZ(); zIdx++) {
                int blockZ = volume.blockZ(zIdx);
                for (int xIdx = 0; xIdx < volume.sizeX(); xIdx++) {
                    int blockX = volume.blockX(xIdx);
                    float value = sampleValue(context, blockX, 0, blockZ);
                    int baseIdx = volume.indexUnchecked(xIdx, 0, zIdx);
                    buffer.setRange(baseIdx, volume.sizeY(), value);
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            return (float) this.terrain.continentalness(x, z);
        }
    }
}
