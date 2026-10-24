package com.hack337.omniisle;

import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.hack337.omniisle.worldgen.SingleStructurePlacement;
import com.hack337.omniisle.worldgen.RiverLife;
import com.hack337.omniisle.worldgen.SpawnSavedData;
import com.hack337.omniisle.command.OmniCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

public class OmniIsleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        IslandDensityFunction.registerCodec();
        OmniBiomeSource.registerCodec();
        SingleStructurePlacement.registerType();
        RiverLife.installSpawns();
        OmniCommands.registerCommands();

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

            SpawnSavedData spawnData = world.getDataStorage().computeIfAbsent(SpawnSavedData.TYPE);
            if (spawnData.initialized) return;

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

            BlockPos safePos = findSafeSpawn(world, targetX, targetZ);
            world.setRespawnData(LevelData.RespawnData.of(world.dimension(), safePos, 0.0f, 0.0f));
            spawnData.initialized = true;
            spawnData.setDirty();
        });
    }

    public static BlockPos findSafeSpawn(ServerLevel world, int startX, int startZ) {
        int seaLevel = world.getSeaLevel();
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, startX, startZ);
        BlockPos pos = new BlockPos(startX, y, startZ);
        if (y > seaLevel && world.getFluidState(pos.below()).isEmpty()) {
            return pos;
        }

        int step = 4;
        int maxR = 256;
        for (int r = step; r <= maxR; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int x = startX + dx;
                    int z = startZ + dz;
                    int curY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos curPos = new BlockPos(x, curY, z);
                    if (curY > seaLevel && world.getFluidState(curPos.below()).isEmpty()) {
                        return curPos;
                    }
                }
            }
        }
        return new BlockPos(startX, Math.max(y, seaLevel + 1), startZ);
    }
}
