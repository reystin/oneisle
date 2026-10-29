package com.reystin.oneisle;

import net.minecraft.resources.Identifier;

/** Shared identity and registry helpers for every OneIsle subsystem. */
public final class OneIsleIds {
    public static final String MOD_ID = "oneisle";
    public static final String DISPLAY_NAME = "OneIsle — All Biomes & Structures on One Island";
    public static final String AUTHOR = "reystin";
    public static final String WEBSITE = "https://github.com/reystin/oneisle";
    public static final String SOURCES = "https://github.com/reystin/oneisle";

    private OneIsleIds() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
