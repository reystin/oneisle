# Changelog

All notable changes to OneIsle are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0+mc26.3-fabric] - 2026-10-29

First standalone **OneIsle** release: an independent continuation of
[OmniIsle](https://github.com/y9hack337/OmniIsle) 0.5.0 by Hack337. OneIsle 1.0.0 includes all
work prepared in the OmniIsle 0.5.0→1.0.1 line; version numbering restarts with the new
project. Licensed GPL-3.0-only; not affiliated with or endorsed by the original author.

### Changed
- **Full rebrand.** Mod id and resource namespace `omniisle` → `oneisle`; Java package
  `com.hack337.omniisle` → `com.reystin.oneisle`; `OmniIsle*`/`Omni*` classes →
  `OneIsle*`/`One*`; world preset `oneisle:oneisle`; noise settings `oneisle:one_terrain`;
  biome source, density functions and saved data under `oneisle:*`; command `/oneisle`;
  config `config/oneisle.json`; server datapack `oneisle-generated.zip`; import/export and
  debug paths under `oneisle/`; share-code prefix `ONE2-`; debug markers `ONE_*`; system
  properties and harness environment variables under `oneisle`/`ONEISLE`.
- New GitHub repository (<https://github.com/reystin/oneisle>) and Modrinth project
  (`oneisle`). The original OmniIsle repository and Modrinth page are left untouched.

### Breaking
- **Worlds created with OmniIsle 0.5.0–1.0.1 cannot be loaded by OneIsle.** Registry
  identifiers changed namespace, so the world preset, biome source, density functions and
  saved data no longer resolve. There is no migration; create a new world.
- Legacy `OMNI2-...` share codes are rejected; export a new `ONE2-...` code.
- `config/omniisle.json` is not read; use `config/oneisle.json` (defaults are regenerated).
- The version number restarts at 1.0.0; OmniIsle 1.0.0/1.0.1 are different projects.

### Unchanged
- Terrain math is untouched: v1 reproduces OmniIsle 0.5.0 and v2 reproduces 0.6.0
  bit-for-bit (`GoldenV1Test`, `GoldenV2Test`); v3 matches the OmniIsle 1.0.1 behavior.
  Only identifiers and packaging changed.
- No `generation_version` change; existing OneIsle v3 worlds follow the same
  "create a new world for fixes" rule.

The sections below document the OmniIsle lineage before the rename and are kept for history.

## [1.0.1+mc26.3-fabric] - 2026-10-28

v3 worldgen release: natural spawn, real highlands, clean beaches, biome-specific villages and
continuous climate bands. v3 is revised in place; there is no `generation_version = 4`. v1/v2
resources and generated packs are untouched (`GoldenV1Test`, `GoldenV2Test` pass; the v2/v3
biome-source regression hashes are unchanged). **Worlds already created with v3 keep their
generated datapack and are not repaired — create a new world.**

### Fixed
- **Beaches inside the island.** v3 granted sand to any column with `d ≤ 34` when patch, width
  and bayness passed, and the 8-point bayness ring could not tell a 10–30-block fjord from open
  ocean. 1.0.1 adds a single-pass `ShoreField.Ring(bay, minDistance)` scan at radius 96 and
  requires water at least 55 blocks deep (`ring.minDistance ≤ −55`), `heightTier ≤ 1.10` and
  `continentalness ≤ 0.20`; the beach band narrows to `MAX_INLAND = 28` with a width of 8–22.
  `ModernTerrainV2` only carves beach shelves while `d < 52` and tier ≤ 1.05. All six
  preview/paint call sites pass the same gates, so the editor always matches generation. Measured:
  0 violations on 15,067 sampled coastal columns across NATURAL/FJORDS/ARCHIPELAGO; a synthetic
  20-block fjord returned NONE on 198/198 banks while a straight open coast still produced
  76/201 sand columns.
- **Circular terraced dome at spawn.** v3 packs no longer emit `omniisle:spawn_plateau` as the
  final density. The guaranteed-dry spawn is now a real-block search at server start: the editor
  marker is accepted only when its column is dry, otherwise rings 16–512 blocks are scanned with
  the cheap SDF and `WORLD_SURFACE_WG` heights and the first dry column becomes the spawn. All-wet
  islands fall back to the marker surface with a warning instead of crashing. The
  `omniisle:spawn_plateau` codec stays registered so already-generated v3 packs still load.
- **Highlands stuck at Y 66–70 despite a ~260 editor preview.** v3 packs now generate
  `overworld/offset.json` as `add(mul(vanilla_offset, scale), lift)`, both splines driven by
  `omniisle:overworld/continents`: lift 0 → 1.0 and scale 1.0 → 0.20 between c = 0.45 and 0.95.
  Surface Y is roughly `(1.5 + offset)·128 − 64`, so painted tier-3 areas now reach the advertised
  range instead of the vanilla offset spline's ~Y 80 median. The v3 `final_density.json` and
  `preliminary_surface_level.json` fades move from 240→256 to 300→316 so peaks are not clipped.
  The editor preview curve is unchanged.

### Changed
- **Five vanilla village variants at ≈40 % density (v3).** v3 used to override the vanilla village
  set with plains-only entries while listing every land biome in `has_structure/village_plains`,
  so villages kept plains architecture in deserts, snow and taiga at vanilla density. 1.0.1 emits
  one weighted `structure_set/villages.json` with plains/desert/savanna/snowy/taiga (weight 1,
  salt 10387312) and family-scoped `has_structure/<tag>.json` tags (`replace: false`); empty
  variants are omitted and no matching family disables the set. Spacing/separation move from
  34/8 to **54/12** for v3 only — `(34/54)² = 0.3964`, a **−60.4 %** village count — while island
  size and density scaling still apply. v1/v2 keep the single fixed plains placement.
- **Continuous warm↔cold climate bands (v3).** `ClimateLayout.Sample` now exposes the raw smoothed
  `warmStrength`/`coldStrength`, and `selectLandFamilyV3` selects by `diff = warm − cold`: cold
  core ≤ −0.35 → snowy plains/taiga, cold band ≤ −0.12 → meadow/birch, temperate, warm band
  ≥ 0.12 → meadow/plains/savanna, warm core ≥ 0.35 → savanna/badlands/desert/jungle. The
  categorical province now only drives oceans, shores, the ice-spike hint and structure tags, so
  taiga no longer borders desert. Mountain snow (`tier ≥ 1.5 && c > 0.50` → snowy peaks/stony
  peaks/meadow) is province-independent, so snow-capped peaks may appear next to warm lowlands.
  Measured: 0 warm-core↔cold-core adjacencies and 100/100 transects separated by a band family on
  NATURAL at 192² (39/39 on Mineshield).
- `Phase13HighlandWorldgenTest` decodes the generated pack through real worldgen registries and
  samples `NoiseBasedChunkGenerator.getBaseHeight`. ROUND 4000 seed 4242 with a tier-3 paint at
  the editor spawn measured min 139 / median 228 / p90 235 / max 289 blocks across 1681 columns
  (±320 blocks, step 16); 1088 columns were at or above Y 220. Previous packs were capped by the
  240 fade and the un-lifted offset spline.
- Test-only: the pack harness in `Phase10PresetDecodeTest`/`Phase13HighlandWorldgenTest` now lists
  packs lowest-priority-first (`vanilla, mod, generated`). `MultiPackResourceManager` gives the
  **last** pack priority; the old order let the mod's static empty `continents.json` shadow the
  generated one, so generated density overrides were not actually exercised.

### Verified / not verified
- Automated: `./gradlew test` **193 tests, 0 failures, 2 assumption-gated skips** (183 baseline +
  10 Phase 14). `GoldenV1Test`, `GoldenV2Test`, `BiomeSourceV2V3RegressionTest` and the Phase
  9/10/11/14 shore, province, preset and reference-climate suites pass. v2 hash
  `−4990092939012319655` and v3 hash `−6312501814918578635` are unchanged.
- Measured 1.0.1: zero beach-gate violations and 136/150/224 shore columns on
  NATURAL/FJORDS/ARCHIPELAGO (3,000–7,272 coastal samples each); 198/198 fjord banks stay bare;
  0 warm↔cold core adjacencies on 39/39 and 100/100 transects; village density ratio
  `(34/54)² = 0.3964`; Phase 11 sand arcs (2-block raster, five presets) have median 36–70 blocks,
  P75 58–124 and 10 components ≤3 blocks of 144 (zero-speck remains **PARTIAL**).
- Interactive checks (beaches gone inside fjords, village biome match, warm→cold transect, F3
  heights, spawn, preview match) were **NOT** performed and are not claimed. 25×25
  chunk-generation timing remains **UNVERIFIED**, and Phase 9 remains **PARTIAL 15/45**.

## [1.0.0+mc26.3-fabric] - 2026-10-26

Presentation-only release: the island editor is rebuilt on a flat dark widget system. Worldgen,
`IslandDraft` data, share codes, datapacks and structure policies are unchanged; v1/v2/v3 output
stays bit-identical (`GoldenV1Test`, `GoldenV2Test`, v2/v3 regression hashes).

### Added
- **Custom editor UI (`client/ui`, 22 classes).** `OmniTheme` (8 measured WCAG pairs >= 4.5:1),
  `OmniUi` primitives, `OmniInteraction` with an 80 ms ease, `OmniButton`/`OmniIconButton`,
  `OmniToggle`, `OmniSegmented`, `OmniSlider` (wheel, arrows, Shift fine step, double-click reset),
  `OmniTabBar`, `OmniSection`, `OmniScrollArea`, `OmniListView`, `OmniBiomeList`, `OmniTooltip`
  (400 ms delay, screen-clamped), `OmniToast`, `OmniDropdown`, `OmniPopover`, `OmniTextField`
  (flat frame around a vanilla `EditBox`) and cached `OmniMetrics`/`OmniText`.
- **Density tiers.** FULL (list + labelled tools), MEDIUM (icon tools, list in a modal), COMPACT
  (scrolling controls), MINI (16-px tools, icon tabs, abbreviated status), MINIMAL (size notice with
  Cancel/Done only). Every rectangle comes from the pure `EditorLayout`; tests cover 13 window sizes
  at GUI scales 1-4 plus a 4-size × 4-scale matrix.
- **Icon atlas.** 20 icons in a 128×48 8×3 PNG generated by `tools/generate_ui_icons.py`
  (Python stdlib only); buttons fall back to text if the texture is missing.
- **Four preview modes** (Land, Height, Biomes, Shore) with cached palettes; switching modes never
  changes generation.
- **Editor shell:** top bar (brand, undo/redo with Ctrl chips, preset menu, share code, PNG tools,
  advanced), tool column with tooltips, six labelled/icon tabs, scrollable controls, FULL-only biome
  list with swatch, name, toggle, weight stepper and measured preview share, status bar with
  coordinates, biome, zoom, tool and symmetry, and primary Done / secondary Cancel.
- **Feedback and access:** toasts for copy/import/export/calibration results, inline confirmation
  before clearing a layer, keyboard focus order and narration on every custom widget, Esc closing
  overlays in order, and en/ru labels proven to fit or ellipsise under worst-case font metrics.
- **Dev-only screenshot and render-cost harness** (`OmniIsleClient` + `UiScreenshotHarness`),
  a no-op unless `-Domniisle.uiScreenshot=<dir>` is set; it writes `manifest.csv`,
  `render_cost.csv` and the captured PNGs.

### Changed
- `BiomeSelectionScreen`, `ShareCodeScreen`, `PngToolsScreen` and `AdvancedSettingsScreen` now use
  the same panels, buttons, toggles, sliders and text fields; their file/draft behaviour is
  unchanged. The Create World button injected by the mixin stays vanilla-styled.
- Top-bar preset and share labels no longer clip: the preset button shows the preset name in FULL
  and an icon elsewhere, the share button is 108 px, and tabs use two columns so "Structures" keeps
  its label at 1280×720. MINI action buttons are 56 px wide.

### Verification
- Full build **181 tests, 0 failures, 0 errors, 2 assumption-gated skips** (150 baseline tests
  unchanged, 31 new UI tests). v2 hash `-4990092939012319655` and v3 hash
  `-6312501814918578635` unchanged. Jar `OmniIsle-26.3-Fabric-1.0.0+mc26.3.jar`, SHA-256
  `5b2da1a4207507638820e2af9f4592e77794749ce9df150a0b52ba5f5ad7a050`.
- Real-client captures: **200 PNGs** in `docs/screens/1.0.0/after/` (6 tabs × 16 window/scale
  combinations × en_us/ru_ru plus 4 preview modes per language) with a 200-row `manifest.csv`,
  validated 200/200 for presence and dimensions. Before evidence is
  `docs/screens/1.0.0/before/editor_0.8.4_old_ui.png`. Reached tiers: FULL, COMPACT, MINI;
  macOS enforces a 640×480 framebuffer and Minecraft clamps the GUI scale to keep at least
  320×240 logical pixels, so MEDIUM is not produced by the matrix and MINIMAL is covered by unit
  tests only.
- Render cost at 1920×1080 GUI scale 2 (960×540), 300 frames after warm-up: medians
  1.219–2.007 ms, p95 2.214–3.702 ms; 11 of 12 medians meet the 2 ms target (en_us Climate
  2.007 ms). The target is **PARTIAL**, recorded in `render_cost.csv` and `FINDINGS.md` §19.6.
- Visual inspection is sampled (all six tabs and four preview modes at en_us 1280×720 scale 1 plus
  representative FULL/COMPACT/MINI and ru_ru captures); the remaining images are
  programmatically validated only. Interactive input feel, tooltips, toasts, dropdowns and the
  satellite screens remain **UNVERIFIED** visually; Phase 9 stays **PARTIAL 15/45** and 25×25
  chunk-generation timing remains **UNVERIFIED**.

## [0.8.4+mc26.3-fabric] - 2026-10-26

### Added
- **Universal v3 climate provinces.** `ClimateLayout` derives a warm sector (top 16 % of
  land by `-nx + 0.20·-nz + noise`), a disjoint cold sector (12 % of total land, warm
  excluded, by `nx + 0.35·-nz + noise`) and a ~70 % temperate heartland from the mask and biome seed
  alone, so every shape preset — not just the reference continent — receives the same
  coherent province system. Strength rasters are smoothed twice with `[1,4,6,4,1]/16`;
  coastal water inherits the nearest sector. Rare pale (top 3 % of temperate land) and ice
  (0.6 % of cold land) pockets are derived from low-frequency masks. The layout is created lazily for v3 and sampled
  through one bilinear grid by preview and worldgen (Invariant I3).
- **Largest-component main-island SDF for v3.** `ModernTerrainV2` now builds a second
  padded EDT for the largest 4-connected land component. `distances().main()` uses it, so
  offshore mushrooms cannot appear on the main island or on the Mineshield SW bay island;
  `signed` keeps the all-mask SDF and satellite islets. v2 keeps the old all-mask `main`.
- **Terrain APIs:** `IslandTerrain.heightTierAt`, `surfaceHeight`, `climateLayout` and the
  shared `physicalRidge(x,z,seed)` (`1 - 2·|fbm2(/750, seed+801, 3) |`) used by both terrain
  and climate; the mountain bias and physical spine can no longer drift apart.
- **Rare-biome control:** family hints (`ice_spikes` in the cold ice pocket), a 1.5–3 %
  cherry-grove band (`0.34 < c ≤ 0.48`, humidity > 0.58, low-frequency mask), and
  pale-garden pockets with `hum + 0.65·paleStrength > 0.88`. Hints apply only when the
  family has no non-neutral explicit weight, so user weights stay authoritative.
- **Strict v3 structure sectors and surface ceilings.** Fixed structures now require the
  requested family, province, continentalness range, slope and `surfaceHeight ≤ maxY`
  (desert pyramid WARM/desert, c ≤ 0.32, slope < 0.22, Y ≤ 88; jungle ≤ 0.36/Y96;
  swamp hut ≤ 0.16/Y88; igloo COLD/Y92; camp Y96; outpost/trail/portal any land Y100).
  Narrow tags contain only accepted families plus the actual point biome. No candidate
  writes `enabled=false`; there is no fallback. `RelaxedPyramids.allowsSurface` re-validates
  the chunk-center biome, ±8-block surface heights, relief, c, slope and province at runtime.
- **Editor fixes:** the resolution control is a full-width row that cycles 192→256→384 and
  reads exactly `Разрешение: 192×192` / `Resolution: 192×192` (the old half-width button
  rendered a fragment). Compact layouts scale their row height with the window so the
  controls never overlap the Done/Cancel panel at GUI scales 1–4; the inspector tooltip is
  clamped/flipped inside the preview canvas. Biome rows now show the actual preview share
  whenever land samples exist, even with neutral (all-100) weights; `—` appears only for
  disabled rows or an empty preview. The biome list is hidden in compact layouts instead of
  overflowing.

### Changed
- **v3 family tree is province-aware and ordered:** offshore mushroom (main island < 0),
  temperature bias (`WARM +0.16·strength`, `COLD -0.22·strength`), cold lowland priority
  (`COLD, c ≤ 0.36, tier < 1.5 → snowy_plains/taiga, with ice_spikes hint`), physical peaks
  (family 15 only at `c > 0.50 && tier ≥ 1.5` outside COLD), foothills, cherry, coastal
  wetlands (`c < 0.15, slope < 0.12, tier ≤ 1.0, mountains < 0.35`), temperate heartland,
  then warm desert/badlands/savanna/jungle. Family 15 can no longer appear as a low-tier
  snow blob, and no desert or badlands is adjacent to a cold-province column.
- **Family-centroid effective climate.** After selecting a family, the weighted picker
  receives that family's centroid temperature/humidity, so affinity favors the intended
  family while explicit 0–400 % weights still work. Ocean subtypes keep the raw climate.
- **Ocean sectors and shores follow the province:** warm/frozen/lukewarm/cold ocean
  variants, `snowy_beach` on cold coasts, and `stony_shore` only on steep/dry coasts. The
  0.8.2 dry-shelf rule is unchanged.
- **Continuous province and ocean contours.** `ClimateLayout` now samples the smoothed
  warm/cold strength rasters bilinearly and compares them against quantile-calibrated
  thresholds plus a 160-block boundary-noise term, instead of looking up the nearest mask
  cell. This removes the visible 18–20-block staircase at province edges while the
  quantiles keep the measured sector shares. Ocean subtypes gained rim transition bands
  (`lukewarm_ocean`/`deep_lukewarm_ocean` before `warm_ocean`, `cold_ocean` before
  `frozen_ocean`). Evidence: `docs/evidence/phase11/after/ocean_edge_natural.png`;
  tests 11L.6/11L.7 in `Phase11ClimateLayoutTest`.
- **Mineshield 4 height tiers cleaned:** a 3×3 majority/median filter removes isolated
  pixels; the curved central-north tier-3 range is one component (16 → 1) and the western
  mesa tier-2 band keeps 3 components (48 → 3, no specks). Tiers after cleanup:
  3/2/1 = 612 / 1,431 / 9,189; the land mask is unchanged (11,232 cells, 30.47 %).
- **v3 biome-source regression hash changes by design** to `-6312501814918578635`
  (`BiomeSourceV2V3RegressionTest`); the v2 hash stays `-4990092939012319655`.

### Compatibility
- No `generation_version` change. v1 and v2 output is **100 % bit-identical**; `GoldenV1Test`
  and `GoldenV2Test` pass unmodified. v3 **new chunks** change intentionally in 0.8.4
  (provinces, family tree, structures, Mineshield relief); existing v3 chunks keep their
  generated biomes. The Mineshield preset ordinal, share codes and saved drafts are
  unchanged. Re-export a world pack to pick up the new structure policies.
- Full suite: **150 tests, 0 failures, 2 assumption-gated skips**. Performance of the new
  v3 sampling is **UNVERIFIED**; GUI screenshots remain **NOT DONE**.

## [0.8.3+mc26.3-fabric] - 2026-10-26

### Added
- **Mineshield 4 Map preset.** The bundled reference map (`карта рефернс.jpg`) is now a
  fixed shape preset in the editor. The image is letterboxed to the canvas and classified
  into a land mask (blue/teal water by hue) and three relief tiers: blue-white snow → 3,
  brown/orange highlands → 2, all other land → 1. At the default 192 resolution the mask
  has 11,232 land pixels (30.47 %), zero border land, and a 91.06 % largest connected
  component; it is identical at 192/256/384 and ignores the shape seed, so the coastline
  is stable. The preset controls only the land mask and relief — biomes still follow the
  selected theme and v3 climate. Evidence: `docs/evidence/presets/mineshield_4_mask.png`,
  `mineshield_4_stats.csv`; tests: `Mineshield4MapPresetTest` (5 tests), Phase 7.4 now
  covers **13 presets × 20 seeds**.
- English and Russian labels for the preset: "Mineshield 4 Map" / "Карта Mineshield 4".

### Compatibility
- No `generation_version` change and no worldgen change. The preset only writes a fixed
  mask/height layer into new drafts; existing drafts, share codes and worlds are unaffected.

## [0.8.2+mc26.3-fabric] - 2026-10-26

### Fixed
- **Dry coastal shelf is land again.** v3 `signedDistance = 0` maps to
  `continentalness ≈ -0.08`, but the real waterline is near `-0.18`. 0.8.1 returned
  OCEAN for every `d <= 0` when no shore outcome fired, painting a ~30–40 block dry
  shelf as ocean. `sampleV3` now treats `d <= 0` as submerged only below
  `V3_OFFSHORE_OCEAN_DISTANCE = -42` or `c < V3_SEA_LEVEL_CONTINENTALNESS = -0.18`;
  the dry shelf becomes LAND, or stony shore on steep/high columns when shore biomes
  are enabled. A live server transect puts the last dry column at `c = -0.1478,
  d = -32.8` (stony shore) and the first ocean at `c = -0.1843, d = -40.4`, confirming
  the constants (`docs/evidence/phase82/waterline_probe.txt`). Reference map:
  1,844 dry-shelf land columns, 0 dry-shelf ocean columns.
- **Swamps no longer cover stone mountains.** The climate `mountainBands` field is now
  driven 70 % by the exact physical ridge used by `ModernTerrainV2`
  (`1 - 2·|fbm2(x/750, z/750, seed+801, 3, 2.0, 0.5)|`) plus 30 % climate noise, using
  the same fallback seed. Family selection also guards swamps/mangroves to
  `c < 0.15` and `mountains < 0.42`; the reference map has 0 violating columns.
  Measured ridge/mountain correlation: 0.9808.
- **Reference continent composition.** The new v3 family tree yields 68.05 % core
  temperate land (65–70 % target), 79.05 % green land, a central snowy spine
  (5,015 peak columns, 135 non-peak exceptions), foothills with cherry/meadow/dappled/
  taiga, and a cohesive low/mid warm-arid region (607 columns, max `c = 0.2871`,
  largest connected component 97.36 %, all within 400 blocks of the coast). Cold,
  cool and mid-temperate bands use elevation cooling, so high terrain stays snowy.
- **Mushrooms are offshore-only.** The old fixed inland mushroom district is replaced
  by a coherent offshore islet/bay-pocket mask at `fbm(x/650, z/650, seed+811)`; the
  reference map has 0 inland mushroom columns and 202 offshore mushroom columns out of
  812 offshore land columns.
- **Fixed structures are strictly gated.** v3 exported structures now use per-structure
  family/continentalness/slope/zone policies with no category or high-continentalness
  fallback. If a family is absent, the set is exported with `enabled=false` and no
  placed point. Tags list only the accepted families' enabled biomes plus the actual
  point biome; broad tags remain for random-spread villages/trial chambers and the
  ocean/deep-dark gates. `RelaxedPyramids` regained runtime validation (biome, surface
  height, 8-block relief, slope, elevation) and returns empty instead of placing a
  pyramid on a mountain. 0.8.1 evidence: desert pyramid at `c = 0.6148` in
  CHERRY_GROVE; 0.8.2 exports it disabled on the same map
  (`docs/evidence/phase82/structure_placement_before.csv` / `_after.csv`).
- **Warm shallow caves use sulfur caves in v3.** The v3 cave branch routes
  `y ∈ [-32, 0)` and `temperature > 0.68` to sulfur caves before the deep-dark noise
  test. Fixture: 259 warm shallow columns, 104 sulfur / 155 deep_dark before,
  259 sulfur / 0 deep_dark after (`docs/evidence/phase82/cave_sulfur.csv`).

### Changed
- **Default island size is 3500 blocks** (was 4000). Share codes and saved presets keep
  their stored size; only new drafts are affected.
- **dappled_forest and pale_garden are natural v3 members.** Dappled forest joins the
  forest family (3) in mid-temperate transitions; pale garden is a rare inland
  mid-temperate pocket (family 17), measured at 27 of 136,337 land columns (0.02 %).
- **v3 temperature/gradient scale:** `V3_GRADIENT_SCALE = 0.55` and
  `V3_TEMPERATURE_NOISE_SCALE = 1.15` produce a 0.285–0.802 temperature span on the
  reference map, replacing the old uniform-hot distribution.
- **v3 surface climate is cached per column.** `OmniBiomeSource.ColumnCache` now stores
  the y-independent v3 `SampleResult`, so the added ridge fBm does not run per Y quart.
- **Main-island distance API:** `IslandTerrain.Distances(signed, main)` and
  `ModernTerrainV2.distances(x, z)` expose the main-island SDF separately from the
  islet-merged SDF; `WorldPackBuilder` uses the main distance for structure gating.

### Migration
- Existing v3 chunks keep their generated biomes. New chunks in 0.8.1 v3 worlds use
  the 0.8.2 climate/shoreline; no `generation_version` was added.
- Re-export the world pack with 0.8.2 to get strict structure placement.
- v1/v2 output is unchanged; `GoldenV1Test` and `GoldenV2Test` pass unmodified.

### Known limitations
- Full 25×25 weighted chunk-generation timing is still **UNVERIFIED**; only the biome
  lookup benchmark runs. GUI 2/4 screenshots remain **NOT DONE**.
- The Phase 9 structure matrix remains **PARTIAL 15/45**.

## [0.8.1+mc26.3-fabric] - 2026-10-26

### Fixed
- **Weighted biome ticket is coherent again.** 0.8.0 picked weighted land biomes
  with an independent per-block hash, so any non-neutral weight turned the preview
  and the real world into 1-block noise. 0.8.1 uses a family-independent 3-octave
  fBm field at a 420-block scale, flattened through a static 1024-bin empirical CDF,
  and caches the ticket per column in `OmniBiomeSource`. A 192×128 one-block window
  changes from 21.8 % to 99.4 % 4-neighbour agreement and 43.9 % to 0.0 % isolated
  pixels (`docs/evidence/phase10/cherry45_before_after.png`,
  `cherry45_metrics.csv`). The island-wide 192×192 preview agreement rises from
  19.1 % to 67.4 % (coastal shore/river pixels included).
- **World-preset export is validated before it is added.** `WorldPackBuilder.validatePreset`
  decodes the generated `world_preset/omniisle.json` through the live
  `DynamicRegistry`/`WorldPreset.DIRECT_CODEC` context. A corrupt or undecodable
  preset now shows `omniisle.editor.error.preset` and keeps the editor open instead
  of failing at world entry.
- **Corrected receipt:** Cherry Grove at 45 % targets **17.32 %** of land (the reported
  editor showed 17.4 %); held-out real-source sampling on a grid different from the
  calibrator gives 17.82 % (0.50 pp error, 1,869 columns). The biome "did not shrink"
  because 45 % is a relative frequency, not a cap; the visible problem was the noise.

### Changed
- **Migration note:** worlds created with 0.8.0 non-neutral weights will generate
  differently after updating. Their 0.8.0 output is the per-block noise bug; the
  affected chunks keep the old noise, newly generated chunks use coherent patches.
  Unweighted/all-100 worlds and share codes without `biome_share` stay bit-identical;
  no `generation_version` was added.
- Cherry-45 evidence and held-out CSVs are now written into
  `docs/evidence/phase10/` (`calibration.csv`, `calibration_examples.csv`,
  `heldout_shares.csv`, `cherry45_metrics.csv`, before/after PNG).

### Known limitations
- The reported **client crash is UNVERIFIED**: no crash report, `hs_err_pid*` or
  DiagnosticReport was found; a fresh dedicated server with the exact draft reached
  "Done" and generated 512 forced chunks without an error. The client instance also
  ships `mcopt-metal 0.3.0-alpha.2`, which remains the leading environmental suspect.
- Full 25×25 weighted chunk-generation timing is still **UNVERIFIED**; only the biome
  lookup benchmark runs. GUI 2/4 screenshots remain **NOT DONE**.

## [0.8.0+mc26.3-fabric] - 2026-10-25

### Added
- **Per-biome frequency weights:** every climate-driven land biome has a 0–400 %
  relative-frequency field (default 100, step 5) in the editor biome panel.
  0 disables a biome exactly like unticking it; other biomes absorb the difference.
  Terrain-driven rows (peaks, hills, mushroom fields) are read-only; shore, ocean,
  river and cave rows keep checkbox-only selection.
- **Optional `biome_share` config:** `weights` (UI intent), `bias` (calibrated
  worldgen input), `achieved`, `target` and `feasible`. Missing field and all-100
  weights are bit-identical to 0.7.0; no new `generation_version`.
- **Calibration:** deterministic 64×64 mask-land fitting shared by the live editor and
  datapack export (log-ratio damping 0.7, ≤24 iterations, mean bias 0, ±8 clamp,
  feasibility threshold 3 pp). 45/45 preset×theme×vector configurations converge.
- **Editor panel:** read-only actual share per biome (green ≤3 pp, amber ≤6 pp, red
  beyond, grey for terrain-driven), ±/click-to-type entry, row reset, Reset all,
  Disable selected, + All/− None, infeasible warning, 150 ms off-thread recalibration,
  undo/redo, share-code and preset round-trip.
- `/omniisle info` now prints weights and achieved shares; `/omniisle debug biome`
  renders through the weighted source.
- Tests 10.1–10.10 (`Phase10BiomeShareTest`) plus `docs/evidence/phase10/`.

### Changed
- New drafts default to theme **ALL** with every biome enabled at 100; existing
  drafts, presets and share codes keep their saved biome sets.
- All-100 weights are stored as absent entries; neutral exports omit `biome_share`.

### Known limitations
- Real chunk-generation timing with non-neutral weights was not rerun; only a biome
  lookup benchmark exists (status UNVERIFIED).
- Panel screenshots at GUI scales 2/4 are not captured; no drag slider.

## [0.7.0+mc26.3-fabric] - 2026-10-24

### Added
- **Generation v3 (new default):**
  - Deterministic `ShoreField`: patchy beaches, bay-aware sand, rocky/stony coasts, intertidal strip and calm-water pockets; configurable `shore.density`, `shore.variety`, `shore.rocky`.
  - Smooth ocean depth ramp reaching the deep threshold at `0.4·Wo` with zero slope at the coast.
  - Guaranteed deep monument band `[0.4·Wo, 1.0·Wo]`; satellite islets moved beyond `1.0·Wo + 80`.
  - River mouths carve across the waterline; v3 river threshold follows the observed drainage.
  - Spawn-plateau density wrapper (`omniisle:spawn_plateau`, radius 80, top Y 78) under the editor spawn marker.
  - Fixed three-start stronghold placement and relaxed desert/jungle pyramids for low islands.
- **Golden v2 snapshot:** 30 configs × 1 500 points × 4 metrics generated from 0.6.0 (`7a5174d`), asserted bit-exactly by `GoldenV2Test`.
- **Expanded v1 snapshot:** biome-source hashes for 30 configs (surface + 4 underground Y levels) in `GoldenV1Test`.
- **Editor (Phase 9.6):**
  - Six tabs (Shape, Terrain, Climate, Biomes, Structures, Tools), responsive down to 480×270.
  - Fill/Line/Rectangle/Ellipse tools with Shift constraints and fill/outline; 6-way/8-way radial symmetry with closed pixel orbits.
  - Zoom 1×–8× at cursor, middle/Space pan, 16-px grid; seed fields, dice and copy; inspector with shore outcome.
  - Shore PNG export (5th map) and heightmap quantile import; height/shore-aware preview.
- **Server & lifecycle:**
  - First-boot dedicated-server datapack export from `config/omniisle.json` before pack scan.
  - Dynamic biome list test through the real `RegistryDataLoader`.
- **Evidence & documentation:** shore/climate/coast/relief/river/server/structure/spawn datasets, corrected `OMNIISLE_AI_SPEC.md` for v1/v2/v3, performance harness and CSVs.

### Changed
- New worlds default to `generation_version = 3`; v1 and v2 keep their frozen output (I1).
- v3 coast warp scales quadratically below 4000-block islands, keeping coast error within 0.6 px mean / 2 px max.
- Climate gradients use explicit per-theme spans (`0.5·span·strength`); v1/v2/v3 temperatures at default settings are unchanged.
- Legacy and share-code configs without `river_density` default to 0 (rivers off).
- v3 biome source resolves shores via `ShoreField`; ocean/deep classification uses the new depth ramp.
- v3 structure placement includes relaxed pyramids and exact stronghold starts; structure set export is island-aware.

### Fixed
- SDF terracing/overshoot at mask edges (exact EDT, clamped Catmull-Rom).
- Straight ruler-like coasts replaced by deterministic patchy shorelines.
- Ocean monuments failing due to missing deep band.
- Strongholds missing on 2500-block islands; pyramids rejected near sea level.
- Spawn in water; spawn marker now has a dry plateau in v3.
- Vanilla ribbon shores at bay heads and steep slopes; slope-aware stony shores.
- Radial symmetry rasterization gaps.
- Ancient Cities selected without Deep Dark; v3 now disables the structure set when `deep_dark` is not in the enabled biome palette.
- Dedicated server loading a world without the generated pack.

## [0.6.0+mc26.3-fabric] - 2026-10-24

### Added
- **Dual-Generation Architecture:**
  - `generation_version = 2` (default for new worlds): High-quality continuous SDF terrain, organic coasts, mountain ridges, bathymetry, satellite islets, and carved rivers.
  - `generation_version = 1` (legacy): Bit-for-bit identical reproduction of 0.5.0 generation. Pre-existing worlds load seamlessly without terrain or biome seams (Invariant I1).
- **High-Precision SDF & Organic Coastlines (v2):**
  - Native grid exact Felzenszwalb-Huttenlocher Euclidean Distance Transform with sub-pixel distance convention.
  - Catmull-Rom bicubic interpolation on the continuous SDF field with 4x4 neighborhood clamping to prevent terracing and overshoot.
  - Domain warping applied exclusively to mask coordinate lookup (amplitude 45 blocks, frequency 1/520; second octave 12 blocks, 1/190).
  - Near-coast micro-roughness centered at the actual shoreline value of -0.08 with smoothstep falloff.
  - Flat `float[]` array caching for thread-safe, allocation-free chunk generation.
- **Dynamic Inland Relief (v2):**
  - Compressed coastal base rise ($\text{base} = -0.08 + 0.48 \cdot \tanh(d \cdot 1.25 / W_c)$) leveling off to ~0.40 inland to provide headroom for mountain peaks.
  - Procedural ridged mountain spines ($\text{ridge} = 1 - 2 \cdot |\text{fbm}|$) fading in inland ($d > 60$ blocks) with tanh soft saturation up to 0.95.
  - 4-Tier Height Layer (Lowland, Normal, Plateau/Hills, Alpine Peaks) painted in the editor, stored as Base64 in `continents.json`, smoothed with a 3x3 kernel and bilinear sampled.
  - Hillshade preview shading (Northwest illumination 315° azimuth, 45° altitude) blended over biome preview colors.
- **Ocean Bathymetry & Satellite Islets (v2):**
  - Soft ocean floor at -0.80 replacing the flat -1.0 lock, leaving dynamic range for ocean trenches (-1.0) and seamounts (-0.50).
  - Continuous seabed noise with weight $w = 0$ at the coastline.
  - Guaranteed deep ocean band ($s \in [0.4 W_o, 1.0 W_o]$) ensuring Ocean Monuments always have valid placement space.
  - Satellite islets generator with cellular noise and density slider (0.0 to 1.0), generating $\ge 350$ blocks offshore with beaches and vanilla ocean structures.
- **Structures & Inland Spawn Protection:**
  - Dynamic concentric ring distance scaling for strongholds ($d = \text{clamp}(8, 32, \text{round}(32 \cdot \text{size} / 8000))$), guaranteeing reachable strongholds on land even on 2500-block islands.
  - Decoupled deep dark conditions from surface peaks, guaranteeing Ancient Cities in subterranean chambers.
  - Automated inland spawn selector picking the pixel with the largest positive SDF within 40% of the island radius.
  - Gold crosshair spawn marker in the editor preview canvas with manual spawn placement and right-click auto-reset.
  - First-load spiral search land relocation to guarantee players never spawn in water.
- **Climate & Biome Enhancements:**
  - Biome scale slider (`climate_scale` from 384 to 1536 blocks, default 720) in editor.
  - Latitudinal temperature gradients (Off, North-South, East-West, Radial) with theme-aware amplitude compression.
  - Noise-jittered blending between neighbouring climate families to prevent straight-line biome borders.
  - Slope-based stony shore cliff selection for slopes $\ge 0.35$.
- **Advanced Editor Tools & Presets:**
  - Multi-resolution canvas support: 192x192 (default), 256x256, and 384x384 with bilinear resampling of land and height layers on resize.
  - Deflate-compressed share codes (`OMNI2-<base64url(deflate(payload))>-<crc16>`) with CRC16 validation and 256 KiB decompressed cap.
  - PNG mask and heightmap import (grayscale threshold, invert, heightmap quantile mode, aspect-preserving letterbox) and PNG maps export (mask, height layer, biome map, hillshade).
  - Advanced drawing tools: Fill (flood fill), Line (Bresenham), Rectangle, Ellipse (Shift for square/circle constraint, filled/outline toggle).
  - 6 Symmetry modes: Off, Mirror-X, Mirror-Z, 4-Way, 6-Way, 8-Way radial symmetry across all brush tools.
  - Canvas Zoom & Pan (1x to 8x zoom via wheel, space/middle-drag to pan) and 16px grid overlay toggle.
  - 7 New Procedural Shape Presets: Atoll, Volcano, Fjords, Twin Islands, Inland Sea, Horseshoe Bay, Ridge Island (12 presets total).
  - Seed text input boxes and dice reroll buttons with clipboard copy.
  - Inspector tooltip upgrade displaying elevation in blocks (~Y), height tier, distance to coast, and islet/ocean status.
  - Responsive 4-tab editor layout (`DRAW`, `SHAPE`, `CLIMATE`, `PRESETS`) usable at GUI scale 4.
- **Stretch Goals & Dedicated Server Support:**
  - Physically carved rivers and lakes derived at load via Priority-Flood depression filling and D8 flow routing with $C^1$ smoothstep carving into continentalness and erosion.
  - Registry-driven dynamic biome provider discovering datapack and modded biomes from `WorldCreationContext` with 56-biome fallback.
  - World border option centering vanilla `WorldBorder` on the island with configurable margin (500 to 4000 blocks).
  - Server auto-datapack generation from `config/omniisle.json` on startup.
  - `/omniisle debug <mode> [radius] [step]` command exporting real server-side PNG maps.
  - `/omniisle import <preset|code>` command for dedicated servers.
  - `/omniisle info` command reporting generation version, resolution, island size, bounding box, spawn, and active layers.

### Changed
- Refactored terrain sampling into side-neutral `IslandTerrain` interface with `ModernTerrainV2` and `LegacyTerrainV1`.
- Upgraded `IslandMask.CODEC` with optional backward-compatible fields.
- Upgraded structure placement configurations with small-island density scaling.
- Complete bilingual localization (`en_us.json` and `ru_ru.json`) for all new tools, sliders, tabs, and messages.

## [0.5.0] - 2026-10-23
- Initial release for Minecraft 26.3 with 5 procedural presets, custom BiomeSource, and in-game editor.
