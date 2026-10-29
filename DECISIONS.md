# OmniIsle 0.6.0 — Architectural Decisions Log

> **Historical record.** Decisions 001–076 were made under the OmniIsle name; references to
> OmniIsle, its package paths and version numbers in those entries are historical. OneIsle
> 1.0.0 (Decision 077) renamed the runtime identity and republished the project.

This log records every design choice, trade-off, and architectural resolution made during the development of OmniIsle 0.6.0.

---

### Decision 001: Verification of Distance Transform Algorithm
* **Question:** Does `IslandMask.java` use an 8-neighbor chamfer transform or Felzenszwalb-Huttenlocher EDT?
* **Options:**
  1. Chamfer transform (as claimed in draft spec §4.2).
  2. Exact Felzenszwalb-Huttenlocher EDT (as claimed in draft spec §8.1).
* **Choice:** Option 2.
* **Reason:** Verified directly in `IslandMask.java` (lines 120-173). `edt1d` implements the exact $O(N)$ parabolic lower envelope algorithm. The terracing artifact is caused by bilinear interpolation across coarse $28\dots 57$ block grid cells, not by a chamfer approximation.

### Decision 002: Cache Wrapper Name in Density Function JSON
* **Question:** Is `"type": "minecraft:cache"` valid in Minecraft 26.3, or should older types (`flat_cache`, `cache_2d`) be used?
* **Options:**
  1. Change to `flat_cache` or `cache_2d`.
  2. Keep `"minecraft:cache"`.
* **Choice:** Option 2.
* **Reason:** Decompiled `net.minecraft.world.level.levelgen.densityfunction.DensityFunctions` in 26.3 verifies that `CacheFunction` is registered solely under `"minecraft:cache"`. Because `IslandDensityFunction.domainAxes()` omits `AXIS_Y`, the 26.3 compiler automatically treats it as a 2D column cache.

### Decision 003: Golden Regression Strategy for Invariant I1
* **Question:** How to guarantee 100% byte-for-byte backward compatibility for worlds generated with OmniIsle 0.5.0?
* **Options:**
  1. Rely on code inspection.
  2. Implement an automated golden regression oracle (`GoldenV1Test`) with frozen inputs and outputs.
* **Choice:** Option 2.
* **Reason:** Invariant I1 requires that old worlds keep generating identical terrain. Storing binary golden samples for all 5 presets × 3 reliefs × 2 sizes before making any algorithmic changes provides an unbreakable verification safety net.

### Decision 004: 4-Tier Height Layer Storage Representation
* **Question:** How should the 4-tier height layer be stored and serialized?
* **Options:**
  1. A separate `.bin` file in the datapack.
  2. 2-bit packed Base64 string inside `continents.json` as `height_layer: { "bits_per_pixel": 2, "data": "..." }`.
* **Choice:** Option 2.
* **Reason:** Minecraft density functions do not support loading arbitrary raw binary files from datapacks without native code. A 2-bit packed Base64 representation takes only 9 216 bytes for a 192×192 grid, integrates naturally with `IslandShapeConfig.CODEC`, and preserves side-neutral portability.

### Decision 005: Compressed Coastal Rise for Mountain Ridge Headroom
* **Question:** How to prevent the continentalness field from flattening into a single cone or saturated plateau?
* **Options:**
  1. Hard clamp base at 0.70 and add ridges on top (creates plateau at 0.70).
  2. Keep unbounded linear base rise $d \times 0.70 / coastWidth$ and apply tanh saturation (saturates to 0.95 across entire island).
  3. Compress coastal rise using `base = -0.08 + 0.48 * tanh(d * 1.25 / coastWidth)` so base levels off at ~0.40 inland.
* **Choice:** Option 3.
* **Reason:** Leaves sufficient dynamic range (0.40 to 0.95) for procedural ridged mountain spines and painted height tiers to form multiple distinct peaks, mountain ranges, and valleys without saturating.

### Decision 006: Stronghold Ring Scaling and Land-Guaranteed Spawn
* **Question:** How to guarantee that the stronghold and player spawn always generate on land on smaller island sizes (e.g. 2500)?
* **Options:**
  1. Force stronghold to fixed coordinates.
  2. Scale the concentric rings `distance` parameter with island radius: `scaledDistance = clamp(8, 32, round(32 * islandSize / 8000))`.
* **Choice:** Option 2 for Stronghold, combined with `SpawnSavedData` and `findSafeSpawn()` spiral search on first world load.
* **Reason:** Stronghold generation in 26.3 uses `concentric_rings` where chunk radius is $\approx 4 \times distance$. At distance=32 chunks, the first ring is at $r \approx 2048$ blocks, falling into the deep ocean on 2500 and 4000 block islands. Scaling `distance` brings the first ring to $r \approx 640$ blocks on size 2500, placing strongholds reliably on land. For spawn, an explicit crosshair editor tool and auto-spawn SDF finder with first-load spiral relocation guarantees players never spawn in water.

### Decision 007: Quantization Alignment for Client-Server Climate Parity
* **Question:** How to achieve exact ocean/shore/land classification parity between client `BiomePreview` and server `OmniBiomeSource` across float/double arithmetic?
* **Options:**
  1. Approximate matching with an arbitrary tolerance margin.
  2. Exactly mirror Minecraft's `Climate.quantizeCoord((float) continentalness)` truncation `(long)((float) continentalness * 10000.0f) / 10000.0` in `IslandClimate.sample`.
* **Choice:** Option 2.
* **Reason:** Minecraft's internal `TargetPoint` quantizes coordinates by `(long)(f * 10000.0F)` (truncating towards zero) and unquantizes with `(float)l / 10000.0F`. Mirroring this in `IslandClimate.sample` achieves 100% ocean/shore/land zone parity (65,536/65,536 sampled points) across the entire island and ocean domain, satisfying Invariant I3.

### Decision 008: Multi-Resolution Draft Architecture, Deflate-Compressed Share Codes, and Border Invariants
* **Question:** How to implement compact share codes, dynamic canvas resolutions (192, 256, 384), and new procedural presets without breaking Invariant I1?
* **Options:**
  1. Store raw uncompressed JSON in share codes, reload canvas from scratch on resolution change, and apply border suppression to all presets.
  2. Use compact binary payload with Deflate compression and CRC16 checksum (`OMNI2-<base64url>-<crc16>`), bilinear resampling for masks and heightmaps on resolution change, and restrict procedural border suppression strictly to new presets (`ordinal >= 5`).
* **Choice:** Option 2.
* **Reason:** Uncompressed JSON codes exceed clipboard and chat limits; Deflate compression with CRC16 ensures safe transmission and tamper resistance while enforcing a strict 256 KiB decompressed payload cap. Resampling masks and heightmaps with bilinear interpolation allows users to switch resolutions seamlessly without losing their painted islands. Restricting border zeroing strictly to new presets guarantees that the 5 legacy presets generate 100% bit-for-bit identically with version 0.5.0, upholding Invariant I1. Furthermore, in `ShareCode.decode()`, mask data must be unpacked before `draft.setSpawn(px, pz)` because spawn validation requires land check against the imported mask.

### Decision 009: Deterministic Physically Carved Rivers, Dynamic Biomes, and World Border
* **Question:** How to implement realistic river carving, dynamic biome catalogs, and island-bounded world borders without breaking continuity or backward compatibility?
* **Options:**
  1. Store explicit river lines in JSON, use discrete lake stamps, and require hardcoded biome lists.
  2. Derive rivers deterministically at load via Priority-Flood depression filling and D8 flow routing with $C^1$ continuous `smoothstep` carving falloff; discover biomes dynamically from `WorldCreationContext` with 56-biome fallback; and configure vanilla `WorldBorder` on world load when enabled.
* **Choice:** Option 2.
* **Reason:** Deriving rivers at load requires 0 extra bytes in `continents.json`. Using `smoothstep(1.0 - dr / width)` eliminates step discontinuities across cell boundaries and preserves the $< 0.015$ per-block continuity invariant. In `IslandShapeConfig.CODEC`, defaulting `river_density` to `0.0` when absent guarantees that pre-existing worlds without a river slider keep generating 100% bit-for-bit identically (Invariant I1). The dynamic biome registry allows modded biomes (e.g. Terralith) to be used while maintaining robust fallbacks for headless tests.

### Decision 010: Phase 9 baseline normalization and deterministic evidence
* **Question:** The checkout arrived from Windows with CRLF and quarantine attributes; prior test CSVs are regenerated on every build with random island seeds. How should the baseline be committed?
* **Choice:** Normalize text files to LF via `.gitattributes`, keep generated CSVs out of the phase commits, and replace the random-seed harness with deterministic seeded evidence in Phase 9.1/9.4.
* **Reason:** A build must not mutate tracked deliverables, and structure claims must be reproducible. Line-ending churn is isolated in one baseline commit so later phase diffs stay reviewable.

### Decision 011: Windows target paths on this macOS host
* **Question:** The task asks to mirror the final jar to `C:\Game\prism\...\mods` and `c:\Users\reystin\Downloads`, but the build host is macOS.
* **Choice:** Map them to `~/Library/Application Support/PrismLauncher/instances/Vulkan Optimization/minecraft/mods` and `~/Downloads` respectively.
* **Reason:** Those are the native macOS equivalents and the prompt permits documenting path mappings. Both destination files and hashes will be reported in the final report.

### Decision 012: v3 explicit shore field instead of the continentalness ribbon
* **Question:** How to replace the uniform v1/v2 beach ribbon with patchy beaches, rocky headlands and no-shore cliffs while keeping old worlds bit-identical?
* **Options:**
  1. Keep the `continentalness < 0.02` band and scale it by noise.
  2. Add an explicit `ShoreField` driven by signed distance `d`, a multi-scale patch mask (380/130/55/17 + 70-block gap mask), noise-varied inland width (3–26 blocks, ×0.25–1.15 factor, 27-block notch), 8-point bayness (R = 60), slope and a noise-jittered intertidal limit; nested optional `shore` config used only when `generation_version = 3`.
* **Choice:** Option 2.
* **Reason:** Distance and biome-family gates are orthogonal: the field can create pockets, bays, headlands and cliffs without touching terrain or the v1/v2 code path. `shore` is absent in v1/v2 configs, so the legacy branch remains byte-identical; v3 configs default to `ShoreSettings.DEFAULT` (density 0.30, variety 0.6, natural, rocky). The inland width is capped below `MAX_INLAND` (34) so the width contour, not the hard cutoff, defines the landward beach edge.
* **Evidence:** `docs/evidence/shore/shore_acceptance_summary.txt`, `shore_density_sweep.csv`, `shore_bay_headland.txt`.

### Decision 013: Straightness check uses a line-fit, not a cell-drift heuristic
* **Question:** The prompt forbids straight biome borders > 120 blocks "check with a line-fit on the debug mask". What counts as straight?
* **Options:**
  1. Link border cells across mask lines when the boundary moves by ≤1 cell; call the run straight.
  2. Build one-to-one border chains in world coordinates, fit a principal-axis line (PCA), and treat a chain as straight only when every point is within 2.5 blocks (half a 5-block cell) of that line.
* **Choice:** Option 2.
* **Reason:** Option 1 flags natural shallow arcs: the north pole of a round island produced a 155-block "straight" chain with 7.1 blocks of bow, which is visibly curved. The line-fit matches the prompt's wording and the mask quantization. Measured result: longest straight segment 56 blocks; longest border 155 blocks with 7.07 blocks max deviation.
* **Evidence:** `docs/evidence/shore/shore_straight_border.txt`, `shore_border_chains.csv`, `shore_mask_4000_101.png`.

### Decision 014: v3 coastal terrain easing and v1/v2 structure-sampling preservation
* **Question:** How to make terrain agree with shore outcomes without violating continuity or changing legacy worlds?
* **Options:**
  1. Leave terrain untouched (biome-only change).
  2. In `ModernTerrainV2`, for v3 only, multiply coastal relief by 0.62 inside beach pockets and 1.18 on no-beach coasts, using smooth masks that fade at the waterline and at the inland width edge; keep `BiomePreview.surface(...)` 4-arg sampling for v1/v2 structure placement in `WorldPackBuilder`.
* **Choice:** Option 2.
* **Reason:** The relief scaling is applied to `c + 0.08` before river carving, so shorelines stay at the same height and rivers are unaffected. Worst measured per-block delta is 0.0109, below the 0.015 bar. Using the modern sampler for v1/v2 structure placement changed the audited Ancient City surface biome (dappled_forest), so legacy placement keeps the old sampler until Phase 9.4 redesigns placement deliberately.
* **Evidence:** `docs/evidence/shore/shore_continuity.csv`; full test suite green (62 tests, 1 skipped golden gate).

