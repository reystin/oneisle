# OmniIsle 0.6.0 — Architectural Findings & Verifications

> **Historical record.** Findings collected under the OmniIsle name before the 1.0.0 rebrand.
> Package paths, IDs and version labels in the sections below are historical. Section 22 records
> the OneIsle identity verification.

This document records the empirical verification of Minecraft 26.3 worldgen internals, Loom cache decompilations, and existing OmniIsle code as required by Phase 0 of `OMNIISLE_IMPROVEMENT_PROMPT.md`.

---

## 1. [VERIFY] EDT Implementation in `IslandMask.java`
* **Finding:** `IslandMask.java` (lines 120–173) **already implements the exact $O(N)$ 1D separable parabolic lower envelope algorithm** by Pedro F. Felzenszwalb and Daniel P. Huttenlocher (2012).
* **Details:**
  - Method `edt1d(double[] input, double[] output, int size)` computes the upper envelope of parabolas with vertices `(q, input[q])`.
  - It is **not** an 8-neighbor chamfer transform.
  - The distance values at integer grid coordinates are mathematically exact squared Euclidean distances ($(\Delta x)^2 + (\Delta z)^2$).
* **Root Cause of "Octagons" and "Terraces":**
  - Not an inaccurate distance transform algorithm.
  - Caused by:
    1. **Coarse grid quantization:** At island size 4000, 1 pixel = 28.33 blocks; at 8000, 1 pixel = 56.66 blocks. The coast transition width (110–320 blocks) spans only 2–7 discrete grid cells.
    2. **Linear interpolation:** Bilinear filtering across large cells produces visible gradient kinks (slope discontinuities) along cell boundaries.
    3. **No sub-pixel domain distortion:** Without domain warping, straight pixel edges translate directly into straight 28-block polygonal coastline edges.

---

## 2. [VERIFY] How Continentalness is Wired & Noise Router Entries
* **Finding:**
  - In `src/main/resources/data/omniisle/worldgen/noise_settings/omni_terrain.json` (lines 111–120):
    ```json
    "noise_router": {
      "chunk_surface_level": "minecraft:overworld/chunk_surface_level",
      "continents": "omniisle:overworld/continents",
      "depth": "omniisle:overworld/depth",
      "erosion": "minecraft:overworld/erosion",
      "final_density": "omniisle:overworld/final_density",
      "ridges": "minecraft:overworld/ridges",
      "temperature": "minecraft:overworld/temperature",
      "vegetation": "minecraft:overworld/vegetation"
    }
    ```
  - `WorldPackBuilder.java` writes `data/omniisle/worldgen/density_function/overworld/continents.json`.
  - When Minecraft evaluates `"continents"`, it resolves to `omniisle:overworld/continents` (our generated file).
* **Can we override `erosion`?**
  - **YES.** In `noise_router`, `"erosion"` is currently mapped to `"minecraft:overworld/erosion"`.
  - We can override it either by mapping `"erosion": "omniisle:overworld/erosion"` or providing a density function in the generated pack. This enables Phase 4's Height Layer / mountain erosion modulation.

---

## 3. [VERIFY] Cache Wrapper Type in Minecraft 26.3
* **Finding:** `"type": "minecraft:cache"` **IS the valid and correct type in Minecraft 26.3.**
* **Details:**
  - Inspection of `net/minecraft/world/level/levelgen/densityfunction/DensityFunctions.class` confirmed:
    `register("cache", CacheFunction.CODEC)` registers `CacheFunction` under `"minecraft:cache"`.
  - The older 1.18/1.19 type names (`flat_cache`, `cache_2d`, `cache_once`, `cache_all_in_cell`) were consolidated into the unified `CacheFunction`.
  - The caching strategy is automatically determined by `DensityFunctionCompiler.class` via `domainAxes()`:
    In `IslandDensityFunction.java`:
    ```java
    @Override
    public int domainAxes() {
        return AXIS_X | AXIS_Z;
    }
    ```
    **Addendum (Phase 9.8):** the earlier claim that omitting `AXIS_Y` makes
    `minecraft:cache` allocate a 2D column slice is wrong. `javap` of the 26.3 merged
    jar shows `CacheFunction` (`op/CacheFunction`, `register("cache", …)`) and
    `SamplerContext$CacheCell`, whose cached state is a single packed 3D `valueKey`,
    not a 2D buffer. Column reuse comes from our density function itself:
    `domainAxes() = AXIS_X|AXIS_Z`, `sampleVolume` loops only XZ and calls
    `buffer.setRange(baseIdx, sizeY, value)`, and `sampleValue` ignores Y. The cache
    type is real and is kept in the generated pack; only the 2D-slice claim is
    withdrawn (Decision 037). Evidence: `IslandDensityFunctionTest` (every Y in a
    column equals the first value and `sampleValue`).

---

## 4. [VERIFY] Vanilla Continentalness Thresholds & Saturation
* **Finding:**
  - In `offset.json` (spline for `omniisle:overworld/continents`):
    - Locations: `[-1.1, -1.0, -0.51, -0.44, -0.18, -0.16, -0.15, -0.1, 0.25, 1.0]`
    - $c \le -1.0$: Deep ocean floor (offset $-0.92$)
    - $c = -0.51$: Deep ocean transition (offset $-0.222$)
    - $c = -0.18$: Near coast (offset $-0.12$)
    - $c = -0.15$: Shoreline / beach shelf start
    - $c = 0.25$: Mid-inland rolling terrain
    - $c = 1.0$: Alpine mountain heights (offsets up to $+1.50$)
* **Saturation at 0.70:**
  - Formula: $\text{continentalness} = -0.08 + \text{worldDistance} \times \frac{0.70}{\text{coastWidth}}$
  - For `BALANCED` (`coastWidth = 192.0`):
    $$0.70 = -0.08 + \text{worldDistance} \times \frac{0.70}{192.0} \implies \text{worldDistance} \approx 213.9 \text{ blocks}$$
  - **Empirical confirmation:** Any land beyond $\approx 214$ blocks from the coast hits the hard clamp $0.70$. On a 4000–8000 block island, over 90% of the interior land area is locked at identical continentalness $0.70$, producing an unnatural flat plateau / monolithic cone.

---

## 5. [VERIFY] GLFW Mouse Constants in 26.3
* **Finding:**
  - In GLFW specifications (`glfw3.h`) and Mojang `InputConstants`:
    - `InputConstants.MOUSE_BUTTON_LEFT = 0` (GLFW button 0)
    - `InputConstants.MOUSE_BUTTON_RIGHT = 1` (GLFW button 1)
    - `InputConstants.MOUSE_BUTTON_MIDDLE = 2` (GLFW button 2)
  - Code strictly uses `InputConstants.MOUSE_BUTTON_LEFT` and `InputConstants.MOUSE_BUTTON_RIGHT`. No raw integer literals are used.

---

## 6. [VERIFY] Ocean vs. Deep Ocean, Deep Dark & Structures
* **Finding:**
  - In `OmniBiomeSource.java`:
    - Ocean boundary: $\text{continentalness} < -0.30$.
    - Deep Ocean boundary: $\text{continentalness} < -0.58$.
    - Deep Dark: Triggered below $Y < 0$ when `fbm(x / 520.0, z / 520.0, saltedSeed(biomeSeed, 977)) > 0.02`.
  - In `WorldPackBuilder.java`:
    - Structures write dynamic `has_structure/<tag>.json` tags matching the sampled biome, but `locateStructure()` requires `accepted` biomes in the sample grid.
    - On a 2500 block island, if mountain peaks are absent, `ancient_city` can fail if not relaxed to any deep dark below $Y=0$.

---

## 8. [VERIFY] Inland Relief & Mountain Headroom
* **Finding:**
  - In 0.5.0, `base = -0.08 + d * 0.70 / coastWidth` was hard-clamped at `0.70`. Because $d / coastWidth$ exceeded $1.11$ within ~210 blocks of the shore, all island centers were flat cones clamped at `0.70`.
  - Adding a linear ridge term without compressing the base caused `tanh` soft saturation to stick at 0.95 across thousands of blocks.
  - Compressing the coastal rise via `base = -0.08 + 0.48 * tanh(x * 1.25)` ensures the base levels off at ~0.40 inland, providing ample headroom for both mountain ridge spines (±0.22) and user-painted height tiers (-0.15 to +0.40).
  - Multi-octave ridged noise generates multiple winding mountain spines and peaks across the island with zero flat plateaus.
  - A 7-tap binomial filter ([1, 6, 15, 20, 15, 6, 1] / 64) across the 4-tier height layer guarantees $C^1$ smooth transitions with max slopes well below the vanilla mountain limit.


---

## 9. [VERIFY] Phase 9.2 — v3 Shoreline Field and Acceptance

**Finding (old ribbon cause, confirmed):**
- `OmniBiomeSource` v1/v2 resolved shore on `continentalness < 0.02`, a constant-width SDF band (the 9.1 audit measured shore on 100 % of `d = 0` contour samples). In v3 the biome is selected from `ShoreField.outcome(d, x, z, slope, bay, settings, seed)` and the `continentalness` gate is no longer used for shore selection.
- v3 thresholds: `d` from `intertidalLimit(x,z)` (noise, −18…0) to local `inlandWidth` (≤ 29.9 by construction, hard guard `MAX_INLAND = 34`); sand and stony share the same pocket; `none` is the default.

**Measured acceptance (5 presets × 3 seeds × sizes 2500/4000, defaults; 30 islands, 61,440 contour points sampled 6 blocks inland):**
- sand 0.226807, stony 0.040625, none 0.732568 (targets 15–35 %, 3–10 %, ≥55 %).
- Run constraints: 0 failures; longest run 384.3 blocks (≤500), largest median run 21.8 (≤200), 14–110 runs per island (≥10); minimum width CV 0.678 (≥0.35).
- Density sweep: 0.0→0 %, 0.2→5.13 %, 0.4→51.61 %, 0.6→71.97 %, 0.8→72.36 %, 1.0→72.36 % (non-decreasing, 70–90 % at 1.0).
- Bay/headland: 152 bay points sand 0.3355 vs 237 headland points sand 0.0844, ratio 3.98 (≥2 required).
- Preview/server parity: 18,403/18,403 = 100 %, 1,016 samples in the shore zone.
- Border line-fit (tolerance 2.5 blocks): longest straight segment 56 blocks; longest border 156.2 blocks with 7.127 max deviation / 3.504 RMS (curved, not straight).
- Continuity: max |Δc| = 0.0109 per block near the coast (<0.015), including v3 relief easing.
- Before/after PNGs: uniform v1/v2 ribbon vs v3 pockets for NATURAL, ROUND, CRESCENT.

**Editor/config wiring:**
- Climate tab adds Beaches (0–100 %), Shore variety (0–100 %) and Rocky shores checkbox; each calls `invalidatePreview()`.
- `shore` is serialized in both `continents.json` and the biome-source JSON for v3 only. `v3SettingsRoundTripAndPack` verifies draft copy, share code, and generated pack values headlessly.
- `/omniisle debug shore` writes a mask (sand `#E8D9A0`, stony `#8C8C8C`, none/ocean/land as terrain colours); `/omniisle debug biome` now colours `beach`/`snowy_beach`/`stony_shore` explicitly so the pattern is visible.

**Limitations:**
- The editor GUI and the in-game debug command were not launched in this environment (no client run); their wiring is covered by compilation, the headless round-trip test and shared `ShoreField` logic only. The final report marks them PARTIAL.

---

## 10. [VERIFY] Phase 9.3 — 26.3 Ocean Depth, `/locate`, and Pack Metadata

