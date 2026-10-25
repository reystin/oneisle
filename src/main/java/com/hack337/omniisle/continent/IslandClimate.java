package com.hack337.omniisle.continent;

import java.util.Locale;

/**
 * Side-neutral shared climate and biome selection logic for OmniIsle.
 * Provides identical climate sampling for both OmniBiomeSource (server)
 * and BiomePreview (client editor).
 */
public final class IslandClimate {
    public static final double OCEAN_START = -0.30;
    public static final double INLAND_START = 0.02;
    public static final double DEEP_OCEAN_THRESHOLD = -0.58;
    public static final double STEEP_SHORE_SLOPE = 0.35;

    public enum GradientMode {
        OFF("omniisle.gradient.off"),
        NORTH_SOUTH("omniisle.gradient.north_south"),
        EAST_WEST("omniisle.gradient.east_west"),
        RADIAL("omniisle.gradient.radial");

        public final String translationKey;
        GradientMode(String translationKey) { this.translationKey = translationKey; }

        public static GradientMode fromString(String name) {
            if (name == null) return NORTH_SOUTH;
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return NORTH_SOUTH;
            }
        }
    }

    public enum SurfaceZone { OCEAN, SHORE, LAND }

    /**
     * Temperature range of a theme palette. The latitudinal gradient covers
     * {@code 0.5 * span} at strength 1, so ALL/STANDARD reach the full range while
     * Nordic, Desert and Tropical stay inside their narrower climate.
     */
    private record ThemeClimate(double centerTemp, double temperatureSpan, double noiseScale) {}

    private static final ThemeClimate THEME_STANDARD = new ThemeClimate(0.50, 0.90, 0.17);
    private static final ThemeClimate THEME_NORDIC = new ThemeClimate(0.22, 0.32, 0.08);
    private static final ThemeClimate THEME_TROPICAL = new ThemeClimate(0.78, 0.28, 0.08);
    private static final ThemeClimate THEME_DESERT = new ThemeClimate(0.82, 0.28, 0.08);
    private static final ThemeClimate THEME_MYSTIC = new ThemeClimate(0.52, 0.44, 0.12);

    public record ClimateValues(double temperature, double humidity, double mountains, double warpX, double warpZ) {}

    public record SampleResult(
        SurfaceZone zone,
        String subType,
        int landFamily,
        double temperature,
        double humidity,
        double mountains
    ) {}

    public static double temperatureSpan(String themeName) {
        return themeClimate(themeName).temperatureSpan();
    }

    /** Peak offset at |u| = 1: half the theme span, scaled by the UI strength. */
    public static double gradientAmplitude(String themeName, double gradientStrength) {
        return amplitude(themeClimate(themeName), gradientStrength);
    }

    private static double amplitude(ThemeClimate theme, double gradientStrength) {
        return 0.5 * theme.temperatureSpan()
            * Math.max(0.0, Math.min(1.0, gradientStrength));
    }

    private static ThemeClimate themeClimate(String themeName) {
        String t = themeName != null ? themeName.toLowerCase(Locale.ROOT) : "standard";
        return switch (t) {
            case "nordic" -> THEME_NORDIC;
            case "tropical" -> THEME_TROPICAL;
            case "desert" -> THEME_DESERT;
            case "mystic" -> THEME_MYSTIC;
            default -> THEME_STANDARD;
        };
    }

    public static ClimateValues climateAt(double x, double z, double scale, long biomeSeed,
                                          GradientMode gradientMode, double gradientStrength,
                                          String themeName, double islandHalfSpan, int version) {
        if (version <= 1) {
            // Legacy 0.5.0 math bit-for-bit
            double warpX = fbm(x / scale, z / scale, saltedSeed(biomeSeed, 101)) * scale * 0.34;
            double warpZ = fbm(x / scale, z / scale, saltedSeed(biomeSeed, 211)) * scale * 0.34;
            double wx = x + warpX;
            double wz = z + warpZ;
            double temperature = clamp01(0.50 + wz / 3200.0 + fbm(wx / (scale * 1.35), wz / (scale * 1.35), saltedSeed(biomeSeed, 307)) * 0.17);
            double humidity = clamp01(0.50 + fbm(wx / scale, wz / scale, saltedSeed(biomeSeed, 401)) * 0.46);
            double ridge = 1.0 - Math.abs(fbm(wx / (scale * 0.82), wz / (scale * 0.82), saltedSeed(biomeSeed, 503)));
            double mountainBands = 0.60 * ridge + 0.40 * clamp01(fbm(wx / (scale * 1.7), wz / (scale * 1.7), saltedSeed(biomeSeed, 601)) * 0.5 + 0.5);
            return new ClimateValues(temperature, humidity, mountainBands, warpX, warpZ);
        }

        // Modern v2 math
        double warpX = fbm(x / scale, z / scale, saltedSeed(biomeSeed, 101)) * scale * 0.34;
        double warpZ = fbm(x / scale, z / scale, saltedSeed(biomeSeed, 211)) * scale * 0.34;
        double wx = x + warpX;
        double wz = z + warpZ;

        // Gradient coordinate u in [-1.0, 1.0]
        double halfSpan = Math.max(100.0, islandHalfSpan);
        double u = switch (gradientMode) {
            case OFF -> 0.0;
            case NORTH_SOUTH -> Math.max(-1.0, Math.min(1.0, wz / halfSpan));
            case EAST_WEST -> Math.max(-1.0, Math.min(1.0, wx / halfSpan));
            case RADIAL -> {
                double dist = Math.hypot(wx, wz) / halfSpan;
                yield Math.max(-1.0, Math.min(1.0, 1.0 - 2.0 * Math.min(1.0, dist)));
            }
        };

        ThemeClimate theme = themeClimate(themeName);
        double gradOffset = u * amplitude(theme, gradientStrength);
        double baseNoise = fbm(wx / (scale * 1.35), wz / (scale * 1.35), saltedSeed(biomeSeed, 307)) * theme.noiseScale();
        double temperature = clamp01(theme.centerTemp() + gradOffset + baseNoise);

        double humidity = clamp01(0.50 + fbm(wx / scale, wz / scale, saltedSeed(biomeSeed, 401)) * 0.46);
        double ridge = 1.0 - Math.abs(fbm(wx / (scale * 0.82), wz / (scale * 0.82), saltedSeed(biomeSeed, 503)));
        double mountainBands = 0.60 * ridge + 0.40 * clamp01(fbm(wx / (scale * 1.7), wz / (scale * 1.7), saltedSeed(biomeSeed, 601)) * 0.5 + 0.5);

        return new ClimateValues(temperature, humidity, mountainBands, warpX, warpZ);
    }

    public static SampleResult sample(double continentalness, double x, double z, double y, double slope,
                                      double scale, long biomeSeed, GradientMode gradientMode, double gradientStrength,
                                      String themeName, double islandHalfSpan, int version) {
        double cQuantized = (double)((long)((float)continentalness * 10000.0f)) / 10000.0;
        ClimateValues c = climateAt(x, z, scale, biomeSeed, gradientMode, gradientStrength, themeName, islandHalfSpan, version);

        if (cQuantized < OCEAN_START) {
            boolean deep = cQuantized < DEEP_OCEAN_THRESHOLD;
            String oceanType;
            if (c.temperature < 0.16) oceanType = deep ? "deep_frozen_ocean" : "frozen_ocean";
            else if (c.temperature < 0.32) oceanType = deep ? "deep_cold_ocean" : "cold_ocean";
            else if (c.temperature > 0.82) oceanType = "warm_ocean";
            else if (c.temperature > 0.66) oceanType = deep ? "deep_lukewarm_ocean" : "lukewarm_ocean";
            else oceanType = deep ? "deep_ocean" : "ocean";
            return new SampleResult(SurfaceZone.OCEAN, oceanType, -1, c.temperature, c.humidity, c.mountains);
        }

        if (cQuantized < INLAND_START) {
            if (y > 86.0) {
                int landFam = selectLandFamily(c, cQuantized, x, z, version, biomeSeed);
                return new SampleResult(SurfaceZone.LAND, "land", landFam, c.temperature, c.humidity, c.mountains);
            }
            if (y > 70.0) {
                return new SampleResult(SurfaceZone.SHORE, "stony_shore", -1, c.temperature, c.humidity, c.mountains);
            }
            if (c.temperature < 0.24) {
                return new SampleResult(SurfaceZone.SHORE, "snowy_beach", -1, c.temperature, c.humidity, c.mountains);
            }
            if (version >= 2 && slope > STEEP_SHORE_SLOPE) {
                return new SampleResult(SurfaceZone.SHORE, "stony_shore", -1, c.temperature, c.humidity, c.mountains);
            }
            if (c.mountains > 0.67) {
                return new SampleResult(SurfaceZone.SHORE, "stony_shore", -1, c.temperature, c.humidity, c.mountains);
            }
            return new SampleResult(SurfaceZone.SHORE, "beach", -1, c.temperature, c.humidity, c.mountains);
        }

        int landFam = selectLandFamily(c, cQuantized, x, z, version, biomeSeed);
        return new SampleResult(SurfaceZone.LAND, "land", landFam, c.temperature, c.humidity, c.mountains);
    }

    /**
     * v3 sample: the shore outcome is computed from the explicit {@link ShoreField}
     * by the caller (which owns the signed-distance terrain). Negative distances
     * below the intertidal limit are always ocean, independent of continentalness.
     */
    public static SampleResult sampleV3(double continentalness, double x, double z, double slope,
                                        double scale, long biomeSeed, GradientMode gradientMode, double gradientStrength,
                                        String themeName, double islandHalfSpan, int version,
                                        double signedDistance, int shoreOutcome) {
        double cQuantized = (double)((long)((float)continentalness * 10000.0f)) / 10000.0;
        ClimateValues c = climateAt(x, z, scale, biomeSeed, gradientMode, gradientStrength, themeName, islandHalfSpan, version);

        int landFam = selectLandFamily(c, cQuantized, x, z, version, biomeSeed);
        if (shoreOutcome != ShoreField.NONE) {
            boolean suppressed = landFam == 16 || landFam == 8 || landFam == 9
                || (shoreOutcome == ShoreField.STONY && (landFam == 12 || landFam == 13));
            if (!suppressed) {
                String subType = shoreOutcome == ShoreField.STONY
                    ? "stony_shore"
                    : (c.temperature < 0.24 ? "snowy_beach" : "beach");
                return new SampleResult(SurfaceZone.SHORE, subType, -1,
                    c.temperature, c.humidity, c.mountains);
            }
        }
        if (signedDistance <= 0.0) {
            boolean deep = cQuantized < DEEP_OCEAN_THRESHOLD;
            return new SampleResult(SurfaceZone.OCEAN, oceanSubType(c, deep), -1,
                c.temperature, c.humidity, c.mountains);
        }
        return new SampleResult(SurfaceZone.LAND, "land", landFam, c.temperature, c.humidity, c.mountains);
    }

    private static String oceanSubType(ClimateValues c, boolean deep) {
        if (c.temperature < 0.16) return deep ? "deep_frozen_ocean" : "frozen_ocean";
        if (c.temperature < 0.32) return deep ? "deep_cold_ocean" : "cold_ocean";
        if (c.temperature > 0.82) return "warm_ocean";
        if (c.temperature > 0.66) return deep ? "deep_lukewarm_ocean" : "lukewarm_ocean";
        return deep ? "deep_ocean" : "ocean";
    }

    public static int selectLandFamily(ClimateValues c, double continentalness, double x, double z, int version, long biomeSeed) {
        double wx = x + c.warpX;
        double wz = z + c.warpZ;

        // Mushroom district
        double mushroomX = x + 560.0 + c.warpX * 0.35;
        double mushroomZ = z - 320.0 + c.warpZ * 0.35;
        if (mushroomX * mushroomX + mushroomZ * mushroomZ < 190.0 * 190.0) return 16;

        double temp = c.temperature;
        double hum = c.humidity;
        if (version >= 2) {
            double jitter = valueNoise(wx / 96.0, wz / 96.0, saltedSeed(biomeSeed, 777)) * 0.04;
            temp = clamp01(temp + jitter);
            hum = clamp01(hum + jitter * 0.8);
        }

        if (continentalness > 0.18 && c.mountains > 0.68) {
            if (temp < 0.30) return 15; // jagged peaks
            if (temp > 0.68) return 14; // stony peaks
            if (hum > 0.45) return 6;   // cherry grove
            return 5;                   // meadow
        }

        if (temp < 0.26) return hum < 0.58 ? 0 : 1; // 0=snowy_plains, 1=taiga
        if (temp < 0.43) {
            if (hum < 0.48) return 5; // meadow
            if (hum < 0.72) return 2; // birch forest
            return 1;                 // taiga
        }
        if (temp < 0.68) {
            if (hum < 0.46) return 4;  // plains
            if (hum < 0.53) return 3;  // forest
            if (hum < 0.58) return 17; // pale garden
            if (hum < 0.62) return 7;  // dark forest
            return 8;                  // swamp
        }
        if (temp < 0.80) {
            if (hum < 0.25) return 13; // badlands
            if (hum < 0.48) return 11; // savanna
            if (hum < 0.58 || continentalness >= 0.32) return 10; // jungle
            return 9;                  // mangrove swamp
        }
        if (hum < 0.40) return 12; // desert
        if (hum < 0.58) return 13; // badlands
        if (hum < 0.72) return 11; // savanna
        return continentalness < 0.32 ? 9 : 10;
    }

    public static int familyFor(double temp, double hum) {
        if (temp < 0.26) return hum < 0.58 ? 0 : 1;
        if (temp < 0.43) {
            if (hum < 0.48) return 5;
            if (hum < 0.72) return 2;
            return 1;
        }
        if (temp < 0.68) {
            if (hum < 0.46) return 4;
            if (hum < 0.53) return 3;
            if (hum < 0.58) return 17;
            if (hum < 0.62) return 7;
            return 8;
        }
        if (temp < 0.80) {
            if (hum < 0.25) return 13;
            if (hum < 0.48) return 11;
            if (hum < 0.58) return 10;
            return 9;
        }
        if (hum < 0.40) return 12;
        if (hum < 0.58) return 13;
        if (hum < 0.72) return 11;
        return 10;
    }

    public static int saltedSeed(long biomeSeed, int salt) {
        if (biomeSeed == 0L) return salt;
        long mixed = biomeSeed + 0x9E3779B97F4A7C15L * salt;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int)(mixed ^ (mixed >>> 31));
    }

    public static double fbm(double x, double z, int seed) {
        return fbm(x, z, seed, 4);
    }

    public static double fbm(double x, double z, int seed, int octaves) {
        double value = 0.0;
        double amplitude = 0.58;
        double sum = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            value += valueNoise(x, z, seed + octave * 1013) * amplitude;
            sum += amplitude;
            x = x * 2.03 + 17.17;
            z = z * 2.03 - 11.31;
            amplitude *= 0.5;
        }
        return value / sum;
    }

    public static double valueNoise(double x, double z, int seed) {
        int x0 = (int)Math.floor(x);
        int z0 = (int)Math.floor(z);
        double tx = smooth(x - x0);
        double tz = smooth(z - z0);
        double a = lerp(hashUnit(x0, z0, seed), hashUnit(x0 + 1, z0, seed), tx);
        double b = lerp(hashUnit(x0, z0 + 1, seed), hashUnit(x0 + 1, z0 + 1, seed), tx);
        return lerp(a, b, tz);
    }

    private static double hashUnit(int x, int z, int seed) {
        int value = x * 0x1f1f1f1f ^ z * 0x6c8e9cf5 ^ seed * 0x9e3779b9;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        return (Math.floorMod(value, 65536) / 32767.5) - 1.0;
    }

    private static double smooth(double value) { return value * value * (3.0 - 2.0 * value); }
    private static double lerp(double a, double b, double delta) { return a + (b - a) * delta; }
    public static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
