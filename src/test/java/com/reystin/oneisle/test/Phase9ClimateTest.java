package com.reystin.oneisle.test;

import com.reystin.oneisle.client.BiomePreview;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandClimate;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.5 acceptance: theme temperature spans, the gradient formula, family
 * fractions and family-border straightness. Sampling goes through the shared
 * side-neutral {@link IslandClimate} code used by OneBiomeSource and BiomePreview.
 */
public class Phase9ClimateTest {
    private static final boolean WRITE = Boolean.getBoolean("oneisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/climate");
    private static final long SEED = 202L;
    private static final int SCALE = 720;
    private static final double HALF_SPAN = 2000.0;
    private static final double CONTINENTALNESS = 0.10;
    private static final int BORDER_STEP = 10;
    private static final double BORDER_RADIUS = 1500.0;
    private static final double BORDER_TOLERANCE = 4.0;
    private static final IslandDraft.BiomeTheme[] THEMES = {
        IslandDraft.BiomeTheme.ALL, IslandDraft.BiomeTheme.STANDARD,
        IslandDraft.BiomeTheme.NORDIC, IslandDraft.BiomeTheme.DESERT,
        IslandDraft.BiomeTheme.TROPICAL, IslandDraft.BiomeTheme.MYSTIC
    };
    private static final IslandClimate.GradientMode[] BORDER_MODES = {
        IslandClimate.GradientMode.OFF, IslandClimate.GradientMode.NORTH_SOUTH
    };

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void themeTemperatureSpansAreFractionsOfTheSelectedPalette() {
        double all = IslandClimate.temperatureSpan("all");
        double standard = IslandClimate.temperatureSpan("standard");
        assertEquals(0.90, all, 1e-9);
        assertEquals(all, standard, 1e-9, "STANDARD exposes the same full temperature range as ALL");
        assertEquals(0.32, IslandClimate.temperatureSpan("nordic"), 1e-9);
        assertEquals(0.28, IslandClimate.temperatureSpan("desert"), 1e-9);
        assertEquals(0.28, IslandClimate.temperatureSpan("tropical"), 1e-9);
        assertEquals(0.44, IslandClimate.temperatureSpan("mystic"), 1e-9);
        for (String narrow : List.of("nordic", "desert", "tropical")) {
            assertTrue(IslandClimate.temperatureSpan(narrow) < all,
                narrow + " span must be narrower than ALL");
        }
        assertTrue(IslandClimate.temperatureSpan("mystic") < all);
        assertEquals(0.16, IslandClimate.gradientAmplitude("nordic", 1.0), 1e-9);
        assertEquals(0.45, IslandClimate.gradientAmplitude("all", 1.0), 1e-9);
        assertEquals(0.14, IslandClimate.gradientAmplitude("desert", 1.0), 1e-9);
        assertEquals(0.14, IslandClimate.gradientAmplitude("tropical", 1.0), 1e-9);
        assertEquals(0.22, IslandClimate.gradientAmplitude("mystic", 1.0), 1e-9);
        assertEquals(0.225, IslandClimate.gradientAmplitude("all", 0.5), 1e-9);
        assertEquals(0.0, IslandClimate.gradientAmplitude("all", 0.0), 1e-9);
    }

