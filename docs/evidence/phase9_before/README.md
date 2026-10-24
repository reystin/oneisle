# Phase 9.1 audit — evidence (before changes)

Re-measurement of the claims in `SUMMARY_0.6.0.md`, produced by
`src/test/java/com/hack337/omniisle/test/Phase9AuditTest.java` and
`GoldenV1Test` on branch `fabric/26.3` at commit `2465187` (phase9.0).

Reproduce:

```
JAVA_HOME="$HOME/Library/Application Support/PrismLauncher/java/java-runtime-epsilon" \
  ./gradlew test --tests com.hack337.omniisle.test.Phase9AuditTest \
  -Domniisle.evidence=true
```

Heavy CSVs/PNGs are written only with `-Domniisle.evidence=true`.

## 1. Stronghold distance — suspicious number confirmed as an artefact

| Claim | Measured | Verdict |
|---|---|---|
| Stronghold at 24–77 blocks from spawn | The 0.6.0 harness (`StructurePlacementHarness:172-181`) computes `abs(ringRadius - spawnDistance)` with `ringRadius = scaledDistance*64` and **no placement search**. Running it twice with identical arguments gives 24.12 both times for stronghold, but other structures move by hundreds of blocks because `IslandDraft` fills seeds with `new Random().nextLong()` (`IslandDraft.java:169`). | **Claim false.** 24.12 is the difference between a hypothetical ring radius (640 blocks) and the spawn radius, not a placed stronghold. |
| Actual ring positions | Replicates the 26.3 `ConcentricRingsStructurePlacement` loop (`radius = 4*d + d*ring*6 + (rand-0.5)*d*2.5` chunks, rounded). Seed 101, NATURAL: ring 0 lands at `(-6,-36)` chunks = 574.8 blocks from origin; distance from spawn 136.4 (size 2500), 218.9 (4000), 461.3 (8000). | **Ring 0 is too close to spawn (136 blocks at size 2500) and may be in ocean.** Fixed in phase 9.4. |

The harness nondeterminism was fixed during 9.1: `evaluateLegacy/evaluateModern` now
call `setShapeSeed(seed)`/`setBiomeSeed(seed)`, and `Phase5StructureTest` does the
same. Two consecutive report runs are byte-identical. This makes the "after"
reports reproducible but does **not** make the fake stronghold arithmetic real;
phase 9.4 replaces it with actual placement/locate.

Evidence: `stronghold_harness_nondeterminism.csv` (before fix),
`stronghold_harness_fix.txt` (after fix), `stronghold_ring_geometry.csv`.

## 2. Ocean floor — not flat, but the deep band is accidental

- `ModernTerrainV2` uses `base = -0.08 - 0.92*s/Wo`, a soft floor `max(c, -0.80)`,
  `smoothstep` seabed weight, fBm seabed and ridged trenches; global clamp `[-1.0, 0.70]`.
- 36 radial rays × seeds 101/202, samples every 10 blocks offshore 0–3000:
  `c_min = -1.000000`, `c_max (s >= 350) = -0.704052`, longest consecutive
  samples below the deep-ocean threshold = 2880 × 10 blocks, 94.02 % of offshore
  samples are in the deep band. 6000×6000 height PNG produced.
- Verdict: **not flat at −0.92**; a deep band exists but `[0.4*Wo, 1.0*Wo]` is not
  guaranteed by construction (positive seabed noise only fades in beyond `1.0*Wo`).
  Phase 9.3 will make the guarantee explicit under v3.

Evidence: `ocean_radial_profiles.csv`, `ocean_profile_summary.csv`,
`ocean_height_6000_before.png`.

## 3. Performance — the 0.088 ms/chunk claim is not real chunk generation

Measured the 2D density sampler (4-block noise columns) for a 25×25-chunk area,
3 repetitions:

| version | ns per 100×100 quarters | ms per 25×25 chunks | ns per column |
|---|---|---|---|
| v1 | 608000 / 605667 / 163958 | 0.973 / 0.969 / 0.262 | 60.8 / 60.6 / 16.4 |
| v2 | 3533292 / 3356042 / 3318833 | 5.653 / 5.370 / 5.310 | 353.3 / 335.6 / 331.9 |

`v2` is ~5.5× the sampler cost of v1, but this excludes noise router, carvers,
features, structures and lighting. **Full chunk generation was not measured in
this phase — marked `UNVERIFIED`.** The `domainAxes()` 2D-cache claim is based on
code inspection (`IslandDensityFunction` implements `domainAxes()` without
`AXIS_Y`; `minecraft:cache` is a valid 26.3 type per `FINDINGS.md:45-59`), not on
an isolated timing test; phase 9.4/9.7 must measure it on a real server.