### Decision 015: v3 ocean ramp and seabed-noise gating
* **Question:** How to replace the legacy linear ocean ramp (`-0.08 - 0.92·s/Wo`) while keeping `c(s=0) = -0.08` and guaranteeing a deep monument band?
* **Options:**
  1. Keep the linear ramp and just clamp at −0.80; rely on a post-hoc clamp inside the band.
  2. Use a two-stage smoothstep ramp for v3: `-0.08 - 0.54·S(s/0.4Wo) - 0.18·S(s/Wo)`, both with zero derivative at the coast and at their endpoints; weight all seabed noise by `S(s/0.5Wo)` so it is exactly 0 at the shoreline; gate positive seamount noise by `s > 1.0·Wo` with a 0.3·Wo fade-in.
* **Choice:** Option 2.
* **Reason:** The first stage reaches ≈−0.68 at 0.4·Wo including trench noise, safely below the −0.58 deep threshold, while the second stage settles onto the −0.80 soft floor. Zero derivative at both stages removes the coastline step and the old hard floor intersection. The `S(s/0.5Wo)` weight keeps the waterline `c` at −0.08 and makes the band's maximum independent of positive noise. The global clamp is only `[-1.0, 0.70]`, so trenches can reach −1.0 and the soft floor is not flat.
* **Evidence:** `docs/evidence/ocean/ocean_band_summary.csv`, `ocean_radial_profiles_v3.csv`, `ocean_profile_summary_v3.csv`, `ocean_height_6000_v3.png`.

### Decision 016: Locate-visible fixed structure placement and deep-band monument target
* **Question:** `/locate structure` never found OmniIsle's fixed structure starts, and the monument was placed in ordinary ocean (`c ≈ -0.40`). How should fixed placement and monument targeting work?
* **Options:**
  1. Keep `AbstractSpreadingStructurePlacement` and accept that `/locate` cannot see the structure; keep the old −0.40 target.
  2. Extend `RandomSpreadStructurePlacement` with spacing 1 / separation 0 / LINEAR and override `applyAdditionalChunkRestrictions` to accept only the fixed chunk; for monuments, require the 5×5 chunk-centre neighbourhood to stay below −0.62 and score toward `c = -0.72`.
* **Choice:** Option 2.
* **Reason:** 26.3's locate scan only walks random-spread and concentric-ring placements, so a non-random placement is invisible regardless of `isPlacementChunk`. Spacing 1 makes every chunk a candidate; without the restriction override, `StructureCheck` runs the full 29-block monument biome survey for all ~40k candidates and trips the 60 s watchdog. The 5×5 (chunk-resolution) check mirrors the survey's 29-block radius and rejects islet skirts: a centre at `c = -0.72` can still have a 29-block neighbour at −0.566 near an islet, which the single-sample check missed.
* **Evidence:** `docs/evidence/ocean/ocean_monument_band.csv` (headless) and `ocean_monument_locate.csv` (real 26.3 server `/locate` + biome probes).

### Decision 017: Pack metadata uses min_format/max_format for 26.3
* **Question:** 26.3 reported the generated datapack as `(incompatible)` even though it loaded and ran.
* **Options:**
  1. Keep the legacy `pack_format: 121` field.
  2. Emit `min_format: 121` and `max_format: 121` for 26.3 packs.
* **Choice:** Option 2.
* **Reason:** For pack formats above 81 the 26.3 `PackFormat`/`PackMetadataSection` codec requires `min_format`/`max_format`; a bare `pack_format` is accepted by the parser only as a legacy integer but the pack is flagged incompatible. The server log no longer shows the warning after the change. No version constants were changed (`PACK_FORMAT` stays 121).
* **Evidence:** `docs/evidence/ocean/pack_metadata.txt` (version.json + server log excerpt) and the `v3OceanMonumentTargetsDeepBand` export test.

### Decision 018: v3 spawn plateau as a density-function wrapper
* **Question:** The editor marker can fall in a basin whose real terrain floods, because inland height is driven by vanilla erosion/ridge noise that the editor mask cannot evaluate.
* **Options:**
  1. Raise terrain in the mask/terrain sampler and accept an island-shape side effect for every version.
  2. Wrap the v3 `final_density` in `omniisle:spawn_plateau`, which only lifts the top ~26 blocks inside an 80-block radius dome around the marker; leave v1/v2 density untouched.
* **Choice:** Option 2.
* **Reason:** The plateau is a local, dry guarantee at the exact editor marker while preserving the painted island and v1/v2 bit-identity (I1). The dome rim drops below sea level, so it reads as a small headland rather than a floating disc; the density wrapper delegates caves and deep terrain to the original function. `final_density_base.json` holds the vanilla-derived function so the wrapper does not self-reference; the override is written only for `generation_version = 3`.
* **Evidence:** 45-config spawn sweep (`docs/evidence/spawn/spawn_evidence_45.csv`) and `ROUND_4000_101` probe output in `docs/evidence/spawn/`.

### Decision 019: Fixed stronghold list instead of concentric rings for v3
* **Question:** How can the three first-ring strongholds satisfy land, spawn-distance, pairwise-distance and Eyes-of-Ender requirements on an arbitrary painted island?
* **Options:**
  1. Keep vanilla `concentric_rings` and hope the preferred-biome filter finds land.
  2. Choose three exact chunks at export time, enforce `min spawn distance` (300 for ≥4000, 150 otherwise) and pairwise ≥250, prefer main-island land with a shore fallback, and encode them as an ordered `omniisle:fixed_multi` placement.
* **Choice:** Option 2.
* **Reason:** Concentric rings cannot guarantee the constraints on an island of arbitrary shape and can place starts outside the locate radius. `fixed_multi` extends `RandomSpreadStructurePlacement` with spacing 1 and restricts candidates to the exact list, so 26.3's locate scan (which ignores other placement classes) sees all three starts; the eye-of-ender path uses the same `findNearestMapStructure` machinery. The three chunks are sorted by spawn distance for stable CSV ordering.
* **Evidence:** `docs/evidence/structures/stronghold_validation.csv`, `stronghold_placement.csv`, and real-server `/locate` + `omniisle query eye` rows in the structure report.

### Decision 020: Fixed placements use a presence tag, not a distribution filter
* **Question:** Three fixed structures were missing in real worlds because the preview biome at the chosen chunk differed from the runtime biome source (river vs swamp, cherry grove vs taiga, meadow vs plains).
* **Options:**
  1. Report the three misses as honest failures.
  2. For fixed placements, list every enabled biome in the `has_structure` tag while still pinning the placement to one chunk, and report the real biome at the start in the CSV.
* **Choice:** Option 2.
* **Reason:** A fixed placement has exactly one candidate chunk, so the biome tag cannot spread the structure elsewhere; it only gates presence. The editor preview and the runtime source can disagree by one family at a structure start that shifts within the chunk. Listing all enabled biomes guarantees the enabled structure exists and lets the report state its actual biome; it does not alter random-spread village/trial tags. The broadened tag is written for v3 only: a review caught the first cut applying it to v1/v2 too, which would have changed legacy worlds; v1/v2 now keep the 0.6.0 single-preview-biome tag JSON byte-for-byte (I1), and `Phase9StructureTest.legacyV1AndV2KeepSingleBiomeFixedStructureTags` locks that in.
* **Evidence:** `STRUCTURE_REPORT_after_v2.csv` — three previously missing rows found; the biome column records river/cherry_grove/plains as generated.

### Decision 021: Structure measurements force-load and wait before locate/query
* **Question:** First harness runs produced `y = 90` for ocean structures and different `y` values for the same seed between fresh worlds.
* **Options:**
  1. Trust the first `/locate`/query result immediately.
  2. Force-load a 65×65-block area around the target, wait 1 s for the structure start to finish generating, then run `/locate` and `omniisle query structure`; report `y_min`/`y_max` from the real `StructureStart` pieces.
* **Choice:** Option 2.
* **Reason:** The first query could observe a partial `StructureStart` (ocean monuments reported the sea surface instead of their actual pieces). All 15 full-sweep configs use the stabilized protocol; the repeatability run was dropped from the reduced measurement plan, so no repeatability claim is made. `StructureStart#getBoundingBox()` is adjusted by the structure, so `y_min`/`y_max` are the physical piece extents.
* **Evidence:** `docs/evidence/structures/STRUCTURE_REPORT_after_v2.csv` (each row stores `y_min`, `y_max`, `start_chunk` and the locate chunk) and `stronghold_validation.csv`.

### Decision 022: v3 pyramids override the vanilla single-piece sea-level gate
* **Question:** The fixed desert and jungle pyramids disappeared on lowland islands even when the chosen chunk sat far inland: 26.3's `SinglePieceStructure#findGenerationPoint` rejects the start when `getLowestY(21×21) < seaLevel`, and OmniIsle's c≈0.6 lowlands routinely have 21×21 footprint corners at y 58–63 because of vanilla ridge/erosion noise.
* **Options:**
  1. Report the misses and keep vanilla structure codecs.
  2. For v3 packs only, override `minecraft:desert_pyramid` and `minecraft:jungle_pyramid` with `omniisle:relaxed_desert_pyramid` / `omniisle:relaxed_jungle_temple`, reusing the vanilla pieces, biome tags, structure IDs and `afterPlace` (desert loot/suspicious sand) but placing them with `onTopOfChunkCenterWithoutBiomeCheck`.
* **Choice:** Option 2.
* **Reason:** The editor cannot evaluate vanilla ridge/erosion noise, so no client-side chunk score can predict the footprint's lowest block; the hard gate is the only blocker. The relaxed types use the same `DesertPyramidPiece` / `JungleTemplePiece`, keep `minecraft:desert_pyramid` / `minecraft:jungle_pyramid` registry IDs for `/locate` and the report, and only change the placement height rule. The override is written only when `generation_version >= 3`, so v1/v2 packs remain byte-identical in behavior (I1). The earlier `DRY_SURFACE_MIN_ELEVATION` workaround is removed because it also pushed the desert pyramid into forest chunks without fixing the gate.
* **Evidence:** `NATURAL_8000_101` real-server log: before, `desert_pyramid found=false` at chunk 2,-140 (footprint corner water y=63) and `jungle_pyramid found=false` at -14,166 (three corners y=63 water); after, both found (desert y=61, jungle y=64) and all 20 structure rows in that config report `found=true`. The reduced full sweep is PARTIAL: 15/45 configs in `docs/evidence/structures/STRUCTURE_REPORT_after_v2.csv`; strongholds are checked on all 45 in `stronghold_validation.csv`.

### Decision 023: explicit theme temperature spans, no climate behavior change
* **Question:** The 9.1 audit reported that the latitudinal gradient ignored the selected theme's temperature span (ALL and STANDARD produced identical fractions). The 9.5 requirement is that amplitude be a fraction of the selected theme's span.
* **Options:**
  1. Narrow STANDARD's span to make ALL and STANDARD differ.
  2. Keep the measured spans (ALL/STANDARD 0.90, MYSTIC 0.44, NORDIC 0.32, TROPICAL/DESERT 0.28) and make the formula explicit: `offset = u · 0.5 · span · strength`, default strength 0.5.
* **Choice:** Option 2.
* **Reason:** Both ALL and STANDARD palettes contain the full snowy→desert temperature range, so sharing the 0.90 span is correct; Nordic/Tropical/Desert are narrower. Changing STANDARD only to manufacture a difference would alter existing worlds and invalidate the Phase 9.4 real-server evidence without a physical reason. The 9.1 audit sentence was too broad: Nordic, Desert and Tropical already produced different fractions; only ALL/STANDARD alias. The formula is now a table plus `IslandClimate.temperatureSpan`/`gradientAmplitude`, and `0.5 · span · s` equals the old `maxSpread · s` for every theme, so v1/v2/v3 temperatures are unchanged.
* **Evidence:** `docs/evidence/climate/climate_family_fractions_v3.csv` (theme × gradient off/on/max), `theme_families_6000.png`; `Phase9ClimateTest.themeTemperatureSpansAreFractionsOfTheSelectedPalette`.

### Decision 024: keep the existing family-border jitter, measure it for real
* **Question:** Requirement 9.5.2 asks for ±8 % family-border jitter so no straight border exceeds 400 blocks. The 0.6.0 code already adds ±0.04 temperature / ±0.032 humidity value noise at a 96-block cell, but the old "6.3" test computed a run counter and never asserted it.
* **Options:**
  1. Increase the jitter blindly.
  2. Keep the existing jitter (v2 and v3 contain it; changing it would break I1 for v2 and invalidate the Phase 9.4 evidence for v3) and replace the vacuous test with a real border-chain + line-fit measurement of the current sampler.