**26.3 source behavior (checked with `javap` against `minecraft-merged.jar`, project Loom cache):**
- `ChunkGenerator#findNearestMapStructure(ServerLevel, HolderSet, BlockPos, int, boolean)` groups structures by `StructurePlacement` and then only handles two cases: `instanceof ConcentricRingsStructurePlacement` and a cast to `RandomSpreadStructurePlacement` (`checkcast`). Any other `StructurePlacement` subclass is silently skipped. `LocateCommand` passes `maxDistance = 100` chunks. This is why the previous `AbstractSpreadingStructurePlacement` was invisible to `/locate`, and why the fixed start must be at most 100 chunks from the command source.
- `StructureCheck#checkStart` calls `StructurePlacement#applyAdditionalChunkRestrictions(int, int, long)` (bytecode `invokeinterface`) but never calls `isPlacementChunk`. With a spacing-1 placement every candidate chunk therefore reaches `findGenerationPoint`; for monuments that means a 29-block `getBiomesWithin` survey per candidate (~40k candidates), which tripped the 60 s server watchdog. Rejecting non-fixed chunks in `applyAdditionalChunkRestrictions` fixes both cost and correctness.
- `OceanMonumentStructure#findGenerationPoint` samples `biomeResolver.getBiomesWithin(chunk.getBlockX(9), seaLevel, chunk.getBlockZ(9), 29)` and returns empty unless every biome is in `BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING` (all oceans and rivers). A monument can therefore generate even if a few survey samples are shallow ocean, not only deep ocean; the v3 placement deliberately keeps the whole 5×5 chunk neighbourhood below the deep threshold as a stronger guarantee.
- The structure registry id is `minecraft:monument`; `ocean_monument` is the `has_structure` biome tag name. `/locate structure minecraft:ocean_monument` fails with "no such structure".

**v3 bathymetry measurements (`docs/evidence/ocean/`):**
- 15 configs × 360 rays: `c ∈ [0.4·Wo, 1.0·Wo]` maximum −0.712976, 120,856 samples, 0 above −0.60 (threshold −0.58). Radial CSV: 23,184 rows, `c_min = -1.000000`, coast `c_max = -0.052040`, soft floor respected.
- Islets: 62 land samples across 6 configs at density 0.35, peak `c = 0.2355`, nearest islet coast 611.4 blocks offshore (> `Wo = 400`), max 1-block profile delta 0.04462.
- Headless monument placement (15 configs): centre `c` −0.748…−0.719, 29-block survey maximum ≤ −0.640, all 15 preview biomes are oceans; `ROUND_4000_s303` selects `cold_ocean` because `deep_cold_ocean` is absent from the default palette while `c` is still −0.724. Since Phase 9.4 makes v3 fixed-placement tags presence gates (Decision 020), the test records the preview biome at the chunk instead of the first tag value.
- Real 26.3 server: `/locate structure minecraft:monument` returned the pack chunk in 10/10 configs (511–1000 blocks away); `execute if biome` confirmed `#minecraft:is_deep_ocean` at y = 62/50/40/30 and `#minecraft:is_ocean` at y = 50 at every located monument. One config was additionally probed for prismarine blocks, confirming the monument actually generated.

**Pack metadata:**
- 26.3's pack metadata codec requires `min_format`/`max_format` for formats above 81; emitting only `pack_format: 121` loads but marks the datapack `(incompatible)` in the server log. `min_format = max_format = 121` removes the warning with no version change.

**Limitations:**
- The locate evidence starts the server once per config with a fresh `run/locate` world; the first locate probe for each config may load chunks, but biome probes are taken after a forceload and 2 s wait. The evidence records the actual tag queries rather than inferred biome names.
- RCON locate does not prove every monument generated; only the probed config was block-scanned. The headless 5×5 check plus the vanilla `required_ocean_monument_surrounding` rule are the generation guarantee.

---

## 11. [VERIFY] Phase 9.4 — Structures and Spawn on Real 26.3 Servers

**Why the old rings could not pass:** v2 and earlier scaled `minecraft:concentric_rings`
with the island size. Concentric rings choose starts from a ring angle and biome filter,
so on an arbitrary painted mask they can land in water, inside the spawn radius or outside
the 100-chunk `/locate` reach. The 9.1 audit had already shown that "24.12 blocks from
spawn" was a pre-0.6.0 artifact, not a measured stronghold distance.

**v3 fixed starts (`WorldPackBuilder.placeStrongholds`, `FixedMultiStructurePlacement`):**
- Candidate generation walks real land chunks, requires distance from spawn ≥150
  (size 2500) or ≥300 (≥4000), scores `|distance − target| − 40·continentalness` with
  `target = min(1200, max(320, 0.22·size))`, picks one candidate per 120° sector and
  rejects pairs closer than 250 blocks. Main-island land is preferred; shore is a
  fallback. The three chunks are written as an ordered `omniisle:fixed_multi` placement
  with spacing 1.
- 26.3 `ChunkGenerator#findNearestMapStructure` only handles `ConcentricRings` and
  `RandomSpread` placements (section 10), so `fixed_multi` extends the latter; both
  `isPlacementChunk` and `applyAdditionalChunkRestrictions` accept exactly the listed
  chunks, which keeps the locate scan cheap and makes `/locate` and the Eye of Ender
  path see the real starts.

**Measured (real dedicated server, 45 configs, 135 starts):** every start found and
located at the target chunk. Spawn distance 526.0–551.9 (2500; requirement 150),
868.4–875.4 (4000) and 818.8–1193.6 (8000; requirement 300). Minimum pairwise distance
273.9 (requirement 250). 115 starts have their centre on dry land; the other 20 are
4–24 blocks from dry land (limit 112). `omniisle query eye` found a target in 45/45
configs and the target chunk equals one of the three starts. Source: `stronghold_validation.csv`.

**Full 20-structure sweep (15 configs, seed 101):** 300/300 rows `found=true` and
`locate_ok=true`; each of the 17 other structure types is 15/15. This is a PARTIAL
matrix (15/45 configs) by the agreed scope cut; the 30 remaining configs have no claim
about the other 17 structures. Source: `STRUCTURE_REPORT_after_v2.csv`.

**Pyramid gate:** 26.3's `SinglePieceStructure#findGenerationPoint` rejects a start when
the 21×21 footprint's lowest block is below sea level. OmniIsle lowlands can have
footprint corners at y=63 from vanilla ridge/erosion noise that the editor cannot
evaluate. v3 replaces only the two pyramid structure codecs with
`omniisle:relaxed_desert_pyramid` / `omniisle:relaxed_jungle_temple`, reusing the vanilla
pieces, IDs, tags and `afterPlace`; v1/v2 packs keep the vanilla codecs. In the reduced
sweep both pyramids are 15/15, including the NATURAL_8000_101 rows that failed before
the override.

**Deep-dark gate (Phase 9.8):** the 9.1 audit found that a 0.6.0 ancient-city tag was
`deep_dark` plus one surface biome, and the city could be selected on a surface biome
even when the user had disabled the Deep Dark biome. v3 now disables the
`ancient_cities` structure set whenever `deep_dark` is absent from the enabled biome
palette; v1/v2 tag JSON and placement are untouched (I1). `Phase9StructureTest`
exports a v3 pack with and without `deep_dark` and asserts the placement `enabled`
flag flips true → false. The remaining scope caveat: when Deep Dark is enabled but a
particular 16-block sample grid has no deep-dark biome, `locateStructure` still falls
back to a non-deep-dark candidate so the option does not silently disappear.

**Spawn:** the v3 world writes a spawn plateau density wrapper around
`effectiveSpawnPx()` (80-block radius, top y 78), and `SERVER_STARTED` reconciles
`getRespawnData()`. Across 45 fresh worlds the real spawn block equals the editor
marker in x/z 45/45, is land 45/45 and dry 45/45; 42/45 stay dry on all 8 ray probes to
64 blocks, nearest water min 56 blocks, and no coast ray hits non-land within 96 blocks.
Spawn coordinates are seed-independent for non-archipelago presets; ARCHIPELAGO moves
its main component with the shape seed. `spawn_y` is the heightmap at query time and
can differ by a feature (pale oak leaves vs moss carpet) between reruns; the overlap
check compares x/z/biome/land/dry and reports such y differences as Y-NOTEs.

**Legacy regression caught in review:** the first cut of the presence-tag change listed
all enabled biomes for every version. Fixed placements have one candidate chunk, so this
did not move structures, but it changed v1/v2 tag JSON and could have enabled a fixed
structure the old single-biome tag rejected. The final code writes the broad tag only
for v3 and keeps the original single-biome JSON for v1/v2; a test asserts the v1/v2
pyramid tag has one value and the ancient-city tag has `deep_dark` plus one biome.

**Config copy fix:** 0.6.0 `IslandDraft.copy()` dropped `riverDensity` and the world-border
settings. The v3 pack/spawn path copies the draft, so a user's river or border choice could
silently reset between preview and export. `copy()` now carries all three fields, and
`Phase9AuditTest` asserts the round-trip; `docs/evidence/phase9_before/world_border_default.txt`
records the fixed result.

**Limitations:**
- Structure rows come from `/locate` plus `StructureStart` queries after force-loading;
  they prove placement/start data, not a full block scan of every structure. One
  monument was block-probed in 9.3; this sweep does not repeat that for every config.
- The repeatability rerun was dropped with the reduced matrix, so there is no
  repeatability claim. The 15 overlapping full/stronghold-only runs agree on all stable
  spawn fields (`mismatches=0`).
- `/omniisle query structure` matches a start only by its chunk; it is a debug helper
  for the harness and is always queried at the located start chunk.

## 12. [VERIFY] Phase 9.5 — Theme Temperature Spans and Family Borders

**Gradient:** the 9.1 audit was only half right. The gradient already used a per-theme
`maxSpread`, but the code did not expose the theme's palette span and the constants were
implicit. 9.5 re-expresses the same numbers as `ThemeClimate(centre, span, noise)` and
computes `offset = u · 0.5 · span · clamp01(strength)`. Comparing `0.5 · span · s` with
the old `maxSpread · s` gives 0.45/0.22/0.16/0.14 at s=1 for ALL/STANDARD, MYSTIC,
NORDIC and DESERT/TROPICAL, i.e. exactly the pre-9.5 constants, so no v1/v2/v3 world
changes (I1). ALL and STANDARD legitimately share the 0.90 full-palette span; only that
pair aliases. Nordic/Desert/Tropical spans are narrower and now exposed through
`temperatureSpan`/`gradientAmplitude`.

**Measured formula (`climate_gradient_summary_v3.csv`):** for each theme and strength
0/0.5, 128 x positions are sampled at z = ±4000 (outside the island, so `u` saturates)
and the gradient-on minus gradient-off temperature is averaged; noise cancels exactly.
The measured south−north delta equals `2 · gradientAmplitude` at 1e-9 for all 12 rows
(0.450, 0.220, 0.160, 0.140 at strength 0.5). The old ±0.75 suggestion is rejected:
`centre ± 0.75` would leave `[0,1]` for most of an ALL map and clamp both extremes; the
kept formula peaks at ±0.45.

**Family fractions (`climate_family_fractions_v3.csv`, 128² inland samples):** ALL and
STANDARD are identical. At the default 0.5, NORDIC snowy_plains+taiga falls from 91.4 %
(gradient off) to 71.9 %, while ALL rises from 0.0 % to 2.5 % and gains 12.1 % warm
families; at strength 1.0 ALL reaches 21.5 % cold and 15.3 % desert+badlands. Hot themes
move the other way: DESERT desert+badlands 57.0 → 48.3 → 41.5 %, TROPICAL 16.8 → 27.9 →
32.0 %. At strength 1.0 the coldest edge of DESERT/TROPICAL crosses the 0.68 temperature
branch after jitter, producing small temperate fractions (1.5 % / 9.7 %); the editor
default stays 0.5. The fixed mushroom district is 0.74 % everywhere.

**Borders:** `selectLandFamily` applies value noise on a 96-block cell: temperature
±0.04, humidity ±0.032 for `generation_version ≥ 2`. The old 6.3 test had counted a run
but never asserted the requirement. `ClimateBorderMetrics` now traces family changes
across 10-block scanlines and reports the longest chord whose intermediate points stay
within 4 blocks of the chord line (chords capped at 46 points, so any straight run over
400 blocks contains a detectable chord). Across six themes × gradient off/on × both
orientations the longest is **230 blocks** (TROPICAL off, vertical z=1100, x −1000→−770),
and the regenerated v2 measurement is 130 blocks in both orientations. The existing
jitter is therefore sufficient; growing it would alter existing v2/v3 worlds.

