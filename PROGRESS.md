# OmniIsle 0.8.1 — Implementation Progress Tracker

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
- [x] **9.1.6** Ancient City tag/placement audited (surface biome can host it); v3 now disables the city when Deep Dark is not in the enabled biome palette
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

Measured (defaults): sand 22.7 %, stony 4.1 %, none 73.3 %; longest run 384.3 blocks
(<500), 0 run failures; density 0→0 %, 1→72.4 % monotone; bays/headlands sand ratio
3.98; widest line-fit-straight border 56 blocks (longest border 156.2 blocks, 7.1 block
deviation). Evidence: `docs/evidence/shore/`.

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
- [x] **9.4.7** v3 ancient city is disabled when `deep_dark` is not in the enabled biome palette; `Phase9StructureTest` asserts the pack placement flag flips with the palette, v1/v2 tags unchanged

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

### Phase 9.7 — Relief, coast, rivers, dynamic biome, dedicated server (completed)
- [x] **9.7.1** Relief on ROUND 4000 seeds 1–5: 93–107 distinct local maxima on every seed (requirement: ≥2 on ≥3/5); max elevation 163.22–163.25; no max-height plateau over 150 blocks (longest plateau row 120, connected component span 48–96, area diameter 46.9–60.6); tier-3 painting boosts the peak by +122.4 blocks (requirement ≥40); max 4-block surface slope 2.010–2.551, below the 2.7 budget (vanilla 1.8 × 1.5)
- [x] **9.7.2** Coast fidelity on 5 presets × 3 seeds × sizes 2500/4000: worst mean 0.2955 px (limit 0.6), worst max 1.5778 px (limit 2), 0 failures; the threshold is now enforced at **both** sizes, not only 4000
- [x] **9.7.3** The 2500 outlier that the old 4000-only gate missed (ARCHIPELAGO seed 101, 2.5091 px) is fixed by scaling v3 coast warp quadratically below 4000 (`0.5 · (blocksPerPixel/28.3333)²`): 2500 max is now 1.3579 px, 4000/8000 warp is unchanged
- [x] **9.7.4** Rivers are disabled for configs without `river_density` (legacy codec and share code both default 0.0); v3 density 0.45 produces 10 river-mouth cells in the mouth test
- [x] **9.7.5** v3 river threshold follows the observed drainage (v2 threshold 556 vs v3 ≈96 at density 0.45) so channels reach the sea; the ocean-side carve crosses the waterline instead of stepping at the land/ocean branch
- [x] **9.7.6** River-mouth sampling at 10,000 points: 2,799 below sea, 1,618 carved; max 1-block continentalness delta 0.024772 (bank budget 0.035), max surface slope 1.958 (<2.7); 10/10 gravity-descended waterline crossings, max crossing delta 0.021143 (<0.035). No floating-water or bank-step artifact in the sampled mouths
- [x] **9.7.7** Dynamic biome registry fixture loaded through the real `RegistryDataLoader`: 2 custom biomes appear in the biome list under `SHORE` and `CAVE` with their pack metadata
- [x] **9.7.8** Dedicated-server config flow: `preLaunch` writes `omniisle-generated.zip` before `WorldLoader` scans packs; a fresh boot enables `file/omniisle-generated.zip (world)`, `omniisle info` reports v3 / 192 / bbox 5440 / rivers 45 %, and the v3 spawn is applied at 14,128,-1062
- [x] **9.7.9** Ocean continuity measurement normalizes by the actual SDF change along oblique rays; bank/waterline budgets are derived analytically instead of using an arbitrary per-ray threshold

Measured: relief peaks 93–107, plateau component ≤96 blocks, tier-3 +122.4; coast
worst 0.2955/1.5778 px at 4000 and 0.2462/1.3579 px at 2500; rivers max delta
0.024772 and max slope 1.958; dynamic biome list has 2/2 entries; server first boot
uses the generated pack (log order: preLaunch export 21:58:12 → world creation
21:58:19 → advancements 21:58:20 → Done 21:58:22 → v3 spawn 21:58:23). Evidence:
`docs/evidence/relief/`, `docs/evidence/coast/`, `docs/evidence/rivers/`,
`docs/evidence/dynamic_biome/`, `docs/evidence/server/`.

