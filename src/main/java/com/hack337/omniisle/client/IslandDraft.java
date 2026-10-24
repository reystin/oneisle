package com.hack337.omniisle.client;

import com.hack337.omniisle.platform.TargetPlatform;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/** Small editable bitmap used by the in-game editor. Expensive world data is built only on Save. */
public final class IslandDraft {
    public static final int RESOLUTION = 192;
    public static final int DEFAULT_ISLAND_SIZE = 4000;
    public static final List<BiomePreview.Type> OVERWORLD_BIOMES = Arrays.stream(BiomePreview.Type.values())
        .filter(type -> TargetPlatform.HAS_PALE_GARDEN || type != BiomePreview.Type.PALE_GARDEN)
        .toList();
    private static final EnumSet<BiomePreview.Type> DEFAULT_BIOMES = EnumSet.of(
        BiomePreview.Type.SNOWY_PLAINS, BiomePreview.Type.TAIGA,
        BiomePreview.Type.BIRCH_FOREST, BiomePreview.Type.FOREST,
        BiomePreview.Type.PLAINS, BiomePreview.Type.MEADOW,
        BiomePreview.Type.CHERRY_GROVE, BiomePreview.Type.DARK_FOREST,
        BiomePreview.Type.DAPPLED_FOREST,
        BiomePreview.Type.PALE_GARDEN, BiomePreview.Type.SWAMP,
        BiomePreview.Type.MANGROVE_SWAMP, BiomePreview.Type.JUNGLE,
        BiomePreview.Type.SAVANNA, BiomePreview.Type.DESERT,
        BiomePreview.Type.BADLANDS, BiomePreview.Type.STONY_PEAKS,
        BiomePreview.Type.JAGGED_PEAKS, BiomePreview.Type.MUSHROOM_FIELDS,
        BiomePreview.Type.BEACH, BiomePreview.Type.STONY_SHORE,
        BiomePreview.Type.SNOWY_BEACH, BiomePreview.Type.OCEAN,
        BiomePreview.Type.DEEP_OCEAN, BiomePreview.Type.WARM_OCEAN,
        BiomePreview.Type.LUKEWARM_OCEAN, BiomePreview.Type.COLD_OCEAN,
        BiomePreview.Type.FROZEN_OCEAN, BiomePreview.Type.RIVER,
        BiomePreview.Type.FROZEN_RIVER, BiomePreview.Type.LUSH_CAVES,
        BiomePreview.Type.DRIPSTONE_CAVES, BiomePreview.Type.DEEP_DARK,
        BiomePreview.Type.SULFUR_CAVES);

    public enum GenerationVersion {
        V2("omniisle.generation.v2", 2),
        V1("omniisle.generation.v1", 1);

        public final String translationKey;
        public final int version;

        GenerationVersion(String translationKey, int version) {
            this.translationKey = translationKey;
            this.version = version;
        }

        public static GenerationVersion fromInt(int v) {
            return v <= 1 ? V1 : V2;
        }
    }

    public enum Preset {
        NATURAL("omniisle.preset.natural"),
        ROUND("omniisle.preset.round"),
        LONG("omniisle.preset.long"),
        CRESCENT("omniisle.preset.crescent"),
        ARCHIPELAGO("omniisle.preset.archipelago");

        public final String translationKey;
        Preset(String translationKey) { this.translationKey = translationKey; }
    }

    public enum Relief {
        BALANCED("omniisle.relief.balanced", 192.0),
        FLAT("omniisle.relief.flat", 320.0),
        MOUNTAINOUS("omniisle.relief.mountainous", 110.0);

        public final String translationKey;
        public final double coastWidth;
        Relief(String translationKey, double coastWidth) {
            this.translationKey = translationKey;
            this.coastWidth = coastWidth;
        }
    }

    public enum BiomeTheme {
        ALL("omniisle.theme.all"),
        STANDARD("omniisle.theme.standard"),
        TROPICAL("omniisle.theme.tropical"),
        NORDIC("omniisle.theme.nordic"),
        DESERT("omniisle.theme.desert"),
        MYSTIC("omniisle.theme.mystic");

        public final String translationKey;
        BiomeTheme(String translationKey) { this.translationKey = translationKey; }
    }

