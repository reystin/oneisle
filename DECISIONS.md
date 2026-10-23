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
