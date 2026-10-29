package com.hack337.omniisle.test;

import com.hack337.omniisle.client.EditorLayout;
import com.hack337.omniisle.client.EditorLayout.Layout;
import com.hack337.omniisle.client.EditorLayout.Rect;
import com.hack337.omniisle.client.ImageImportExport;
import com.hack337.omniisle.client.IslandDraft;
import com.hack337.omniisle.client.PreviewViewport;
import com.hack337.omniisle.continent.IslandMask;
import com.hack337.omniisle.continent.IslandShapeConfig;
import com.hack337.omniisle.continent.IslandTerrain;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase9EditorTest {

    @Test
    @DisplayName("9.6 Viewport: cursor-anchored zoom, clamp 1-8x, pan bounds, reset")
    void viewportZoomPanTest() {
        PreviewViewport vp = new PreviewViewport();
        assertEquals(1.0, vp.zoom(), 1e-9);
        assertEquals(0.5, vp.centerX(), 1e-9);
        assertEquals(1.0, vp.span(), 1e-9);

        double fu = 0.75;
        double fv = 0.25;
        double beforeU = vp.sourceU(fu);
        double beforeV = vp.sourceV(fv);
        vp.zoomAt(fu, fv, 4.0);
        assertEquals(4.0, vp.zoom(), 1e-9);
        assertEquals(beforeU, vp.sourceU(fu), 1e-9, "Cursor source U must stay anchored");
        assertEquals(beforeV, vp.sourceV(fv), 1e-9, "Cursor source V must stay anchored");
        assertEquals(0.25, vp.span(), 1e-9);
        assertEquals(0.5 + (0.75 - vp.centerX()) * 4.0, vp.screenFractionU(0.75), 1e-9);

        vp.zoomAt(0.5, 0.5, 1000.0);
        assertEquals(8.0, vp.zoom(), 1e-9, "Zoom clamps at 8x");
        vp.zoomAt(0.5, 0.5, 1e-6);
        assertEquals(1.0, vp.zoom(), 1e-9, "Zoom clamps at 1x");
        assertEquals(0.5, vp.centerX(), 1e-9);

        vp.zoomAt(0.5, 0.5, 4.0);
        vp.panByScreenFraction(100.0, 100.0);
        assertEquals(0.5 / 4.0, vp.centerX(), 1e-9, "Pan clamps left/top at half span");
        assertEquals(0.5 / 4.0, vp.centerZ(), 1e-9);
        vp.panByScreenFraction(-100.0, -100.0);
        assertEquals(1.0 - 0.5 / 4.0, vp.centerX(), 1e-9, "Pan clamps right/bottom at 1 - half span");
        assertEquals(1.0 - 0.5 / 4.0, vp.centerZ(), 1e-9);
        assertTrue(vp.u0() >= 0.0 && vp.u0() + vp.span() <= 1.0 + 1e-9);
        assertTrue(vp.v0() >= 0.0 && vp.v0() + vp.span() <= 1.0 + 1e-9);

        vp.reset();
        assertEquals(1.0, vp.zoom(), 1e-9);
        assertEquals(0.5, vp.centerX(), 1e-9);
        assertEquals(0.5, vp.centerZ(), 1e-9);
    }

    @Test
    @DisplayName("9.6 Radial symmetry: 6-way and 8-way painted sets are closed under their transforms")
    void radialSymmetryTest() {
        int res = 192;

        IslandDraft six = new IslandDraft(res);
        six.clearLand();
        six.paint(60, 40, 3, true, IslandDraft.Symmetry.SIX_WAY);
        six.drawLine(30, 90, 130, 90, 2, true, IslandDraft.Symmetry.SIX_WAY);
        six.drawEllipse(70, 120, 120, 160, false, 1, true, IslandDraft.Symmetry.SIX_WAY);
        int paintedSix = countLand(six);
        assertTrue(paintedSix > 1, "6-way symmetry must paint more than one pixel");
        forEachLand(six, (x, z) -> {
            double cx = (res - 1) / 2.0;
            double cz = (res - 1) / 2.0;
            double dx = x - cx;
            double dz = z - cz;
            for (int k = 1; k < 6; k++) {
                double angle = k * (Math.PI / 3.0);
                int tx = (int) Math.round(cx + dx * Math.cos(angle) - dz * Math.sin(angle));
                int tz = (int) Math.round(cz + dx * Math.sin(angle) + dz * Math.cos(angle));
                if (tx >= 0 && tx < res && tz >= 0 && tz < res) {
                    assertTrue(six.isLand(tx, tz),
                        "6-way orbit not closed: (" + x + "," + z + ") -> (" + tx + "," + tz + ")");
                }
            }
        });

        IslandDraft eight = new IslandDraft(res);
        eight.clearLand();
        eight.paint(55, 35, 3, true, IslandDraft.Symmetry.EIGHT_WAY);
        eight.drawRect(40, 100, 110, 150, true, 1, true, IslandDraft.Symmetry.EIGHT_WAY);
        assertTrue(countLand(eight) > 1, "8-way symmetry must paint more than one pixel");
        forEachLand(eight, (x, z) -> {
            double cx = (res - 1) / 2.0;
            double cz = (res - 1) / 2.0;
            int d1x = (int) Math.round(cx + (z - cz));
            int d1z = (int) Math.round(cz + (x - cx));
            int[][] targets = {
                {res - 1 - x, z},
                {x, res - 1 - z},
                {res - 1 - x, res - 1 - z},
                {d1x, d1z},
                {res - 1 - d1x, d1z},
                {d1x, res - 1 - d1z},
                {res - 1 - d1x, res - 1 - d1z}
            };
            for (int[] t : targets) {
                if (t[0] >= 0 && t[0] < res && t[1] >= 0 && t[1] < res) {
                    assertTrue(eight.isLand(t[0], t[1]),
                        "8-way orbit not closed: (" + x + "," + z + ") -> (" + t[0] + "," + t[1] + ")");
                }
            }
        });
    }

    @Test
    @DisplayName("9.6 Tools: fill, line, rect, ellipse on both layers with undo/redo")
    void toolsUndoRedoTest() {
        IslandDraft draft = new IslandDraft(192);
        draft.clearLand();
        draft.clearHeight();

        // Flood fill from water fills the connected background, undo/redo restores.
        draft.pushUndo();
        draft.floodFillLand(10, 10, true, IslandDraft.Symmetry.OFF);
        assertTrue(draft.isLand(10, 10));
        assertTrue(draft.isLand(191, 191));
        assertTrue(draft.undo());
        assertFalse(draft.isLand(10, 10));
        assertFalse(draft.isLand(191, 191));
        assertTrue(draft.redo());
        assertTrue(draft.isLand(10, 10));

        draft.clearLand();

        // Line tool (radius 2).
        draft.pushUndo();
        draft.drawLine(20, 20, 100, 20, 2, true, IslandDraft.Symmetry.OFF);
        assertTrue(draft.isLand(20, 20));
        assertTrue(draft.isLand(60, 20));
        assertTrue(draft.isLand(100, 20));
        assertFalse(draft.isLand(60, 60));
        assertTrue(draft.undo());
        assertFalse(draft.isLand(60, 20));

        // Rectangle outline vs filled.
        draft.pushUndo();
        draft.drawRect(30, 30, 80, 70, false, 1, true, IslandDraft.Symmetry.OFF);
        assertTrue(draft.isLand(30, 50));
        assertTrue(draft.isLand(80, 50));
        assertFalse(draft.isLand(55, 50), "Outline rectangle must not fill the center");
        draft.pushUndo();
        draft.drawRect(30, 30, 80, 70, true, 1, true, IslandDraft.Symmetry.OFF);
        assertTrue(draft.isLand(55, 50), "Filled rectangle must fill the center");
        assertTrue(draft.undo());
        assertFalse(draft.isLand(55, 50));

        // Ellipse outline vs filled.
        draft.clearLand();
        draft.pushUndo();
        draft.drawEllipse(40, 40, 120, 90, false, 1, true, IslandDraft.Symmetry.OFF);
        assertTrue(draft.isLand(40, 65), "Ellipse west edge must be painted");
        assertTrue(draft.isLand(120, 65), "Ellipse east edge must be painted");
        assertFalse(draft.isLand(80, 65), "Outline ellipse must not fill the center");
        draft.pushUndo();
        draft.drawEllipse(40, 40, 120, 90, true, 1, true, IslandDraft.Symmetry.OFF);
        assertTrue(draft.isLand(80, 65));
        assertTrue(draft.undo());
        assertFalse(draft.isLand(80, 65));

        // Height layer: line, rect, ellipse and undo restore the cleared tier 1.
        draft.clearHeight();
        draft.pushUndo();
        draft.drawLineHeight(10, 10, 100, 10, 2, 3, IslandDraft.Symmetry.OFF);
        assertEquals(3, draft.tier(50, 10));
        assertTrue(draft.undo());
        assertEquals(1, draft.tier(50, 10));

        draft.pushUndo();
        draft.drawRectHeight(20, 20, 80, 80, false, 1, 2, IslandDraft.Symmetry.OFF);
        assertEquals(2, draft.tier(20, 50));
        assertEquals(1, draft.tier(50, 50));
        draft.pushUndo();
        draft.drawRectHeight(20, 20, 80, 80, true, 1, 2, IslandDraft.Symmetry.OFF);
        assertEquals(2, draft.tier(50, 50));
        assertTrue(draft.undo());
        assertEquals(1, draft.tier(50, 50));

        draft.pushUndo();
        draft.drawEllipseHeight(30, 30, 90, 90, true, 1, 0, IslandDraft.Symmetry.OFF);
        assertEquals(0, draft.tier(60, 60));
        assertTrue(draft.undo());
        assertEquals(1, draft.tier(60, 60));

        // One snapshot restores both layers and spawn.
        draft.clearLand();
        draft.clearHeight();
        draft.clearSpawn();
        draft.pushUndo();
        draft.paint(96, 96, 10, true, IslandDraft.Symmetry.OFF);
        draft.paintHeight(96, 96, 10, 3, IslandDraft.Symmetry.OFF);
        assertTrue(draft.undo());
        assertFalse(draft.isLand(96, 96));
        assertEquals(1, draft.tier(96, 96));
        assertTrue(draft.redo());
        assertTrue(draft.isLand(96, 96));
        assertEquals(3, draft.tier(96, 96));

        // Spawn tool: set/clear are undoable.
        draft.setSpawn(96, 96);
        assertEquals(Integer.valueOf(96), draft.explicitSpawnPx());
        assertTrue(draft.undo());
        assertNull(draft.explicitSpawnPx());
        assertTrue(draft.redo());
        assertEquals(Integer.valueOf(96), draft.explicitSpawnPx());
        draft.clearSpawn();
        assertNull(draft.explicitSpawnPx());
        assertTrue(draft.undo());
        assertEquals(Integer.valueOf(96), draft.explicitSpawnPx());

        // Resolution changes are undoable and restore the previous grid.
        draft.setResolution(256);
        assertEquals(256, draft.resolution());
        assertTrue(draft.undo());
        assertEquals(192, draft.resolution());
    }

    @Test
    @DisplayName("9.6 Tools honor FOUR_WAY symmetry on land and height layers")
    void toolsFourWaySymmetryTest() {
        int res = 192;
        IslandDraft draft = new IslandDraft(res);
        draft.clearLand();
        draft.clearHeight();

        draft.drawLine(30, 40, 60, 40, 2, true, IslandDraft.Symmetry.FOUR_WAY);
        assertTrue(draft.isLand(45, 40));
        assertTrue(draft.isLand(res - 1 - 45, 40));
        assertTrue(draft.isLand(45, res - 1 - 40));
        assertTrue(draft.isLand(res - 1 - 45, res - 1 - 40));

        draft.drawRect(50, 60, 80, 90, false, 1, true, IslandDraft.Symmetry.FOUR_WAY);
        assertTrue(draft.isLand(50, 75));
        assertTrue(draft.isLand(res - 1 - 50, 75));
        assertTrue(draft.isLand(50, res - 1 - 75));
        assertTrue(draft.isLand(res - 1 - 50, res - 1 - 75));

        draft.drawEllipseHeight(40, 40, 70, 70, true, 1, 3, IslandDraft.Symmetry.FOUR_WAY);
        assertEquals(3, draft.tier(55, 55));
        assertEquals(3, draft.tier(res - 1 - 55, 55));
        assertEquals(3, draft.tier(55, res - 1 - 55));
        assertEquals(3, draft.tier(res - 1 - 55, res - 1 - 55));
    }

    @Test
    @DisplayName("9.6 Seed UX: reroll/set shape and biome seeds")
    void seedUxTest() {
        IslandDraft draft = new IslandDraft(192);
        long shapeSeed = draft.shapeSeed();
        String mask = draft.packedMask();
        draft.rerollShape();
        assertNotEquals(shapeSeed, draft.shapeSeed());
        assertNotEquals(mask, draft.packedMask());
        draft.setShapeSeed(77L);
        assertEquals(77L, draft.shapeSeed());

        long biomeSeed = draft.biomeSeed();
        draft.rerollBiomes();
        assertNotEquals(biomeSeed, draft.biomeSeed());
        draft.setBiomeSeed(99L);
        assertEquals(99L, draft.biomeSeed());
    }

    @Test
    @DisplayName("9.6 Heightmap import: threshold decides land, land quantiles decide tiers")
    void heightmapQuantileImportTest() {
        int res = 192;
        BufferedImage image = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                int value = ((x >= res / 2) ? 85 : 0) + ((z >= res / 2) ? 170 : 0);
                image.setRGB(x, z, (value << 16) | (value << 8) | value);
            }
        }
        ImageImportExport.ImportedDraftData data = ImageImportExport.importHeightmap(image, res, 0, false);
        for (int i = 0; i < res * res; i++) {
            assertTrue(data.land()[i], "threshold 0 marks every pixel as land");
        }
        assertEquals(0, data.height()[24 * res + 24]);
        assertEquals(1, data.height()[24 * res + 168]);
        assertEquals(2, data.height()[168 * res + 24]);
        assertEquals(3, data.height()[168 * res + 168]);

        BufferedImage uniform = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                uniform.setRGB(x, z, 0x787878);
            }
        }
        ImageImportExport.ImportedDraftData flat = ImageImportExport.importHeightmap(uniform, res, 0, false);
        for (int i = 0; i < res * res; i++) {
            assertEquals(1, flat.height()[i], "A flat heightmap imports as tier 1");
        }

        BufferedImage twoTone = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                twoTone.setRGB(x, z, x < res / 2 ? 0x0A0A0A : 0xC8C8C8);
            }
        }
        ImageImportExport.ImportedDraftData inverted = ImageImportExport.importHeightmap(twoTone, res, 100, true);
        assertTrue(inverted.land()[100 * res + 24], "Inverted threshold keeps dark pixels");
        assertFalse(inverted.land()[100 * res + 168], "Inverted threshold rejects bright pixels");
    }

    @Test
    @DisplayName("9.6 PNG export: five maps with documented palettes and shore outcomes")
    void pngFiveMapExportTest() throws Exception {
        IslandDraft draft = new IslandDraft(192);
        draft.setPreset(IslandDraft.Preset.ROUND);
        draft.setGenerationVersion(IslandDraft.GenerationVersion.V3);
        draft.setShoreDensity(1.0);
        draft.setShoreVariety(0.5);
        draft.setShoreRocky(true);
        draft.setShapeSeed(4242L);

        double halfSpan = draft.worldSpan() / 2.0;
        IslandMask mask = new IslandMask(192, 192, -halfSpan, halfSpan, -halfSpan, halfSpan,
            draft.relief().coastWidth, TestWorldgen.OCEAN_TRANSITION, draft.packedMask());
        IslandShapeConfig cfg = new IslandShapeConfig(3, draft.isletDensity(), draft.riverDensity(),
            draft.isWorldBorderEnabled(), draft.worldBorderMargin(), Optional.empty(), Optional.empty(),
            Collections.emptyList(), Optional.of(mask), draft.biomeSeed(), Optional.of(draft.shoreSettings()));
        IslandTerrain terrain = IslandTerrain.create(cfg);

        Path gameDir = Files.createTempDirectory("omniisle_export_test");
        Path exports = ImageImportExport.exportAll(draft, terrain, gameDir);
        List<Path> files;
        try (Stream<Path> stream = Files.list(exports)) {
            files = stream.filter(p -> p.toString().endsWith(".png")).sorted().toList();
        }
        assertEquals(5, files.size(), "exportAll must write mask, height, biomes, hillshade and shore");
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().endsWith("_mask.png")));
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().endsWith("_height.png")));
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().endsWith("_biomes.png")));
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().endsWith("_hillshade.png")));
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().endsWith("_shore.png")));

        Path shorePath = files.stream().filter(p -> p.getFileName().toString().endsWith("_shore.png")).findFirst().orElseThrow();
        BufferedImage shore = ImageIO.read(shorePath.toFile());
        assertEquals(192, shore.getWidth());
        assertEquals(192, shore.getHeight());
        Set<Integer> palette = Set.of(0x333333, 0x000000, 0xF0E6A0, 0x90989B);
        int land = 0, water = 0, shorePixels = 0, other = 0;
        for (int z = 0; z < 192; z++) {
            for (int x = 0; x < 192; x++) {
                int color = shore.getRGB(x, z) & 0xFFFFFF;
                if (!palette.contains(color)) other++;
                if (color == 0x333333) land++;
                else if (color == 0x000000) water++;
                else shorePixels++;
            }
        }
        assertEquals(0, other, "Shore map must use only the documented palette");
        assertTrue(land > 0, "Shore map must contain inland pixels");
        assertTrue(water > 0, "Shore map must contain water pixels");
        assertTrue(shorePixels > 0, "Density 1.0 must produce sand/stony shore pixels");

        BufferedImage biomes = ImageIO.read(files.stream()
            .filter(p -> p.getFileName().toString().endsWith("_biomes.png")).findFirst().orElseThrow().toFile());
        assertEquals(192, biomes.getWidth());
        long distinct = 0;
        Set<Integer> colors = new java.util.HashSet<>();
        for (int z = 0; z < 192; z++) {
            for (int x = 0; x < 192; x++) {
                colors.add(biomes.getRGB(x, z) & 0xFFFFFF);
            }
        }
        distinct = colors.size();
        assertTrue(distinct > 1, "Biome map must not be a single flat color");
    }

    @Test
    @DisplayName("9.6 Layout: six tabs and eight control rows fit at GUI scales 1-4")
    void layoutFitsGuiScales() {
        int[][] sizes = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {320, 160}, {285, 160}};
        for (int[] size : sizes) {
            Layout layout = EditorLayout.compute(size[0], size[1]);
            String at = size[0] + "x" + size[1];
            Rect screen = new Rect(0, 0, size[0], size[1]);
            assertTrue(EditorLayout.inside(screen, layout.done()), at + ": Done button must stay on screen");
            assertTrue(EditorLayout.inside(screen, layout.cancel()), at + ": Cancel must stay on screen");
            if (layout.minimal()) {
                assertTrue(EditorLayout.inside(screen, layout.notice()), at + ": notice must stay on screen");
                assertTrue(EditorLayout.inside(layout.notice(), layout.done()), at + ": Done must stay in notice");
                continue;
            }
            assertTrue(EditorLayout.inside(screen, layout.canvas()), at + ": canvas must stay on screen");
            assertTrue(EditorLayout.inside(screen, layout.controls()), at + ": controls must stay on screen");
            assertTrue(EditorLayout.inside(screen, layout.toolColumn()), at + ": tools must stay on screen");
            assertFalse(EditorLayout.overlaps(layout.canvasFrame(), layout.controls()),
                at + ": canvas and controls must not overlap");
            assertFalse(EditorLayout.overlaps(layout.toolColumn(), layout.canvasFrame()),
                at + ": tools and canvas must not overlap");
            assertTrue(layout.done().bottom() <= size[1] - 2, at + ": Done/Cancel clipped");
            assertTrue(layout.canvas().width() > 0 && layout.canvas().height() > 0,
                at + ": canvas must be visible");
            if (layout.listVisible()) {
                assertTrue(EditorLayout.inside(screen, layout.listPanel()),
                    at + ": biome list must stay on screen");
                assertFalse(EditorLayout.overlaps(layout.listPanel(), layout.controls()),
                    at + ": list and controls must not overlap");
            }
        }
        assertTrue(EditorLayout.compute(480, 270).compact());
        assertFalse(EditorLayout.compute(960, 540).compact());
        assertTrue(EditorLayout.compute(285, 160).minimal());
    }

    private static int countLand(IslandDraft draft) {
        int res = draft.resolution();
        int count = 0;
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                if (draft.isLand(x, z)) count++;
            }
        }
        return count;
    }

    private static void forEachLand(IslandDraft draft, LandConsumer consumer) {
        int res = draft.resolution();
        for (int z = 0; z < res; z++) {
            for (int x = 0; x < res; x++) {
                if (draft.isLand(x, z)) consumer.accept(x, z);
            }
        }
    }

    @FunctionalInterface
    private interface LandConsumer {
        void accept(int x, int z);
    }
}