* **Choice:** Option 2.
* **Reason:** A 4-block line-fit over family borders sampled every 10 blocks finds a longest straight segment of 230 blocks on v3 (limit 400, all six themes × both gradient modes) and 130 blocks on the v2 sampler, so the existing jitter is sufficient. ±0.04 is 8 % of the 0.5 neutral climate axis, so the documented amplitude matches the requirement. Any increase would change existing v2/v3 worlds without a measured need.
* **Evidence:** `docs/evidence/climate/climate_straight_borders_v3.csv`, the regenerated v2 measurement printed by `Phase6ClimateTest` step 6.3, and `Phase9ClimateTest.v3StraightFamilyBordersStayUnder400Blocks`.

### Decision 025: reduced Phase 9.4 measurement matrix (PARTIAL)
* **Question:** The plan asks for a 45-config full 20-structure sweep and 90 spawn configs, but a real 26.3 server config costs roughly a minute (fresh world, boot, force-loaded locates).
* **Options:**
  1. Run the full 45-config, 20-structure matrix and the 90-config spawn extension.
  2. Measure the full 20-structure set on 15 configs (5 presets × 3 sizes × seed 101), run the stronghold-only real locate on all 45 configs (seeds 101/202/303) for the distance/land/eye thresholds, produce spawn evidence from the same 45, and skip the ROUND_4000_101 repeat.
* **Choice:** Option 2.
* **Reason:** The stronghold acceptance gates (spawn distance, mutual distance, on land, Eyes of Ender) are checked on all 45 configs; the other 17 structures are measured on 15. The matrix is marked PARTIAL (15/45 full) in `PROGRESS.md` and the final report, and the 30 configs without a full sweep carry no claim about the other structures. The repeatability run was dropped, so no repeatability claim is made; the 15 overlapping configs are used as a consistency check instead.
* **Evidence:** `docs/evidence/structures/STRUCTURE_REPORT_after_v2.csv` (300 rows, 15 configs), `stronghold_validation.csv` (45/45 pass), `docs/evidence/spawn/spawn_evidence_45.csv`; the overlap check reports 0 mismatches and two feature-dependent `spawn_y` Y-NOTEs.

### Decision 026: radial symmetry paints forward orbits instead of rotated disks
* **Question:** 6-way/8-way symmetry initially stamped the brush disk at each rotated center. A closure test showed the result was not symmetric: `(126,34)` was painted but its 60° image `(164,91)` was not, because `Math.round` rotation is not reversible at pixel edges.
* **Options:**
  1. Relax the test and accept approximate radial symmetry.
  2. For every pixel in the source disk, paint its complete forward orbit under the rasterized transform; the painted set is then closed by construction.
* **Choice:** Option 2.
* **Reason:** The requirement is exact symmetry, and the forward-orbit closure is cheap for editor-sized masks (a reusable stamp buffer and queue, no per-call allocation on the worldgen path). Mirrors and 4-way are unchanged. Land and height painting both use the same closure, so line/rect/ellipse/fill strokes inherit it.
* **Evidence:** `Phase9EditorTest.radialSymmetryTest` (6-way paint+line+ellipse, 8-way paint+filled rect) and the closure check over every painted pixel. The pre-fix failure coordinate is recorded in `FINDINGS.md`.

### Decision 027: heightmap import uses land-only 25/50/75 % quantiles
* **Question:** Requirement 9.6.5 says heightmap import should use the threshold for land and quantiles for tiers.
* **Options:**
  1. Map luminosity linearly from the threshold to 255.
  2. Collect luminosity only on land pixels, sort, and split at q25/q50/q75; map `<=q25` to tier 0, `<=q50` to 1, `<=q75` to 2, else 3.
* **Choice:** Option 2.
* **Reason:** The threshold selects the mask; quantiles adapt tiers to the image's own contrast instead of assuming a 0–255 range. A flat land region has no quantile information, so it imports as tier 1. Excluded water keeps tier 1.
* **Evidence:** `Phase9EditorTest.heightmapQuantileImportTest` (exact 0/85/170/255 → 0/1/2/3, flat → 1, inverted threshold). This also changes the Phase 7 PNG test: the exported 4-shade height layer is a visualization, not a standalone mask, so it is checked for exact shade encoding rather than re-imported as a land mask.

### Decision 028: normalized viewport and one texture slot
* **Question:** Zoom/pan must survive resolution changes and must not leak a `DynamicTexture` across editor sessions.
* **Options:**
  1. Store pan in preview pixels and release the texture in every screen lifecycle branch.
  2. Store zoom/pan as normalized map coordinates (`PreviewViewport`) and route registration/release through `PreviewTextureSlot`.
* **Choice:** Option 2.
* **Reason:** Normalized coordinates are resolution-independent and unit-testable; the slot enforces exactly one release per registration and reuses the texture while resolution is unchanged. The preview is drawn with the verified 26.3 `GuiGraphicsExtractor.blit` 12-argument subregion overload (source UV + source width/height).
* **Evidence:** `Phase9EditorTest.viewportZoomPanTest`, `PreviewTextureSlotTest` (50 sessions → 50/50), `UI_CHECKLIST.md`.

### Decision 029: six tabs, compact layout, screenshots marked NOT DONE
* **Question:** The old editor put every control in one column, which did not fit at GUI scale 4.
* **Options:**
  1. Keep one column and shrink labels.
  2. Split controls into six tabs, two rows of three, with a compact row height; extract the math into `EditorLayout` and assert fit at scale 1–4 logical sizes.
* **Choice:** Option 2.
* **Reason:** At 1080p, GUI scale 4 gives 480×270 logical pixels. Compact mode uses 16-px rows/18-px steps, a 140-px preview and a 184-px control panel; content bottom 216, panel bottom 222, Done bottom 250. The biome/structure side list is hidden in compact mode; the existing `BiomeSelectionScreen` buttons remain the path to the full lists. Real screenshots were not captured in this environment (no automated client screenshot harness), so the screenshot acceptance item is explicitly NOT DONE rather than claimed.
* **Evidence:** `EditorLayout`, `Phase9EditorTest.layoutFitsGuiScales`, `UI_CHECKLIST.md`, `docs/screens/README.md`.

### Decision 030: coast warp scales quadratically below the 4000 reference
* **Question:** The first coast gate only enforced the 2 px maximum at size 4000. Measuring size 2500 too revealed ARCHIPELAGO seed 101 at 2.5091 px, above the requirement, because one editor pixel is 17.71 blocks at 2500 versus 28.33 at 4000 and the same block-space warp costs more pixels.
* **Options:**
  1. Keep the gate at 4000 and report the 2500 measurement as out of scope.
  2. Scale the v3 warp by `0.5 · (blocksPerPixel / 28.3333)²` with a 0.15 floor, leave 4000/8000 at their existing factors, and enforce the threshold at both measured sizes.
* **Choice:** Option 2.
* **Reason:** The quadratic gives 0.195 at 2500 while keeping 0.5 at 4000 and the 1.0 clamp at 8000, so no previously accepted world changes at the reference sizes. Measured max displacement: 2.5091 px at warp 0.35, 2.2262 px at 0.3125, 1.3579 px at 0.195. The 4000 worst stays 1.5778 px. Lowering only the floor did nothing below 0.3125 because the size formula, not the floor, was binding — that is why the exponent changed.
* **Evidence:** `docs/evidence/coast/coast_displacement.csv` (30 configs), `coast_displacement_summary.txt` (`criterion_applies_to_sizes,2500;4000`, worst mean 0.2955, worst max 1.5778, failures 0), `Phase9CoastFidelityTest`.

### Decision 031: v3 river threshold follows drainage; carve continues into the ocean branch
* **Question:** With the v2 flow-accumulation threshold, no river reached the waterline: the maximum coastal accumulation was 192 cells while the density-0.45 threshold was 556, so every river cell was an inland lake. Even after lowering the threshold, a river crossing the coast would step by the full 0.14 carve at the land/ocean branch.
* **Options:**
  1. Keep the v2 threshold and accept rivers that stop inland.
  2. For `generation_version >= 3` only, use `thresholdFactor = 0.0004 + (1−density)·0.0040` (≈96 cells at 0.45) and subtract the same river carve on the ocean side of the branch.
* **Choice:** Option 2.
* **Reason:** The v3 threshold scales with the measured drainage instead of an arbitrary factor 5.8× larger; v2 stays bit-identical for I1. The ocean-side carve removes the discontinuity exactly where the code switches from land to ocean formulas, without changing the carve shape inland. The mouth test now sees 10 mouths, 2,799 below-sea samples and 1,618 carved samples.
* **Evidence:** `docs/evidence/rivers/river_mouth_summary.txt`, `river_mouth_samples.csv`, `river_waterline_crossings.csv`; `Phase9RiversTest`.

### Decision 032: river bank and ocean continuity budgets are analytic, not per-ray
* **Question:** A first version flagged a 0.0248 continentalness delta per block as a "step" and flagged an oblique ocean ray whose `c` changed 0.02 while the shoreline distance changed 5.57 blocks per ray block. Both were smooth fields measured along the wrong axis.
* **Options:**
  1. Flatten the carve/ramp until the raw per-ray deltas fit an arbitrary threshold.
  2. Derive a bank budget from the carve shape (`0.14 × 1.5 / 6 = 0.035 c/block`), measure river banks in the normal direction and at gravity-descended waterline crossings, and normalize ocean deltas by `max(1, |Δ signed distance|)`.
* **Choice:** Option 2.
* **Reason:** The budget is per block of normal shoreline distance, and a radial ray can meet the coast obliquely. Flattening the fields would remove real relief to satisfy a measurement artifact. Measured maxima: river bank 0.024772, waterline 0.021143 (budget 0.035); ocean normalized delta within the 0.02/0.05 bands; river surface slope 1.958 < 2.7.
* **Evidence:** `Phase9RiversTest` (`BANK_DELTA_BUDGET`, `WATERLINE_DELTA_BUDGET`), `Phase9OceanTest` normalized continuity assertions, `docs/evidence/rivers/`.

### Decision 033: dedicated-server pack export runs at preLaunch
* **Question:** With the export in `ServerLifecycleEvents.SERVER_STARTING`, the first boot of a fresh world enabled only the mod's built-in pack; the generated zip existed in `world/datapacks` but was not in `datapack list enabled`, and `omniisle info` reported legacy v1. Fabric invokes main entrypoints when `MinecraftServer` is constructed, which is after `WorldLoader` has already scanned the pack list.
* **Options:**
  1. Require the pack to be copied into the world before the first start.
  2. Add a `preLaunch` entrypoint that writes `<universe>/<level-name>/datapacks/omniisle-generated.zip` from `config/omniisle.json` before `WorldLoader` scans, keeping the `SERVER_STARTING` handler as an integrated-server fallback.
* **Choice:** Option 2.
* **Reason:** Only pre-launch is early enough while still reading the normal config file. The world folder is resolved the same way the server does: working directory + optional `--universe` (both `--universe X` and `--universe=X`) + `level-name` from `server.properties` (default `world`). The generated classes used by the exporter are Minecraft-free, so they load safely before the game. No manual copy is needed and the pack can shape the very first spawn chunks.
* **Evidence:** `docs/evidence/server/README.md`, `server_startup.log` (preLaunch export 21:58:12, world creation 21:58:19, advancements 21:58:20, Done 21:58:22), `rcon_world_info.txt` (`file/omniisle-generated.zip (world)`, Generation Version 3, Rivers 45 %), `rcon_verify.txt`; `OmniIslePreLaunch`.

### Decision 034: cache biome-lookup column data per thread
* **Question:** Every biome lookup sampled four y-independent continentalness neighbors, plus `signedDistance` and shore outcome in v3. The chunk generator asks for each XZ column at every Y quart (about 24.6 k lookups per chunk), so the same column was recomputed at ~96 Y levels.
* **Options:**
  1. Leave the redundant sampling; `jdk.ExecutionSample` stacks showed only ~5 % of samples in the biome source, so it looked minor.
  2. Add a fixed-size per-thread column cache keyed by `(terrain identity, biomeX, biomeZ)` storing slope, half-span, signed distance and shore outcome; invalidate naturally when the active terrain changes.
* **Choice:** Option 2, with the terrain reference as the owner key (the terrain is already the source of truth for the sampler in `getNoiseBiome`).
* **Reason:** All cached values are pure functions of the XZ column; continentalness in `IslandDensityFunction` ignores Y. The cache writes only primitive arrays (no per-call allocation) and is thread-local, preserving I4. Hash evidence over 29 282 v2/v3 lookups is bit-identical before/after (`BiomeSourceV2V3RegressionTest`). Real inland generation improved by ~27–31 % and moved v2/v3 below the v1 baseline.
* **Evidence:** `BiomeSourceV2V3RegressionTest` (hashes 0xBABFA0E91DFC5659 / 0x693CB057CB9B9DB7C), `docs/evidence/perf/`.

