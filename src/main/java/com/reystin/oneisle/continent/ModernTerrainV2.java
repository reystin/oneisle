package com.reystin.oneisle.continent;

import java.util.Arrays;

/**
 * Modern terrain generator (v2) for OneIsle 0.6.0+.
 * Features:
 * - Exact Felzenszwalb-Huttenlocher EDT on native grid with sub-pixel distance convention.
 * - Flat float[] cached SDF grid with optional 3x3 Gaussian smoothing.
 * - Catmull-Rom bicubic interpolation on the continuous SDF field with 4x4 neighbourhood clamping.
 * - Domain warping on mask lookup only (frequency 1/520, amplitude 45; octave 2 at 1/190, amplitude 12).
 * - Near-coast micro-roughness centered at shoreline -0.08 with smoothstep falloff.
 * - Ocean bathymetry with soft floor at -0.80, seabed noise (fBm + ridged trenches), and guaranteed deep ocean band.
 * - v3: smooth depth ramp reaching the deep threshold before 0.4*Wo, no positive seabed noise
 *   inside the [0.4*Wo, 1.0*Wo] monument band, and islets placed beyond 1.0*Wo.
 * - Satellite islets with jittered cellular placement, 350-block margin from main island, and density slider.
 * - Procedural ridged mountain spines with headroom and tanh soft saturation (stops the cone).
 * - 4-tier height layer support with blurred sampling.
 * - Pure, thread-safe, allocation-free evaluation.
 */
public final class ModernTerrainV2 implements IslandTerrain {
    private static final double INF = 1.0e20;

    private final IslandShapeConfig config;
    private final IslandMask mask;
    private final int width;
    private final int height;
    private final double minX;
    private final double maxX;
    private final double minZ;
    private final double maxZ;
    private final double coastWidth;
    private final double oceanTransitionWidth;
    private final double blocksPerPixel;
    private final long seed;
    private final double coastWarp;
    private final double isletDensity;
    private final double isletClearance;
    private final boolean v3Ocean;
    private final double v3WarpScale;
    private final ShoreSettings shoreSettings;

    private final float[] sdfGrid;
    private final float[] mainSdfGrid;
    private final float[] heightGrid;
    private final IslandRivers rivers;
    private volatile ClimateLayout climateLayout;

    public ModernTerrainV2(IslandShapeConfig config) {
        this.config = config;
        this.mask = config != null && config.mask != null ? config.mask : new IslandMask(192, 192, -2720, 2720, -2720, 2720, 192.0, 400.0, "");
        this.width = this.mask.width;
        this.height = this.mask.height;
        this.minX = this.mask.minX;
        this.maxX = this.mask.maxX;
        this.minZ = this.mask.minZ;
        this.maxZ = this.mask.maxZ;
        this.coastWidth = this.mask.coastWidth;
        this.oceanTransitionWidth = this.mask.oceanTransitionWidth;
        this.blocksPerPixel = 0.5 * ((maxX - minX) / width + (maxZ - minZ) / height);
        this.seed = config != null && config.biomeSeed != 0L ? config.biomeSeed : 0x5EED1234ABCDL;
        this.coastWarp = 45.0;
        this.isletDensity = config != null ? config.isletDensity : 0.25;
        this.v3Ocean = config != null && config.generationVersion >= 3;
        // v3 keeps coast warp and roughness inside the editor-pixel displacement budget so
        // the generated shoreline follows the painted mask. The quadratic size factor
        // reduces warp on sub-4000 masks, where one editor pixel is fewer blocks and the
        // same block-space wobble costs more pixels. v2 math stays untouched (I1).
        double v3SizeRatio = blocksPerPixel / 28.3333;
        this.v3WarpScale = v3Ocean
            ? Math.max(0.15, Math.min(1.0, 0.5 * v3SizeRatio * v3SizeRatio))
            : 1.0;
        // 80 blocks covers the maximum domain warp (45 + 12 amplitudes) so islets placed
        // by unwarped distance still start beyond 1.0*Wo in the warped field.
        this.isletClearance = v3Ocean ? this.oceanTransitionWidth + 80.0 : 350.0;
        this.shoreSettings = config != null && config.generationVersion >= 3 ? config.shore : null;

        this.sdfGrid = buildSdfGrid();
        this.mainSdfGrid = v3Ocean ? buildSdfGrid(largestLandComponent()) : null;
        this.heightGrid = buildHeightGrid(config != null ? config.unpackedHeight : null);
        this.rivers = new IslandRivers(config, this.mask, this.sdfGrid, this.heightGrid);
    }

