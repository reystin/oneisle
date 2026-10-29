package com.hack337.omniisle.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static guard: steady-state render paths do not allocate collections, records or formatted strings. */
public class Phase11UiHygieneTest {
    private static final Path CLIENT = Path.of("src/main/java/com/hack337/omniisle/client");
    private static final Path UI = CLIENT.resolve("ui");
    private static final Path EDITOR = CLIENT.resolve("OmniIsleEditorScreen.java");

    private static final String[] RENDER_NAMES = {
        "extractWidgetRenderState", "extractBackground", "extractRenderState",
        "drawChrome", "drawBrand", "drawCanvas", "drawGridOverlay", "drawSpawnMarker",
        "drawBrushCursor", "drawInspector", "drawRow", "drawStepperCell", "drawButton",
        "drawMinimalNotice", "render"
    };

    private static final String[] FORBIDDEN = {
        ".stream(", "String.format", "new ArrayList", "new HashMap", "new StringBuilder",
        "new Rect", "new Tip", "new Row", "new Omni", "Integer.toString", "Long.toHexString",
        "Component.literal", "Component.translatable", "new int[", "new float[", "new byte[",
        "new short[", "new String["
    };

    @Test
    @DisplayName("11H.1 render methods allocate no collections, records or formatted strings")
    void renderMethodsAreAllocationFree() throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.list(UI)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
        }
        sources.add(EDITOR);
        int methods = 0;
        for (Path source : sources) {
            String text = readUtf8(source);
            methods += scanRenderMethods(source.getFileName().toString(), text);
        }
        assertTrue(methods >= 20, "expected render methods to scan, found " + methods);
    }

    @Test
    @DisplayName("11H.2 labels, list shares and inspector strings are cached fields")
    void cachedValues() throws IOException {
        String editor = readUtf8(EDITOR);
        assertTrue(editor.contains("private String statusLine"), "status line must be cached");
        assertTrue(editor.contains("private String inspectName"), "inspector strings must be cached");
        assertTrue(editor.contains("private String hoverTipText"), "row tooltip must be cached");
        assertTrue(editor.contains("private static final BiomePreview.Type[] TYPES"),
            "biome enum values must be cached, not cloned per frame");
        assertFalse(regex("updateStatusLine\\([^)]*\\)\\s*\\{[^}]*String\\.format", editor),
            "status formatting must live outside the per-frame method");

        String metrics = readUtf8(UI.resolve("OmniMetrics.java"));
        assertTrue(metrics.contains("Map<String, Integer>"), "font widths must be cached");
        assertTrue(metrics.contains("Map<Integer, String>"), "fitted labels must be cached");
        assertTrue(metrics.contains("tick("), "metrics must expose a language epoch reset");

        String list = readUtf8(UI.resolve("OmniBiomeList.java"));
        assertTrue(list.contains("cachedWeightText") && list.contains("weightText()"),
            "row weights must be cached for steady-state rendering");
        assertTrue(list.contains("row.weightText()"),
            "row weight rendering must use the cached string");

        assertFalse(readUtf8(UI.resolve("OmniListView.java")).contains(".stream("),
            "list views must not stream per frame");
        assertFalse(readUtf8(UI.resolve("OmniBiomeList.java")).contains(".stream("),
            "biome lists must not stream per frame");
    }

    @Test
    @DisplayName("11H.3 all UI sources and language files are valid UTF-8")
    void utf8() throws IOException {
        try (Stream<Path> files = Files.list(UI)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                if (path.toString().endsWith(".java")) readUtf8(path);
            }
        }
        readUtf8(EDITOR);
        readUtf8(Path.of("src/main/resources/assets/omniisle/lang/en_us.json"));
        readUtf8(Path.of("src/main/resources/assets/omniisle/lang/ru_ru.json"));
    }

    private static int scanRenderMethods(String file, String text) {
        int found = 0;
        for (String name : RENDER_NAMES) {
            Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(text);
            while (matcher.find()) {
                int open = text.indexOf('{', matcher.end());
                int closeParen = text.indexOf(')', matcher.end());
                if (closeParen < 0) continue;
                int terminator = text.indexOf(';', closeParen);
                if (open < 0 || (terminator >= 0 && terminator < open)) continue;
                int depth = 0;
                int end = open;
                for (; end < text.length(); end++) {
                    char c = text.charAt(end);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) break;
                    }
                }
                String body = text.substring(open, Math.min(end + 1, text.length()));
                for (String forbidden : FORBIDDEN) {
                    assertFalse(body.contains(forbidden),
                        file + " " + name + " allocates per frame via '" + forbidden + "'");
                }
                found++;
            }
        }
        return found;
    }

    private static boolean regex(String pattern, String text) {
        return Pattern.compile(pattern, Pattern.DOTALL).matcher(text).find();
    }

    private static String readUtf8(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw new AssertionError("invalid UTF-8 in " + path, error);
        }
    }
}
