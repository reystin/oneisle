package com.hack337.omniisle.client;

/** Fast client-side counterpart of OmniBiomeSource, used only for the editor preview. */
public final class BiomePreview {
    private static final double OCEAN_START = -0.30;
    private static final double INLAND_START = 0.02;

    public enum Category { LAND, SHORE, OCEAN, RIVER, CAVE }

    public enum Type {
        // Surface land. Variants share a climate family with one of the 18 primary biomes.
        SNOWY_PLAINS("snowy_plains", 0xFFF1F4F4, Category.LAND, 0),
        ICE_SPIKES("ice_spikes", 0xFFD7EEEE, Category.LAND, 0),
        TAIGA("taiga", 0xFF0B6659, Category.LAND, 1),
        SNOWY_TAIGA("snowy_taiga", 0xFF4F7772, Category.LAND, 1),
        OLD_GROWTH_PINE_TAIGA("old_growth_pine_taiga", 0xFF486B4A, Category.LAND, 1),
        OLD_GROWTH_SPRUCE_TAIGA("old_growth_spruce_taiga", 0xFF335A45, Category.LAND, 1),
        BIRCH_FOREST("birch_forest", 0xFF65A36F, Category.LAND, 2),
        OLD_GROWTH_BIRCH_FOREST("old_growth_birch_forest", 0xFF78AD72, Category.LAND, 2),
        FOREST("forest", 0xFF2E7D32, Category.LAND, 3),
        FLOWER_FOREST("flower_forest", 0xFF5A9C55, Category.LAND, 3),
        WINDSWEPT_FOREST("windswept_forest", 0xFF3E7546, Category.LAND, 3),
        PLAINS("plains", 0xFF8DB84D, Category.LAND, 4),
        SUNFLOWER_PLAINS("sunflower_plains", 0xFFA9C84E, Category.LAND, 4),
        MEADOW("meadow", 0xFF76B852, Category.LAND, 5),
        GROVE("grove", 0xFF6B8F7A, Category.LAND, 5),
        CHERRY_GROVE("cherry_grove", 0xFFFFB7C5, Category.LAND, 6),
        DARK_FOREST("dark_forest", 0xFF174C24, Category.LAND, 7),
        SWAMP("swamp", 0xFF405C3B, Category.LAND, 8),
        MANGROVE_SWAMP("mangrove_swamp", 0xFF294C38, Category.LAND, 9),
        JUNGLE("jungle", 0xFF4C7A1B, Category.LAND, 10),
        BAMBOO_JUNGLE("bamboo_jungle", 0xFF5E8E2B, Category.LAND, 10),
        SPARSE_JUNGLE("sparse_jungle", 0xFF648B35, Category.LAND, 10),
        SAVANNA("savanna", 0xFFC2A35D, Category.LAND, 11),
        SAVANNA_PLATEAU("savanna_plateau", 0xFFB39351, Category.LAND, 11),
        WINDSWEPT_SAVANNA("windswept_savanna", 0xFFA98B49, Category.LAND, 11),
        DESERT("desert", 0xFFF4D06F, Category.LAND, 12),
        BADLANDS("badlands", 0xFFD26A3A, Category.LAND, 13),
        ERODED_BADLANDS("eroded_badlands", 0xFFC95B32, Category.LAND, 13),
        WOODED_BADLANDS("wooded_badlands", 0xFFB36B3D, Category.LAND, 13),
        STONY_PEAKS("stony_peaks", 0xFF8C9092, Category.LAND, 14),
        WINDSWEPT_HILLS("windswept_hills", 0xFF7E8C78, Category.LAND, 14),
        WINDSWEPT_GRAVELLY_HILLS("windswept_gravelly_hills", 0xFF8D9189, Category.LAND, 14),
        JAGGED_PEAKS("jagged_peaks", 0xFFDDE8E8, Category.LAND, 15),
        FROZEN_PEAKS("frozen_peaks", 0xFFE7F2F4, Category.LAND, 15),
        SNOWY_SLOPES("snowy_slopes", 0xFFD4E2E1, Category.LAND, 15),
        MUSHROOM_FIELDS("mushroom_fields", 0xFFB75AA5, Category.LAND, 16),
        PALE_GARDEN("pale_garden", 0xFF9AA89A, Category.LAND, 17),

