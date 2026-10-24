package com.hack337.omniisle.test;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.continent.ShoreSettings;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import java.util.Optional;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks v2/v3 biome-source output (including the per-column cache) to exact hashes. */
public class BiomeSourceV2V3RegressionTest {
    @BeforeAll
    static void boot() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void v2AndV3HashesStayExact() {
        for (int version : new int[]{2, 3}) {
            IslandDraft draft = new IslandDraft();
            draft.setPreset(IslandDraft.Preset.ROUND);
            draft.setRelief(IslandDraft.Relief.BALANCED);
            draft.setIslandSize(4000);
            draft.setShapeSeed(4242);
            draft.setBiomeSeed(4242);
            draft.setRiverDensity(0.35);
            draft.setGenerationVersion(version == 2
                ? IslandDraft.GenerationVersion.V2 : IslandDraft.GenerationVersion.V3);
            ShoreSettings shore = new ShoreSettings(0.30, 0.6, "natural", true);
            IslandShapeConfig cfg = new IslandShapeConfig(version, 0.25, 0.35, false, 1000,
                Optional.empty(), Optional.empty(), java.util.List.of(),
                Optional.of(TestWorldgen.mask(draft)), 4242,
                version == 3 ? Optional.of(shore) : Optional.empty());
            IslandTerrain terrain = IslandTerrain.create(cfg);
            IslandDensityFunction.setActive(new IslandDensityFunction(cfg));
            Climate.Sampler sampler = TestWorldgen.sampler(terrain);
            OmniBiomeSource source = TestWorldgen.biomeSource(720, 4242, version,
                "north_south", 0.5, "standard", shore, version == 3);
            long hash = 0xCBF29CE484222325L;
            int count = 0;
            for (int qz = -120; qz <= 120; qz += 2) {
                for (int qx = -120; qx <= 120; qx += 2) {
                    for (int y : new int[]{4, -4}) {
                        String id = source.getNoiseBiome(qx, y, qz, sampler).unwrapKey()
                            .map(k -> k.identifier().toString()).orElse("");
                        for (int i = 0; i < id.length(); i++) {
                            hash ^= id.charAt(i);
                            hash *= 0x100000001B3L;
                        }
                        hash ^= 0x9E3779B97F4A7C15L;
                        count++;
                    }
                }
            }
            long expected = version == 2 ? -4990092939012319655L : 7583129763709705084L;
            assertEquals(29282, count);
            assertEquals(expected, hash, "v" + version + " biome-source output changed");
        }
    }
}
