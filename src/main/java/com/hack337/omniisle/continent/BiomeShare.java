package com.hack337.omniisle.continent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Side-neutral biome-share catalog, weight settings and the deterministic weighted
 * land selector shared by OmniBiomeSource, the editor preview and calibration.
 *
 * <p>Worldgen consumes only {@code bias}. Weights are editor intent; calibration
 * converts them into biases. With every enabled weight at 100 and every bias at
 * zero the callers stay on the untouched legacy selection path (invariant I1).</p>
 *
 * <p>The weighted model keeps the legacy preferred family at prior 1 and gives a
 * neighbouring family prior {@code exp(-max(0, d - d_pref) / 0.22)}; the variant
 * probability is that prior times {@code exp(bias)}. Candidates beyond
 * {@code R_max = 0.62} normalized climate distance are dropped when any candidate
 * inside the cap exists. Uniform factors short-circuit to the legacy picker, so
 * zero biases reproduce it exactly, including the cyclic nearest-family fallback
 * used when the preferred family has no enabled variant.</p>
 */
public final class BiomeShare {
    private static final Logger LOGGER = LoggerFactory.getLogger(BiomeShare.class);

    public static final int MAX_WEIGHT = 400;
    public static final int DEFAULT_WEIGHT = 100;
    public static final int WEIGHT_STEP = 5;
    public static final double DEFAULT_R_MAX = 0.62;
    private static final double AXIS_SCALE = 0.60;
    /** Decay width for neighbouring climate families, in normalized distance units. */
    private static final double AFFINITY_WIDTH = 0.22;
    public static final double MIN_BIAS = -8.0;
    public static final double MAX_BIAS = 8.0;
    private static final double EPSILON = 1.0e-9;
    private static final double TICKET_LIMIT = 0.999999999999;
    /** Spatial scale of the coherent weighted ticket, matching the legacy patch field. */
    public static final double TICKET_SCALE = 420.0;
    private static final int TICKET_SALT = 7919;
    private static final int TICKET_OCTAVES = 3;
    private static final int TICKET_LUT_BINS = 1024;
    private static final int TICKET_SAMPLE_EDGE = 256;
    private static final double TICKET_SAMPLE_STEP = 97.0;
    private static final int TICKET_LUT_SEED = 0x2C9277B5;
    /**
     * Empirical CDF of the raw fbm ticket over a fixed sample lattice. The field is
     * stationary, so the mapping is seed-independent; flattening it keeps cumulative
     * shares unbiased while the underlying values remain spatially coherent.
     */
    private static final double[] TICKET_CDF = buildTicketCdf();

    private static final int FAMILY_COUNT = 18;

    /** Read-only rows: fixed by relief or by the mushroom district, never calibrated. */
    private static final int[] TERRAIN_DRIVEN_FAMILIES = {14, 15, 16};

    private static final String[] FAMILY_CENTERS = {
        "snowy_plains", "taiga", "birch_forest", "forest", "plains", "meadow",
        "cherry_grove", "dark_forest", "swamp", "mangrove_swamp", "jungle",
        "savanna", "desert", "badlands", "stony_peaks", "jagged_peaks",
        "mushroom_fields", "pale_garden"
    };

    // Temperature/humidity centroids of the threshold tree, normalized by AXIS_SCALE.
    private static final double[] CENTER_TEMPERATURE = {
        0.13, 0.16, 0.345, 0.555, 0.555, 0.40, 0.60, 0.555, 0.555, 0.74,
        0.74, 0.74, 0.90, 0.82, 0.80, 0.15, 0.50, 0.555
    };
    private static final double[] CENTER_HUMIDITY = {
        0.29, 0.72, 0.60, 0.495, 0.23, 0.27, 0.55, 0.60, 0.81, 0.70,
        0.55, 0.35, 0.18, 0.30, 0.40, 0.50, 0.50, 0.555
    };

    public record Variant(String id, int family, boolean terrainDriven) {}

