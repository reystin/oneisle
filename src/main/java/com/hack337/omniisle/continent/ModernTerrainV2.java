package com.hack337.omniisle.continent;

/**
 * Modern terrain generator for OmniIsle 0.6.0+.
 * In Phase 1, serves as the target for generation_version >= 2.
 * Phase 2-4 augment this with sub-pixel EDT, Catmull-Rom bicubic interpolation,
 * domain warping, near-coast roughness, ocean bathymetry, satellite islets, and mountain ridges.
 */
public final class ModernTerrainV2 implements IslandTerrain {
    private final IslandShapeConfig config;
    private final LegacyTerrainV1 baseline;

    public ModernTerrainV2(IslandShapeConfig config) {
        this.config = config;
        this.baseline = new LegacyTerrainV1(config);
    }

    @Override
    public double continentalness(double x, double z) {
        return baseline.continentalness(x, z);
    }

    @Override
    public double erosionBias(double x, double z) {
        return baseline.erosionBias(x, z);
    }

    @Override
    public double signedDistance(double x, double z) {
        return baseline.signedDistance(x, z);
    }

    public IslandShapeConfig config() {
        return config;
    }
}
