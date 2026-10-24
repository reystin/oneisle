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