Evidence: `density_sampling_perf.csv`.

## 4. Preview/server parity — tautological at 100 %, but rivers break it

- Real `OmniBiomeSource` (`getNoiseBiome` through a `Climate.Sampler`) vs
  `BiomePreview.surface` on **256×256** surface points, rivers disabled:
  overall/ocean/shore/land/land-family = **65536/65536 = 100 %**.
  This is expected because both sides call the same `IslandClimate`/shore code:
  the test proves there is no wiring drift, not that the preview is independently correct.
- With the 0.6.0 default `river_density = 0.35`, 697/16384 columns are river
  biomes and land-family agreement drops to **88.3 %** because `BiomePreview.surface`
  has no river parameter. This is a real preview/server divergence.

Evidence: `parity_before_summary.csv`, `parity_before_mismatches.csv`,
`parity_before_diff.png`, `parity_rivers_on_summary.csv`, `parity_v1_summary.csv`
(v1 source vs preview: 40000/40000 zones, 40000/40000 exact biomes, 15289/15289
land families).

## 5. Golden tests — extended in phase 9.1

- Existing binary golden: 30 configs (5 presets × 3 reliefs × sizes 2500/8000),
  10 000 LCG terrain points, 200×200 preview biome grid, 20 deep-dark samples.
  Audit check: `mask.sampleTerrainValue` vs real `LegacyTerrainV1` over 10 000
  points → max difference **0.0**.
- Fixed: the generator iterated all 12 presets while writing a 30-config header;
  it now iterates the same 5 v1 presets and never silently regenerates a missing file.
- Added: `src/test/resources/golden/v1/golden_v1_source.txt` — real
  `OmniBiomeSource` snapshot for all 30 configs: 200×200 surface grid plus
  4 underground Y levels × 100 LCG points, stored as FNV-1a hashes.
  Regenerate only with `-Domniisle.regenerateGolden=source`.

Evidence: `golden_audit.txt`, `src/test/resources/golden/v1/golden_v1_source.txt`.

## 6. Ancient City — placement does not require Deep Dark

The generated tag `has_structure/ancient_city` is `deep_dark` **plus one selected
surface biome** (e.g. `jagged_peaks`), and the structure set uses
`omniisle:fixed` at chunk `(2,-73)`. A city can therefore generate under a
surface biome while the user has Deep Dark disabled. Marked for fix in phase 9.4.

Evidence: `ancient_city_audit.txt`.

## 7. World border default — off, with a copy bug

`IslandDraft.worldBorder` defaults to `false`, `worldBorderMargin` to 1000, and
the JSON codec default is `false`. Existing worlds are unaffected. However
`IslandDraft.copy()` silently drops `worldBorder`, `worldBorderMargin` and
`riverDensity`; recorded here, fixed in phase 9.4.

Evidence: `world_border_default.txt`.

## 8. Climate gradient — large family skew

Biome-family fractions per theme with gradient off/on (128×128 land samples,
seed 202): cherry grove 32.7–33.4 %, swamp 11.0–16.7 %, pale garden 5.0–6.7 %,
snowy/taiga nearly absent in ALL/STANDARD. `ALL` and `STANDARD` produce identical
fractions at this config, i.e. the theme temperature span is not actually used
for the gradient in 0.6.0. Phase 9.5 makes the gradient theme-aware.

Evidence: `climate_family_fractions.csv`.

## Shoreline baseline (input for phase 9.2)

72 equally spaced contour samples on NATURAL/ROUND (seed 101/202/303, size 4000)
give **100 % shore biome** at `d ≈ 6`; CRESCENT 49–50/49–50. The 6000×6000 biome
PNG shows a continuous pale sand/stony ribbon around the whole island.

Evidence: `shore_ribbon_before.csv`, `biome_before_6000.png`.

## Honest status of the audit items

| Item | Status |
|---|---|
| 1 stronghold | Measured (geometry + fake harness); real `locate` **NOT DONE** (phase 9.4) |
| 2 ocean floor | Measured (radial CSV + PNG); flatness conclusion documented |
| 3 performance | Density sampler measured; full chunk generation **UNVERIFIED** |
| 4 parity | 256×256 + diff PNG + rivers-on gap measured |
| 5 golden | Extended with real source snapshot; **DONE** |
| 6 ancient city | Audited; fix pending in 9.4 |
| 7 world border | Verified off; copy bug found |
| 8 climate | Fractions measured; theme-gradient issue found |