    public enum StructureOption {
        VILLAGE("omniisle.structure.village"), OUTPOST("omniisle.structure.outpost"),
        MANSION("omniisle.structure.mansion"), DESERT_PYRAMID("omniisle.structure.desert_pyramid"),
        IGLOO("omniisle.structure.igloo"), JUNGLE_TEMPLE("omniisle.structure.jungle_temple"),
        SWAMP_HUT("omniisle.structure.swamp_hut"), TRAIL_RUINS("omniisle.structure.trail_ruins"),
        TRIAL_CHAMBERS("omniisle.structure.trial_chambers"), ANCIENT_CITY("omniisle.structure.ancient_city"),
        STRONGHOLD("omniisle.structure.stronghold"), RUINED_PORTAL("omniisle.structure.ruined_portal"),
        MINESHAFT("omniisle.structure.mineshaft"), OCEAN_MONUMENT("omniisle.structure.ocean_monument"),
        OCEAN_RUINS("omniisle.structure.ocean_ruins"), SHIPWRECK("omniisle.structure.shipwreck"),
        BURIED_TREASURE("omniisle.structure.buried_treasure"),
        ABANDONED_CAMP("omniisle.structure.abandoned_camp");

        public final String translationKey;
        StructureOption(String translationKey) { this.translationKey = translationKey; }
    }

    public static final List<StructureOption> STRUCTURE_OPTIONS = Arrays.stream(StructureOption.values())
        .filter(option -> TargetPlatform.HAS_TRIAL_CHAMBERS || option != StructureOption.TRIAL_CHAMBERS)
        .filter(option -> TargetPlatform.HAS_ABANDONED_CAMP || option != StructureOption.ABANDONED_CAMP)
        .toList();

    public record DraftSnapshot(boolean[] land, byte[] height, Integer spawnPx, Integer spawnPz) {}

    private boolean[] land;
    private byte[] height;
    private Integer spawnPx = null;
    private Integer spawnPz = null;
    private int islandSize;
    private long shapeSeed;
    private long biomeSeed;
    private Preset preset;
    private Relief relief = Relief.BALANCED;
    private BiomeTheme theme = BiomeTheme.STANDARD;
    private GenerationVersion generationVersion = GenerationVersion.V2;
    private double isletDensity = 0.25;
    private double structureDensity = 1.0;
    private int climateScale = 720;
    private com.hack337.omniisle.continent.IslandClimate.GradientMode gradientMode =
        com.hack337.omniisle.continent.IslandClimate.GradientMode.NORTH_SOUTH;
    private double gradientStrength = 0.50;
    private EnumSet<BiomePreview.Type> enabledBiomes;
    private EnumSet<StructureOption> enabledStructures;

    private final ArrayDeque<DraftSnapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<DraftSnapshot> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO_STEPS = 32;

    public IslandDraft() {
        this(new boolean[RESOLUTION * RESOLUTION], defaultHeight(), DEFAULT_ISLAND_SIZE,
            new Random().nextLong(), new Random().nextLong(), Preset.NATURAL, defaultBiomes(),
            defaultStructures());
        rebuildShape();
    }

    private static byte[] defaultHeight() {
        byte[] h = new byte[RESOLUTION * RESOLUTION];
        Arrays.fill(h, (byte) 1);
        return h;
    }

    private IslandDraft(boolean[] land, byte[] height, int islandSize, long shapeSeed, long biomeSeed, Preset preset,
                              EnumSet<BiomePreview.Type> enabledBiomes) {
        this(land, height, islandSize, shapeSeed, biomeSeed, preset, enabledBiomes, defaultStructures());
    }

    private IslandDraft(boolean[] land, byte[] height, int islandSize, long shapeSeed, long biomeSeed, Preset preset,
                              EnumSet<BiomePreview.Type> enabledBiomes,
                              EnumSet<StructureOption> enabledStructures) {
        this.land = land;
        this.height = height != null ? height : defaultHeight();
        this.islandSize = islandSize;
        this.shapeSeed = shapeSeed;
        this.biomeSeed = biomeSeed;
        this.preset = preset;
        this.enabledBiomes = enabledBiomes;
        this.enabledStructures = enabledStructures;
    }

    private static EnumSet<BiomePreview.Type> defaultBiomes() {
        EnumSet<BiomePreview.Type> result = EnumSet.copyOf(DEFAULT_BIOMES);
        result.retainAll(OVERWORLD_BIOMES);
        return result;
    }