**Test:** `Phase9ClimateTest` asserts the spans/amplitudes, the exact on/off differential,
non-empty land fractions, and the ≤400-block border on the shared side-neutral sampler
(`IslandClimate`); `Phase6ClimateTest` 6.3 now measures the v2 sampler instead of
counting a loop with no assertion.

## 13. [VERIFY] Phase 9.6 — Editor tools, radial symmetry, PNG import/export, layout

**Radial symmetry (measured failure then fix):** with `paint(..., SIX_WAY)` stamping a
disk at each rounded rotation of the center, the resulting mask was not closed under
the 60° transform. The test reported `(126,34) → (164,91)` as painted → not painted
for a disk at `(60,40)` on a 192 grid. The cause is that `Math.round(center + d·R(θ))`
is not reversible at pixel edges; a disk around a rotated center does not contain the
exact rotated positions of every pixel in the source disk. Painting the forward orbit
of each source disk pixel (reusable int stamp + int queue) produces a closed set.
The same closure is used for height tiers. 8-way reflection/rotation orbits are also
closed; the test checks every painted pixel's seven transforms.

**Heightmap quantiles:** with land luminance values sorted, `q25 = sorted[count/4-1]`,
`q50 = sorted[count/2-1]`, `q75 = sorted[3·count/4-1]`, and tiers are
`<=q25 → 0`, `<=q50 → 1`, `<=q75 → 2`, else 3. An image with four equal quadrants
0/85/170/255 (192², 9216 pixels each) imports exactly 0/1/2/3 in the quadrant centers.
A flat image (all 120) imports as tier 1 for every pixel. Inverting with threshold 100
keeps dark pixels and rejects bright pixels. Quantiles are computed over land pixels
only; water keeps tier 1.

**PNG export:** `exportAll` now writes 5 files: mask, height, biomes, hillshade, shore.
The ROUND preset at density 1.0/variety 0.5/rocky true produced a 192×192 shore map
with palette-only pixels: 13,953 land (37.85 %), 22,608 water (61.33 %), 228 sand
(0.62 %), 75 stony (0.20 %). The biome map has 25 distinct colours; the hillshade has
224 grey levels. The exported height layer is still a 4-shade visualization
(`tier·85`): water columns are tier 1, so the height PNG is not a standalone land-mask
round trip; the separate mask PNG is the land source. The Phase 7 test was corrected
to assert the exact shade encoding instead of re-importing the height PNG as a mask.

**Layout:** `EditorLayout.compute` is pure. The controls content (8 rows) ends at
`contentY + 7·rowStep + rowH`; the panel interior ends 2 px lower. Checked logical
sizes: 1920×1080 (scale 1, non-compact), 960×540 (scale 2, non-compact), 640×360
(scale 3, non-compact, 144-px preview) and 480×270 (scale 4, compact, 140-px preview).
At 480×270 the content bottom is 216, panel bottom 222, Done button bottom 250.
Below ~270 logical pixels of height the fixed control set cannot fit; this is recorded
as the practical minimum rather than claimed as scale-independent.

**Texture lifecycle:** `PreviewTextureSlot.ensure` releases the old texture before
creating a new one when resolution changes, reuses it when unchanged, and `release`
is idempotent. Unit test: 50 open/close cycles → 50 creates, 50 releases; a 192→256
change → one extra release + create. This verifies accounting, not the GL texture
count in a live client (screenshots/interactive checks are marked UNVERIFIED).

**Verified 26.3 API:** `GuiGraphicsExtractor.blit(RenderPipeline, Identifier, int x,
int y, float u, float v, int width, int height, int uWidth, int vHeight, int textureWidth,
int textureHeight)` is the 12-argument subregion overload used for zoom; the previous
1:1 blit call was replaced so panning/zooming does not require re-uploading the texture.

## 14. [VERIFY] Phase 9.7 — relief, coast, river mouths, dynamic biomes, server startup

**Relief on round islands (seeds 1–5):** `Phase9ReliefTest` samples 101×101 columns at
40-block spacing over a 4000 island. Every seed has 93–107 distinct local maxima
(requirement ≥2 on ≥3/5), max elevation 163.22–163.25, and no flat plateau at the
maximum above 150 blocks: the longest row at max height is 120 blocks, the largest
connected component spans 48–96 blocks and its area diameter is 46.9–60.6 blocks.
Painting the full height layer as tier 3 raises the peak by +122.4 blocks (≥40) and
the largest 4-block surface slope is 2.551 blocks/block; the natural-terrain maximum
is 2.010–2.551. The budget is 2.7 = the 1.8 blocks/block vanilla-mountain reference
already used by the Phase 4 relief test × 1.5. Evidence: `docs/evidence/relief/`.

**Coast fidelity and the 2500 exception:** the generated `c = −0.08` contour is
sampled on an 8-block grid and each point's displacement is
`|mask.sampleSignedDistance| / blocksPerPixel`. The set is 5 presets × 3 seeds × 2
sizes = 30 configs. At size 4000 the worst mean is 0.2955 px and worst max
1.5778 px, both under 0.6/2. The initial gate only asserted size 4000; extending it to
2500 exposed ARCHIPELAGO seed 101 at 2.5091 px. The warp budget was
`0.5 · blocksPerPixel/28.3333` (floor 0.35), which gave 0.35 at 2500; lowering the
floor to 0.25/0.15 changed nothing because the formula produced 0.3125 there. Scaling
quadratically below the 4000 reference (`0.5 · (ratio)²`, floor 0.15) gives 0.195 at
2500, 0.5 at 4000 and the 1.0 clamp at 8000. Measured effect: 2.5091 → 2.2262 →
1.3579 px at 2500, with 4000 unchanged at 0.5. The test now applies the 0.6/2 px
threshold to both sizes: `docs/evidence/coast/coast_displacement.csv`
(`criterion_applies_to_sizes,2500;4000`, failures 0).

**River mouths:** legacy configs without `river_density` decode to 0.0 and
`IslandRivers` stays disabled (verified for both raw JSON and share codes). At density
0.45 the v2 threshold was 556 accumulated cells while the maximum coastal accumulation
measured 192, so no channel reached the sea. The v3-only threshold factor
`0.0004 + (1−density)·0.0040` (≈96 at 0.45) yields 10 mouth cells. The river carve is
also applied on the ocean side of the branch, so crossing the waterline does not step
by the carve amount. 10,000 samples around the mouths: 2,799 below sea level, 1,618
inside a carved channel; maximum 1-block continentalness delta 0.024772 against the
analytic bank budget `0.14·1.5/6 = 0.035`; maximum surface slope 1.958 against 2.7.
Ten gravity-descent probes reach the waterline (10/10), with maximum crossing delta
0.021143. Evidence: `docs/evidence/rivers/`.

**26.3/Fabric startup order (server flow bug):** the first fresh boot with
`config/omniisle.json` had the generated zip in `world/datapacks` but only
`[omniisle (Fabric mod)]` enabled and `omniisle info` reported generation version 1.
The log order shows why: Fabric Loader 0.19.3's `EntrypointPatch` injects
`Hooks.startServer` at the `MinecraftServer` constructor (`javap` of
`net/fabricmc/loader/impl/game/minecraft/patch/EntrypointPatch.class`), while
`net.minecraft.server.Main.main` calls `WorldLoader.load` earlier (bytecode offset
~721 in the mapped class). `SERVER_STARTING` therefore fires after the pack list is
built. A `preLaunch` entrypoint now writes the pack before world load; on the fresh
run the log shows preLaunch export at 21:58:12, `No existing world data` at 21:58:19,
advancements at 21:58:20, `Done (1.589s)` at 21:58:22, and the v3 spawn at
21:58:23. `datapack list enabled` then includes `file/omniisle-generated.zip (world)`,
and `omniisle info` reports Generation Version 3, 192×192, mask bbox 5440 (1.36× the
nominal size), rivers 45 %. Evidence: `docs/evidence/server/`.

**Dynamic biome registry:** a fixture datapack is loaded through the real
`RegistryDataLoader` (`DynamicBiomeRegistryTest`); both custom biomes appear in
`DynamicBiomeRegistry`'s list with the expected category (`custom_beach` → SHORE,
`custom_caves` → CAVE) and the pack metadata is written to
`docs/evidence/dynamic_biome/`.

**Measurement corrections:** ocean continuity is now normalized by the actual
`|Δ signed distance|` (floor 1 block) because a smooth ramp measured along an oblique
ray changes `c` by more per ray block than per normal block; the worst raw value
0.020876 occurred where the shoreline moved 5.57 blocks per ray block. River banks use
the analytic 0.035 budget above rather than an arbitrary per-ray threshold.

## 15. [VERIFY] Phase 9.8 — v2 golden, biome column cache, final performance, spec corrections

**Independent v2 golden snapshot:** `GoldenV2Test` compares current code against a
snapshot generated inside a detached worktree of 0.6.0 commit
`7a5174d6bed14602f309efec138b283d10271137`, not against the current tree. Coverage:
30 configs × 1500 LCG points × 4 metrics (`continentalness`,
`computeSignedDistance(false)`, `signedDistance`, `erosionBias`) = 180 000 values,
compared with `Double.doubleToLongBits` exact equality; all match. Seeds are shape
1337 / biome 4242, rivers 0.35, islets 0.25, sizes 2500 and 8000. Snapshot sha256
`abb4bb1badd53b247759ffb06f313ad5d8ab71deb57ece61771bc55a589b96ee`; provenance is in
`src/test/resources/golden/v2/golden_v2_source.txt`. This also proves the Phase 9.8
biome-cache change did not alter v2 terrain.

**Biome column cache (exactness and effect):** `OmniBiomeSource` sampled four
continentalness neighbours, `signedDistance`, bayness and the shore outcome for every
biome Y quart, although all are Y-independent. A per-thread 512-slot direct-mapped
cache keyed by `(biomeX, biomeZ)` and terrain identity now reuses them. Exactness:
`BiomeSourceV2V3RegressionTest` hashes 29 282 v2/v3 lookup results (ROUND 4000, seed
4242, rivers 0.35, islets 0.25, shore 0.30/0.60 rocky; quart grid −120..120 step 2 ×
y {4,−4}); v2 hash `13456651134697231961` and v3 `7583129763709705084` (unsigned)
match the pre-change values. Inland 625-chunk probes: v2 median 19.432 → 15.251 s,
v3 20.534 → 14.209 s, against a pre-change v1 median 16.785 s. The analogous cache
inside `ModernTerrainV2` measured 15.128/15.081 s vs 15.17 s and was reverted.

**Final dedicated-server timing:** method and full diagnostics are in
`docs/evidence/perf/README.md`. Fresh world per run, same ROUND 4000 / seed 4242
config, only `generation_version` changes; 25×25 inland chunks (x 28–52, z −12..12),
5 forceload strips, 25 `execute if loaded` probes, wall clock to 625/625. Three
interleaved rounds (v1 → v2 → v3, reps 10–12) avoid host drift: a prior sequential
run moved from 14.410/14.196 s to 15.837 s for v1 between rounds. Medians:
v1 14.891 s, v2 14.231 s (−4.43 %), v3 14.557 s (−2.24 %); worst modern run vs
fastest v1 run +2.54 % / +6.71 %, inside the +15 % budget. Offshore rows
(v2 +22.0 %, v3 +38.7 %) are diagnostic only: v1 generates flat ocean there while
v2/v3 generate bathymetry, the deep band and islets.

**Spec corrections:** (1) `minecraft:cache` exists in 26.3 (`CacheFunction`), but it
does not create a 2D column slice; `CacheCell.valueKey` is a packed 3D key and the
column fill comes from our `sampleVolume` (see §3 addendum, Decision 037).
(2) `height_layer` is base64-encoded JSON in the config, not a `height_layer.bin`
file. (3) spawn initialization sets the world respawn data; it does not teleport
players. `OMNIISLE_AI_SPEC.md`, `CHANGELOG.md` and `README.md` now state the actual
0.7.0 behavior.

