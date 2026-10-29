package com.reystin.oneisle.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reystin.oneisle.client.ui.OneText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Worst-case text fitting: every label either fits or is ellipsised without exceeding its box. */
public class Phase11UiTextTest {
    private static final OneText.Metrics WORST = text -> {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += cp < 0x0400 ? 8 : 9;
            i += Character.charCount(cp);
        }
        return width;
    };

    @Test
    @DisplayName("11T.1 short labels pass through unchanged")
    void shortLabels() {
        assertEquals("Shape", OneText.fit(WORST, "Shape", 200));
        assertEquals("Форма", OneText.fit(WORST, "Форма", 200));
        assertTrue(OneText.fits(WORST, "192×192", 60));
        assertFalse(OneText.fits(WORST, "Форма острова", 80));
    }

    @Test
    @DisplayName("11T.2 long labels ellipsise inside the width for both alphabets")
    void ellipsis() {
        String[] labels = {
            "Resolution: 192×192",
            "Разрешение: 384×384",
            "Island relief: mountainous",
            "Рельеф острова: гористый",
            "Show biome percentages in the list",
            "Показывать проценты биомов в списке"
        };
        for (String label : labels) {
            for (int max = 24; max <= 220; max += 4) {
                String fitted = OneText.fit(WORST, label, max);
                assertTrue(WORST.width(fitted) <= max,
                    label + " at " + max + " produced " + fitted + " (" + WORST.width(fitted) + ")");
                if (WORST.width(label) > max) {
                    assertTrue(fitted.endsWith(OneText.ELLIPSIS), "expected ellipsis for " + label + " at " + max);
                } else {
                    assertEquals(label, fitted);
                }
            }
        }
    }

    @Test
    @DisplayName("11T.3 surrogate pairs are never split")
    void surrogates() {
        String label = "abc\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00";
        for (int max = 8; max <= 80; max += 4) {
            String fitted = OneText.fit(WORST, label, max);
            for (int i = 0; i < fitted.length(); i++) {
                char c = fitted.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    assertTrue(i + 1 < fitted.length() && Character.isLowSurrogate(fitted.charAt(i + 1)));
                    i++;
                } else {
                    assertFalse(Character.isLowSurrogate(c), "unpaired low surrogate");
                }
            }
            assertTrue(WORST.width(fitted) <= max);
        }
    }

    @Test
    @DisplayName("11T.4 zero or negative width yields an empty string")
    void emptyCases() {
        assertEquals("", OneText.fit(WORST, "Shape", 0));
        assertEquals("", OneText.fit(WORST, "Shape", -10));
        assertEquals("", OneText.fit(WORST, "", 50));
    }

    @Test
    @DisplayName("11T.5 en_us and ru_ru expose the same non-empty keys and placeholders")
    void keyParity() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> ru = lang("ru_ru");
        assertEquals(new TreeSet<>(en.keySet()), new TreeSet<>(ru.keySet()), "key sets differ");
        for (String key : en.keySet()) {
            assertFalse(en.get(key).isBlank(), "empty en_us value for " + key);
            assertFalse(ru.get(key).isBlank(), "empty ru_ru value for " + key);
            assertEquals(placeholders(en.get(key)), placeholders(ru.get(key)),
                "placeholder mismatch for " + key);
        }
    }

    @Test
    @DisplayName("11T.6 every literal oneisle key in main sources exists in both languages")
    void referencedKeysExist() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> ru = lang("ru_ru");
        Set<String> referenced = new TreeSet<>();
        Pattern pattern = Pattern.compile("\"(oneisle\\.[a-z0-9_.]+)\"");
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path path : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher matcher = pattern.matcher(Files.readString(path, StandardCharsets.UTF_8));
                while (matcher.find()) referenced.add(matcher.group(1));
            }
        }
        assertTrue(referenced.size() > 200, "expected the UI key inventory, found " + referenced.size());
        for (String key : referenced) {
            if (key.endsWith(".")) {
                assertTrue(en.keySet().stream().anyMatch(k -> k.startsWith(key)),
                    "no en_us keys under prefix " + key);
                assertTrue(ru.keySet().stream().anyMatch(k -> k.startsWith(key)),
                    "no ru_ru keys under prefix " + key);
                continue;
            }
            if (key.equals("oneisle.json")) continue;
            assertTrue(en.containsKey(key), "en_us is missing " + key);
            assertTrue(ru.containsKey(key), "ru_ru is missing " + key);
        }
    }

    @Test
    @DisplayName("11T.7 every language line fits or ellipsises within the worst-case width")
    void corpusFits() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Map<String, String> lang : List.of(lang("en_us"), lang("ru_ru"))) {
            for (Map.Entry<String, String> entry : lang.entrySet()) {
                for (String line : entry.getValue().split("\\R", -1)) {
                    if (line.isEmpty()) continue;
                    int natural = WORST.width(line);
                    for (int max = 16; max <= 320; max += 8) {
                        String fitted = OneText.fit(WORST, line, max);
                        if (WORST.width(fitted) > max) {
                            failures.add(entry.getKey() + " at " + max + " -> " + fitted);
                        } else if (natural <= max && !fitted.equals(line)) {
                            failures.add(entry.getKey() + " altered although it fits at " + max);
                        } else if (natural > max && !fitted.endsWith(OneText.ELLIPSIS)) {
                            failures.add(entry.getKey() + " not ellipsised at " + max);
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.subList(0, Math.min(10, failures.size())).toString());
    }

    private static final Path LANG = Path.of("src/main/resources/assets/oneisle/lang");
    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sdf%]");

    private static Map<String, String> lang(String id) throws IOException {
        String json = Files.readString(LANG.resolve(id + ".json"), StandardCharsets.UTF_8);
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        TreeMap<String, String> values = new TreeMap<>();
        for (var entry : object.entrySet()) values.put(entry.getKey(), entry.getValue().getAsString());
        return values;
    }

    private static List<String> placeholders(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) tokens.add(matcher.group());
        tokens.sort(null);
        return tokens;
    }
}
