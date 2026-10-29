# OmniIsle 1.0.0 — Editor UI checklist (flat widget system)

Date: 2026-10-26. Target: Minecraft 26.3 / Fabric, `OmniIsleEditorScreen` and
`com.hack337.omniisle.client.ui`.

Status legend: DONE = verified by automated test, direct code inspection, or a captured and
validated screenshot; PARTIAL = implemented but only partly verified; NOT DONE = not produced;
UNVERIFIED = not exercised in a real client session.

## Acceptance summary

| Area | Status | Evidence |
|---|---|---|
| Flat widget system (22 `client/ui` classes) | DONE | `Phase11UiWidgetTest`, `Phase11UiThemeTest`, `Phase11UiHygieneTest` |
| Theme tokens and WCAG contrast | DONE | 8 body-text pairs >= 4.5:1 asserted by `Phase11UiThemeTest` |
| 80 ms time-based hover/press easing, 50 % disabled alpha | DONE | `Phase11UiThemeTest` monotonicity + alpha checks |
| Layout tiers and overflow safety | DONE (math) | `Phase11UiLayoutTest` matrix + rewritten `Phase11EditorLayoutTest` |
| Six editor tabs | DONE | Captured at 16 window/scale combinations × 2 languages |
| Tool column with 8 tools and right-click eraser | DONE | Captures + unchanged tool behavior tests |
| Four canvas preview modes | DONE | `preview_land/height/biomes/shore.png` for both languages |
| FULL-only biome/structure list | DONE | Captures; row interactions in `Phase11UiWidgetTest` |
| Status bar and Done/Cancel | DONE | All captures; reachable in every density |
| Tooltips (400 ms), toasts, dropdown, clear popover | PARTIAL | Code + unit-tested placement/state; not visible in still captures |
| Keyboard/focus order and narration | DONE (unit) / UNVERIFIED (feel) | Focus order, activation, list keys in `Phase11UiWidgetTest`; no live input session |
| `BiomeSelectionScreen` and satellite screens restyled | DONE (code/tests) | Existing behavior tests unchanged; not captured by the editor harness |
| Create World button stays vanilla | DONE | `WorldTypeEditorMixin` untouched |
| Texture lifecycle | DONE (unit) | `PreviewTextureSlotTest`, 50 open/close cycles |
| Screenshots (after matrix) | DONE (captured, sampled inspection) | `docs/screens/1.0.0/after/` — 200 PNGs + 200-row `manifest.csv` |
| Render cost <= 2 ms median | PARTIAL | 11/12 medians <= 2 ms; en Climate 2.007 ms; p95 2.214–3.702 ms |
| GUI screenshots at every tier incl. MEDIUM/MINIMAL | PARTIAL | MEDIUM not produced by the matrix; MINIMAL unreachable in a real client |

## Density tiers

| Tier | Logical minimum | List | Tool column | Controls | Reached by captures |
|---|---|---|---|---|---|
| FULL | 840×470 | visible | icons + labels | 230–280 px | 854×480 s1, 1280×720 s1, 1920×1080 s1/s2 |
| MEDIUM | 700×420 | hidden → modal | icon-only | 230–260 px | no — unit tests only |
| COMPACT | 400×240 | hidden → modal | icon-only | 200–220 px | 640×480 s1, 854×480 s2–4, 1280×720 s2–4, 1920×1080 s3–4 |
| MINI | 320×150 | hidden → modal | 16-px icons | 150–170 px, icon tabs, scroll | 640×480 s2–4 (320×240) |
| MINIMAL | below 320×150 | none | none | none | no — unit tests only |

Actual logical sizes (requested framebuffer → logical): 640×480 s1 640×480; 640×480 s2–4 320×240;
854×480 s1 854×480; 854×480 s2–4 427×240; 1280×720 s1 1280×720; s2 640×360; s3–4 427×240;
1920×1080 s1 1920×1080; s2 960×540; s3 640×360; s4 480×270. macOS enforces a 640×480 framebuffer
minimum; Minecraft clamps the GUI scale so the logical area never drops below 320×240, which is why
MINIMAL cannot be photographed here.

## Tab contents

- **Shape:** preset menu (13 presets), island-size slider, relief segmented control, full-width
  `Resolution: 192×192` / `Разрешение: 192×192` row, shape seed field + dice, copy seeds.
- **Terrain:** Land/Height layer segmented control, tiers T0–T3, height smooth toggle, Clear with an
  inline confirmation popover before the undoable change.
- **Climate:** theme, gradient mode, climate scale, gradient strength, beach density, shore variety,
  rocky shores, relief shading.
- **Biomes:** category filter, seed field + dice, copy seeds, `+ All` / `− None`, reset weights,
  disable-weights toggle, open the biome selection screen.
- **Structures:** density 0.5× / 1× / 2×, select/deselect all, open the structure selection screen.
- **Tools:** symmetry, fill/outline, brush radius, grid, zoom reset, undo/redo, share code, PNG
  tools, advanced settings.
