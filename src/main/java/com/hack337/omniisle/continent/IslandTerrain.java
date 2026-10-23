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
