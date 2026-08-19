package com.hack337.omniisle.command;

import com.mojang.brigadier.Command;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import java.util.StringJoiner;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;

public final class OmniCommands {
    private OmniCommands() {}

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("omniisle")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("verify").executes(context -> {
                    ServerLevel world = context.getSource().getLevel();
                    BiomeSource source = world.getChunkSource().getGenerator().getBiomeSource();
                    Climate.Sampler sampler = world.getChunkSource().randomState().sampler();
                    int[][] points = {
                        {0, 0}, {256, 0}, {-256, 0}, {0, 256}, {0, -256},
                        {768, 0}, {-768, 0}, {0, 768}, {0, -768}, {1536, 1536}
                    };
                    StringJoiner samples = new StringJoiner(", ");
                    for (int[] point : points) {
                        Holder<Biome> biome = source.getNoiseBiome(point[0] >> 2, 16, point[1] >> 2, sampler);
                        world.getChunk(point[0] >> 4, point[1] >> 4);
                        int topY = world.getHeight(Heightmap.Types.WORLD_SURFACE, point[0], point[1]);
                        String topBlock = world.getBlockState(new BlockPos(point[0], topY - 1, point[1])).getBlock().getDescriptionId();
                        samples.add(point[0] + "/" + point[1] + "=" + biome.getRegisteredName() + "@" + topY + ":" + topBlock);
                    }
                    Map<String, String> found = new LinkedHashMap<>();
                    for (int z = -2048; z <= 2048; z += 64) {
                        for (int x = -2048; x <= 2048; x += 64) {
                            Holder<Biome> biome = source.getNoiseBiome(x >> 2, 16, z >> 2, sampler);
                            found.putIfAbsent(biome.getRegisteredName(), x + "/" + z);
                        }
                    }
                    context.getSource().sendSuccess(() -> Component.literal(
                        "BiomeSource=" + source.getClass().getName() +
                        ", rework=" + (source instanceof OmniBiomeSource) +
                        "; samples: " + samples +
                        "; surface scan (biome=first x/z): " + found
                    ), false);
                    return Command.SINGLE_SUCCESS;
                }))
            )
        );
    }
}
