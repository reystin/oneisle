package com.reystin.oneisle.test;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.worldgen.IslandDensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class IslandDensityFunctionTest {
    @BeforeAll
    static void boot() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void columnFunctionEvaluatesOncePerXZAndIgnoresY() {
        IslandDraft draft = new IslandDraft();
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setIslandSize(4000);
        draft.setShapeSeed(4242);
        draft.setBiomeSeed(4242);
        IslandShapeConfig config = TestWorldgen.config(draft, 3, 0.25);
        IslandDensityFunction function = new IslandDensityFunction(config);

        assertEquals(DensityFunction.AXIS_X | DensityFunction.AXIS_Z, function.domainAxes(),
            "the island density must advertise a 2D XZ domain");
        assertFalse((function.domainAxes() & DensityFunction.AXIS_Y) != 0,
            "Y must not participate in the sampled column");

        DensitySampler sampler = function.compileSampler(null);
        DensityVolume volume = new DensityVolume(8, 32, 8, -32, -64, -32);
        DensityBuffer buffer = DensityBuffer.createUnpooled(volume.size());
        sampler.sampleVolume(SamplerContext.EMPTY_UNCACHED, buffer, volume);

        for (int zIdx = 0; zIdx < volume.sizeZ(); zIdx++) {
            for (int xIdx = 0; xIdx < volume.sizeX(); xIdx++) {
                float first = buffer.get(volume.indexUnchecked(xIdx, 0, zIdx));
                for (int yIdx = 1; yIdx < volume.sizeY(); yIdx++) {
                    assertEquals(first, buffer.get(volume.indexUnchecked(xIdx, yIdx, zIdx)), 0.0f,
                        "column value changed with Y at x=" + volume.blockX(xIdx) + ", z=" + volume.blockZ(zIdx));
                }
                float direct = sampler.sampleValue(SamplerContext.EMPTY_UNCACHED,
                    volume.blockX(xIdx), 123, volume.blockZ(zIdx));
                assertEquals(first, direct, 0.0f, "sampleVolume and sampleValue disagree");
            }
        }
    }
}