**Final build:** `./gradlew build` succeeds; 97 tests, 0 failures, 0 errors, 2
conditional skips (`GoldenV1Test.regenerateGoldenIfRequested`,
`Phase9HarnessPacksTest.exportHarnessPacks`, both assumption-gated). Jar:
`build/libs/OmniIsle-26.3-Fabric-0.7.0+mc26.3.jar`, `fabric.mod.json` version
`0.7.0+mc26.3`.

## 16. [VERIFY] Phase 10 — per-biome frequency weights (0.8.0)

**I1 on the new optional field:** `Phase10BiomeShareTest.allHundredIsBitIdentical`
hashes the biome source over 64×64 columns with the field absent and with an explicit
all-100 `biome_share`: both are `2951624326361821806` (the test pins the active
density function, so the fixture hash is stable across test order). A neutral `WorldPackBuilder`
export contains no `biome_share` key, and `hasWeightedShares()` stays false, so the
selector never leaves the legacy path. `GoldenV1Test`, `GoldenV2Test` and
`BiomeSourceV2V3RegressionTest` pass in the same suite. No `generation_version` was
added.

**Selector model:** land candidates are the enabled climate-driven variants with
weight > 0. The preferred family keeps prior 1; another family gets
`exp(−max(0, d − d_pref)/0.22)`; the variant probability is that prior times
`exp(bias)`. Candidates farther than `R_max = 0.62` from the sample climate are
dropped while any candidate lies inside the cap; if none does, the cap is relaxed so
the palette still yields a biome. A deterministic SplitMix-style hash provided the
0.8.0 uniform ticket; it was replaced in 0.8.1 because the per-block hash made every
weighted world noisy (see §16.1). Uniform
factors short-circuit to `legacyEquivalent`, which is why all-100 is exact. Scratch
buffers are per-thread and the hot path allocates nothing. Terrain-driven families
(14/15/16: peaks, hills, mushroom fields) are never selected by the weighted climate
path; their legacy terrain selection is unchanged.

**Calibration:** 64×64 mask-land samples per config; v3 excludes
`signedDistance <= ShoreField.MAX_INLAND`, v1/v2 exclude `c <= 0.06`; rivers and
terrain-driven families are excluded. The zero-bias baseline is measured with the
legacy-equivalent selector, so `s_f` is the current natural share. Targets
`t_f = s_f·w_f/100 / Σ(s_g·w_g/100)`. Initial bias is
`ln(t_f) − ln(max(s_f, 0.5 %))`; each iteration adds
`0.7·(ln t_f − ln max(actual_f, 0.5 %))`, subtracts the mean and clamps to ±8; stop at
1 pp or 24 iterations; feasible iff max error ≤3 pp and every weighted biome is
reachable. 45/45 configurations (5 presets × 3 themes × 3 vectors) are feasible:
samples 163–1358, iterations 3–24, max error 1.88 pp, mean 0.79 pp
(`docs/evidence/phase10/calibration.csv`). `NATURAL`/`NORDIC` with desert at 200 is
flagged infeasible and still returns a valid map.

**Achieved vs target examples** (`calibration_examples.csv`): NATURAL/ALL
cherry_grove 31.645 → 31.214 %, plains at 300 13.796 → 12.958 %; ROUND/NORDIC
snowy_plains at 150 24.565 → 23.962 %, snowy_taiga at 200 23.746 → 24.281 %;
ARCHIPELAGO/TROPICAL mangrove_swamp at 300 46.362 → 45.870 %, bamboo_jungle
22.656 → 23.261 %.

**Acceptance results:** monotonic cherry_grove 0 → 0.00, 50 → 20.13, 100 → 32.93,
200 → 48.98, 400 → 66.10 %; zero weight equals unticking (same preview hash and bias,
re-enable restores 100); 8-thread fingerprints identical; 20,000 extreme samples have
9 selections beyond `R_max`, all 9 with no closer candidate (max distance 1.3717);
preview/real parity 9836/9836 = 100.00 %; codec/share/preset/undo/bad-data round trips
pass; UI logic test passes. The lookup benchmark medians are 8.623 ms neutral vs
7.457 ms weighted (−13.52 %; run noise, but inside the +3 % gate).

**Limitations (historical; weighted chunk timing is measured for 1.0.0 in §23):** the
benchmark covers biome lookups, not full 25×25 chunk generation; real chunk timing with
weights was not rerun for 0.8.0 and is **UNVERIFIED** there. Panel screenshots
at GUI scales 2 and 4 are **NOT DONE** (headless test environment). Percent entry has
click-to-type and −/+, but no drag slider. The Phase 9 structure matrix remains
PARTIAL 15/45 full configs; Phase 9 editor screenshots remain NOT DONE.

**Final build:** see the Phase 10 delivery report; 107 tests, 0 failures, 2
assumption-gated skips (`GoldenV1Test.regenerateGoldenIfRequested`,
`Phase9HarnessPacksTest.exportHarnessPacks`).

### 16.1 [VERIFY] 0.8.1 — coherent weighted ticket, preset preflight, crash status

**Root cause of the reported noise:** 0.8.0's `weightedTicketFor` hashed `(x, z)`
independently for every block. Any non-neutral weight therefore re-rolled the weighted
candidate per block while the legacy all-100 path used a coherent 420-block fBm. The
reported Cherry 45 "did not shrink" because 45 is a relative frequency: the editor's
17.4 % green value is a target, not a disabled budget. `docs/evidence/phase10/
cherry45_before_after.png` shows the actual before/after.

**Ticket fix:** family-independent 3-octave fBm at `x/420, z/420`, salted with
`biomeSeed ^ 7919`, flattened by a static 1024-bin empirical CDF (65,536 fixed samples,
step 97, `TICKET_LUT_SEED = 0x2C9277B5`); `ticketFromRaw` is a piecewise-linear,
allocation-free lookup. The y-independent ticket is stored in
`OmniBiomeSource.ColumnCache`. Uniform factors still short-circuit to the legacy picker,
so all-100/absent is unchanged (I1 hash `2951624326361821806` with the active density
function pinned); no `generation_version` was added. 0.8.0 worlds with non-neutral
weights intentionally generate differently after updating (their old output was the
noise bug); already-generated chunks are not rewritten.

**Ticket tests:** `WeightedTicketTest` 10.5.1 measures adjacency along rows/columns
with step 97 over ±2048: `maxStep = 0.01013`, `meanStep = 0.00145`, range
`[0.0000, 0.9998]`, histogram deviation 1.06 % over 131,072 samples. 10.5.2 verifies
the LUT is monotone, clamps low values to 0 and maps high values to >0.999.

**Cherry-45 receipt:** shape seed `0x306d956984a4bf25`, biome seed `+5` (hidden in the
report; found with a ±32 sweep), NATURAL/ALL/4000, weight 45. Calibration target
17.319 %, achieved 17.303 %; held-out real-source sampling on a 79-grid with a
different pixel offset gives 17.817 % (0.498 pp error, 1,869 land columns); worst
target ≥0.5 % error is 0.92 pp (meadow). Calibration remains 45/45 feasible, max
0.986 pp, mean 0.726 pp. Speckle: the island preview (192×192) agreement rises from
0.1907 to 0.6741 and isolated pixels fall from 49.13 % to 5.12 % (coastal shore/river
pixels included); a representative 1-block 192×128 window rises from 0.2175 to 0.9938
and isolated pixels from 43.85 % to 0.00 %. Evidence:
`docs/evidence/phase10/heldout_shares.csv`, `cherry45_metrics.csv`,
`cherry45_before_after.png`.

**Preset decode and preflight:** `Phase10PresetDecodeTest` 10.11 decodes a generated
zip through `ServerPacksSource` + the full `RegistryDataLoader.WORLD_REGISTRIES`, with
custom codecs registered after `Bootstrap`; the overworld exists and its biome source
carries Cherry weight 45 with a non-empty bias. 10.12 rejects a corrupt zip via
`WorldPackBuilder.validatePreset` with `Generated world preset is not valid JSON`.
`commitDesign` now validates the exported zip through the captured live
`RegistryOps` context before adding/reloading the pack, showing
`omniisle.editor.error.preset` on failure instead of letting world entry crash.

**Crash status:** a fresh 0.8.1 dedicated server with the exact draft reached
`Done (1.564s)`, applied the pack, reported calibrated weights, generated 512 forced
chunks (27 region files, 29 MB) with zero errors/exceptions, and stopped cleanly. There
is no new crash report; the only report in `run/crash-reports/` is the unrelated 0.6.0
watchdog from 2026-10-24. The reported **client crash is UNVERIFIED**: no
crash-report, `hs_err_pid*`, or matching DiagnosticReport exists. The Prism instance
also ships `mcopt-metal 0.3.0-alpha.2`, which remains the leading environmental suspect;
no Prism mods were modified. Evidence: `docs/evidence/phase10/server_smoke.txt`.

**Final build:** 113 tests, 0 failures, 0 errors, 2 assumption-gated skips
(`GoldenV1Test.regenerateGoldenIfRequested`,
`Phase9HarnessPacksTest.exportHarnessPacks`); jar
`build/libs/OmniIsle-26.3-Fabric-0.8.1+mc26.3.jar`. Full 25×25 weighted chunk-gen
timing remains **UNVERIFIED**; GUI 2/4 screenshots remain **NOT DONE**; the Phase 9
structure matrix remains PARTIAL 15/45.

## 17. [VERIFY] Phase 10.6 — reference continent climate, shoreline fix, strict structures (0.8.2)

### 17.1 Measured waterline and dry shelf

v3 `ModernTerrainV2` maps `signedDistance = 0` to `continentalness ≈ -0.08`, but the
physical waterline is deeper. Two live dedicated-server transects on the reference pack
(seed 2025, size 3500) bracket it:

| Coast | Last dry column | First ocean |
| --- | --- | --- |
| steep, `z=-930` | `x=1408, c=-0.1478, d=-32.8`, stony_shore, stone | `x=1416, c=-0.1843, d=-40.4`, water at y=63 |
| flat, `z=300` | `x=1792, c=-0.1397, d=-29.5`, flower_forest, dry | `x=1808, c=-0.2074, d=-45.7` (x=1800 is a flooded pocket) |

The plan defaults `V3_SEA_LEVEL_CONTINENTALNESS = -0.18` and
`V3_OFFSHORE_OCEAN_DISTANCE = -42.0` were kept unchanged; no deviation to report.
`sampleV3` now returns OCEAN for `d <= 0` only when `d < -42` or `c < -0.18`. Otherwise
it returns LAND, or stony shore on `slope > STEEP_SHORE_SLOPE` / `c.mountains > 0.60`
when shore biomes are enabled. The test never applies the `c < -0.18` ocean test at
`d > 0`, so underground biome lookups stay land. Reference map: 1,844 dry-shelf land
columns, 0 dry-shelf ocean columns. Evidence: `docs/evidence/phase82/
waterline_probe.txt`, `shoreline_probe.csv`, `shoreline_transect.png`,
`Phase82ReferenceClimateTest.dryShelfIsLandNotOcean`.

### 17.2 Mountain sync and reference composition

`ModernTerrainV2` builds physical spines with
`1 - 2·|fbm2(x/750, z/750, seed+801, 3, 2.0, 0.5)|` (fallback seed
`0x5EED1234ABCDL`). The climate `mountainBands` field now combines exactly that ridge
(70 %) with the previous climate noise (30 %), using the same seed. Measured
ridge/mountain correlation is **0.9808**; `mountainBandsMatchPhysicalRidge` also asserts
no swamp column on ridges. Swamps are guarded to `c < 0.15 && mountains < 0.42`; the
reference map has **0** violating columns.

