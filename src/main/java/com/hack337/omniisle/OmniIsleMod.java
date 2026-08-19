package com.hack337.omniisle;

import com.hack337.omniisle.worldgen.IslandDensityFunction;
import com.hack337.omniisle.worldgen.OmniBiomeSource;
import com.hack337.omniisle.worldgen.SingleStructurePlacement;
import com.hack337.omniisle.worldgen.RiverLife;
import com.hack337.omniisle.command.OmniCommands;
import net.fabricmc.api.ModInitializer;

public class OmniIsleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        IslandDensityFunction.registerCodec();
        OmniBiomeSource.registerCodec();
        SingleStructurePlacement.registerType();
        RiverLife.installSpawns();
        OmniCommands.registerCommands();
    }
}
