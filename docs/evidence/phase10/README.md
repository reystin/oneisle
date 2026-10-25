# Phase 10 — biome frequency weights (0.8.0, coherent ticket and preset safety in 0.8.1)

All numbers below come from `./gradlew build` on 2026-10-26 (macOS arm64, Java 25;
113 tests, 0 failures, 2 assumption-gated skips) unless a file says otherwise.
Raw data: `calibration.csv`, `calibration_examples.csv`, `heldout_shares.csv`,
`cherry45_metrics.csv`, `cherry45_before_after.png`, `server_smoke.txt`.

## I1 — all-100 is bit-identical

`Phase10BiomeShareTest.allHundredIsBitIdentical`: the biome-source output hash over
64×64 columns is `2951624326361821806` both with the field absent and with an explicit
`biome_share` containing every land biome at 100. The test now pins the active density
function around the hash, so the value is stable regardless of test order. A neutral
export writes no `biome_share` key. `GoldenV1Test`, `GoldenV2Test` and the v2/v3
regression hashes stay green in the same suite.

## Coherent weighted ticket (0.8.1)

0.8.0 selected weighted biomes with an independent per-block SplitMix hash, so every
non-neutral weight produced 1-block noise. 0.8.1 samples a family-independent 3-octave
fBm at `x/420, z/420` salted with `biomeSeed ^ 7919`, flattens it through a static
1024-bin empirical CDF (65,536 fixed samples), and caches the y-independent ticket in
`OmniBiomeSource.ColumnCache`.

* `WeightedTicketTest` 10.5.1: adjacency sampled along rows/columns with step 97 over
  ±2048; `maxStep = 0.01013`, `meanStep = 0.00145`, range `[0.0000, 0.9998]`,
  histogram deviation 1.06 % over 131,072 samples.
* 10.5.2: `ticketFromRaw` is monotone, clamps below 0 to 0, and maps above 2 to
  `> 0.999`; observed range `[0.0000, 1.0000]`.
* The all-100 short-circuit is untouched: the weighted ticket is computed only for
  non-uniform factors, and no `generation_version` was added.

### Cherry-45 regression

`cherry45Draft` uses the reported shape seed `0x306d956984a4bf25` and biome seed `+5`
(the report hides the biome seed; a ±32 seed sweep found the value that reproduces the
displayed 17.4 % Cherry target). NATURAL / ALL / 4000 / v3.

| measurement | value |
|---|---|
| calibration target | 17.319 % |
| fit-grid achieved | 17.303 % |
| held-out real source | 17.817 % (0.498 pp error, 1,869 land columns) |
| worst held-out target ≥0.5 % | 0.92 pp (`meadow`) |

`cherry45_metrics.csv` and `cherry45_before_after.png` compare the exact 0.8.0 white
ticket with the 0.8.1 coherent ticket:

| variant | view | Cherry % | land px | 4-neighbour agreement | isolated |
|---|---|---:|---:|---:|---:|
| 0.8.0 white | island 192×192 | 14.010 | 11,677 | 0.1907 | 0.4913 |
| 0.8.1 coherent | island 192×192 | 16.956 | 11,677 | 0.6741 | 0.0512 |
| 0.8.0 white | 1-block 192×128 | 6.340 | 24,576 | 0.2175 | 0.4385 |
| 0.8.1 coherent | 1-block 192×128 | 24.601 | 24,576 | 0.9938 | 0.0000 |

The island overview agreement includes coastal shore/river pixels, which is why it is
lower than the interior window; it still improves from 19.1 % to 67.4 %. The window is
centred on a representative land area (chosen at 24-block sampling to match the global
Cherry target; 1-block patch geometry gives 24.6 % there).

## Calibration

Fixed 64×64 mask-land grid per configuration; shore band (`signedDistance <=
MAX_INLAND`), rivers and terrain-driven families excluded; baseline measured with zero
bias; targets `s_f·w_f/100 / Σ(s_g·w_g/100)`; damped log-ratio update (0.7, ≤24
iterations, ≤1 pp stop, mean bias 0, bias clamp ±8); feasible when max error ≤ 3 pp.

* 5 presets × 3 themes × 3 vectors = **45/45 feasible**.
* Samples per configuration: 163–1358; iterations: 4–16; max error: 0.986 pp
  (mean 0.726 pp).
* Out-of-theme target (`NATURAL`/`NORDIC`, desert at 200) is flagged infeasible and
  still produces a valid map.
* The calibrated Cherry-45 draft exports a non-empty `biome_share` with
  `cherry_grove=45` and bias `+4.86864690939113`; the dedicated server reports
  `Biome Share: calibrated` and achieved `cherry_grove=17.3%`.

Example rows (full data in `calibration_examples.csv`):

