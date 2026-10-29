package com.reystin.oneisle.command;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.ShareCode;
import com.reystin.oneisle.client.WorldPackBuilder;
import com.reystin.oneisle.continent.IslandMask;
import com.reystin.oneisle.continent.IslandShapeConfig;
import com.reystin.oneisle.continent.IslandTerrain;
import com.reystin.oneisle.continent.ShoreField;
import com.reystin.oneisle.worldgen.IslandDensityFunction;
import com.reystin.oneisle.worldgen.OneBiomeSource;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;

public final class OneCommands {
    private OneCommands() {}

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("oneisle")
                .then(Commands.literal("info").executes(context -> {
                    ServerLevel world = context.getSource().getLevel();
                    IslandDensityFunction idf = IslandDensityFunction.getActive();
                    IslandShapeConfig cfg = idf != null ? idf.shapeConfig() : null;

                    if (cfg == null) {
                        context.getSource().sendFailure(Component.literal("OneIsle island configuration not found for this world."));
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
                    String shares = shareSummary(world);

                    context.getSource().sendSuccess(() -> Component.literal(
                        "=== OneIsle World Info ===\n" +
                        "Generation Version: " + genVersion + (genVersion == 1 ? " (legacy)" : " (modern)") + "\n" +
                        "Resolution: " + resolution + "x" + resolution + "\n" +
                        "Island Size: " + size + " blocks\n" +
                        "Bounding Box: " + bbox + "\n" +
                        "World Spawn: " + spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ() + "\n" +
                        "World Border: " + (cfg.worldBorder ? "Enabled (+ " + cfg.worldBorderMargin + " margin)" : "Disabled") + "\n" +
                        "Enabled Layers: " + layers +
                        (shares.isEmpty() ? "" : "\n" + shares)
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
                            ", rework=" + (source instanceof OneBiomeSource) +
                            "; samples: " + samples +
                            "; surface scan (biome=first x/z): " + found
                        ), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("query")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("spawn").executes(OneCommands::executeSpawnQuery))
                    .then(Commands.literal("eye").executes(OneCommands::executeEyeQuery))
                    .then(Commands.literal("dry")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                            .then(Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                .executes(context -> executeDryQuery(context,
                                    IntegerArgumentType.getInteger(context, "x"),
                                    IntegerArgumentType.getInteger(context, "z"))))))
                    .then(Commands.literal("column")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                            .then(Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                .executes(context -> executeColumnQuery(context,
                                    IntegerArgumentType.getInteger(context, "x"),
                                    IntegerArgumentType.getInteger(context, "z"))))))
                    .then(Commands.literal("structure")
                        .then(Commands.argument("structure", StringArgumentType.string())
                            .then(Commands.argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                .then(Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                    .executes(context -> executeStructureQuery(context,
                                        StringArgumentType.getString(context, "structure"),
                                        IntegerArgumentType.getInteger(context, "x"),
                                        IntegerArgumentType.getInteger(context, "z"))))))))
                .then(Commands.literal("debug")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"continentalness", "height", "biome", "river", "tier", "shore"}, builder))
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
                                if (input.startsWith("ONE2-")) {
                                    draft = ShareCode.decode(input);
                                } else {
                                    Path presetPath = FabricLoader.getInstance().getGameDir().resolve("oneisle/presets").resolve(input.endsWith(".json") ? input : input + ".json");
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

    private static double slopeAt(IslandTerrain terrain, double x, double z) {
        double cE = terrain.continentalness(x + 4.0, z);
        double cW = terrain.continentalness(x - 4.0, z);
        double cS = terrain.continentalness(x, z + 4.0);
        double cN = terrain.continentalness(x, z - 4.0);
        return Math.hypot((cE - cW) / 8.0, (cS - cN) / 8.0) * 64.0;
    }

    private static String shareSummary(ServerLevel world) {
        BiomeSource source = world.getChunkSource().getGenerator().getBiomeSource();
        if (!(source instanceof OneBiomeSource omni) || !omni.hasWeightedShares()) return "";
        com.reystin.oneisle.continent.BiomeShare.Settings share = omni.biomeShareSettings();
        StringJoiner weights = new StringJoiner(", ");
        for (int i = 0; i < com.reystin.oneisle.continent.BiomeShare.catalogSize(); i++) {
            com.reystin.oneisle.continent.BiomeShare.Variant variant =
                com.reystin.oneisle.continent.BiomeShare.variant(i);
            if (variant == null || variant.terrainDriven()) continue;
            int weight = share.weight(variant.id());
            if (weight == com.reystin.oneisle.continent.BiomeShare.DEFAULT_WEIGHT) continue;
            weights.add(variant.id() + "=" + weight + "%");
        }
        StringJoiner achieved = new StringJoiner(", ");
        for (int i = 0; i < com.reystin.oneisle.continent.BiomeShare.catalogSize(); i++) {
            com.reystin.oneisle.continent.BiomeShare.Variant variant =
                com.reystin.oneisle.continent.BiomeShare.variant(i);
            if (variant == null) continue;
            Double value = share.achieved().get(variant.id());
            if (value == null) continue;
            achieved.add(String.format(java.util.Locale.ROOT, "%s=%.1f%%", variant.id(), value));
        }
        StringBuilder out = new StringBuilder("Biome Share: ")
            .append(share.feasible() ? "calibrated" : "infeasible (targets unreachable)")
            .append("\nWeights: ").append(weights.length() > 0 ? weights : "all 100%");
        if (achieved.length() > 0) out.append("\nAchieved: ").append(achieved);
        return out.toString();
    }

    private static int executeSpawnQuery(CommandContext<CommandSourceStack> context) {
        ServerLevel world = context.getSource().getLevel();
        IslandDensityFunction idf = IslandDensityFunction.getActive();
        IslandShapeConfig cfg = idf != null ? idf.shapeConfig() : null;
        IslandTerrain terrain = idf != null ? idf.terrain() : null;
        BlockPos spawn = world.getRespawnData().pos();
        world.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        String biome = world.getBiome(spawn).unwrapKey()
            .map(key -> key.identifier().toString()).orElse("unknown");
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, spawn.getX(), spawn.getZ());
        BlockPos below = spawn.below();
        String feetBlock = world.getBlockState(spawn).getBlock().getDescriptionId();
        String groundBlock = world.getBlockState(below).getBlock().getDescriptionId();
        boolean feetWater = !world.getFluidState(spawn).isEmpty();
        boolean groundWater = !world.getFluidState(below).isEmpty();
        boolean dry = feetBlock.equals("block.minecraft.air") && !groundWater
            && !groundBlock.equals("block.minecraft.air");
        boolean river = false;
        double terrainElevation = -1.0;
        if (terrain != null) {
            river = terrain.rivers() != null && terrain.rivers().isEnabled()
                && terrain.rivers().isRiver(spawn.getX() + 0.5, spawn.getZ() + 0.5);
            terrainElevation = IslandTerrain.continentalnessToElevation(
                terrain.continentalness(spawn.getX() + 0.5, spawn.getZ() + 0.5));
        }
        String markerX = "none";
        String markerZ = "none";
        boolean match = false;
        if (cfg != null && cfg.spawn != null) {
            markerX = String.valueOf(cfg.spawn.x());
            markerZ = String.valueOf(cfg.spawn.z());
            match = spawn.getX() == cfg.spawn.x() && spawn.getZ() == cfg.spawn.z();
        }
        boolean land = terrain != null
            && terrain.signedDistance(spawn.getX() + 0.5, spawn.getZ() + 0.5) > 0.0;
        int coastDistance = -1;
        if (terrain != null) {
            int nearest = 999;
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI * 2.0 / 16.0;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                for (int r = 2; r <= 96; r += 2) {
                    if (terrain.signedDistance(spawn.getX() + 0.5 + dx * r,
                        spawn.getZ() + 0.5 + dz * r) <= 0.0) {
                        nearest = Math.min(nearest, r);
                        break;
                    }
                }
            }
            coastDistance = nearest;
        }
        int nearestWater = 65;
        int dryProbes = 0;
        int dryProbesOk = 0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            for (int r = 8; r <= 64; r += 4) {
                int x = (int) Math.floor(spawn.getX() + 0.5 + dx * r);
                int z = (int) Math.floor(spawn.getZ() + 0.5 + dz * r);
                world.getChunk(x >> 4, z >> 4);
                int top = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                boolean water = !world.getFluidState(new BlockPos(x, top - 1, z)).isEmpty();
                dryProbes++;
                if (water) {
                    nearestWater = Math.min(nearestWater, r);
                    break;
                }
                dryProbesOk++;
            }
        }
        String line = "ONE_SPAWN spawn_x=" + spawn.getX() + " spawn_y=" + spawn.getY()
            + " spawn_z=" + spawn.getZ() + " surface_y=" + surfaceY
            + " marker_x=" + markerX + " marker_z=" + markerZ + " match=" + match
            + " biome=" + biome + " land=" + land + " coast_distance=" + coastDistance
            + " nearest_water=" + nearestWater + " dry_probes=" + dryProbesOk + "/" + dryProbes
            + " feet_block=" + feetBlock + " ground_block=" + groundBlock
            + " feet_water=" + feetWater + " dry=" + dry + " river=" + river
            + " terrain_elevation=" + String.format(java.util.Locale.ROOT, "%.1f", terrainElevation);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeEyeQuery(CommandContext<CommandSourceStack> context) {
        ServerLevel world = context.getSource().getLevel();
        BlockPos spawn = world.getRespawnData().pos();
        BlockPos target = world.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, spawn, 100, false);
        if (target == null) {
            context.getSource().sendSuccess(() -> Component.literal("ONE_EYE found=false"), false);
            return Command.SINGLE_SUCCESS;
        }
        int chunkX = Math.floorDiv(target.getX(), 16);
        int chunkZ = Math.floorDiv(target.getZ(), 16);
        world.getChunk(chunkX, chunkZ);
        Structure stronghold = context.getSource().getServer().registryAccess()
            .lookupOrThrow(Registries.STRUCTURE).getValue(Identifier.withDefaultNamespace("stronghold"));
        int pieces = 0;
        if (stronghold != null) {
            for (StructureStart start : world.structureManager().startsForStructure(chunkX, chunkZ, stronghold)) {
                if (start.isValid()) {
                    pieces = Math.max(pieces, start.getPieces().size());
                }
            }
        }
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, target.getX(), target.getZ());
        String line = "ONE_EYE found=true x=" + target.getX() + " z=" + target.getZ()
            + " chunk=" + chunkX + "," + chunkZ + " surface_y=" + surfaceY
            + " stronghold_pieces=" + pieces;
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDryQuery(CommandContext<CommandSourceStack> context, int x, int z) {
        ServerLevel world = context.getSource().getLevel();
        int surfaceY = columnTop(world, x, z);
        boolean centerDry = !columnWater(world, x, z, surfaceY);
        int nearestDry = 113;
        int nearestWater = 113;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8.0;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            boolean dryFound = centerDry;
            boolean waterFound = !centerDry;
            if (dryFound) nearestDry = 0;
            if (waterFound) nearestWater = 0;
            for (int r = 4; r <= 112; r += 4) {
                int sx = (int) Math.floor(x + 0.5 + dx * r);
                int sz = (int) Math.floor(z + 0.5 + dz * r);
                int top = columnTop(world, sx, sz);
                boolean water = columnWater(world, sx, sz, top);
                if (!dryFound && !water) {
                    nearestDry = Math.min(nearestDry, r);
                    dryFound = true;
                }
                if (!waterFound && water) {
                    nearestWater = Math.min(nearestWater, r);
                    waterFound = true;
                }
                if (dryFound && waterFound) break;
            }
        }
        int top = surfaceY;
        String biome = world.getBiome(new BlockPos(x, Math.max(top - 1, world.getMinY()), z)).unwrapKey()
            .map(key -> key.identifier().toString()).orElse("unknown");
        String block = world.getBlockState(new BlockPos(x, top - 1, z)).getBlock().getDescriptionId();
        String line = "ONE_DRY x=" + x + " z=" + z + " surface_y=" + top
            + " center_dry=" + centerDry + " center_water=" + !centerDry
            + " nearest_dry=" + nearestDry + " nearest_water=" + nearestWater
            + " biome=" + biome + " block=" + block;
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int columnTop(ServerLevel world, int x, int z) {
        world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        return world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }

    private static boolean columnWater(ServerLevel world, int x, int z, int surfaceY) {
        return surfaceY > world.getMinY()
            && !world.getFluidState(new BlockPos(x, surfaceY - 1, z)).isEmpty();
    }

    private static int executeColumnQuery(CommandContext<CommandSourceStack> context, int x, int z) {
        ServerLevel world = context.getSource().getLevel();
        IslandDensityFunction idf = IslandDensityFunction.getActive();
        IslandTerrain terrain = idf != null ? idf.terrain() : null;
        world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int floorY = world.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        BlockPos top = new BlockPos(x, surfaceY - 1, z);
        String topBlock = world.getBlockState(top).getBlock().getDescriptionId();
        boolean water = !world.getFluidState(top).isEmpty();
        String biome = world.getBiome(new BlockPos(x, Math.max(floorY, water ? surfaceY : floorY), z))
            .unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
        double c = terrain != null ? terrain.continentalness(x + 0.5, z + 0.5) : Double.NaN;
        double d = terrain != null ? terrain.signedDistance(x + 0.5, z + 0.5) : Double.NaN;
        double estimated = terrain != null ? IslandTerrain.continentalnessToElevation(c) : Double.NaN;
        String line = "ONE_COLUMN x=" + x + " z=" + z + " surface_y=" + surfaceY
            + " floor_y=" + floorY + " biome=" + biome + " block=" + topBlock
            + " water=" + water + " c=" + String.format(java.util.Locale.ROOT, "%.4f", c)
            + " d=" + String.format(java.util.Locale.ROOT, "%.1f", d)
            + " estimated_y=" + String.format(java.util.Locale.ROOT, "%.1f", estimated);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeStructureQuery(CommandContext<CommandSourceStack> context,
                                             String structureId, int x, int z) {
        ServerLevel world = context.getSource().getLevel();
        Identifier id = Identifier.tryParse(structureId);
        Structure structure = id == null ? null : context.getSource().getServer().registryAccess()
            .lookupOrThrow(Registries.STRUCTURE).getValue(id);
        if (structure == null) {
            context.getSource().sendFailure(Component.literal("Unknown structure id: " + structureId));
            return 0;
        }

        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        ChunkAccess chunk = world.getChunk(chunkX, chunkZ);
        StructureStart start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            for (StructureStart candidate : world.structureManager()
                .startsForStructure(chunkX, chunkZ, structure)) {
                if (candidate.isValid() && candidate.getChunkPos().x() == chunkX
                    && candidate.getChunkPos().z() == chunkZ) {
                    start = candidate;
                    break;
                }
            }
        }
        if (start == null || !start.isValid()) {
            String line = "ONE_STRUCTURE structure=" + id + " found=false chunk=" + chunkX + "," + chunkZ
                + " x=" + x + " z=" + z;
            context.getSource().sendSuccess(() -> Component.literal(line), false);
            return Command.SINGLE_SUCCESS;
        }

        BoundingBox box = start.getBoundingBox();
        int pieceMinY = Integer.MAX_VALUE;
        int pieceMaxY = Integer.MIN_VALUE;
        StringBuilder pieceBoxes = new StringBuilder();
        for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
            BoundingBox pieceBox = piece.getBoundingBox();
            pieceMinY = Math.min(pieceMinY, pieceBox.minY());
            pieceMaxY = Math.max(pieceMaxY, pieceBox.maxY());
            if (pieceBoxes.length() < 160) {
                pieceBoxes.append(pieceBox.minY()).append('/').append(pieceBox.maxY()).append(',');
            }
        }
        int centerX = (box.minX() + box.maxX()) / 2;
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        world.getChunk(Math.floorDiv(centerX, 16), Math.floorDiv(centerZ, 16));
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
        String biome = world.getBiome(new BlockPos(centerX, Math.max(world.getMinY(), surfaceY - 1), centerZ))
            .unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
        String line = "ONE_STRUCTURE structure=" + id + " found=true"
            + " chunk=" + start.getChunkPos().x() + "," + start.getChunkPos().z()
            + " query_chunk=" + chunkX + "," + chunkZ
            + " x=" + centerX + " z=" + centerZ
            + " y=" + box.minY() + " y_min=" + box.minY() + " y_max=" + box.maxY()
            + " piece_y_min=" + pieceMinY + " piece_y_max=" + pieceMaxY
            + " piece_boxes=" + pieceBoxes
            + " surface_y=" + surfaceY + " biome=" + biome
            + " pieces=" + start.getPieces().size();
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDebug(CommandContext<CommandSourceStack> context, String mode, int radius, int step) {
        ServerLevel world = context.getSource().getLevel();
        IslandDensityFunction idf = IslandDensityFunction.getActive();
        IslandShapeConfig cfg = idf != null ? idf.shapeConfig() : null;
        IslandTerrain terrain = idf != null ? idf.terrain() : null;

        if (cfg == null || terrain == null) {
            context.getSource().sendFailure(Component.literal("OneIsle terrain sampler not active for this world."));
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
                        String path = b.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                        if (path.equals("beach")) {
                            color = 0xFFE8D9A0;
                        } else if (path.equals("snowy_beach")) {
                            color = 0xFFF4F0E0;
                        } else if (path.equals("stony_shore")) {
                            color = 0xFF8C8C8C;
                        } else if (path.contains("ocean")) {
                            color = path.startsWith("deep") ? 0xFF123260 : 0xFF2F5E9E;
                        } else if (path.equals("river") || path.equals("frozen_river")) {
                            color = 0xFF4F8FD0;
                        } else {
                            int foliage = b.value().getFoliageColor();
                            color = 0xFF000000 | (foliage != 0 ? foliage : 0x8DB84D);
                        }
                    }
                    case "shore" -> {
                        if (cfg.shore == null || !cfg.shore.enabled()) {
                            context.getSource().sendFailure(Component.literal(
                                "Shore field is not active (generation_version " + cfg.generationVersion + ")."));
                            return 0;
                        }
                        double d = terrain.signedDistance(x, z);
                        double limit = ShoreField.intertidalLimit(x, z, cfg.biomeSeed);
                        if (d < limit) {
                            color = 0xFF2F5E9E;
                        } else if (d > ShoreField.MAX_INLAND) {
                            color = 0xFF5C8A4E;
                        } else {
                            double c = terrain.continentalness(x, z);
                            ShoreField.Ring ring = ShoreField.ring(terrain, x, z, d);
                            int outcome = ShoreField.outcome(d, x, z, slopeAt(terrain, x, z),
                                terrain.heightTierAt(x, z), c, ring, cfg.shore, cfg.biomeSeed);
                            color = switch (outcome) {
                                case ShoreField.SAND -> 0xFFE8D9A0;
                                case ShoreField.STONY -> 0xFF8C8C8C;
                                default -> d >= 0.0 ? 0xFF5C8A4E : 0xFF2F5E9E;
                            };
                        }
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
            Path outDir = FabricLoader.getInstance().getGameDir().resolve("oneisle/debug");
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
