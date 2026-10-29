package com.reystin.oneisle.client.dev;

import com.reystin.oneisle.client.ClientScreenNavigator;
import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.OneIsleEditorScreen;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;

/**
 * Dev-only screenshot and render-cost harness. Enabled with
 * {@code -Doneisle.uiScreenshot=<dir>}; drives the editor without a world.
 */
public final class UiScreenshotHarness {
    private static final String[] ALL_LANGS = {"en_us", "ru_ru"};
    /*
     * macOS enforces a 320x240-point minimum window, so a 640x320 framebuffer
     * cannot be created; 640x480 is the smallest real capture.
     */
    private static final int[][] SIZES = {{640, 480}, {854, 480}, {1280, 720}, {1920, 1080}};
    private static final int[] SCALES = {1, 2, 3, 4};
    private static final String[] TABS = {"shape", "terrain", "climate", "biomes", "structures", "tools"};
    private static final String[] MODES = {"land", "height", "biomes", "shore"};

    private static final int BOOT_TICKS = 60;
    private static final int OPEN_WAIT = 12;
    private static final int RESIZE_WAIT = 8;
    private static final int TAB_WAIT = 4;
    private static final int COST_FRAMES = 300;
    private static final int COST_TIMEOUT = 600;

    private enum Kind { SWITCH_LANG, OPEN, RESIZE, TAB, SHOT, MODE, COST, CLOSE }

    private record Task(Kind kind, String arg, int a, int b, int c, int delay) {}

    private final Path root;
    private final String[] langs;
    private final List<Task> tasks = new ArrayList<>();
    private final StringBuilder manifest = new StringBuilder(
        "path,lang,tab,scale,requested,actual_logical,fps\n");
    private final StringBuilder cost = new StringBuilder(
        "tab,scale,actual_logical,samples,median_ms,p95_ms\n");

    private int taskIndex;
    private int waitTicks;
    private boolean started;
    private boolean booted;
    private int bootTicks;
    private boolean finished;
    private CompletableFuture<Void> reload;
    private boolean shotPending;
    private boolean shotDone;
    private String shotError;
    private boolean costStarted;
    private String currentLang = "";
    private Method switchTab;
    private Method setPreviewMode;

    public UiScreenshotHarness(Path root) {
        this.root = root.toAbsolutePath().normalize();
        boolean smoke = "1".equals(System.getProperty("oneisle.uiScreenshot.smoke"));
        String only = System.getProperty("oneisle.uiScreenshot.lang");
        this.langs = only == null || only.isBlank() ? ALL_LANGS : new String[]{only};
        if (smoke) {
            tasks.add(new Task(Kind.SWITCH_LANG, langs[0], 0, 0, 0, 0));
            tasks.add(new Task(Kind.OPEN, "", 0, 0, 0, OPEN_WAIT));
            tasks.add(new Task(Kind.RESIZE, "", 1280, 720, 1, RESIZE_WAIT));
            tasks.add(new Task(Kind.TAB, "", 0, 0, 0, TAB_WAIT));
            tasks.add(new Task(Kind.SHOT, langs[0] + "/1280x720_scale1/shape.png", 1280, 720, 1, 0));
            tasks.add(new Task(Kind.RESIZE, "", 640, 480, 1, RESIZE_WAIT));
            tasks.add(new Task(Kind.TAB, "", 0, 0, 0, TAB_WAIT));
            tasks.add(new Task(Kind.SHOT, langs[0] + "/640x480_scale1/shape.png", 640, 480, 1, 0));
            tasks.add(new Task(Kind.RESIZE, "", 1280, 720, 4, RESIZE_WAIT));
            tasks.add(new Task(Kind.TAB, "", 0, 0, 0, TAB_WAIT));
            tasks.add(new Task(Kind.SHOT, langs[0] + "/1280x720_scale4/shape.png", 1280, 720, 4, 0));
            tasks.add(new Task(Kind.RESIZE, "", 640, 480, 2, RESIZE_WAIT));
            tasks.add(new Task(Kind.TAB, "", 0, 0, 0, TAB_WAIT));
            tasks.add(new Task(Kind.SHOT, langs[0] + "/640x480_scale2/shape.png", 640, 480, 2, 0));
            tasks.add(new Task(Kind.CLOSE, "", 0, 0, 0, 2));
            return;
        }
        for (String lang : langs) {
            tasks.add(new Task(Kind.SWITCH_LANG, lang, 0, 0, 0, 0));
            tasks.add(new Task(Kind.OPEN, "", 0, 0, 0, OPEN_WAIT));
            for (int[] size : SIZES) {
                for (int scale : SCALES) {
                    tasks.add(new Task(Kind.RESIZE, "", size[0], size[1], scale, RESIZE_WAIT));
                    for (int tab = 0; tab < TABS.length; tab++) {
                        tasks.add(new Task(Kind.TAB, "", tab, 0, 0, TAB_WAIT));
                        String path = lang + "/" + size[0] + "x" + size[1] + "_scale" + scale
                            + "/" + TABS[tab] + ".png";
                        tasks.add(new Task(Kind.SHOT, path, size[0], size[1], scale, 0));
                    }
                }
            }
            tasks.add(new Task(Kind.RESIZE, "", 1280, 720, 1, RESIZE_WAIT));
            for (int mode = 0; mode < MODES.length; mode++) {
                tasks.add(new Task(Kind.MODE, "", mode, 0, 0, TAB_WAIT));
                String path = lang + "/1280x720_scale1/preview_" + MODES[mode] + ".png";
                tasks.add(new Task(Kind.SHOT, path, 1280, 720, 1, 0));
            }
            tasks.add(new Task(Kind.RESIZE, "", 1920, 1080, 2, RESIZE_WAIT));
            for (int tab = 0; tab < TABS.length; tab++) {
                tasks.add(new Task(Kind.COST, TABS[tab], tab, 2, 0, TAB_WAIT));
            }
            tasks.add(new Task(Kind.CLOSE, "", 0, 0, 0, 2));
        }
    }

