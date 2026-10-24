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
