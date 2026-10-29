package com.reystin.oneisle.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SpawnSavedData extends SavedData {
    public static final SavedDataType<SpawnSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("oneisle", "spawn"),
        () -> new SpawnSavedData(false),
        RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("initialized").forGetter(data -> data.initialized)
            ).apply(instance, SpawnSavedData::new)
        ),
        DataFixTypes.SAVED_DATA_WORLD_GEN_SETTINGS
    );

    public boolean initialized;

    public SpawnSavedData() {
        this(false);
    }

    public SpawnSavedData(boolean initialized) {
        this.initialized = initialized;
    }
}