### Phase 9.8 — Spec, changelog, README, v2 golden, final performance (completed)
- [x] **9.8.1** `OMNIISLE_AI_SPEC.md` rewritten to 0.7.0: v1/v2/v3 pipeline, class roles (`ShoreField`, `SpawnPlateauDensity`, `RelaxedPyramids`, `FixedMultiStructurePlacement`, `OmniIslePreLaunch`, mixins), JSON schema for `generation_version` 1/2/3 incl. `shore`, `islet_density`, `river_density`, `height_layer` (base64 JSON, no `.bin`), `spawn`; SDF/relief/shore/ocean/islet/climate/structure/spawn formulas; invariants I1–I7
- [x] **9.8.2** False claims corrected: `minecraft:cache` is real in 26.3 but does not allocate a 2D slice (column reuse comes from `domainAxes()` + `sampleVolume`); spawn initialization sets respawn data, it does not teleport. `CHANGELOG.md` has `[0.7.0+mc26.3-fabric]`; README covers v1/v2/v3 compatibility, 18 structures, shore/ocean/editor features, before/after shore images, final perf
- [x] **9.8.3** Independent v2 golden from detached worktree of 0.6.0 commit `7a5174d`: 30 configs × 1500 points × 4 doubles (180 000 values), sha256 `abb4bb1b…b96ee`; `GoldenV2Test` exact bit equality, current code matches
- [x] **9.8.4** `OmniBiomeSource` per-thread 512-slot column cache removes per-Y slope/distance/shore recomputation; `BiomeSourceV2V3RegressionTest` locks 29 282 v2/v3 lookups (v2 `13456651134697231961`, v3 `7583129763709705084` unsigned) and passes; pre/post output hashes identical
- [x] **9.8.5** Real dedicated-server timing: 25×25 inland chunks, fresh world/run, RCON forceload + 25 row probes, 3 interleaved rounds. Medians v1 14.891 s / v2 14.231 s (−4.43 %) / v3 14.557 s (−2.24 %); worst-case +2.54 %/+6.71 % vs fastest v1 — inside +15 %
- [x] **9.8.6** Offshore diagnostic (+22.0 %/+38.7 %) separated from the verdict; `ModernTerrainV2` cache experiment reverted (15.128/15.081 s vs 15.17 s, no gain); sequential-run drift documented
- [x] **9.8.7** Final `./gradlew build` green: 97 tests, 0 failures, 2 assumption-gated skips; jar `OmniIsle-26.3-Fabric-0.7.0+mc26.3.jar`; mirrored to PrismLauncher mods and `~/Downloads`
- [x] **9.8.8** Final report follows Section 11, preserving structure matrix PARTIAL 15/45 and 9.6 screenshots NOT DONE

Measured: v2 golden 180 000/180 000 exact; biome cache medians v2 19.432 → 15.251 s and
v3 20.534 → 14.209 s; final inland medians v1 14.891 / v2 14.231 / v3 14.557 s; build
97 tests, 0 failures. Evidence: `docs/evidence/perf/`, `FINDINGS.md` §15, `DECISIONS.md`
034–038, `src/test/resources/golden/v2/`.

### Phase 10 — Biome frequency weights, 0–400 % (completed; chunk-gen perf UNVERIFIED; screenshots NOT DONE)
- [x] **10.1** Optional `biome_share` (`weights`, `bias`, `achieved`, `target`, `feasible`) nested in the biome-source `ThemeShore` codec; absent field, explicit all-100 and neutral exports stay on the untouched legacy path. I1 hash `2951624326361821806` for absent and explicit-100 (test now pins the active density function around the hash); goldens v1/v2/v3 green; no new `generation_version`
- [x] **10.2** Side-neutral weighted selector `BiomeShare.select`: preferred family prior 1, neighbouring family prior `exp(−max(0, d − d_pref)/0.22)`, times `exp(bias)`; climate cap `R_max = 0.62`; weight 0 removes the candidate; uniform factors short-circuit to the legacy picker
- [x] **10.3** `BiomeShareCalibrator`: fixed 64×64 mask-land grid, shore band/rivers/terrain-driven families excluded, baseline `s_f` with zero bias, target `s_f·w_f/100 / Σ(s_g·w_g/100)`, log-ratio damping 0.7, ≤24 iterations, ≤1 pp stop, mean bias 0, bias clamp ±8; identical code in the editor and `WorldPackBuilder.export`; 45/45 configurations feasible with max error 1.88 pp (`docs/evidence/phase10/calibration.csv`)
- [x] **10.4** Editor UI: new drafts default to theme ALL with every biome enabled at 100; land rows have a 0–400 step-5 percent field with −/+, click-to-type and row reset; read-only actual % (green ≤3 pp, amber ≤6 pp, red beyond, grey terrain); header Reset all / Disable selected / + All / − None; typing 0 unticks, ticking restores 100; terrain-driven rows read-only; 150 ms recalibration debounce off the render thread; infeasible warning
- [x] **10.5** Undo/redo covers weights; share codes and exported presets round-trip weights/bias/achieved/target; `/omniisle info` prints weights and achieved shares; `/omniisle debug biome` renders through the real weighted source; `en_us` + `ru_ru` keys
- [x] **10.6** Ten Phase-10 tests green (`Phase10BiomeShareTest`): 107-test suite, 0 failures, 2 assumption-gated skips; preview/real parity 9836/9836 = 100.00 %; 8-thread determinism; 20,000-sample coherence; monotonic sweep; codec/share/preset/undo/bad data; UI logic
- [ ] **10.7** Real 25×25 chunk-generation timing with non-neutral weights vs all-100 — **UNVERIFIED**; only the biome-lookup benchmark ran (median 7.457 ms weighted vs 8.623 ms neutral, −13.52 %, inside the +3 % gate, but not full chunk generation)
- [ ] **10.8** Panel screenshots at GUI scales 2 and 4 — **NOT DONE**; the test run is headless and no client session was captured

