# Phase 10 — biome frequency weights (0.8.0)

All numbers below come from `./gradlew test` on 2026-10-25 (macOS arm64, Java 25).
Raw data: `calibration.csv` (45 configurations) and `calibration_examples.csv`
(per-biome target/achieved for three example configurations).

## I1 — all-100 is bit-identical

`Phase10BiomeShareTest.allHundredIsBitIdentical`: the biome-source output hash over
64×64 columns is `3408052023102023366` both with the field absent and with an explicit
`biome_share` containing every land biome at 100. A neutral export writes no
`biome_share` key. `GoldenV1Test`, `GoldenV2Test` and the v2/v3 regression hashes stay
green in the same suite.

## Calibration

Fixed 64×64 mask-land grid per configuration; shore band (`signedDistance <=
MAX_INLAND`), rivers and terrain-driven families excluded; baseline measured with zero
bias; targets `s_f·w_f/100 / Σ(s_g·w_g/100)`; damped log-ratio update (0.7, ≤24
iterations, ≤1 pp stop, mean bias 0, bias clamp ±8); feasible when max error ≤ 3 pp.

* 5 presets × 3 themes × 3 vectors = **45/45 feasible**.
* Samples per configuration: 163–1358; iterations: 3–24; max error: 1.88 pp
  (mean 0.79 pp).
* Out-of-theme target (`NATURAL`/`NORDIC`, desert at 200) is flagged infeasible and
  still produces a valid map.

Example rows (full data in `calibration_examples.csv`):

| config | biome | weight | target % | achieved % | error pp |
|---|---|---|---|---|---|
| NATURAL/ALL | cherry_grove | 100 | 31.645 | 31.214 | 0.430 |
| NATURAL/ALL | plains | 300 | 13.796 | 12.958 | 0.837 |
| ROUND/NORDIC | snowy_plains | 150 | 24.565 | 23.962 | 0.603 |
| ROUND/NORDIC | snowy_taiga | 200 | 23.746 | 24.281 | 0.535 |
| ARCHIPELAGO/TROPICAL | mangrove_swamp | 300 | 46.362 | 45.870 | 0.492 |
| ARCHIPELAGO/TROPICAL | bamboo_jungle | 100 | 22.656 | 23.261 | 0.605 |

## Other acceptance tests

* **Monotonic** (`monotonicWeights`): cherry_grove 0 → 0.00 %, 50 → 20.13 %,
  100 → 32.93 %, 200 → 48.98 %, 400 → 66.10 %.
* **Zero ≡ disabled** (`zeroEqualsDisabled`): identical preview hash and calibration
  bias; re-enabling restores 100.
* **Determinism** (`deterministicAcrossThreads`): 8 threads produce identical result
  fingerprints and bias maps.
* **Climate coherence** (`climateCoherence`): 20,000 extreme-weight samples, R_max =
  0.62; 9 samples exceeded it and all 9 had no candidate inside the cap (max observed
  distance 1.3717, desert forced to max bias in a non-desert climate). No selection
  beyond R_max occurred while a closer alternative existed.
* **Parity** (`previewMatchesRealSource`): 9,836/9,836 land samples = 100.00 % family
  agreement between `BiomePreview` and a full 38-holder `OmniBiomeSource` with
  non-default weights.
* **Lookup overhead** (`weightedLookupOverhead`): median weighted 7.457 ms vs neutral
  8.623 ms over 48×48 columns × 5 repetitions (−13.52 %; run-to-run noise, but the
  +3 % gate passed). This is a biome-lookup benchmark, **not** full chunk generation.
* **Codec/share/preset/undo/bad data** (`codecRoundTripsAndBadData`): weights, bias,
  achieved, target and feasible round-trip; unknown ids are logged and ignored;
  negative weights clamp to 0; 999 clamps to 400; NaN bias is dropped; bias clamps to
  ±8.
* **UI logic** (`uiLogic`): new drafts list every biome enabled at 100; typing 0
  unticks; a positive value re-enables; 999 → 400; −50 untick; invalid text ignored;
  ± steps by 5; reset-all restores 100; terrain-driven rows are read-only.

## Limitations

* Real 25×25 chunk-generation timing with weights was not rerun (`docs/evidence/perf`
  covers Phase 9 without weights); only the lookup benchmark above exists. Status:
  **UNVERIFIED**.
* Panel screenshots at GUI scales 2 and 4 were not captured in this environment
  (headless test run). Status: **NOT DONE**.
* Percent entry is click-to-type plus −/+ buttons; there is no drag slider.
