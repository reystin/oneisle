package com.reystin.oneisle.client;

import com.reystin.oneisle.client.dev.UiScreenshotHarness;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Client entrypoint; the screenshot harness is inert unless a system property enables it. */
public final class OneIsleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String target = System.getProperty("oneisle.uiScreenshot");
        if (target == null || target.isBlank()) return;
        UiScreenshotHarness harness = new UiScreenshotHarness(Path.of(target));
        ClientTickEvents.END_CLIENT_TICK.register(harness::tick);
    }
}