        BEACH("beach", 0xFFF0E6A0, Category.SHORE, -1),
        STONY_SHORE("stony_shore", 0xFF90989B, Category.SHORE, -1),
        SNOWY_BEACH("snowy_beach", 0xFFF4F4E8, Category.SHORE, -1),

        OCEAN("ocean", 0xFF1565C0, Category.OCEAN, -1),
        DEEP_OCEAN("deep_ocean", 0xFF123B73, Category.OCEAN, -1),
        WARM_OCEAN("warm_ocean", 0xFF20BCE5, Category.OCEAN, -1),
        LUKEWARM_OCEAN("lukewarm_ocean", 0xFF168FCE, Category.OCEAN, -1),
        DEEP_LUKEWARM_OCEAN("deep_lukewarm_ocean", 0xFF116DA7, Category.OCEAN, -1),
        COLD_OCEAN("cold_ocean", 0xFF315B89, Category.OCEAN, -1),
        DEEP_COLD_OCEAN("deep_cold_ocean", 0xFF233F63, Category.OCEAN, -1),
        FROZEN_OCEAN("frozen_ocean", 0xFF7798C8, Category.OCEAN, -1),
        DEEP_FROZEN_OCEAN("deep_frozen_ocean", 0xFF536F9C, Category.OCEAN, -1),

        RIVER("river", 0xFF2879C7, Category.RIVER, -1),
        FROZEN_RIVER("frozen_river", 0xFF7FA7CF, Category.RIVER, -1),

        LUSH_CAVES("lush_caves", 0xFF4F8F55, Category.CAVE, -1),
        DRIPSTONE_CAVES("dripstone_caves", 0xFF8A6F62, Category.CAVE, -1),
        DEEP_DARK("deep_dark", 0xFF183B3B, Category.CAVE, -1);

        public final String id;
        public final int color;
        public final Category category;
        public final int family;
        Type(String id, int color, Category category, int family) {
            this.id = id;
            this.color = color;
            this.category = category;
            this.family = family;
        }
        public boolean isLand() { return category == Category.LAND; }
        public String biomeId() { return "minecraft:" + id; }
        public String translationKey() { return "biome.minecraft." + id; }
    }

    private BiomePreview() {}

    public static Type surface(double continentalness, double x, double z, long seed) {
        Climate c = climateAt(x, z, 720.0, seed);
        if (continentalness < OCEAN_START) return ocean(c, continentalness);
        if (continentalness < INLAND_START) {
            if (c.temperature < 0.24) return Type.SNOWY_BEACH;
            return c.mountains > 0.67 ? Type.STONY_SHORE : Type.BEACH;
        }
        double mushroomX = x + 560.0 + c.warpX * 0.35;
        double mushroomZ = z - 320.0 + c.warpZ * 0.35;
        if (mushroomX * mushroomX + mushroomZ * mushroomZ < 190.0 * 190.0) return Type.MUSHROOM_FIELDS;
        if (continentalness > 0.18 && c.mountains > 0.68) {
            if (c.temperature < 0.30) return Type.JAGGED_PEAKS;
            if (c.temperature > 0.68) return Type.STONY_PEAKS;
            return c.humidity > 0.45 ? Type.CHERRY_GROVE : Type.MEADOW;
        }
        if (c.temperature < 0.26) return c.humidity < 0.58 ? Type.SNOWY_PLAINS : Type.TAIGA;
        if (c.temperature < 0.43) {
            if (c.humidity < 0.48) return Type.MEADOW;
            return c.humidity < 0.72 ? Type.BIRCH_FOREST : Type.TAIGA;
        }
        if (c.temperature < 0.68) {
            if (c.humidity < 0.46) return Type.PLAINS;
            if (c.humidity < 0.53) return Type.FOREST;
            if (c.humidity < 0.58) return Type.PALE_GARDEN;
            if (c.humidity < 0.62) return Type.DARK_FOREST;
            return Type.SWAMP;
        }
        if (c.temperature < 0.80) {
            if (c.humidity < 0.25) return Type.BADLANDS;
            if (c.humidity < 0.48) return Type.SAVANNA;
            if (c.humidity < 0.58 || continentalness >= 0.32) return Type.JUNGLE;
            return Type.MANGROVE_SWAMP;
        }
        if (c.humidity < 0.40) return Type.DESERT;
        if (c.humidity < 0.58) return Type.BADLANDS;
        if (c.humidity < 0.72) return Type.SAVANNA;
        return continentalness < 0.32 ? Type.MANGROVE_SWAMP : Type.JUNGLE;
    }

