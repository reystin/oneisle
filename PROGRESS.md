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
- [ ] **3.1** Soft ocean floor at -0.80
- [ ] **3.2** Continuous seabed noise (weight = 0 at coast)
- [ ] **3.3** Guaranteed deep band for Ocean Monuments
- [ ] **3.4** Satellite islets generator with `islet_density` slider

### Phase 4 — Inland Relief (Stop the Cone) [v2]
- [ ] **4.1** Procedural ridged mountain spines with tanh soft saturation
- [ ] **4.2** 4-tier height layer with blurred sampling and Base64 packing
- [ ] **4.3** Hillshade lighting in preview (NW light 315°, 45° altitude)

### Phase 5 — Structures & Spawn
- [ ] **5.1** Automated structure placement verification harness
- [ ] **5.2** Stronghold distance scaling for island radius
- [ ] **5.3** Relaxed Ancient City & Ocean Monument placement fallbacks
- [ ] **5.4** Largest-SDF inland spawn selector & editor gold crosshair marker

### Phase 6 — Climate & Biomes
- [ ] **6.1** `climate_scale` slider (384-1536) in editor
- [ ] **6.2** Latitudinal temperature gradient (N-S) with theme-aware compression
- [ ] **6.3** Noise-jittered border blend
- [ ] **6.4** Server/preview parity test (≥ 97% agreement)
- [ ] **6.5** Slope-based stony shore vs sandy beach

### Phase 7 — Editor Features
- [ ] **7.1** PNG mask import/export
- [ ] **7.2** Presets & share codes (`OMNI2-...`)
- [ ] **7.3** Advanced drawing tools: Fill, Symmetry, Zoom/Pan, Grid
- [ ] **7.4** New procedural presets (Atoll, Volcano, Fjords, Twin Islands, Inland Sea)
- [ ] **7.5** Optional higher resolution: 192 / 256 / 384
- [ ] **7.6** Seed text fields & copy buttons
- [ ] **7.7** Inspector hover tooltip upgrade

### Phase 8 — Stretch Goals
- [ ] **8.1** Physically carved rivers & lakes
- [ ] **8.2** Registry-driven biome list
- [ ] **8.3** World border option
- [ ] **8.4** `/omniisle debug` visual exporters
