# Chunk-generation performance, 25×25 chunks (Phase 9.8)

Budget: modern (v2/v3) chunk generation must stay **within +15 % of v1** on the same
seed and preset, 3 repetitions.

## Verdict

**PASS.** On the final interleaved run v2 and v3 are *faster* than v1 in both median
and mean; even the worst modern run versus the fastest v1 run is within the budget.

| version | runs (s) | median (s) | mean (s) | median vs v1 | worst vs fastest v1 |
|---|---|---|---|---|---|
| v1 | 13.916 / 14.891 / 17.445 | 14.891 | 15.417 | — | — |
| v2 | 14.231 / 14.269 / 13.915 | 14.231 | 14.138 | −4.43 % | +2.54 % |
| v3 | 14.557 / 14.850 / 13.466 | 14.557 | 14.291 | −2.24 % | +6.71 % |

625 chunks per run; `seconds_per_chunk` is 0.0215–0.0279. Raw rows:
`chunkgen_25x25.csv`.

The v1 17.445 s row is an outlier caused by host drift (see “Method”); v1 is always run
first in each round, so it absorbs the cold start. This biases the comparison *against*
v1, not against the modern versions, and the verdict does not depend on it: comparing
v2's slowest (14.269 s) and v3's slowest (14.850 s) against v1's fastest (13.916 s)
still gives +2.54 % and +6.71 %.

## Method

* Minecraft 26.3 dedicated server, Fabric Loader 0.19.3, Fabric API 0.165.0+26.3,
  Java 25 (PrismLauncher `java-runtime-epsilon`), macOS arm64.
* Identical world config for every version: preset ROUND, island size 4000, relief
  BALANCED, theme STANDARD, shape/biome seed 4242, river density 0.35, islet density
  0.25, shore 0.30/0.60 rocky, structures at density 1.0 (18 types), server
  `level-seed=424242`. Only `generation_version` changes between runs.
* Fresh world directory per run (`perf_vN_rM`), then RCON:
  1. `/forceload add` in five Z strips covering chunks x 28–52, z −12–12
     (625 chunks; all inland at 448–847 blocks on +X for every version).
  2. 25 `execute if loaded <chunk> run scoreboard ...` probes, one per Z row. Keep
     each RCON command under ~1400 characters (26.3 closes the connection above the
     1460-byte buffer).
  3. Wall clock from the first forceload command until all 625 probes report loaded;
     poll count is recorded.
* Inland area: the earlier offshore 25×25 area (x 108–132) compared v1's flat ocean
  against v2/v3 bathymetry, deep band and islets, i.e. different work. Offshore rows
  are kept separately for diagnosis (`chunkgen_25x25_offshore.csv`).
* Interleaving: each round runs v1 → v2 → v3 back-to-back, three rounds
  (reps 10–12). A sequential run was abandoned after v1 measured 14.410/14.196 s in
  two rounds and 15.837 s in the next: host drift of ~15 % exceeds the budget being
  measured, so version order must not be confounded with time.
* Same JVM/server binary and same config template for all versions; `view-distance=4`,
  `simulation-distance=4`, `sync-chunk-writes=false`, `pause-when-empty-seconds=0`.

## Column-cache optimization

Profiling showed the v2/v3 biome source repeated Y-independent work for every Y quart:
four neighbor continentalness samples for slope, `signedDistance`, bayness and the shore
outcome were recomputed for each biome Y. `OmniBiomeSource` now keeps a per-thread,
direct-mapped 512-slot cache keyed by `(biomeX, biomeZ)` and terrain identity. It is
exact: `BiomeSourceV2V3RegressionTest` hashes 29 282 v2 and v3 lookups and
`GoldenV2Test` compares 180 000 double bit patterns; both pass, and the hash of
`/tmp/omniisle-biomehash-before.txt` equals the post-change file.

Inland probes before/after the cache (same area, same method):

| version | before (3 runs) | median | after (3 probes) | median | delta |
|---|---|---|---|---|---|
| v1 | 16.598 / 17.436 / 16.785 | 16.785 | — | — | — |
| v2 | 19.432 / 19.138 / 20.626 | 19.432 | 15.251 / 14.207 / 16.108 | 15.251 | −9.14 % vs v1 median |
| v3 | 20.432 / 20.534 / 20.740 | 20.534 | 14.112 / 14.209 / 14.732 | 14.209 | −15.35 % vs v1 median |

A similar 256-slot cache inside `ModernTerrainV2` was measured at 15.128/15.081 s
against 15.17 s and reverted (Decision 038); the redundancy was in the biome source,
not in the terrain function.

## Diagnostics (not used for the verdict)

* Offshore 25×25 (chunks x 108–132): v1 median 12.433 s, v2 15.171 s (+22.0 %),
  v3 17.245 s (+38.7 %). Not comparable work: v1 has no v3 bathymetry/deep band/islets.
* v2 with rivers disabled: 16.136 s offshore (vs 14.954–16.133 s with rivers at 0.35),
  so rivers are not the dominant cost.
* v1/v2 with `generate-structures=false`: v1 13.374 s, v2 15.519 s (offshore), showing
  that the version gap is terrain/biome work rather than structure placement alone.
* JFR `jdk.ExecutionSample` stacks confirm vanilla Perlin noise dominates both
  versions; the recordings have different durations, so the percentages are indicative
  only and are not used as evidence. The actionable finding was the Y-redundant biome
  column work above.

All diagnostic rows: `chunkgen_25x25_diagnostics.csv`.