### Decision 035: performance evidence uses an inland area and interleaved rounds
* **Question:** The first 25×25 area (chunks x=108..132) lies 240–640 blocks offshore of the +X coast at ~1490 blocks. v1 generates flat −1.0 ocean there while v2/v3 add bathymetry, islets and shore-adjacent content, so the comparison mixed new content with algorithmic overhead. Sequential version blocks also drifted by up to 15 % between runs on the same machine.
* **Options:**
  1. Keep the offshore area and sequential order; report the worst numbers.
  2. Make the official run an inland area (chunks x=28..52, z=−12..12) where all versions generate land, and interleave versions per repetition (v1, v2, v3) to distribute thermal/background drift. Keep the offshore run as diagnostic evidence.
* **Choice:** Option 2.
* **Reason:** The budget is about generation overhead for comparable content. Interleaving prevents a fast or slow machine phase from biasing one version. The offshore CSV remains committed so the difference in content cost is visible rather than hidden.
* **Evidence:** `docs/evidence/perf/chunkgen_25x25.csv`, `chunkgen_25x25_offshore.csv`, `docs/evidence/perf/README.md`.

### Decision 036: freeze v2 from an independent 0.6.0 worktree
* **Question:** A v2 golden regenerated from the current code would only prove the code equals itself, not that 0.6.0 worlds keep generating identically.
* **Options:**
  1. Generate the snapshot from current HEAD and call it v2 compatibility.
  2. Check out commit `7a5174d` in a detached worktree, compile the 0.6.0 test harness there, and generate 30 configs × 1 500 points × 4 metrics from that binary.
* **Choice:** Option 2.
* **Reason:** It is an independent oracle. `GoldenV2Test` compares `doubleToLongBits` exactly for `continentalness`, `computeSignedDistance(false)`, `signedDistance` and `erosionBias`; all 180 000 values match current code. This also proves the speculative biome-cache change does not alter v2 terrain.
* **Evidence:** `src/test/resources/golden/v2/golden_v2.bin.gz` (sha256 `abb4bb1b…b96ee`), `golden_v2_source.txt` (provenance: commit `7a5174d`, LCG seed, 30 configs), `GoldenV2Test`.

### Decision 037: keep `minecraft:cache`, remove the "2D slice" claim
* **Question:** Phase 9.8 asked to remove "non-existent `minecraft:cache`" from the spec. `javap` of the 26.3 merged jar shows `op/CacheFunction` registered as `"cache"`, with an `input` codec field and compiler-managed `CachingDensitySampler`; the generated pack uses it and loads.
* **Options:**
  1. Follow the prompt literally and remove a type the game supports.
  2. Keep the type, delete the unsupported claim that omitting Y automatically compiles a 2D slice cache, and document the real mechanism: `domainAxes() = X|Z` plus a `sampleVolume` that writes one value across `sizeY`.
* **Choice:** Option 2.
* **Reason:** Evidence wins over the prompt's example. `SamplerContext$CacheCell` stores a single `valueKey` (packed 3D position), not a 2D column buffer; column reuse is created by our sampler's range fill. The spec now states this and keeps `height_layer` as base64 JSON (no `.bin` file). Also corrected: spawn initialization sets the respawn point, it does not teleport players.
* **Evidence:** `FINDINGS.md` §3 addendum, `javap` output of `CacheFunction`, `DensityFunctionCompiler$PreparedCache`, `SamplerContext$CacheCell`, `IslandDensityFunctionTest`.

### Decision 038: revert the ineffective terrain cache
* **Question:** A per-thread direct-mapped cache was added to `ModernTerrainV2.continentalness`/`signedDistance` to speed up repeated column sampling.
* **Options:**
  1. Keep it because it is exact and might help other workloads.
  2. Revert it; measured inland v2 was 15.128 s with the cache vs 15.17 s without (within noise), and it adds ThreadLocal state to a hot path.
* **Choice:** Option 2.
* **Reason:** No measurable benefit; goldens passed either way. The real redundancy was in `OmniBiomeSource` (Y-repeated column lookups), where the cache is ~30 % faster. Keeping dead complexity would violate the simplicity invariant.
* **Evidence:** pre-cache/post-revert runs in `docs/evidence/perf/README.md`; `git diff` shows no remaining cache in `ModernTerrainV2`.

### Decision 039: nest optional `biome_share` in the biome source and keep neutral configurations byte-identical
* **Question:** The biome source codec already has many top-level fields; DataFixerUpper records are limited to 16 fields. Where should the new optional weights/bias live, and how do old configs stay untouched?
* **Options:**
  1. Add five new top-level fields to `OmniBiomeSource`.
  2. Nest one optional `biome_share` record (`weights`, `bias`, `achieved`, `target`, `feasible`) inside the existing `ThemeShore` group that already carries `biome_theme` and `shore`.
* **Choice:** Option 2. Worldgen consumes the sanitized `bias` map; zero weights are expressed by the enabled list (typing 0 unticks), so the field is emitted only when at least one climate-driven weight differs from 100.
* **Reason:** The nested record stays under the DFU field limit and keeps the base codec unchanged. `Settings.sanitized()` normalizes keys to path-only ids, logs unknown ids, clamps weights to 0–400 and bias to ±8, and drops non-finite values. A missing field, an explicit all-100 map and a neutral export all leave `hasNonNeutralWeight()` false, which selects the legacy code path — no new `generation_version` and no output change (I1 hash `3408052023102023366` in both cases).
* **Evidence:** `Phase10BiomeShareTest.allHundredIsBitIdentical`, `codecRoundTripsAndBadData`; `GoldenV1Test`, `GoldenV2Test`, `BiomeSourceV2V3RegressionTest`.

### Decision 040: multiply the family prior by `exp(bias)` and cap climate distance at 0.62
* **Question:** Calibrated biases must shift shares without placing snowy biomes in hot climate cells. How should bias affect selection while preserving I1 when all biases are zero?
* **Options:**
  1. Pick the highest biased score directly; a large bias can override any climate distance.
  2. Keep the legacy family prior (`1` for the preferred family, `exp(−max(0, d − d_pref)/0.22)` otherwise), multiply by `exp(bias)`, and drop candidates whose normalized centre distance exceeds `R_max`.
* **Choice:** Option 2 with `R_max = 0.62`, bias clamp ±8 and a deterministic uniform hash ticket. If every candidate is outside the cap, the selector retries without the cap so a biome is always returned when the palette is non-empty; uniform factors short-circuit to `legacyEquivalent`.
* **Reason:** A multiplicative factor preserves the climate ordering while allowing large share changes inside the feasible climate neighbourhood. The uniform-ticket path is required because the legacy fbm ticket clusters and makes cumulative shares jump. `R_max` was measured with 20,000 extreme-weight samples: 9 selections exceeded it and all 9 had no candidate inside the cap (maximum distance 1.3717). Allocation-free per-thread scratch buffers keep the hot path clean.
* **Evidence:** `Phase10BiomeShareTest.climateCoherence`, `monotonicWeights`, `deterministicAcrossThreads`, `weightedLookupOverhead`.

### Decision 041: calibrate on a frozen 64×64 land grid with a legacy baseline
* **Question:** Weights define target relative shares, but the noise selector does not produce shares linearly. Where and how should bias be fitted so the editor preview and the exported datapack agree?
* **Options:**
  1. Fit against the live preview texture resolution and render thread, or use random sampling.
  2. Use a fixed 64×64 mask-land grid, exclude the shore band, rivers and terrain-driven families, measure the zero-bias baseline (which is exactly the legacy distribution), then apply damped log-ratio updates.
* **Choice:** Option 2, shared by `BiomeShareCalibrator` (editor) and `WorldPackBuilder.export` (datapack). Targets are `s_f·w_f/100 / Σ(s_g·w_g/100)`; update `bias += 0.7·(ln target − ln max(actual, 0.5 %))`; stop at 1 pp or 24 iterations; subtract the mean bias each round; flag infeasible when max error >3 pp or a weighted biome is unreachable.
* **Reason:** The fixed grid is deterministic, resolution-independent and cheap enough to rerun while editing. Excluding shore/river/terrain cells avoids calibrating biomes whose selection is not climate-share-driven. Damping prevents oscillation from the piecewise family picker. 45/45 preset×theme×vector configurations converge with max error 1.88 pp; out-of-theme targets are flagged instead of forced.
* **Evidence:** `docs/evidence/phase10/calibration.csv`, `calibration_examples.csv`, `Phase10BiomeShareTest.calibrationConverges`.

### Decision 042: keep weights in the draft, recompute off the render thread, and store all-100 as "absent"
* **Question:** Percent editing must be undoable, round-trip through share codes/presets, and refresh the preview without stuttering.
* **Options:**
  1. Recalibrate synchronously on every click and persist every weight, including 100.
  2. Store only non-100 land weights in `IslandDraft`, push undo snapshots on committed edits, debounce recalibration by 150 ms on a daemon executor, and reject stale results with a fingerprint.
* **Choice:** Option 2. Zero removes the biome from the enabled set and its weight entry; re-enabling or resetting stores 100 as an absent entry. The editor's fingerprint is FNV-64 over the share-code JSON of the draft with calibration cleared; results whose fingerprint no longer matches are discarded. Share codes export `biome_share` only when weights are non-neutral or a bias is present; presets export it only for non-neutral weights and always include the freshly calibrated bias/achieved/target/feasible maps.
* **Reason:** Absent-100 keeps the JSON small and makes the neutral case unambiguous for I1. Undo snapshots cover the weight map exactly like other draft fields. The debounce and fingerprint prevent out-of-order background results from overwriting newer edits, and no calibration work runs on the render thread.
* **Evidence:** `Phase10BiomeShareTest.zeroEqualsDisabled`, `codecRoundTripsAndBadData`, `uiLogic`; `IslandDraft.applyBiomeWeightText`, `OmniIsleEditorScreen` calibration tick.

### Decision 043: replace the per-block weighted ticket with a coherent field and a static quantile table
* **Question:** In 0.8.0 every non-neutral weight turned the preview and the real world into 1-block noise because `BiomeShare.weightedTicketFor` hashed `(x, z)` independently. Calibration still needs an approximately uniform ticket so cumulative probabilities map to targets.
* **Options:**
  1. Reuse the legacy fBm ticket directly; it is coherent but strongly clustered, so weighted cumulative shares would jump and calibration would fight the bias.
  2. Use a family-independent coherent fBm at the legacy 420-block scale, flatten it with a static empirical CDF, and cache it per column.
  3. Keep the white hash and blur it in the preview only; real worldgen would stay noisy.
* **Choice:** Option 2. `weightedTicketFor` samples 3-octave fBm at `x/420, z/420` salted with `biomeSeed ^ 7919`, then `ticketFromRaw` indexes a static 1024-bin CDF built once from 65,536 deterministic samples (`TICKET_LUT_SEED = 0x2C9277B5`, step 97 to avoid the noise lattice). The legend ignores the preferred family so adjacent columns cannot flicker across family borders. `OmniBiomeSource.ColumnCache` stores the y-independent ticket. The legacy ticket is still computed only for uniform factors.
* **Reason:** Coherence is restored without changing the target math, the uniform short-circuit, or all-100 output. The 420-block scale matches the legacy patch size. The LUT is seed-independent, immutable and thread-safe; the hot path is allocation-free. Tests: adjacency `maxStep = 0.0101`, `meanStep = 0.00145`, full range `[0.0000, 0.9998]`, histogram deviation 1.06 % over 131,072 samples; the LUT is monotone. The 0.8.0-noise regression window improves from 21.8 % to 99.4 % 4-neighbour agreement and from 43.9 % to 0.0 % isolated pixels.
* **Consequence:** 0.8.0 worlds with non-neutral weights generate differently in 0.8.1 (their old output is the bug). Unweighted/all-100 worlds remain bit-identical; no new `generation_version`.
* **Evidence:** `WeightedTicketTest`, `Phase10BiomeShareTest.previewHasNoSpeckleAtCherry45`, `docs/evidence/phase10/cherry45_metrics.csv` and `cherry45_before_after.png`.

### Decision 044: verify calibrated shares on a held-out grid and reproduce the reported 17.4 % case
* **Question:** 0.8.0 measured `achieved` on the same 64×64 fit grid used to fit the bias, so it could not prove that the real biome source matches the target. The report's hidden biome seed also made the exact 17.4 % Cherry case ambiguous.
* **Options:**
  1. Trust the fit-grid `achieved` numbers.
  2. Add an independent real-source measurement plus a seed sweep for the reported draft.