    public IslandShapeConfig config() {
        return config;
    }

    public IslandMask mask() {
        return mask;
    }

    public float[] sdfGrid() {
        return sdfGrid;
    }

    public double isletDensity() {
        return isletDensity;
    }

    @Override
    public IslandRivers rivers() {
        return rivers;
    }

    @Override
    public Distances distances(double worldX, double worldZ) {
        double signed = computeSignedDistance(worldX, worldZ, true);
        double main = mainSdfGrid != null
            ? computeSignedDistance(worldX, worldZ, true, mainSdfGrid) : signed;

        // Check if inside any satellite islet
        if (signed < 0.0 && isletDensity > 0.0) {
            double isletDist = sampleSatelliteIsletDistance(worldX, worldZ);
            if (isletDist > signed) {
                signed = isletDist;
            }
        }

        return new Distances(signed, main);
    }

    @Override
    public double signedDistance(double worldX, double worldZ) {
        return distances(worldX, worldZ).signed();
    }

    @Override
    public double heightTierAt(double worldX, double worldZ) {
        if (heightGrid == null) return 1.0;
        double px = ((Math.max(minX, Math.min(maxX, worldX)) - minX) / (maxX - minX)) * width - 0.5;
        double pz = ((Math.max(minZ, Math.min(maxZ, worldZ)) - minZ) / (maxZ - minZ)) * height - 0.5;
        return sampleHeight(px, pz);
    }

    @Override
    public ClimateLayout climateLayout() {
        if (!v3Ocean) return null;
        ClimateLayout local = climateLayout;
        if (local == null) {
            synchronized (this) {
                local = climateLayout;
                if (local == null) {
                    local = new ClimateLayout(mask, seed);
                    climateLayout = local;
                }
            }
        }
        return local;
    }

    /** Physical ridged spine shared by terrain relief and climate mountain bias. */
    public static double physicalRidge(double worldX, double worldZ, long seed) {
        return 1.0 - 2.0 * Math.abs(IslandNoise.fbm2(
            worldX / 750.0, worldZ / 750.0, seed + 801L, 3, 2.0, 0.5));
    }

