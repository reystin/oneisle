package com.hack337.omniisle.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import com.hack337.omniisle.continent.LandmassSampler;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.OmniIsleIds;

public class IslandDensityFunction implements DensityFunction.SimpleFunction {
    public static final MapCodec<IslandDensityFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            IslandShapeConfig.CODEC.fieldOf("config").forGetter(c -> c.config)
        ).apply(instance, IslandDensityFunction::new)
    );
    public static final KeyDispatchDataCodec<IslandDensityFunction> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);
    
    private final IslandShapeConfig config;
    private final LandmassSampler landmass;

    private IslandDensityFunction(IslandShapeConfig config) {
        this.config = config;
        this.landmass = new LandmassSampler(config);
    }

    public IslandShapeConfig shapeConfig() {
        return this.config;
    }

    @Override
    public double compute(DensityFunction.FunctionContext pos) {
        return this.landmass.sample(pos.blockX(), pos.blockZ());
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }

    public static void registerCodec() {
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, OmniIsleIds.id("island_continents"), CODEC);
    }
}