The v3 family tree (`selectLandFamilyV3`, v1/v2 `selectLandFamily` untouched) applies
elevation cooling `effTemp = temp - max(0, c - 0.28)·0.62`, then branches through high
mountains, foothills, swamp/mangrove lowlands, cold, cool, mid-temperate and warm/arid
climates. Reference composition (NATURAL/ALL/3500, seed 2025, land 33,978 columns):

| Metric | Target | Measured |
| --- | --- | --- |
| core temperate {2,3,4,7,17} | 65–70 % | **68.05 %** |
| green total {1,2,3,4,5,6,7,17} | recorded | **79.05 %** |
| central peak band `c > 0.45` | {14,15} dominant, no swamps | 5,015 columns, 135 non-14/15, 0 swamps |
| foothills `0.30 < c <= 0.45` | cherry/meadow/dappled/taiga | 4,289 columns, 3,575 green |
| warm/arid {11,12,13} | all `c <= 0.36`, component ≥75 %, near coast | 607 columns, max `c=0.2871`, **97.36 %**, 100 % within 400 blocks |
| swamps/mangroves | zero above `c>0.15` or `mountains>0.42` | 0 violations |
| mushrooms | offshore only, <3 % of land | 202 offshore, 0 inland |
| pale garden | rare | 27/136,337 = 0.02 % |
| dappled forest | present in forest transitions | 31/932 family-3 columns = 3.3 % |

Temperature spans 0.285–0.802 and humidity 0.158–0.808 on the reference grid (old v3 was
uniformly hot). `V3_GRADIENT_SCALE = 0.55` and `V3_TEMPERATURE_NOISE_SCALE = 1.15` are
the tuned values. Evidence: `reference_composition_before/after.csv`,
`temp_achieved_vs_target.csv`, `mountain_sync.csv`, `mushroom_offshore.csv`,
`reference_map_before/after.png`, `preview_closeup_1block.png`.

### 17.3 Preview/source parity, caching and biome families

`IslandTerrain.Distances(signed, main)` exposes the main-island distance separately from
the islet-merged SDF; `ModernTerrainV2.distances()` computes the main distance once and
merges the islet distance once, and `signedDistance` delegates to it. `WorldPackBuilder`
uses the main distance for structure gates. `OmniBiomeSource.ColumnCache` caches the
y-independent v3 `SampleResult`, so the added ridge fBm is not recomputed per Y quart.
Zone-family parity between `BiomePreview.selectionV3` and the real source is **99.02 %**
(the residual is shore/ocean subtype fallback where the requested type is disabled, as
before). `Phase82ReferenceClimateTest.previewSourceZoneFamilyParityV3` includes the dry
shelf and disabled-shore cases.

Weighted shares were recalibrated for the v3 tree. `Phase10BiomeShareTest` still passes
all-100 absent-vs-explicit equality, zero-weight, monotonicity, 8-thread determinism,
coherent tickets and ≤3 pp held-out errors; the held-out grid increased from 79 to 101
because the reference tree sends more columns to terrain-driven families. Cherry-45's
calibrated target is now **9.18 %** (achieved 9.47 %; 0.8.1: 17.4 %) because the new
tree confines cherry to foothills/high mountains; the calibration still reproduces
the natural relative target.

### 17.4 Strict structures and the runtime guard

`WorldPackBuilder` now samples `(continentalness, biome, deepDark, family, slope)` and
applies per-structure policies only for `version >= 3`: desert `{12}` 0.00–0.32 slope
<0.22; jungle `{10}` ≤0.36 slope <0.28; swamp `{8,9}` ≤0.16 slope <0.10; mansion `{7}`
≤0.36; igloo `{0,1}` ≤0.30; outpost/portal any land ≤0.40; trail `{1}`; camp `{3}`;
mineshaft any land; monument/ruins/shipwreck/wreck ocean bands; buried treasure shore.
Candidates must satisfy both the requested family and the actual point family, and score
by closeness to `preferC` + slope + small spawn-distance penalty — never by highest
continentalness. No candidate → `enabled=false`, no tag point, export succeeds.

0.8.1 on the same reference draft placed:
`desert_pyramids` at chunk 5,-60 (`c=0.6148`, CHERRY_GROVE), `jungle_temples` at 3,74
(`c=0.6142`, BAMBOO_JUNGLE), `swamp_huts` at -10,-41 (`c=0.6188`), and emitted broad
all-land tags. 0.8.2 disables desert/jungle/igloo (desert and jungle families are
absent from the map; no igloo candidate met the `c <= 0.30` / slope `< 0.25` policy), places
`swamp_huts` at 65,-82 (live query: `c=-0.0923`, `biome=swamp`, y82–88, surface 87) and
`woodland_mansions` at 44,81, and emits narrow tags only. A live `/locate structure
minecraft:swamp_hut` returned 1040,-1312; desert/jungle/igloo returned nothing. Evidence:
`structure_placement_before/after.csv`, `structure_queries.txt`, `structure_tags/`.

`RelaxedPyramids` regained a testable `allowsSurface(biomePath, topY, relief, c, slope,
Policy)` predicate: it samples the chunk-center biome through the real biome source,
requires the allowed path and `validBiome`, checks base height at center and ±8 blocks,
rejects above `maxY` or above an 8-block relief, and re-checks the active OmniIsle
terrain's continentalness/slope bounds when present. A rejected site returns
`Optional.empty()`; nothing is placed. `Phase82StructureTest` covers high mountain, wrong
biome, valid lowland desert/jungle, absent family → disabled set, and narrow tags.

### 17.5 New biomes, caves, server smoke and status

`dappled_forest` is family 3; `pale_garden` is a rare family-17 pocket; both appear
naturally in the reference map. v3 warm shallow caves (`y ∈ [-32, 0)`,
`temperature > 0.68`) route to sulfur caves before the deep-dark noise test: a fixture
with 259 warm shallow columns changed from 104 sulfur / 155 deep_dark to 259 sulfur /
0 deep_dark, while cold columns are unchanged (`cave_sulfur.csv`).

Two fresh dedicated servers reached `Done` with no crash reports: reference 1.559 s,
spawn 12,96,-930 on land, 5 packs, `omniisle verify` 26 surface biomes; weighted 1.417 s,
`info` shows calibrated cherry 45 % and achieved 9.5 %, verify resolves the weighted
palette. Both stopped cleanly (`server_startup.log`, `omniisle_info.txt`,
`omniisle_verify.txt`). One cold-start "Can't keep up" warning appeared during the first
RCON locate after spawn-area generation and did not recur; no performance claim is made.

**Final build:** 123 tests, 0 failures, 0 errors, 2 assumption-gated skips; jar
`build/libs/OmniIsle-26.3-Fabric-0.8.2+mc26.3.jar`. Full 25×25 weighted chunk-gen
timing remains **UNVERIFIED**; GUI 2/4 screenshots remain **NOT DONE**; the Phase 9
structure matrix remains PARTIAL 15/45. v3 worlds intentionally generate differently
after 0.8.2; no `generation_version` was added, and v1/v2 goldens pass unmodified.

## 18. [VERIFY] Phase 11 — universal v3 climate provinces, rare biomes, strict structures, editor fixes (0.8.4)

### 18.1 Universal `ClimateLayout`

0.8.2's climate was tuned on the reference continent; other presets had no guaranteed warm or
cold sectors. 0.8.4 adds `continent/ClimateLayout`, derived only from the mask bounds + biome
seed, so it works for every preset without knowing the preset identity. Warm = top 16 % of land
by `-nx + 0.20·(-nz) + 0.10·noise(/900, seed+1301)`; cold = top 12 % of total land (warm excluded)
by `nx + 0.35·(-nz) + 0.10·noise(/900, seed+1307)`; the rest is temperate. Masks are smoothed with
two 5-tap `[1,4,6,4,1]/16` passes, which makes the strength a continuous transition and keeps
province membership disjoint. Pale (3.0 %, `/420`, +1601), ice (0.6 %, `/260`, +1607) and
cherry-support (36 %, `/520`, +1613) noise masks add the rare pockets.

Province membership is not a nearest-cell lookup: `sample()` bilinearly interpolates the smoothed
strength rasters, adds a 160-block `boundaryNoise` term (salt +1621), and compares the result
against quantile-calibrated thresholds (`warmThreshold` = smoothed top 16 % of land,
`coldThreshold` = smoothed top 12 % of total land excluding warm). This was the 0.8.4 boundary
fix: the original nearest-cell mask reproduced the 18–20-block editor grid as visible stairs,
while the continuous formulation yields organic contours and preserves the measured sector
shares. Water within a small margin of a province coast inherits that province so ocean sectors
match the land.

Measured on the five shipping presets (`Phase11PresetClimateTest`, 256×256 samples, seed 2025):
warm families `{11,12,13}` 13.78–16.95 %, cold `{0,1}` 13.18–14.43 %, green
`{2,3,4,5,6,7,17}` 68.53–70.55 %. The controlled shares land inside the requested warm
12–18 % / cold 10–15 % and the test band 60–75 % green on every preset.

### 18.2 Terrain APIs and the largest-component main SDF

`IslandTerrain` gained default `heightTierAt`, `surfaceHeight` and `climateLayout` methods so
legacy implementations stay source-compatible. `ModernTerrainV2` exposes the 7-tap-smoothed
tier grid, the same surface height used by relief, and the cached v3 layout. `physicalRidge`
is now a static helper shared by terrain and climate, so mountain bias and biome spine cannot
drift apart. For v3, 4-connected land components are labelled on the mask grid and the largest
one gets its own padded EDT/SDF; `distances().main` returns that SDF while `signed` keeps the
all-mask coast behavior. This is what makes the Mineshield SW bay islet offshore for mushrooms.
v2 keeps `main = signed` (all-mask), so its golden output is untouched.

### 18.3 Province-aware family tree and rare biomes

The v3 tree is ordered: offshore mushrooms → effective temperature with province bias → cold
lowlands → physical peaks → foothills → cherry → wetlands → temperate heartland → warm sector.
Key gates and measured results:

- Snow family 15 requires `c > 0.50` and `heightTier >= 1.5` outside the cold province; the
  six counter categories (snow below tier 1.4 outside cold, wetlands above `c=0.18`,
  main-island mushrooms, dry-shelf ocean) measured **0 on all five presets**.
- Mushrooms require `main < 0` and `c < 0.05` with a 650-block fBm mask > 0.36; share is
  0.12–1.04 % of land and strictly 0 % on the main island.
- Cherry grove lives at `0.34 < c <= 0.48`, humidity > 0.58, with a low-frequency mask
  (> 0.45): 1.16–2.78 % of land (target 1.5–3 %, Mineshield 1.16 % below because its
  foothill band is narrower).
- Pale garden requires the 3 % pocket plus effective humidity > 0.88 (raw humidity is boosted
  by `0.65·paleStrength` inside the pocket): 0.93–1.57 % of land.
- `ice_spikes` is a family-0 land hint used only inside the 0.6 % cold pocket; explicit
  non-neutral per-biome weights remain authoritative.

After the family is chosen, `SampleResult` reports that family's `BiomeShare` centroid climate
as the effective climate, so weighted selection favors the intended family; raw climate is
retained for ocean subtypes. The editor preview and real source share the same code path
(Invariant I3); the v2/v3 regression hash pins both.

### 18.4 Province oceans, dry shelf, and shore arcs

Warm columns next to warm land select `warm_ocean`/`lukewarm_ocean`; cold columns select
`cold_ocean`/`frozen_ocean`; temperate keeps the legacy temperature logic. The province ocean
subtypes have rim transition bands: WARM strength below 0.55 selects lukewarm water (warm water
only in the core), and COLD strength below 0.55 selects cold water (frozen only at strength
≥ 0.55 and temperature < 0.34), so the ocean tint fades with the province instead of switching
at a hard line. Shore subtypes inherit the province (`beach`, `snowy_beach`, `stony_shore`). The
0.8.2 dry-shelf rule is unchanged: at `signedDistance <= 0` and `c >= -0.18` there is never
OCEAN, and a suppressed beach falls through to LAND. `Phase11PresetClimateTest` reports 0
dry-shelf ocean samples on all five presets; `Phase11ClimateLayoutTest` 11L.6/11L.7 measure
continuous WARM crossings (more than half fractional) and the four transition-band outcomes.
`docs/evidence/phase11/after/ocean_edge_natural.png` shows the smooth coastline/province edge.

