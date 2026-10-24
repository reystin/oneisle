package com.hack337.omniisle;

import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.ShareCode;
import com.hack337.omniisle.client.WorldPackBuilder;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import com.hack337.omniisle.worldgen.FixedMultiStructurePlacement;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.hack337.omniisle.worldgen.RelaxedPyramids;
import com.hack337.omniisle.worldgen.SingleStructurePlacement;
import com.hack337.omniisle.worldgen.SpawnPlateauDensity;
import com.hack337.omniisle.worldgen.RiverLife;
import com.hack337.omniisle.worldgen.SpawnSavedData;
import com.hack337.omniisle.command.OmniCommands;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OmniIsleMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OmniIsleMod.class);

    @Override
    public void onInitialize() {
        IslandDensityFunction.registerCodec();
        SpawnPlateauDensity.registerCodec();
        OmniBiomeSource.registerCodec();
        SingleStructurePlacement.registerType();
        FixedMultiStructurePlacement.registerType();
        RelaxedPyramids.register();
        RiverLife.installSpawns();
        OmniCommands.registerCommands();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("omniisle.json");
            if (Files.exists(configPath)) {
                try {
                    Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
                    Path targetPack = datapackDir.resolve(WorldPackBuilder.FILE_NAME);
                    if (!Files.exists(targetPack)) {
                        String json = Files.readString(configPath);
                        IslandDraft draft = ShareCode.decodeJson(json);
                        Files.createDirectories(datapackDir);
                        WorldPackBuilder.export(datapackDir, draft);
                        LOGGER.info("OmniIsle: Automatically generated island datapack from config/omniisle.json");
                    }
                } catch (Exception e) {
                    LOGGER.error("OmniIsle: Failed to generate island datapack from config/omniisle.json", e);
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (IslandShapeConfig.unknownGenerationVersionDetected) {
                int unknownVersion = IslandShapeConfig.lastUnknownGenerationVersion;
                int maxSupported = IslandShapeConfig.MAX_SUPPORTED_GENERATION_VERSION;
                handler.player.sendSystemMessage(
                    Component.translatable("omniisle.warning.unsupported_version", unknownVersion, maxSupported, maxSupported)
                        .withStyle(ChatFormatting.YELLOW)
                );
                IslandShapeConfig.unknownGenerationVersionDetected = false;
            }
        });

        ServerLevelEvents.LOAD.register((server, world) -> {
            if (world.dimension() != Level.OVERWORLD) return;
            IslandDensityFunction activeDf = IslandDensityFunction.getActive();
            if (activeDf == null) return;
            IslandShapeConfig config = activeDf.shapeConfig();
            if (config == null) return;

            if (config.generationVersion < 3) {
                SpawnSavedData spawnData = world.getDataStorage().computeIfAbsent(SpawnSavedData.TYPE);
                if (!spawnData.initialized) {
                    int[] target = spawnTarget(config);
                    BlockPos safePos = findSafeSpawn(world, target[0], target[1]);
                    world.setRespawnData(LevelData.RespawnData.of(world.dimension(), safePos, 0.0f, 0.0f));
                    spawnData.initialized = true;
                    spawnData.setDirty();
                }
            }

            if (config.worldBorder && config.mask().isPresent()) {
                var mask = config.mask().get();
                double cx = 0.5 * (mask.minX + mask.maxX);
                double cz = 0.5 * (mask.minZ + mask.maxZ);
                double spanX = mask.maxX - mask.minX;
                double spanZ = mask.maxZ - mask.minZ;
                double borderSize = Math.max(spanX, spanZ) + 2.0 * config.worldBorderMargin;
                world.getWorldBorder().setCenter(cx, cz);
                world.getWorldBorder().setSize(borderSize);
                LOGGER.info("OmniIsle: Set world border centered at [{}, {}] with size {}", cx, cz, borderSize);
            }
        });

        // Vanilla picks the global spawn while loading levels, after ServerLevelEvents.LOAD,
        // so v3 spawns must be reconciled once the server has fully started.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel overworld = server.overworld();
            if (overworld == null) return;
            IslandDensityFunction activeDf = IslandDensityFunction.getActive();
            if (activeDf == null) return;
            IslandShapeConfig config = activeDf.shapeConfig();
            if (config == null || config.generationVersion < 3) return;

            int[] target = spawnTarget(config);
            BlockPos safePos = findModernSpawn(overworld, target[0], target[1]);
            SpawnSavedData spawnData = overworld.getDataStorage().computeIfAbsent(SpawnSavedData.TYPE);
            BlockPos current = overworld.getRespawnData().pos();
            boolean mismatch = !current.equals(safePos);
            if (!spawnData.initialized || mismatch) {
                overworld.setRespawnData(LevelData.RespawnData.of(overworld.dimension(), safePos, 0.0f, 0.0f));
                spawnData.initialized = true;
                spawnData.setDirty();
                LOGGER.info("OmniIsle: Applied world spawn at {} (previous {}, marker {}), land={}",
                    safePos, current, "[" + target[0] + ", " + target[1] + "]",
                    activeDf.terrain() == null || activeDf.terrain().signedDistance(safePos.getX(), safePos.getZ()) > 0.0);
            }
        });
    }

    private static int[] spawnTarget(IslandShapeConfig config) {
        int targetX = 0;
        int targetZ = 0;
        if (config.spawn().isPresent()) {
            targetX = config.spawn().get().x();
            targetZ = config.spawn().get().z();
        } else if (config.mask().isPresent()) {
            var mask = config.mask().get();
            targetX = (int) Math.round((mask.minX + mask.maxX) / 2.0);
            targetZ = (int) Math.round((mask.minZ + mask.maxZ) / 2.0);
        }
        return new int[]{targetX, targetZ};
    }

    private static BlockPos findModernSpawn(ServerLevel world, int startX, int startZ) {
        IslandDensityFunction activeDf = IslandDensityFunction.getActive();
        IslandTerrain terrain = activeDf != null ? activeDf.terrain() : null;
        if (terrain != null && terrain.signedDistance(startX, startZ) > 0.0) {
            return surfaceAt(world, startX, startZ);
        }

        if (terrain != null) {
            // The marker is meant to be land; if it is not, pick the deepest land
            // within a short radius rather than the first 1-2 block spit.
            double bestDepth = -1.0e9;
            int bestX = startX;
            int bestZ = startZ;
            for (int radius = 16; radius <= 512; radius += 16) {
                int samples = 32;
                for (int i = 0; i < samples; i++) {
                    double angle = Math.PI * 2.0 * i / samples;
                    int x = startX + (int) Math.round(Math.cos(angle) * radius);
                    int z = startZ + (int) Math.round(Math.sin(angle) * radius);
                    double depth = terrain.signedDistance(x, z);
                    if (depth > bestDepth) {
                        bestDepth = depth;
                        bestX = x;
                        bestZ = z;
                    }
                }
                if (bestDepth >= 48.0) break;
            }
            if (bestDepth > 0.0) {
                return surfaceAt(world, bestX, bestZ);
            }
        }

        return surfaceAt(world, startX, startZ);
    }

    private static BlockPos surfaceAt(ServerLevel world, int x, int z) {
        world.getChunk(x >> 4, z >> 4);
        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        return new BlockPos(x, surfaceY, z);
    }

    public static BlockPos findSafeSpawn(ServerLevel world, int startX, int startZ) {
        IslandDensityFunction activeDf = IslandDensityFunction.getActive();
        if (activeDf == null || activeDf.terrain() == null) {
            int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, startX, startZ);
            return new BlockPos(startX, surfaceY, startZ);
        }

        IslandTerrain terrain = activeDf.terrain();
        if (terrain.signedDistance(startX, startZ) > 0.0) {
            int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, startX, startZ);
            return new BlockPos(startX, surfaceY, startZ);
        }

        // Spiral search outward in steps of 16 blocks to find the nearest land column
        int bestX = startX;
        int bestZ = startZ;
        double maxDist = -1.0e9;
        boolean foundLand = false;

        for (int r = 16; r <= 3200; r += 16) {
            for (int dx = -r; dx <= r; dx += 16) {
                for (int dz : new int[]{-r, r}) {
                    double dist = terrain.signedDistance(startX + dx, startZ + dz);
                    if (dist > maxDist) {
                        maxDist = dist;
                        bestX = startX + dx;
                        bestZ = startZ + dz;
                        if (dist > 0.0) {
                            foundLand = true;
                            break;
                        }
                    }
                }
                if (foundLand) break;
            }
            if (foundLand) break;
        }

        int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, bestX, bestZ);
        return new BlockPos(bestX, surfaceY, bestZ);
    }
}
