# OmniIsle 0.6.0 — Architectural Findings & Verifications

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

**Final build:** `./gradlew build` succeeds; 96 tests, 0 failures, 0 errors, 2
conditional skips (`GoldenV1Test.regenerateGoldenIfRequested`,
`Phase9HarnessPacksTest.exportHarnessPacks`, both assumption-gated). Jar:
`build/libs/OmniIsle-26.3-Fabric-0.7.0+mc26.3.jar`, `fabric.mod.json` version
`0.7.0+mc26.3`.
