package com.hack337.omniisle.continent;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

public class ShapeLayerSettings {
    public final double xTran;
    public final double zTran;
    public final double xSize;
    public final double zSize;
    public final float rotSin;
    public final float rotCos;
    public final float multiplier;
    public final boolean hasRot;
    public final boolean overlap;
    public final ShapeLayerSettings.Noise noise;

    public ShapeLayerSettings(IslandShapeConfig.Entry entry) {
        this.xTran = entry.center_x;
        this.zTran = entry.center_z;
        this.xSize = 1.0 / entry.x_divisor;
        this.zSize = 1.0 / entry.z_divisor;
        float rotation = -entry.rotation_degrees * (float) (Math.PI / 180.0);
        this.hasRot = rotation != 0.0F;
        this.rotSin = this.hasRot ? Mth.sin(rotation) : 0.0F;
        this.rotCos = this.hasRot ? Mth.cos(rotation) : 0.0F;
        this.multiplier = entry.multiplier;
        this.overlap = entry.overlap;
        this.noise = new ShapeLayerSettings.Noise(entry.noise);
    }

    public static class Noise {
        private final PerlinNoise sampler;
        private final double scale;

        public Noise(IslandShapeConfig.Entry.Noise noise) {
            this.sampler = PerlinNoise.create(
                new LegacyRandomSource(noise.getSeed()),
                noise.first_octave,
                (DoubleList)(noise.amplitudes instanceof DoubleList l ? l : new DoubleArrayList(noise.amplitudes))
            );
            this.scale = noise.scale / this.sampler.maxBrokenValue(-1.0);
        }

        public double sample(double x, double z) {
            return this.sampler.getValue(x, 1.95, z) * this.scale;
        }
    }
}