Measured: monotonic cherry_grove 0 → 0.00 %, 50 → 20.13 %, 100 → 32.93 %,
200 → 48.98 %, 400 → 66.10 %; coherence 20,000 extreme samples, 9 beyond `R_max`,
all 9 with no closer alternative (max distance 1.3717); calibration 45/45 feasible,
max error 1.88 pp, 163–1358 samples, 3–24 iterations. Evidence:
`docs/evidence/phase10/`, `FINDINGS.md` §16, `DECISIONS.md` 039–042.

### Phase 10.5 — coherent weighted tickets, preset safety, 0.8.1 (completed)
- [x] **10.5.1** Replaced the 0.8.0 per-block SplitMix weighted ticket with a family-independent 3-octave fBm at 420-block scale plus a static 1024-bin empirical CDF (65,536 fixed samples) and a per-column cache in `OmniBiomeSource.ColumnCache`. All-100/absent remains bit-identical; no new `generation_version`. `WeightedTicketTest` 10.5.1/10.5.2: adjacency max step 0.01013, mean 0.00145, range [0.0000, 0.9998], histogram deviation 1.06 % over 131,072 samples; LUT monotone
- [x] **10.5.2** Reproduced the reported Cherry-45 draft: NATURAL/ALL/4000, shape seed `0x306d956984a4bf25`, biome seed `+5`, weight 45. Calibration target 17.319 %, achieved 17.303 %, held-out real-source 17.817 % (0.498 pp, 1,869 columns, worst target ≥0.5 % 0.92 pp). 45/45 calibration configs still feasible, max 0.986 pp, mean 0.726 pp (`docs/evidence/phase10/calibration*.csv`, `heldout_shares.csv`)
- [x] **10.5.3** Speckle regression `previewHasNoSpeckleAtCherry45`: island preview 192×192 agreement 0.191→0.674 (isolated 49.1 %→5.1 %); 1-block 192×128 window agreement 0.217→0.994 and isolated 43.9 %→0.0 %. Evidence: `cherry45_before_after.png`, `cherry45_metrics.csv`
- [x] **10.5.4** `WorldPackBuilder.validatePreset` decodes the generated preset through the live `RegistryOps` context before the editor enables it; failures show `omniisle.editor.error.preset` and keep Done enabled. `Phase10PresetDecodeTest` 10.11/10.12: full `RegistryDataLoader.WORLD_REGISTRIES` decode of a valid generated pack (Cherry weight 45, non-empty bias) and localized rejection of a corrupt zip
- [x] **10.5.5** Fresh 0.8.1 dedicated server with the exact draft: pre-launch pack export, `Done (1.564s)`, spawn on land, `omniisle info` v3/calibrated with `cherry_grove=17.3%`, `omniisle verify` samples, 512 forced chunks (27 region files, 29 MB), 0 errors/exceptions, clean stop, no new crash report. Client crash remains **UNVERIFIED** (no crash artifacts; `mcopt-metal 0.3.0-alpha.2` is the environmental suspect; no Prism mods modified). Evidence: `docs/evidence/phase10/server_smoke.txt`
- [x] **10.5.6** Full `./gradlew build` green: **113 tests, 0 failures, 0 errors, 2 assumption-gated skips**; jar `build/libs/OmniIsle-26.3-Fabric-0.8.1+mc26.3.jar`. Chunk-generation perf with weights remains **UNVERIFIED**; GUI 2/4 screenshots remain **NOT DONE**; Phase 9 structure matrix remains PARTIAL 15/45

Measured: calibration 45/45 feasible, max 0.986 pp; held-out Cherry 17.817 % vs target
17.319 % (0.498 pp); coherent window agreement 0.9938 vs 0.2175 and isolated 0.0000 vs
0.4385; lookup benchmark −3.58 % (weighted faster; CPU-time median). Evidence:
`docs/evidence/phase10/`, `FINDINGS.md` §16.1, `DECISIONS.md` 043–046.
