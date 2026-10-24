# OmniIsle 0.6.0 — Implementation Progress Tracker

## Global Invariants
- [x] **I1.** Old worlds (0.5.0 / v1) keep generating identically (no seams).
- [x] **I2.** Spec §7 invariants maintained (fallbacks, texture cleanup, UTF-8, InputConstants).
- [x] **I3.** Server/preview parity (single source of truth in side-neutral package).
- [x] **I4.** Determinism & thread safety (pure functions, no shared Random, double arithmetic).
- [x] **I5.** Graceful failure on unknown version / corrupt mask data.
- [x] **I6.** Bilingual localization (en_us and ru_ru).

---

## Phase Checklist

### Phase 0 — Baseline Capture & Verification
- [x] **0.1** Verify facts and write `FINDINGS.md`
- [x] **0.2** Setup `DECISIONS.md` and `PROGRESS.md`
- [x] **0.3** Generate and commit golden regression data (`src/test/resources/golden/v1/`)
- [x] **0.4** Implement `GoldenV1Test` passing against unmodified code

### Phase 1 — Versioning & Compatibility
- [x] **1.1** `generation_version` in `continents.json` (v1 legacy vs v2 modern)
- [x] **1.2** `resolution` and `version` fields in `IslandMask.CODEC` with optional fallback
- [x] **1.3** `IslandTerrain` interface in side-neutral package (`com.hack337.omniisle.continent`)
- [x] **1.4** Frozen `LegacyTerrainV1` preserving 0.5.0 math bit-for-bit
- [x] **1.5** Editor advanced toggle: Generation v2 / v1
- [x] **1.6** `/omniisle info` command

### Phase 2 — SDF Quality (Coast & Slopes) [v2]
- [x] **2.1** Sub-pixel distance convention (`dIn - 0.5`, `-(dOut - 0.5)`)
- [x] **2.2** Catmull-Rom bicubic interpolation on SDF with neighborhood clamping
- [x] **2.3** Domain warping on mask lookup only (frequency 1/520, amplitude 45)
- [x] **2.4** Near-coast micro-roughness centered at shoreline -0.08
- [x] **2.5** Performance benchmarking & flat float[] cache

### Phase 3 — Ocean Floor & Satellite Islets [v2]
- [x] **3.1** Soft ocean floor at -0.80
- [x] **3.2** Continuous seabed noise (weight = 0 at coast)
- [x] **3.3** Guaranteed deep band for Ocean Monuments
- [x] **3.4** Satellite islets generator with `islet_density` slider

### Phase 4 — Inland Relief (Stop the Cone) [v2]
- [x] **4.1** Procedural ridged mountain spines with tanh soft saturation
- [x] **4.2** 4-tier height layer with blurred sampling and Base64 packing
- [x] **4.3** Hillshade lighting in preview (NW light 315°, 45° altitude)

### Phase 5 — Structures & Spawn
- [x] **5.1** Automated structure placement verification harness
- [x] **5.2** Stronghold distance scaling for island radius
- [x] **5.3** Relaxed Ancient City & Ocean Monument placement fallbacks
- [x] **5.4** Largest-SDF inland spawn selector & editor gold crosshair marker

### Phase 6 — Climate & Biomes
- [x] **6.1** `climate_scale` slider (384-1536) in editor
- [x] **6.2** Latitudinal temperature gradient (N-S, E-W, Radial) with theme-aware compression
- [x] **6.3** Noise-jittered border blend (no straight borders > 400 blocks)
- [x] **6.4** Server/preview parity test (≥ 97% family agreement, 100% zone parity)
- [x] **6.5** Slope-based stony shore vs sandy beach (cliffs >= 0.35 slope become stony shore)

### Phase 7 — Editor Features
- [x] **7.1** PNG mask import/export
- [x] **7.2** Presets & share codes (`OMNI2-...`)
- [x] **7.3** Advanced drawing tools: Fill, Symmetry, Zoom/Pan, Grid
- [x] **7.4** New procedural presets (Atoll, Volcano, Fjords, Twin Islands, Inland Sea, Horseshoe Bay, Ridge Island)
- [x] **7.5** Optional higher resolution: 192 / 256 / 384
- [x] **7.6** Seed text fields & copy buttons
- [x] **7.7** Inspector hover tooltip upgrade

