package com.hack337.omniisle.continent;

/**
 * Universal terrain elevation and signed distance sampler for OmniIsle islands.
 * Side-neutral interface used by density functions, biome sources, client previews,
 * inspector tooltips, and structure/spawn analyzers.
 */
public interface IslandTerrain {
    /**
     * Continentalness value in range [-1.0, 1.0].
     * Negative values correspond to ocean (-0.08 is the shoreline), positive to land.
     */
    double continentalness(double x, double z);

    /**
     * Erosion bias in range [-1.0, 1.0]. Returns 0.0 for v1 generation.
     */
    double erosionBias(double x, double z);

    /**
     * Signed distance in blocks to the shoreline.
     * Positive values are inland, negative values are offshore.
     */
    double signedDistance(double x, double z);

    /**
     * Estimated surface elevation in Minecraft Y coordinates corresponding to the given
     * continentalness value. Calibrated against vanilla 1.18+ / 26.3 overworld surface generation.
     */
    static double continentalnessToElevation(double c) {
        if (c < -0.15) {
            return Math.max(20.0, 62.0 + (c + 0.15) * 50.0);
        } else if (c <= 0.0) {
            return 62.0 + (c + 0.15) / 0.15 * 2.0;
        } else if (c <= 0.45) {
            double t = c / 0.45;
            return 64.0 + t * 40.0;
        } else {
            double t = (c - 0.45) / 0.40;
            return 104.0 + t * 140.0;
        }
    }

    /**
     * Factory method creating the appropriate terrain sampler for the given config.
     * Picks LegacyTerrainV1 for generation_version <= 1, ModernTerrainV2 for >= 2.
     */
    static IslandTerrain create(IslandShapeConfig cfg) {
        if (cfg == null || cfg.generationVersion <= IslandShapeConfig.LEGACY_GENERATION_VERSION) {
            return new LegacyTerrainV1(cfg);
        } else {
            return new ModernTerrainV2(cfg);
        }
    }
}
