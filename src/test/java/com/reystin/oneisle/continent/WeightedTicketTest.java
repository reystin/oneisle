package com.reystin.oneisle.continent;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 10.5: the weighted ticket must be a spatially coherent, near-uniform field,
 * not the white per-block hash that produced speckled worlds in 0.8.0.
 */
class WeightedTicketTest {
    private static final long SEED = 0x306d956984a4bf25L;

    @Test
    @DisplayName("10.5.1 weighted ticket is coherent, full-range and near-uniform")
    void weightedTicketIsCoherentAndUniform() {
        double maxStep = 0.0;
        double sumStep = 0.0;
        int steps = 0;
        for (int z = -2048; z <= 2048; z += 97) {
            for (int x = -2048; x < 2048; x++) {
                double delta = Math.abs(BiomeShare.weightedTicketFor(SEED, x + 1, z)
                    - BiomeShare.weightedTicketFor(SEED, x, z));
                maxStep = Math.max(maxStep, delta);
                sumStep += delta;
                steps++;
            }
        }
        for (int x = -2048; x <= 2048; x += 97) {
            for (int z = -2048; z < 2048; z++) {
                double delta = Math.abs(BiomeShare.weightedTicketFor(SEED, x, z + 1)
                    - BiomeShare.weightedTicketFor(SEED, x, z));
                maxStep = Math.max(maxStep, delta);
                sumStep += delta;
                steps++;
            }
        }
        double meanStep = sumStep / steps;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int[] histogram = new int[16];
        long samples = 0;
        for (int seedIndex = 0; seedIndex < 32; seedIndex++) {
            long seed = SEED + 0x9E3779B97F4A7C15L * seedIndex;
            for (int gz = 0; gz < 64; gz++) {
                for (int gx = 0; gx < 64; gx++) {
                    double value = BiomeShare.weightedTicketFor(seed,
                        gx * 420 + 137 - 13440, gz * 420 + 89 - 13440);
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    int bin = (int) (value * histogram.length);
                    histogram[Math.min(histogram.length - 1, Math.max(0, bin))]++;
                    samples++;
                }
            }
        }
        int maxBin = 0;
        int minBin = Integer.MAX_VALUE;
        for (int count : histogram) {
            maxBin = Math.max(maxBin, count);
            minBin = Math.min(minBin, count);
        }
        double maxDeviation = Math.max(maxBin - samples / (double) histogram.length,
            samples / (double) histogram.length - minBin) / samples;
        System.out.println(String.format(Locale.ROOT,
            "10.5.1 ticket adjacency maxStep=%.5f meanStep=%.5f range=[%.4f, %.4f] "
                + "histDeviation=%.2f%% samples=%d",
            maxStep, meanStep, min, max, maxDeviation * 100.0, samples));

        assertTrue(maxStep < 0.02, "ticket jumps between adjacent columns: " + maxStep);
        assertTrue(meanStep < 0.002, "ticket is not spatially smooth: " + meanStep);
        assertTrue(min < 0.02, "ticket never reaches the low end: " + min);
        assertTrue(max > 0.98, "ticket never reaches the high end: " + max);
        assertTrue(maxDeviation < 0.02,
            "ticket histogram deviates from uniform by " + maxDeviation * 100.0 + "%");
    }

    @Test
    @DisplayName("10.5.2 quantile table is monotone and maps the raw range onto the ticket range")
    void quantileTableIsMonotone() {
        assertEquals(0.0, BiomeShare.ticketFromRaw(-2.0));
        double previous = -1.0;
        double rawMin = Double.POSITIVE_INFINITY;
        double rawMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 2000; i++) {
            double raw = -1.0 + 2.0 * i / 2000.0;
            double value = BiomeShare.ticketFromRaw(raw);
            rawMin = Math.min(rawMin, value);
            rawMax = Math.max(rawMax, value);
            assertTrue(value >= previous - 1.0e-9,
                "quantile mapping must be monotone at raw=" + raw);
            previous = value;
        }
        assertTrue(rawMin < 0.01, "mapping never reaches low tickets: " + rawMin);
        assertTrue(rawMax > 0.99, "mapping never reaches high tickets: " + rawMax);
        assertTrue(BiomeShare.ticketFromRaw(2.0) > 0.999);
        System.out.println(String.format(Locale.ROOT,
            "10.5.2 ticketFromRaw range=[%.4f, %.4f]", rawMin, rawMax));
    }
}