    public void tick(Minecraft minecraft) {
        if (finished || minecraft == null) return;
        try {
            if (!booted) {
                if (!canStart(minecraft)) {
                    if (++bootTicks > 1200) fail("client did not reach a screen within 60 s");
                    return;
                }
                if (++bootTicks < BOOT_TICKS) return;
                booted = true;
                Files.createDirectories(root);
            }
            if (taskIndex >= tasks.size()) {
                finish(minecraft);
                return;
            }
            Task task = tasks.get(taskIndex);
            if (!started) {
                startTask(minecraft, task);
                return;
            }
            waitTicks++;
            switch (task.kind()) {
                case SWITCH_LANG -> {
                    if (reload != null && reload.isCompletedExceptionally()) {
                        fail("language reload failed for " + task.arg());
                        return;
                    }
                    if (reload != null && reload.isDone() && waitTicks >= 5
                        && minecraft.gui.overlay() == null) advance();
                    else if (waitTicks > 600) fail("language reload timed out for " + task.arg());
                }
                case SHOT -> {
                    if (shotDone) {
                        recordShot(minecraft, task);
                        advance();
                    } else if (!shotPending) {
                        fail("screenshot failed for " + task.arg() + ": " + shotError);
                    } else if (waitTicks > 200) {
                        fail("screenshot timed out for " + task.arg());
                    }
                }
                case COST -> {
                    if (!costStarted) {
                        if (waitTicks >= task.delay()) {
                            OneIsleEditorScreen.beginPerfCapture();
                            costStarted = true;
                            waitTicks = 0;
                        }
                    } else if (OneIsleEditorScreen.perfSampleCount() >= COST_FRAMES
                        || waitTicks > COST_TIMEOUT) {
                        long[] samples = OneIsleEditorScreen.endPerfCapture();
                        recordCost(minecraft, task, samples);
                        advance();
                    }
                }
                default -> {
                    if (waitTicks >= task.delay()) advance();
                }
            }
        } catch (Throwable error) {
            fail(stack(error));
        }
    }

    private static boolean canStart(Minecraft minecraft) {
        return minecraft.getWindow() != null
            && minecraft.getWindow().getWidth() > 0
            && minecraft.gui.overlay() == null
            && minecraft.gui.screen() != null;
    }

    private void startTask(Minecraft minecraft, Task task) throws IOException {
        started = true;
        waitTicks = 0;
        switch (task.kind()) {
            case SWITCH_LANG -> {
                currentLang = task.arg();
                minecraft.getLanguageManager().setSelected(currentLang);
                reload = minecraft.reloadResourcePacks();
            }
            case OPEN -> {
                Screen editor = new OneIsleEditorScreen(null, new IslandDraft());
                resolveMethods();
                ClientScreenNavigator.open(minecraft, editor);
                warpMouse(minecraft);
            }
            case RESIZE -> {
                int winWidth = Math.max(1, Math.round(task.a() / displayScale(minecraft)));
                int winHeight = Math.max(1, Math.round(task.b() / displayScale(minecraft)));
                minecraft.getWindow().setWindowed(winWidth, winHeight);
                minecraft.options.guiScale().set(task.c());
                minecraft.resizeGui();
                warpMouse(minecraft);
            }
            case TAB -> {
                invoke(switchTab, task.a());
                warpMouse(minecraft);
            }
            case MODE -> invoke(setPreviewMode, task.a());
            case SHOT -> {
                Path file = root.resolve(task.arg());
                Files.createDirectories(file.getParent());
                requestShot(minecraft, file);
            }
            case COST -> {
                invoke(switchTab, task.a());
                warpMouse(minecraft);
                costStarted = false;
            }
            case CLOSE -> ClientScreenNavigator.open(minecraft, new TitleScreen(false));
        }
    }

