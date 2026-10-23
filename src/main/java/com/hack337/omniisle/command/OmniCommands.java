package com.hack337.omniisle.command;

import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.mojang.brigadier.Command;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;

public final class OmniCommands {
    private OmniCommands() {}

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("omniisle")
                .then(Commands.literal("info").executes(context -> {
                    ServerLevel world = context.getSource().getLevel();
                    IslandDensityFunction idf = IslandDensityFunction.getActive();
                    IslandShapeConfig cfg = idf != null ? idf.shapeConfig() : null;

                    if (cfg == null) {
                        context.getSource().sendFailure(Component.literal("OmniIsle island configuration not found for this world."));
                        return 0;
                    }

                    int genVersion = cfg.generationVersion;
                    IslandMask mask = cfg.mask;
                    int resolution = mask != null ? mask.resolution : 192;
                    int size = mask != null ? (int)Math.round(mask.maxX - mask.minX) : 4000;
                    String bbox = mask != null
                        ? String.format("[%.0f, %.0f] to [%.0f, %.0f]", mask.minX, mask.minZ, mask.maxX, mask.maxZ)
                        : "N/A";
                    BlockPos spawn = world.getRespawnData().pos();
                    String layers = "Land (Mask)";

                    context.getSource().sendSuccess(() -> Component.literal(
                        "=== OmniIsle World Info ===\n" +
                        "Generation Version: " + genVersion + (genVersion == 1 ? " (legacy)" : " (modern)") + "\n" +
                        "Resolution: " + resolution + "x" + resolution + "\n" +
                        "Island Size: " + size + " blocks\n" +
                        "Bounding Box: " + bbox + "\n" +
                        "World Spawn: " + spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ() + "\n" +
                        "Enabled Layers: " + layers
                    ).withStyle(ChatFormatting.AQUA), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("verify")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(context -> {
                        ServerLevel world = context.getSource().getLevel();
                        BiomeSource source = world.getChunkSource().getGenerator().getBiomeSource();
                        BiomeResolver resolver = source.createCachingResolver(world.getChunkSource().randomState());
                        int[][] points = {
                            {0, 0}, {256, 0}, {-256, 0}, {0, 256}, {0, -256},
                            {768, 0}, {-768, 0}, {0, 768}, {0, -768}, {1536, 1536}
                        };
                        StringJoiner samples = new StringJoiner(", ");
                        for (int[] point : points) {
                            Holder<Biome> biome = resolver.getNoiseBiome(point[0] >> 2, 16, point[1] >> 2);
                            world.getChunk(point[0] >> 4, point[1] >> 4);
                            int topY = world.getHeight(Heightmap.Types.WORLD_SURFACE, point[0], point[1]);
                            String topBlock = world.getBlockState(new BlockPos(point[0], topY - 1, point[1])).getBlock().getDescriptionId();
                            samples.add(point[0] + "/" + point[1] + "=" + biome.getRegisteredName() + "@" + topY + ":" + topBlock);
                        }
                        Map<String, String> found = new LinkedHashMap<>();
                        for (int z = -2048; z <= 2048; z += 64) {
                            for (int x = -2048; x <= 2048; x += 64) {
                                Holder<Biome> biome = resolver.getNoiseBiome(x >> 2, 16, z >> 2);
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
                    })
                )
            )
        );
    }
}
