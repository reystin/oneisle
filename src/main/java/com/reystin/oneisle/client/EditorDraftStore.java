package com.reystin.oneisle.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

public final class EditorDraftStore {
    private static final Map<CreateWorldScreen, IslandDraft> SESSIONS = new WeakHashMap<>();
    private EditorDraftStore() {}

    public static synchronized IslandDraft openDraft(CreateWorldScreen screen) {
        return SESSIONS.computeIfAbsent(screen, ignored -> new IslandDraft()).copy();
    }

    public static synchronized void saveDraft(CreateWorldScreen screen, IslandDraft state) {
        SESSIONS.put(screen, state.copy());
    }
}
