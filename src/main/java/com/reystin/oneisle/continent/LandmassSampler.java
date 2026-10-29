package com.reystin.oneisle.continent;

import java.util.List;
import net.minecraft.util.Mth;

public class LandmassSampler {
    private final List<ShapeLayerSettings> continents;
    private final IslandMask mask;

    public LandmassSampler(IslandShapeConfig config) {
        this.continents = config.createSettings();
        this.mask = config.mask;
    }

    public double sample(double worldX, double worldZ) {
        if (this.mask != null) {
            return this.mask.sampleTerrainValue(worldX, worldZ);
        }

        double value = 0.0;
        int size = this.continents.size();

        for (int i = 0; i < size; i++) {
            ShapeLayerSettings c = this.continents.get(i);
            double x = worldX - c.xTran;
            double z = worldZ - c.zTran;
            if (c.hasRot) {
                double xt = x;
                x = x * (double)c.rotCos - z * (double)c.rotSin;
                z = xt * (double)c.rotSin + z * (double)c.rotCos;
            }

            double sizeMax = Math.max(c.xSize, c.zSize);
            double noise = c.noise.sample(x * sizeMax, z * sizeMax);
            x *= c.xSize;
            z *= c.zSize;
            double v = this.radialFalloff(x, z) * 2.0;
            if (v > 0.5) {
                v += (double)Mth.cos((float)v * (float) Math.PI) * noise;
            }

            v *= (double)c.multiplier;
            if (c.overlap) {
                value = Math.max(value, v);
            } else {
                value += v;
            }
        }

        return value - 1.0;
    }

    private double radialFalloff(double x, double z) {
        return Math.pow(2.0, -(x * x + z * z));
    }
}
