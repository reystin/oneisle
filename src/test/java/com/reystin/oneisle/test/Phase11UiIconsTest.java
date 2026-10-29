package com.reystin.oneisle.test;

import com.reystin.oneisle.client.ui.OneIcons;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generated icon atlas dimensions, non-empty cells and the white-only tint palette. */
public class Phase11UiIconsTest {
    @Test
    @DisplayName("11I.1 atlas is 128x48 with an 8x3 grid of unique cells")
    void dimensions() {
        assertEquals(16, OneIcons.CELL);
        assertEquals(8, OneIcons.COLUMNS);
        assertEquals(3, OneIcons.ROWS);
        assertEquals(20, OneIcons.values().length);
        Set<Integer> cells = new HashSet<>();
        for (OneIcons icon : OneIcons.values()) {
            int cell = icon.row * OneIcons.COLUMNS + icon.column;
            assertTrue(icon.column >= 0 && icon.column < OneIcons.COLUMNS);
            assertTrue(icon.row >= 0 && icon.row < OneIcons.ROWS);
            assertTrue(cells.add(cell), "duplicate atlas cell " + cell);
            assertEquals(icon.column * 16, icon.u());
            assertEquals(icon.row * 16, icon.v());
        }
        assertEquals(20, cells.size());
    }

    @Test
    @DisplayName("11I.2 every cell has pixels and only the transparent/white palette")
    void atlasPixels() throws IOException {
        Path path = Path.of("src/main/resources/assets/oneisle/textures/gui/icons.png");
        assertTrue(Files.exists(path), "icons.png missing");
        BufferedImage image;
        try (InputStream in = Files.newInputStream(path)) {
            image = ImageIO.read(in);
        }
        assertNotNull(image);
        assertEquals(128, image.getWidth());
        assertEquals(48, image.getHeight());
        for (OneIcons icon : OneIcons.values()) {
            int painted = 0;
            for (int y = icon.v(); y < icon.v() + 16; y++) {
                for (int x = icon.u(); x < icon.u() + 16; x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    int rgb = argb & 0x00FFFFFF;
                    assertTrue(alpha == 0 || rgb == 0x00FFFFFF,
                        "cell " + icon + " has colour " + Integer.toHexString(argb));
                    if (alpha > 0) painted++;
                }
            }
            assertTrue(painted >= 8, "cell " + icon + " painted only " + painted + " pixels");
        }
    }

    @Test
    @DisplayName("11I.3 texture identifier is namespaced under oneisle")
    void identifier() {
        assertEquals("oneisle", OneIcons.TEXTURE.getNamespace());
        assertEquals("textures/gui/icons.png", OneIcons.TEXTURE.getPath());
    }
}