* **Choice:** Option 2. `heldOutSharesMatchTarget` samples the real `OmniBiomeSource` on a 79×79 grid with pixel offset 0.31, excludes shore/river/terrain families, and compares every target biome. `cherry45Draft` uses shape seed `0x306d956984a4bf25` and biome seed `+5`, which reproduces the screenshot's 17.4 % Cherry target; a ±32 seed sweep was used to find it and then deleted as a temp harness.
* **Reason:** The fit grid is no longer the judge of its own work. Result: Cherry 45 target 17.319 %, achieved 17.303 %, held-out 17.817 % (0.498 pp error, 1,869 land columns); worst target ≥0.5 % error is 0.92 pp. Calibration remains 45/45 feasible with max error 0.986 pp.
* **Evidence:** `docs/evidence/phase10/heldout_shares.csv`, `calibration.csv`, `Phase10BiomeShareTest.heldOutSharesMatchTarget`.

### Decision 045: validate the generated world preset through the live registry before enabling it
* **Question:** The reported world-entry crash had no stack trace. A generated `world_preset/omniisle.json` that fails to decode under the live dynamic registries would crash exactly at world creation, and no test covered that decode.
* **Options:**
  1. Keep the existing export and assume the codec is correct.
  2. Parse the generated zip with `WorldPreset.DIRECT_CODEC` inside the existing `RegistryOps` context before reloading/enabling the pack, and add a real-registry decode test plus a corrupt-preset test.
* **Choice:** Option 2. `WorldPackBuilder.validatePreset(Path, HolderLookup.Provider)` reads `data/omniisle/worldgen/world_preset/omniisle.json`, decodes it, and requires an overworld. `commitDesign` captures `parent.getUiState().getSettings().worldgenLoadContext()` before the async export, validates in the worker, and on failure shows `omniisle.editor.error.preset` while re-enabling Done without adding the pack.
* **Reason:** The test path matches production: `ServerPacksSource.createVanillaPackSource().fullResources()` + `RegistryDataLoader.WORLD_REGISTRIES` (unfiltered) + custom codecs registered after Bootstrap. A valid pack decodes to an `OmniBiomeSource` with Cherry weight 45 and a non-empty bias; a corrupt zip is rejected with a localized validation error and no exception escapes.
* **Evidence:** `Phase10PresetDecodeTest` (10.11/10.12), `WorldPackBuilder.validatePreset`, `OmniIsleEditorScreen.commitDesign`.

### Decision 046: report the client crash as UNVERIFIED, verify the server path, and mark the migration change
* **Question:** The user reported a crash when entering the weighted world, but the instance contained no crash report, no `hs_err_pid*` and no recent DiagnosticReport; the only crash report predates 0.8.0.
* **Options:**
  1. Claim the coherent-ticket fix also fixes the crash.
  2. Reproduce the world on a fresh dedicated server and the dev client, record exactly what was observed, and leave the client crash **UNVERIFIED** if it cannot be captured.
* **Choice:** Option 2. A fresh dedicated server with the exact Cherry-45 draft reached `Done (1.504s)`, applied the pack, reported calibrated weights/achieved shares, and generated 512 forced chunks (27 region files, 33 MB) with zero errors/exceptions and a clean stop. The client instance ships `mcopt-metal 0.3.0-alpha.2`; no Prism mods were modified. The client crash remains **UNVERIFIED** and mcopt-metal is the leading environmental suspect. 0.8.0 weighted worlds are documented as intentionally generating differently in 0.8.1.
* **Reason:** Claiming an unobserved crash fix would violate the evidence standard; the server run does prove the generated preset, worldgen path and startup are healthy. The preset preflight (Decision 045) reduces the blast radius of any future codec failure with a visible error instead of a crash.
* **Evidence:** `docs/evidence/phase10/server_smoke.txt` (startup, `omniisle info`/`verify`, forced-chunk result), `CHANGELOG.md` 0.8.1, `README.md` migration note.

### Decision 047: treat the dry v3 shelf as land by measuring the physical waterline, not `signedDistance = 0`
* **Question:** 0.8.1 returned OCEAN for every `signedDistance ≤ 0` when no shore outcome fired, but the terrain stays dry for 30–40 more blocks because `d = 0` maps to `continentalness ≈ -0.08`. What threshold matches the real waterline?
* **Options:**
  1. Keep the `d ≤ 0` ocean cut and accept a dry shelf painted as ocean.
  2. Use a synthetic estimate of `-0.08`.
  3. Measure the waterline on a real dedicated server and gate OCEAN on both a continentalness constant and an offshore-distance constant.
* **Choice:** Option 3. `V3_SEA_LEVEL_CONTINENTALNESS = -0.18` and `V3_OFFSHORE_OCEAN_DISTANCE = -42.0`. For `d ≤ 0`, OCEAN requires `d < -42` or `c < -0.18`; otherwise LAND, or stony shore on steep/high columns when shore biomes are enabled. The `c < -0.18` test is never applied at `d > 0`.
* **Reason:** The measured transects bracket the waterline at `c = -0.1478…-0.1843` and `d = -32.8…-40.4` (steep coast) and `c = -0.1698…-0.2074` (flat coast). The constants were kept as planned; no deviation. Gating the stony-shore fallback on `shoreAllowed` avoids a hidden stony shore when the user disabled shore biomes. Reference result: 1,844 dry-shelf land columns, 0 dry-shelf ocean columns.
* **Evidence:** `docs/evidence/phase82/waterline_probe.txt`, `shoreline_probe.csv`, `shoreline_transect.png`, `Phase82ReferenceClimateTest.dryShelfIsLandNotOcean`, `previewSourceZoneFamilyParityV3`.

### Decision 048: sync climate mountain bands to the exact physical ridge and cool temperature with elevation
* **Question:** Swamps appeared on stone mountains because climate `mountainBands` sampled unrelated noise and `selectLandFamily` ignored continentalness/ridge height.
* **Options:**
  1. Keep two independent noise fields and add a family-level guard only.
  2. Drive `mountainBands` 70 % from the exact physical ridge used by `ModernTerrainV2` plus 30 % climate noise, keep the same seeded fallback, and apply elevation cooling in the v3 family tree.
* **Choice:** Option 2. `terrainRidge = 1 - 2·|fbm2(x/750, z/750, biomeSeed != 0 ? biomeSeed : 0x5EED1234ABCDL + 801, 3, 2.0, 0.5)|`; `climateRidge = 1 - |fbm(wx/(scale·0.82), wz/(scale·0.82), saltedSeed(biomeSeed, 503))|`; `mountainBands = clamp01(0.70·max(0, terrainRidge) + 0.30·climateRidge)`. `terrainRidge` uses unwarped `x,z`, matching the terrain exactly; climate noise keeps the warped `wx,wz`. `effTemp = clamp01(temp - max(0, c - 0.28)·0.62)`.
* **Reason:** The physical and climate fields now agree (correlation 0.9808), so high terrain is cold and is never classified as swamp; swamps/mangroves additionally require `c < 0.15 && mountains < 0.42`. Reference map: 0 swamp columns above either threshold.
* **Evidence:** `Phase82ReferenceClimateTest.mountainBandsMatchPhysicalRidge`, `referenceCompositionMatchesDesign`, `docs/evidence/phase82/mountain_sync.csv`.

### Decision 049: replace the v3 family tree with an ordered reference-continent tree
* **Question:** The old v3 tree produced ~84 % broad temperate green (dominated by forest), no cohesive warm/arid region, and no guarantee that peaks stay snowy.
* **Options:**
  1. Tune the old tree's thresholds.
  2. Add a v3-only ordered tree: offshore mushroom, elevation cooling, high mountains, foothills, swamp/mangrove lowlands, cold, cool, mid-temperate, warm/arid; keep `selectLandFamily` untouched for v1/v2.
* **Choice:** Option 2 with the branch order and thresholds in §3 of the approved plan: high mountains `c > 0.45` (or `c > 0.30 && mountains > 0.56`) → 15/14/6/5; foothills `c > 0.30 && mountains > 0.42` → 1/6/5; swamps/mangroves at `c < 0.15 && mountains < 0.42 && hum > 0.64`; cold `<0.25`; cool `<0.36`; mid `<0.72`; warm/arid else with desert only at `c ≤ 0.36`. `V3_GRADIENT_SCALE = 0.55`, `V3_TEMPERATURE_NOISE_SCALE = 1.15`.
* **Reason:** The reference map now meets every acceptance gate: core temperate 68.05 % (65–70 %), green total 79.05 %, peak columns 5,015 with 135 non-14/15, foothills 4,289/3,575 green, warm/arid 607 at max `c = 0.2871` with a 97.36 % largest component and 100 % within 400 blocks of the coast, swamp violations 0, inland mushrooms 0. dappled forest is family 3 (31/932 family-3 columns); pale garden is family 17 (27/136,337 = 0.02 %). The old method is untouched for v1/v2, so goldens pass unmodified.
* **Evidence:** `Phase82ReferenceClimateTest.referenceCompositionMatchesDesign`, `dappledAndPaleAppearNaturally`, `reference_composition_after.csv`, `temp_achieved_vs_target.csv`, `reference_map_after.png`.

### Decision 050: make mushrooms offshore-only and expose main-island distance without changing v1/v2
* **Question:** The old fixed inland mushroom district put mushroom fields on the main island, and structure/climate code had no way to distinguish a satellite islet from the main landmass.
* **Options:**
  1. Keep the fixed inland circle and approximate the main island with `signedDistance`.
  2. Add `IslandTerrain.Distances(signed, main)` with an islet-merged `signed` and a main-island `main` distance, override it in `ModernTerrainV2` by computing the main SDF once, and gate mushrooms on `main < 0` or an outer-bay dry pocket with a coherent mask.
* **Choice:** Option 2. Family 16 is allowed only when `main < 0` or (`signed ≤ 0 && signed > V3_OFFSHORE_OCEAN_DISTANCE`) and `c < 0.05`, with `fbm(x/650, z/650, saltedSeed(biomeSeed, 811)) > 0.22`; never inside the main island. `signedDistance` delegates to `distances().signed()`, and `WorldPackBuilder.mainIslandDistance` uses `.main()` (no behavior change). `OmniBiomeSource.ColumnCache` stores the y-independent v3 surface result so the extra ridge fBm is not run per Y quart.
* **Reason:** Reference map: 0 inland mushroom columns, 202 offshore mushroom columns out of 812 offshore land columns (24.9 % of offshore land, <3 % of total land). Preview/real-source zone-family parity is 99.02 %, including the dry shelf and disabled-shore case. v1/v2 default `Distances` is the identity, so their output is unchanged.
* **Evidence:** `Phase82ReferenceClimateTest.referenceCompositionMatchesDesign`, `previewSourceZoneFamilyParityV3`, `docs/evidence/phase82/mushroom_offshore.csv`, `GoldenV1Test`, `GoldenV2Test`.

### Decision 051: strict per-structure policies with no category fallback and a runtime surface guard
* **Question:** 0.8.1 fixed structures used the highest-continentalness candidate in a category, and `RelaxedPyramids` skipped biome/elevation/slope checks. That put desert pyramids on mountain columns.
* **Options:**
  1. Keep the fallback and tune scores.
  2. Apply v3-only family/continentalness/slope/zone policies, require the actual point family, disable a set with no valid candidate, emit narrow tags, and restore runtime validation in `RelaxedPyramids`.
* **Choice:** Option 2. The policy table is in §6.1 of the plan (`CHANGELOG`/spec summarize it); scoring uses closeness to `preferC` + low slope + a small spawn-distance penalty, never highest continentalness. No candidate → `enabled=false`, no tag point, export still succeeds. `allowsSurface` checks biome path, `validBiome`, center/±8 base heights, `maxY`, 8-block relief, slope, and active OmniIsle continentalness/slope; rejection returns `Optional.empty()`.
* **Reason:** The same reference draft that 0.8.1 placed at `c=0.6148` in CHERRY_GROVE (desert pyramid) and `c=0.6142` in BAMBOO_JUNGLE (jungle temple) now has desert/jungle/igloo sets disabled (desert and jungle families are absent from the map; no igloo candidate met the cold `c <= 0.30` / slope `< 0.25` policy), while the swamp hut is placed at 65,-82 (`c=-0.0923`, swamp, y82–88) and verified live. Broad tags remain only where the tag itself is the gate (random-spread villages/trials, strongholds, ocean/deep-dark).
* **Evidence:** `Phase82StructureTest.strictPoliciesPlaceOnlyValidSites`, `absentFamilyDisablesSetWithoutFallback`, `relaxedPyramidsValidateSurface`; `docs/evidence/phase82/structure_placement_before/after.csv`, `structure_queries.txt`, `structure_tags/`.

### Decision 052: default island size 3500 and document the intentional v3 regeneration
* **Question:** The approved 0.8.2 spec changes the default size from 4000 to 3500, and the v3 climate/shoreline changes alter existing v3 worlds' new chunks. How should the change be scoped and communicated?
* **Options:**
  1. Bump `generation_version` and gate all changes behind it.
  2. Keep v3 (no new version), change only the new-draft default, keep v1/v2 untouched, and add a migration note.