    private static EnumSet<StructureOption> defaultStructures() {
        return STRUCTURE_OPTIONS.isEmpty() ? EnumSet.noneOf(StructureOption.class)
            : EnumSet.copyOf(STRUCTURE_OPTIONS);
    }

    public IslandDraft copy() {
        IslandDraft draft = new IslandDraft(Arrays.copyOf(land, land.length), Arrays.copyOf(height, height.length), islandSize, shapeSeed, biomeSeed, preset,
            EnumSet.copyOf(enabledBiomes), EnumSet.copyOf(enabledStructures));
        draft.relief = this.relief;
        draft.theme = this.theme;
        draft.generationVersion = this.generationVersion;
        draft.isletDensity = this.isletDensity;
        draft.structureDensity = this.structureDensity;
        draft.climateScale = this.climateScale;
        draft.gradientMode = this.gradientMode;
        draft.gradientStrength = this.gradientStrength;
        draft.spawnPx = this.spawnPx;
        draft.spawnPz = this.spawnPz;
        return draft;
    }

    public boolean isLand(int x, int z) {
        return x >= 0 && z >= 0 && x < RESOLUTION && z < RESOLUTION && land[z * RESOLUTION + x];
    }

    public byte tier(int x, int z) {
        if (x < 0 || z < 0 || x >= RESOLUTION || z >= RESOLUTION) return 1;
        return height[z * RESOLUTION + x];
    }

    public void setTier(int x, int z, int tier) {
        if (x >= 0 && z >= 0 && x < RESOLUTION && z < RESOLUTION) {
            height[z * RESOLUTION + x] = (byte) Math.max(0, Math.min(3, tier));
        }
    }

    public byte[] heightLayer() {
        return height;
    }

    public boolean hasExplicitSpawn() {
        return spawnPx != null && spawnPz != null;
    }

    public Integer spawnPx() {
        return spawnPx;
    }

    public Integer spawnPz() {
        return spawnPz;
    }

    public void setSpawn(int px, int pz) {
        if (isLand(px, pz)) {
            pushUndo();
            this.spawnPx = px;
            this.spawnPz = pz;
        }
    }

    public void clearSpawn() {
        pushUndo();
        this.spawnPx = null;
        this.spawnPz = null;
    }

    public double structureDensity() {
        return structureDensity;
    }

    public void setStructureDensity(double structureDensity) {
        this.structureDensity = Math.max(0.5, Math.min(2.0, structureDensity));
    }

    public int climateScale() { return climateScale; }
    public void setClimateScale(int scale) {
        this.climateScale = Math.max(384, Math.min(1536, Math.round(scale / 48.0f) * 48));
    }

    public com.hack337.omniisle.continent.IslandClimate.GradientMode gradientMode() { return gradientMode; }
    public void setGradientMode(com.hack337.omniisle.continent.IslandClimate.GradientMode mode) {
        this.gradientMode = mode != null ? mode : com.hack337.omniisle.continent.IslandClimate.GradientMode.NORTH_SOUTH;
    }

    public double gradientStrength() { return gradientStrength; }
    public void setGradientStrength(double strength) {
        this.gradientStrength = Math.max(0.0, Math.min(1.0, strength));
    }

    /**
     * Returns [px, pz] for the effective spawn location.
     * Uses explicit spawn if valid and on land, otherwise computes auto-spawn.
     */
    public int[] effectiveSpawnPx() {
        if (spawnPx != null && spawnPz != null && isLand(spawnPx, spawnPz)) {
            return new int[]{spawnPx, spawnPz};
        }
        return computeAutoSpawn();
    }

    public int[] computeAutoSpawn() {
        int cx = RESOLUTION / 2;
        int cz = RESOLUTION / 2;

        int bestPx = cx;
        int bestPz = cz;
        double bestDist = -1.0;

        // Search within expanding radii (40% default) from center
        for (double factor : new double[]{0.40, 0.60, 0.85, 1.0}) {
            int r = (int) Math.round(RESOLUTION * 0.5 * factor);
            int r2 = r * r;

            for (int z = Math.max(0, cz - r); z <= Math.min(RESOLUTION - 1, cz + r); z++) {
                for (int x = Math.max(0, cx - r); x <= Math.min(RESOLUTION - 1, cx + r); x++) {
                    int dx = x - cx;
                    int dz = z - cz;
                    if (dx * dx + dz * dz > r2) continue;
                    if (!land[z * RESOLUTION + x]) continue;

                    double d = distanceToWater(x, z);
                    if (d > bestDist) {
                        bestDist = d;
                        bestPx = x;
                        bestPz = z;
                    }
                }
            }
            if (bestDist > 0.0) break;
        }

        if (bestDist < 0.0) {
            for (int z = 0; z < RESOLUTION; z++) {
                for (int x = 0; x < RESOLUTION; x++) {
                    if (land[z * RESOLUTION + x]) {
                        return new int[]{x, z};
                    }
                }
            }
            return new int[]{cx, cz};
        }

        return new int[]{bestPx, bestPz};
    }

