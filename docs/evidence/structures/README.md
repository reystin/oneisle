# Phase 9.4 — structures on real 26.3 servers

This evidence was produced with a headless Fabric 26.3 dedicated server started from the
project (`./gradlew runServer`) for every row. Each config gets a fresh world
(`level-type=omniisle\:omniisle`, `view-distance=4`), the generated datapack is copied
into `run/locate/datapacks/`, and the server is queried through RCON. No placement is
re-implemented in the harness: positions come from `/locate structure` and from
`omniisle query structure`, which reads the real `StructureStart` bounding boxes and
piece list.

Protocol per structure:

1. Force-load a 65×65-block area around the target chunk and wait 1 s so the start is
   fully generated.
2. Run `/locate structure <id>` from the target/spawn and record its block position.
3. Run `omniisle query structure <id> <x> <z>` at the located position (or the fixed
   chunk), recording the real surface y, biome, piece count and the minimum piece y.
4. For strongholds, run `omniisle query dry <x> <z>` at the start to classify the
   centre and the nearest dry land within the structure footprint.
5. At the end of a structure config, run `omniisle query eye` (the same
   `findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, ...)` call used by
   `EnderEyeItem`) and `/locate structure minecraft:stronghold` from spawn.

The per-config request/response logs are kept outside the repository in the harness
work directory (`/tmp/omniisle-harness/logs/`) because they contain whole RCON
transcripts; the committed CSVs are the aggregated evidence.
`STRUCTURE_REPORT_after_v2.csv` holds all 20 rows per full config,
`stronghold_validation.csv` holds the 45-config stronghold check, and
`structure_scaling.csv` is the headless pack proof for the village/trial-chamber
spacing formula. The stronghold-only runs execute the same spawn, forceload,
`/locate`, `query structure`, `query dry`, `query eye` sequence and differ only in
skipping the other 17 structures.

## Matrix (PARTIAL)

A real-server config needs roughly a minute (fresh world, boot, 20 force-loaded
locates), so the measurement was cut as agreed:

- **Full 20-structure sweep: 15 / 45 configs (PARTIAL).** Presets NATURAL, ROUND,
  LONG, CRESCENT, ARCHIPELAGO × sizes 2500/4000/8000 × seed 101. Each config yields the
  17 other structure layers plus the three v3 stronghold starts (20 rows).
- **Stronghold-only real locate: 45 / 45 configs.** Seeds 101/202/303, same
  presets/sizes. These runs reuse the full harness protocol for spawn and the three
  stronghold chunks but skip the other 17 structures, and they also produce the
  45-row spawn evidence.

The reduced matrix is recorded as PARTIAL in `PROGRESS.md`; the 30 configs without a
full sweep have no claim about the other 17 structures.

## v3 stronghold selection

The v3 pack does not use `minecraft:concentric_rings`. `WorldPackBuilder.placeStrongholds`
enumerates real land chunks between the island bounds and the locate reach, rejects
anything closer than 150 blocks to spawn (2500) or 300 blocks (4000/8000), scores by
distance to `min(1200, max(320, 0.22·size))` minus continentalness, and picks one
candidate in each of three sectors 120° apart. Every candidate must be at least 250
blocks from the already chosen starts and on main-island land; shore chunks are a
fallback. The three chunks are written as an ordered `omniisle:fixed_multi` placement,
which extends `RandomSpreadStructurePlacement`, so 26.3's locate scan and the Eye of
Ender path see exactly those starts. This is why the validation table can check the
actual located positions instead of ring arithmetic.

## v3 pyramid sea-level gate

26.3's `SinglePieceStructure#findGenerationPoint` returns empty when
`getLowestY(context, width, depth) < chunkGenerator.getSeaLevel()` (bytecode offsets
20–36); the desert pyramid also checks that a valid biome can exist on top of the chunk
centre. OmniIsle's lowlands are close to sea level and the editor cannot evaluate the
Vanilla ridge/erosion noise, so footprint corners could dip to y=63 and remove an
otherwise valid fixed pyramid. v3 packs therefore override
`minecraft:desert_pyramid` / `minecraft:jungle_pyramid` with
`omniisle:relaxed_desert_pyramid` / `omniisle:relaxed_jungle_temple`; those types reuse
the Vanilla pieces, structure IDs, biome tags and `afterPlace` behaviour but call
`onTopOfChunkCenterWithoutBiomeCheck` directly. v1/v2 packs are untouched (I1).

## Results

Full sweep: **15 configs, 300 rows, 300 found, 300 locate_ok**.

| structure | found | distance (blocks) |
|---|---|---|
| `minecraft:abandoned_camp_forest` | 15/15 | 168–1364 |
| `minecraft:ancient_city` | 15/15 | 242–1306 |
| `minecraft:buried_treasure` | 15/15 | 270–1896 |
| `minecraft:desert_pyramid` | 15/15 | 40–380 |
| `minecraft:igloo` | 15/15 | 83–7863 |
| `minecraft:jungle_pyramid` | 15/15 | 596–4788 |
| `minecraft:mansion` | 15/15 | 66–1485 |
| `minecraft:mineshaft` | 15/15 | 242–2061 |
| `minecraft:monument` | 15/15 | 376–1979 |
| `minecraft:ocean_ruin_cold` | 15/15 | 193–1753 |
| `minecraft:pillager_outpost` | 15/15 | 67–688 |
| `minecraft:ruined_portal` | 15/15 | 173–1420 |
| `minecraft:shipwreck` | 15/15 | 255–1792 |
| `minecraft:stronghold` | 45/45 | 526–1211 |
| `minecraft:swamp_hut` | 15/15 | 88–2853 |
| `minecraft:trail_ruins` | 15/15 | 136–3476 |
| `minecraft:trial_chambers` | 15/15 | 95–436 |
| `minecraft:village_plains` | 15/15 | 126–609 |

Stronghold-only sweep: **45/45 configs pass** all checks (three starts, locate,
spawn distance, mutual distance, dry land, eye).

| size | configs | spawn distance min–max | min mutual | on land | eye targets |
|---|---|---|---|---|---|
| 2500 | 15 | 526.0–551.9 (median 539.8) | 273.9–738.8 | 15/15 | 15/15 |
| 4000 | 15 | 868.4–875.4 (median 869.2) | 480.0–1346.8 | 15/15 | 15/15 |
| 8000 | 15 | 818.8–1193.6 (median 1190.2) | 714.3–1996.6 | 15/15 | 15/15 |

Minimum mutual distance across all 45 configs: **273.9 blocks**.
Nearest dry land from a start centre: 0 blocks ×115, 4 ×8, 8 ×6, 12 ×4, 20 ×1,
24 ×1 (112-block limit). Thresholds: spawn distance ≥ 150 (size 2500) / ≥ 300
(4000, 8000), mutual ≥ 250.

The 8000 minimum of 818.8 blocks is a CRESCENT island whose mask leaves less
buildable land in one 120° sector; it is still far above both the 300-block
requirement and the vanilla first-ring distance. The `stronghold` distance range in
the full-sweep table is 526–1211 because that run only covers seed 101 and sizes
2500/4000/8000 for the five presets.

The 15 full-sweep runs and their stronghold-only reruns agree on spawn x/z, marker,
biome, land and dry flags (`overlap check: mismatches=0`). Two 8000 configs report a
different `spawn_y` (87 vs 79) between the runs: the ground block was
`pale_oak_leaves` in one and `pale_moss_carpet` in the other, i.e. a feature placed
before or after the query, not a different spawn. Those two are written to the
harness log as Y-NOTEs, not CSV mismatches.
