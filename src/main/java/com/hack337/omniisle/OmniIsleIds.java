package com.hack337.omniisle;

import net.minecraft.resources.Identifier;

/** Shared identity and registry helpers for every OmniIsle subsystem. */
public final class OmniIsleIds {
    public static final String MOD_ID = "omniisle";
    public static final String DISPLAY_NAME = "OmniIsle — All Biomes & Structures on One Island";
    public static final String AUTHOR = "Hack337";
    public static final String WEBSITE = "https://hack337.com";
    public static final String SOURCES = "https://github.com/y9hack337";

    private OmniIsleIds() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