* **Choice:** Option 2. `DEFAULT_ISLAND_SIZE = 3500`; saved presets/share codes keep their stored size. All fixes are `version >= 3`; v1/v2 paths and goldens are unmodified. Existing v3 chunks keep their generated biomes; new chunks use the 0.8.2 rules; re-exporting the pack is required for strict structures.
* **Reason:** The reported defects are v3-only and the user explicitly asked to continue the 0.8.2 plan without a `generation_version` bump. A new version would strand the fixes behind a manual version switch and multiply the golden/regression matrix. The migration note makes the intentional v3 change explicit instead of presenting it as bit-identical.
* **Evidence:** `gradle.properties` 0.8.2, `IslandDraft.DEFAULT_ISLAND_SIZE`, `GoldenV1Test`, `GoldenV2Test`, `BiomeSourceV2V3RegressionTest`, `README.md` migration note, `CHANGELOG.md` 0.8.2.

### Decision 053: fixed image-backed Mineshield 4 Map preset
* **Question:** The user wants the supplied Mineshield 4 reference map available as a separate editor preset. Should it be a procedural approximation, a one-off PNG import, or a bundled image-backed fixed preset?
* **Options:**
  1. Approximate the coastline with a new procedural shape and tell the user to import the PNG manually.
  2. Bundle the image in the mod, derive a fixed land mask and height tiers from its colors, and expose it as `Preset.MINESHIELD_4_MAP` named “Mineshield 4 Map”.
  3. Add a full image-to-biome pipeline that also bakes the map’s biome regions into worldgen.
* **Choice:** Option 2. `Mineshield4Map` letterboxes the 1024×764 image to the draft resolution, classifies water by blue hue/saturation/brightness, and maps cool white → tier 3, warm brown/orange → tier 2, and all other land → tier 1 (tier 0 is unused to keep the fixed coast above the height layer’s negative offset). `rebuildShape()` short-circuits for this preset, so it is identical at 192/256/384 and ignores the shape seed.
* **Reason:** This reproduces the reference coastline exactly and deterministically without an external file, while reusing the existing mask → SDF → worldgen path. A procedural approximation would not match the requested map; a biome-baked image would be a much larger worldgen feature. The preset intentionally controls land mask and relief only — biomes remain procedural.
* **Limitation:** Color-derived relief is approximate (beaches/shallow shelves are tier 1, not tier 0), and the biome colors in the image are not reproduced. Letterboxing preserves the source aspect ratio.
* **Evidence:** `Mineshield4MapPresetTest`, Phase 7.4 (13 presets × 20 seeds), `docs/evidence/presets/mineshield_4_mask.png`, `docs/evidence/presets/mineshield_4_stats.csv`; measured 11,232 land pixels (30.47 %), 0 border land, largest component 10,228 (91.06 %), tiers 3/2/1 = 740 / 1,509 / 8,983.

### Decision 054: universal `ClimateLayout` derived from the mask instead of preset-specific climate tuning
* **Question:** 0.8.2's warm/cold/temperate distribution was tuned on the reference continent; other key presets (Round, Archipelago, Crescent) had no guaranteed warm or cold sector. Should each preset get its own tuning, or should one shared rule apply to all?
* **Options:**
  1. Add per-preset climate overrides.
  2. Build one `ClimateLayout` from the mask bounds and biome seed: warm = top 16 % of land by a west-biased score, cold = top 12 % of total land by a north-east-biased score (warm excluded), rest temperate, with smoothed strength and water inheritance.
  3. Keep the 0.8.2 temperature gradient and accept that some presets have no warm/cold sector.
* **Choice:** Option 2. Scores use normalized coordinates `-nx + 0.20·(-nz)` and `nx + 0.35·(-nz)` plus small 900-block noise; masks are smoothed twice with `[1,4,6,4,1]/16`, giving a continuous strength with a ≥0.65 core. The class is v3-only, cached, allocation-free when sampled, and returns `temperate()` for empty/all-water masks. Preview and worldgen resolve the same instance via `IslandTerrain.climateLayout()`.
* **Reason:** One rule keeps preview↔source parity (I3) and works for every current and future preset without a preset-identity branch. Measured on all five shipping presets the warm share is 13.78–16.95 % and cold 13.18–14.43 %, meeting the requested 12–18 % / 10–15 % bands (after the Decision 063 continuous-contour fix).
* **Evidence:** `Phase11PresetClimateTest`, `Phase11ClimateLayoutTest`, `docs/evidence/phase11/after/province_map.png`, `composition_controlled.csv`.

### Decision 055: v3 largest-component main-island SDF for offshore mushrooms
* **Question:** `Distances.main` used the all-mask SDF, so the Mineshield SW bay island was treated as part of the main island and could not host the requested offshore-only mushroom biome. Changing the all-mask SDF would alter coast/water behavior and v2 output.
* **Options:**
  1. Keep `main` = all-mask and accept inland mushrooms on bay-connected land.
  2. For v3 only, label 4-connected land components, keep the largest, and build a second padded EDT/SDF exposed as `main`; leave `signed` (coast/water) unchanged and keep v2's all-mask `main`.
  3. Gate mushrooms by distance from a hand-placed point.
* **Choice:** Option 2. `ModernTerrainV2` reuses the same EDT/smoothing code for both grids; `distances()` returns the merged `signed` plus the largest-component `main`. v2 and `LegacyTerrainV1` keep `main = signed`.
* **Reason:** Mushrooms are now strictly 0 % on the main island and appear only on separate islets (0.12–1.04 % of total land where islets exist), while the coastline SDF and every v1/v2 value remain unchanged.
* **Evidence:** `Phase11PresetClimateTest`, `Phase82ReferenceClimateTest.previewSourceZoneFamilyParityV3`, `GoldenV2Test`.

### Decision 056: expose height tier, surface height and the physical ridge as terrain APIs
* **Question:** The climate picker needed the smoothed height tier and real surface height for elevation gates and strict structure ceilings, and the terrain/climate mountain spine were computed independently.
* **Options:**
  1. Duplicate the smoothing and ridge formulas in each caller.
  2. Add default `heightTierAt`/`surfaceHeight`/`climateLayout` methods to `IslandTerrain`, implement them in `ModernTerrainV2`, and share `physicalRidge(x,z,seed)` between terrain and `IslandClimate`.
  3. Pass precomputed values through every call site.
* **Choice:** Option 2. Defaults keep non-modern implementations source-compatible (`1.0`, elevation from continentalness, null layout); v3 uses the same 7-tap grid as relief. Ridge constants are `scale 750`, `seed + 801`, 3 octaves.
* **Reason:** One source of truth prevents snow/mountain drift, and strict structures can bound top Y without resampling terrain. v2 never calls the new v3 paths.
* **Evidence:** `Phase11BiomeProvinceTest`, `Phase11StructureTest`, `Phase82ReferenceClimateTest` ridge correlation.

### Decision 057: province-aware family tree with family-centroid effective climate
* **Question:** A province bias on raw temperature was not enough: weighted selection still preferred families near the raw climate, and high-continentalness columns could receive snow outside the cold sector or wetlands on slopes.
* **Options:**
  1. Add province checks only to the legacy picker.
  2. Rewrite the v3 tree with fixed gate order and, after choosing the family, report that family's `BiomeShare` centroid climate as the effective climate for weighted selection; keep raw climate for ocean subtypes.
  3. Remove weighted selection in v3.
* **Choice:** Option 2. Gates: offshore mushrooms → province bias → cold lowlands → physical peaks (`c > 0.50 && tier ≥ 1.5`, never COLD) → foothills → cherry → wetlands → temperate heartland → warm sector. Snow 15 below tier 1.4 outside cold is structurally impossible; wetlands require `c < 0.15`, slope < 0.12, tier ≤ 1.0 and mountains < 0.35; desert/badlands have c ceilings and the cold branch runs first.
* **Reason:** This satisfies every acceptance counter (all zero across five presets) while keeping explicit 0–400 % weights authoritative. Phase 10 calibration was recalibrated for the centroid climate; held-out shares remain ≤3 pp.
* **Evidence:** `Phase11BiomeProvinceTest`, `Phase11PresetClimateTest`, `Phase10BiomeShareTest`, `Phase82ReferenceClimateTest`.

### Decision 058: rare biomes as low-frequency pockets and a land hint
* **Question:** Pale garden, ice spikes and cherry grove needed to be rare but reachable on every preset; adding new family IDs would break share codes, saves and the 0–17 family contract.
* **Options:**
  1. Add families 18+ for the rare variants.
  2. Keep families 0–17 and gate the variants with low-frequency pocket masks: pale 3.0 % of temperate (`/420`), ice 0.6 % of cold (`/260`), cherry support 36 % (`/520`); `ice_spikes` is a `landHint` on family 0, used only when the family has no non-neutral explicit weight.
  3. Keep the 0.8.2 rules (pale ~0.02 %, no ice spikes).
* **Choice:** Option 2. Pale requires `paleStrength > 0.50` and effective humidity > 0.88 via the pocket-local boost `hum + 0.65·paleStrength`; measured pale 0.93–1.57 %, cherry 1.17–2.78 %, ice confined to the cold pocket.
* **Reason:** Family IDs, ordinals, share codes and save decoding stay compatible; explicit user weights still override hints; each rare biome is reachable but bounded under 3.5 %.
* **Evidence:** `Phase11ClimateLayoutTest`, `Phase11BiomeProvinceTest`, `Phase11PresetClimateTest`.

### Decision 059: coarse shoal fields for contiguous sand arcs, with an honest partial result
* **Question:** The 0.8.3 shore field produced 1–3-block sand specks because the SAND presence and inland-width fields included a 55-block octave and 17-block jitter.
* **Options:**
  1. Keep the field and accept specks.
  2. Remove the finest octave/jitter, use only ≥130-block fields with smooth thresholds, and measure 8-connected component lengths.
  3. Post-process the sand mask with morphological opening.
* **Choice:** Option 2. Patch/pocket/gap/selector scales are now 380/95/110/150, inland width `170 − 60·variety` at `/190` and `/160`. Component measurement uses STEP=2 over the full coast band.
* **Result:** Median runs 44–70 blocks and P75 90–132 in all five presets, but **14 of 210 components are still ≤3 blocks**. Three further tunings (MIN_WIDTH 6/12, coarser fields, 12-block dilation) worsened either the sub-16 count or the 25–90 share and were reverted. The zero-speck target is therefore **PARTIAL**, and the achieved distribution is published instead of claiming perfection, per the plan's honesty clause.
* **Evidence:** `Phase11ShoreTest`, `docs/evidence/phase11/after/shore_runs.csv`, `shore_runs_summary.txt`.

### Decision 060: province + surface-ceiling structure policies with no fallback
* **Question:** 0.8.2 strict structures already gated family/c/slope, but they could still place a desert pyramid on warm high ground or a structure above a steep surface, and there was no province requirement.
* **Options:**
  1. Add only a province tag check.
  2. Extend `StructurePolicy` with `province` and `maxSurfaceY`, add `topY`/family/province/slope to `WorldPackBuilder.Sample`, require all bounds for v3, score by preferred c + low slope + spawn penalty, disable sets with no candidate, and re-check Y/relief/slope in `RelaxedPyramids`.
  3. Keep the 0.8.2 policies and document the exceptions.
* **Choice:** Option 2. Desert pyramid: family 12, WARM, c 0.00–0.32, slope < 0.22, topY ≤ 88. Jungle temple: 10, WARM, c ≤ 0.36, slope < 0.28, topY ≤ 96. Swamp hut: 8/9, c ≤ 0.16, slope < 0.10, topY ≤ 88. Igloo: 0/1, COLD, c ≤ 0.30, slope < 0.25, topY ≤ 92. Camp: 3, c ≤ 0.36, topY ≤ 96; outpost/trail/portal: c ≤ 0.40, topY ≤ 100. Ocean/shore types keep their depth/band rules.
* **Reason:** Export tags and the runtime guard are both authoritative; no valid site means the structure simply does not spawn, which is the requested behavior. NATURAL and Mineshield evidence packs have 14 sites each plus disabled rows.
* **Evidence:** `Phase11StructureTest`, `docs/evidence/phase11/after/structure_sites.csv`, `Phase82StructureTest`.

### Decision 061: clean Mineshield tiers by median + component reassignment
* **Question:** The extracted Mineshield height map had 16 detached tier-3 components and 48 tier-2 components, producing isolated snow/rock specks and a fragmented mesa.
* **Options:**
  1. Leave the raw color extraction.
  2. Apply a 3×3 median, keep the single curved central-north tier-3 range and the western tier-2 band, and reassign detached specks to tier 1.
  3. Hand-edit a replacement heightmap.