    private static final Variant[] VARIANTS = {
        land("snowy_plains", 0), land("ice_spikes", 0),
        land("taiga", 1), land("snowy_taiga", 1), land("old_growth_pine_taiga", 1),
        land("old_growth_spruce_taiga", 1),
        land("birch_forest", 2), land("old_growth_birch_forest", 2),
        land("forest", 3), land("flower_forest", 3), land("windswept_forest", 3),
        land("dappled_forest", 3),
        land("plains", 4), land("sunflower_plains", 4),
        land("meadow", 5), land("grove", 5),
        land("cherry_grove", 6), land("dark_forest", 7), land("swamp", 8),
        land("mangrove_swamp", 9),
        land("jungle", 10), land("bamboo_jungle", 10), land("sparse_jungle", 10),
        land("savanna", 11), land("savanna_plateau", 11), land("windswept_savanna", 11),
        land("desert", 12),
        land("badlands", 13), land("eroded_badlands", 13), land("wooded_badlands", 13),
        terrain("stony_peaks", 14), terrain("windswept_hills", 14),
        terrain("windswept_gravelly_hills", 14),
        terrain("jagged_peaks", 15), terrain("frozen_peaks", 15), terrain("snowy_slopes", 15),
        terrain("mushroom_fields", 16),
        land("pale_garden", 17)
    };

    public static final int CATALOG_SIZE = VARIANTS.length;
    private static final Map<String, Integer> BY_ID = buildIndex();

    private BiomeShare() {}

    private static Variant land(String id, int family) { return new Variant(id, family, false); }
    private static Variant terrain(String id, int family) { return new Variant(id, family, true); }

