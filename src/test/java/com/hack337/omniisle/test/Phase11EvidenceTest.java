package com.hack337.omniisle.test;

import com.hack337.omniisle.client.BiomePreview;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.ClimateLayout;
import com.hack337.omniisle.continent.IslandTerrain;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Phase 11 before/after preset evidence; only writes when -Domniisle.evidence=true. */
public class Phase11EvidenceTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final String LABEL = System.getProperty("omniisle.evidence.label", "after");
    private static final Path EVIDENCE = Path.of("docs/evidence/phase11").resolve(LABEL);
    private static final long SEED = 2025L;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void presetOverviewEvidence() throws IOException {
        if (!WRITE) return;
        Files.createDirectories(EVIDENCE);
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("preset,land,warm_11_12_13,cold_0_1,green_2_3_4_5_6_7_17,mushroom_16,pale_17,"
            + "swamp_8_9,desert_12,snow_15,shore,ocean", "");
        List<BufferedImage> provincePanels = new ArrayList<>();
        for (IslandDraft.Preset preset : new IslandDraft.Preset[]{
            IslandDraft.Preset.MINESHIELD_4_MAP, IslandDraft.Preset.NATURAL,
            IslandDraft.Preset.ROUND, IslandDraft.Preset.ARCHIPELAGO,
            IslandDraft.Preset.CRESCENT}) {
            IslandDraft draft = new IslandDraft();
            draft.setPreset(preset);
            draft.setShapeSeed(SEED);
            draft.setBiomeSeed(SEED);
            draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
            IslandTerrain terrain = draft.createTerrain();
            double half = draft.worldSpan() * 0.5;
            int size = 512;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            TreeMap<Integer, Integer> families = new TreeMap<>();
            int land = 0;
            int shore = 0;
            int ocean = 0;
            for (int py = 0; py < size; py++) {
                double z = -half + (py + 0.5) * (draft.worldSpan() / size);
                for (int px = 0; px < size; px++) {
                    double x = -half + (px + 0.5) * (draft.worldSpan() / size);
                    double c = terrain.continentalness(x, z);
                    double slope = slope(terrain, x, z);
                    BiomePreview.Selection selection = BiomePreview.selectionV3(
                        c, x, z, draft.biomeSeed(), 720,
                        draft.gradientMode(), draft.gradientStrength(), draft.theme(), slope, 3,
                        half, terrain.rivers(), terrain, draft.shoreSettings(), true);
                    int rgb;
                    if (selection.type().category == BiomePreview.Category.OCEAN) {
                        ocean++;
                        rgb = 0x0B3D91;
                    } else if (selection.type().category == BiomePreview.Category.SHORE) {
                        shore++;
                        rgb = 0xE8DCA0;
                    } else {
                        land++;
                        families.merge(selection.landFamily(), 1, Integer::sum);
                        BiomePreview.Type type = BiomePreview.landTypeFromFamily(selection.landFamily());
                        rgb = type != null ? type.color : 0x804020;
                    }
                    image.setRGB(px, py, rgb);
                }
            }
            ImageIO.write(image, "png", EVIDENCE.resolve("preset_" + preset.name().toLowerCase(Locale.ROOT) + ".png").toFile());
            provincePanels.add(provincePanel(terrain, half, 320));
            writeCloseup(draft, terrain,
                EVIDENCE.resolve("closeup_" + preset.name().toLowerCase(Locale.ROOT) + ".png"));
            if (preset == IslandDraft.Preset.NATURAL) {
                double[] edge = provinceWaterEdge(terrain, half);
                writeCloseupAt(draft, terrain, EVIDENCE.resolve("ocean_edge_natural.png"),
                    edge[0], edge[1]);
            }
            if (preset == IslandDraft.Preset.MINESHIELD_4_MAP) {
                BufferedImage tiers = new BufferedImage(draft.resolution(), draft.resolution(),
                    BufferedImage.TYPE_INT_RGB);
                for (int z = 0; z < draft.resolution(); z++) {
                    for (int x = 0; x < draft.resolution(); x++) {
                        int rgb = !draft.isLand(x, z) ? 0x0B2A5B : switch (draft.tier(x, z)) {
                            case 3 -> 0xF2F5F7;
                            case 2 -> 0x9A6A3A;
                            default -> 0x3F7A3A;
                        };
                        tiers.setRGB(x, z, rgb);
                    }
                }
                ImageIO.write(tiers, "png", EVIDENCE.resolve("mineshield_tiers.png").toFile());
            }
            int total = Math.max(1, land);
            lines.put(preset.name().toLowerCase(Locale.ROOT), String.format(Locale.ROOT,
                "%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d",
                preset.name(), land,
                fraction(families, total, 11, 12, 13), fraction(families, total, 0, 1),
                fraction(families, total, 2, 3, 4, 5, 6, 7, 17),
                fraction(families, total, 16), fraction(families, total, 17),
                fraction(families, total, 8, 9), fraction(families, total, 12),
                fraction(families, total, 15), shore, ocean));
            System.out.println("PHASE11 " + LABEL + " " + preset + " " + families);
        }
        Files.writeString(EVIDENCE.resolve("composition.csv"),
            String.join("\n", lines.values()) + "\n", StandardCharsets.UTF_8);
        BufferedImage strip = new BufferedImage(provincePanels.size() * 330 - 10, 320,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = strip.createGraphics();
        graphics.setColor(new java.awt.Color(0x101614));
        graphics.fillRect(0, 0, strip.getWidth(), strip.getHeight());
        for (int i = 0; i < provincePanels.size(); i++) {
            graphics.drawImage(provincePanels.get(i), i * 330, 0, null);
        }
        graphics.dispose();
        ImageIO.write(strip, "png", EVIDENCE.resolve("province_map.png").toFile());
    }

    private static BufferedImage provincePanel(IslandTerrain terrain, double half, int size) {
        ClimateLayout layout = terrain.climateLayout();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double span = half * 2.0;
        for (int py = 0; py < size; py++) {
            double z = -half + (py + 0.5) * span / size;
            for (int px = 0; px < size; px++) {
                double x = -half + (px + 0.5) * span / size;
                ClimateLayout.Sample sample = layout.sample(x, z);
                int rgb;
                if (terrain.signedDistance(x, z) > 0.0) {
                    rgb = switch (sample.province()) {
                        case WARM -> 0xD9A441;
                        case COLD -> 0x8FC7E8;
                        case TEMPERATE -> 0x4E8F4A;
                    };
                    if (sample.paleStrength() > 0.55) rgb = 0xF2A6C8;
                    else if (sample.iceStrength() > 0.55) rgb = 0xFFFFFF;
                } else {
                    rgb = switch (sample.province()) {
                        case WARM -> 0x4A2E18;
                        case COLD -> 0x274A5E;
                        case TEMPERATE -> 0x0B2A5B;
                    };
                }
                image.setRGB(px, py, rgb);
            }
        }
        return image;
    }

    private static void writeCloseup(IslandDraft draft, IslandTerrain terrain, Path path)
        throws IOException {
        double cx = -0.30 * draft.worldSpan();
        double cz = 0.0;
        if (terrain.signedDistance(cx, cz) < -80.0) {
            cx = 0.0;
            cz = 0.0;
        }
        writeCloseupAt(draft, terrain, path, cx, cz);
    }

    private static double[] provinceWaterEdge(IslandTerrain terrain, double half) {
        ClimateLayout layout = terrain.climateLayout();
        double bestX = 0.0;
        double bestZ = 0.0;
        int best = -1;
        for (double z = -half; z <= half; z += 24.0) {
            for (double x = -half; x <= half; x += 24.0) {
                if (terrain.signedDistance(x, z) > -48.0) continue;
                ClimateLayout.Province province = layout.sample(x, z).province();
                if (province == ClimateLayout.Province.TEMPERATE) continue;
                int different = 0;
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (layout.sample(x + dx * 48.0, z + dz * 48.0).province() != province) {
                            different++;
                        }
                    }
                }
                if (different > best) {
                    best = different;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        return new double[]{bestX, bestZ};
    }

    private static void writeCloseupAt(IslandDraft draft, IslandTerrain terrain, Path path,
                                       double cx, double cz) throws IOException {
        int size = 320;
        double blocksPerPixel = 2.0;
        double half = draft.worldSpan() * 0.5;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++) {
            double z = cz + (py - size * 0.5) * blocksPerPixel;
            for (int px = 0; px < size; px++) {
                double x = cx + (px - size * 0.5) * blocksPerPixel;
                double c = terrain.continentalness(x, z);
                BiomePreview.Selection selection = BiomePreview.selectionV3(
                    c, x, z, draft.biomeSeed(), draft.climateScale(), draft.gradientMode(),
                    draft.gradientStrength(), draft.theme(), slope(terrain, x, z), 3,
                    half, terrain.rivers(), terrain, draft.shoreSettings(), true);
                image.setRGB(px, py, selection.type().color & 0x00FFFFFF);
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private static double fraction(Map<Integer, Integer> families, int total, int... ids) {
        int count = 0;
        for (int id : ids) count += families.getOrDefault(id, 0);
        return count / (double) total;
    }

    private static double slope(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 1.0, z);
        double cW = terrain.continentalness(x - 1.0, z);
        double cS = terrain.continentalness(x, z + 1.0);
        double cN = terrain.continentalness(x, z - 1.0);
        return Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
    }
}