### Phase 8 — Stretch Goals
- [x] **8.1** Physically carved rivers & lakes
- [x] **8.2** Registry-driven biome list
- [x] **8.3** World border option
- [x] **8.4** `/omniisle debug` visual exporters
- [x] **8.5** Dedicated server support

---

## Phase 9 — Audit & Redesign (0.7.0)

### Phase 9.0 — Orientation
- [x] **9.0.1** Baseline build green; LF normalization; mac JDK documented
- [x] **9.0.2** Architecture map and contradiction list in `PHASE9_NOTES.md`

### Phase 9.1 — Audit of 0.6.0 claims
- [x] **9.1.1** Stronghold harness/ring geometry measured — fake 24.12 number explained
- [x] **9.1.2** Ocean radial profiles + 6000×6000 height PNG — floor is soft −0.80, not flat −0.92
- [x] **9.1.3** Density-sampler timing v1/v2; full chunk generation marked UNVERIFIED
- [x] **9.1.4** Real source vs preview parity 256×256 (100 % rivers off; 88.3 % family with rivers)
- [x] **9.1.5** Golden tests extended with real v1 `OmniBiomeSource` snapshot
- [x] **9.1.6** Ancient City tag/placement audited (surface biome can host it)
- [x] **9.1.7** World border default off; `copy()` bug recorded
- [x] **9.1.8** Climate family fractions measured; theme gradient ineffective in ALL/STANDARD
- [x] **9.1.9** Uniform shore ribbon measured: 100 % of coast samples
- [x] **9.1.10** Structure harness/test seeds fixed; report generation now deterministic

Evidence: `docs/evidence/phase9_before/README.md`.

### Phase 9.2 — Shoreline redesign [v3] (completed)
- [x] **9.2.1** Explicit shore field (`ShoreField`): patch mask, noise-varied inland width + notch, 8-point bayness, slope, three outcomes (sand/stony/none), noise-jittered intertidal limit
- [x] **9.2.2** Nested optional `shore:{density,variety,style,rocky}`; `generation_version = 3` defaults; v1/v2 path and structure sampling untouched
- [x] **9.2.3** Climate-tab controls (Beaches 0–100 %, Shore variety, Rocky shores) with live preview invalidation; `/omniisle debug shore` three-colour mask; `/omniisle debug biome` colours shore biomes; settings/copy/share-code/pack round-trip test
- [x] **9.2.4** 30-island acceptance (61,440 contour points), density sweep, bay/headland ratio, 100 % preview/server parity, continuity, line-fit border check, before/after PNGs
- [x] **9.2.5** v3 coastal terrain easing: beach pockets flattened (×0.62 relief), no-beach coasts steepened (×1.18); max continuity delta 0.0109 < 0.015

Measured (defaults): sand 22.1 %, stony 3.9 %, none 74.0 %; longest run failures 0;
density 0→0 %, 1→71.2 % monotone; bays/headlands sand ratio 2.51; widest line-fit-straight
border 56 blocks (longest border 155 blocks, 7.1 block deviation). Evidence: `docs/evidence/shore/`.