- **List:** follows the active tab — biome rows (swatch, ellipsised name, toggle, −/value/+ stepper,
  actual preview share with ✓/!/✕) or structure rows; the header names the contents and is disabled
  in structure mode. Weights are not shown in structure mode. `—` appears only for disabled rows or
  when there are no land samples.

## Screenshot matrix (after)

Each cell is 6 tab captures for en_us and 6 for ru_ru; all were validated for presence and exact
dimensions against `manifest.csv`. Capture status is **PASS 192/192** (every tab × window × scale ×
language) plus **PASS 8/8** preview modes; the "Visual inspection" column records what was actually
looked at, not what was captured.

| Request | Scale | Actual logical | Tier | Captured | Visual inspection |
|---|---|---|---|---|---|
| 640×480 | 1 | 640×480 | COMPACT | 12/12 | sampled |
| 640×480 | 2–4 | 320×240 | MINI | 36/36 | en shape, ru structures |
| 854×480 | 1 | 854×480 | FULL | 12/12 | en biomes, ru tools |
| 854×480 | 2–4 | 427×240 | COMPACT | 36/36 | — |
| 1280×720 | 1 | 1280×720 | FULL | 12/12 | en all six tabs, ru structures |
| 1280×720 | 2 | 640×360 | COMPACT | 12/12 | en biomes |
| 1280×720 | 3–4 | 427×240 | COMPACT | 24/24 | — |
| 1920×1080 | 1 | 1920×1080 | FULL | 12/12 | — |
| 1920×1080 | 2 | 960×540 | FULL | 12/12 | en tools, ru climate |
| 1920×1080 | 3 | 640×360 | COMPACT | 12/12 | — |
| 1920×1080 | 4 | 480×270 | COMPACT | 12/12 | — |

Preview modes at 1280×720 scale 1: `preview_land`, `preview_height`, `preview_biomes`,
`preview_shore` for en_us and ru_ru (8/8). All four en_us images were inspected; ru previews were
validated programmatically only. The Shape tab's list returns to biomes after visiting Structures,
and the preview-mode selector highlights the active mode (both were regressions fixed before the
final capture).

## Keyboard and focus

- Tab / Shift+Tab: top bar → tool column → canvas modes → tabs → active tab controls → list →
  status (Done/Cancel). Disabled widgets are skipped.
- Enter / Space activate the focused widget; Space toggles a list row or switch.
- Up / Down move in lists and tabs; Home / End jump to the first/last item; Left / Right adjust a
  list row's weight or a slider.
- Ctrl+Z undo; Ctrl+Y and Ctrl+Shift+Z redo. No new single-letter shortcuts.
- Esc closes, in order: dropdown → popover → weight edit → tooltip → screen.
- Mouse: wheel zooms the canvas or adjusts a hovered slider; middle/Space-drag pans; right-click
  selects the eraser; Shift draws squares/circles and fine-steps sliders; double-click resets a
  slider; list weight `−`/`+` buttons and click-to-type remain.

## Measured checks

- Full build **181 tests, 0 failures, 0 errors, 2 assumption-gated skips** (150 baseline tests
  unchanged, 31 new UI tests); v2 hash `-4990092939012319655` and v3 hash
  `-6312501814918578635` unchanged.
- Contrast: text/bg 15.02, text/panel 13.75, muted/panel 5.31, muted/raised 4.71, accent/panel 9.70,
  danger/panel 6.08, warn/panel 10.97, ok/panel 9.44; accent-button text 10.59.
- Screenshots: 200/200 files, 200/200 manifest rows, correct dimensions; before evidence is
  `docs/screens/1.0.0/before/editor_0.8.4_old_ui.png` (user-supplied 0.8.4 capture).
- Render cost at 1920×1080 GUI scale 2 (960×540), 300 frames after warm-up: medians 1.219–2.007 ms,
  p95 2.214–3.702 ms; 11/12 medians <= 2 ms, en_us Climate 2.007 ms. Full table in
  `FINDINGS.md` §19.6 and `render_cost.csv`.

## Not verified / known limits

- Live mouse/keyboard feel, IME composition, tooltip delay, toast fades and dropdown interaction
  were not exercised in an interactive session; unit tests cover state and placement only.
- The gradient segmented control ellipsises `North-South` (en) and `Запад-Восток` (ru) at FULL;
  its tooltip lists all four full option names, but that tooltip is not visible in still captures.
- `BiomeSelectionScreen`, `ShareCodeScreen`, `PngToolsScreen` and `AdvancedSettingsScreen` are not
  part of the screenshot harness; their file/draft behavior is covered by the unchanged tests.
- MEDIUM has no captured screenshot and MINIMAL is unreachable in a real client; both are covered by
  layout unit tests.
- 192 of 200 after images were not individually inspected; the programmatic validation checks set,
  dimensions and manifest only.
- Render cost misses the median target on one tab by 0.007 ms and every p95 is above 2 ms.
- 25×25 chunk-generation timing remains **UNVERIFIED**; Phase 9 structure matrix remains
  **PARTIAL 15/45**.