| config | biome | weight | target % | achieved % | error pp |
|---|---|---|---|---|---|
| NATURAL/ALL | cherry_grove | 100 | 31.645 | 31.540 | 0.104 |
| NATURAL/ALL | plains | 300 | 13.796 | 13.855 | 0.059 |
| ROUND/NORDIC | snowy_plains | 150 | 24.565 | 23.962 | 0.603 |
| ROUND/NORDIC | snowy_taiga | 200 | 23.746 | 24.601 | 0.854 |
| ARCHIPELAGO/TROPICAL | mangrove_swamp | 300 | 46.362 | 46.087 | 0.275 |
| ARCHIPELAGO/TROPICAL | bamboo_jungle | 100 | 22.656 | 23.261 | 0.605 |

## World-preset decode and editor preflight (0.8.1)

`WorldPackBuilder.validatePreset(zip, registries)` reads
`data/omniisle/worldgen/world_preset/omniisle.json` from the generated zip and decodes
it with `WorldPreset.DIRECT_CODEC` under the live `RegistryOps` context. The editor
captures that context before the async export; a failure shows
`omniisle.editor.error.preset` and re-enables Done without adding the pack.

* 10.11: a generated pack is decoded through `ServerPacksSource` +
  `RegistryDataLoader.WORLD_REGISTRIES`; the overworld exists, its biome source is an
  `OmniBiomeSource` with a non-neutral `biome_share` (Cherry weight 45, non-empty bias).
* 10.12: a corrupt zip is rejected by `validatePreset` with
  `Generated world preset is not valid JSON`; no exception escapes.

## Fresh dedicated server (0.8.1)

See `server_smoke.txt`. NATURAL/ALL/4000, shape seed `0x306d956984a4bf25`, biome seed
`+5`, Cherry 45:

* pre-launch exported the pack, server reached `Done (1.564s)`, spawn applied on land;
* `omniisle info` reported v3 / 192 / bbox 5440 / calibrated weights and
  `cherry_grove=17.3%`;
* 512 forced chunks generated (27 region files, 29 MB); 0 errors/exceptions; clean stop;
  no new crash report;
* two bulk force-load `Can't keep up!` warnings (expected burst behaviour). Full 25×25
  weighted chunk-generation timing remains **UNVERIFIED**.
* The reported client crash remains **UNVERIFIED**: no report/hs_err/DiagnosticReport;
  the Prism instance also ships `mcopt-metal 0.3.0-alpha.2`, and no Prism mods were
  modified.

## Other acceptance tests

* **Monotonic** (`monotonicWeights`): cherry_grove 0 → 0.00 %, 50 → 20.13 %,
  100 → 32.93 %, 200 → 48.98 %, 400 → 66.10 %.
* **Zero ≡ disabled** (`zeroEqualsDisabled`): identical preview hash and calibration
  bias; re-enabling restores 100.
* **Determinism** (`deterministicAcrossThreads`): 8 threads produce identical result
  fingerprints and bias maps.
* **Climate coherence** (`climateCoherence`): 20,000 extreme-weight samples, R_max =
  0.62; 9 samples exceeded it and all 9 had no candidate inside the cap (max observed
  distance 1.3717). No selection beyond R_max occurred while a closer alternative
  existed.
* **Parity** (`previewMatchesRealSource`): 9,836/9,836 land samples = 100.00 % family
  agreement between `BiomePreview` and a full 38-holder `OmniBiomeSource` with
  non-default weights.
* **Lookup overhead** (`weightedLookupOverhead`): final full-suite value neutral
  5.889 ms vs weighted 5.678 ms (−3.58 %). The benchmark now measures current-thread
  CPU time with 8 warmup rounds and 7 repetitions; repeated runs ranged roughly −5 %
  to −16 % (weighted faster), so the +3 % gate is satisfied. This is a biome-lookup
  benchmark, **not** full chunk generation.
* **Codec/share/preset/undo/bad data** (`codecRoundTripsAndBadData`): weights, bias,
  achieved, target and feasible round-trip; unknown ids are logged and ignored;
  negative weights clamp to 0; 999 clamps to 400; NaN bias is dropped; bias clamps to
  ±8.
* **UI logic** (`uiLogic`): new drafts list every biome enabled at 100; typing 0
  unticks; a positive value re-enables; 999 → 400; −50 untick; invalid text ignored;
  ± steps by 5; reset-all restores 100; terrain-driven rows are read-only.

## Limitations

* Real 25×25 chunk-generation timing with weights was not rerun as an interleaved
  weighted-vs-neutral measurement (`docs/evidence/perf` covers Phase 9 without
  weights); only the lookup benchmark above exists. Status: **UNVERIFIED**.
* The reported client crash is **UNVERIFIED**; see `server_smoke.txt` for the server
  evidence and the `mcopt-metal` note.
* Panel screenshots at GUI scales 2 and 4 were not captured in this environment
  (headless test run). Status: **NOT DONE**.
* Percent entry is click-to-type plus −/+ buttons; there is no drag slider.
