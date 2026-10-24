# Changelog

All notable changes to OmniIsle are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
