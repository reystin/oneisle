package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.ModernTerrainV2;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Bit-exact regression for the v2 terrain path. The snapshot was generated from the
 * v0.6.0 release commit 7a5174d, so it freezes pre-Phase-9 v2 behavior independently
 * of the current sources (invariant I1: v3 work must not alter v2 output).
 */
public class GoldenV2Test {
    private static final String GOLDEN_RESOURCE_PATH = "/golden/v2/golden_v2.bin.gz";
    private static final int MAGIC = 0x4F4D4E32;
    private static final int CONFIGS = 30;
    private static final int POINTS = 1500;

    private static final long SHAPE_SEED = 1337L;
    private static final long BIOME_SEED = 4242L;

    private static final IslandDraft.Preset[] PRESETS = {
        IslandDraft.Preset.NATURAL,
        IslandDraft.Preset.ROUND,
        IslandDraft.Preset.LONG,
        IslandDraft.Preset.CRESCENT,
        IslandDraft.Preset.ARCHIPELAGO
    };

    @Test
    void testGoldenV2Regression() throws Exception {
        InputStream in = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH);
        assertNotNull(in, "Golden v2 file must exist in src/test/resources/golden/v2");
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)))) {
            assertEquals(MAGIC, dis.readInt(), "Golden v2 magic mismatch");
            assertEquals(CONFIGS, dis.readInt(), "Must contain exactly 30 preset/relief/size configurations");
            assertEquals(POINTS, dis.readInt(), "Unexpected sample count");

            for (IslandDraft.Preset preset : PRESETS) {
                for (IslandDraft.Relief relief : IslandDraft.Relief.values()) {
                    for (int size : new int[]{2500, 8000}) {
                        assertEquals(preset.ordinal(), dis.readInt(), "Preset ordering mismatch");
                        assertEquals(relief.ordinal(), dis.readInt(), "Relief ordering mismatch");
                        assertEquals(size, dis.readInt(), "Size ordering mismatch");

                        IslandDraft draft = new IslandDraft();
                        draft.setPreset(preset);
                        draft.setRelief(relief);
                        draft.setIslandSize(size);
                        draft.setShapeSeed(SHAPE_SEED);
                        draft.setBiomeSeed(BIOME_SEED);
                        draft.setRiverDensity(0.35);
                        draft.setGenerationVersion(IslandDraft.GenerationVersion.V2);

                        double halfSpan = draft.worldSpan() * 0.5;
                        IslandMask mask = new IslandMask(
                            IslandDraft.RESOLUTION, IslandDraft.RESOLUTION,
                            -halfSpan, halfSpan, -halfSpan, halfSpan,
                            draft.relief().coastWidth, 400.0, draft.packedMask());
                        IslandShapeConfig cfg = new IslandShapeConfig(2, draft.isletDensity(),
                            draft.riverDensity(), false, 1000, Optional.empty(), Optional.empty(),
                            List.of(), Optional.of(mask), BIOME_SEED);
                        ModernTerrainV2 terrain = new ModernTerrainV2(cfg);

                        String where = preset + "/" + relief + "/" + size;
                        long lcg = 0x2545F4914F6CDD1DL;
                        double maxRange = halfSpan + 1500.0;
                        for (int i = 0; i < POINTS; i++) {
                            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                            double x = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (maxRange * 2) - maxRange;
                            lcg = lcg * 6364136223846793005L + 1442695040888963407L;
                            double z = (((lcg >>> 32) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * (maxRange * 2) - maxRange;

                            assertBits(dis.readLong(), terrain.continentalness(x, z),
                                "continentalness", where, i);
                            assertBits(dis.readLong(), terrain.computeSignedDistance(x, z, false),
                                "computeSignedDistance", where, i);
                            assertBits(dis.readLong(), terrain.signedDistance(x, z),
                                "signedDistance", where, i);
                            assertBits(dis.readLong(), terrain.erosionBias(x, z),
                                "erosionBias", where, i);
                        }
                    }
                }
            }
        }
    }

    private static void assertBits(long expectedBits, double actual, String metric, String where, int sample) {
        assertEquals(expectedBits, Double.doubleToLongBits(actual),
            metric + " mismatch at sample " + sample + " for " + where);
    }
}
