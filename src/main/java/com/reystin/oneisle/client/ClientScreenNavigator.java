package com.reystin.oneisle.client;

import java.lang.reflect.InvocationTargetException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Bridges the screen-owner move between Minecraft 26.1 and 26.2. */
public final class ClientScreenNavigator {
    private ClientScreenNavigator() {}

    public static void open(Minecraft client, Screen screen) {
        try {
            client.gui.getClass().getMethod("setScreen", Screen.class).invoke(client.gui, screen);
            return;
        } catch (NoSuchMethodException ignored) {
            // 26.1 still owns this method directly on Minecraft.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not open OneIsle screen", exception);
        }
        try {
            Minecraft.class.getMethod("setScreen", Screen.class).invoke(client, screen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Minecraft screen API", exception);
        }
    }
}
