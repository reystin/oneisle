package com.hack337.omniisle.continent;

import java.util.Arrays;

/**
 * Deterministic physically carved rivers and lakes for OmniIsle (v2).
 * Derived at world load from (mask, height layer, seed, riverDensity):
 * 1. Coarse surface height field from uncarved terrain sampler (SDF + ridges + tiers).
 * 2. Priority-Flood depression filling to remove pits and identify lakes.
 * 3. D8 flow routing towards the ocean and flow accumulation.
 * 4. River channel extraction where accumulation >= threshold.
 * 5. Multi-source distance transform to produce continuous distance-to-river dr(x, z).
 * 6. Smooth carving into continentalness and erosion bias, and river biome selection.
 *
 * Deterministic, allocation-free in query paths, thread-safe.
 */
public final class IslandRivers {
    private final boolean enabled;
    private final int resolution;
    private final double minX;
    private final double maxX;
    private final double minZ;
    private final double maxZ;
    private final double blocksPerPixel;

    private final float[] distToRiver;    // In pixels
    private final float[] channelWidth;   // In blocks
    private final boolean[] isLake;       // Grid lake flag

    public IslandRivers(IslandShapeConfig config, IslandMask mask, float[] sdfGrid, float[] heightGrid) {
        if (config == null || config.generationVersion <= 1 || config.riverDensity <= 0.001 || mask == null || sdfGrid == null) {
            this.enabled = false;
            this.resolution = 192;
            this.minX = 0;
            this.maxX = 0;
            this.minZ = 0;
            this.maxZ = 0;
            this.blocksPerPixel = 1.0;
            this.distToRiver = null;
            this.channelWidth = null;
            this.isLake = null;
            return;
        }

        this.enabled = true;
        this.resolution = mask.resolution > 0 ? mask.resolution : 192;
        this.minX = mask.minX;
        this.maxX = mask.maxX;
        this.minZ = mask.minZ;
        this.maxZ = mask.maxZ;
        int res = this.resolution;
        this.blocksPerPixel = 0.5 * ((maxX - minX) / res + (maxZ - minZ) / res);

        float[] rawHeight = new float[res * res];
        boolean[] isLand = new boolean[res * res];
        long seed = config.biomeSeed != 0L ? config.biomeSeed : 0x5EED1234ABCDL;
        double coastWidth = mask.coastWidth > 0 ? mask.coastWidth : 192.0;

        // 1. Coarse height estimation
        for (int pz = 0; pz < res; pz++) {
            for (int px = 0; px < res; px++) {
                int idx = pz * res + px;
                boolean land = mask.isLand(px, pz);
                isLand[idx] = land;
                if (!land) {
                    rawHeight[idx] = -1.0f;
                } else {
                    double d = sdfGrid[idx] * blocksPerPixel;
                    double x = d / coastWidth;
                    double base = -0.08 + 0.48 * Math.tanh(x * 1.25);
                    double wx = minX + (px + 0.5) * (maxX - minX) / res;
                    double wz = minZ + (pz + 0.5) * (maxZ - minZ) / res;
                    double ridge = 1.0 - 2.0 * Math.abs(simplexFbm(wx / 750.0, wz / 750.0, seed));
                    float tierWeight = heightGrid != null ? heightGrid[idx] : 1.0f;
                    double tierOffset = (tierWeight - 1.0f) * 0.25;
                    rawHeight[idx] = (float) (base + tierOffset + 0.15 * ridge);
                }
            }
        }

        // 2. Priority-Flood depression filling
        float[] filled = rawHeight.clone();
        int[] drainTarget = new int[res * res];
        Arrays.fill(drainTarget, -1);
        boolean[] inQueue = new boolean[res * res];

        MinHeap heap = new MinHeap(res * res, filled);

        // Seed heap with boundary and ocean cells
        for (int pz = 0; pz < res; pz++) {
            for (int px = 0; px < res; px++) {
                int idx = pz * res + px;
                if (px == 0 || px == res - 1 || pz == 0 || pz == res - 1 || !isLand[idx] || rawHeight[idx] <= -0.05f) {
                    inQueue[idx] = true;
                    heap.push(idx);
                }
            }
        }

        int[] dx = {1, -1, 0, 0, 1, -1, 1, -1};
        int[] dz = {0, 0, 1, -1, 1, -1, -1, 1};

        while (!heap.isEmpty()) {
            int u = heap.poll();
            int ux = u % res;
            int uz = u / res;
            float hu = filled[u];

            for (int dir = 0; dir < 8; dir++) {
                int vx = ux + dx[dir];
                int vz = uz + dz[dir];
                if (vx < 0 || vx >= res || vz < 0 || vz >= res) continue;
                int v = vz * res + vx;
                if (!inQueue[v]) {
                    inQueue[v] = true;
                    if (filled[v] < hu) {
                        filled[v] = hu;
                    }
                    drainTarget[v] = u;
                    heap.push(v);
                }
            }
        }

        // 3. Detect Lakes
        this.isLake = new boolean[res * res];
        for (int i = 0; i < res * res; i++) {
            if (isLand[i] && (filled[i] - rawHeight[i]) >= 0.045f) {
                this.isLake[i] = true;
            }
        }

        // 4. Flow Accumulation
        int landCount = 0;
        for (int i = 0; i < res * res; i++) {
            if (isLand[i]) landCount++;
        }
        int[] landIndices = new int[landCount];
        int pos = 0;
        for (int i = 0; i < res * res; i++) {
            if (isLand[i]) landIndices[pos++] = i;
        }

        // Sort land descending by filled height
        sortDescending(landIndices, filled, 0, landCount - 1);

        int[] acc = new int[res * res];
        Arrays.fill(acc, 1);
        for (int v : landIndices) {
            int target = drainTarget[v];
            if (target >= 0 && target != v) {
                acc[target] += acc[v];
            }
        }

        // 5. River mask and channel widths
        double density = Math.max(0.01, Math.min(1.0, config.riverDensity));
        // v2's threshold marks only the largest inland trunks and lakes; v3 scales it to the
        // observed drainage so channels reach the waterline and real river mouths exist.
        double thresholdFactor = config.generationVersion >= 3
            ? 0.0004 + (1.0 - density) * 0.0040
            : 0.003 + (1.0 - density) * 0.022;
        int threshold = (int) (res * res * thresholdFactor);
        boolean[] isRiverCell = new boolean[res * res];
        this.channelWidth = new float[res * res];

        int riverCount = 0;
        for (int i = 0; i < res * res; i++) {
            if (isLand[i] && (acc[i] >= threshold || isLake[i])) {
                isRiverCell[i] = true;
                double w = 6.0 + 16.0 * Math.min(1.0, Math.sqrt((double) acc[i] / (res * res * 0.05)));
                this.channelWidth[i] = (float) w;
                riverCount++;
            }
        }

        // 6. Multi-source distance propagation (up to 12 pixels)
        this.distToRiver = new float[res * res];
        Arrays.fill(this.distToRiver, 999.0f);

        int[] q = new int[res * res];
        int qHead = 0;
        int qTail = 0;

        for (int i = 0; i < res * res; i++) {
            if (isRiverCell[i]) {
                distToRiver[i] = 0.0f;
                q[qTail++] = i;
            }
        }

        float[] dDist = {1.0f, 1.0f, 1.0f, 1.0f, 1.4142f, 1.4142f, 1.4142f, 1.4142f};
        while (qHead < qTail) {
            int u = q[qHead++];
            int ux = u % res;
            int uz = u / res;
            float du = distToRiver[u];
            if (du >= 12.0f) continue;

            for (int dir = 0; dir < 8; dir++) {
                int vx = ux + dx[dir];
                int vz = uz + dz[dir];
                if (vx < 0 || vx >= res || vz < 0 || vz >= res) continue;
                int v = vz * res + vx;
                float nd = du + dDist[dir];
                if (nd < distToRiver[v]) {
                    distToRiver[v] = nd;
                    if (channelWidth[v] == 0.0f) {
                        channelWidth[v] = channelWidth[u];
                    }
                    q[qTail++] = v;
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRiverPixel(int px, int pz) {
        if (!enabled || px < 0 || px >= resolution || pz < 0 || pz >= resolution) return false;
        int idx = pz * resolution + px;
        return isLake[idx] || distToRiver[idx] <= 0.6f;
    }

    public boolean isRiver(double worldX, double worldZ) {
        if (!enabled) return false;
        double gx = (worldX - minX) / (maxX - minX) * resolution - 0.5;
        double gz = (worldZ - minZ) / (maxZ - minZ) * resolution - 0.5;

        float distPx = sampleBilinear(distToRiver, gx, gz);
        if (distPx >= 12.0f) return false;
        float widthBlocks = sampleBilinear(channelWidth, gx, gz);
        if (widthBlocks <= 0.0f) return false;
        double dr = distPx * blocksPerPixel;
        return dr < widthBlocks * 0.65;
    }

    public double riverCarveContinentalness(double worldX, double worldZ) {
        if (!enabled) return 0.0;
        double gx = (worldX - minX) / (maxX - minX) * resolution - 0.5;
        double gz = (worldZ - minZ) / (maxZ - minZ) * resolution - 0.5;

        float distPx = sampleBilinear(distToRiver, gx, gz);
        if (distPx >= 12.0f) return 0.0;
        float widthBlocks = sampleBilinear(channelWidth, gx, gz);
        if (widthBlocks <= 0.0f) return 0.0;
        double dr = distPx * blocksPerPixel;
        if (dr >= widthBlocks) return 0.0;

        double u = 1.0 - dr / widthBlocks;
        double falloff = u * u * (3.0 - 2.0 * u);
        return 0.14 * falloff;
    }

    public double riverCarveErosionBias(double worldX, double worldZ) {
        if (!enabled) return 0.0;
        double gx = (worldX - minX) / (maxX - minX) * resolution - 0.5;
        double gz = (worldZ - minZ) / (maxZ - minZ) * resolution - 0.5;

        float distPx = sampleBilinear(distToRiver, gx, gz);
        if (distPx >= 12.0f) return 0.0;
        float widthBlocks = sampleBilinear(channelWidth, gx, gz);
        if (widthBlocks <= 0.0f) return 0.0;
        double dr = distPx * blocksPerPixel;
        if (dr >= widthBlocks) return 0.0;

        double u = 1.0 - dr / widthBlocks;
        double falloff = u * u * (3.0 - 2.0 * u);
        return -0.25 * falloff;
    }

    private float sampleBilinear(float[] grid, double gx, double gz) {
        int x0 = (int) Math.floor(gx);
        int z0 = (int) Math.floor(gz);
        double tx = gx - x0;
        double tz = gz - z0;

        x0 = Math.max(0, Math.min(resolution - 1, x0));
        int x1 = Math.max(0, Math.min(resolution - 1, x0 + 1));
        z0 = Math.max(0, Math.min(resolution - 1, z0));
        int z1 = Math.max(0, Math.min(resolution - 1, z0 + 1));

        float v00 = grid[z0 * resolution + x0];
        float v10 = grid[z0 * resolution + x1];
        float v01 = grid[z1 * resolution + x0];
        float v11 = grid[z1 * resolution + x1];

        float v0 = (float) (v00 * (1.0 - tx) + v10 * tx);
        float v1 = (float) (v01 * (1.0 - tx) + v11 * tx);
        return (float) (v0 * (1.0 - tz) + v1 * tz);
    }

    private static double simplexFbm(double x, double z, long seed) {
        return IslandNoise.fbm2(x, z, seed, 3, 2.0, 0.5);
    }

    private static void sortDescending(int[] arr, float[] priority, int left, int right) {
        if (left >= right) return;
        int pivot = arr[left + (right - left) / 2];
        int i = left, j = right;
        while (i <= j) {
            while (compareDescending(arr[i], pivot, priority) < 0) i++;
            while (compareDescending(arr[j], pivot, priority) > 0) j--;
            if (i <= j) {
                int tmp = arr[i];
                arr[i] = arr[j];
                arr[j] = tmp;
                i++;
                j--;
            }
        }
        if (left < j) sortDescending(arr, priority, left, j);
        if (i < right) sortDescending(arr, priority, i, right);
    }

    private static int compareDescending(int a, int b, float[] priority) {
        int c = Float.compare(priority[b], priority[a]);
        if (c != 0) return c;
        return Integer.compare(b, a);
    }

    private static final class MinHeap {
        private final int[] data;
        private int size = 0;
        private final float[] priority;

        public MinHeap(int capacity, float[] priority) {
            this.data = new int[capacity];
            this.priority = priority;
        }

        public void push(int val) {
            int i = size++;
            data[i] = val;
            siftUp(i);
        }

        public int poll() {
            int res = data[0];
            int last = data[--size];
            if (size > 0) {
                data[0] = last;
                siftDown(0);
            }
            return res;
        }

        public boolean isEmpty() {
            return size == 0;
        }

        private void siftUp(int k) {
            int x = data[k];
            while (k > 0) {
                int parent = (k - 1) >>> 1;
                int pVal = data[parent];
                if (compare(x, pVal) >= 0) break;
                data[k] = pVal;
                k = parent;
            }
            data[k] = x;
        }

        private void siftDown(int k) {
            int x = data[k];
            int half = size >>> 1;
            while (k < half) {
                int child = (k << 1) + 1;
                int right = child + 1;
                if (right < size && compare(data[right], data[child]) < 0) {
                    child = right;
                }
                if (compare(x, data[child]) <= 0) break;
                data[k] = data[child];
                k = child;
            }
            data[k] = x;
        }

        private int compare(int a, int b) {
            int c = Float.compare(priority[a], priority[b]);
            if (c != 0) return c;
            return Integer.compare(a, b);
        }
    }
}
