package com.hack337.omniisle;

import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.hack337.omniisle.worldgen.SingleStructurePlacement;
import com.hack337.omniisle.worldgen.RiverLife;
import com.hack337.omniisle.command.OmniCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

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
    }
}