    @Test
    void v3GradientAmplitudeMatchesThemeSpan() throws IOException {
        // Sample beyond the island half-span so u saturates at +-1 regardless of warp;
        // the off/on differential removes the identical noise contribution, and themes
        // stay inside [0,1] at strength <= 0.5, so no clamp distorts the measurement.
        StringBuilder csv = new StringBuilder(
            "theme,strength,formula_amplitude,measured_north_south_delta,delta_error,samples\n");
        int samples = 128;
        double farZ = HALF_SPAN * 2.0;
        for (IslandDraft.BiomeTheme theme : THEMES) {
            for (double strength : new double[]{0.0, 0.5}) {
                double sum = 0.0;
                for (int i = 0; i < samples; i++) {
                    double x = -HALF_SPAN + (i + 0.5) * (2.0 * HALF_SPAN / samples);
                    double south = differential(x, farZ, theme, strength);
                    double north = differential(x, -farZ, theme, strength);
                    sum += south - north;
                }
                double delta = sum / samples;
                double amplitude = IslandClimate.gradientAmplitude(theme.name(), strength)
                    * IslandClimate.V3_GRADIENT_SCALE;
                double error = Math.abs(delta - 2.0 * amplitude);
                assertEquals(2.0 * amplitude, delta, 1e-9,
                    theme + " strength " + strength + " measured delta");
                csv.append(theme.name()).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", strength)).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", amplitude)).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", delta)).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", error)).append(',')
                    .append(samples).append('\n');
            }
        }
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("climate_gradient_summary_v3.csv"), csv.toString(),
                StandardCharsets.UTF_8);
        }
    }

    @Test
    void v3FamilyFractionsPerThemeAndGradient() throws IOException {
        StringBuilder csv = new StringBuilder("version,theme,gradient,family,count,fraction,span\n");
        int grid = 128;
        double step = HALF_SPAN * 2.0 / grid;
        for (IslandDraft.BiomeTheme theme : THEMES) {
            for (double strength : new double[]{0.0, 0.5, 1.0}) {
                String label = strength == 0.0 ? "off" : (strength == 0.5 ? "on" : "max");
                EnumMap<BiomePreview.Type, Integer> counts = new EnumMap<>(BiomePreview.Type.class);
                int land = 0;
                for (int gz = 0; gz < grid; gz++) {
                    for (int gx = 0; gx < grid; gx++) {
                        double x = -HALF_SPAN + (gx + 0.5) * step;
                        double z = -HALF_SPAN + (gz + 0.5) * step;
                        IslandClimate.SampleResult res = sample(x, z, theme,
                            strength == 0.0 ? IslandClimate.GradientMode.OFF
                                : IslandClimate.GradientMode.NORTH_SOUTH,
                            strength, 3);
                        assertEquals(IslandClimate.SurfaceZone.LAND, res.zone());
                        BiomePreview.Type type = BiomePreview.landTypeFromFamily(res.landFamily());
                        counts.merge(type, 1, Integer::sum);
                        land++;
                    }
                }
                for (Map.Entry<BiomePreview.Type, Integer> e : counts.entrySet()) {
                    csv.append("3,").append(theme.name()).append(',')
                        .append(label).append(',')
                        .append(e.getKey().id).append(',').append(e.getValue()).append(',')
                        .append(String.format(Locale.ROOT, "%.4f", e.getValue() / (double) land)).append(',')
                        .append(String.format(Locale.ROOT, "%.2f", IslandClimate.temperatureSpan(theme.name())))
                        .append('\n');
                }
            }
        }
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("climate_family_fractions_v3.csv"), csv.toString(),
                StandardCharsets.UTF_8);
        }
    }

    @Test
    void v3StraightFamilyBordersStayUnder400Blocks() throws IOException {
        StringBuilder csv = new StringBuilder(
            "theme,gradient,orientation,longest_straight,points,start_x,start_z,end_x,end_z\n");
        for (IslandDraft.BiomeTheme theme : THEMES) {
            for (IslandClimate.GradientMode mode : BORDER_MODES) {
                short[][] families = familyGrid(theme, mode, BORDER_STEP, BORDER_RADIUS);
                for (boolean horizontal : new boolean[]{true, false}) {
                    ClimateBorderMetrics.Segment best = ClimateBorderMetrics.longestStraight(
                        families, horizontal, BORDER_STEP, BORDER_RADIUS, BORDER_TOLERANCE);
                    assertTrue(best.length() <= 400.0,
                        theme + " " + mode + (horizontal ? " horizontal" : " vertical")
                            + " straight family border " + best.length() + " blocks");
                    csv.append(theme.name()).append(',').append(mode).append(',')
                        .append(horizontal ? "horizontal" : "vertical").append(',')
                        .append(String.format(Locale.ROOT, "%.0f", best.length())).append(',')
                        .append(best.points()).append(',')
                        .append(String.format(Locale.ROOT, "%.0f,%.0f,%.0f,%.0f",
                            best.startX(), best.startZ(), best.endX(), best.endZ())).append('\n');
                }
            }
        }
        if (WRITE) {
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("climate_straight_borders_v3.csv"), csv.toString(),
                StandardCharsets.UTF_8);
        }
    }

    @Test
    void v3ThemeFamilyMontagePng() throws IOException {
        if (!WRITE) return;
        Files.createDirectories(EVIDENCE);
        int tile = 200;
        double radius = 1500.0;
        double[] strengths = {0.0, 0.5, 1.0};
        BufferedImage image = new BufferedImage(tile * THEMES.length, tile * strengths.length,
            BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < strengths.length; row++) {
            double strength = strengths[row];
            IslandClimate.GradientMode mode = row == 0
                ? IslandClimate.GradientMode.OFF : IslandClimate.GradientMode.NORTH_SOUTH;
            for (int col = 0; col < THEMES.length; col++) {
                IslandDraft.BiomeTheme theme = THEMES[col];
                for (int py = 0; py < tile; py++) {
                    double z = -radius + (py + 0.5) * (2.0 * radius / tile);
                    for (int px = 0; px < tile; px++) {
                        double x = -radius + (px + 0.5) * (2.0 * radius / tile);
                        int rgb = 0x202020;
                        if (x * x + z * z <= radius * radius) {
                            IslandClimate.SampleResult res = sample(x, z, theme, mode, strength, 3);
                            rgb = BiomePreview.landTypeFromFamily(res.landFamily()).color;
                        }
                        image.setRGB(col * tile + px, row * tile + py, rgb);
                    }
                }
            }
        }
        ImageIO.write(image, "png", EVIDENCE.resolve("theme_families_6000.png").toFile());
    }

    private static double differential(double x, double z, IslandDraft.BiomeTheme theme, double strength) {
        double on = IslandClimate.climateAt(x, z, SCALE, SEED,
            IslandClimate.GradientMode.NORTH_SOUTH, strength, theme.name(), HALF_SPAN, 3).temperature();
        double off = IslandClimate.climateAt(x, z, SCALE, SEED,
            IslandClimate.GradientMode.OFF, 0.0, theme.name(), HALF_SPAN, 3).temperature();
        return on - off;
    }

    private static IslandClimate.SampleResult sample(double x, double z, IslandDraft.BiomeTheme theme,
                                                     IslandClimate.GradientMode mode, double strength, int version) {
        return IslandClimate.sample(CONTINENTALNESS, x, z, 64.0, 0.0, SCALE, SEED, mode, strength,
            theme.name(), HALF_SPAN, version);
    }

    private static short[][] familyGrid(IslandDraft.BiomeTheme theme, IslandClimate.GradientMode mode,
                                        int step, double radius) {
        int n = (int) (2 * radius / step) + 1;
        short[][] families = new short[n][n];
        for (int gz = 0; gz < n; gz++) {
            double z = -radius + gz * step;
            for (int gx = 0; gx < n; gx++) {
                double x = -radius + gx * step;
                if (x * x + z * z > radius * radius) {
                    families[gz][gx] = -1;
                    continue;
                }
                families[gz][gx] = (short) sample(x, z, theme, mode, 0.5, 3).landFamily();
            }
        }
        return families;
    }
}
