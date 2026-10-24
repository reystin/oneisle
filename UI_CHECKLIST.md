# OmniIsle 0.7.0 — Editor UI checklist (Phase 9.6)

Date: 2026-10-24. Target: Minecraft 26.3 / Fabric, `OmniIsleEditorScreen`.

Status legend: DONE = verified by automated test or direct code inspection;
PARTIAL = implemented but only partly verified; UNVERIFIED = not exercised in a
real client session.

## Acceptance summary

| Area | Status | Evidence |
|---|---|---|
| Six tabs (Shape/Terrain/Climate/Biomes/Structures/Tools) | DONE | `EditorTab` + per-tab widget lists; `layoutFitsGuiScales` |
| Layout at GUI scales 1–4 (1920×1080, 960×540, 640×360, 480×270 logical) | DONE (math) / UNVERIFIED (visual) | `EditorLayout` + `Phase9EditorTest.layoutFitsGuiScales` |
| Fill, Line, Ellipse, Rectangle; Shift square/circle; fill/outline | DONE | `toolsUndoRedoTest`, `toolsFourWaySymmetryTest` |
| Zoom/Pan: wheel at cursor 1×–8×, middle/Space drag, 16-px grid | DONE (model) / UNVERIFIED (input feel) | `PreviewViewport` + `viewportZoomPanTest` |
| Symmetry X, Z, 4-way, 6-way, 8-way | DONE | Phase 7 mirror/4-way tests; radial closure test |
| 7 new presets, 12 presets × 20 seeds validity | DONE | Phase 7 `testAllShapePresets` (land 12–55 %, main component ≥ 70 %, no border) |
| Shape/biome seed display, edit, dice, copy | DONE | Shape and Biomes tabs; `seedUxTest` |
| Inspector: elevation, tier, coast distance, shore outcome, islet status | DONE (code) / UNVERIFIED (visual) | `drawMapInspectorTooltip` |
| PNG export: mask, height, biomes, hillshade, shore | DONE | `pngFiveMapExportTest`; `docs/evidence/editor/png_export_*.png` |
| PNG import: mask + heightmap threshold/quantiles | DONE | `heightmapQuantileImportTest` |
| Undo/redo: land, height, spawn, resolution, all tools | DONE | `toolsUndoRedoTest` |
| Compact `BiomeSelectionScreen` path retained | DONE (compiles, button path) / UNVERIFIED (interactive) | `openBiomes` / `openStructures` buttons |
| Texture lifecycle: 50 open/close cycles | DONE (lifecycle) / UNVERIFIED (real texture manager count) | `PreviewTextureSlotTest` |
| Screenshots at GUI scales 1–4 | NOT DONE | No automated client screenshot harness in this environment; `docs/screens/README.md` |

## Tab contents (code inspection)

- **Shape:** preset cycle, island-size slider, relief cycle, new shape, resolution
  cycle (192/256/384), shape seed field + dice, copy both seeds.
- **Terrain:** land/height layer toggle, tiers T0–T3 (height only), height smooth,
  clear active layer.
- **Climate:** theme, gradient mode, climate scale, gradient strength, beach
  density, shore variety, rocky shores, relief shading.
- **Biomes:** category filter, select/deselect all, biome seed field + dice, copy
  seeds, open the full biome selection screen.
- **Structures:** density 0.5×/1×/2×, select/deselect all, open the structure
  selection screen.
- **Tools:** land/eraser/smooth/fill on row 1; line/rect/ellipse/spawn on row 2;
  symmetry + fill/outline; brush size; grid + zoom reset; undo/redo; share code +
  PNG tools; advanced settings (generation v1/v2/v3).

## Measured checks

- Presets: 12 × 20 seeds, land fraction range observed 13.5–40.1 %, largest
  component 100 % for every non-archipelago/twin preset, 0 border pixels.
- Radial symmetry: the naive "stamp a disk at rotated centers" approach was not
  closed under rasterized 60° rotation (e.g. `(126,34) → (164,91)` was missing in
  a measured failure). Radial painting now stamps the forward orbit of every
  painted pixel; the closure test passes for 6-way and 8-way paint/line/rect/ellipse.
- PNG export: 5/5 files at 192×192. Shore map palette: land `#333333` 37.85 %,
  water `#000000` 61.33 %, sand `#F0E6A0` 0.62 %, stony `#90989B` 0.20 %
  (density 1.0, variety 0.5, rocky true). Biome map contains 25 distinct colours.
- Heightmap import: equal-quartile 0/85/170/255 image maps to tiers 0/1/2/3
  exactly; flat image maps to tier 1; inverted threshold keeps dark pixels only.
- Layout fit at 480×270: controls content bottom 216, panel bottom 222, Done
  button bottom 250, screen height 270.
- Texture lifecycle: 50 editor sessions → 50 registrations, 50 releases; same
  resolution is reused; a resolution change releases once and registers once.

## Not verified interactively

- Real mouse-wheel/middle-drag/Space-drag feel in a running client.
- Tooltip legibility and tab button label clipping in ru_ru at GUI scale 4.
- Actual GL texture-manager count after 50 open/close cycles (unit test covers the
  register/release accounting only).
- Screenshots at GUI scales 1–4.
