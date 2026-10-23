package com.hack337.omniisle.continent;

/**
 * Fast, allocation-free, thread-safe 2D noise library for OmniIsle terrain generation.
 * All noise outputs are strictly normalized to the range [-1.0, 1.0].
 */
public final class IslandNoise {
    private IslandNoise() {}

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    // 12 2D gradient directions
    private static final int[] GRAD_X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0};
    private static final int[] GRAD_Z = {0, 0, 0, 0, 1, 1, -1, -1, 1, -1, 1, -1};

    /**
     * 2D Simplex Noise with explicit seed.
     * Output range is strictly normalized to [-1.0, 1.0].
     */
    public static double simplex2(double x, double z, long seed) {
        double s = (x + z) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(z + s);

        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double z0 = z - (j - t);

        int i1, j1;
        if (x0 > z0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        double x1 = x0 - i1 + G2;
        double z1 = z0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double z2 = z0 - 1.0 + 2.0 * G2;

        double n0, n1, n2;

        double t0 = 0.5 - x0 * x0 - z0 * z0;
        if (t0 < 0.0) {
            n0 = 0.0;
        } else {
            t0 *= t0;
            int gi0 = hashCoords(i, j, seed) % 12;
            n0 = t0 * t0 * (GRAD_X[gi0] * x0 + GRAD_Z[gi0] * z0);
        }

        double t1 = 0.5 - x1 * x1 - z1 * z1;
        if (t1 < 0.0) {
            n1 = 0.0;
        } else {
            t1 *= t1;
            int gi1 = hashCoords(i + i1, j + j1, seed) % 12;
            n1 = t1 * t1 * (GRAD_X[gi1] * x1 + GRAD_Z[gi1] * z1);
        }

        double t2 = 0.5 - x2 * x2 - z2 * z2;
        if (t2 < 0.0) {
            n2 = 0.0;
        } else {
            t2 *= t2;
            int gi2 = hashCoords(i + 1, j + 1, seed) % 12;
            n2 = t2 * t2 * (GRAD_X[gi2] * x2 + GRAD_Z[gi2] * z2);
        }

        // Scale factor 70.1481... maps 2D simplex peak to 1.0
        double result = 70.14805770653953 * (n0 + n1 + n2);
        return Math.max(-1.0, Math.min(1.0, result));
    }

    /**
     * Fractal Brownian Motion (fBm) using 2D simplex noise.
     * Guaranteed strictly normalized to [-1.0, 1.0].
     *
     * @param x coordinate X
     * @param z coordinate Z
     * @param seed base seed
     * @param octaves number of noise octaves (typically 3 or 4)
     * @param lacunarity frequency multiplier per octave (typically 2.0)
     * @param gain amplitude multiplier per octave (typically 0.5)
     * @return noise value in range [-1.0, 1.0]
     */
    public static double fbm2(double x, double z, long seed, int octaves, double lacunarity, double gain) {
        double total = 0.0;
        double amplitude = 1.0;
        double maxAmplitude = 0.0;
        double curX = x;
        double curZ = z;

        for (int i = 0; i < octaves; i++) {
            total += simplex2(curX, curZ, seed + i * 1013L) * amplitude;
            maxAmplitude += amplitude;
            curX = curX * lacunarity + 17.17;
            curZ = curZ * lacunarity - 11.31;
            amplitude *= gain;
        }

        return maxAmplitude > 0.0 ? total / maxAmplitude : 0.0;
    }

    public static double smoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double hashUnit01(long seed, int salt) {
        long h = seed + 0x9E3779B97F4A7C15L * salt;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return ((h ^ (h >>> 31)) >>> 11) * 0x1.0p-53;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private static int hashCoords(int x, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return (int) ((h ^ (h >>> 31)) & 0x7FFFFFFF);
    }
}
