# Phase 9.4 — spawn across 45 configs

> **STALE (Phase 13):** these measurements were taken while v3 packs still emitted the
> `omniisle:spawn_plateau` density dome, which forced `marker_match = 45/45`. v3 now removes the
> dome and accepts the editor marker only if its column is dry, otherwise it searches rings
> 16–512 for a dry column at server start. World spawn may therefore differ from the marker (x/z),
> and **this 45/45 marker-match result no longer describes current behavior**. The `land`, `dry`
> and ray-probe columns are retained as historical baseline; a new sweep was not performed.

Every row was measured on a headless 26.3 Fabric server with a fresh world. The server
writes the config's `spawn` block into the generated pack, and the world spawn is read
back with `omniisle query spawn` (it uses `ServerLevel.getRespawnData().pos()`, the same
position the player starts at). The editor marker is the gold crosshair derived from
`IslandDraft.effectiveSpawnPx()`; the pack stores its rounded world block coordinate.

Matrix: 5 presets (NATURAL, ROUND, LONG, CRESCENT, ARCHIPELAGO) × 3 sizes
(2500/4000/8000) × 3 seeds (101/202/303) = 45 configs, all v3. The planned 90-config
spawn-only extension (seeds 404/505/606) was dropped: a real-server config takes about
a minute, not seconds, so the agreed fallback is 45. Seed 101 rows additionally belong
to the 15-config full-structure sweep; seeds 202/303 are stronghold-only runs.

Metrics in `spawn_evidence_45.csv`:

- `marker_match` — real block spawn equals the pack's marker (x/z).
- `land` — `IslandTerrain.signedDistance(spawn)` > 0.
- `dry` — feet air, ground neither air nor fluid.
- `nearest_water` — first water hit along 8 rays, sampled every 4 blocks from 8 to 64
  (65 means no water within 64).
- `coast_distance` — first non-land sample on 16 rays up to 96 blocks (999 means none).
- `dry_probe_ok/total` — dry samples out of all ray probes; a full count means every
  ray stayed dry to 64 blocks.
- `terrain_elevation` — the island continentalness spline estimate at the marker; the
  real surface also includes vanilla ridge/erosion noise.

The v3 spawn plateau (`omniisle:spawn_plateau`) lifts the top of the density field in an
80-block radius around the marker, so a basin whose real 3D terrain would flood cannot
put the player in water. It does not flatten high ground: spawns on hills keep their
surface height.

The marker x/z, biome, land and dry flags are stable when the same config is generated
again. `spawn_y` is the heightmap height at query time and can differ when a feature
(tree canopy vs moss carpet) has or has not been placed in the spawn chunk yet; the
overlap check between the 15 full-sweep runs and their stronghold-only reruns compares
x/z, biome, land and dry, and reports a y difference separately when one occurs.

## Results

**45/45 marker match, 45/45 land, 45/45 dry, 45/45 feet not in water.**

- 42/45 configs stayed dry on every ray probe out to 64 blocks.
- nearest water: min 56, median 65, max 65 blocks; 42/45 have no water within
  64 blocks.
- 45/45 have no coast sample within 96 blocks (the spawn is inland or the island
  edge is farther away).
- 15/45 spawn in the `river` biome but on dry ground; the v3 surface is above the
  water at the marker, so the biome label does not imply standing in water.

Spawn coordinates repeat per preset/size for the non-archipelago presets regardless of
the world seed (for example NATURAL 2500 uses (9, −664) for seeds 101/202/303);
ARCHIPELAGO differs per seed because its main component moves. `spawn_y` follows the
local terrain and ranges from 78 to 129. The full `spawn_evidence_45.csv` also carries
`terrain_elevation`, `feet_block`, `ground_block` and the ray counts.