    private double distanceToWater(int px, int pz) {
        int maxCheck = 15;
        double minDistSq = maxCheck * maxCheck;
        for (int dz = -maxCheck; dz <= maxCheck; dz++) {
            int z = pz + dz;
            if (z < 0 || z >= RESOLUTION) {
                minDistSq = Math.min(minDistSq, dz * dz);
                continue;
            }
            for (int dx = -maxCheck; dx <= maxCheck; dx++) {
                int x = px + dx;
                if (x < 0 || x >= RESOLUTION || !land[z * RESOLUTION + x]) {
                    double distSq = dx * dx + dz * dz;
                    if (distSq < minDistSq) minDistSq = distSq;
                }
            }
        }
        return Math.sqrt(minDistSq);
    }

    public double worldSpawnX() {
        int[] sp = effectiveSpawnPx();
        return worldX(sp[0]);
    }

    public double worldSpawnZ() {
        int[] sp = effectiveSpawnPx();
        return worldZ(sp[1]);
    }

    public void pushUndo() {
        if (undoStack.size() >= MAX_UNDO_STEPS) {
            undoStack.removeLast();
        }
        undoStack.push(new DraftSnapshot(Arrays.copyOf(land, land.length), Arrays.copyOf(height, height.length), spawnPx, spawnPz));
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        redoStack.push(new DraftSnapshot(Arrays.copyOf(land, land.length), Arrays.copyOf(height, height.length), spawnPx, spawnPz));
        DraftSnapshot snap = undoStack.pop();
        land = snap.land;
        height = snap.height;
        spawnPx = snap.spawnPx;
        spawnPz = snap.spawnPz;
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(new DraftSnapshot(Arrays.copyOf(land, land.length), Arrays.copyOf(height, height.length), spawnPx, spawnPz));
        DraftSnapshot snap = redoStack.pop();
        land = snap.land;
        height = snap.height;
        spawnPx = snap.spawnPx;
        spawnPz = snap.spawnPz;
        return true;
    }

    public void clearLand() {
        pushUndo();
        Arrays.fill(land, false);
    }

    public void clearHeight() {
        pushUndo();
        Arrays.fill(height, (byte) 1);
    }

