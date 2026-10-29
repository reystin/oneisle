package com.reystin.oneisle.worldgen;

import com.reystin.oneisle.OneIsleIds;
import com.mojang.serialization.Codec;
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

/**
 * Wraps the v3 final density with a small dry plateau at the editor's spawn marker.
 *
 * <p>Inland terrain height is driven by vanilla erosion/ridge noise, not by the island
 * continentalness spline, so the editor cannot know from the mask whether a basin at the
 * marker floods to sea level. This function lifts a shallow dome under the marker, leaving
 * deeper caves and the rest of the island untouched.</p>
 */
public final class SpawnPlateauDensity implements DensityFunction {
    public static final MapCodec<SpawnPlateauDensity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        DensityFunction.CODEC.fieldOf("input").forGetter(f -> f.input),
        Codec.INT.fieldOf("spawn_x").forGetter(f -> f.spawnX),
        Codec.INT.fieldOf("spawn_z").forGetter(f -> f.spawnZ),
        Codec.DOUBLE.optionalFieldOf("radius", 80.0).forGetter(f -> f.radius),
        Codec.DOUBLE.optionalFieldOf("top_y", 78.0).forGetter(f -> f.topY)
    ).apply(instance, SpawnPlateauDensity::new));

    private final DensityFunction input;
    private final int spawnX;
    private final int spawnZ;
    private final double radius;
    private final double topY;

    public SpawnPlateauDensity(DensityFunction input, int spawnX, int spawnZ, double radius, double topY) {
        this.input = input;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.radius = radius;
        this.topY = topY;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(this.input.compileSampler(context), this.spawnX, this.spawnZ,
            this.radius * this.radius, this.topY);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction rewritten = rule.rewrite(this.input);
        if (rewritten == this.input) return this;
        return new SpawnPlateauDensity(rewritten, this.spawnX, this.spawnZ, this.radius, this.topY);
    }

    @Override
    public Interval range() {
        return this.input.range();
    }

    @Override
    public int domainAxes() {
        return this.input.domainAxes() | AXIS_X | AXIS_Z;
    }

    @Override
    public MapCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    @SuppressWarnings("unchecked")
    public static void registerCodec() {
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, OneIsleIds.id("spawn_plateau"),
            (MapCodec<? extends DensityFunction>) (MapCodec<?>) CODEC);
    }

    private record Sampler(DensitySampler input, int spawnX, int spawnZ, double radiusSq, double topY)
        implements DensitySampler {
        private static final double RIM_DROP = 16.0;

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
            this.input.sampleVolume(context, buffer, volume);
            for (int zIdx = 0; zIdx < volume.sizeZ(); zIdx++) {
                int blockZ = volume.blockZ(zIdx);
                for (int xIdx = 0; xIdx < volume.sizeX(); xIdx++) {
                    int blockX = volume.blockX(xIdx);
                    double mask = mask(blockX, blockZ);
                    if (mask <= 0.0) continue;
                    for (int yIdx = 0; yIdx < volume.sizeY(); yIdx++) {
                        int index = volume.indexUnchecked(xIdx, yIdx, zIdx);
                        buffer.set(index, apply(mask, volume.blockY(yIdx), buffer.get(index)));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            double mask = mask(x, z);
            float value = this.input.sampleValue(context, x, y, z);
            return mask <= 0.0 ? value : apply(mask, y, value);
        }

        private double mask(int x, int z) {
            double dx = x - this.spawnX;
            double dz = z - this.spawnZ;
            double r2 = dx * dx + dz * dz;
            if (r2 >= this.radiusSq) return 0.0;
            double t = 1.0 - r2 / this.radiusSq;
            return t * t;
        }

        private float apply(double mask, int y, float base) {
            // The dome tapers from topY at the marker down to below sea level at the rim,
            // so the forced surface has no cliff where the mask reaches zero.
            double domeTop = this.topY - RIM_DROP * (1.0 - mask);
            double depth = domeTop - y;
            double target;
            if (depth <= 0.0) {
                target = -0.4;
            } else if (depth < 2.0) {
                target = 0.25 * depth;
            } else if (depth < 26.0) {
                target = 0.5;
            } else {
                target = -1.0;
            }
            return (float) Math.max(base, target * mask);
        }
    }
}
