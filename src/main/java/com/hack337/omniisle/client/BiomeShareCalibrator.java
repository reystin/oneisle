package com.hack337.omniisle.client;

import com.hack337.omniisle.continent.BiomeShare;
import com.hack337.omniisle.continent.IslandClimate;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ShoreField;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts editor weights into per-biome bias values on a fixed mask-land grid.
 *
 * <p>Terrain-driven families, the shore band and rivers are excluded. The
 * baseline share is measured with all biases zero, which is exactly the legacy
 * distribution. Targets follow {@code s_f * w_f / sum(s_g * w_g)} and are fitted
 * with damped log-ratio updates. The result is deterministic and identical for
 * the live editor and {@link WorldPackBuilder#export}.</p>
 */
public final class BiomeShareCalibrator {
    public static final int GRID_SIZE = 64;
    public static final int MAX_ITERATIONS = 24;
    public static final double DAMPING = 0.7;
    public static final double CONVERGED_ERROR_PP = 1.0;
    public static final double FEASIBLE_ERROR_PP = 3.0;
    private static final double COUNT_FLOOR = 0.005;
    private static final double ACTIVE_TARGET_PP = 0.05;

    public record Result(Map<String, Integer> weights,
                         Map<String, Double> bias,
                         Map<String, Double> achieved,
                         Map<String, Double> target,
                         boolean feasible,
                         double maxErrorPp,
                         int samples,
                         int iterations) {
        public static Result empty() {
            return new Result(Map.of(), Map.of(), Map.of(), Map.of(), true, 0.0, 0, 0);
        }
    }

    private BiomeShareCalibrator() {}

    public static Result calibrate(IslandDraft draft) {
        return calibrate(draft, GRID_SIZE, MAX_ITERATIONS);
    }

    public static Result calibrate(IslandDraft draft, int gridSize, int maxIterations) {
        int count = 0;
        int[] ids = new int[BiomeShare.CATALOG_SIZE];
        int[] weights = new int[BiomeShare.CATALOG_SIZE];
        int[] positionByCatalog = new int[BiomeShare.CATALOG_SIZE];
        java.util.Arrays.fill(positionByCatalog, -1);
        Map<String, Integer> weightsById = new LinkedHashMap<>();
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (!type.isLand() || !draft.isBiomeEnabled(type)) continue;
            if (BiomeShare.isTerrainDriven(type.family)) continue;
            int weight = draft.biomeWeight(type);
            if (weight <= 0) continue;
            int index = BiomeShare.indexOf(type.id);
            if (index < 0 || index >= BiomeShare.CATALOG_SIZE) continue;
            positionByCatalog[index] = count;
            ids[count] = index;
            weights[count] = weight;
            weightsById.put(type.id, weight);
            count++;
        }
        if (count == 0) return Result.empty();

        int resolution = draft.resolution();
        int grid = Math.max(8, Math.min(256, gridSize));
        IslandTerrain terrain = draft.createTerrain();
        double halfSpan = draft.worldSpan() / 2.0;
        int generationVersion = draft.generationVersionNumber();
        long biomeSeed = draft.biomeSeed();
        int climateScale = draft.climateScale();
        IslandClimate.GradientMode gradientMode = draft.gradientMode();
        double gradientStrength = draft.gradientStrength();

        int capacity = grid * grid;
        int[] preferredFamilies = new int[capacity];
        double[] temperatures = new double[capacity];
        double[] humidities = new double[capacity];
        double[] tickets = new double[capacity];
        double[] legacyTickets = new double[capacity];
        int samples = 0;

        for (int gz = 0; gz < grid; gz++) {
            int pz = Math.min(resolution - 1, (int) Math.floor((gz + 0.5) * resolution / grid));
            for (int gx = 0; gx < grid; gx++) {
                int px = Math.min(resolution - 1, (int) Math.floor((gx + 0.5) * resolution / grid));
                if (!draft.isLand(px, pz)) continue;
                double wx = draft.worldX(px);
                double wz = draft.worldZ(pz);
                double continentalness = terrain.continentalness(wx, wz);
                if (generationVersion >= 3) {
                    if (terrain.signedDistance(wx, wz) <= ShoreField.MAX_INLAND) continue;
                } else if (continentalness <= 0.06) {
                    continue;
                }
                if (generationVersion >= 2 && terrain.rivers() != null && terrain.rivers().isEnabled()
                    && terrain.rivers().isRiver(wx, wz)) {
                    continue;
                }

                BiomePreview.Selection selection = BiomePreview.selectionV3(
                    continentalness, wx, wz, biomeSeed, climateScale, gradientMode, gradientStrength,
                    draft.theme(), 0.0, generationVersion, halfSpan, terrain.rivers(), terrain,
                    draft.shoreSettings(), false);
                if (!selection.isLand()) continue;
                int family = selection.landFamily();
                if (family < 0 || BiomeShare.isTerrainDriven(family)) continue;
                if (!familyHasCandidate(family, ids, count)) continue;

                preferredFamilies[samples] = family;
                temperatures[samples] = selection.temperature();
                humidities[samples] = selection.humidity();
                int sampleX = (int) Math.floor(wx / 4.0) * 4;
                int sampleZ = (int) Math.floor(wz / 4.0) * 4;
                tickets[samples] = BiomeShare.weightedTicketFor(biomeSeed, sampleX, sampleZ);
                legacyTickets[samples] = BiomeShare.ticketFor(family, biomeSeed, sampleX, sampleZ);
                samples++;
            }
        }
        if (samples == 0) {
            return new Result(weightsById, Map.of(), Map.of(), Map.of(), true, 0.0, 0, 0);
        }

        double[] bias = new double[count];
        double[] factors = new double[count];
        java.util.Arrays.fill(factors, 1.0);
        int[] baselineCounts = new int[count];
        BiomeShare.SelectorScratch scratch = new BiomeShare.SelectorScratch();
        for (int i = 0; i < samples; i++) {
            int selected = BiomeShare.select(preferredFamilies[i], temperatures[i], humidities[i],
                ids, factors, count, tickets[i], legacyTickets[i], scratch);
            if (selected < 0) continue;
            int position = positionByCatalog[selected];
            if (position >= 0) baselineCounts[position]++;
        }

        int measured = 0;
        for (int value : baselineCounts) measured += value;
        if (measured == 0) {
            return new Result(weightsById, Map.of(), Map.of(), Map.of(), true, 0.0, samples, 0);
        }

        double[] baseline = new double[count];
        double[] target = new double[count];
        double targetSum = 0.0;
        for (int i = 0; i < count; i++) {
            baseline[i] = baselineCounts[i] / (double) measured;
            target[i] = baseline[i] * weights[i] / BiomeShare.DEFAULT_WEIGHT;
            targetSum += target[i];
        }
        if (targetSum <= 0.0) {
            return new Result(weightsById, Map.of(), Map.of(), Map.of(), true, 0.0, samples, 0);
        }
        for (int i = 0; i < count; i++) target[i] /= targetSum;

        double[] achieved = new double[count];
        double maxErrorPp = 0.0;
        int iterations = 0;
        int[] counts = new int[count];
        boolean unreachable = false;
        for (int i = 0; i < count; i++) {
            if (target[i] > 0.0) {
                bias[i] = Math.log(target[i]) - Math.log(Math.max(baseline[i], COUNT_FLOOR));
            } else {
                if (weights[i] != BiomeShare.DEFAULT_WEIGHT) unreachable = true;
                bias[i] = BiomeShare.MIN_BIAS;
            }
        }
        double initialMean = 0.0;
        for (double value : bias) initialMean += value;
        initialMean /= Math.max(1, count);
        for (int i = 0; i < count; i++) {
            bias[i] = BiomeShare.clampBias(bias[i] - initialMean);
            factors[i] = BiomeShare.biasFactor(bias[i]);
        }
        for (int iteration = 1; iteration <= Math.max(1, maxIterations); iteration++) {
            iterations = iteration;
            java.util.Arrays.fill(counts, 0);
            for (int i = 0; i < samples; i++) {
                int selected = BiomeShare.select(preferredFamilies[i], temperatures[i], humidities[i],
                    ids, factors, count, tickets[i], legacyTickets[i], scratch);
                if (selected < 0) continue;
                int position = positionByCatalog[selected];
                if (position >= 0) counts[position]++;
            }
            int total = 0;
            for (int value : counts) total += value;
            if (total <= 0) total = 1;
            for (int i = 0; i < count; i++) achieved[i] = counts[i] / (double) total;
            maxErrorPp = maxErrorPp(achieved, target);
            if (maxErrorPp <= CONVERGED_ERROR_PP) break;
            if (iteration == maxIterations) break;

            for (int i = 0; i < count; i++) {
                if (target[i] <= 0.0) continue;
                double actual = Math.max(achieved[i], COUNT_FLOOR);
                bias[i] += DAMPING * (Math.log(target[i]) - Math.log(actual));
            }
            double mean = 0.0;
            for (double value : bias) mean += value;
            mean /= Math.max(1, count);
            for (int i = 0; i < count; i++) {
                bias[i] = BiomeShare.clampBias(bias[i] - mean);
                factors[i] = BiomeShare.biasFactor(bias[i]);
            }
        }

        Map<String, Double> biasById = new LinkedHashMap<>();
        Map<String, Double> achievedById = new LinkedHashMap<>();
        Map<String, Double> targetById = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = BiomeShare.variant(ids[i]).id();
            if (!BiomeShare.isZeroBias(bias[i])) biasById.put(id, bias[i]);
            achievedById.put(id, achieved[i] * 100.0);
            targetById.put(id, target[i] * 100.0);
        }
        boolean feasible = maxErrorPp <= FEASIBLE_ERROR_PP && !unreachable;
        return new Result(weightsById, biasById, achievedById, targetById,
            feasible, maxErrorPp, samples, iterations);
    }

    private static boolean familyHasCandidate(int family, int[] ids, int count) {
        for (int i = 0; i < count; i++) {
            if (BiomeShare.familyOf(ids[i]) == family) return true;
        }
        return false;
    }

    private static double maxErrorPp(double[] achieved, double[] target) {
        double max = 0.0;
        for (int i = 0; i < achieved.length; i++) {
            if (target[i] * 100.0 < ACTIVE_TARGET_PP) continue;
            max = Math.max(max, Math.abs(achieved[i] - target[i]) * 100.0);
        }
        return max;
    }
}
