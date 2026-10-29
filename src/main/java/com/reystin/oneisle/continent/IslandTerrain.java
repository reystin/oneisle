package com.reystin.oneisle.continent;

/**
 * Universal terrain elevation and signed distance sampler for OneIsle islands.
 * Side-neutral interface used by density functions, biome sources, client previews,
 * inspector tooltips, and structure/spawn analyzers.
 */
public interface IslandTerrain {
    /**
     * Continentalness value in range [-1.0, 1.0].
     * Negative values correspond to ocean; the mask shoreline sits at -0.08 and the
     * actual sea-level waterline is deeper (v3 targets about -0.18).
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
     * Signed distance to the nearest land (including satellite islets) and to the main
     * island mask separately. The main distance is negative on an islet even when the
     * merged signed distance is positive.
     */
    record Distances(double signed, double main) {}

    default Distances distances(double x, double z) {
        double distance = signedDistance(x, z);
        return new Distances(distance, distance);
    }

    /**
     * Smoothed height tier in [0, 3]; 1.0 is the neutral lowland fallback.
     */
    default double heightTierAt(double x, double z) {
        return 1.0;
    }

    /**
     * Estimated surface Y at the column. The default uses the calibrated
     * continentalness-to-elevation curve.
     */
    default double surfaceHeight(double x, double z) {
        return continentalnessToElevation(continentalness(x, z));
    }

    /**
     * Universal v3 climate sectors, or null for legacy/v2 generation.
     */
    default ClimateLayout climateLayout() {
        return null;
    }

    /**
     * Carved river system, or null if rivers are disabled / v1.
     */
    default IslandRivers rivers() {
        return null;
    }

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