    @Override
    public double continentalness(double worldX, double worldZ) {
        double worldDistance = computeSignedDistance(worldX, worldZ, true);

        if (worldDistance >= 0.0) {
            // Land side
            double d = worldDistance;
            double reliefScale = Math.max(0.5, Math.min(1.5, 192.0 / coastWidth));
            double x = d / coastWidth;
            double baseUnclamped = -0.08 + 0.48 * Math.tanh(x * 1.25);

            // Height layer sampling
            double px = ((Math.max(minX, Math.min(maxX, worldX)) - minX) / (maxX - minX)) * width - 0.5;
            double pz = ((Math.max(minZ, Math.min(maxZ, worldZ)) - minZ) / (maxZ - minZ)) * height - 0.5;
            double tierVal = sampleHeight(px, pz);

            double tierOffset;
            double mountainBias;
            if (tierVal <= 1.0) {
                double u = Math.max(0.0, tierVal);
                tierOffset = -0.15 * (1.0 - u);
                mountainBias = 0.3 + 0.7 * u;
            } else if (tierVal <= 2.0) {
                double u = tierVal - 1.0;
                tierOffset = 0.18 * u;
                mountainBias = 1.0 + 0.3 * u;
            } else {
                double u = Math.min(1.0, tierVal - 2.0);
                tierOffset = 0.18 + 0.22 * u;
                mountainBias = 1.3 + 0.4 * u;
            }

            // Procedural ridged mountain spines
            double inlandFactor = IslandNoise.smoothstep((d - 60.0) / 190.0);
            double ridge = physicalRidge(worldX, worldZ, seed);
            double ridgeTerm = ridge * 0.22 * inlandFactor * mountainBias * reliefScale;

            double cTotal = baseUnclamped + ridgeTerm + tierOffset;

            // Soft saturation instead of hard clamp
            double c;
            if (cTotal > 0.55) {
                c = 0.55 + 0.40 * Math.tanh((cTotal - 0.55) / 0.40);
            } else {
                c = cTotal;
            }

            // Near-coast micro-roughness centered at shoreline -0.08
            double diff = Math.abs(c - (-0.08));
            if (diff < 0.22) {
                double t = 1.0 - diff / 0.22;
                double roughness = IslandNoise.fbm2(worldX / 72.0, worldZ / 72.0, seed + 911L, 3, 2.0, 0.5)
                    * 0.045 * v3WarpScale * IslandNoise.smoothstep(t);
                c += roughness;
            }

            // v3 shore terrain: flatten beach pockets, slightly steepen no-beach coasts.
            // Hills keep their relief: the shore outcome is gated to the same low-tier band.
            if (shoreSettings != null && shoreSettings.enabled() && d < 52.0 && tierVal <= 1.05) {
                double patch = ShoreField.patch(worldX, worldZ, shoreSettings, seed);
                double beachMask = 0.0;
                if (patch > 0.0) {
                    double width = ShoreField.inlandWidth(worldX, worldZ, shoreSettings, seed, patch);
                    beachMask = (1.0 - IslandNoise.smoothstep((d - width + 6.0) / 16.0))
                        * IslandNoise.smoothstep(d / 4.0);
                }
                double rockyMask = (1.0 - patch) * (1.0 - IslandNoise.smoothstep((d - 6.0) / 32.0));
                double relief = c + 0.08;
                relief *= 1.0 - 0.38 * beachMask;
                relief *= 1.0 + 0.18 * rockyMask;
                c = -0.08 + relief;
            }

            if (rivers != null && rivers.isEnabled()) {
                double riverCarve = rivers.riverCarveContinentalness(worldX, worldZ);
                if (riverCarve > 0.0) {
                    c -= riverCarve;
                }
            }

            return Math.max(-1.0, Math.min(0.95, c));
        }

        // Ocean bathymetry
        double s = -worldDistance; // offshore distance in blocks
        double wo = oceanTransitionWidth;
        double base;
        if (v3Ocean) {
            // Reach the deep band by 0.4*Wo (-0.62 at that point), then settle onto the
            // -0.80 soft floor by Wo. Zero derivative at the coast keeps c(s=0) = -0.08.
            base = -0.08
                - 0.54 * IslandNoise.smoothstep(s / (0.4 * wo))
                - 0.18 * IslandNoise.smoothstep(s / wo);
        } else {
            base = -0.08 - 0.92 * (s / wo);
        }
        double cBase = base;

        // Near-coast micro-roughness on the water side of the shoreline
        double diff = Math.abs(cBase - (-0.08));
        if (diff < 0.22) {
            double t = 1.0 - diff / 0.22;
            double roughness = IslandNoise.fbm2(worldX / 72.0, worldZ / 72.0, seed + 911L, 3, 2.0, 0.5)
                * 0.045 * v3WarpScale * IslandNoise.smoothstep(t);
            cBase += roughness;
        }

        // v3 only: the carve must cross the waterline, or a river mouth becomes a
        // 0.14-continentalness step exactly at the land/ocean branch switch.
        if (v3Ocean && rivers != null && rivers.isEnabled()) {
            double riverCarve = rivers.riverCarveContinentalness(worldX, worldZ);
            if (riverCarve > 0.0) {
                cBase -= riverCarve;
            }
        }

        double b = Math.max(cBase, -0.80); // soft floor at -0.80

        double w = IslandNoise.smoothstep(s / (0.5 * wo));
        double fbmBed = IslandNoise.fbm2(worldX / 1100.0, worldZ / 1100.0, seed + 555L, 3, 2.0, 0.5);
        double trench = 1.0 - Math.abs(IslandNoise.fbm2(worldX / 450.0, worldZ / 450.0, seed + 777L, 2, 2.0, 0.5));

        // Seamounts (positive seabed noise) are forbidden inside the monument band
        // [0.4*Wo, 1.0*Wo] and fade in only beyond 1.0*Wo.
        double seamountFade = s > 1.0 * wo ? IslandNoise.smoothstep((s - 1.0 * wo) / (0.3 * wo)) : 0.0;
        double positiveBed = Math.max(0.0, fbmBed * 0.18) * seamountFade;
        double negativeBed = Math.min(0.0, fbmBed * 0.18) - trench * 0.12;
        double seabed = positiveBed + negativeBed;

        double cOcean = Math.max(-1.0, Math.min(0.70, b + w * seabed));

        // Satellite islets (density slider in [0, 1])
        if (isletDensity > 0.0) {
            cOcean = sampleSatelliteIslets(worldX, worldZ, cOcean);
        }

        return cOcean;
    }

