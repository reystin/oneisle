package com.hack337.omniisle.client;

import com.hack337.omniisle.continent.BiomeShare;
import com.hack337.omniisle.platform.TargetPlatform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Small editable bitmap used by the in-game editor. Expensive world data is built only on Save. */
public final class IslandDraft {
    public static final int RESOLUTION = 192;
    public static final int DEFAULT_ISLAND_SIZE = 3500;
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
        V3("omniisle.generation.v3", 3),
        V2("omniisle.generation.v2", 2),
        V1("omniisle.generation.v1", 1);

        public final String translationKey;
        public final int version;

        GenerationVersion(String translationKey, int version) {
            this.translationKey = translationKey;
            this.version = version;
        }

        public static GenerationVersion fromInt(int v) {
            if (v >= 3) return V3;
            return v <= 1 ? V1 : V2;
        }
    }

    public enum Preset {
        NATURAL("omniisle.preset.natural"),
        ROUND("omniisle.preset.round"),
        LONG("omniisle.preset.long"),
        CRESCENT("omniisle.preset.crescent"),
        ARCHIPELAGO("omniisle.preset.archipelago"),
        ATOLL("omniisle.preset.atoll"),
        VOLCANO("omniisle.preset.volcano"),
        FJORDS("omniisle.preset.fjords"),
        TWIN_ISLANDS("omniisle.preset.twin_islands"),
        INLAND_SEA("omniisle.preset.inland_sea"),
        HORSESHOE_BAY("omniisle.preset.horseshoe_bay"),
        RIDGE_ISLAND("omniisle.preset.ridge_island");

        public final String translationKey;
        Preset(String translationKey) { this.translationKey = translationKey; }
    }

    public enum Symmetry {
        OFF("omniisle.symmetry.off"),
        MIRROR_X("omniisle.symmetry.mirror_x"),
        MIRROR_Z("omniisle.symmetry.mirror_z"),
        FOUR_WAY("omniisle.symmetry.four_way"),
        SIX_WAY("omniisle.symmetry.six_way"),
        EIGHT_WAY("omniisle.symmetry.eight_way");

        public final String translationKey;
        Symmetry(String translationKey) { this.translationKey = translationKey; }
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

    public record DraftSnapshot(boolean[] land, byte[] height, Integer spawnPx, Integer spawnPz, int resolution,
                                EnumSet<BiomePreview.Type> enabledBiomes,
                                Map<BiomePreview.Type, Integer> weights,
                                Map<BiomePreview.Type, Double> bias,
                                Map<BiomePreview.Type, Double> achieved,
                                Map<BiomePreview.Type, Double> target,
                                boolean feasible) {}

    private int resolution = RESOLUTION;
    private boolean[] land;
    private byte[] height;
    private Integer spawnPx = null;
    private Integer spawnPz = null;
    private int islandSize;
    private long shapeSeed;
    private long biomeSeed;
    private Preset preset;
    private Relief relief = Relief.BALANCED;
    private BiomeTheme theme = BiomeTheme.ALL;
    private GenerationVersion generationVersion = GenerationVersion.V3;
    private double shoreDensity = com.hack337.omniisle.continent.ShoreSettings.DEFAULT.density();
    private double shoreVariety = com.hack337.omniisle.continent.ShoreSettings.DEFAULT.variety();
    private boolean shoreRocky = com.hack337.omniisle.continent.ShoreSettings.DEFAULT.rocky();
    private double isletDensity = 0.25;
    private double structureDensity = 1.0;
    private int climateScale = 720;
    private com.hack337.omniisle.continent.IslandClimate.GradientMode gradientMode =
        com.hack337.omniisle.continent.IslandClimate.GradientMode.NORTH_SOUTH;
    private double gradientStrength = 0.50;
    private double riverDensity = 0.35;
    private boolean worldBorder = false;
    private int worldBorderMargin = 1000;
    private EnumSet<BiomePreview.Type> enabledBiomes;
    private EnumSet<StructureOption> enabledStructures;
    private final EnumMap<BiomePreview.Type, Integer> biomeWeights = new EnumMap<>(BiomePreview.Type.class);
    private final EnumMap<BiomePreview.Type, Double> biomeBias = new EnumMap<>(BiomePreview.Type.class);
    private final EnumMap<BiomePreview.Type, Double> achievedShares = new EnumMap<>(BiomePreview.Type.class);
    private final EnumMap<BiomePreview.Type, Double> targetShares = new EnumMap<>(BiomePreview.Type.class);
    private boolean sharesFeasible = true;
    private double sharesMaxErrorPp;
    private int calibrationSamples;

    private static final class ShareScratch {
        private final int[] ids = new int[BiomeShare.CATALOG_SIZE];
        private final double[] factors = new double[BiomeShare.CATALOG_SIZE];
        private final BiomeShare.SelectorScratch selector = new BiomeShare.SelectorScratch();
    }

    private final ThreadLocal<ShareScratch> shareScratch = ThreadLocal.withInitial(ShareScratch::new);

    private final ArrayDeque<DraftSnapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<DraftSnapshot> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO_STEPS = 32;

    public IslandDraft() {
        this(RESOLUTION);
    }

    public IslandDraft(int resolution) {
        this(resolution, new boolean[resolution * resolution], defaultHeight(resolution), DEFAULT_ISLAND_SIZE,
            new Random().nextLong(), new Random().nextLong(), Preset.NATURAL, allBiomes(),
            defaultStructures());
        rebuildShape();
    }

    private static EnumSet<BiomePreview.Type> allBiomes() {
        return EnumSet.copyOf(OVERWORLD_BIOMES);
    }

    private static byte[] defaultHeight(int res) {
        byte[] h = new byte[res * res];
        Arrays.fill(h, (byte) 1);
        return h;
    }

    private IslandDraft(int resolution, boolean[] land, byte[] height, int islandSize, long shapeSeed, long biomeSeed, Preset preset,
                        EnumSet<BiomePreview.Type> enabledBiomes,
                        EnumSet<StructureOption> enabledStructures) {
        this.resolution = resolution;
        this.land = land;
        this.height = height != null ? height : defaultHeight(resolution);
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
        IslandDraft draft = new IslandDraft(this.resolution, Arrays.copyOf(land, land.length),
            Arrays.copyOf(height, height.length), islandSize, shapeSeed, biomeSeed, preset,
            EnumSet.copyOf(enabledBiomes), EnumSet.copyOf(enabledStructures));
        draft.relief = this.relief;
        draft.theme = this.theme;
        draft.generationVersion = this.generationVersion;
        draft.isletDensity = this.isletDensity;
        draft.structureDensity = this.structureDensity;
        draft.climateScale = this.climateScale;
        draft.gradientMode = this.gradientMode;
        draft.gradientStrength = this.gradientStrength;
        draft.shoreDensity = this.shoreDensity;
        draft.shoreVariety = this.shoreVariety;
        draft.shoreRocky = this.shoreRocky;
        draft.riverDensity = this.riverDensity;
        draft.worldBorder = this.worldBorder;
        draft.worldBorderMargin = this.worldBorderMargin;
        draft.spawnPx = this.spawnPx;
        draft.spawnPz = this.spawnPz;
        draft.biomeWeights.putAll(this.biomeWeights);
        draft.biomeBias.putAll(this.biomeBias);
        draft.achievedShares.putAll(this.achievedShares);
        draft.targetShares.putAll(this.targetShares);
        draft.sharesFeasible = this.sharesFeasible;
        draft.sharesMaxErrorPp = this.sharesMaxErrorPp;
        draft.calibrationSamples = this.calibrationSamples;
        return draft;
    }

    public int resolution() {
        return resolution;
    }

    public void setResolution(int newRes) {
        if (newRes == this.resolution || newRes <= 0) return;
        pushUndo();
        boolean[] newLand = new boolean[newRes * newRes];
        byte[] newHeight = new byte[newRes * newRes];
        Arrays.fill(newHeight, (byte) 1);

        for (int z = 0; z < newRes; z++) {
            double srcZ = (z + 0.5) * this.resolution / (double) newRes - 0.5;
            int z0 = Math.max(0, Math.min(this.resolution - 1, (int) Math.floor(srcZ)));
            int z1 = Math.max(0, Math.min(this.resolution - 1, z0 + 1));
            double fz = Math.max(0.0, Math.min(1.0, srcZ - z0));

            for (int x = 0; x < newRes; x++) {
                double srcX = (x + 0.5) * this.resolution / (double) newRes - 0.5;
                int x0 = Math.max(0, Math.min(this.resolution - 1, (int) Math.floor(srcX)));
                int x1 = Math.max(0, Math.min(this.resolution - 1, x0 + 1));
                double fx = Math.max(0.0, Math.min(1.0, srcX - x0));

                double l00 = land[z0 * this.resolution + x0] ? 1.0 : 0.0;
                double l10 = land[z0 * this.resolution + x1] ? 1.0 : 0.0;
                double l01 = land[z1 * this.resolution + x0] ? 1.0 : 0.0;
                double l11 = land[z1 * this.resolution + x1] ? 1.0 : 0.0;
                double l = (1.0 - fz) * ((1.0 - fx) * l00 + fx * l10) + fz * ((1.0 - fx) * l01 + fx * l11);
                newLand[z * newRes + x] = l >= 0.5;

                double h00 = height[z0 * this.resolution + x0];
                double h10 = height[z0 * this.resolution + x1];
                double h01 = height[z1 * this.resolution + x0];
                double h11 = height[z1 * this.resolution + x1];
                double h = (1.0 - fz) * ((1.0 - fx) * h00 + fx * h10) + fz * ((1.0 - fx) * h01 + fx * h11);
                newHeight[z * newRes + x] = (byte) Math.max(0, Math.min(3, (int) Math.round(h)));
            }
        }
        if (spawnPx != null && spawnPz != null) {
            spawnPx = (int) Math.round(spawnPx * (double) newRes / this.resolution);
            spawnPz = (int) Math.round(spawnPz * (double) newRes / this.resolution);
        }
        this.resolution = newRes;
        this.land = newLand;
        this.height = newHeight;
    }

    public boolean isLand(int x, int z) {
        return x >= 0 && z >= 0 && x < resolution && z < resolution && land[z * resolution + x];
    }

    public void setLand(int x, int z, boolean value) {
        if (x >= 0 && z >= 0 && x < resolution && z < resolution) {
            land[z * resolution + x] = value;
        }
    }

    public byte tier(int x, int z) {
        if (x < 0 || z < 0 || x >= resolution || z >= resolution) return 1;
        return height[z * resolution + x];
    }

    public void setTier(int x, int z, int tier) {
        if (x >= 0 && z >= 0 && x < resolution && z < resolution) {
            height[z * resolution + x] = (byte) Math.max(0, Math.min(3, tier));
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

    public Integer explicitSpawnPx() {
        return spawnPx;
    }

    public Integer explicitSpawnPz() {
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

    public double riverDensity() { return riverDensity; }
    public void setRiverDensity(double density) {
        this.riverDensity = Math.max(0.0, Math.min(1.0, density));
    }

    public double shoreDensity() { return shoreDensity; }
    public void setShoreDensity(double density) {
        this.shoreDensity = Math.max(0.0, Math.min(1.0, density));
    }

    public double shoreVariety() { return shoreVariety; }
    public void setShoreVariety(double variety) {
        this.shoreVariety = Math.max(0.0, Math.min(1.0, variety));
    }

    public boolean shoreRocky() { return shoreRocky; }
    public void setShoreRocky(boolean rocky) {
        this.shoreRocky = rocky;
    }

    public com.hack337.omniisle.continent.ShoreSettings shoreSettings() {
        return new com.hack337.omniisle.continent.ShoreSettings(shoreDensity, shoreVariety, "natural", shoreRocky);
    }

    public boolean isWorldBorderEnabled() { return worldBorder; }
    public void setWorldBorderEnabled(boolean enabled) {
        this.worldBorder = enabled;
    }

    public int worldBorderMargin() { return worldBorderMargin; }
    public void setWorldBorderMargin(int margin) {
        this.worldBorderMargin = Math.max(500, Math.min(4000, margin));
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
        int cx = resolution / 2;
        int cz = resolution / 2;

        int bestPx = cx;
        int bestPz = cz;
        double bestDist = -1.0;

        // Search within expanding radii (40% default) from center
        for (double factor : new double[]{0.40, 0.60, 0.85, 1.0}) {
            int r = (int) Math.round(resolution * 0.5 * factor);
            int r2 = r * r;

            for (int z = Math.max(0, cz - r); z <= Math.min(resolution - 1, cz + r); z++) {
                for (int x = Math.max(0, cx - r); x <= Math.min(resolution - 1, cx + r); x++) {
                    int dx = x - cx;
                    int dz = z - cz;
                    if (dx * dx + dz * dz > r2) continue;
                    if (!land[z * resolution + x]) continue;

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
            for (int z = 0; z < resolution; z++) {
                for (int x = 0; x < resolution; x++) {
                    if (land[z * resolution + x]) {
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
            if (z < 0 || z >= resolution) {
                minDistSq = Math.min(minDistSq, dz * dz);
                continue;
            }
            for (int dx = -maxCheck; dx <= maxCheck; dx++) {
                int x = px + dx;
                if (x < 0 || x >= resolution || !land[z * resolution + x]) {
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
        undoStack.push(captureSnapshot());
        redoStack.clear();
    }

    private DraftSnapshot captureSnapshot() {
        return new DraftSnapshot(Arrays.copyOf(land, land.length), Arrays.copyOf(height, height.length),
            spawnPx, spawnPz, resolution, EnumSet.copyOf(enabledBiomes),
            new EnumMap<>(biomeWeights), new EnumMap<>(biomeBias),
            new EnumMap<>(achievedShares), new EnumMap<>(targetShares), sharesFeasible);
    }

    private void restoreSnapshot(DraftSnapshot snap) {
        this.resolution = snap.resolution();
        this.land = snap.land();
        this.height = snap.height();
        this.spawnPx = snap.spawnPx();
        this.spawnPz = snap.spawnPz();
        this.enabledBiomes.clear();
        this.enabledBiomes.addAll(snap.enabledBiomes());
        this.biomeWeights.clear();
        this.biomeWeights.putAll(snap.weights());
        this.biomeBias.clear();
        this.biomeBias.putAll(snap.bias());
        this.achievedShares.clear();
        this.achievedShares.putAll(snap.achieved());
        this.targetShares.clear();
        this.targetShares.putAll(snap.target());
        this.sharesFeasible = snap.feasible();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        redoStack.push(captureSnapshot());
        restoreSnapshot(undoStack.pop());
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(captureSnapshot());
        restoreSnapshot(redoStack.pop());
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

    public List<int[]> getSymmetricPoints(int x, int z, Symmetry symmetry) {
        if (symmetry == null || symmetry == Symmetry.OFF) {
            return List.of(new int[]{x, z});
        }
        int w = resolution;
        int h = resolution;
        double cx = (w - 1) / 2.0;
        double cz = (h - 1) / 2.0;
        double dx = x - cx;
        double dz = z - cz;

        java.util.LinkedHashSet<Long> visited = new java.util.LinkedHashSet<>();
        List<int[]> result = new ArrayList<>();

        java.util.function.BiConsumer<Integer, Integer> add = (px, pz) -> {
            if (px >= 0 && px < w && pz >= 0 && pz < h) {
                long key = (((long) px) << 32) | (pz & 0xFFFFFFFFL);
                if (visited.add(key)) {
                    result.add(new int[]{px, pz});
                }
            }
        };

        List<int[]> initial = transformPoints(x, z, symmetry, w, h, cx, cz, dx, dz);
        for (int[] point : initial) {
            add.accept(point[0], point[1]);
        }

        // Rasterized rotations can drift by a pixel; close the orbit so every
        // painted point maps back into the same painted set.
        if (symmetry == Symmetry.SIX_WAY || symmetry == Symmetry.EIGHT_WAY) {
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>(initial);
            int guard = 0;
            while (!queue.isEmpty() && guard++ < 512) {
                int[] point = queue.poll();
                double pdx = point[0] - cx;
                double pdz = point[1] - cz;
                for (int[] next : transformPoints(point[0], point[1], symmetry, w, h, cx, cz, pdx, pdz)) {
                    int before = result.size();
                    add.accept(next[0], next[1]);
                    if (result.size() > before) {
                        queue.add(next);
                    }
                }
            }
        }
        return result;
    }

    private static List<int[]> transformPoints(int x, int z, Symmetry symmetry, int w, int h,
                                               double cx, double cz, double dx, double dz) {
        List<int[]> points = new ArrayList<>(8);
        points.add(new int[]{x, z});
        switch (symmetry) {
            case MIRROR_X -> points.add(new int[]{w - 1 - x, z});
            case MIRROR_Z -> points.add(new int[]{x, h - 1 - z});
            case FOUR_WAY -> {
                points.add(new int[]{w - 1 - x, z});
                points.add(new int[]{x, h - 1 - z});
                points.add(new int[]{w - 1 - x, h - 1 - z});
            }
            case SIX_WAY -> {
                for (int k = 1; k < 6; k++) {
                    double angle = k * (Math.PI / 3.0);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    points.add(new int[]{
                        (int) Math.round(cx + dx * cos - dz * sin),
                        (int) Math.round(cz + dx * sin + dz * cos)
                    });
                }
            }
            case EIGHT_WAY -> {
                points.add(new int[]{w - 1 - x, z});
                points.add(new int[]{x, h - 1 - z});
                points.add(new int[]{w - 1 - x, h - 1 - z});
                int d1x = (int) Math.round(cx + dz);
                int d1z = (int) Math.round(cz + dx);
                points.add(new int[]{d1x, d1z});
                points.add(new int[]{w - 1 - d1x, d1z});
                points.add(new int[]{d1x, h - 1 - d1z});
                points.add(new int[]{w - 1 - d1x, h - 1 - d1z});
            }
            default -> {}
        }
        return points;
    }

    public void paint(int centerX, int centerZ, int radius, boolean value) {
        paint(centerX, centerZ, radius, value, Symmetry.OFF);
    }

    public void paint(int centerX, int centerZ, int radius, boolean value, Symmetry symmetry) {
        if (symmetry == Symmetry.SIX_WAY || symmetry == Symmetry.EIGHT_WAY) {
            paintRadial(centerX, centerZ, radius, value, symmetry);
            return;
        }
        for (int[] pt : getSymmetricPoints(centerX, centerZ, symmetry)) {
            paintDirect(pt[0], pt[1], radius, value);
        }
    }

    private int[] radialStamp = new int[0];
    private int[] radialQueue = new int[0];
    private int radialGeneration = 0;

    private void ensureRadialBuffers() {
        int size = resolution * resolution;
        if (radialStamp.length != size) {
            radialStamp = new int[size];
            radialQueue = new int[size];
            radialGeneration = 0;
        }
    }

    /**
     * Rasterized 60-degree rotations are not exactly reversible, so a disk
     * stamped at rotated centers is not closed under rotation. Instead, paint
     * the forward orbit of every pixel in the source disk; the resulting set
     * is closed by construction.
     */
    private void paintRadial(int centerX, int centerZ, int radius, boolean value, Symmetry symmetry) {
        radialClosure(centerX, centerZ, radius, symmetry, index -> land[index] = value);
    }

    @FunctionalInterface
    private interface RadialWriter {
        void accept(int index);
    }

    private void radialClosure(int centerX, int centerZ, int radius, Symmetry symmetry, RadialWriter writer) {
        ensureRadialBuffers();
        if (++radialGeneration == 0) {
            Arrays.fill(radialStamp, 0);
            radialGeneration = 1;
        }
        int generation = radialGeneration;
        int r2 = radius * radius;
        double cx = (resolution - 1) / 2.0;
        double cz = cx;

        for (int z = Math.max(0, centerZ - radius); z <= Math.min(resolution - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(resolution - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz > r2) continue;
                int start = z * resolution + x;
                if (radialStamp[start] == generation) continue;

                int head = 0;
                int tail = 0;
                radialStamp[start] = generation;
                radialQueue[tail++] = start;
                while (head < tail) {
                    int current = radialQueue[head++];
                    writer.accept(current);
                    int px = current % resolution;
                    int pz = current / resolution;
                    for (int[] point : transformPoints(px, pz, symmetry, resolution, resolution,
                        cx, cz, px - cx, pz - cz)) {
                        if (point[0] < 0 || point[0] >= resolution || point[1] < 0 || point[1] >= resolution) continue;
                        int next = point[1] * resolution + point[0];
                        if (radialStamp[next] == generation) continue;
                        radialStamp[next] = generation;
                        radialQueue[tail++] = next;
                    }
                }
            }
        }
    }

    private void paintDirect(int centerX, int centerZ, int radius, boolean value) {
        int r2 = radius * radius;
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(resolution - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(resolution - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz <= r2) land[z * resolution + x] = value;
            }
        }
    }

    public void paintHeight(int centerX, int centerZ, int radius, int tier) {
        paintHeight(centerX, centerZ, radius, tier, Symmetry.OFF);
    }

    public void paintHeight(int centerX, int centerZ, int radius, int tier, Symmetry symmetry) {
        if (symmetry == Symmetry.SIX_WAY || symmetry == Symmetry.EIGHT_WAY) {
            byte bTier = (byte) Math.max(0, Math.min(3, tier));
            radialClosure(centerX, centerZ, radius, symmetry, index -> height[index] = bTier);
            return;
        }
        for (int[] pt : getSymmetricPoints(centerX, centerZ, symmetry)) {
            paintHeightDirect(pt[0], pt[1], radius, tier);
        }
    }

    private void paintHeightDirect(int centerX, int centerZ, int radius, int tier) {
        int r2 = radius * radius;
        byte bTier = (byte) Math.max(0, Math.min(3, tier));
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(resolution - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(resolution - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz <= r2) height[z * resolution + x] = bTier;
            }
        }
    }

    public void smoothHeight(int centerX, int centerZ, int radius) {
        smoothHeight(centerX, centerZ, radius, Symmetry.OFF);
    }

    public void smoothHeight(int centerX, int centerZ, int radius, Symmetry symmetry) {
        for (int[] pt : getSymmetricPoints(centerX, centerZ, symmetry)) {
            smoothHeightDirect(pt[0], pt[1], radius);
        }
    }

    private void smoothHeightDirect(int centerX, int centerZ, int radius) {
        int r2 = radius * radius;
        byte[] snapshot = Arrays.copyOf(height, height.length);
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(resolution - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(resolution - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz > r2) continue;
                int sum = 0;
                int count = 0;
                for (int nz = Math.max(0, z - 1); nz <= Math.min(resolution - 1, z + 1); nz++) {
                    for (int nx = Math.max(0, x - 1); nx <= Math.min(resolution - 1, x + 1); nx++) {
                        sum += snapshot[nz * resolution + nx];
                        count++;
                    }
                }
                height[z * resolution + x] = (byte) Math.round((double) sum / count);
            }
        }
    }

    public void smooth(int centerX, int centerZ, int radius) {
        smooth(centerX, centerZ, radius, Symmetry.OFF);
    }

    public void smooth(int centerX, int centerZ, int radius, Symmetry symmetry) {
        for (int[] pt : getSymmetricPoints(centerX, centerZ, symmetry)) {
            smoothDirect(pt[0], pt[1], radius);
        }
    }

    private void smoothDirect(int centerX, int centerZ, int radius) {
        int r2 = radius * radius;
        boolean[] snapshot = Arrays.copyOf(land, land.length);
        for (int z = Math.max(0, centerZ - radius); z <= Math.min(resolution - 1, centerZ + radius); z++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(resolution - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz > r2) continue;
                int landCount = 0;
                int total = 0;
                for (int nz = Math.max(0, z - 1); nz <= Math.min(resolution - 1, z + 1); nz++) {
                    for (int nx = Math.max(0, x - 1); nx <= Math.min(resolution - 1, x + 1); nx++) {
                        total++;
                        if (snapshot[nz * resolution + nx]) landCount++;
                    }
                }
                if (landCount * 2 > total) {
                    land[z * resolution + x] = true;
                } else if (landCount * 2 < total) {
                    land[z * resolution + x] = false;
                }
            }
        }
    }

    public void floodFillLand(int startX, int startZ, boolean targetValue, Symmetry symmetry) {
        if (startX < 0 || startX >= resolution || startZ < 0 || startZ >= resolution) return;
        for (int[] pt : getSymmetricPoints(startX, startZ, symmetry)) {
            floodFillLandDirect(pt[0], pt[1], targetValue);
        }
    }

    private void floodFillLandDirect(int startX, int startZ, boolean targetValue) {
        boolean original = land[startZ * resolution + startX];
        if (original == targetValue) return;

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startZ});
        land[startZ * resolution + startX] = targetValue;

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0];
            int z = p[1];

            int[][] neighbors = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
            for (int[] n : neighbors) {
                int nx = n[0];
                int nz = n[1];
                if (nx >= 0 && nx < resolution && nz >= 0 && nz < resolution) {
                    if (land[nz * resolution + nx] == original) {
                        land[nz * resolution + nx] = targetValue;
                        queue.add(new int[]{nx, nz});
                    }
                }
            }
        }
    }

    public void floodFillHeight(int startX, int startZ, int newTier, Symmetry symmetry) {
        if (startX < 0 || startX >= resolution || startZ < 0 || startZ >= resolution) return;
        byte bTier = (byte) Math.max(0, Math.min(3, newTier));
        for (int[] pt : getSymmetricPoints(startX, startZ, symmetry)) {
            floodFillHeightDirect(pt[0], pt[1], bTier);
        }
    }

    private void floodFillHeightDirect(int startX, int startZ, byte newTier) {
        byte original = height[startZ * resolution + startX];
        if (original == newTier) return;

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startZ});
        height[startZ * resolution + startX] = newTier;

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0];
            int z = p[1];

            int[][] neighbors = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
            for (int[] n : neighbors) {
                int nx = n[0];
                int nz = n[1];
                if (nx >= 0 && nx < resolution && nz >= 0 && nz < resolution) {
                    if (height[nz * resolution + nx] == original) {
                        height[nz * resolution + nx] = newTier;
                        queue.add(new int[]{nx, nz});
                    }
                }
            }
        }
    }

    public void drawLine(int x0, int z0, int x1, int z1, int radius, boolean value, Symmetry symmetry) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int curX = x0;
        int curZ = z0;
        while (true) {
            paint(curX, curZ, radius, value, symmetry);
            if (curX == x1 && curZ == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                curX += sx;
            }
            if (e2 < dx) {
                err += dx;
                curZ += sz;
            }
        }
    }

    public void drawLineHeight(int x0, int z0, int x1, int z1, int radius, int tier, Symmetry symmetry) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int curX = x0;
        int curZ = z0;
        while (true) {
            paintHeight(curX, curZ, radius, tier, symmetry);
            if (curX == x1 && curZ == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                curX += sx;
            }
            if (e2 < dx) {
                err += dx;
                curZ += sz;
            }
        }
    }

    public void drawRect(int x0, int z0, int x1, int z1, boolean filled, int radius, boolean value, Symmetry symmetry) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);

        if (filled) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    paint(x, z, 0, value, symmetry);
                }
            }
        } else {
            drawLine(minX, minZ, maxX, minZ, radius, value, symmetry);
            drawLine(maxX, minZ, maxX, maxZ, radius, value, symmetry);
            drawLine(maxX, maxZ, minX, maxZ, radius, value, symmetry);
            drawLine(minX, maxZ, minX, minZ, radius, value, symmetry);
        }
    }

    public void drawRectHeight(int x0, int z0, int x1, int z1, boolean filled, int radius, int tier, Symmetry symmetry) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);

        if (filled) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    paintHeight(x, z, 0, tier, symmetry);
                }
            }
        } else {
            drawLineHeight(minX, minZ, maxX, minZ, radius, tier, symmetry);
            drawLineHeight(maxX, minZ, maxX, maxZ, radius, tier, symmetry);
            drawLineHeight(maxX, maxZ, minX, maxZ, radius, tier, symmetry);
            drawLineHeight(minX, maxZ, minX, minZ, radius, tier, symmetry);
        }
    }

    public void drawEllipse(int x0, int z0, int x1, int z1, boolean filled, int radius, boolean value, Symmetry symmetry) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);
        double cx = (minX + maxX) / 2.0;
        double cz = (minZ + maxZ) / 2.0;
        double rx = Math.max(0.5, (maxX - minX) / 2.0);
        double rz = Math.max(0.5, (maxZ - minZ) / 2.0);

        if (filled) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double dx = (x - cx) / rx;
                    double dz = (z - cz) / rz;
                    if (dx * dx + dz * dz <= 1.0) {
                        paint(x, z, 0, value, symmetry);
                    }
                }
            }
        } else {
            int steps = Math.max(16, (int) Math.round(Math.PI * (rx + rz)));
            for (int i = 0; i <= steps; i++) {
                double theta = i * (2.0 * Math.PI / steps);
                int px = (int) Math.round(cx + rx * Math.cos(theta));
                int pz = (int) Math.round(cz + rz * Math.sin(theta));
                paint(px, pz, radius, value, symmetry);
            }
        }
    }

    public void drawEllipseHeight(int x0, int z0, int x1, int z1, boolean filled, int radius, int tier, Symmetry symmetry) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);
        double cx = (minX + maxX) / 2.0;
        double cz = (minZ + maxZ) / 2.0;
        double rx = Math.max(0.5, (maxX - minX) / 2.0);
        double rz = Math.max(0.5, (maxZ - minZ) / 2.0);

        if (filled) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double dx = (x - cx) / rx;
                    double dz = (z - cz) / rz;
                    if (dx * dx + dz * dz <= 1.0) {
                        paintHeight(x, z, 0, tier, symmetry);
                    }
                }
            }
        } else {
            int steps = Math.max(16, (int) Math.round(Math.PI * (rx + rz)));
            for (int i = 0; i <= steps; i++) {
                double theta = i * (2.0 * Math.PI / steps);
                int px = (int) Math.round(cx + rx * Math.cos(theta));
                int pz = (int) Math.round(cz + rz * Math.sin(theta));
                paintHeight(px, pz, radius, tier, symmetry);
            }
        }
    }

    public void rebuildShape() {
        pushUndo();
        boolean[] next = new boolean[resolution * resolution];
        byte[] nextHeight = defaultHeight(resolution);

        Random random = new Random(shapeSeed);
        double rotation = random.nextDouble() * Math.PI * 2.0;
        List<ArchipelagoBlob> archipelago = preset == Preset.ARCHIPELAGO
            ? createArchipelago(shapeSeed) : List.of();

        for (int z = 0; z < resolution; z++) {
            double nz = ((z + 0.5) / resolution) * 2.0 - 1.0;
            for (int x = 0; x < resolution; x++) {
                double nx = ((x + 0.5) / resolution) * 2.0 - 1.0;
                double rx = nx * Math.cos(rotation) - nz * Math.sin(rotation);
                double rz = nx * Math.sin(rotation) + nz * Math.cos(rotation);
                double r = Math.hypot(nx, nz);
                double rRot = Math.hypot(rx, rz);
                double theta = Math.atan2(rz, rx);

                double noise = fbm(nx * 3.1, nz * 3.1, shapeSeed) * 0.10
                    + fbm(nx * 7.4, nz * 7.4, shapeSeed ^ 0x5DEECE66DL) * 0.035;

                boolean value = false;
                switch (preset) {
                    case NATURAL -> value = Math.hypot(nx / 0.72, nz / 0.68) < 1.0 + noise;
                    case ROUND -> value = Math.hypot(nx / 0.70, nz / 0.70) < 1.0 + noise * 0.30;
                    case LONG -> value = Math.hypot(rx / 0.78, rz / 0.47) < 1.0 + noise * 0.65;
                    case CRESCENT -> value = Math.hypot(nx / 0.70, nz / 0.70) < 1.0 + noise * 0.45
                        && Math.hypot((nx - 0.27) / 0.59, (nz + 0.02) / 0.58) > 1.0;
                    case ARCHIPELAGO -> value = archipelagoLand(nx, nz, archipelago);

                    case ATOLL -> {
                        double rOut = 0.72 + noise * 0.22;
                        double rIn = 0.44 + noise * 0.15;
                        boolean ring = rRot >= rIn && rRot <= rOut;
                        // Single ocean channel opening
                        boolean channel = Math.abs(theta) < 0.24;
                        value = ring && !channel;
                    }

                    case VOLCANO -> {
                        double rOut = 0.70 + noise * 0.20;
                        double rIn = 0.22 + noise * 0.10;
                        value = r >= rIn && r <= rOut;
                        if (value) {
                            if (r < 0.36) nextHeight[z * resolution + x] = (byte) 3;
                            else if (r < 0.52) nextHeight[z * resolution + x] = (byte) 2;
                            else nextHeight[z * resolution + x] = (byte) 1;
                        }
                    }

                    case FJORDS -> {
                        boolean body = r < 0.74 + noise * 0.20;
                        if (body) {
                            double rotTheta = theta + 0.18 * Math.sin(r * 6.0);
                            double step = 2.0 * Math.PI / 5.0;
                            boolean fjordCut = false;
                            for (int k = 0; k < 5; k++) {
                                double target = k * step - Math.PI;
                                double diff = Math.abs(rotTheta - target);
                                while (diff > Math.PI) diff = Math.abs(diff - 2.0 * Math.PI);
                                if (r > 0.20 && diff < 0.12 * ((r - 0.20) / 0.54)) {
                                    fjordCut = true;
                                    break;
                                }
                            }
                            value = !fjordCut;
                        }
                    }

                    case TWIN_ISLANDS -> {
                        double dA = Math.hypot((rx + 0.33) / 0.34, rz / 0.30);
                        double dB = Math.hypot((rx - 0.33) / 0.32, rz / 0.28);
                        boolean isA = dA < 1.0 + noise * 0.20;
                        boolean isB = dB < 1.0 + fbm(rx * 3.5, rz * 3.5, shapeSeed ^ 0x99AL) * 0.20;
                        value = (isA || isB) && Math.abs(rx) >= 0.08;
                    }

                    case INLAND_SEA -> {
                        boolean continent = r < 0.78 + noise * 0.20;
                        double seaDist = Math.hypot(nx - 0.06 * Math.cos(rotation), nz - 0.06 * Math.sin(rotation));
                        boolean sea = seaDist < 0.36 + fbm(nx * 4.0, nz * 4.0, shapeSeed ^ 0xBEEFL) * 0.12;
                        value = continent && !sea;
                    }

                    case HORSESHOE_BAY -> {
                        boolean outer = Math.hypot(rx / 0.75, rz / 0.70) < 1.0 + noise * 0.25;
                        double bayDist = Math.hypot((rx - 0.25) / 0.46, rz / 0.34);
                        value = outer && (bayDist >= 1.0);
                    }

                    case RIDGE_ISLAND -> {
                        double clampedRx = Math.max(-0.80, Math.min(0.80, rx));
                        double maxRz = 0.28 * Math.sqrt(Math.max(0.0, 1.0 - (clampedRx / 0.82) * (clampedRx / 0.82)))
                            + noise * 0.10;
                        value = Math.abs(rz) < maxRz;
                        if (value) {
                            double distSpine = Math.abs(rz + 0.03);
                            if (distSpine < 0.06) nextHeight[z * resolution + x] = (byte) 3;
                            else if (distSpine < 0.14) nextHeight[z * resolution + x] = (byte) 2;
                            else nextHeight[z * resolution + x] = (byte) 1;
                        }
                    }
                }
                next[z * resolution + x] = value;
            }
        }

        // Suppress border pixels strictly for new presets
        if (preset != Preset.NATURAL && preset != Preset.ROUND && preset != Preset.LONG
            && preset != Preset.CRESCENT && preset != Preset.ARCHIPELAGO) {
            for (int z = 0; z < resolution; z++) {
                for (int x = 0; x < resolution; x++) {
                    if (x == 0 || z == 0 || x == resolution - 1 || z == resolution - 1) {
                        next[z * resolution + x] = false;
                    }
                }
            }
        }

        this.land = next;
        this.height = nextHeight;
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
        ArrayList<ArchipelagoBlob> blobs = new ArrayList<>();
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

    public long shapeSeed() { return shapeSeed; }

    public void rerollBiomes() { biomeSeed = new Random().nextLong(); }
    public void setBiomeSeed(long seed) { this.biomeSeed = seed; }
    public long biomeSeed() { return biomeSeed; }

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

    public void unpackMask(String base64) {
        if (base64 == null || base64.isEmpty()) return;
        pushUndo();
        try {
            byte[] packed = Base64.getDecoder().decode(base64);
            int total = resolution * resolution;
            for (int i = 0; i < total && (i >>> 3) < packed.length; i++) {
                land[i] = ((packed[i >>> 3] >>> (i & 7)) & 1) != 0;
            }
        } catch (Exception ignored) {}
    }

    public String packedHeight() {
        byte[] packed = new byte[(height.length + 3) / 4];
        for (int i = 0; i < height.length; i++) {
            int val = height[i] & 3;
            packed[i >>> 2] |= (byte) (val << ((i & 3) * 2));
        }
        return Base64.getEncoder().encodeToString(packed);
    }

    public void unpackHeight(String base64) {
        if (base64 == null || base64.isEmpty()) return;
        pushUndo();
        this.height = unpackHeight(base64, resolution * resolution);
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
    public double worldX(int pixelX) { return ((pixelX + 0.5) / resolution - 0.5) * worldSpan(); }
    public double worldZ(int pixelZ) { return ((pixelZ + 0.5) / resolution - 0.5) * worldSpan(); }

    public com.hack337.omniisle.continent.IslandTerrain createTerrain() {
        double halfSpan = worldSpan() / 2.0;
        int[] sp = effectiveSpawnPx();
        com.hack337.omniisle.continent.IslandShapeConfig cfg = new com.hack337.omniisle.continent.IslandShapeConfig(
            generationVersionNumber(), isletDensity, riverDensity,
            isWorldBorderEnabled(), worldBorderMargin,
            hasCustomHeight()
                ? java.util.Optional.of(new com.hack337.omniisle.continent.IslandShapeConfig.HeightLayer(2, packedHeight()))
                : java.util.Optional.empty(),
            java.util.Optional.of(new com.hack337.omniisle.continent.IslandShapeConfig.SpawnPos(
                (int) Math.round(worldX(sp[0])), (int) Math.round(worldZ(sp[1])))),
            java.util.Collections.emptyList(),
            java.util.Optional.of(new com.hack337.omniisle.continent.IslandMask(
                resolution, resolution,
                -halfSpan, halfSpan, -halfSpan, halfSpan,
                relief.coastWidth, 400.0,
                packedMask())),
            biomeSeed,
            generationVersionNumber() >= 3
                ? java.util.Optional.of(shoreSettings())
                : java.util.Optional.empty());
        return com.hack337.omniisle.continent.IslandTerrain.create(cfg);
    }

    public int biomeWeight(BiomePreview.Type type) {
        Integer value = biomeWeights.get(type);
        return value != null ? BiomeShare.clampWeight(value) : BiomeShare.DEFAULT_WEIGHT;
    }

    public static boolean isBiomeWeightEditable(BiomePreview.Type type) {
        return type != null && type.isLand() && !BiomeShare.isTerrainDriven(type.family);
    }

    public void setBiomeWeight(BiomePreview.Type type, int weight) {
        if (!isBiomeWeightEditable(type)) return;
        int clamped = BiomeShare.clampWeight(weight);
        if (clamped <= 0) {
            enabledBiomes.remove(type);
            biomeWeights.remove(type);
            return;
        }
        enabledBiomes.add(type);
        if (clamped == BiomeShare.DEFAULT_WEIGHT) {
            biomeWeights.remove(type);
        } else {
            biomeWeights.put(type, clamped);
        }
    }

    public void resetBiomeWeight(BiomePreview.Type type) {
        if (isBiomeWeightEditable(type)) {
            setBiomeWeight(type, BiomeShare.DEFAULT_WEIGHT);
        }
    }

    /** Applies an inline percent-field edit; returns true when the draft actually changed. */
    public boolean applyBiomeWeightText(BiomePreview.Type type, String text) {
        if (!isBiomeWeightEditable(type) || text == null) return false;
        int parsed;
        try {
            parsed = Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return false;
        }
        int target = BiomeShare.clampWeight(parsed);
        boolean enabled = isBiomeEnabled(type);
        if (target <= 0) {
            if (!enabled) return false;
            pushUndo();
            setBiomeWeight(type, 0);
            return true;
        }
        if (enabled && biomeWeight(type) == target) return false;
        pushUndo();
        setBiomeWeight(type, target);
        return true;
    }

    public void resetAllBiomeWeights() {
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (type.isLand() && enabledBiomes.contains(type)) biomeWeights.remove(type);
        }
    }

    public boolean hasNonNeutralWeights() {
        for (BiomePreview.Type type : enabledBiomes) {
            if (type.isLand() && biomeWeight(type) != BiomeShare.DEFAULT_WEIGHT) return true;
        }
        return false;
    }

    public Map<BiomePreview.Type, Integer> biomeWeightsView() {
        return Map.copyOf(biomeWeights);
    }

    public double biomeBias(BiomePreview.Type type) {
        Double value = biomeBias.get(type);
        return value != null ? BiomeShare.clampBias(value) : 0.0;
    }

    public double achievedShare(BiomePreview.Type type) {
        Double value = achievedShares.get(type);
        return value != null ? value : -1.0;
    }

    public double targetShare(BiomePreview.Type type) {
        Double value = targetShares.get(type);
        return value != null ? value : -1.0;
    }

    public boolean sharesFeasible() { return sharesFeasible; }
    public double sharesMaxErrorPp() { return sharesMaxErrorPp; }
    public int calibrationSamples() { return calibrationSamples; }

    public void applyCalibration(BiomeShareCalibrator.Result result) {
        biomeBias.clear();
        achievedShares.clear();
        targetShares.clear();
        if (result == null) return;
        sharesFeasible = result.feasible();
        sharesMaxErrorPp = result.maxErrorPp();
        calibrationSamples = result.samples();
        for (Map.Entry<String, Double> entry : result.bias().entrySet()) {
            BiomePreview.Type type = biomeType(entry.getKey());
            if (type != null) biomeBias.put(type, BiomeShare.clampBias(entry.getValue()));
        }
        for (Map.Entry<String, Double> entry : result.achieved().entrySet()) {
            BiomePreview.Type type = biomeType(entry.getKey());
            if (type != null) achievedShares.put(type, entry.getValue());
        }
        for (Map.Entry<String, Double> entry : result.target().entrySet()) {
            BiomePreview.Type type = biomeType(entry.getKey());
            if (type != null) targetShares.put(type, entry.getValue());
        }
    }

    public void applySharedSettings(Map<String, Integer> weights, Map<String, Double> bias,
                                    Map<String, Double> achieved, Map<String, Double> target,
                                    boolean feasible) {
        biomeWeights.clear();
        if (weights != null) {
            for (Map.Entry<String, Integer> entry : weights.entrySet()) {
                BiomePreview.Type type = biomeType(entry.getKey());
                if (type == null || entry.getValue() == null) continue;
                setBiomeWeight(type, entry.getValue());
            }
        }
        applyCalibration(new BiomeShareCalibrator.Result(
            weights != null ? weights : Map.of(), bias != null ? bias : Map.of(),
            achieved != null ? achieved : Map.of(), target != null ? target : Map.of(),
            feasible, 0.0, 0, 0));
    }

    public void clearCalibration() {
        biomeBias.clear();
        achievedShares.clear();
        targetShares.clear();
        sharesFeasible = true;
        sharesMaxErrorPp = 0.0;
        calibrationSamples = 0;
    }

    private static BiomePreview.Type biomeType(String id) {
        int index = BiomeShare.indexOf(id);
        if (index < 0 || index >= BiomePreview.Type.values().length) return null;
        BiomePreview.Type type = BiomePreview.Type.values()[index];
        return type.isLand() && type.id.equals(BiomeShare.pathOf(id)) ? type : null;
    }

    public Map<String, Integer> biomeShareWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : draftLandWeightMap().entrySet()) {
            if (entry.getValue() != BiomeShare.DEFAULT_WEIGHT) weights.put(entry.getKey(), entry.getValue());
        }
        return weights;
    }

    public Map<String, Integer> draftLandWeightMap() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (!type.isLand() || !enabledBiomes.contains(type)) continue;
            int weight = biomeWeight(type);
            if (weight > 0) weights.put(type.id, weight);
        }
        return weights;
    }

    public Map<String, Double> biomeShareBias() {
        Map<String, Double> bias = new LinkedHashMap<>();
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            double value = biomeBias(type);
            if (type.isLand() && !BiomeShare.isZeroBias(value)) bias.put(type.id, value);
        }
        return bias;
    }

    public Map<String, Double> biomeShareAchieved() {
        Map<String, Double> achieved = new LinkedHashMap<>();
        for (Map.Entry<BiomePreview.Type, Double> entry : achievedShares.entrySet()) {
            achieved.put(entry.getKey().id, entry.getValue());
        }
        return achieved;
    }

    public Map<String, Double> biomeShareTarget() {
        Map<String, Double> target = new LinkedHashMap<>();
        for (Map.Entry<BiomePreview.Type, Double> entry : targetShares.entrySet()) {
            target.put(entry.getKey().id, entry.getValue());
        }
        return target;
    }

    public BiomeShare.Settings biomeShareSettings() {
        return BiomeShare.Settings.fromMaps(biomeShareWeights(), biomeShareBias(),
            biomeShareAchieved(), biomeShareTarget(), sharesFeasible).sanitized();
    }

    /**
     * Fills the weighted land candidate arrays in config/enum order.
     * Terrain-driven variants stay in the list so zero-bias selection stays legacy-exact.
     */
    public int fillLandCandidates(int[] ids, double[] factors) {
        int count = 0;
        for (BiomePreview.Type type : BiomePreview.Type.values()) {
            if (!type.isLand() || !OVERWORLD_BIOMES.contains(type) || !enabledBiomes.contains(type)) continue;
            if (BiomeShare.isTerrainDriven(type.family)) {
                ids[count] = BiomeShare.indexOf(type.id);
                factors[count] = 1.0;
                count++;
                continue;
            }
            int weight = biomeWeight(type);
            if (weight <= 0) continue;
            ids[count] = BiomeShare.indexOf(type.id);
            factors[count] = BiomeShare.biasFactor(biomeBias(type));
            count++;
        }
        return count;
    }

    public int selectWeightedLand(int preferredFamily, double temperature, double humidity,
                                  int worldX, int worldZ, int[] ids, double[] factors, int count,
                                  BiomeShare.SelectorScratch scratch) {
        boolean uniform = BiomeShare.areFactorsUniform(factors, count);
        double ticket = uniform ? 0.0 : BiomeShare.weightedTicketFor(biomeSeed, worldX, worldZ);
        double legacyTicket = uniform ? BiomeShare.ticketFor(preferredFamily, biomeSeed, worldX, worldZ) : 0.0;
        return BiomeShare.select(preferredFamily, temperature, humidity, ids, factors, count,
            ticket, legacyTicket, scratch);
    }

    /** Resolves a preview selection, applying calibrated shares exactly like the worldgen source. */
    public BiomePreview.Type selectPreviewBiome(BiomePreview.Selection selection, int worldX, int worldZ) {
        return resolvePreviewBiome(selection, worldX, worldZ, worldX, worldZ);
    }

    public BiomePreview.Type selectPreviewBiome(BiomePreview.Selection selection, double worldX, double worldZ) {
        return resolvePreviewBiome(selection, (int)Math.floor(worldX), (int)Math.floor(worldZ), worldX, worldZ);
    }

    private BiomePreview.Type resolvePreviewBiome(BiomePreview.Selection selection,
                                                  int weightX, int weightZ, double worldX, double worldZ) {
        if (selection == null) return null;
        if (!selection.isLand() || !hasNonNeutralWeights()
            || BiomeShare.isTerrainDriven(selection.landFamily())) {
            return selectBiome(selection.type(), worldX, worldZ);
        }
        ShareScratch scratch = shareScratch.get();
        int count = fillLandCandidates(scratch.ids, scratch.factors);
        if (count > 0) {
            int selected = selectWeightedLand(selection.landFamily(), selection.temperature(),
                selection.humidity(), weightX, weightZ, scratch.ids, scratch.factors, count, scratch.selector);
            BiomePreview.Type chosen = selected >= 0 ? BiomePreview.landTypeForCatalog(selected) : null;
            if (chosen != null) return chosen;
        }
        return selectBiome(selection.type(), worldX, worldZ);
    }

    public int islandSize() { return islandSize; }
    public void setIslandSize(int islandSize) { this.islandSize = Math.max(2500, Math.min(8000, islandSize)); }
    public SetView enabledBiomes() { return new SetView(EnumSet.copyOf(enabledBiomes)); }
    public boolean isBiomeEnabled(BiomePreview.Type type) { return enabledBiomes.contains(type); }

    public boolean toggleBiome(BiomePreview.Type type) {
        if (!enabledBiomes.remove(type)) {
            enabledBiomes.add(type);
        }
        if (type.isLand()) {
            biomeWeights.remove(type);
        }
        return true;
    }

    public void selectAllBiomes(BiomePreview.Category category) {
        for (BiomePreview.Type type : OVERWORLD_BIOMES) {
            if (category == null || type.category == category) {
                if (enabledBiomes.add(type) && type.isLand()) biomeWeights.remove(type);
            }
        }
    }

    public void deselectAllBiomes(BiomePreview.Category category) {
        if (category == null) {
            enabledBiomes.clear();
            biomeWeights.clear();
        } else {
            enabledBiomes.removeIf(type -> type.category == category);
            if (category == BiomePreview.Category.LAND) biomeWeights.clear();
        }
    }

    public void restoreBiomesFromIds(List<String> ids) {
        enabledBiomes.clear();
        biomeWeights.clear();
        for (String id : ids) {
            for (BiomePreview.Type type : OVERWORLD_BIOMES) {
                if (type.id.equals(id)) {
                    enabledBiomes.add(type);
                    break;
                }
            }
        }
        if (enabledBiomes.isEmpty()) {
            enabledBiomes.addAll(defaultBiomes());
        }
    }

    public void restoreStructuresFromNames(List<String> names) {
        enabledStructures.clear();
        for (String name : names) {
            try {
                enabledStructures.add(StructureOption.valueOf(name));
            } catch (Exception ignored) {}
        }
    }

    public void applyTheme(BiomeTheme theme) {
        this.theme = theme;
        enabledBiomes.clear();
        biomeWeights.clear();
        biomeBias.clear();
        achievedShares.clear();
        targetShares.clear();
        sharesFeasible = true;
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
    public EnumSet<StructureOption> enabledStructures() { return EnumSet.copyOf(enabledStructures); }
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
            if (generationVersion == GenerationVersion.V3 && category == BiomePreview.Category.SHORE) {
                return List.of();
            }
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