### Phase 9.3 — Ocean floor and deep-ocean band [v3] (completed)
- [x] **9.3.1** v3-only smooth ocean ramp `base = -0.08 - 0.54·smoothstep(s/0.4Wo) - 0.18·smoothstep(s/Wo)` with zero slope at the coastline; soft floor −0.80; v1/v2 linear ramp untouched (I1)
- [x] **9.3.2** Seabed noise weight `smoothstep(s/0.5Wo)` is exactly 0 at the coast; seamounts (`max(0,fbm)·0.18`) fade in only beyond `1.0·Wo`; trenches `0.12`; global clamp `[-1.0, 0.70]`
- [x] **9.3.3** Deep-ocean guarantee: in `[0.4·Wo, 1.0·Wo]` no positive seabed noise, worst `c = -0.712976` (threshold `-0.58`) over 120,856 samples in 15 configs, 0 violations
- [x] **9.3.4** Islets keep `c = max(c_ocean, c_islet)`; v3 clearance `Wo + 80` (legacy 350); 62 islet land samples, peak 0.2355, nearest coast 611 blocks, max profile delta 0.0446
- [x] **9.3.5** Ocean monuments target deep water: 5×5 chunk-centre survey below −0.62 (mirrors the 29-block biome check), score toward −0.72; placement class now extends `RandomSpreadStructurePlacement` so 26.3 `/locate` finds it, with exact-chunk restriction to keep the scan cheap
- [x] **9.3.6** Evidence: 23,184-row radial CSV, 6000×6000 height PNG, `/locate structure minecraft:monument` in 10/10 configs (511–1000 blocks away) with `#minecraft:is_deep_ocean` at y=62/50/40/30

Measured: `c` in band max −0.713 across all 15 headless configs; global `c` range
[−1.000, −0.052] (coast micro-roughness); monument centres `c` −0.748…−0.719 with
29-block survey maximum ≤ −0.640; 14/15 pack tags are `deep_ocean`, one config
selects `cold_ocean` because deep-cold variants are not in the default palette, while
`c` still sits below the deep threshold. Real 26.3 server evidence used
`minecraft:monument` (vanilla ID; `ocean_monument` is not a structure ID).
Full suite green with evidence gating: 67 tests, 0 failed, 1 skipped (golden
regeneration gate). Evidence: `docs/evidence/ocean/`.

### Phase 9.4 — Structures and spawn [v3] (completed; structure matrix PARTIAL)
- [x] **9.4.1** v3 stronghold selection: three fixed starts in 120° sectors, spawn-distance ≥150 (2500) / ≥300 (≥4000), pairwise ≥250, main-island land preferred; written as `omniisle:fixed_multi` (extends `RandomSpreadStructurePlacement`, visible to `/locate` and Eyes of Ender)
- [x] **9.4.2** Real-server full sweep: NATURAL/ROUND/LONG/CRESCENT/ARCHIPELAGO × sizes 2500/4000/8000 × seed 101 = 15 configs × 20 structure rows; **15/45 full configs (PARTIAL)**; 300/300 rows found and locate_ok
- [x] **9.4.3** Real-server stronghold-only sweep on all 45 configs (seeds 101/202/303): 135/135 starts found, locate matches the target chunk, on land / within 112 blocks, Eyes of Ender target a start; **45/45 configs pass**
- [x] **9.4.4** v3 village/trial-chamber spacing scales with size and the structure-density slider (`min(1, size/4000)`, separation < spacing); headless `structure_scaling.csv`
- [x] **9.4.5** Spawn evidence on 45 configs: 45/45 marker x/z match, land and dry; 42/45 fully dry ray probes to 64 blocks; v3 spawn plateau keeps the marker dry
- [x] **9.4.6** v3 relaxed pyramids bypass the vanilla 21×21 sea-level gate; v1/v2 packs untouched (I1); legacy `has_structure` tags stay single-biome (review regression fixed and tested)

Measured: stronghold spawn distances 526.0–551.9 (2500, requirement 150), 868.4–875.4
(4000) and 818.8–1193.6 (8000, requirement 300); minimum mutual distance 273.9
(threshold 250); all 135 starts on land; 45/45 eyes target a start. Full-sweep 300/300
structure rows found. Spawn: 45/45 match/land/dry. Evidence:
`docs/evidence/structures/`, `docs/evidence/spawn/`.

