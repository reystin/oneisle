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
    Because `AXIS_Y` is omitted, Minecraft's compiler automatically allocates and caches a 2D column slice (`DensityBuffer`).

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
- sand 0.2214, stony 0.0390, none 0.7396 (targets 15–35 %, 3–10 %, ≥55 %).
- Run constraints: 0 failures (longest ≤500 blocks, median ≤200, ≥10 runs per island); width CV ≥0.35 passed.
- Density sweep: 0.0→0 %, 0.2→5.0 %, 0.4→50.6 %, 0.6→71.0 %, 0.8→71.2 %, 1.0→71.2 % (monotone, 70–90 % at 1.0).
- Bay/headland: 282 bay points sand 0.3227 vs 497 headland points sand 0.1288, ratio 2.51 (≥2 required).
- Preview/server parity: 18,382/18,382 = 100 %, 1,044 samples in the shore zone.
- Border line-fit (tolerance 2.5 blocks): longest straight segment 56 blocks; longest border 155 blocks with 7.07 max deviation / 2.85 RMS (curved, not straight).
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
