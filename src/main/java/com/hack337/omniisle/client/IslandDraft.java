package com.hack337.omniisle.client;

import com.hack337.omniisle.platform.TargetPlatform;
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
        BiomePreview.Type.DRIPSTONE_CAVES, BiomePreview.Type.DEEP_DARK);

    public enum Preset {
        NATURAL("omniisle.preset.natural"),
        ROUND("omniisle.preset.round"),
        LONG("omniisle.preset.long"),
        CRESCENT("omniisle.preset.crescent"),
        ARCHIPELAGO("omniisle.preset.archipelago");

        public final String translationKey;
        Preset(String translationKey) { this.translationKey = translationKey; }
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
        BURIED_TREASURE("omniisle.structure.buried_treasure");

        public final String translationKey;
        StructureOption(String translationKey) { this.translationKey = translationKey; }
    }

    public static final List<StructureOption> STRUCTURE_OPTIONS = Arrays.stream(StructureOption.values())
        .filter(option -> TargetPlatform.HAS_TRIAL_CHAMBERS || option != StructureOption.TRIAL_CHAMBERS)
        .toList();

    private boolean[] land;
    private int islandSize;
    private long shapeSeed;
    private long biomeSeed;
    private Preset preset;
    private EnumSet<BiomePreview.Type> enabledBiomes;
    private EnumSet<StructureOption> enabledStructures;

    public IslandDraft() {
        this(new boolean[RESOLUTION * RESOLUTION], DEFAULT_ISLAND_SIZE,
            new Random().nextLong(), new Random().nextLong(), Preset.NATURAL, defaultBiomes(),
            defaultStructures());
        rebuildShape();
    }

    private IslandDraft(boolean[] land, int islandSize, long shapeSeed, long biomeSeed, Preset preset,
                              EnumSet<BiomePreview.Type> enabledBiomes) {
        this(land, islandSize, shapeSeed, biomeSeed, preset, enabledBiomes, defaultStructures());
    }

    private IslandDraft(boolean[] land, int islandSize, long shapeSeed, long biomeSeed, Preset preset,
                              EnumSet<BiomePreview.Type> enabledBiomes,
                              EnumSet<StructureOption> enabledStructures) {
        this.land = land;
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
        return new IslandDraft(Arrays.copyOf(land, land.length), islandSize, shapeSeed, biomeSeed, preset,
            EnumSet.copyOf(enabledBiomes), EnumSet.copyOf(enabledStructures));
    }

    public boolean isLand(int x, int z) {
        return x >= 0 && z >= 0 && x < RESOLUTION && z < RESOLUTION && land[z * RESOLUTION + x];
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

    public void rebuildShape() {
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

    public void rerollBiomes() { biomeSeed = new Random().nextLong(); }

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

    public double worldSpan() { return islandSize * 1.36; }
    public double worldX(int pixelX) { return ((pixelX + 0.5) / RESOLUTION - 0.5) * worldSpan(); }
    public double worldZ(int pixelZ) { return ((pixelZ + 0.5) / RESOLUTION - 0.5) * worldSpan(); }
    public int islandSize() { return islandSize; }
    public void setIslandSize(int islandSize) { this.islandSize = Math.max(2500, Math.min(8000, islandSize)); }
    public long biomeSeed() { return biomeSeed; }
    public SetView enabledBiomes() { return new SetView(EnumSet.copyOf(enabledBiomes)); }
    public boolean isBiomeEnabled(BiomePreview.Type type) { return enabledBiomes.contains(type); }
    public boolean toggleBiome(BiomePreview.Type type) {
        if (enabledBiomes.contains(type)) {
            long remainingInCategory = enabledBiomes.stream()
                .filter(other -> other.category == type.category).count();
            if (remainingInCategory == 1) return false;
            enabledBiomes.remove(type);
        } else {
            enabledBiomes.add(type);
        }
        return true;
    }
    public boolean isStructureEnabled(StructureOption option) { return enabledStructures.contains(option); }
    public void toggleStructure(StructureOption option) {
        if (!enabledStructures.remove(option)) enabledStructures.add(option);
    }

    public BiomePreview.Type selectBiome(BiomePreview.Type preferred, double x, double z) {
        List<BiomePreview.Type> compatible = compatibleChoices(preferred);
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
        return enabledBiomes.stream().filter(type -> type.category == preferred.category).toList();
    }

    public List<BiomePreview.Type> enabledBiomes(BiomePreview.Category category) {
        return enabledBiomes.stream().filter(type -> type.category == category).toList();
    }

    /** Immutable-enough iterable wrapper kept off Minecraft's serialization path. */
    public record SetView(EnumSet<BiomePreview.Type> values) {}
    public Preset preset() { return preset; }
    public void setPreset(Preset preset) { this.preset = preset; rebuildShape(); }

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