### Phase 9.5 — Theme-aware climate and family borders (completed)
- [x] **9.5.1** Explicit per-theme temperature spans (`ALL`/`STANDARD` 0.90, `MYSTIC` 0.44, `NORDIC` 0.32, `DESERT`/`TROPICAL` 0.28) and `gradientAmplitude = 0.5 · span · clamp01(strength)`; default strength 0.5; `0.5 · span · s` equals the old `maxSpread · s`, so v1/v2/v3 temperatures are unchanged (I1)
- [x] **9.5.2** Rejected the plan's ±0.75 amplitude: it would clamp most of an ALL island at both extremes; the measured peak is ±0.45
- [x] **9.5.3** Kept the existing ±0.04 temperature / ±0.032 humidity border jitter (8 % of the neutral axis) for `generation_version ≥ 2`; replaced the vacuous 6.3 run counter with a real border-chain + 4-block line-fit measurement
- [x] **9.5.4** Slope-aware shore interplay stays in 9.2; no duplicate work
- [x] **9.5.5** Evidence: family fractions for six themes × off/on/max (CSV), 6 × 3 montage PNG, gradient-delta CSV, straight-border CSV; README with measured Results

Measured: gradient south−north differential at strength 0.5 is exactly 2 × amplitude for
every theme (0.450 ALL/STANDARD, 0.220 MYSTIC, 0.160 NORDIC, 0.140 DESERT/TROPICAL;
delta error 0.000). Longest straight family border on v3 is **230 blocks** (limit 400;
TROPICAL, gradient off, vertical at z=1100); v2 measures 130 blocks. ALL and STANDARD
share the full snowy→desert palette and therefore identical fractions; at default
strength NORDIC stays 71.9 % snowy_plains+taiga (91.4 % off), ALL gains 2.5 %
snowy_plains+taiga (up to 21.5 % at strength 1.0) and 12.1 % warm families. Evidence:
`docs/evidence/climate/`.

### Phase 9.6 — Editor tools, presets, seed UX, inspector, PNG, layout (completed; screenshots PARTIAL)
- [x] **9.6.1** Fill/Line/Rectangle/Ellipse on land and height layers, Shift square/circle, fill/outline toggle; symmetry (X, Z, 4-way) preserved and 6-way/8-way radial added
- [x] **9.6.2** Zoom/Pan: normalized `PreviewViewport`, wheel zoom at cursor 1×–8×, middle-drag / Space+left-drag pan, 16-px grid overlay, zoom reset
- [x] **9.6.3** Seven new seeded presets (Atoll, Volcano, Fjords, Twin Islands, Inland Sea, Horseshoe Bay, Ridge Island); all 12 presets × 20 seeds pass land 12–55 %, main component ≥ 70 % (except Archipelago/Twin) and border checks
- [x] **9.6.4** Seed UX: shape/biome seed fields, dice buttons, copy both seeds
- [x] **9.6.5** Inspector: biome, X/Z + surface elevation, land/water + signed coast distance + islet status, height tier, shore outcome
- [x] **9.6.6** PNG: 5-map export (mask, height, biomes, hillshade, shore) and heightmap import with threshold + land quantiles (25/50/75 %)
- [x] **9.6.7** Six tabs (Shape/Terrain/Climate/Biomes/Structures/Tools), per-tab widget visibility, compact layout down to 480×270; compact `BiomeSelectionScreen` buttons retained
- [x] **9.6.8** `/omniisle info` and Advanced generation v1/v2/v3 selector (default v3) present
- [x] **9.6.9** Undo/redo covers land, height, spawn, resolution and every tool; `PreviewTextureSlot` guarantees one release per registration
- [ ] **9.6.10** Screenshots at GUI scales 1–4 — **NOT DONE**; see `UI_CHECKLIST.md` and `docs/screens/README.md`

Measured: 12 presets × 20 seeds pass (observed land fraction 13.5–40.1 %, largest
component 100 %, 0 border pixels). Radial symmetry required a fix: stamping a disk at
rotated centers left rasterization gaps (measured `(126,34) → (164,91)` missing for
6-way); painting the forward orbit of every pixel is now exactly closed. PNG export
writes 5/5 files; shore palette on ROUND at density 1.0 is 37.85 % land, 61.33 % water,
0.62 % sand, 0.20 % stony. Equal-quartile heightmap import maps 0/85/170/255 to
tiers 0/1/2/3 exactly; flat maps import as tier 1. Layout content bottom/panel
bottom/Done bottom at 480×270 = 216/222/250. Texture lifecycle: 50/50
registrations/releases. Evidence: `docs/evidence/editor/`, `UI_CHECKLIST.md`.