    public static boolean isDeepDark(double x, double z, long seed) {
        return fbm(x / 520.0, z / 520.0, saltedSeed(seed, 977)) > 0.02;
    }

    private static Type ocean(Climate c, double continentalness) {
        boolean deep = continentalness < -0.58;
        if (c.temperature < 0.16) return deep ? Type.DEEP_FROZEN_OCEAN : Type.FROZEN_OCEAN;
        if (c.temperature < 0.32) return deep ? Type.DEEP_COLD_OCEAN : Type.COLD_OCEAN;
        if (c.temperature > 0.82) return Type.WARM_OCEAN;
        if (c.temperature > 0.66) return deep ? Type.DEEP_LUKEWARM_OCEAN : Type.LUKEWARM_OCEAN;
        return deep ? Type.DEEP_OCEAN : Type.OCEAN;
    }

    private static Climate climateAt(double x, double z, double scale, long seed) {
        double warpX = fbm(x / scale, z / scale, saltedSeed(seed, 101)) * scale * 0.34;
        double warpZ = fbm(x / scale, z / scale, saltedSeed(seed, 211)) * scale * 0.34;
        double wx = x + warpX;
        double wz = z + warpZ;
        double temperature = clamp01(0.50 + wz / 3200.0 + fbm(wx / (scale * 1.35), wz / (scale * 1.35), saltedSeed(seed, 307)) * 0.17);
        double humidity = clamp01(0.50 + fbm(wx / scale, wz / scale, saltedSeed(seed, 401)) * 0.46);
        double ridge = 1.0 - Math.abs(fbm(wx / (scale * 0.82), wz / (scale * 0.82), saltedSeed(seed, 503)));
        double mountains = 0.60 * ridge + 0.40 * clamp01(fbm(wx / (scale * 1.7), wz / (scale * 1.7), saltedSeed(seed, 601)) * 0.5 + 0.5);
        return new Climate(temperature, humidity, mountains, warpX, warpZ);
    }

    private static int saltedSeed(long seed, int salt) {
        if (seed == 0L) return salt;
        long mixed = seed + 0x9E3779B97F4A7C15L * salt;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int)(mixed ^ (mixed >>> 31));
    }

    private static double fbm(double x, double z, int seed) {
        double value = 0.0, amplitude = 0.58, sum = 0.0;
        for (int octave = 0; octave < 4; octave++) {
            value += valueNoise(x, z, seed + octave * 1013) * amplitude;
            sum += amplitude;
            x = x * 2.03 + 17.17;
            z = z * 2.03 - 11.31;
            amplitude *= 0.5;
        }
        return value / sum;
    }

    private static double valueNoise(double x, double z, int seed) {
        int x0 = (int)Math.floor(x), z0 = (int)Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = lerp(hashUnit(x0, z0, seed), hashUnit(x0 + 1, z0, seed), tx);
        double b = lerp(hashUnit(x0, z0 + 1, seed), hashUnit(x0 + 1, z0 + 1, seed), tx);
        return lerp(a, b, tz);
    }

    private static double hashUnit(int x, int z, int seed) {
        int value = x * 0x1f1f1f1f ^ z * 0x6c8e9cf5 ^ seed * 0x9e3779b9;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        return Math.floorMod(value, 65536) / 32767.5 - 1.0;
    }

    private static double smooth(double v) { return v * v * (3.0 - 2.0 * v); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private record Climate(double temperature, double humidity, double mountains, double warpX, double warpZ) {}
}