    private double sampleHeight(double px, double pz) {
        if (heightGrid == null) return 1.0;
        px = Math.max(0.0, Math.min(width - 1.0, px));
        pz = Math.max(0.0, Math.min(height - 1.0, pz));
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        int x1 = Math.min(width - 1, x0 + 1);
        int z1 = Math.min(height - 1, z0 + 1);
        double tx = px - x0;
        double tz = pz - z0;
        double a = lerp(heightGrid[z0 * width + x0], heightGrid[z0 * width + x1], tx);
        double b = lerp(heightGrid[z1 * width + x0], heightGrid[z1 * width + x1], tx);
        return lerp(a, b, tz);
    }

    private float[] buildHeightGrid(byte[] rawHeight) {
        if (rawHeight == null || rawHeight.length != width * height) return null;

        // Separable 7-tap binomial blur [1, 6, 15, 20, 15, 6, 1] / 64 (radius ~1.5 px)
        float[] temp = new float[width * height];
        for (int z = 0; z < height; z++) {
            int row = z * width;
            for (int x = 0; x < width; x++) {
                float sum = 0.0f;
                sum += 1.0f * (rawHeight[row + Math.max(0, x - 3)] & 3);
                sum += 6.0f * (rawHeight[row + Math.max(0, x - 2)] & 3);
                sum += 15.0f * (rawHeight[row + Math.max(0, x - 1)] & 3);
                sum += 20.0f * (rawHeight[row + x] & 3);
                sum += 15.0f * (rawHeight[row + Math.min(width - 1, x + 1)] & 3);
                sum += 6.0f * (rawHeight[row + Math.min(width - 1, x + 2)] & 3);
                sum += 1.0f * (rawHeight[row + Math.min(width - 1, x + 3)] & 3);
                temp[row + x] = sum / 64.0f;
            }
        }

        float[] result = new float[width * height];
        for (int z = 0; z < height; z++) {
            int zPrev3 = Math.max(0, z - 3) * width;
            int zPrev2 = Math.max(0, z - 2) * width;
            int zPrev1 = Math.max(0, z - 1) * width;
            int zCur = z * width;
            int zNext1 = Math.min(height - 1, z + 1) * width;
            int zNext2 = Math.min(height - 1, z + 2) * width;
            int zNext3 = Math.min(height - 1, z + 3) * width;
            for (int x = 0; x < width; x++) {
                float sum = 0.0f;
                sum += 1.0f * temp[zPrev3 + x];
                sum += 6.0f * temp[zPrev2 + x];
                sum += 15.0f * temp[zPrev1 + x];
                sum += 20.0f * temp[zCur + x];
                sum += 15.0f * temp[zNext1 + x];
                sum += 6.0f * temp[zNext2 + x];
                sum += 1.0f * temp[zNext3 + x];
                result[zCur + x] = sum / 64.0f;
            }
        }

        return result;
    }

