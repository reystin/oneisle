package com.reystin.oneisle.mixin.client;

import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldBridge {
    @Invoker("getDataPackSelectionSettings")
    Pair<Path, PackRepository> oneisle$getScannedPack(WorldDataConfiguration configuration);

    @Invoker("tryApplyNewDataPacks")
    void oneisle$applyPack(PackRepository manager, boolean dataPackScreen,
                            Consumer<WorldDataConfiguration> onAbort);
}