`ShoreField` now uses only coarse ≥130-block fields (the 55-block octave and 17-block jitter
were removed). Measured 8-connected sand components (`Phase11ShoreTest`, STEP=2, band
[−20, 36]): Mineshield 89 components / 7 tiny / median 56 / P75 108 / in-range share 0.44;
Natural 23 / 1 / 50 / 102 / 0.52; Round 16 / 0 / 70 / 132 / 0.56; Archipelago 60 / 5 / 44 / 90
/ 0.45; Crescent 22 / 1 / 58 / 128 / 0.45. **The zero ≤3-block target is not achieved**:
14 tiny components remain out of 210. Three tuning attempts (MIN_WIDTH 6/12, coarser
pocket/gap/selector fields, and a 12-block inland-width dilation) either raised the sub-16
count or reduced the 25–90 share, so the original field was kept and the achieved distribution
is recorded in `shore_runs.csv` — **PARTIAL**, per the plan's honesty clause.

### 18.5 Mineshield tier cleanup

The extracted height map had 16 detached tier-3 components and 48 tier-2 components. A 3×3
median filter plus component reassignment keeps a single curved tier-3 range in the central
north (612 cells, bbox 46×52, centroid normalized 0.521, 0.398) and three tier-2 components
that form the western mesa band (1,108 / 253 / 70 cells, all centroids west of x=0.26);
detached specks become tier 1. Land pixels stay 11,232; tiers become 1/2/3 = 9,189 / 1,431 / 612
(was 8,983 / 1,509 / 740). The cleanup is applied to the cached tier array at every resolution;
256 and 384 also yield one tier-3 component (990 and 2,396 cells). Mask/share-code/save tests
are unchanged.

### 18.6 Strict structures with province and surface ceilings

`WorldPackBuilder.Sample` now carries family, province, topY (terrain surface height) and
slope. `StructurePolicy` adds `province` and `maxSurfaceY`; v3-only candidate rules combine
accepted family, required province, continentalness, slope and topY, and score by preferred
continentalness + low slope + a tiny spawn-distance penalty. No candidate writes
`enabled=false` with no point and no exception. Tags list only enabled biome IDs in the
accepted families plus the point biome. `RelaxedPyramids.allowsSurface` samples the center
biome through the real source and `WORLD_SURFACE_WG` at center/±8, and rejects wrong biome,
province, Y, relief, c or slope. The NATURAL and Mineshield packs exported for evidence have
14 sites each; absent types appear as disabled rows in `structure_sites.csv`.

### 18.7 Editor fixes

The resolution control is a full-width button with a two-argument label, cycling
192 → 256 → 384 and rendering `Разрешение: 192×192` (the old half-width `CycleButton`
rendered the clipped `...ение: %sx%s: 192x192`). `EditorLayout` computes every rectangle from
shared metrics with a dynamic compact row height; `Phase11EditorLayoutTest` verifies no
overlaps or out-of-panel controls on 13 representative window sizes at GUI scales 1–4, and
`placeTooltip` keeps the inspector inside the preview canvas, flipping and clamping when it
would cover the center controls. Biome rows count preview family samples on every refresh and
show the actual share even with all-100 weights, falling back to `—` only when the row is
disabled or no land was sampled.

### 18.8 Build, compatibility and status

**Final build:** 150 tests, 0 failures, 0 errors, 2 assumption-gated skips; jar
`build/libs/OmniIsle-26.3-Fabric-0.8.4+mc26.3.jar`. `GoldenV1Test`/`GoldenV2Test` pass
unmodified; the v2 regression hash is unchanged (`-4990092939012319655`) and the v3 hash is
now `-6312501814918578635` (0.8.3: `3958321154656118617`) by design. No `generation_version`
was added. New v3 chunks generate differently in 0.8.4; existing chunks are untouched. Phase 9
structure matrix remains **PARTIAL 15/45**, editor screenshots remain **NOT DONE**, and 25×25
chunk-generation timing for 0.8.4 is **UNVERIFIED** (re-measured on 1.0.0 in §23). The worktree remains uncommitted by
request.

## 19. [VERIFY] Phase 12 — 1.0.0 flat editor UI, custom widgets and the screenshot harness

Presentation-only release. Worldgen, `IslandDraft`, share codes, datapacks and structure policies
are untouched; `GoldenV1Test`/`GoldenV2Test` and the v2/v3 regression hashes stay unchanged. The
editor now renders through `com.hack337.omniisle.client.ui` instead of vanilla `Button`,
`CycleButton`, `AbstractSliderButton` and bordered `EditBox`.

### 19.1 Pinned 26.3 GUI API surface (verified with javap on the mapped jar)

All drawing and input goes through these mapped members; no mixins were added:

- `net.minecraft.client.gui.screens.Screen`: `extractBackground(GuiGraphicsExtractor,int,int,float)`,
  `extractRenderState(GuiGraphicsExtractor,int,int,float)`, `keyPressed(KeyEvent)`,
  `init()`, `removed()`.
- `net.minecraft.client.gui.GuiGraphicsExtractor`: `fill(int,int,int,int,int)`,
  `outline(int,int,int,int,int)`, `text(Font,String,int,int,int)`,
  `textWithWordWrap(Font,FormattedText,int,int,int,int)`, `enableScissor(int,int,int,int)`,
  `disableScissor()`, `blit(RenderPipeline,Identifier,int,int,float,float,int,int,int,int,int)`.
- `net.minecraft.client.gui.components.AbstractWidget`: abstract
  `extractWidgetRenderState(GuiGraphicsExtractor,int,int,float)`,
  `nextFocusPath(FocusNavigationEvent)`, `createNarrationMessage()`,
  `updateWidgetNarration(NarrationElementOutput)`; `OmniWidget` overrides these so custom controls
  keep vanilla focus traversal and narration. `OmniControlsPanel` extends
  `AbstractContainerWidget` and delegates focus to its scrolled children.
- `net.minecraft.client.gui.components.EditBox` (`setBordered(boolean)`, `setTextColor(int)`) is the
  single documented vanilla control retained inside `OmniTextField` so IME, selection, clipboard
  and accessibility stay vanilla-tested.
- The icon atlas is a 128×48 PNG blitted with `GuiGraphicsExtractor.blit`; buttons fall back to
  their text label when the texture is unavailable.

### 19.2 Theme, layout and density tiers

`OmniTheme` holds the measured tokens: bg `#121418`, panel `#1A1D23`, raised `#222731`, border
`#2C313A`, text `#E6E8EB`, muted `#8A919C`, accent `#3DDCB0`, danger `#FF6B6B`, warn `#FFC857`,
ok `#5BD98B`. Body-text contrast pairs are asserted >= 4.5:1 by `Phase11UiThemeTest` (text/bg 15.02,
text/panel 13.75, muted/panel 5.31, muted/raised 4.71, accent/panel 9.70, danger/panel 6.08,
warn/panel 10.97, ok/panel 9.44). Hover/press/focus easing is time-based (80 ms via
`Util.getMillis()`), never frame-count based; disabled controls render at 50 % alpha and are skipped
by focus and pointer handling.

`EditorLayout.compute(width,height)` is pure and is called only from `Screen.init()` (resize or GUI
scale change). Tiers: FULL >= 840×470 (right list, labelled tools, 2×3 labelled tabs, 260-px
controls), MEDIUM >= 700×420 (icon tools, list in a modal), COMPACT >= 400×240 (icon tools,
scrolling controls), MINI >= 320×150 (16-px tools in two columns, six icon tabs, abbreviated
status), MINIMAL below 320×150 (actual logical size notice plus Cancel/Done only). The status bar
keeps Done (primary) and Cancel (secondary) reachable in every tier. `Rect`, `overlaps`, `inside`
and the allocation-free `placeTooltip(bounds, mouseX, mouseY, width, height, out)` keep inspector
and hover tooltips inside their panels.

Tab labels need more width than the old 3-column grid gave: at 1280×720 the third column clipped
"Structures" to an icon. Non-MINI densities now use 2 columns × 3 rows (~125 px per tab at FULL,
~99 px at COMPACT), and the top bar shows the preset name only (icon on narrower tiers), so all six
labels and "Share Code" fit without ellipsis. MINI keeps the 6-column icon-only row because full
labels live in tooltips and narration.

### 19.3 Widgets, feedback and text fitting

- `OmniButton` (flat/secondary/primary/danger/ghost, leading icon, shortcut chip), `OmniToggle`
  (switch), `OmniSegmented`, `OmniSlider` (thin track, wheel, arrow keys, Shift fine step,
  double-click reset), `OmniTabBar` (arrow/Home/End), `OmniSection` (collapsible, session state),
  `OmniListView`/`OmniBiomeList` (Up/Down/Home/End, Space toggle, Left/Right weight, row narration),
  `OmniDropdown` (preset menu), `OmniPopover` (layer-clear confirmation), `OmniTooltip` (400 ms,
  dark box, title + wrapped description + shortcut, clamped/flipped), `OmniToast` (queue, fade,
  bottom-right, success/error) and `OmniTextField`.
- Editor feedback: toasts for seed/share copy, import/export and infeasible calibration; the Clear
  action opens an inline confirmation first; satellite screens return their messages through
  `queueToast`, which fires on the next editor frame instead of aging off-screen.
- Keyboard: Tab/Shift+Tab in a fixed order (top bar → tools → canvas modes → tabs → controls →
  list → status), Enter/Space activate, arrows navigate lists/tabs/sliders, Home/End jump,
  Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z undo/redo, Esc closes dropdown → popover → weight edit → tooltip
  before the screen. No new letter shortcuts. Right-click selects the eraser; Space-drag and
  middle-drag pan; Shift draws squares/circles and fine-tunes sliders.
- `OmniMetrics` caches real font widths and fitted strings per language epoch and invalidates on a
  language or GUI change; `OmniText` performs grapheme-safe ellipsis. Unit tests run headless with
  `WorstCaseMetrics` (Latin 8 px, Cyrillic 9 px per character), assert identical en_us/ru_ru key
  sets, that every referenced `omniisle.*` literal exists in both languages, and that every language
  line either fits or is ellipsised at widths 16–320. Real font widths are checked only by the
  screenshots, not claimed from unit tests.

### 19.4 Dev-only screenshot and render-cost harness

`OmniIsleClient` (client entrypoint) registers `UiScreenshotHarness` only when
`-Domniisle.uiScreenshot=<dir>` is set; without the property the entrypoint returns immediately and
the shipped jar behaves identically. `build.gradle` forwards the property (plus `.lang` and `.smoke`)
only to `runClient`. The harness:

- boots on `ClientTickEvents.END_CLIENT_TICK` after the title screen, opens
  `new OmniIsleEditorScreen(null, new IslandDraft())` without a real `CreateWorldScreen`, and drives
  tab switches and preview modes through the private `switchTab(int)` / `setPreviewMode(int)` hooks.
- switches language with `LanguageManager.setSelected` + `Minecraft.reloadResourcePacks()` and waits
  for the loading overlay to clear before opening the editor (the earlier smoke captured the Mojang
  splash because the overlay was still up).
- resizes with `Window.setWindowed` / `Window.setGuiScale` + `Minecraft.resizeGui()`, compensating
  for the display scale via `SDLVideo.SDL_GetWindowDisplayScale` so the requested width/height are
  framebuffer pixels on Retina.
- captures with `Screenshot.takeScreenshot(GameRenderer.mainRenderTarget(), 1, Consumer<NativeImage>)`
  and `NativeImage.writeToFile(Path)`, writing `manifest.csv` (path, language, tab, requested scale,
  requested size, actual logical size, FPS) and `harness_error.txt` on failure.
