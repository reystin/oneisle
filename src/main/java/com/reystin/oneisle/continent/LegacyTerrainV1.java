package com.reystin.oneisle.continent;

/**
 * Frozen legacy terrain generator preserving OneIsle 0.5.0 generation math bit-for-bit.
 * Evaluates continentalness, 0.0 erosion bias, and signed distance to shoreline.
 */
public final class LegacyTerrainV1 implements IslandTerrain {
    private final IslandShapeConfig config;
    private final LandmassSampler landmass;

    public LegacyTerrainV1(IslandShapeConfig config) {
        this.config = config;
        this.landmass = new LandmassSampler(config);
    }

    @Override
    public double continentalness(double x, double z) {
        return this.landmass.sample(x, z);
    }

    @Override
    public double erosionBias(double x, double z) {
        return 0.0;
    }

    @Override
    public double signedDistance(double x, double z) {
        if (config != null && config.mask != null) {
            return config.mask.sampleSignedDistance(x, z);
        }
        // Approximate distance if only procedural noise entries are used without mask
        double c = continentalness(x, z);
        return c >= -0.08 ? (c + 0.08) * 192.0 / 0.70 : (c + 0.08) * 400.0 / 0.92;
    }

    public IslandShapeConfig config() {
        return config;
    }
}