    private double sampleSatelliteIslets(double worldX, double worldZ, double cOcean) {
        int cellX = fastFloor(worldX / 800.0);
        int cellZ = fastFloor(worldZ / 800.0);
        double maxC = cOcean;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long cellSeed = seed ^ ((long) cx * 0x4B372819L + (long) cz * 0x9E3779B9L);
                double p = 0.15 + 0.5 * isletDensity;
                if (IslandNoise.hashUnit01(cellSeed, 1) < p) {
                    double centerX = (cx + 0.15 + IslandNoise.hashUnit01(cellSeed, 2) * 0.70) * 800.0;
                    double centerZ = (cz + 0.15 + IslandNoise.hashUnit01(cellSeed, 3) * 0.70) * 800.0;

                    double r = 40.0 + IslandNoise.hashUnit01(cellSeed, 4) * 120.0;
                    // Islet discs must stay outside the islet clearance (350 blocks in legacy
                    // versions; the full ocean transition width in v3 so the monument band is clean).
                    double centerDist = computeSignedDistance(centerX, centerZ, false);
                    if (centerDist > -(isletClearance + r)) continue;

                    double dist = Math.hypot(worldX - centerX, worldZ - centerZ);
                    if (dist < r) {
                        double t = dist / r;
                        double isletC;
                        if (t <= 0.55) {
                            double u = (0.55 - t) / 0.55;
                            isletC = -0.08 + 0.33 * (u * u * (3.0 - 2.0 * u));
                        } else {
                            double v = (t - 0.55) / 0.45;
                            isletC = -0.08 - 0.92 * (v * v * (3.0 - 2.0 * v));
                        }
                        if (isletC > maxC) {
                            maxC = isletC;
                        }
                    }
                }
            }
        }
        return maxC;
    }

    private double sampleSatelliteIsletDistance(double worldX, double worldZ) {
        int cellX = fastFloor(worldX / 800.0);
        int cellZ = fastFloor(worldZ / 800.0);
        double maxDist = Double.NEGATIVE_INFINITY;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long cellSeed = seed ^ ((long) cx * 0x4B372819L + (long) cz * 0x9E3779B9L);
                double p = 0.15 + 0.5 * isletDensity;
                if (IslandNoise.hashUnit01(cellSeed, 1) < p) {
                    double centerX = (cx + 0.15 + IslandNoise.hashUnit01(cellSeed, 2) * 0.70) * 800.0;
                    double centerZ = (cz + 0.15 + IslandNoise.hashUnit01(cellSeed, 3) * 0.70) * 800.0;

                    double r = 40.0 + IslandNoise.hashUnit01(cellSeed, 4) * 120.0;
                    double centerDist = computeSignedDistance(centerX, centerZ, false);
                    if (centerDist > -(isletClearance + r)) continue;

                    double dist = Math.hypot(worldX - centerX, worldZ - centerZ);
                    if (dist < r) {
                        double shoreDist = (0.55 - dist / r) * r;
                        if (shoreDist > maxDist) {
                            maxDist = shoreDist;
                        }
                    }
                }
            }
        }
        return maxDist;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    @Override
    public double erosionBias(double x, double z) {
        double worldDistance = computeSignedDistance(x, z, true);
        if (worldDistance < 0.0) {
            return 0.0;
        }
        double bias = 0.0;
        if (heightGrid != null) {
            double px = ((Math.max(minX, Math.min(maxX, x)) - minX) / (maxX - minX)) * width - 0.5;
            double pz = ((Math.max(minZ, Math.min(maxZ, z)) - minZ) / (maxZ - minZ)) * height - 0.5;
            double tierVal = sampleHeight(px, pz);
            if (tierVal <= 1.0) {
                double u = Math.max(0.0, tierVal);
                bias = 0.50 * (1.0 - u);
            } else if (tierVal <= 2.0) {
                double u = tierVal - 1.0;
                bias = -0.20 * u;
            } else {
                double u = Math.min(1.0, tierVal - 2.0);
                bias = -0.20 - 0.45 * u;
            }
        }
        if (rivers != null && rivers.isEnabled()) {
            bias += rivers.riverCarveErosionBias(x, z);
        }
        return Math.max(-1.0, Math.min(1.0, bias));
    }

    /**
     * Computes signed distance in blocks.
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @param applyWarp whether to apply low-frequency domain warping
     * @return signed distance in blocks (> 0 inland, < 0 offshore)
     */
    public double computeSignedDistance(double worldX, double worldZ, boolean applyWarp) {
        return computeSignedDistance(worldX, worldZ, applyWarp, sdfGrid);
    }

    private double computeSignedDistance(double worldX, double worldZ, boolean applyWarp, float[] grid) {
        double targetX = worldX;
        double targetZ = worldZ;

        if (applyWarp && coastWarp > 0.0) {
            double warpFactor = coastWarp / 45.0 * v3WarpScale;
            double wx = (IslandNoise.fbm2(worldX / 520.0, worldZ / 520.0, seed + 101L, 3, 2.0, 0.5) * 45.0
                + IslandNoise.fbm2(worldX / 190.0, worldZ / 190.0, seed + 307L, 2, 2.0, 0.5) * 12.0) * warpFactor;
            double wz = (IslandNoise.fbm2(worldX / 520.0, worldZ / 520.0, seed + 211L, 3, 2.0, 0.5) * 45.0
                + IslandNoise.fbm2(worldX / 190.0, worldZ / 190.0, seed + 401L, 2, 2.0, 0.5) * 12.0) * warpFactor;
            targetX += wx;
            targetZ += wz;
        }

        double sampledX = Math.max(minX, Math.min(maxX, targetX));
        double sampledZ = Math.max(minZ, Math.min(maxZ, targetZ));

        double px = (sampledX - minX) / (maxX - minX) * width - 0.5;
        double pz = (sampledZ - minZ) / (maxZ - minZ) * height - 0.5;

        double pixelDistance = sampleBicubicClamped(px, pz, grid);
        double worldDistance = pixelDistance * blocksPerPixel;

        double outsideX = targetX - sampledX;
        double outsideZ = targetZ - sampledZ;
        if (outsideX != 0.0 || outsideZ != 0.0) {
            double existingOceanDistance = Math.max(0.0, -worldDistance);
            worldDistance = -Math.sqrt(existingOceanDistance * existingOceanDistance
                + outsideX * outsideX + outsideZ * outsideZ);
        }

        return worldDistance;
    }

    /**
     * Catmull-Rom bicubic interpolation on the SDF grid with 4x4 neighbourhood clamping.
     */
    public double sampleBicubicClamped(double px, double pz) {
        return sampleBicubicClamped(px, pz, sdfGrid);
    }

    private double sampleBicubicClamped(double px, double pz, float[] grid) {
        px = Math.max(0.0, Math.min(width - 1.0, px));
        pz = Math.max(0.0, Math.min(height - 1.0, pz));

        int x1 = (int) Math.floor(px);
        int z1 = (int) Math.floor(pz);
        double tx = px - x1;
        double tz = pz - z1;

        int x0 = Math.max(0, x1 - 1);
        int x2 = Math.min(width - 1, x1 + 1);
        int x3 = Math.min(width - 1, x1 + 2);

        int z0 = Math.max(0, z1 - 1);
        int z2 = Math.min(height - 1, z1 + 1);
        int z3 = Math.min(height - 1, z1 + 2);

        float minVal = Float.POSITIVE_INFINITY;
        float maxVal = Float.NEGATIVE_INFINITY;

        int[] xs = {x0, x1, x2, x3};
        int[] zs = {z0, z1, z2, z3};

        for (int z : zs) {
            int row = z * width;
            for (int x : xs) {
                float v = grid[row + x];
                if (v < minVal) minVal = v;
                if (v > maxVal) maxVal = v;
            }
        }

        double r0 = catmullRom1D(grid[z0 * width + x0], grid[z0 * width + x1], grid[z0 * width + x2], grid[z0 * width + x3], tx);
        double r1 = catmullRom1D(grid[z1 * width + x0], grid[z1 * width + x1], grid[z1 * width + x2], grid[z1 * width + x3], tx);
        double r2 = catmullRom1D(grid[z2 * width + x0], grid[z2 * width + x1], grid[z2 * width + x2], grid[z2 * width + x3], tx);
        double r3 = catmullRom1D(grid[z3 * width + x0], grid[z3 * width + x1], grid[z3 * width + x2], grid[z3 * width + x3], tx);

        double val = catmullRom1D(r0, r1, r2, r3, tz);
        return Math.max(minVal, Math.min(maxVal, val));
    }

    /**
     * Bilinear interpolation on SDF grid (for parity / sign consistency tests).
     */
    public double sampleBilinear(double px, double pz) {
        px = Math.max(0.0, Math.min(width - 1.0, px));
        pz = Math.max(0.0, Math.min(height - 1.0, pz));
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        int x1 = Math.min(width - 1, x0 + 1);
        int z1 = Math.min(height - 1, z0 + 1);
        double tx = px - x0;
        double tz = pz - z0;
        double a = lerp(sdfGrid[z0 * width + x0], sdfGrid[z0 * width + x1], tx);
        double b = lerp(sdfGrid[z1 * width + x0], sdfGrid[z1 * width + x1], tx);
        return lerp(a, b, tz);
    }

    private static double catmullRom1D(double p0, double p1, double p2, double p3, double t) {
        double c0 = p1;
        double c1 = 0.5 * (p2 - p0);
        double c2 = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3;
        double c3 = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3;
        return ((c3 * t + c2) * t + c1) * t + c0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private float[] buildSdfGrid() {
        return buildSdfGrid(null);
    }

    private float[] buildSdfGrid(boolean[] sourceLand) {
        int paddedWidth = width + 2;
        int paddedHeight = height + 2;
        boolean[] padded = new boolean[paddedWidth * paddedHeight];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                boolean isLand = sourceLand != null ? sourceLand[z * width + x] : mask.isLand(x, z);
                if (isLand) {
                    padded[(z + 1) * paddedWidth + (x + 1)] = true;
                }
            }
        }

        double[] toLand = distanceTransform(padded, paddedWidth, paddedHeight, true);
        double[] toOcean = distanceTransform(padded, paddedWidth, paddedHeight, false);

        float[] rawSdf = new float[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int source = (z + 1) * paddedWidth + x + 1;
                int target = z * width + x;
                boolean isLand = sourceLand != null ? sourceLand[target] : mask.isLand(x, z);
                if (isLand) {
                    double dOut = Math.sqrt(toOcean[source]);
                    rawSdf[target] = (float) Math.max(0.0, dOut - 0.5);
                } else {
                    double dIn = Math.sqrt(toLand[source]);
                    rawSdf[target] = (float) -Math.max(0.0, dIn - 0.5);
                }
            }
        }

        // 3x3 Gaussian smoothing pass (separable [0.25, 0.5, 0.25])
        float[] temp = new float[width * height];
        for (int z = 0; z < height; z++) {
            int row = z * width;
            for (int x = 0; x < width; x++) {
                int xPrev = Math.max(0, x - 1);
                int xNext = Math.min(width - 1, x + 1);
                temp[row + x] = 0.25f * rawSdf[row + xPrev] + 0.5f * rawSdf[row + x] + 0.25f * rawSdf[row + xNext];
            }
        }

        float[] smoothed = new float[width * height];
        for (int z = 0; z < height; z++) {
            int zPrev = Math.max(0, z - 1) * width;
            int zCur = z * width;
            int zNext = Math.min(height - 1, z + 1) * width;
            for (int x = 0; x < width; x++) {
                smoothed[zCur + x] = 0.25f * temp[zPrev + x] + 0.5f * temp[zCur + x] + 0.25f * temp[zNext + x];
            }
        }

        return smoothed;
    }

    private boolean[] largestLandComponent() {
        int cells = width * height;
        boolean[] visited = new boolean[cells];
        boolean[] current = new boolean[cells];
        boolean[] best = new boolean[cells];
        int[] queue = new int[cells];
        int bestSize = 0;
        for (int start = 0; start < cells; start++) {
            if (visited[start] || !mask.isLand(start % width, start / width)) continue;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            int size = 0;
            while (head < tail) {
                int index = queue[head++];
                current[index] = true;
                size++;
                int x = index % width;
                int z = index / width;
                if (x > 0) tail = enqueueLand(index - 1, visited, queue, tail);
                if (x + 1 < width) tail = enqueueLand(index + 1, visited, queue, tail);
                if (z > 0) tail = enqueueLand(index - width, visited, queue, tail);
                if (z + 1 < height) tail = enqueueLand(index + width, visited, queue, tail);
            }
            if (size > bestSize) {
                bestSize = size;
                System.arraycopy(current, 0, best, 0, cells);
            }
            Arrays.fill(current, false);
        }
        return best;
    }

    private int enqueueLand(int index, boolean[] visited, int[] queue, int tail) {
        if (visited[index] || !mask.isLand(index % width, index / width)) return tail;
        visited[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private static double[] distanceTransform(boolean[] mask, int width, int height, boolean targetValue) {
        double[] first = new double[width * height];
        double[] output = new double[width * height];
        double[] input = new double[Math.max(width, height)];
        double[] transformed = new double[Math.max(width, height)];

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) input[x] = mask[z * width + x] == targetValue ? 0.0 : INF;
            edt1d(input, transformed, width);
            System.arraycopy(transformed, 0, first, z * width, width);
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) input[z] = first[z * width + x];
            edt1d(input, transformed, height);
            for (int z = 0; z < height; z++) output[z * width + x] = transformed[z];
        }
        return output;
    }

    private static void edt1d(double[] input, double[] output, int size) {
        int firstFinite = -1;
        for (int i = 0; i < size; i++) {
            if (input[i] < INF) { firstFinite = i; break; }
        }
        if (firstFinite < 0) {
            for (int i = 0; i < size; i++) output[i] = INF;
            return;
        }
        int[] v = new int[size];
        double[] z = new double[size + 1];
        int k = 0;
        v[0] = firstFinite;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = firstFinite + 1; q < size; q++) {
            if (input[q] >= INF) continue;
            double intersection;
            do {
                int vk = v[k];
                intersection = ((input[q] + q * (double)q) - (input[vk] + vk * (double)vk)) / (2.0 * (q - vk));
                if (intersection <= z[k]) k--;
            } while (intersection <= z[k]);
            k++;
            v[k] = q;
            z[k] = intersection;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < size; q++) {
            while (z[k + 1] < q) k++;
            double delta = q - v[k];
            output[q] = delta * delta + input[v[k]];
        }
    }
}
