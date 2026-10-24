package com.hack337.omniisle.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.hack337.omniisle.OmniIsleIds;
import com.hack337.omniisle.continent.IslandClimate;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ShoreField;
import com.hack337.omniisle.continent.ShoreSettings;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

/** A climate-based primary-biome source with broad, organically curved regions. */
public final class OmniBiomeSource extends BiomeSource {
    /**
     * Vanilla terrain can remain a few blocks above sea level after it has entered
     * negative continentalness. Keep that exposed shelf in a shore biome so the
     * visible order is inland biome -> beach -> shallow ocean.
     */
    private static final double OCEAN_START = -0.30;
    private static final double INLAND_START = 0.02;
    private static final int SANDY_BEACH_MAX_Y = 70;
    private static final int ROCKY_COAST_MAX_Y = 86;

    private record ThemeShore(String theme, Optional<ShoreSettings> shore) {}

    // Inlined so the flat "biome_theme"/"shore" fields fit DFU's 16-field record limit.
    private static final MapCodec<ThemeShore> THEME_SHORE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.STRING.optionalFieldOf("biome_theme", "standard").forGetter(ThemeShore::theme),
        ShoreSettings.CODEC.optionalFieldOf("shore").forGetter(ThemeShore::shore)
    ).apply(i, ThemeShore::new));

    public static final MapCodec<OmniBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Biome.CODEC.listOf().fieldOf("land_biomes").forGetter(s -> s.landBiomes),
        Biome.CODEC.listOf().optionalFieldOf("enabled_land_biomes", List.of()).forGetter(s -> s.enabledLandBiomes),
        Biome.CODEC.listOf().fieldOf("shore_biomes").forGetter(s -> s.shoreBiomes),
        Biome.CODEC.listOf().optionalFieldOf("enabled_shore_biomes", List.of()).forGetter(s -> s.enabledShoreBiomes),
        Biome.CODEC.listOf().fieldOf("ocean_biomes").forGetter(s -> s.oceanBiomes),
        Biome.CODEC.listOf().optionalFieldOf("enabled_ocean_biomes", List.of()).forGetter(s -> s.enabledOceanBiomes),
        Biome.CODEC.listOf().optionalFieldOf("river_biomes", List.of()).forGetter(s -> s.riverBiomes),
        Biome.CODEC.listOf().optionalFieldOf("enabled_river_biomes", List.of()).forGetter(s -> s.enabledRiverBiomes),
        Biome.CODEC.listOf().fieldOf("cave_biomes").forGetter(s -> s.caveBiomes),
        Biome.CODEC.listOf().optionalFieldOf("enabled_cave_biomes", List.of()).forGetter(s -> s.enabledCaveBiomes),
        Codec.intRange(384, 2048).optionalFieldOf("climate_scale", 720).forGetter(s -> s.climateScale),
        Codec.LONG.optionalFieldOf("biome_seed", 0L).forGetter(s -> s.biomeSeed),
        Codec.INT.optionalFieldOf("generation_version", 1).forGetter(s -> s.generationVersion),
        Codec.STRING.optionalFieldOf("gradient_mode", "north_south").forGetter(s -> s.gradientModeStr),
        Codec.DOUBLE.optionalFieldOf("gradient_strength", 0.5).forGetter(s -> s.gradientStrength),
        THEME_SHORE_CODEC.forGetter(s -> new ThemeShore(s.biomeThemeStr, Optional.ofNullable(s.shore)))
    ).apply(instance, (landBiomes, enabledLandBiomes, shoreBiomes, enabledShoreBiomes, oceanBiomes, enabledOceanBiomes,
                      riverBiomes, enabledRiverBiomes, caveBiomes, enabledCaveBiomes, climateScale, biomeSeed,
                      generationVersion, gradientModeStr, gradientStrength, themeShore) ->
        new OmniBiomeSource(landBiomes, enabledLandBiomes, shoreBiomes, enabledShoreBiomes, oceanBiomes, enabledOceanBiomes,
            riverBiomes, enabledRiverBiomes, caveBiomes, enabledCaveBiomes, climateScale, biomeSeed,
            generationVersion, gradientModeStr, gradientStrength, themeShore.theme(), themeShore.shore())));

    private final List<Holder<Biome>> landBiomes;
    private final List<Holder<Biome>> enabledLandBiomes;
    private final List<Holder<Biome>> shoreBiomes;
    private final List<Holder<Biome>> enabledShoreBiomes;
    private final List<Holder<Biome>> oceanBiomes;
    private final List<Holder<Biome>> enabledOceanBiomes;
    private final List<Holder<Biome>> riverBiomes;
    private final List<Holder<Biome>> enabledRiverBiomes;
    private final List<Holder<Biome>> caveBiomes;
    private final List<Holder<Biome>> enabledCaveBiomes;
    private final int climateScale;
    private final long biomeSeed;
    private final int generationVersion;
    private final String gradientModeStr;
    private final double gradientStrength;
    private final String biomeThemeStr;
    private final IslandClimate.GradientMode gradientMode;
    private final ShoreSettings shore;
    private final boolean shoreDisabled;

    public OmniBiomeSource(List<Holder<Biome>> landBiomes,
                           List<Holder<Biome>> enabledLandBiomes,
                           List<Holder<Biome>> shoreBiomes,
                           List<Holder<Biome>> enabledShoreBiomes,
                           List<Holder<Biome>> oceanBiomes,
                           List<Holder<Biome>> enabledOceanBiomes,
                           List<Holder<Biome>> riverBiomes,
                           List<Holder<Biome>> enabledRiverBiomes,
                           List<Holder<Biome>> caveBiomes,
                           List<Holder<Biome>> enabledCaveBiomes,
                           int climateScale,
                           long biomeSeed) {
        this(landBiomes, enabledLandBiomes, shoreBiomes, enabledShoreBiomes, oceanBiomes, enabledOceanBiomes,
            riverBiomes, enabledRiverBiomes, caveBiomes, enabledCaveBiomes, climateScale, biomeSeed,
            1, "off", 0.5, "standard");
    }

    public OmniBiomeSource(List<Holder<Biome>> landBiomes,
                           List<Holder<Biome>> enabledLandBiomes,
                           List<Holder<Biome>> shoreBiomes,
                           List<Holder<Biome>> enabledShoreBiomes,
                           List<Holder<Biome>> oceanBiomes,
                           List<Holder<Biome>> enabledOceanBiomes,
                           List<Holder<Biome>> riverBiomes,
                           List<Holder<Biome>> enabledRiverBiomes,
                           List<Holder<Biome>> caveBiomes,
                           List<Holder<Biome>> enabledCaveBiomes,
                           int climateScale,
                           long biomeSeed,
                           int generationVersion,
                           String gradientModeStr,
                           double gradientStrength,
                           String biomeThemeStr) {
        this(landBiomes, enabledLandBiomes, shoreBiomes, enabledShoreBiomes, oceanBiomes, enabledOceanBiomes,
            riverBiomes, enabledRiverBiomes, caveBiomes, enabledCaveBiomes, climateScale, biomeSeed,
            generationVersion, gradientModeStr, gradientStrength, biomeThemeStr, Optional.empty());
    }

    public OmniBiomeSource(List<Holder<Biome>> landBiomes,
                           List<Holder<Biome>> enabledLandBiomes,
                           List<Holder<Biome>> shoreBiomes,
                           List<Holder<Biome>> enabledShoreBiomes,
                           List<Holder<Biome>> oceanBiomes,
                           List<Holder<Biome>> enabledOceanBiomes,
                           List<Holder<Biome>> riverBiomes,
                           List<Holder<Biome>> enabledRiverBiomes,
                           List<Holder<Biome>> caveBiomes,
                           List<Holder<Biome>> enabledCaveBiomes,
                           int climateScale,
                           long biomeSeed,
                           int generationVersion,
                           String gradientModeStr,
                           double gradientStrength,
                           String biomeThemeStr,
                           Optional<ShoreSettings> shore) {
        if (landBiomes.size() < 18 || shoreBiomes.size() < 3 || oceanBiomes.size() < 6
            || (!riverBiomes.isEmpty() && riverBiomes.size() < 2) || caveBiomes.size() < 3) {
            throw new IllegalArgumentException("OmniIsle biome palette is incomplete");
        }
        this.landBiomes = List.copyOf(landBiomes);
        this.enabledLandBiomes = enabledLandBiomes.isEmpty() ? this.landBiomes : List.copyOf(enabledLandBiomes);
        this.shoreBiomes = List.copyOf(shoreBiomes);
        this.enabledShoreBiomes = enabledShoreBiomes.isEmpty() ? this.shoreBiomes : List.copyOf(enabledShoreBiomes);
        this.oceanBiomes = List.copyOf(oceanBiomes);
        this.enabledOceanBiomes = enabledOceanBiomes.isEmpty() ? this.oceanBiomes : List.copyOf(enabledOceanBiomes);
        this.riverBiomes = List.copyOf(riverBiomes);
        this.enabledRiverBiomes = enabledRiverBiomes.isEmpty() ? this.riverBiomes : List.copyOf(enabledRiverBiomes);
        this.caveBiomes = List.copyOf(caveBiomes);
        this.enabledCaveBiomes = enabledCaveBiomes.isEmpty() ? this.caveBiomes : List.copyOf(enabledCaveBiomes);
        this.climateScale = climateScale;
        this.biomeSeed = biomeSeed;
        this.generationVersion = generationVersion;
        this.gradientModeStr = gradientModeStr != null ? gradientModeStr : "north_south";
        this.gradientStrength = gradientStrength;
        this.biomeThemeStr = biomeThemeStr != null ? biomeThemeStr : "standard";
        this.gradientMode = IslandClimate.GradientMode.fromString(this.gradientModeStr);
        this.shore = shore.orElse(this.generationVersion >= 3 ? ShoreSettings.DEFAULT : null);
        this.shoreDisabled = this.generationVersion >= 3 && enabledShoreBiomes.isEmpty();
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(landBiomes, enabledLandBiomes, shoreBiomes, enabledShoreBiomes,
            oceanBiomes, enabledOceanBiomes, riverBiomes, enabledRiverBiomes,
            caveBiomes, enabledCaveBiomes).flatMap(List::stream).distinct();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public net.minecraft.world.level.biome.BiomeResolver createResolver(net.minecraft.world.level.biome.Climate.Sampler sampler) {
        return (biomeX, biomeY, biomeZ) -> getNoiseBiome(biomeX, biomeY, biomeZ, sampler);
    }

    public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ, net.minecraft.world.level.biome.Climate.Sampler sampler) {
        net.minecraft.world.level.biome.Climate.TargetPoint point = sampler.sample(biomeX, biomeY, biomeZ);
        double continentalness = net.minecraft.world.level.biome.Climate.unquantizeCoord(point.continentalness());
        int x = biomeX << 2;
        int y = biomeY << 2;
        int z = biomeZ << 2;

        if (this.generationVersion <= 1) {
            Climate climate = climateAt(x, z, climateScale, biomeSeed);

            if (continentalness < OCEAN_START) return oceanBiome(climate, continentalness);
            if (continentalness < INLAND_START) {
                if (y > ROCKY_COAST_MAX_Y) return landBiome(climate, continentalness, x, z);
                if (y > SANDY_BEACH_MAX_Y) return elevatedCoast(climate, continentalness, x, z);
                return shoreBiome(climate);
            }
            double erosion = net.minecraft.world.level.biome.Climate.unquantizeCoord(point.erosion());
            double weirdness = net.minecraft.world.level.biome.Climate.unquantizeCoord(point.weirdness());
            if (y >= 0 && !riverBiomes.isEmpty() && isRiverValley(continentalness, erosion, weirdness)) {
                return selectEnabled(enabledRiverBiomes, riverBiomes,
                    climate.temperature < 0.26 ? "frozen_river" : "river");
            }
            if (y < 0 && shouldUseCaveBiome(x, y, z, point)) return caveBiome(climate, x, y, z);
            return landBiome(climate, continentalness, x, z);
        }

        // Modern generation (v2)
        double cE = net.minecraft.world.level.biome.Climate.unquantizeCoord(sampler.sample(biomeX + 1, biomeY, biomeZ).continentalness());
        double cW = net.minecraft.world.level.biome.Climate.unquantizeCoord(sampler.sample(biomeX - 1, biomeY, biomeZ).continentalness());
        double cS = net.minecraft.world.level.biome.Climate.unquantizeCoord(sampler.sample(biomeX, biomeY, biomeZ + 1).continentalness());
        double cN = net.minecraft.world.level.biome.Climate.unquantizeCoord(sampler.sample(biomeX, biomeY, biomeZ - 1).continentalness());
        double slope = Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
        double halfSpan = 2000.0;
        IslandDensityFunction activeDf = IslandDensityFunction.getActive();
        if (activeDf != null && activeDf.shapeConfig() != null && activeDf.shapeConfig().mask().isPresent()) {
            var m = activeDf.shapeConfig().mask().get();
            halfSpan = (m.maxX - m.minX) / 2.0;
        }

        IslandClimate.SampleResult res;
        if (generationVersion >= 3) {
            IslandTerrain terrain = activeDf != null ? activeDf.terrain() : null;
            double distance;
            if (terrain != null) {
                distance = terrain.signedDistance(x, z);
            } else {
                distance = continentalness < IslandClimate.OCEAN_START ? -10.0
                    : Math.max(1.0, continentalness * 100.0);
            }
            int shoreOutcome = ShoreField.NONE;
            if (!shoreDisabled && shore != null && shore.enabled()
                && distance >= ShoreField.intertidalLimit(x, z, biomeSeed) && distance <= ShoreField.MAX_INLAND) {
                double bay = ShoreField.bayness(terrain, x, z, distance);
                shoreOutcome = ShoreField.outcome(distance, x, z, slope, bay, shore, biomeSeed);
            }
            res = IslandClimate.sampleV3(
                continentalness, x, z, slope, climateScale, biomeSeed,
                gradientMode, gradientStrength, biomeThemeStr, halfSpan, generationVersion,
                distance, shoreOutcome
            );
        } else {
            res = IslandClimate.sample(
                continentalness, x, z, y, slope, climateScale, biomeSeed,
                gradientMode, gradientStrength, biomeThemeStr, halfSpan, generationVersion
            );
        }

        if (res.zone() == IslandClimate.SurfaceZone.OCEAN) {
            return selectEnabled(enabledOceanBiomes, oceanBiomes, res.subType());
        }
        if (res.zone() == IslandClimate.SurfaceZone.SHORE) {
            return selectEnabled(enabledShoreBiomes, shoreBiomes, res.subType());
        }

        if (y >= 0 && !riverBiomes.isEmpty()) {
            boolean river = false;
            if (activeDf != null && activeDf.terrain() != null && activeDf.terrain().rivers() != null && activeDf.terrain().rivers().isEnabled()) {
                river = activeDf.terrain().rivers().isRiver(x, z);
            }
            if (!river) {
                double erosion = net.minecraft.world.level.biome.Climate.unquantizeCoord(point.erosion());
                double weirdness = net.minecraft.world.level.biome.Climate.unquantizeCoord(point.weirdness());
                river = isRiverValley(continentalness, erosion, weirdness);
            }
            if (river) {
                return selectEnabled(enabledRiverBiomes, riverBiomes,
                    res.temperature() < 0.26 ? "frozen_river" : "river");
            }
        }

        if (y < 0 && shouldUseCaveBiome(x, y, z, point)) {
            return caveBiome(new Climate(res.temperature(), res.humidity(), res.mountains(), 0, 0), x, y, z);
        }

        return enabledLand(res.landFamily(), x, z);
    }

    private static boolean isRiverValley(double continentalness, double erosion, double weirdness) {
        return Math.abs(weirdness) <= 0.05 && (erosion >= -0.375 || continentalness < 0.05);
    }

    private Holder<Biome> oceanBiome(Climate climate, double continentalness) {
        boolean deep = continentalness < -0.58;
        String preferred;
        if (climate.temperature < 0.16) preferred = deep ? "deep_frozen_ocean" : "frozen_ocean";
        else if (climate.temperature < 0.32) preferred = deep ? "deep_cold_ocean" : "cold_ocean";
        else if (climate.temperature > 0.82) preferred = "warm_ocean";
        else if (climate.temperature > 0.66) preferred = deep ? "deep_lukewarm_ocean" : "lukewarm_ocean";
        else preferred = deep ? "deep_ocean" : "ocean";
        return selectEnabled(enabledOceanBiomes, oceanBiomes, preferred);
    }

    private Holder<Biome> shoreBiome(Climate climate) {
        if (climate.temperature < 0.24) return selectEnabled(enabledShoreBiomes, shoreBiomes, "snowy_beach");
        if (climate.mountains > 0.67) return selectEnabled(enabledShoreBiomes, shoreBiomes, "stony_shore");
        return selectEnabled(enabledShoreBiomes, shoreBiomes, "beach");
    }

    private Holder<Biome> elevatedCoast(Climate climate, double continentalness, int x, int z) {
        for (Holder<Biome> entry : enabledShoreBiomes) {
            if (path(entry).equals("stony_shore")) return entry;
        }
        return landBiome(climate, continentalness, x, z);
    }

    private Holder<Biome> landBiome(Climate c, double continentalness, int x, int z) {
        double mushroomX = x + 560.0 + c.warpX * 0.35;
        double mushroomZ = z - 320.0 + c.warpZ * 0.35;
        if (mushroomX * mushroomX + mushroomZ * mushroomZ < 190.0 * 190.0) return enabledLand(16, x, z);

        if (continentalness > 0.18 && c.mountains > 0.68) {
            if (c.temperature < 0.30) return enabledLand(15, x, z);
            if (c.temperature > 0.68) return enabledLand(14, x, z);
            if (c.humidity > 0.45) return enabledLand(6, x, z);
            return enabledLand(5, x, z);
        }

        if (c.temperature < 0.26) return c.humidity < 0.58 ? enabledLand(0, x, z) : enabledLand(1, x, z);
        if (c.temperature < 0.43) {
            if (c.humidity < 0.48) return enabledLand(5, x, z);
            if (c.humidity < 0.72) return enabledLand(2, x, z);
            return enabledLand(1, x, z);
        }
        if (c.temperature < 0.68) {
            if (c.humidity < 0.46) return enabledLand(4, x, z);
            if (c.humidity < 0.53) return enabledLand(3, x, z);
            if (c.humidity < 0.58) return enabledLand(17, x, z);
            if (c.humidity < 0.62) return enabledLand(7, x, z);
            return enabledLand(8, x, z);
        }
        if (c.temperature < 0.80) {
            if (c.humidity < 0.25) return enabledLand(13, x, z);
            if (c.humidity < 0.48) return enabledLand(11, x, z);
            if (c.humidity < 0.58 || continentalness >= 0.32) return enabledLand(10, x, z);
            return enabledLand(9, x, z);
        }
        if (c.humidity < 0.40) return enabledLand(12, x, z);
        if (c.humidity < 0.58) return enabledLand(13, x, z);
        if (c.humidity < 0.72) return enabledLand(11, x, z);
        return continentalness < 0.32 ? enabledLand(9, x, z) : enabledLand(10, x, z);
    }

    private Holder<Biome> enabledLand(int preferredIndex, int x, int z) {
        List<Holder<Biome>> family = enabledLandBiomes.stream()
            .filter(entry -> landFamily(path(entry)) == preferredIndex).toList();
        if (!family.isEmpty()) return choosePatch(family, x, z, preferredIndex);

        int bestDistance = Integer.MAX_VALUE;
        java.util.ArrayList<Holder<Biome>> nearest = new java.util.ArrayList<>();
        for (Holder<Biome> entry : enabledLandBiomes) {
            int candidate = landFamily(path(entry));
            if (candidate < 0) continue;
            int distance = Math.min(Math.floorMod(candidate - preferredIndex, 18),
                Math.floorMod(preferredIndex - candidate, 18));
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest.clear();
            }
            if (distance == bestDistance) nearest.add(entry);
        }
        return choosePatch(nearest.isEmpty() ? enabledLandBiomes : nearest, x, z, preferredIndex);
    }

    private boolean shouldUseCaveBiome(int x, int y, int z, net.minecraft.world.level.biome.Climate.TargetPoint point) {
        double depth = net.minecraft.world.level.biome.Climate.unquantizeCoord(point.depth());
        double cavePatch = fbm(x / 360.0, z / 360.0, saltedSeed(biomeSeed, 901));
        return y < 0 ? cavePatch > -0.28 : depth > 0.12 && cavePatch > 0.34;
    }

    private Holder<Biome> caveBiome(Climate climate, int x, int y, int z) {
        if (y < 0 && fbm(x / 520.0, z / 520.0, saltedSeed(biomeSeed, 977)) > 0.02) {
            return selectEnabled(enabledCaveBiomes, caveBiomes, "deep_dark");
        }
        if (climate.temperature > 0.65) {
            return selectEnabled(enabledCaveBiomes, caveBiomes, "sulfur_caves");
        }
        return selectEnabled(enabledCaveBiomes, caveBiomes,
            climate.humidity > 0.52 ? "lush_caves" : "dripstone_caves");
    }

    private Holder<Biome> selectEnabled(List<Holder<Biome>> enabled,
                                        List<Holder<Biome>> fallback,
                                        String preferredPath) {
        String[] priorities = switch (preferredPath) {
            case "deep_frozen_ocean" -> new String[] {"deep_frozen_ocean", "frozen_ocean", "deep_cold_ocean", "cold_ocean"};
            case "deep_cold_ocean" -> new String[] {"deep_cold_ocean", "cold_ocean", "deep_ocean", "ocean"};
            case "deep_lukewarm_ocean" -> new String[] {"deep_lukewarm_ocean", "lukewarm_ocean", "deep_ocean", "ocean"};
            case "frozen_ocean" -> new String[] {"frozen_ocean", "deep_frozen_ocean", "cold_ocean", "deep_cold_ocean"};
            case "cold_ocean" -> new String[] {"cold_ocean", "deep_cold_ocean", "ocean", "deep_ocean"};
            case "lukewarm_ocean" -> new String[] {"lukewarm_ocean", "deep_lukewarm_ocean", "warm_ocean", "ocean"};
            case "deep_ocean" -> new String[] {"deep_ocean", "ocean", "deep_cold_ocean", "deep_lukewarm_ocean"};
            default -> new String[] {preferredPath};
        };
        for (String candidate : priorities) {
            for (Holder<Biome> entry : enabled) {
                if (path(entry).equals(candidate)) return entry;
            }
        }
        return enabled.isEmpty() ? fallback.get(0) : enabled.get(0);
    }

    private Holder<Biome> choosePatch(List<Holder<Biome>> choices, int x, int z, int salt) {
        if (choices.size() == 1) return choices.get(0);
        double patch = fbm(x / 420.0, z / 420.0, saltedSeed(biomeSeed, 1201 + salt * 37));
        int index = Math.min(choices.size() - 1,
            Math.max(0, (int)Math.floor((patch * 0.5 + 0.5) * choices.size())));
        return choices.get(index);
    }

    private static String path(Holder<Biome> entry) {
        return entry.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
    }

    public static int landFamily(String path) {
        return switch (path) {
            case "snowy_plains", "ice_spikes" -> 0;
            case "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" -> 1;
            case "birch_forest", "old_growth_birch_forest" -> 2;
            case "forest", "flower_forest", "windswept_forest", "dappled_forest" -> 3;
            case "plains", "sunflower_plains" -> 4;
            case "meadow", "grove" -> 5;
            case "cherry_grove" -> 6;
            case "dark_forest" -> 7;
            case "swamp" -> 8;
            case "mangrove_swamp" -> 9;
            case "jungle", "bamboo_jungle", "sparse_jungle" -> 10;
            case "savanna", "savanna_plateau", "windswept_savanna" -> 11;
            case "desert" -> 12;
            case "badlands", "eroded_badlands", "wooded_badlands" -> 13;
            case "stony_peaks", "windswept_hills", "windswept_gravelly_hills" -> 14;
            case "jagged_peaks", "frozen_peaks", "snowy_slopes" -> 15;
            case "mushroom_fields" -> 16;
            case "pale_garden" -> 17;
            default -> -1;
        };
    }

    private static Climate climateAt(double x, double z, double scale, long biomeSeed) {
        double warpX = fbm(x / scale, z / scale, saltedSeed(biomeSeed, 101)) * scale * 0.34;
        double warpZ = fbm(x / scale, z / scale, saltedSeed(biomeSeed, 211)) * scale * 0.34;
        double wx = x + warpX;
        double wz = z + warpZ;
        double temperature = clamp01(0.50 + wz / 3200.0 + fbm(wx / (scale * 1.35), wz / (scale * 1.35), saltedSeed(biomeSeed, 307)) * 0.17);
        double humidity = clamp01(0.50 + fbm(wx / scale, wz / scale, saltedSeed(biomeSeed, 401)) * 0.46);
        double ridge = 1.0 - Math.abs(fbm(wx / (scale * 0.82), wz / (scale * 0.82), saltedSeed(biomeSeed, 503)));
        double mountainBands = 0.60 * ridge + 0.40 * clamp01(fbm(wx / (scale * 1.7), wz / (scale * 1.7), saltedSeed(biomeSeed, 601)) * 0.5 + 0.5);
        return new Climate(temperature, humidity, mountainBands, warpX, warpZ);
    }

    private static int saltedSeed(long biomeSeed, int salt) {
        if (biomeSeed == 0L) return salt;
        long mixed = biomeSeed + 0x9E3779B97F4A7C15L * salt;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int)(mixed ^ (mixed >>> 31));
    }

    private static double fbm(double x, double z, int seed) {
        double value = 0.0;
        double amplitude = 0.58;
        double sum = 0.0;
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
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }

    private record Climate(double temperature, double humidity, double mountains, double warpX, double warpZ) {}

    public static void registerCodec() {
        Registry.register(BuiltInRegistries.BIOME_SOURCE, OmniIsleIds.id("island_biomes"), CODEC);
    }
}