* **Choice:** Option 2, applied to the cached tier array for every resolution while the land mask stays byte-identical. Result at 192: tier-3 components 16 → 1 (612 cells, centroid 0.521, 0.398); tier-2 components 48 → 3 (1,108 / 253 / 70 cells, all west of x=0.26); tiers 1/2/3 = 9,189 / 1,431 / 612; land 11,232. 256 and 384 also yield one tier-3 component.
* **Reason:** The reference map's relief now matches the intended central range/western mesa, existing mask and share-code tests remain valid, and no manual asset is required.
* **Evidence:** `Mineshield4MapPresetTest`, `docs/evidence/phase11/after/mineshield_tiers.png`, `mineshield_tiers.csv`, `docs/evidence/phase11/before/mineshield_tiers.png`.

### Decision 062: rebuild the editor resolution row, control geometry, tooltip placement and preview shares
* **Question:** The screenshot showed a clipped resolution `CycleButton`, overlapping center controls, an inspector tooltip covering those controls, and `—` in every biome share row even while the preview had data.
* **Options:**
  1. Patch spacing values.
  2. Replace the half-width `CycleButton` with a full-width two-argument button, derive all rectangles from shared `EditorLayout` metrics with a dynamic compact row height, add `Rect`/`placeTooltip` helpers, and count preview land families on every refresh so rows always show actual shares.
  3. Hide the affected controls in compact mode.
* **Choice:** Option 2. Resolution cycles 192 → 256 → 384 and renders exactly `Разрешение: 192×192` / `Resolution: 192×192`; layout is tested on 13 window sizes at GUI scales 1–4 with no overlaps or out-of-panel controls; the tooltip flips/clamps inside the preview canvas; biome rows use preview counts and neutral coloring when target/achieved calibration data is absent.
* **Reason:** This fixes all four defects at the layout level instead of tuning pixels, keeps the label correct in both languages, and makes the share rows truthful with all-100 weights (the `hasNonNeutralWeights` gate was removed).
* **Evidence:** `Phase11EditorLayoutTest`, `Phase9EditorTest`, `en_us.json`/`ru_ru.json`, `Phase11EvidenceTest` (province/close-up PNGs; real editor screenshots remain **NOT DONE**).

### Decision 063: continuous province contours with calibrated thresholds and ocean transition bands
* **Question:** The 0.8.4 screenshots showed 18–20-block stair-stepped biome/province boundaries. Membership came from the nearest mask cell, and ocean subtypes switched categorically at the province edge.
* **Options:**
  1. Keep the nearest-cell lookup and rely on mask smoothing alone.
  2. Bilinearly sample the smoothed warm/cold strength rasters, add a low-frequency boundary-noise term, compare against quantile-calibrated thresholds computed from the same smoothed values, and blend ocean subtypes through lukewarm/cold transition bands.
  3. Replace the quantile sectors with a full-resolution continuous climate noise field.
* **Choice:** Option 2. `ClimateLayout.sample()` interpolates the strength rasters, adds `0.22·boundaryNoise` (160-block fBm, salt +1621), and compares against `warmThreshold` (smoothed top 16 % of land) and `coldThreshold` (smoothed top 12 % of total land excluding warm); a province column with strength < 0.16 falls back to TEMPERATE. Ocean: WARM strength < 0.55 → `lukewarm_ocean`/`deep_lukewarm_ocean`, COLD strength < 0.55 → `cold_ocean`/`deep_cold_ocean`, frozen only at strength ≥ 0.55 and temperature < 0.34.
* **Reason:** The contour is now continuous at world scale and reads as an organic curve in both the preview and in-world, while land shares stay within 0.2 pp of the pre-fix values (Mineshield 15.31/14.62/68.18 → 15.15/14.43/68.53). v1/v2 are untouched; the v3 regression hash stays `-6312501814918578635` because its ±480-block sample area does not cross a changed province edge.
* **Evidence:** `Phase11ClimateLayoutTest` 11L.6 (`provinceBoundaryIsSmooth`) and 11L.7 (`oceanTransitionBands`), `Phase11PresetClimateTest`, `docs/evidence/phase11/after/composition_controlled.csv`, `ocean_edge_natural.png`.

### Decision 064: flat custom widget system for the editor, presentation only
* **Question:** The 0.9/0.8 editor still used vanilla `Button`, `CycleButton`, `AbstractSliderButton` and bordered `EditBox`; the 1.0 target is a flat dark UI from the supplied mockup, and worldgen/share/data formats must not change.
* **Options:**
  1. Restyle vanilla widgets with client mixins.
  2. Add `com.hack337.omniisle.client.ui` with theme tokens, custom flat widgets, pure layout and cached labels; keep only `EditBox` from vanilla (Decision 066).
  3. Keep the vanilla look and only change colours.
* **Choice:** Option 2. `OmniTheme`/`OmniUi` provide tokens and primitives; `OmniWidget extends AbstractWidget`, `OmniControlsPanel extends AbstractContainerWidget`, so focus traversal, narration and input routing stay vanilla-tested. The editor renders tool column, canvas tabs, six icon tabs, controls and the FULL-only list from `EditorLayout`.
* **Reason:** No mixins and no new libraries; worldgen, `IslandDraft`, share codes and tool semantics are untouched; one layout source keeps every rectangle testable without a client.
* **Evidence:** `src/main/java/com/hack337/omniisle/client/ui/` (22 classes), `Phase11UiThemeTest`, `Phase11UiWidgetTest`, `Phase11UiHygieneTest`.

### Decision 065: five density tiers, icon-only MINI and a MINIMAL notice
* **Question:** 640×320 at GUI scale 3–4 leaves 213×107 and 160×80 logical pixels — no room for labelled tabs, a canvas and controls, yet the plan requires GUI scales 1–4 on four window sizes.
* **Options:**
  1. Let content clip and rely on scrolling.
  2. Shrink fonts or scale the whole screen.
  3. Use explicit density tiers and show an honest notice when even the smallest tier cannot fit.
* **Choice:** Option 3. FULL ≥840×470 (list visible), MEDIUM ≥700×420 (icon tools, list in a modal), COMPACT ≥400×240 (scrolling controls), MINI ≥320×150 (16-px icon tools, icon tabs, abbreviated status), MINIMAL below 320×150 (actual logical size + Cancel/Done only). MINI keeps full text in tooltips and narration.
* **Reason:** Every tier is provably non-overlapping and reachable; the notice is honest instead of a clipped editor. Tests assert Done/Cancel reachability and absence of overlap in every tier.
* **Evidence:** `EditorLayout.densityFor`, `Phase11UiLayoutTest.densityBudgets`/`slotsTilePanels`, `Phase11EditorLayoutTest.densityThresholds`/`layoutMatrix`.

### Decision 066: `OmniTextField` wraps a vanilla `EditBox`
* **Question:** A fully custom text field would lose IME composition, selection, clipboard and accessibility behavior that vanilla already provides and tests, while a bordered vanilla box clashes with the flat design.
* **Options:**
  1. Build text input from scratch.
  2. Keep `EditBox` with `setBordered(false)` and custom frame/text colours behind an `OmniTextField` wrapper.
  3. Keep the border and accept the style break.
* **Choice:** Option 2. The wrapper draws a flat frame and registers the inner `EditBox` as the focusable element, so typing, IME and narration stay vanilla.
* **Reason:** A documented, minimal exception to "custom widgets only"; no behavior change for seed and weight entry.
* **Evidence:** `OmniTextField.java`, `Phase11UiWidgetTest`, `seedUxTest`.

### Decision 067: dev-only screenshot harness inside the mod
* **Question:** GUI screenshots at scales 1–4 had never been captured; a manual client session is not reproducible and the agent cannot click through Create World reliably.
* **Options:**
  1. Leave screenshots NOT DONE (the 0.7–0.8 status).
  2. Ship a separate harness mod.
  3. Add a client entrypoint that is a no-op unless `-Domniisle.uiScreenshot=<dir>` is set, driving a tick-based state machine that opens the editor with a default draft, switches language via `LanguageManager.setSelected` + `Minecraft.reloadResourcePacks()`, resizes with `Window.setWindowed`/`setGuiScale` + `resizeGui`, waits frames, captures with `Screenshot.takeScreenshot(GameRenderer.mainRenderTarget(), ...)`, and writes `manifest.csv`/`render_cost.csv`.
* **Choice:** Option 3. `build.gradle` forwards `omniisle.uiScreenshot*` system properties to `runClient` only. Render cost is measured around background+foreground extraction over 300 frames after warm-up per tab at 1920×1080 scale 2.
* **Reason:** Reproducible real-client evidence with no impact on normal play or the shipped jar; failures are recorded in `harness_error.txt` instead of being claimed as success.
* **Evidence:** `OmniIsleClient`, `dev/UiScreenshotHarness`, `build.gradle`, `docs/screens/1.0.0/`.

### Decision 068: runtime font metrics with worst-case test metrics
* **Question:** Unit tests run headless without a `Font`, but ru_ru labels can be wider than en_us and must not clip at MINI/COMPACT.
* **Options:**
  1. Hard-code an 8-px per-character estimate everywhere.
  2. Skip text-fitting tests.
  3. Measure real widths at runtime through a cached `OmniMetrics`, and in tests use `WorstCaseMetrics` (Latin 8 px, Cyrillic 9 px per character) to prove every label either fits or is ellipsised with the full text in a tooltip.
* **Choice:** Option 3. `OmniMetrics` caches widths and fitted strings per language epoch and is invalidated on language change; en/ru key sets are asserted identical, and every referenced key must exist.
* **Reason:** Runtime labels fit the real vanilla font, while tests provably catch clipping for the worst-case font without pretending to measure it; screenshots provide the real-font check where available.
* **Evidence:** `OmniMetrics`, `OmniText`, `Phase11UiTextTest.corpusFits`/`keyParity`/`referencedKeysExist`.

### Decision 069: screenshot coverage and render cost are reported as measured, not normalized
* **Question:** The 1.0.0 plan asks for a full tab × window × scale × language matrix and a ≤2 ms median render cost, but the real environment cannot create every requested logical size and the measured cost straddles the target.
* **Options:**
  1. Claim the full matrix and the target as met.
  2. Skip screenshots/cost because they cannot be perfect.
  3. Capture every combination the environment allows, record the actual logical size and density per row, keep MEDIUM/MINIMAL to unit tests, and report the cost distribution including the miss.
* **Choice:** Option 3. 200 images (6 tabs × 16 requested window/scale combinations × 2 languages + 4 preview modes × 2) with `manifest.csv`; MEDIUM is not produced by the matrix; MINIMAL is unreachable because macOS enforces a 640×480 framebuffer and Minecraft clamps the logical area to ≥320×240; render medians/p95 are recorded per tab and language, and 11/12 medians ≤2 ms is reported as PARTIAL because en_us Climate measures 2.007 ms.
* **Reason:** Honest evidence is more useful than a green checkmark; the plan forbids percentage or performance claims without evidence, and the tier math is still proven by `Phase11UiLayoutTest`/`Phase11EditorLayoutTest`.
* **Evidence:** `docs/screens/1.0.0/after/` (`manifest.csv`, `render_cost.csv`, 200 PNGs), `FINDINGS.md` §19.6, `UI_CHECKLIST.md`.