- measures render cost by wrapping the screen's background + foreground extraction with
  `System.nanoTime` for 300 frames after warm-up per tab at 1920×1080 GUI scale 2, and writes
  `render_cost.csv` (samples, median ms, p95 ms).

Measured environment constraint: macOS enforces a 320×240-point minimum window, so a 640×320
framebuffer cannot be created (the smallest real capture is 640×480), and Minecraft clamps the GUI
scale so the logical size never drops below 320×240. The 640×320 matrix entry is therefore captured
as 640×480 and the actual logical size is recorded per row; MINIMAL (<320×150) is not reachable in
a real client and is covered by `Phase11UiLayoutTest`/`Phase11EditorLayoutTest` only.

### 19.5 Tests and status

`Phase11UiThemeTest` (tokens, contrast, easing, disabled), `Phase11UiLayoutTest` (density budgets,
tab/tool tiling, centered canvas, allocation-free tooltip), `Phase11UiTextTest` (short labels,
surrogates, empty cases, key parity, referenced keys, worst-case corpus), `Phase11UiIconsTest`
(128×48 atlas, painted cells, palette, namespaced identifier), `Phase11UiWidgetTest` (button, toggle,
segmented, slider, scroll, tab bar, dropdown, narration), `Phase11UiHygieneTest` (no `stream()`,
`String.format` or collection creation in render methods; cached values; UTF-8) plus the rewritten
`Phase11EditorLayoutTest` and `Phase9EditorTest.layoutFitsGuiScales`. Behaviour tests for tools,
undo/redo, zoom/pan, seeds, PNG import/export and textures are unchanged. Full build: **181 tests,
0 failures, 0 errors, 2 assumption-gated skips** (43 classes; 150 baseline + 31 UI/layout);
`GoldenV1Test`, `GoldenV2Test` and `BiomeSourceV2V3RegressionTest` pass. Shipped jar:
`build/libs/OmniIsle-26.3-Fabric-1.0.0+mc26.3.jar`, SHA-256
`5b2da1a4207507638820e2af9f4592e77794749ce9df150a0b52ba5f5ad7a050`.

### 19.6 Captured screenshots and measured render cost (2026-10-26)

Command: `JAVA_HOME=<Prism epsilon> ./gradlew runClient -Domniisle.uiScreenshot=docs/screens/1.0.0/after`
(en_us and ru_ru in one run). Result: **200 PNGs** — 6 tabs × 16 window/scale combinations × 2
languages (192) plus 4 preview modes × 2 languages at 1280×720 scale 1 — with a 200-row
`manifest.csv`, `render_cost.csv` and `harness_done.txt`. `docs/screens/README.md` documents the
commands and paths. The set, image dimensions and manifest rows were validated programmatically
(200/200, no missing or extra file); `docs/screens/1.0.0/before/editor_0.8.4_old_ui.png` is the
user-supplied 0.8.4 client capture (3272×2046) used as before evidence — no harness "before" pairs
were captured because the harness was developed after the UI migration started.

Actual tiers reached by the 4-window × 4-scale matrix (requested → actual logical):

| Request | Scale 1 | Scale 2 | Scale 3 | Scale 4 |
|---|---|---|---|---|
| 640×480 | 640×480 COMPACT | 320×240 MINI | 320×240 MINI | 320×240 MINI |
| 854×480 | 854×480 FULL | 427×240 COMPACT | 427×240 COMPACT | 427×240 COMPACT |
| 1280×720 | 1280×720 FULL | 640×360 COMPACT | 427×240 COMPACT | 427×240 COMPACT |
| 1920×1080 | 1920×1080 FULL | 960×540 FULL | 640×360 COMPACT | 480×270 COMPACT |

MEDIUM (700–840×420–470) is not produced by any combination (854×480 scale 1 is already FULL,
the next logical size is 960×540), and MINIMAL (<320×150) is unreachable because macOS enforces a
640×480 framebuffer and Minecraft clamps the GUI scale to at least 320×240 logical pixels. Both are
covered by `Phase11UiLayoutTest`/`Phase11EditorLayoutTest` only, not by screenshots.

Visual inspection was **sampled, not exhaustive**: all six tabs and all four preview modes at
en_us 1280×720 scale 1; en 854×480 scale 1 biomes; en 640×480 scale 2 shape; en 1280×720 scale 2
biomes; en 1920×1080 scale 2 tools; ru 1280×720 scale 1 structures; ru 640×480 scale 2 structures;
ru 854×480 scale 1 tools; ru 1920×1080 scale 2 climate. Every inspected capture keeps labels and
rows inside their panels, MINI is icon-only, ru names ellipsise without overlapping the weight
stepper, and the Shape list returns to biomes after visiting Structures. One deliberate ellipsis
remains visible at FULL: the gradient segmented control shows `North-So...` (en) and `Запад-В...`
(ru); the control's tooltip lists all four full options (Off, North-South, East-West, Radial), so
the full text is available on hover (tooltips are not visible in still captures). Tooltips, toasts,
dropdowns and the clear-confirmation popover are not visible in the non-interactive captures; they
are covered by unit tests and by code inspection only.

Render cost, 300 frames after warm-up, 1920×1080 at GUI scale 2 (960×540 logical), background +
foreground extraction (`render_cost.csv`):

| Tab | en median / p95 (ms) | ru median / p95 (ms) |
|---|---|---|
| Shape | 1.758 / 3.102 | 1.899 / 3.391 |
| Terrain | 1.715 / 2.972 | 1.736 / 2.897 |
| Climate | 2.007 / 3.702 | 1.800 / 3.144 |
| Biomes | 1.911 / 3.584 | 1.828 / 3.235 |
| Structures | 1.219 / 2.214 | 1.238 / 2.231 |
| Tools | 1.806 / 2.830 | 1.938 / 3.235 |

11 of 12 medians meet the ≤ 2 ms target; en_us Climate misses it by 0.007 ms. Every p95 is above
2 ms (2.214–3.702 ms). The target is therefore **PARTIAL**, not fully met; no per-frame allocation
regression was observed (source scan in `Phase11UiHygieneTest`).

## 20. [VERIFY] Phase 13 — v3 spawn dome and highland height mismatch

Two v3 bugs were reported with screenshots: a perfectly concentric terraced plateau at the spawn
arrow, and snowy mountains that stayed at Y 66–70 while the editor height preview advertised up
to ~260. Both root causes were confirmed in code and data before changing anything.

### 20.1 Spawn dome: a fixed-radius density wrapper outside interpolation

`WorldPackBuilder.export` wrote `omniisle:spawn_plateau` as the v3 `final_density`
(`WorldPackBuilder.java:168-175` before this phase). `SpawnPlateauDensity` used a perfect-circle
quartic mask (radius 80), a fixed 78→62 dome, hard density bands at depth <2 and <26, and a
lift-only `max(...)`. Because `final_density` is evaluated outside the vanilla 4×8 cell
interpolation of the offset/factor chain, the dome kept its exact circular silhouette and its
band quantisation survived as visible terraces. It was technical scaffolding for a dry spawn,
not terrain. It is no longer emitted for v3; the codec stays registered because already-generated
v3 packs reference both `omniisle:spawn_plateau` and `final_density_base`.

### 20.2 Highlands: preview uses continents, real surface uses offset splines

`IslandTerrain.continentalnessToElevation(continents)` (the editor preview and `/omniisle` maps)
reaches 279 blocks at c = 0.95. The real surface Y is driven by the vanilla-derived
`offset.json` spline chain, which is keyed mostly on erosion and ridges, not continentalness:
a linearised evaluation of the static spline across erosion/ridges at c = 0.95 gives median
offset −0.37 (Y ≈ 81), p90 +0.32 (Y ≈ 169) and max +0.98 (Y ≈ 253). At c = 0.45 the median is
also ≈ Y 79. Painting tier 3 therefore changed the preview without moving the median surface.
The v3 vertical fade (240→256 in both `final_density.json` and `preliminary_surface_level.json`)
additionally flattened the rare high offsets before they could become peaks.

The fix keeps vanilla's 3D noise, caves and aquifers and wraps the offset instead:
`offset = vanilla_offset · scale(continents) + lift(continents)` with knots
(c = −1.10, 0.45, 0.55, 0.65, 0.75, 0.85, 0.90, 0.95, 1.10),
lift = (0, 0, 0.15, 0.40, 0.60, 0.80, 0.90, 1.00, 1.00) and
scale = (1, 1, 0.85, 0.65, 0.45, 0.30, 0.22, 0.20, 0.20). Scaling down the base offset at high
continentalness stops rare vanilla offsets from stacking into the build limit. The v3 fade moves
to 300→316; `preliminary_surface_level.upper_bound` already clamped at 320 and did not change.

### 20.3 Measured result and a test-harness priority bug

`Phase13HighlandWorldgenTest` exports ROUND 4000 seed 4242 (v3, BALANCED, rivers/islets off)
with a 20-px tier-3 paint at the editor spawn, extracts the generated pack, decodes the preset
through real worldgen registries, builds `RandomState` and samples
`NoiseBasedChunkGenerator.getBaseHeight(..., WORLD_SURFACE_WG, ...)` over 1681 columns
(±320 blocks, step 16) around the painted area:

| metric | measured |
|---|---|
| continent at spawn centre | 0.915 |
| min surface | Y 139 |
| median surface | Y 228 |
| p90 surface | Y 235 |
| max surface | Y 289 |
| columns ≥ Y 220 | 1088 |

The first two test runs sampled Y 63 everywhere. The generated pack was correct, but the harness
passed `List.of(generated, mod, vanilla)` to `MultiPackResourceManager`; its
`FallbackResourceManager` checks packs in reverse (`getResource`) and overwrites in forward order
(`listResources`), so the **last** pack has priority. The mod's static empty-config
`continents.json` was shadowing the generated pack. The order is now `vanilla, mod, generated`,
matching the game (world datapack last). The same order bug existed in `Phase10PresetDecodeTest`,
which therefore had not been exercising the generated density overrides at all; both tests are
fixed and pass.

The preview curve was intentionally left unchanged: after the lift, the measured median and p90
sit close to the curve's expectations, and changing it would touch UI, commands and v1/v2. The
measured peak (289) is slightly above the preview value at c = 0.915 (~265) because vanilla
offset/factor noise still adds relief; peaks above Y 316 would be clipped by the new fade and are
not produced in this sample.

## 21. [VERIFY] Phase 14 — 1.0.1: interior beaches, village variants, warm↔cold bands

Three post-1.0.0 reports were reproduced in code before changing anything: pale beach ribbons
deep inside the island (especially along fjords), too many villages and all of them plains-style,
and hard warm/cold walls where taiga could touch desert.

### 21.1 Interior beach ribbons: bayness cannot see fjord width

`ShoreField.outcome` granted SAND/STONY to any column with `d ∈ [intertidalLimit, 34]` when the
patch mask, beach weight, inland width and bayness passed. `bayness` compared the mean signed
distance of an 8-point ring at radius 96 with the centre distance and saturated near 1.0 for both
an open bay and a narrow channel: on a 20-block fjord every ring sample lies on land, so the mean
is high and the ratio cannot distinguish the two. Nothing gated altitude or continentality either,
and `ModernTerrainV2` flattened the whole band into a beach shelf, so sand followed fjord banks
far inland.

The ring scan now returns both values — `Ring(bay, minDistance)`, one 8-point pass at the same
radius — and `outcome` requires the ring to see actual open water (`minDistance ≤ −55`), low
terrain (`heightTier ≤ 1.10`) and coastal continentalness (`c ≤ 0.20`) in addition to the old
patch/width/depth checks. `MAX_INLAND` shrank from 34 to 28 and the inland width is now
`(4 + 18·noise)` filtered by patch, coarse and notch terms (floor 8, cap 22). The terrain
flattening only runs while `d < 52` and `tier ≤ 1.05`, so hills keep their relief. All six
preview/paint/export call sites pass the same ring, tier and continentalness values.

