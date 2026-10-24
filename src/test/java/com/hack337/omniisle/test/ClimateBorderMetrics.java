package com.hack337.omniisle.test;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only measurement of straight runs along biome-family boundary chains.
 * Family grids come from {@code IslandClimate.sample}; a border point is a pair of
 * horizontally or vertically adjacent samples with different land families.
 */
final class ClimateBorderMetrics {
    record Segment(double length, int points, double startX, double startZ, double endX, double endZ) {}

    /** 45 intervals at a 10-block step: any straight run past 400 blocks contains such a chord. */
    private static final int MAX_CHORD_POINTS = 46;

    private ClimateBorderMetrics() {}

    /**
     * Longest chord whose intermediate chain points stay within {@code tolerance}
     * blocks of the chord. Each chain is traced across adjacent scanlines.
     */
    static Segment longestStraight(short[][] families, boolean horizontal, int step,
                                   double radius, double tolerance) {
        List<List<int[]>> chains = chains(families, horizontal);
        double best = 0.0;
        int bestPoints = 0;
        double[] bestEnds = new double[4];
        for (List<int[]> chain : chains) {
            int n = chain.size();
            for (int i = 0; i < n - 1; i++) {
                double ax = worldX(chain.get(i), horizontal, step, radius);
                double az = worldZ(chain.get(i), horizontal, step, radius);
                for (int j = i + 1; j < n && j - i <= MAX_CHORD_POINTS; j++) {
                    double bx = worldX(chain.get(j), horizontal, step, radius);
                    double bz = worldZ(chain.get(j), horizontal, step, radius);
                    boolean straight = true;
                    for (int k = i + 1; k < j; k++) {
                        double px = worldX(chain.get(k), horizontal, step, radius);
                        double pz = worldZ(chain.get(k), horizontal, step, radius);
                        if (pointLineDistance(px, pz, ax, az, bx, bz) > tolerance) {
                            straight = false;
                            break;
                        }
                    }
                    if (!straight) break;
                    double span = Math.hypot(bx - ax, bz - az);
                    if (span > best) {
                        best = span;
                        bestPoints = j - i + 1;
                        bestEnds = new double[]{ax, az, bx, bz};
                    }
                }
            }
        }
        return new Segment(best, bestPoints, bestEnds[0], bestEnds[1], bestEnds[2], bestEnds[3]);
    }

    private static List<List<int[]>> chains(short[][] families, boolean horizontal) {
        int n = families.length;
        List<List<int[]>> completed = new ArrayList<>();
        List<List<int[]>> active = new ArrayList<>();
        for (int line = 0; line < n; line++) {
            List<Integer> coords = new ArrayList<>();
            for (int i = 1; i < n; i++) {
                short a = horizontal ? families[line][i - 1] : families[i - 1][line];
                short b = horizontal ? families[line][i] : families[i][line];
                if (a >= 0 && b >= 0 && a != b) coords.add(i);
            }
            boolean[] used = new boolean[coords.size()];
            List<List<int[]>> next = new ArrayList<>();
            for (List<int[]> chain : active) {
                int[] lastPoint = chain.get(chain.size() - 1);
                int last = lastPoint[1];
                int bestIndex = -1;
                int bestDelta = 2;
                for (int i = 0; i < coords.size(); i++) {
                    if (used[i]) continue;
                    int delta = Math.abs(coords.get(i) - last);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestIndex = i;
                    }
                }
                if (bestIndex >= 0) {
                    used[bestIndex] = true;
                    chain.add(new int[]{line, coords.get(bestIndex)});
                    next.add(chain);
                } else {
                    completed.add(chain);
                }
            }
            for (int i = 0; i < coords.size(); i++) {
                if (used[i]) continue;
                List<int[]> chain = new ArrayList<>();
                chain.add(new int[]{line, coords.get(i)});
                next.add(chain);
            }
            active = next;
        }
        completed.addAll(active);
        return completed;
    }

    private static double worldX(int[] point, boolean horizontal, int step, double radius) {
        int coord = horizontal ? point[1] : point[0];
        return -radius + coord * step;
    }

    private static double worldZ(int[] point, boolean horizontal, int step, double radius) {
        int coord = horizontal ? point[0] : point[1];
        return -radius + coord * step;
    }

    private static double pointLineDistance(double px, double pz, double ax, double az,
                                            double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double length = Math.hypot(dx, dz);
        if (length == 0.0) return Math.hypot(px - ax, pz - az);
        return Math.abs(dx * (pz - az) - dz * (px - ax)) / length;
    }
}
