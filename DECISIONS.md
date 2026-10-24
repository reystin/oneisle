# OmniIsle 0.6.0 — Architectural Decisions Log

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