    private void advance() {
        taskIndex++;
        started = false;
        waitTicks = 0;
    }

    private void requestShot(Minecraft minecraft, Path file) {
        shotDone = false;
        shotPending = true;
        shotError = null;
        try {
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), 1, image -> {
                try (NativeImage captured = image) {
                    captured.writeToFile(file);
                    shotDone = true;
                } catch (IOException | RuntimeException error) {
                    shotError = error.toString();
                } finally {
                    shotPending = false;
                }
            });
        } catch (RuntimeException error) {
            shotPending = false;
            shotError = error.toString();
        }
    }

    private void recordShot(Minecraft minecraft, Task task) {
        Path file = root.resolve(task.arg());
        String name = file.getFileName().toString();
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        int[] logical = logicalSize(minecraft);
        manifest.append(task.arg()).append(',')
            .append(currentLang).append(',')
            .append(name).append(',')
            .append(task.c()).append(',')
            .append(task.a()).append('x').append(task.b()).append(',')
            .append(logical[0]).append('x').append(logical[1]).append(',')
            .append(minecraft.getFps()).append('\n');
    }

    private void recordCost(Minecraft minecraft, Task task, long[] raw) {
        int[] logical = logicalSize(minecraft);
        if (raw.length == 0) {
            cost.append(task.arg()).append(',').append(task.b()).append(',')
                .append(logical[0]).append('x').append(logical[1]).append(",0,n/a,n/a\n");
            return;
        }
        long[] samples = raw.clone();
        Arrays.sort(samples);
        double median = samples[samples.length / 2] / 1_000_000.0;
        int index = (int) Math.ceil(samples.length * 0.95) - 1;
        double p95 = samples[Math.max(0, Math.min(samples.length - 1, index))] / 1_000_000.0;
        cost.append(task.arg()).append(',').append(task.b()).append(',')
            .append(logical[0]).append('x').append(logical[1]).append(',')
            .append(samples.length).append(',')
            .append(String.format(Locale.ROOT, "%.3f", median)).append(',')
            .append(String.format(Locale.ROOT, "%.3f", p95)).append('\n');
    }

    private static int[] logicalSize(Minecraft minecraft) {
        return new int[]{
            minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight()
        };
    }

    private void resolveMethods() {
        if (switchTab != null) return;
        try {
            switchTab = OneIsleEditorScreen.class.getDeclaredMethod("switchTab", int.class);
            switchTab.setAccessible(true);
            setPreviewMode = OneIsleEditorScreen.class.getDeclaredMethod("setPreviewMode", int.class);
            setPreviewMode.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("editor capture hooks are unavailable", error);
        }
    }

    private void invoke(Method method, int value) {
        if (switchTab == null) resolveMethods();
        if (method == null) throw new IllegalStateException("capture hook is missing");
        try {
            method.invoke(minecraftScreen(), value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("capture hook failed: " + method.getName(), error);
        }
    }

    private Screen minecraftScreen() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) throw new IllegalStateException("editor is not open");
        return screen;
    }

    /** Window sizes are requested in framebuffer pixels; SDL reports points on Retina. */
    private static float displayScale(Minecraft minecraft) {
        try {
            float scale = SDLVideo.SDL_GetWindowDisplayScale(minecraft.getWindow().handle());
            return scale >= 1.0F && scale <= 4.0F ? scale : 1.0F;
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    private static void warpMouse(Minecraft minecraft) {
        try {
            SDLMouse.SDL_WarpMouseInWindow(minecraft.getWindow().handle(), 2.0F, 2.0F);
        } catch (Throwable ignored) {
            // Screenshots still render; only the inspected pixel may differ.
        }
    }

    private void finish(Minecraft minecraft) {
        if (finished) return;
        finished = true;
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
            Files.writeString(root.resolve("render_cost.csv"), cost.toString(), StandardCharsets.UTF_8);
            Files.writeString(root.resolve("harness_done.txt"), "ok\n", StandardCharsets.UTF_8);
        } catch (IOException error) {
            writeError(root, stack(error));
        }
        minecraft.execute(minecraft::stop);
    }

    private void fail(String message) {
        if (finished) return;
        finished = true;
        writeError(root, message);
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // The error log already carries the failure.
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.execute(minecraft::stop);
    }

    private static void writeError(Path root, String message) {
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve("harness_error.txt"),
                message + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Nothing else can be reported.
        }
    }

    private static String stack(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