### 21.2 Villages: one plains set behind a broad tag

The v3 export replaced vanilla's five-entry village set with a single `minecraft:village_plains`
random-spread set at 34/8 and rewrote `has_structure/village_plains` to list every enabled land
biome. Villages therefore kept plains architecture in desert/snow/taiga and appeared at vanilla
density (count scales as `1/spacing²`).

v3 now emits one `structure_set/villages.json` with five weighted entries — plains, desert,
savanna, snowy, taiga — at spacing 54 / separation 12, salt 10387312, and writes family-scoped
`has_structure/<tag>.json` files merged with `replace: false`: plains covers families 2–6 except
grove, desert 12/13, savanna 11, snowy 0 except ice spikes, taiga 1. Empty variants are omitted;
if no family is enabled the set is exported disabled. Island-size and density scaling still apply.
`(34/54)² = 0.3964`, i.e. **−60.4 %** villages by spacing math (not counted in a live world). v1/v2
keep the single fixed plains placement, so the old worlds are untouched.

### 21.3 Categorical province walls

`ClimateLayout` assigned each column to TEMPERATE/WARM/COLD (with a 0.16 strength dead zone) and
`selectLandFamilyV3` switched families on it: COLD lowlands were forced to snowy plains/taiga,
WARM to savanna/badlands/desert/jungle, and mountain snow was allowed only in the temperate
province. A categorical boundary can put taiga directly next to desert and forbids snow peaks in
warm/cold sectors.

The sample now exposes the raw smoothed `warmStrength`/`coldStrength` (a 7-component record; the
5-arg constructor derives them from the province for older callers), and land selection uses
`diff = warm − cold`: cold core ≤ −0.35, cold band ≤ −0.12, temperate, warm band ≥ 0.12, warm core
≥ 0.35. The province remains only for ocean/shore selection, the ice-spike hint and structure
tags. The mountain exception is province-independent: `tier ≥ 1.5 && c > 0.50` selects snowy
peaks (effTemp < 0.50), stony peaks (effTemp > 0.72) or meadow in any sector. Themed maps keep
the physical warm/cold layout; the theme flag only scales the effective-temperature nudge, which
restored the themed-map family coverage measured by Phase 10.

### 21.4 Measured result (1.0.1)

| check | measured |
|---|---|
| Phase 14 shore gates, NATURAL/FJORDS/ARCHIPELAGO (384² grid) | 0/0/0 violations among 2,597/5,198/7,272 sampled coastal columns; 136/150/224 shore columns |
| synthetic 20-block fjord | 198/198 NONE, bayness 1.0 |
| synthetic straight open coast | 76/201 sand columns, every ring saw ≤ −55 |
| NATURAL open-coast trace (2,048 points) | 302 beach/snowy_beach points |
| Phase 9 acceptance (30 islands, 61,440 points) | sand 11.56 %, stony 3.25 %, none 85.19 %; density sweep 0→0, 0.2→10.74 %, 0.4→29.93 %, 0.6→48.05 %, 0.8→55.32 %, 1.0→56.30 % |
| Phase 11 sand arcs (five presets, 2-block raster) | 17–48 components per preset, median 36–70, P75 58–124, share 25–90 0.47–0.55, 10 tiny ≤3 of 144 |
| warm↔cold adjacency (192², seed 2025) | Mineshield 0 adjacent / 39 transects all banded; Natural 0 adjacent / 100 transects all banded |
| village spread | 54/12 at size 4000, 34/8 at 2500, 38/8 at density 2.0; five weighted entries |
| regression hashes | v2 `−4990092939012319655`, v3 `−6312501814918578635` unchanged |

The v3 biome-source regression hash is unchanged on its 29,282-sample grid; the hash is recorded as
measured, and no explanation is claimed for it. Tests: `Phase14ShoreInteriorTest` (3),
`Phase14VillagePackTest` (4), `Phase14ClimateTransitionTest` (3), plus recalibrated
`Phase9ShoreTest`, `Phase11ShoreTest`, `Phase11BiomeProvinceTest` and `Phase9StructureTest`.
`./gradlew test` **193 tests, 0 failures, 2 assumption-gated skips**. Interactive checks
(in-game beach inspection, village inspection, a warm→cold walk) were **NOT** performed.
Evidence: `docs/evidence/phase14/README.md`, `docs/evidence/shore/`, `docs/evidence/phase11/after/shore_runs.csv`.

## 22. [VERIFY] Phase 15 — OneIsle rebrand and standalone 1.0.0 release

### 22.1 What changed

The project was renamed from OmniIsle to OneIsle and republished as an independent GPL-3.0-only
continuation of OmniIsle 0.5.0 by Hack337. The rename is mechanical: mod id and namespace
`omniisle` → `oneisle`, Java package `com.hack337.omniisle` → `com.reystin.oneisle`, 27 `Omni*`
classes → `One*`, world preset `oneisle:oneisle`, noise settings `oneisle:one_terrain`, saved data
`oneisle:spawn`, command `/oneisle`, config `config/oneisle.json`, server pack
`oneisle-generated.zip`, directories `oneisle/presets|debug|imports|exports`, share prefix
`ONE2-`, debug markers `ONE_*`, system properties `oneisle.*`, env `ONEISLE_HARNESS_DIR`,
resources `assets/oneisle` / `data/oneisle` / `oneisle.client.mixins.json`,
`LICENSE_OneIsle`, jar `OneIsle-26.3-Fabric-1.0.0+mc26.3.jar`. No alias or legacy ID is
registered.

The terrain/climate mathematics, `generation_version` and golden values were not touched.

### 22.2 Measured checks

| check | result |
|---|---|
| `clean test build` | 197 tests, 0 failures, 0 errors, 2 assumption-gated skips, 48 classes |
| goldens | `GoldenV1Test` / `GoldenV2Test` pass unchanged; v2 hash `−4990092939012319655`, v3 hash `−6312501814918578635` |
| namespace gate `rg -i omniisle src/main` | 0 matches |
| namespace gate `rg '\bOmni' src/main/java` | 0 matches |
| old IDs in `src/main/resources` | 0 matches |
| lowercase `omniisle` in extracted jar | 0 matches; the only remaining mention is the intended `fabric.mod.json` attribution sentence “Independent continuation of OmniIsle 0.5.0 by Hack337.” |
| new namespace in `src/main/resources` | 16 files, 648 matches |
| jar size / SHA-256 | 2,819,326 bytes, `45a1cf04aa4deda5ae039f6b213542615046b3539a092bc7cd4177702cb9db22`; sources `ff85609f097a42fe3cfb9f595fab66a2b7b3c4dae1301c8ab8f9b5cc72fe3b4c` |
| in-game smoke (`runClient`, smoke harness) | log line `oneisle 1.0.0+mc26.3`; editor header “OneIsle”; `harness_done.txt` = `ok`; captures at 1280×720 s1 (1280×720) and s4 (427×240), 640×480 s1 (640×480) and s2 (320×240) |

### 22.3 Compatibility

Worlds created with OmniIsle 0.5.0–1.0.1 reference `omniisle:*` and **do not load** in OneIsle.
There is no migration, by design. Within OneIsle, the v1/v2 generation math is unchanged, so
worlds created by OneIsle v1/v2 match what OmniIsle produced for the same drafts; v3 is the
1.0.1 result plus the identifier change.

### 22.4 Not verified

- Interactive feel beyond the smoke capture (tooltips, toasts, dropdown, IME) — unchanged from
  §19 and not re-exercised.
- The smoke run captured only the Shape tab; the other five tabs were not re-inspected after the
  rename (layout code unchanged).
- Modrinth publication had not happened at the time of this measurement; see
  `docs/evidence/phase16-release/` once published.

Evidence: `docs/evidence/phase15-rename/` (`namespace_gates.txt`, `test_summary.txt`,
`jar_metadata.txt`, `sha256.txt`, `build.log`, `README.md`).

## 23. [VERIFY] OneIsle 1.0.0 — 25×25 chunk-generation timing re-measurement

Measured 2026-10-29 on the released 1.0.0 code (`v1.0.0+mc26.3-fabric`) to close the
0.8.4-era UNVERIFIED note and the Phase 10.7 weighted gate that still cited only a biome
lookup benchmark. This section supersedes the 0.7.0 performance numbers in the README; it
does not change the Phase 9 structure matrix.

### Method

* Four packs from one draft: ROUND, size 4000, shape/biome seed 4242, river density 0.35,
  islet density 0.25, shore 0.30/0.60 rocky, structures 1.0, server `level-seed=424242`.
  Variants: v1, v2, v3 all-100, v3 Cherry-45. `PerfHarnessPacksTest` exports them; hashes
  are in `docs/evidence/perf/perf_packs_1.0.0.csv`.
* Fresh world per run. RCON: five `/forceload add` strips covering chunks x 28–52,
  z −12–12 (625 chunks), then 25 row probes, each chaining 25 `execute if loaded` checks;
  wall clock from the first forceload until 625/625. Seven interleaved rounds, order
  v1 → v2 → v3 all-100 → v3 Cherry-45; v1 runs first, biasing the comparison against v1.
* Runner `tools/perf/chunkgen_25x25.py`; server logs stay outside the repository. Every
  valid run reported 625 forced chunks and 25/25 non-ocean probe columns, so the timed
  block is inland for all variants.

### Results

| variant | runs (s) | median | mean | range | median vs v1 |
|---|---|---|---|---|---|
| v1 | 14.291 / 14.284 / 14.764 / 14.163 / 14.135 / 16.708 / 13.495 | 14.284 | 14.549 | 13.495–16.708 | — |
| v2 | 13.768 / 13.951 / 13.930 / 13.659 / 12.458 / 14.002 / 13.882 | 13.882 | 13.664 | 12.458–14.002 | −2.81 % |
| v3 all-100 | 12.510 / 12.794 / 14.551 / 13.858 / 12.022 / 13.687 / 13.374 | 13.374 | 13.257 | 12.022–14.551 | −6.37 % |
| v3 Cherry-45 | 13.242 / 13.408 / 13.606 / 13.712 / 13.388 / 14.133 / 14.143 | 13.606 | 13.662 | 13.242–14.143 | −4.75 % |

Gates:

* **+15 % modern-vs-v1: PASS.** Worst modern run versus fastest v1 (13.495 s): v2 +3.76 %,
  v3 all-100 +7.83 %, v3 Cherry-45 +4.80 %.
* **Weighted vs all-100 (+3 %): PASS on medians (+1.73 %), weak.** Paired round differences
  span −6.49 % … +11.36 % and the full run range is 12.022–16.708 s, so host noise exceeds
  the 3 % threshold; the pass is not a tight verification. The weighted pack has two fewer
  enabled structure sets (swamp huts, woodland mansions), so it does not benefit from extra
  structure work.
* The weighted path is not optimized further: a cache for the per-column weighted choice
  was considered, but the measured median gap is inside the gate and a change would require
  a new release plus golden/regression re-verification.

### Discarded run

The first v3-neutral round-5 attempt threw `MissingPaletteEntryException: Missing Palette
entry for index 3` in a vanilla lighting worker during forceload and the server stopped
responding; it was killed and the run repeated. The stack has no OneIsle frames; the rerun
and the other 28 valid runs were clean. Excerpt:
`docs/evidence/perf/palette_exception_1.0.0.txt`. The exception is recorded as a runtime
risk of the harness, not as a performance row.

### Conclusion

No code change and no 1.0.1 release are required by this measurement. The README
performance section was updated to these numbers because its previous wording ("same
generation code before the rename") was no longer true after the 0.8.2–1.0.1 v3 work.
Phase 9 remains **PARTIAL 15/45** until the 45-config structure matrix is rerun.

Evidence: `docs/evidence/perf/chunkgen_25x25_1.0.0.csv`, `perf_packs_1.0.0.csv`,
`palette_exception_1.0.0.txt`, `README.md`; harness `tools/perf/chunkgen_25x25.py`,
`src/test/java/com/reystin/oneisle/test/PerfHarnessPacksTest.java`.
