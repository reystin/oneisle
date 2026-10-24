package com.hack337.omniisle.command;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.ShareCode;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

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
                    String layers = "Land (Mask)" + (cfg.unpackedHeight != null ? ", Height Layer" : "")
                        + (cfg.riverDensity > 0.001 ? ", Rivers (" + (int)Math.round(cfg.riverDensity * 100) + "%)" : "");

                    context.getSource().sendSuccess(() -> Component.literal(
                        "=== OmniIsle World Info ===\n" +
                        "Generation Version: " + genVersion + (genVersion == 1 ? " (legacy)" : " (modern)") + "\n" +
                        "Resolution: " + resolution + "x" + resolution + "\n" +
                        "Island Size: " + size + " blocks\n" +
                        "Bounding Box: " + bbox + "\n" +
                        "World Spawn: " + spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ() + "\n" +
                        "World Border: " + (cfg.worldBorder ? "Enabled (+ " + cfg.worldBorderMargin + " margin)" : "Disabled") + "\n" +
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
                .then(Commands.literal("debug")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"continentalness", "height", "biome", "river", "tier"}, builder))
                        .executes(ctx -> executeDebug(ctx, StringArgumentType.getString(ctx, "mode"), 2000, 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(100, 16000))
                            .executes(ctx -> executeDebug(ctx, StringArgumentType.getString(ctx, "mode"), IntegerArgumentType.getInteger(ctx, "radius"), 8))
                            .then(Commands.argument("step", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> executeDebug(ctx, StringArgumentType.getString(ctx, "mode"),
                                    IntegerArgumentType.getInteger(ctx, "radius"), IntegerArgumentType.getInteger(ctx, "step")))
                            )
                        )
                    )
                )
                .then(Commands.literal("import")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("source", StringArgumentType.greedyString())
                        .executes(context -> {
                            String input = StringArgumentType.getString(context, "source").trim();
                            ServerLevel world = context.getSource().getLevel();
                            Path datapackDir = world.getServer().getWorldPath(LevelResource.DATAPACK_DIR);
                            try {
                                IslandDraft draft;
                                if (input.startsWith("OMNI2-")) {
                                    draft = ShareCode.decode(input);
                                } else {
                                    Path presetPath = FabricLoader.getInstance().getGameDir().resolve("omniisle/presets").resolve(input.endsWith(".json") ? input : input + ".json");
                                    if (!Files.exists(presetPath)) {
                                        presetPath = Path.of(input);
                                    }
                                    if (!Files.exists(presetPath)) {
                                        context.getSource().sendFailure(Component.literal("Preset file not found: " + input));
                                        return 0;
                                    }
                                    String json = Files.readString(presetPath);
                                    draft = ShareCode.decodeJson(json);
                                }

                                Files.createDirectories(datapackDir);
                                WorldPackBuilder.export(datapackDir, draft);
                                context.getSource().sendSuccess(() -> Component.literal(
                                    "Successfully imported preset into datapacks: " + datapackDir.resolve(WorldPackBuilder.FILE_NAME)
                                ).withStyle(ChatFormatting.GREEN), true);
                                return Command.SINGLE_SUCCESS;
                            } catch (Exception e) {
                                context.getSource().sendFailure(Component.literal("Failed to import preset: " + e.getMessage()));
                                return 0;
                            }
                        })
                    )
                )
            )
        );
    }

    private static int executeDebug(CommandContext<CommandSourceStack> context, String mode, int radius, int step) {
        ServerLevel world = context.getSource().getLevel();
        IslandDensityFunction idf = IslandDensityFunction.getActive();
        IslandShapeConfig cfg = idf != null ? idf.shapeConfig() : null;
        IslandTerrain terrain = idf != null ? idf.terrain() : null;

        if (cfg == null || terrain == null) {
            context.getSource().sendFailure(Component.literal("OmniIsle terrain sampler not active for this world."));
            return 0;
        }

        BiomeSource biomeSource = world.getChunkSource().getGenerator().getBiomeSource();
        BiomeResolver resolver = biomeSource.createCachingResolver(world.getChunkSource().randomState());

        int imgWidth = (2 * radius) / step + 1;
        int imgHeight = (2 * radius) / step + 1;
        BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);

        for (int zi = 0; zi < imgHeight; zi++) {
            int z = -radius + zi * step;
            for (int xi = 0; xi < imgWidth; xi++) {
                int x = -radius + xi * step;
                int color = 0xFF000000;

                switch (mode.toLowerCase()) {
                    case "continentalness" -> {
                        double c = terrain.continentalness(x, z);
                        if (c < -0.08) {
                            double t = Math.max(0.0, Math.min(1.0, (c - (-1.0)) / 0.92));
                            int blue = (int) Math.round(50 + t * 180);
                            color = 0xFF000000 | (blue / 2 << 8) | blue;
                        } else if (c < 0.02) {
                            color = 0xFFF0E6A0; // Sandy shore
                        } else {
                            double t = Math.max(0.0, Math.min(1.0, (c - 0.02) / 0.70));
                            int green = (int) Math.round(180 - t * 80);
                            int red = (int) Math.round(50 + t * 170);
                            int blue = (int) Math.round(40 + t * 180);
                            color = 0xFF000000 | (red << 16) | (green << 8) | blue;
                        }
                    }
                    case "height" -> {
                        double c = terrain.continentalness(x, z);
                        double h = IslandTerrain.continentalnessToElevation(c);
                        int gray = Math.max(0, Math.min(255, (int) Math.round((h / 256.0) * 255.0)));
                        color = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
                    }
                    case "biome" -> {
                        Holder<Biome> b = resolver.getNoiseBiome(x >> 2, 16, z >> 2);
                        int foliage = b.value().getFoliageColor();
                        color = 0xFF000000 | (foliage != 0 ? foliage : 0x8DB84D);
                    }
                    case "river" -> {
                        if (terrain.rivers() != null && terrain.rivers().isEnabled()) {
                            boolean isRiver = terrain.rivers().isRiver(x, z);
                            double carve = terrain.rivers().riverCarveContinentalness(x, z);
                            if (isRiver) {
                                color = 0xFF00DFFF; // Cyan river
                            } else if (carve > 0.0) {
                                color = 0xFF104E8B; // Deep blue riverbed valley
                            } else if (terrain.signedDistance(x, z) < 0.0) {
                                color = 0xFF0A1931; // Ocean
                            } else {
                                color = 0xFF353C42; // Land
                            }
                        } else {
                            color = 0xFF404040;
                        }
                    }
                    case "tier" -> {
                        if (cfg.mask != null && cfg.unpackedHeight != null) {
                            int res = cfg.mask.resolution > 0 ? cfg.mask.resolution : 192;
                            int px = (int) Math.round(((x - cfg.mask.minX) / (cfg.mask.maxX - cfg.mask.minX)) * res - 0.5);
                            int pz = (int) Math.round(((z - cfg.mask.minZ) / (cfg.mask.maxZ - cfg.mask.minZ)) * res - 0.5);
                            if (px >= 0 && px < res && pz >= 0 && pz < res) {
                                int tier = cfg.unpackedHeight[pz * res + px] & 3;
                                color = switch (tier) {
                                    case 0 -> 0xFF2E6F40; // Lowland
                                    case 1 -> 0xFF4E9A5A; // Normal
                                    case 2 -> 0xFFC29B38; // Plateau
                                    case 3 -> 0xFFFFFFFF; // Alpine
                                    default -> 0xFF000000;
                                };
                            } else {
                                color = 0xFF0A1830;
                            }
                        } else {
                            color = 0xFF4E9A5A;
                        }
                    }
                    default -> {
                        context.getSource().sendFailure(Component.literal("Unknown debug mode: " + mode));
                        return 0;
                    }
                }
                image.setRGB(xi, zi, color);
            }
        }

        try {
            Path outDir = FabricLoader.getInstance().getGameDir().resolve("omniisle/debug");
            Files.createDirectories(outDir);
            File outFile = outDir.resolve("debug_" + mode.toLowerCase() + "_" + System.currentTimeMillis() + ".png").toFile();
            ImageIO.write(image, "png", outFile);
            context.getSource().sendSuccess(() -> Component.literal(
                "Exported " + mode + " debug map (" + imgWidth + "x" + imgHeight + " px, radius " + radius + ") to: " + outFile.getAbsolutePath()
            ).withStyle(ChatFormatting.GREEN), false);
            return Command.SINGLE_SUCCESS;
        } catch (IOException e) {
            context.getSource().sendFailure(Component.literal("Failed to write debug image: " + e.getMessage()));
            return 0;
        }
    }
}
