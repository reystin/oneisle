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
