package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.ShareCode;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixed Mineshield 4 reference-map preset. */
public class Mineshield4MapPresetTest {
    private static final boolean WRITE = Boolean.getBoolean("omniisle.evidence");
    private static final Path EVIDENCE = Path.of("docs/evidence/presets");

    private static IslandDraft draft() {
        IslandDraft draft = new IslandDraft(192);
        draft.setPreset(IslandDraft.Preset.MINESHIELD_4_MAP);
        return draft;
    }

    @Test
    void fixedMaskSatisfiesShapeContract() {
        IslandDraft draft = draft();
        int res = draft.resolution();
        int land = 0;
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                if (x == 0 || z == 0 || x == res - 1 || z == res - 1) {
                    assertFalse(draft.isLand(x, z), "border land at " + x + "," + z);
                }
                if (draft.isLand(x, z)) land++;
            }
        }
        double fraction = land / (double) (res * res);
        assertTrue(fraction >= 0.27 && fraction <= 0.34, "land fraction " + fraction);

        int largest = largestComponent(draft);
        assertTrue(largest / (double) land >= 0.85,
            "largest component " + largest + "/" + land);
    }

    @Test
    void maskRebuildsAtEveryResolution() {
        for (int res : new int[]{256, 384}) {
            IslandDraft draft = new IslandDraft(res);
            draft.setPreset(IslandDraft.Preset.MINESHIELD_4_MAP);
            int land = 0;
            for (int z = 0; z < res; z++) {
                for (int x = 0; x < res; x++) {
                    if (x == 0 || z == 0 || x == res - 1 || z == res - 1) {
                        assertFalse(draft.isLand(x, z), "border land at " + res + " " + x + "," + z);
                    }
                    if (draft.isLand(x, z)) land++;
                }
            }
            double fraction = land / (double) (res * res);
            assertTrue(fraction >= 0.27 && fraction <= 0.34, res + " land fraction " + fraction);
            int largest = largestComponent(draft);
            assertTrue(largest / (double) land >= 0.85,
                res + " largest component " + largest + "/" + land);
        }
    }

    @Test
    void fixedMaskIgnoresShapeSeed() {
        IslandDraft a = draft();
        a.setShapeSeed(1L);
        IslandDraft b = draft();
        b.setShapeSeed(999_999L);
        int res = a.resolution();
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                assertEquals(a.isLand(x, z), b.isLand(x, z), "land differs at " + x + "," + z);
                assertEquals(a.tier(x, z), b.tier(x, z), "tier differs at " + x + "," + z);
            }
        }
    }

    @Test
    void heightTiersFollowReferenceRelief() {
        IslandDraft draft = draft();
        int res = draft.resolution();
        int snow = 0;
        int highland = 0;
        int lowland = 0;
        int flat = 0;
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                if (!draft.isLand(x, z)) continue;
                switch (draft.tier(x, z)) {
                    case 3 -> snow++;
                    case 2 -> highland++;
                    case 1 -> lowland++;
                    default -> flat++;
                }
            }
        }
        assertTrue(snow > 0, "snowy spine missing");
        assertTrue(highland > 0, "highland plateaus missing");
        assertTrue(lowland > 0, "lowland missing");
        assertEquals(0, flat, "reference map must not use tier 0");
        if (WRITE) writeEvidence(draft, res, snow, highland, lowland);
    }

    @Test
    void tierRegionsAreCohesive() {
        StringBuilder csv = new StringBuilder("resolution,tier3_components,tier3_area,tier3_centroid_x,"
            + "tier3_centroid_z,tier2_components,tier2_min_area,tier2_max_centroid_x\n");
        for (int resolution : new int[]{192, 256, 384}) {
            IslandDraft draft = new IslandDraft(resolution);
            draft.setPreset(IslandDraft.Preset.MINESHIELD_4_MAP);
            int res = draft.resolution();

            List<TierComponent> snow = tierComponents(draft, 3, false);
            assertEquals(1, snow.size(), res + " disconnected tier-3 ranges: " + snow);
            assertEquals(1, tierComponents(draft, 3, true).size(),
                res + " tier-3 range splits under 8-connectivity");
            TierComponent range = snow.get(0);
            double rangeX = range.centroidX() / res;
            double rangeZ = range.centroidZ() / res;
            assertTrue(rangeX >= 0.33 && rangeX <= 0.75,
                res + " tier-3 range is not central (x " + rangeX + ")");
            assertTrue(rangeZ >= 0.15 && rangeZ <= 0.65,
                res + " tier-3 range is not central-north (z " + rangeZ + ")");

            List<TierComponent> highland = tierComponents(draft, 2, true);
            assertFalse(highland.isEmpty(), res + " western tier-2 band missing");
            double minArea = 0.00065 * res * res;
            double maxCentroidX = 0.0;
            for (TierComponent c : highland) {
                double cx = c.centroidX() / res;
                assertTrue(cx < 0.45, res + " tier-2 component east of center at x " + cx);
                assertTrue(c.area() >= minArea,
                    res + " isolated tier-2 speck of " + c.area() + " cells at x " + cx);
                maxCentroidX = Math.max(maxCentroidX, cx);
            }

            csv.append(res).append(',').append(snow.size())
                .append(',').append(range.area())
                .append(',').append(fmt(rangeX)).append(',').append(fmt(rangeZ))
                .append(',').append(highland.size())
                .append(',').append(highland.stream().mapToInt(TierComponent::area).min().orElse(0))
                .append(',').append(fmt(maxCentroidX)).append('\n');
        }
        if (WRITE) {
            try {
                Path dir = Path.of("docs/evidence/phase11/after");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("mineshield_tiers.csv"), csv.toString(),
                    StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void shareCodeRoundTripKeepsPresetAndMask() {
        IslandDraft original = draft();
        IslandDraft decoded = ShareCode.decodeJson(ShareCode.toJson(original));
        assertEquals(IslandDraft.Preset.MINESHIELD_4_MAP, decoded.preset());
        int res = original.resolution();
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                assertEquals(original.isLand(x, z), decoded.isLand(x, z), "land at " + x + "," + z);
                assertEquals(original.tier(x, z), decoded.tier(x, z), "tier at " + x + "," + z);
            }
        }
    }

    private record TierComponent(int area, double centroidX, double centroidZ) {}

    private static List<TierComponent> tierComponents(IslandDraft draft, int tier, boolean diagonal) {
        int res = draft.resolution();
        boolean[] seen = new boolean[res * res];
        int[][] dirs = diagonal
            ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}
            : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<TierComponent> out = new ArrayList<>();
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                int start = z * res + x;
                if (seen[start] || !draft.isLand(x, z) || draft.tier(x, z) != tier) continue;
                int area = 0;
                double sumX = 0.0;
                double sumZ = 0.0;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, z});
                seen[start] = true;
                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    area++;
                    sumX += p[0];
                    sumZ += p[1];
                    for (int[] d : dirs) {
                        int nx = p[0] + d[0];
                        int nz = p[1] + d[1];
                        if (nx < 0 || nz < 0 || nx >= res || nz >= res) continue;
                        int i = nz * res + nx;
                        if (seen[i] || !draft.isLand(nx, nz) || draft.tier(nx, nz) != tier) continue;
                        seen[i] = true;
                        queue.add(new int[]{nx, nz});
                    }
                }
                out.add(new TierComponent(area, sumX / area, sumZ / area));
            }
        }
        return out;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static int largestComponent(IslandDraft draft) {
        int res = draft.resolution();
        boolean[] seen = new boolean[res * res];
        int largest = 0;
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                int start = z * res + x;
                if (!draft.isLand(x, z) || seen[start]) continue;
                int size = 0;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, z});
                seen[start] = true;
                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    size++;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = p[0] + d[0];
                        int nz = p[1] + d[1];
                        if (nx < 0 || nz < 0 || nx >= res || nz >= res) continue;
                        int i = nz * res + nx;
                        if (!draft.isLand(nx, nz) || seen[i]) continue;
                        seen[i] = true;
                        queue.add(new int[]{nx, nz});
                    }
                }
                largest = Math.max(largest, size);
            }
        }
        return largest;
    }

    private static void writeEvidence(IslandDraft draft, int res, int snow, int highland, int lowland) {
        try {
            Files.createDirectories(EVIDENCE);
            BufferedImage image = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
            int land = 0;
            for (int z = 0; z < res; z++) {
                for (int x = 0; x < res; x++) {
                    int rgb;
                    if (!draft.isLand(x, z)) {
                        rgb = 0x0B2A5B;
                    } else {
                        land++;
                        rgb = switch (draft.tier(x, z)) {
                            case 3 -> 0xF2F5F7;
                            case 2 -> 0x9A6A3A;
                            default -> 0x3F7A3A;
                        };
                    }
                    image.setRGB(x, z, rgb);
                }
            }
            int scale = 4;
            BufferedImage out = new BufferedImage(res * scale, res * scale, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < out.getHeight(); y++) {
                for (int x = 0; x < out.getWidth(); x++) {
                    out.setRGB(x, y, image.getRGB(x / scale, y / scale));
                }
            }
            ImageIO.write(out, "png", EVIDENCE.resolve("mineshield_4_mask.png").toFile());
            String csv = "land_pixels,snow_tier3,highland_tier2,lowland_tier1,land_fraction\n"
                + land + "," + snow + "," + highland + "," + lowland + ","
                + String.format(Locale.ROOT, "%.4f", land / (double) (res * res)) + "\n";
            Files.writeString(EVIDENCE.resolve("mineshield_4_stats.csv"), csv, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