    private static Map<String, Integer> buildIndex() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < VARIANTS.length; i++) map.put(VARIANTS[i].id, i);
        return Map.copyOf(map);
    }

    public static int catalogSize() { return VARIANTS.length; }

    public static Variant variant(int index) {
        return index >= 0 && index < VARIANTS.length ? VARIANTS[index] : null;
    }

    public static int indexOf(String id) {
        if (id == null) return -1;
        Integer index = BY_ID.get(pathOf(id));
        return index != null ? index : -1;
    }

    public static String pathOf(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    public static int familyOf(int index) {
        Variant v = variant(index);
        return v != null ? v.family : -1;
    }

    public static boolean isTerrainDriven(int family) {
        for (int candidate : TERRAIN_DRIVEN_FAMILIES) if (candidate == family) return true;
        return false;
    }

    public static boolean isTerrainDriven(String id) {
        int index = indexOf(id);
        return index >= 0 && VARIANTS[index].terrainDriven;
    }

    public static List<String> climateDrivenIds() {
        List<String> ids = new ArrayList<>();
        for (Variant v : VARIANTS) if (!v.terrainDriven) ids.add(v.id);
        return ids;
    }

    public static List<String> allLandIds() {
        List<String> ids = new ArrayList<>();
        for (Variant v : VARIANTS) ids.add(v.id);
        return ids;
    }

    public static int clampWeight(int weight) {
        if (weight <= 0) return 0;
        int clamped = Math.min(MAX_WEIGHT, weight);
        return Math.max(WEIGHT_STEP, Math.round(clamped / (float) WEIGHT_STEP) * WEIGHT_STEP);
    }

    public static double clampBias(double bias) {
        if (!Double.isFinite(bias)) return 0.0;
        return Math.max(MIN_BIAS, Math.min(MAX_BIAS, bias));
    }

    public static double biasFactor(double bias) {
        return Math.exp(clampBias(bias));
    }

    public static double distanceSquared(int family, double temperature, double humidity) {
        int f = Math.max(0, Math.min(FAMILY_CENTERS.length - 1, family));
        double dt = (temperature - CENTER_TEMPERATURE[f]) / AXIS_SCALE;
        double dh = (humidity - CENTER_HUMIDITY[f]) / AXIS_SCALE;
        return dt * dt + dh * dh;
    }

    public static double familyCenterTemperature(int family) {
        return CENTER_TEMPERATURE[Math.max(0, Math.min(CENTER_TEMPERATURE.length - 1, family))];
    }

    public static double familyCenterHumidity(int family) {
        return CENTER_HUMIDITY[Math.max(0, Math.min(CENTER_HUMIDITY.length - 1, family))];
    }

    /** Legacy patch ticket, still the exact picker when weighted factors are uniform. */
    public static double ticketFor(int preferredFamily, long biomeSeed, int x, int z) {
        double patch = IslandClimate.fbm(x / 420.0, z / 420.0,
            IslandClimate.saltedSeed(biomeSeed, 1201 + preferredFamily * 37));
        return Math.max(0.0, Math.min(TICKET_LIMIT, patch * 0.5 + 0.5));
    }

    /**
     * Coherent near-uniform ticket for weighted selection. Adjacent columns share a
     * 420-block fbm patch (independent of the preferred family so biome borders do
     * not flicker); the empirical CDF flattens its clustered values so cumulative
     * probabilities stay unbiased. Never a per-block hash.
     */
    public static double weightedTicketFor(long biomeSeed, int x, int z) {
        double raw = IslandClimate.fbm(x / TICKET_SCALE, z / TICKET_SCALE,
            IslandClimate.saltedSeed(biomeSeed, TICKET_SALT), TICKET_OCTAVES);
        return ticketFromRaw(raw);
    }

    /** Maps one raw ticket sample through the static quantile table; allocation-free. */
    static double ticketFromRaw(double raw) {
        if (!(raw > -1.0)) return 0.0;
        if (raw >= 1.0) return TICKET_LIMIT;
        double position = (raw + 1.0) * 0.5 * TICKET_LUT_BINS;
        int bin = (int) position;
        if (bin >= TICKET_LUT_BINS) bin = TICKET_LUT_BINS - 1;
        double lower = bin == 0 ? 0.0 : TICKET_CDF[bin - 1];
        double upper = TICKET_CDF[bin];
        double frac = position - bin;
        double value = lower + (upper - lower) * frac;
        return Math.max(0.0, Math.min(TICKET_LIMIT, value));
    }

    private static double[] buildTicketCdf() {
        int samples = TICKET_SAMPLE_EDGE * TICKET_SAMPLE_EDGE;
        int[] histogram = new int[TICKET_LUT_BINS];
        for (int gz = 0; gz < TICKET_SAMPLE_EDGE; gz++) {
            double z = (gz + 0.5) * TICKET_SAMPLE_STEP / TICKET_SCALE;
            for (int gx = 0; gx < TICKET_SAMPLE_EDGE; gx++) {
                double x = (gx + 0.5) * TICKET_SAMPLE_STEP / TICKET_SCALE;
                double raw = IslandClimate.fbm(x, z, TICKET_LUT_SEED, TICKET_OCTAVES);
                int bin = (int) Math.floor((raw + 1.0) * 0.5 * TICKET_LUT_BINS);
                if (bin < 0) bin = 0;
                if (bin >= TICKET_LUT_BINS) bin = TICKET_LUT_BINS - 1;
                histogram[bin]++;
            }
        }
        double[] cdf = new double[TICKET_LUT_BINS];
        int cumulative = 0;
        for (int bin = 0; bin < TICKET_LUT_BINS; bin++) {
            cumulative += histogram[bin];
            cdf[bin] = cumulative / (double) samples;
        }
        return cdf;
    }

    /** True when every candidate has the same bias factor, i.e. the legacy picker applies. */
    public static boolean areFactorsUniform(double[] factors, int count) {
        if (count <= 1) return true;
        double first = factors[0];
        for (int i = 1; i < count; i++) {
            if (Math.abs(factors[i] - first) > EPSILON * Math.max(1.0, Math.abs(first))) {
                return false;
            }
        }
        return true;
    }

    /** Reusable per-thread buffers; the selector itself never allocates. */
    public static final class SelectorScratch {
        private final double[] variantWeights = new double[CATALOG_SIZE];

        public SelectorScratch() {}
    }

    /**
     * Deterministic weighted land selection.
     *
     * @param preferredFamily family produced by the legacy climate tree
     * @param ids enabled catalog indices in config order; may include terrain-driven rows
     * @param factors precomputed {@code exp(bias)} per candidate, aligned with {@code ids}
     * @param ticket near-uniform ticket from {@link #weightedTicketFor(long, int, int)}
     * @param legacyTicket fbm patch ticket from {@link #ticketFor(int, long, int, int)}
     * @return the selected catalog index, or -1 when the caller should use the legacy fallback
     */
    public static int select(int preferredFamily, double temperature, double humidity,
                             int[] ids, double[] factors, int count,
                             double ticket, double legacyTicket, SelectorScratch scratch) {
        if (count <= 0 || preferredFamily < 0 || preferredFamily >= FAMILY_COUNT) return -1;
        if (isTerrainDriven(preferredFamily)) return -1;

        if (areFactorsUniform(factors, count)) {
            return legacyEquivalent(preferredFamily, ids, count, legacyTicket);
        }

        double preferredDistance = Math.sqrt(distanceSquared(preferredFamily, temperature, humidity));
        double total = accumulate(preferredFamily, preferredDistance, temperature, humidity,
            ids, factors, count, true, scratch);
        if (!(total > 0.0)) {
            total = accumulate(preferredFamily, preferredDistance, temperature, humidity,
                ids, factors, count, false, scratch);
        }
        if (!(total > 0.0)) return -1;

        double target = Math.max(0.0, Math.min(TICKET_LIMIT, ticket)) * total;
        double cumulative = 0.0;
        int last = -1;
        for (int i = 0; i < count; i++) {
            double weight = scratch.variantWeights[i];
            if (weight <= 0.0) continue;
            last = i;
            cumulative += weight;
            if (target < cumulative) return ids[i];
        }
        return last >= 0 ? ids[last] : -1;
    }

    private static double accumulate(int preferredFamily, double preferredDistance,
                                     double temperature, double humidity,
                                     int[] ids, double[] factors, int count,
                                     boolean capped, SelectorScratch scratch) {
        double maxDistanceSq = DEFAULT_R_MAX * DEFAULT_R_MAX;
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            int family = familyOf(ids[i]);
            if (family < 0 || isTerrainDriven(family)) {
                scratch.variantWeights[i] = 0.0;
                continue;
            }
            double distanceSq = distanceSquared(family, temperature, humidity);
            if (capped && distanceSq > maxDistanceSq) {
                scratch.variantWeights[i] = 0.0;
                continue;
            }
            double prior;
            if (family == preferredFamily) {
                prior = 1.0;
            } else {
                double distance = Math.sqrt(distanceSq);
                prior = Math.exp(-Math.max(0.0, distance - preferredDistance) / AFFINITY_WIDTH);
            }
            double probability = prior * factors[i];
            scratch.variantWeights[i] = probability;
            sum += probability;
        }
        return sum;
    }

    /**
     * Zero-bias selection identical to the legacy family filter and patch picker.
     * Candidate order must match the enabled list order used by the caller.
     */
    static int legacyEquivalent(int preferredFamily, int[] ids, int count, double ticket) {
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int family = familyOf(ids[i]);
            if (family < 0) continue;
            int distance = Math.min(Math.floorMod(family - preferredFamily, FAMILY_COUNT),
                Math.floorMod(preferredFamily - family, FAMILY_COUNT));
            if (distance < bestDistance) bestDistance = distance;
        }
        if (bestDistance == Integer.MAX_VALUE) return -1;

        int matches = 0;
        for (int i = 0; i < count; i++) {
            int family = familyOf(ids[i]);
            if (family >= 0 && Math.min(Math.floorMod(family - preferredFamily, FAMILY_COUNT),
                Math.floorMod(preferredFamily - family, FAMILY_COUNT)) == bestDistance) {
                matches++;
            }
        }
        int pick = Math.min(matches - 1, (int) Math.floor(Math.max(0.0, Math.min(TICKET_LIMIT, ticket)) * matches));
        int seen = 0;
        for (int i = 0; i < count; i++) {
            int family = familyOf(ids[i]);
            if (family < 0 || Math.min(Math.floorMod(family - preferredFamily, FAMILY_COUNT),
                Math.floorMod(preferredFamily - family, FAMILY_COUNT)) != bestDistance) continue;
            if (seen++ == pick) return ids[i];
        }
        return -1;
    }

    public static boolean isNeutralWeight(int weight) {
        return weight == DEFAULT_WEIGHT;
    }

    public static boolean isZeroBias(double bias) {
        return Math.abs(bias) <= EPSILON;
    }

    public record Settings(Map<String, Integer> weights,
                           Map<String, Double> bias,
                           Map<String, Double> achieved,
                           Map<String, Double> target,
                           boolean feasible) {
        public static final Settings EMPTY = new Settings(Map.of(), Map.of(), Map.of(), Map.of(), true);

        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                .optionalFieldOf("weights", Map.of()).forGetter(Settings::weights),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .optionalFieldOf("bias", Map.of()).forGetter(Settings::bias),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .optionalFieldOf("achieved", Map.of()).forGetter(Settings::achieved),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .optionalFieldOf("target", Map.of()).forGetter(Settings::target),
            Codec.BOOL.optionalFieldOf("feasible", true).forGetter(Settings::feasible)
        ).apply(instance, Settings::new));

        public Settings {
            weights = weights != null ? weights : Map.of();
            bias = bias != null ? bias : Map.of();
            achieved = achieved != null ? achieved : Map.of();
            target = target != null ? target : Map.of();
        }

        public int weight(String id) {
            Integer value = weights.get(fullKey(id));
            if (value == null) value = weights.get(pathOf(id));
            return value != null ? clampWeight(value) : DEFAULT_WEIGHT;
        }

        public double bias(String id) {
            Double value = bias.get(fullKey(id));
            if (value == null) value = bias.get(pathOf(id));
            return value != null ? clampBias(value) : 0.0;
        }

        public boolean hasWeightZero(String id) {
            return weight(id) == 0;
        }

        /** True when at least one climate-driven weight differs from the natural 100. */
        public boolean hasNonNeutralWeight() {
            for (Map.Entry<String, Integer> entry : weights.entrySet()) {
                int index = indexOf(entry.getKey());
                if (index < 0 || VARIANTS[index].terrainDriven || entry.getValue() == null) continue;
                if (clampWeight(entry.getValue()) != DEFAULT_WEIGHT) return true;
            }
            return false;
        }

        public Settings sanitized() {
            Map<String, Integer> safeWeights = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : weights.entrySet()) {
                int index = indexOf(e.getKey());
                if (index < 0) {
                    LOGGER.warn("OmniIsle biome_share: ignoring unknown biome id '{}'", e.getKey());
                    continue;
                }
                Integer value = e.getValue();
                if (value == null) continue;
                safeWeights.put(VARIANTS[index].id, clampWeight(value));
            }
            Map<String, Double> safeBias = sanitizeDoubles(bias, MIN_BIAS, MAX_BIAS, "bias");
            Map<String, Double> safeAchieved = sanitizeDoubles(achieved, 0.0, 100.0, "achieved");
            Map<String, Double> safeTarget = sanitizeDoubles(target, 0.0, 100.0, "target");
            return new Settings(Map.copyOf(safeWeights), Map.copyOf(safeBias),
                Map.copyOf(safeAchieved), Map.copyOf(safeTarget), feasible);
        }

        private static Map<String, Double> sanitizeDoubles(Map<String, Double> source,
                                                           double min, double max, String field) {
            Map<String, Double> safe = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : source.entrySet()) {
                int index = indexOf(e.getKey());
                if (index < 0) {
                    LOGGER.warn("OmniIsle biome_share: ignoring unknown {} biome id '{}'", field, e.getKey());
                    continue;
                }
                Double value = e.getValue();
                if (value == null || !Double.isFinite(value)) {
                    LOGGER.warn("OmniIsle biome_share: ignoring non-finite {} for '{}'", field, e.getKey());
                    continue;
                }
                double clamped = Math.max(min, Math.min(max, value));
                if (Math.abs(clamped) > EPSILON) safe.put(VARIANTS[index].id, clamped);
            }
            return safe;
        }

        public static Settings fromMaps(Map<String, Integer> weights,
                                        Map<String, Double> bias,
                                        Map<String, Double> achieved,
                                        Map<String, Double> target,
                                        boolean feasible) {
            return new Settings(weights, bias, achieved, target, feasible);
        }
    }

    private static String fullKey(String id) {
        return "minecraft:" + pathOf(id);
    }
}