    public void paint(int centerX, int centerZ, int radius, boolean value) {
        int r2 = radius * radius;
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(RESOLUTION - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(RESOLUTION - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz <= r2) land[z * RESOLUTION + x] = value;
            }
        }
    }

    public void paintHeight(int centerX, int centerZ, int radius, int tier) {
        int r2 = radius * radius;
        byte bTier = (byte) Math.max(0, Math.min(3, tier));
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(RESOLUTION - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(RESOLUTION - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz <= r2) height[z * RESOLUTION + x] = bTier;
            }
        }
    }

    public void smoothHeight(int centerX, int centerZ, int radius) {
        int r2 = radius * radius;
        byte[] snapshot = Arrays.copyOf(height, height.length);
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(RESOLUTION - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(RESOLUTION - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz > r2) continue;
                int sum = 0;
                int count = 0;
                for (int nz = Math.max(0, z - 1); nz <= Math.min(RESOLUTION - 1, z + 1); nz++) {
                    for (int nx = Math.max(0, x - 1); nx <= Math.min(RESOLUTION - 1, x + 1); nx++) {
                        sum += snapshot[nz * RESOLUTION + nx];
                        count++;
                    }
                }
                height[z * RESOLUTION + x] = (byte) Math.round((double) sum / count);
            }
        }
    }

    public void smooth(int centerX, int centerZ, int radius) {
        int r2 = radius * radius;
        boolean[] snapshot = Arrays.copyOf(land, land.length);
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(RESOLUTION - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(RESOLUTION - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz > r2) continue;
                int landCount = 0;
                int total = 0;
                for (int nz = Math.max(0, z - 1); nz <= Math.min(RESOLUTION - 1, z + 1); nz++) {
                    for (int nx = Math.max(0, x - 1); nx <= Math.min(RESOLUTION - 1, x + 1); nx++) {
                        total++;
                        if (snapshot[nz * RESOLUTION + nx]) landCount++;
                    }
                }
                if (landCount * 2 > total) {
                    land[z * RESOLUTION + x] = true;
                } else if (landCount * 2 < total) {
                    land[z * RESOLUTION + x] = false;
                }
            }
        }
    }

    public void rebuildShape() {
        pushUndo();
        boolean[] next = new boolean[land.length];
        Random random = new Random(shapeSeed);
        double rotation = random.nextDouble() * Math.PI * 2.0;
        List<ArchipelagoBlob> archipelago = preset == Preset.ARCHIPELAGO
            ? createArchipelago(shapeSeed) : List.of();
        for (int z = 0; z < RESOLUTION; z++) {
            double nz = ((z + 0.5) / RESOLUTION) * 2.0 - 1.0;
            for (int x = 0; x < RESOLUTION; x++) {
                double nx = ((x + 0.5) / RESOLUTION) * 2.0 - 1.0;
                double rx = nx * Math.cos(rotation) - nz * Math.sin(rotation);
                double rz = nx * Math.sin(rotation) + nz * Math.cos(rotation);
                double noise = fbm(nx * 3.1, nz * 3.1, shapeSeed) * 0.10
                    + fbm(nx * 7.4, nz * 7.4, shapeSeed ^ 0x5DEECE66DL) * 0.035;
                boolean value = switch (preset) {
                    case NATURAL -> Math.hypot(nx / 0.72, nz / 0.68) < 1.0 + noise;
                    case ROUND -> Math.hypot(nx / 0.70, nz / 0.70) < 1.0 + noise * 0.30;
                    case LONG -> Math.hypot(rx / 0.78, rz / 0.47) < 1.0 + noise * 0.65;
                    case CRESCENT -> Math.hypot(nx / 0.70, nz / 0.70) < 1.0 + noise * 0.45
                        && Math.hypot((nx - 0.27) / 0.59, (nz + 0.02) / 0.58) > 1.0;
                    case ARCHIPELAGO -> archipelagoLand(nx, nz, archipelago);
                };
                next[z * RESOLUTION + x] = value;
            }
        }
        land = next;
    }

    private static List<ArchipelagoBlob> createArchipelago(long seed) {
        Random random = new Random(seed ^ 0xA11C4E1A90L);
        double[][] main = {
            {-0.48, -0.48, 0.31, 0.22, -0.20},
            { 0.42, -0.48, 0.27, 0.22,  0.20},
            {-0.48,  0.31, 0.23, 0.34,  0.12},
            { 0.43,  0.36, 0.31, 0.25, -0.30},
            {-0.02, -0.10, 0.13, 0.19, -0.25},
            { 0.05,  0.59, 0.15, 0.10,  0.10},
            { 0.58, -0.02, 0.12, 0.17, -0.10},
            {-0.13,  0.26, 0.10, 0.13,  0.25}
        };
        java.util.ArrayList<ArchipelagoBlob> blobs = new java.util.ArrayList<>();
        for (double[] values : main) {
            blobs.add(new ArchipelagoBlob(
                values[0] + (random.nextDouble() - 0.5) * 0.07,
                values[1] + (random.nextDouble() - 0.5) * 0.07,
                values[2] * (0.90 + random.nextDouble() * 0.20),
                values[3] * (0.90 + random.nextDouble() * 0.20),
                values[4] + (random.nextDouble() - 0.5) * 0.35,
                random.nextDouble() * Math.PI * 2.0,
                0.13 + random.nextDouble() * 0.08));
        }
        // Small satellite islands turn the preset into a real island chain instead of four circles.
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 0.20 + random.nextDouble() * 0.65;
            blobs.add(new ArchipelagoBlob(
                Math.cos(angle) * radius + (random.nextDouble() - 0.5) * 0.12,
                Math.sin(angle) * radius + (random.nextDouble() - 0.5) * 0.12,
                0.035 + random.nextDouble() * 0.055,
                0.030 + random.nextDouble() * 0.060,
                random.nextDouble() * Math.PI,
                random.nextDouble() * Math.PI * 2.0,
                0.12 + random.nextDouble() * 0.12));
        }
        return blobs;
    }

    private static boolean archipelagoLand(double x, double z, List<ArchipelagoBlob> blobs) {
        boolean land = false;
        for (ArchipelagoBlob blob : blobs) {
            if (insideBlob(x, z, blob)) { land = true; break; }
        }
        if (!land) return false;
        // A small lagoon in the north-eastern main island, inspired by ring-shaped volcanic islands.
        ArchipelagoBlob atoll = blobs.get(1);
        double dx = x - atoll.cx;
        double dz = z - atoll.cz;
        double cos = Math.cos(atoll.rotation);
        double sin = Math.sin(atoll.rotation);
        double rx = dx * cos + dz * sin;
        double rz = -dx * sin + dz * cos;
        return rx * rx / (atoll.rx * atoll.rx * 0.15) + rz * rz / (atoll.rz * atoll.rz * 0.15) >= 1.0;
    }

    private static boolean insideBlob(double x, double z, ArchipelagoBlob blob) {
        double dx = x - blob.cx;
        double dz = z - blob.cz;
        double cos = Math.cos(blob.rotation);
        double sin = Math.sin(blob.rotation);
        double rx = (dx * cos + dz * sin) / blob.rx;
        double rz = (-dx * sin + dz * cos) / blob.rz;
        double angle = Math.atan2(rz, rx);
        double irregularEdge = 1.0 + blob.roughness * (
            0.56 * Math.sin(angle * 3.0 + blob.phase)
                + 0.29 * Math.sin(angle * 5.0 - blob.phase * 0.7)
                + 0.15 * Math.sin(angle * 8.0 + blob.phase * 1.3));
        return Math.hypot(rx, rz) < irregularEdge;
    }

    private record ArchipelagoBlob(double cx, double cz, double rx, double rz,
                                    double rotation, double phase, double roughness) {}

    public void rerollShape() {
        shapeSeed = new Random().nextLong();
        rebuildShape();
    }

    public void setShapeSeed(long seed) {
        this.shapeSeed = seed;
        rebuildShape();
    }

    public void rerollBiomes() { biomeSeed = new Random().nextLong(); }
    public void setBiomeSeed(long seed) { this.biomeSeed = seed; }

    public boolean hasLand() {
        for (boolean pixel : land) if (pixel) return true;
        return false;
    }

    public String packedMask() {
        byte[] packed = new byte[(land.length + 7) / 8];
        for (int i = 0; i < land.length; i++) {
            if (land[i]) packed[i >>> 3] |= (byte)(1 << (i & 7));
        }
        return Base64.getEncoder().encodeToString(packed);
    }

    public String packedHeight() {
        byte[] packed = new byte[(height.length + 3) / 4];
        for (int i = 0; i < height.length; i++) {
            int val = height[i] & 3;
            packed[i >>> 2] |= (byte) (val << ((i & 3) * 2));
        }
        return Base64.getEncoder().encodeToString(packed);
    }

    public boolean hasCustomHeight() {
        for (byte b : height) if (b != 1) return true;
        return false;
    }

    public static byte[] unpackHeight(String base64, int totalPixels) {
        byte[] result = new byte[totalPixels];
        Arrays.fill(result, (byte) 1);
        if (base64 == null || base64.isEmpty()) return result;
        try {
            byte[] packed = Base64.getDecoder().decode(base64);
            for (int i = 0; i < totalPixels && (i >>> 2) < packed.length; i++) {
                int shift = (i & 3) * 2;
                result[i] = (byte) (((packed[i >>> 2] & 0xFF) >>> shift) & 3);
            }
        } catch (Exception ignored) {}
        return result;
    }

    public double worldSpan() { return islandSize * 1.36; }
    public double worldX(int pixelX) { return ((pixelX + 0.5) / RESOLUTION - 0.5) * worldSpan(); }
    public double worldZ(int pixelZ) { return ((pixelZ + 0.5) / RESOLUTION - 0.5) * worldSpan(); }
    public int islandSize() { return islandSize; }
    public void setIslandSize(int islandSize) { this.islandSize = Math.max(2500, Math.min(8000, islandSize)); }
    public long biomeSeed() { return biomeSeed; }
    public SetView enabledBiomes() { return new SetView(EnumSet.copyOf(enabledBiomes)); }
    public boolean isBiomeEnabled(BiomePreview.Type type) { return enabledBiomes.contains(type); }

    public boolean toggleBiome(BiomePreview.Type type) {
        if (!enabledBiomes.remove(type)) {
            enabledBiomes.add(type);
        }
        return true;
    }

    public void selectAllBiomes(BiomePreview.Category category) {
        for (BiomePreview.Type type : OVERWORLD_BIOMES) {
            if (category == null || type.category == category) {
                enabledBiomes.add(type);
            }
        }
    }

    public void deselectAllBiomes(BiomePreview.Category category) {
        if (category == null) {
            enabledBiomes.clear();
        } else {
            enabledBiomes.removeIf(type -> type.category == category);
        }
    }

    public void applyTheme(BiomeTheme theme) {
        this.theme = theme;
        enabledBiomes.clear();
        switch (theme) {
            case ALL -> enabledBiomes.addAll(OVERWORLD_BIOMES);
            case STANDARD -> enabledBiomes.addAll(defaultBiomes());
            case TROPICAL -> enabledBiomes.addAll(List.of(
                BiomePreview.Type.JUNGLE, BiomePreview.Type.BAMBOO_JUNGLE, BiomePreview.Type.SPARSE_JUNGLE,
                BiomePreview.Type.SAVANNA, BiomePreview.Type.SAVANNA_PLATEAU, BiomePreview.Type.WINDSWEPT_SAVANNA,
                BiomePreview.Type.MANGROVE_SWAMP, BiomePreview.Type.CHERRY_GROVE,
                BiomePreview.Type.BEACH,
                BiomePreview.Type.WARM_OCEAN, BiomePreview.Type.LUKEWARM_OCEAN, BiomePreview.Type.DEEP_LUKEWARM_OCEAN,
                BiomePreview.Type.RIVER,
                BiomePreview.Type.LUSH_CAVES
            ));
            case NORDIC -> enabledBiomes.addAll(List.of(
                BiomePreview.Type.SNOWY_PLAINS, BiomePreview.Type.ICE_SPIKES,
                BiomePreview.Type.TAIGA, BiomePreview.Type.SNOWY_TAIGA,
                BiomePreview.Type.OLD_GROWTH_PINE_TAIGA, BiomePreview.Type.OLD_GROWTH_SPRUCE_TAIGA,
                BiomePreview.Type.GROVE, BiomePreview.Type.JAGGED_PEAKS, BiomePreview.Type.FROZEN_PEAKS,
                BiomePreview.Type.SNOWY_SLOPES, BiomePreview.Type.WINDSWEPT_HILLS,
                BiomePreview.Type.SNOWY_BEACH, BiomePreview.Type.STONY_SHORE,
                BiomePreview.Type.COLD_OCEAN, BiomePreview.Type.DEEP_COLD_OCEAN,
                BiomePreview.Type.FROZEN_OCEAN, BiomePreview.Type.DEEP_FROZEN_OCEAN,
                BiomePreview.Type.FROZEN_RIVER,
                BiomePreview.Type.DRIPSTONE_CAVES
            ));
            case DESERT -> enabledBiomes.addAll(List.of(
                BiomePreview.Type.DESERT, BiomePreview.Type.BADLANDS,
                BiomePreview.Type.ERODED_BADLANDS, BiomePreview.Type.WOODED_BADLANDS,
                BiomePreview.Type.SAVANNA, BiomePreview.Type.SAVANNA_PLATEAU, BiomePreview.Type.WINDSWEPT_SAVANNA,
                BiomePreview.Type.BEACH,
                BiomePreview.Type.WARM_OCEAN, BiomePreview.Type.OCEAN, BiomePreview.Type.DEEP_OCEAN,
                BiomePreview.Type.RIVER,
                BiomePreview.Type.DRIPSTONE_CAVES, BiomePreview.Type.SULFUR_CAVES
            ));
            case MYSTIC -> enabledBiomes.addAll(List.of(
                BiomePreview.Type.DARK_FOREST, BiomePreview.Type.PALE_GARDEN,
                BiomePreview.Type.MUSHROOM_FIELDS, BiomePreview.Type.SWAMP,
                BiomePreview.Type.DAPPLED_FOREST,
                BiomePreview.Type.STONY_SHORE,
                BiomePreview.Type.DEEP_OCEAN,
                BiomePreview.Type.RIVER,
                BiomePreview.Type.DEEP_DARK, BiomePreview.Type.SULFUR_CAVES, BiomePreview.Type.LUSH_CAVES
            ));
        }
        enabledBiomes.retainAll(OVERWORLD_BIOMES);
    }

    public boolean isStructureEnabled(StructureOption option) { return enabledStructures.contains(option); }
    public void toggleStructure(StructureOption option) {
        if (!enabledStructures.remove(option)) enabledStructures.add(option);
    }
    public void selectAllStructures() { enabledStructures.addAll(STRUCTURE_OPTIONS); }
    public void deselectAllStructures() { enabledStructures.clear(); }

    public BiomePreview.Type selectBiome(BiomePreview.Type preferred, double x, double z) {
        List<BiomePreview.Type> compatible = compatibleChoices(preferred);
        if (compatible.isEmpty()) return preferred;
        if (compatible.size() == 1) return compatible.get(0);
        if (!preferred.isLand() && enabledBiomes.contains(preferred)) return preferred;
        double patch = fbm(x / 420.0, z / 420.0,
            biomeSeed ^ (0x6A09E667F3BCC909L + preferred.family * 0x9E3779B97F4A7C15L));
        int index = Math.min(compatible.size() - 1,
            Math.max(0, (int)Math.floor((patch * 0.5 + 0.5) * compatible.size())));
        return compatible.get(index);
    }

    public List<BiomePreview.Type> compatibleChoices(BiomePreview.Type preferred) {
        List<BiomePreview.Type> exactFamily = enabledBiomes.stream()
            .filter(type -> type.category == preferred.category)
            .filter(type -> !preferred.isLand() || type.family == preferred.family)
            .toList();
        if (!exactFamily.isEmpty()) return exactFamily;
        List<BiomePreview.Type> inCategory = enabledBiomes.stream().filter(type -> type.category == preferred.category).toList();
        if (!inCategory.isEmpty()) return inCategory;
        return List.of(preferred);
    }

    public List<BiomePreview.Type> enabledBiomes(BiomePreview.Category category) {
        List<BiomePreview.Type> list = enabledBiomes.stream().filter(type -> type.category == category).toList();
        if (list.isEmpty()) {
            return DEFAULT_BIOMES.stream().filter(type -> type.category == category && OVERWORLD_BIOMES.contains(type)).toList();
        }
        return list;
    }

    /** Immutable-enough iterable wrapper kept off Minecraft's serialization path. */
    public record SetView(EnumSet<BiomePreview.Type> values) {}
    public Preset preset() { return preset; }
    public void setPreset(Preset preset) { this.preset = preset; rebuildShape(); }
    public Relief relief() { return relief; }
    public void setRelief(Relief relief) { this.relief = relief; }
    public BiomeTheme theme() { return theme; }
    public GenerationVersion generationVersion() { return generationVersion; }
    public void setGenerationVersion(GenerationVersion generationVersion) { this.generationVersion = generationVersion; }
    public int generationVersionNumber() { return generationVersion.version; }
    public double isletDensity() { return isletDensity; }
    public void setIsletDensity(double isletDensity) { this.isletDensity = Math.max(0.0, Math.min(1.0, isletDensity)); }

    private static double fbm(double x, double z, long seed) {
        double total = 0.0;
        double amplitude = 0.58;
        double norm = 0.0;
        for (int octave = 0; octave < 4; octave++) {
            total += valueNoise(x, z, seed + octave * 1013L) * amplitude;
            norm += amplitude;
            x = x * 2.03 + 17.17;
            z = z * 2.03 - 11.31;
            amplitude *= 0.5;
        }
        return total / norm;
    }

    private static double valueNoise(double x, double z, long seed) {
        int x0 = (int)Math.floor(x);
        int z0 = (int)Math.floor(z);
        double tx = smooth(x - x0);
        double tz = smooth(z - z0);
        double a = lerp(hash(x0, z0, seed), hash(x0 + 1, z0, seed), tx);
        double b = lerp(hash(x0, z0 + 1, seed), hash(x0 + 1, z0 + 1, seed), tx);
        return lerp(a, b, tz);
    }

    private static double hash(int x, int z, long seed) {
        long value = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-52 - 1.0;
    }

    private static double smooth(double v) { return v * v * (3.0 - 2.0 * v); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