### Decision 070: revise v3 in place, drop the spawn plateau and search for a dry column
* **Question:** The v3 spawn marker sits on a perfectly circular, terraced dome (Decision 018's `omniisle:spawn_plateau`), which users see as an artificial hill. How should the dry-spawn guarantee be kept without a visible structure?
* **Options:**
  1. Keep the plateau and soften its mask/bands.
  2. Stop emitting the plateau for v3 and find a real dry column at server start; keep the codec registered.
  3. Add `generation_version = 4` and leave v3 untouched.
* **Choice:** Option 2, revising v3 in place. The codec and `final_density_base.json` stay so already-generated v3 packs keep loading.
* **Reason:** v3 is the current evolving generation; v1/v2 are the frozen ones (I1), and their resources are untouched. A dome is the wrong tool for "spawn somewhere dry": the marker is only a hint, while the requirement is a validated column. A v4 would duplicate the whole v3 surface pipeline for no player benefit. Existing v3 worlds keep their already-generated datapack and are not repaired; a new world is required — documented rather than claimed as a migration.
* **Evidence:** `WorldPackBuilder.export` v3 branch, `OmniIsleMod.findModernSpawn`/`drySpawnAt`, `Phase13HighlandWorldgenTest` (v3 pack contains no `spawn_plateau`/`top_y`), `FINDINGS.md` §20.1.

### Decision 071: lift highlands through a generated v3 offset wrapper, fade 300→316
* **Question:** The editor preview advertises up to ~260 blocks, but v3 terrain stays near Y 66–70 because the real surface follows vanilla's offset spline (median Y ≈ 81 at both c = 0.45 and c = 0.95), and the 240→256 fade clips the rare high offsets. How can painted highlands reach the advertised range?
* **Options:**
  1. Lower the preview curve to match the old terrain.
  2. Bake absolute height into the island mask/continentalness.
  3. Generate `overworld/offset.json` as `add(mul(vanilla_offset, scale(continents)), lift(continents))` and raise the vertical fade to 300→316.
* **Choice:** Option 3.
* **Reason:** The offset function maps roughly linearly to surface Y (`Y ≈ (1.5 + offset)·128 − 64`), is evaluated per 4×8 cell, and leaves vanilla 3D noise, caves and aquifers intact. Scaling the base offset down at high continentalness prevents rare vanilla offsets from stacking into the build limit. Option 1 would hide the bug and change the UI/commands; option 2 would destroy the shape/mask contract and v1/v2 neutrality. The preview curve is unchanged because after the lift it is a reasonable estimate, not the cause.
* **Evidence:** generated `offset.json`/`final_density.json`/`preliminary_surface_level.json`, `Phase13HighlandWorldgenTest` measured min 139 / median 228 / p90 235 / max 289 and 1088 columns ≥ Y 220 (ROUND 4000 seed 4242, tier-3 paint), `FINDINGS.md` §20.2–20.3.

### Decision 072: test pack lists are ordered lowest priority first
* **Question:** `Phase13HighlandWorldgenTest` sampled ocean-only Y 63 despite a correct generated pack. Which pack order does `MultiPackResourceManager` use?
* **Options:**
  1. Assume "first in list = highest priority" and reorder the generated pack first (as both tests did).
  2. Verify the bytecode semantics and order packs lowest priority first.
* **Choice:** Option 2. `FallbackResourceManager.getResource` iterates the fallback list in reverse and `listResources` overwrites in forward order, so the **last** pack passed to `MultiPackResourceManager` wins. Tests now pass `vanilla, mod, generated`, matching the game where the world datapack is selected last.
* **Reason:** Both tests had the order backwards; the mod's static empty-config `continents.json` was shadowing the generated pack, so `Phase10PresetDecodeTest` had never actually exercised generated density overrides. This is a test-only fix; production pack selection is unchanged.
* **Evidence:** `javap` output for `MultiPackResourceManager`/`FallbackResourceManager`, active SDF before (−400) and after (815.43, equal to the draft) the fix, `FINDINGS.md` §20.3.

### Decision 073: gate beaches on a ring open-water scan, low tier and coastal continentality
* **Question:** `ShoreField` selected SAND/STONY from patch noise, an 8-point "bayness" ring mean at radius 96 and an inland-width cutoff, but the mean cannot tell an open coast from a narrow fjord whose banks are close on every side, and neither terrain height nor continentality was checked. Sand ribbons therefore appeared along fjord banks deep inside the island.
* **Options:**
  1. Retune the existing bayness/width thresholds.
  2. Restrict beaches to `d ≥ 0`.
  3. Return the minimum signed distance from the same 8-point scan and require an actually open water body, low terrain and coastal continentality in one place.
* **Choice:** Option 3. `ShoreField.ring(...)` returns `Ring(bay, minDistance)`; `outcome(...)` takes the ring and rejects a column unless `ring.minDistance ≤ −OPEN_WATER_MIN (55)`, `heightTier ≤ 1.10`, `continentalness ≤ 0.20`, `bay ≥ 0.20` and `d ∈ [intertidalLimit, MAX_INLAND 28]`, before the existing patch/beach/width/depth checks. The inland width narrows to `(4 + 18·noise) · (0.42 + 0.58·patch) · factor · (1 − 0.35·notch)`, floor `MIN_WIDTH = 8`, cap 22. `ModernTerrainV2` flattens the shore shelf only for `d < 52 && tier ≤ 1.05`, so beach relief no longer carves into hills.
* **Reason:** A narrow fjord keeps every ring sample close to its banks, so `minDistance` stays shallow even when the mean looks "open"; the gate rejects the bank without additional sampling (same 8 SDF calls as before). Keeping the gates inside `outcome` makes `OmniBiomeSource`, `BiomePreview`, the editor palette/inspector, the PNG shore export and `/omniisle debug shore` share one rule. Option 1 cannot separate fjord from coast; option 2 would delete the seaward part of every real beach.
* **Evidence:** `ShoreField.java`, `ModernTerrainV2` v3 shore block, `Phase14ShoreInteriorTest`: 0 violations (`beyondBand`/`shallow`/`highTier`/`inlandC`) over 2,597 / 5,198 / 7,272 coastal columns (NATURAL/FJORDS/ARCHIPELAGO), a synthetic 20-block channel returns 198/198 NONE with minBay 1.0, open coast 76/201 sand, NATURAL coast trace 302 beach points; `FINDINGS.md` §21.1.

### Decision 074: five vanilla village variants at reduced density with family-scoped tags
* **Question:** The v3 export replaced vanilla's five-entry village set with a single `minecraft:village_plains` entry and listed every enabled land biome in `has_structure/village_plains`, producing plains villages in deserts, savannas and taiga at vanilla density (spacing 34 / separation 8).
* **Options:**
  1. Keep one plains variant and filter its tag by family.
  2. Add custom village structures for every biome family.
  3. Emit all five vanilla village variants as weighted entries, each with a tag built only from its matching family, at a wider spacing.
* **Choice:** Option 3. `WorldPackBuilder.VILLAGE_VARIANTS` lists plains {2,3,4,5,6} minus grove, desert {12,13}, savanna {11}, snowy {0} minus ice spikes, taiga {1}; one `structure_set/villages.json` with five weight-1 entries, salt 10387312, spacing 54 / separation 12 (`scaledSpread` keeps island-size and density-setting scaling). Each variant gets `has_structure/<tag>.json` with `replace:false`; variants with no enabled biome are omitted, and if none survive the set is exported disabled via `omniisle:fixed`.
* **Reason:** Vanilla pieces and structure types already exist and need no new registries; family-scoped tags both choose the right architecture and shrink the candidate area. `(34/54)² = 0.396` ⇒ −60.4 % villages, matching the user's request. Option 1 still forces one architecture; option 2 duplicates five vanilla structure types for no benefit. `replace:false` merges with vanilla's own additions, which remain valid (documented, not a defect).
* **Evidence:** `WorldPackBuilder` (`VILLAGE_SPACING_V3`, `VILLAGE_VARIANTS`, `villageSetJson`, `villageBiomeIds`), `Phase14VillagePackTest`: 5 entries at 54/12/salt, weight 1, exact ids, family tags exclude grove/ice_spikes, empty variants omitted, desert-only export leaves one entry, all-unsupported export is disabled, v2 keeps the single 34/8 plains placement; `FINDINGS.md` §21.2.

### Decision 075: continuous warm/cold climate bands; province kept for oceans, shores, hints and structures
* **Question:** `selectLandFamilyV3` switched on the categorical `ClimateLayout.Province` with a 0.16 strength dead zone: COLD lowlands were forced to family 0/1 and WARM to 10–13 at the same boundary, so taiga could border desert, and mountain snow was suppressed outside TEMPERATE.
* **Options:**
  1. Keep the province switch and insert a transition strip.
  2. Select families from the raw continuous warm/cold strengths with core and band thresholds, and keep the province only for non-family decisions.
* **Choice:** Option 2. `ClimateLayout.Sample` now exposes `warmStrength`/`coldStrength` (7-component record; the 5-arg constructor derives them from province+strength so existing callers compile). `diff = warmStrength − coldStrength` with `V3_CLIMATE_CORE_DIFF = 0.35` and `V3_CLIMATE_BAND_DIFF = 0.12` selects cold core → cold band → temperate → warm band → warm core. The mountain exception (`tier ≥ 1.5 && c > 0.50` → snowy peaks / stony peaks / meadow by effective temperature) is province-independent. `effTemp` uses the raw strengths (`+0.14·warm − 0.24·cold`) when province bias is enabled.
* **Reason:** The strengths are the same smooth fields that already produce continuous province contours, so families change over a ramp instead of a wall; the bands guarantee an intermediate family (meadow/birch/plains/savanna) between warm and cold cores. Province still drives ocean subtypes, shore outcomes, the ice-spike hint and strict structure sectors, where categorical sectors are intended. Mountain snow in a warm province next to a desert is intentional (explicit user requirement) and stays restricted to high terrain.
* **Evidence:** `ClimateLayout.Sample`, `IslandClimate.selectLandFamilyV3`, `Phase14ClimateTransitionTest`: 0 adjacent warm-core↔cold-core pixel pairs and 0 unbanded transects on MINESHIELD_4_MAP (39 switches) and NATURAL (100 switches), intermediate families present (bands 2/4/5), synthetic high-tier family 15 chosen in a WARM province; `Phase11BiomeProvinceTest`; `FINDINGS.md` §21.3.

### Decision 076: revise v3 again for 1.0.1 and recalibrate only measured thresholds
* **Question:** 1.0.1 changes shore, village and climate output. Does it need `generation_version = 4`, and what happens to 1.0.0-calibrated tests?
* **Options:**
  1. Add v4 and freeze v3 as-is.
  2. Revise v3 in place and adjust v3-dependent tests only where the measured run moved, keeping v1/v2 golden hashes and the SPEC composition targets.
* **Choice:** Option 2. No `generation_version` change. v1/v2 stay bit-identical (`GoldenV1Test`/`GoldenV2Test`; v2 hash `-4990092939012319655`). Existing v3 worlds keep their generated datapack and need a new world to see the fixes. Shore/village/climate thresholds in `Phase9ShoreTest`, `Phase11ShoreTest` and the province/climate tests were updated from measured output; composition targets (warm ≥ 8 %, cold ≥ 8 %, green 60–75 %, rare families < 3.5 %) and the zero-counters were kept unchanged.
* **Reason:** v3 has been the evolving generation since 0.8.2 (Decision 070); a v4 would duplicate the entire terrain pipeline for no player benefit. Recalibrating from measured distributions keeps evidence honest; silently loosening the SPEC targets would not. The v3 biome-source hash stayed `-6312501814918578635` on the regression grid.
* **Evidence:** `BiomeSourceV2V3RegressionTest` (both hashes unchanged), full suite **193 tests, 0 failures, 2 assumption-gated skips**, jar `OmniIsle-26.3-Fabric-1.0.1+mc26.3.jar`; `docs/evidence/phase14/test_run.log`, `Phase14*` tests, `FINDINGS.md` §21.4.

### Decision 077: rebrand as OneIsle with a new runtime identity
* **Question:** OmniIsle 0.5.0 is GPL-3.0-only, but the original GitHub repository (`y9hack337/OmniIsle`) and Modrinth project (`omniisle`) belong to Hack337. The authenticated account `reystin` has read-only access to the repository and the stored Modrinth token is invalid (401). How should the finished 0.5.0→1.0.1 work be published?
* **Options:**
  1. Publish into the original GitHub repository and Modrinth project.
  2. Republish under the OmniIsle name as a fork with attribution.
  3. Create a new project identity (name, namespace, repository and page) and credit the original.
* **Choice:** Option 3. The project becomes **OneIsle**, first release **1.0.0**. The rename covers mod id and resource namespace (`omniisle` → `oneisle`), Java package (`com.hack337.omniisle` → `com.reystin.oneisle`), `OmniIsle*`/`Omni*` class names, world preset (`oneisle:oneisle`), noise settings (`oneisle:one_terrain`), biome source and density functions, saved data, command (`/oneisle`), config (`config/oneisle.json`), datapack name, import/export/debug paths, share-code prefix (`ONE2-`), debug markers, system properties, harness environment, build naming and the new GitHub/Modrinth pages. Version numbering restarts at 1.0.0. Original pages and tags stay untouched.
* **Reason:** GPL-3.0-only permits redistribution of modified code with source and notices, but the original pages are not writable and publishing there would misrepresent ownership. A distinct name avoids impersonation and confusion with the original author's releases; attribution to Hack337 is prominent (CREDITS.md, README, `fabric.mod.json` contributors, Modrinth body), and no endorsement is claimed. The Git history keeps the original 0.5.0 commits. The user explicitly chose "rename everything".
* **Consequences:** Breaking change: OmniIsle worlds (0.5.0–1.0.1), `OMNI2-` share codes and `config/omniisle.json` do not carry over; there is no migration. Terrain math is untouched — v1/v2 goldens still pass and v3 produces the 1.0.1 behavior. Modrinth's reupload rule remains a residual risk; if moderation rejects the project, the fallback is GitHub-only distribution.
* **Evidence:** `docs/evidence/phase15-rename/` (namespace gates, test run, jar metadata, SHA-256), new GitHub repository `reystin/oneisle`, new Modrinth project `oneisle` (publication tracked in Phase 16 evidence); the original `y9hack337/OmniIsle` repository and Modrinth project are not modified.
