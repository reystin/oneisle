# Changelog

All notable changes to OmniIsle are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
