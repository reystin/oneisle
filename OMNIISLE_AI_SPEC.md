# OmniIsle: Technical Specification & AI Agent Reference Manual
> **Target Audience:** Autonomous Coding Agents, LLM Pair Programmers, Modding Architects.  
> **Mod version:** 0.7.0+mc26.3-fabric  
> **Minecraft Version:** 26.3  
> **Mod Loader:** Fabric Loader (`>= 0.19.3`)  
> **Fabric API:** `0.165.0+26.3`  
> **Java Version:** Java 25 (LTS)  
> **Loom Version:** 1.17.19  
> **License:** GPL-3.0-only  

---

## 1. Executive Summary & Core Objective
**OmniIsle** turns the infinite Overworld into one customizable island surrounded by an endless ocean.

1. An **in-game editor** embedded in the vanilla "Create New World" screen: drawing tools, symmetry, undoing, 12 presets, 3 canvas resolutions, PNG import/export and share codes.
2. A bridge from the painted mask to Minecraft's noise router: the mask is converted to a continuous **signed distance field (SDF)** and sampled by the custom density function `omniisle:island_continents`.
3. **Three frozen generation versions** behind one config field:
   * **v1** — legacy 0.5.0 sampler (`LegacyTerrainV1`), bit-for-bit compatible.
   * **v2** — modern SDF terrain (`ModernTerrainV2`): EDT + Catmull-Rom, domain warp, ridged mountains, height tiers, bathymetry, satellite islets, carved rivers. This is the exact 0.6.0 output, bit-for-bit frozen by `GoldenV2Test`.
   * **v3** — v2 plus a deterministic **shore field** (patchy sand/stony/rocky coasts), a smooth ocean depth ramp that reaches the monument band before `0.4·Wo`, islets moved beyond the monument band, river mouths carved across the waterline, and a spawn plateau under the editor marker. **Default for new worlds.**
4. A climate-driven custom **BiomeSource** (`omniisle:island_biomes`) using the shared side-neutral `IslandClimate`; every vanilla Overworld biome plus dynamically discovered datapack/modded biomes.
5. Automatic generation and injection of a self-contained worldgen datapack (`omniisle-generated.zip`) at world creation or dedicated-server first boot.

---

## 2. Technical Stack & Build Environment
* **Platform:** Fabric mod for the Minecraft Overworld.
* **Build System:** Gradle 9.5+ with `fabric-loom` 1.17.19.
* **Compiler Requirements:** JDK 25 (`sourceCompatibility = JavaVersion.VERSION_25`, `targetCompatibility = JavaVersion.VERSION_25`).
* **Key Dependencies:**
  * `net.fabricmc:fabric-loader:0.19.3`
  * `net.fabricmc.fabric-api:0.165.0+26.3`
  * `net.minecraft:minecraft:26.3`
  * Mojang `DataFixerUpper` (`Codec`, `MapCodec`, `RecordCodecBuilder`).
  * `com.mojang.blaze3d.platform.NativeImage` for in-memory GUI textures and PNG export.

---

## 3. End-to-End Execution Pipeline

```
[CreateWorldScreen (vanilla)]
            │
            ▼ (mixin: WorldTypeEditorMixin -> DynamicBiomeRegistry.updateFromRegistry)
[OmniIsleEditorScreen] <──────> [IslandDraft (192/256/384 grid, land + 2-bit height)]
            │                               │
            │ paints / symmetry / tools     ▼
            │                         [BiomePreview] <──> [IslandTerrain (v1/v2/v3)]
            ▼                               │
[Done]                                      │
            │                               │
            ▼ (WorldPackBuilder.export())
[omniisle-generated.zip]
   ├── pack.mcmeta (min_format = max_format = 121)
   ├── data/omniisle/worldgen/density_function/overworld/continents.json
   │      (minecraft:cache -> omniisle:island_continents)
   ├── data/omniisle/worldgen/density_function/overworld/final_density.json      (v3 only)
   │      (omniisle:spawn_plateau -> omniisle:overworld/final_density_base)
   ├── data/minecraft/worldgen/structure/desert_pyramid.json, jungle_pyramid.json (v3 only)
   ├── data/omniisle/worldgen/world_preset/omniisle.json (omnibiome source + enabled lists)
   └── data/minecraft/worldgen/structure_set/*.json (single/ring/fixed placements)
            │
            ▼ (mixin: CreateWorldBridge injects the pack into the PackRepository)
[World generation / dedicated-server startup]
   ├── IslandDensityFunction (omniisle:island_continents)
   │     └── IslandTerrain (LegacyTerrainV1 | ModernTerrainV2)
   │           ├── Landside: EDT/Catmull-Rom SDF, domain warp, micro-roughness,
   │           │   mountain spines, height tiers, v3 shore carving, river carve
   │           └── Ocean side: soft floor -0.80, seabed fBm + trenches,
   │               v3 deep band, satellite islets
   ├── OmniBiomeSource (omniisle:island_biomes)
   │     └── IslandClimate (family blend, latitudinal gradient, cave/river zones)
   │         └── v3: ShoreField outcome (NONE/SAND/STONY)
   ├── SpawnPlateauDensity (v3 final_density wrapper, only near the spawn marker)
   └── SpawnSavedData + world border setup (first-load relocation, centered border)
```

---

## 4. Codebase Architecture & Class Roles

### 4.1. Client / GUI (`com.hack337.omniisle.client`)
* **`IslandDraft.java`:** Draft data model. Resolutions 192/256/384 (bilinear resampling), `boolean[] land` + `byte[] height` (0–3), undo/redo snapshots, 12 seeded presets, 6 symmetry modes, climate/gradient/shore/river/islet/structure settings, **generation version default V3**, world border, largest-SDF spawn selection.
* **`OmniIsleEditorScreen.java`:** Six-tab editor (`SHAPE`, `TERRAIN`, `CLIMATE`, `BIOMES`, `STRUCTURES`, `TOOLS`), zoom/pan (1×–8×), 16-px grid, brush/line/rect/ellipse/fill/smooth, seed fields with dice, inspector (biome, surface Y, coast distance, islet, height tier, shore outcome).
* **`AdvancedSettingsScreen.java`:** Generation version (v1/v2/v3), climate scale (384–1536), gradient mode/strength, islet/river/structure density, shore density/variety/rocky, world border margin, seed and share-code actions.
* **`ShareCode.java`:** `OMNI2-<base64url(deflate(json))>-<crc16>` with CRC16 check and 256 KiB decompressed cap. Missing `river_density`/`shore`/`height_layer` keys keep legacy defaults.
* **`ImageImportExport.java`:** grayscale mask import (threshold/invert/letterbox), heightmap import (25/50/75 % quantiles → tiers 0–3), exports mask, height, biome color map, NW hillshade and shore map.
* **`BiomePreview.java`:** Client mirror of `OmniBiomeSource` built on the same `IslandClimate`, `IslandRivers`, `ShoreField` and `IslandTerrain` classes. Preview textures upload `NativeImage` into a registered `DynamicTexture` and release it exactly once.
* **`DynamicBiomeRegistry.java`:** Scans `WorldCreationContext`; categorizes biomes into LAND/SHORE/OCEAN/RIVER/CAVE with structure tags; falls back to 56 vanilla biomes.
* **`WorldPackBuilder.java`:** Writes `omniisle-generated.zip` (schema in §6), including scaled structure sets and, for v3, the spawn-plateau wrapper and relaxed pyramid structures.
* **`PreviewViewport.java`, `PreviewTextureSlot.java`, `EditorLayout.java`, `PngToolsScreen.java`, `ShareCodeScreen.java`, `BiomeSelectionScreen.java`, `EditorDraftStore.java`, `ClientScreenNavigator.java`:** viewport math, texture lifecycle guard, responsive layout, PNG/share-code/biome dialogs, draft persistence, screen transitions.

### 4.2. Side-neutral terrain (`com.hack337.omniisle.continent`)
* **`IslandTerrain.java`:** Interface: `continentalness(x,z)`, `signedDistance(x,z)`, `erosionBias(x,z)`, `rivers()`. Factory selects v1 vs v2/v3.
* **`ModernTerrainV2.java`:** v2 and v3 sampler. Exact Felzenszwalb–Huttenlocher EDT on the native grid, sub-pixel convention, Catmull-Rom bicubic with 4×4 clamp, mask-only domain warp, shoreline micro-roughness at `-0.08`, height-tier offsets, ridged mountains, soft tanh saturation, ocean bathymetry/islets, v3 ocean ramp and shore carving. Precomputes flat `float[]` SDF/height grids; sampling itself allocates nothing.
* **`LegacyTerrainV1.java`:** Frozen 0.5.0 formula (`LandmassSampler` + erosion), no rivers, no v2/v3 terms. Do not edit for behavior.
* **`IslandRivers.java`:** Deterministic river network from `(mask, height, seed, river_density)`: coarse height, Priority-Flood depression fill, D8 routing, flow accumulation, C¹ smoothstep carve into continentalness and erosion. Density 0 disables it.
* **`IslandMask.java`:** Raster + EDT + bilinear sampling + codec (`version`, `resolution`, bounds, `coast_width`, `ocean_transition_width`, base64 `data`); tolerant of short/corrupt data.
* **`IslandClimate.java`:** Shared classification: `OCEAN_START = -0.30`, `INLAND_START = 0.02`, `DEEP_OCEAN_THRESHOLD = -0.58`, `STEEP_SHORE_SLOPE = 0.35`; theme spans, gradient modes (OFF/NORTH_SOUTH/EAST_WEST/RADIAL), jittered family blending, v1/v2 `sample` and v3 `sampleV3` (shore outcome + signed distance).
* **`ShoreField.java` / `ShoreSettings.java`:** v3 deterministic shore outcome: patch noise, inland width, bayness, beach/stony weights. Settings record `density` (0–1), `variety` (0–1), `style` (`"natural"`), `rocky`; `density = 0` disables.
* **`IslandNoise.java`:** Pure hashed value/simplex/fBm helpers, seed-salted; no shared `Random`.
* **`IslandShapeConfig.java`:** Codec for the density-function config (fields/defaults in §6.1), builds the terrain.
* **`ShapeLayerSettings.java`:** Converts optional `entries` (legacy shape layers) into sampler settings.

### 4.3. Worldgen (`com.hack337.omniisle.worldgen`)
* **`IslandDensityFunction.java`:** Registered as `omniisle:island_continents`. `domainAxes() = AXIS_X | AXIS_Z`; `Sampler.sampleVolume` evaluates **once per XZ column** and calls `buffer.setRange(baseIdx, sizeY, value)`; `sampleValue` ignores Y. Holds a volatile `activeInstance` so the biome source and commands can reach the same terrain.
* **`SpawnPlateauDensity.java`:** Registered as `omniisle:spawn_plateau`. Wraps v3 `final_density_base`; inside `radius` (default 80) a quadratic mask lifts a dome from `top_y` (78) at the marker to `top_y − 16` at the rim, leaving deeper caves untouched.
* **`OmniBiomeSource.java`:** Registered as `omniisle:island_biomes`. v1 uses `climateAt`; v2 samples neighboring continentalness for slope (5 climate samples per lookup); v3 additionally samples signed distance and `ShoreField`. Resolves surface/shore/ocean/river/cave biomes, honoring enabled lists with non-empty fallbacks.
* **`SpawnSavedData.java`:** Persistent flag + first-load spawn initialization/relocation.
* **`SingleStructurePlacement.java` / `FixedMultiStructurePlacement.java`:** 1-per-world and exact ordered start-chunk placements, both extending `RandomSpreadStructurePlacement` so 26.3 `/locate` sees them.
* **`RelaxedPyramids.java`:** v3 desert/jungle pyramid structure types that reuse vanilla pieces/tags but skip the hard `lowestY < seaLevel` rejection on low islands.
* **`RiverLife.java`:** Adds fish spawns to river biomes.

### 4.4. Commands & lifecycle (`com.hack337.omniisle.command`)
* **`OmniCommands.java`:** `/omniisle info`, `/omniisle verify`, `/omniisle debug <mode> [radius] [step]` (server-side PNG exporter), `/omniisle import <preset|code>`.
* **`OmniIslePreLaunch.java`:** On dedicated-server first boot, exports `config/omniisle.json` to `world/datapacks/omniisle-generated.zip` before packs are scanned.
* **Mixins:** `WorldTypeEditorMixin` (editor entry), `CreateWorldBridge` (select world type + inject pack into the pack repository).

---

## 5. Minecraft 26.3 API Mappings & Conventions

| Concept | Legacy (≤ 1.21.1) | 26.3 standard used here |
| :--- | :--- | :--- |
| **Identifier** | `new Identifier(...)` | `Identifier.fromNamespaceAndPath` / `Identifier.withDefaultNamespace` |
| **Density function type** | `Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, ...)` | Same; `MapCodec<? extends DensityFunction>` |
| **Density function cache** | `flat_cache`, `cache_2d` | `minecraft:cache` (`CacheFunction` → compiler-managed `CachingDensitySampler`). It is a **3D-keyed value cache**; column reuse comes from `domainAxes()` + `sampleVolume`, not from the cache alias. |
| **Density dispatch** | `sample(x,y,z)` | `compileSampler` → `DensitySampler.sampleValue` / `sampleVolume`; `DensityFunction.domainAxes()` |
| **Mouse buttons** | raw 0/1 | `InputConstants.MOUSE_BUTTON_LEFT/RIGHT` |
| **Key input** | `keyPressed(int,int,int)` | `net.minecraft.client.input.KeyEvent` |
| **Pack format** | 48 / 61 | `min_format = max_format = 121` (`TargetPlatform.PACK_FORMAT`) |
| **Structure placement** | `RandomSpreadStructurePlacement` | Must extend it for `/locate`; override `getStartChunk`/acceptance for 1-per-world and fixed sets |
| **Biomes** | — | All 56 Overworld biomes incl. `dappled_forest`, `sulfur_caves`, `pale_garden` |
| **Structures** | — | 18 options incl. `abandoned_camp` (forest/birch/taiga/jungle variants) and fixed v3 pyramids |

---

## 6. Datapack Schema

### 6.1. `continents.json`
The generated pack always has this shape (values from `IslandDraft`; optional keys are omitted when unset):

```json
{
  "type": "minecraft:cache",
  "input": {
    "type": "omniisle:island_continents",
    "config": {
      "generation_version": 3,
      "islet_density": 0.25,
      "river_density": 0.35,
      "world_border": false,
      "world_border_margin": 1000,
      "height_layer": { "bits_per_pixel": 2, "data": "<base64>" },
      "spawn": { "x": 128, "z": -64 },
      "entries": [],
      "mask": {
        "version": 1,
        "resolution": 192, "width": 192, "height": 192,
        "min_x": -2720.0, "max_x": 2720.0,
        "min_z": -2720.0, "max_z": 2720.0,
        "coast_width": 192.0,
        "ocean_transition_width": 400.0,
        "data": "<base64>"
      },
      "biome_seed": 123456789,
      "shore": { "density": 0.30, "variety": 0.6, "style": "natural", "rocky": true }
    }
  }
}
```

Codec defaults (`IslandShapeConfig.CODEC`): `generation_version=1`, `islet_density=0.25`, `river_density=0.0`, `world_border=false`, `world_border_margin=1000`, `entries=[]`, `biome_seed=0`; `height_layer`, `spawn`, `mask`, `shore` are optional. `shore` is emitted only for v3. The height layer is JSON base64 (there is no `.bin` sidecar). The static classpath template contains only `{"entries": []}` and is always overridden by the generated pack.

### 6.2. `omniisle.json` (world preset)
`generator.type = minecraft:noise`, `settings = omniisle:omni_terrain`, `biome_source.type = omniisle:island_biomes`. Generated keys: `biome_seed`, `climate_scale`, `generation_version`, `gradient_mode`, `gradient_strength`, `biome_theme`, enabled lists for land/shore/ocean/river/cave, and `shore` for v3. Missing enabled lists fall back to the full category list; empty categories fall back to a valid vanilla biome.

### 6.3. v3 extras
* `overworld/final_density.json`: `omniisle:spawn_plateau` (`input = omniisle:overworld/final_density_base`, `spawn_x`, `spawn_z`, `radius = 80`, `top_y = 78`).
* `data/minecraft/worldgen/structure/desert_pyramid.json` and `jungle_pyramid.json`: `omniisle:relaxed_desert_pyramid` / `omniisle:relaxed_jungle_temple`.
* Structure sets: single placements (ocean monument, woodland mansion, ancient city, stronghold set) and scaled random-spread sets for the rest; stronghold ring distance is `clamp(8, 32, round(32 · islandSize / 8000))`.

---

## 7. Hard Invariants (I1–I7)

1. **I1. Frozen versions:** v1 reproduces 0.5.0 bit-for-bit (`GoldenV1Test`). v2 reproduces 0.6.0 bit-for-bit (`GoldenV2Test`, snapshot generated from commit `7a5174d`). Any behavior change requires a new `generation_version` or a new optional field whose absence reproduces the old output. Golden comparisons are exact (`doubleToLongBits`), not tolerance-based.
2. **I2. Platform & UI conventions:** non-empty biome fallbacks; texture released once via `getTextureManager().release(...)`; `InputConstants` mouse constants; UTF-8 files; mod JAR mirrored to the PrismLauncher instance.
3. **I3. Server/preview parity:** `IslandDensityFunction`, `OmniBiomeSource`, `BiomePreview` and commands all call the same side-neutral `com.hack337.omniisle.continent` code. Zone parity must be 100 %; family agreement ≥ 97 %.
4. **I4. Determinism & thread safety:** samplers are pure functions of `(x, z, seed, config)`. No shared `Random`, no mutable static per-world state, no per-sample allocation in hot paths, no platform-dependent arithmetic.
5. **I5. Graceful failure:** corrupt/short mask data, unknown future `generation_version`, or missing optional JSON keys log and fall back; never crash world load. Unknown future versions are clamped to the newest known behavior.
6. **I6. Bilingual UI:** every user-facing string exists in `en_us.json` and `ru_ru.json`.
7. **I7. Platform freeze:** Minecraft 26.3, Fabric Loader 0.19.3, Fabric API 0.165.0+26.3, Java 25 and Loom 1.17.19 are fixed; public registry IDs (`omniisle:island_continents`, `omniisle:island_biomes`, `omniisle:spawn_plateau`, `omniisle:omni_terrain`) and world-preset ID are never renamed.

---

## 8. Generation Details

### 8.1. SDF core (v2/v3)
* **Exact EDT:** Felzenszwalb–Huttenlocher parabolic lower envelope on the native mask grid (192/256/384). Sub-pixel signed convention: inside `d_in − 0.5` px, outside `−(d_out − 0.5)` px.
* **Catmull-Rom with neighborhood clamp:** bicubic resampling clamped to the `[min,max]` of the 4×4 neighborhood, preventing overshoot/sign flips and terracing.
* **Precomputed grids:** flat `float[]` SDF and height grids built once per world config; sampling is allocation-free.
* **Domain warp (mask lookup only):** `x' = x + 45·n1(x/520,z/520) + 12·n2(x/190,z/190)`, same for `z'`. The warp never affects vanilla noise coordinates; v3 scales it quadratically below 4000-block islands (`0.5·(blocksPerPixel/28.3333)²`) to keep coast fidelity.
* **Shoreline micro-roughness:** `t = max(0, 1 − |c + 0.08| / 0.22)`, added fBm amplitude `0.045·smoothstep(t)`, centered on the actual shoreline value `−0.08`.

### 8.2. Land relief (v2/v3)
* **Coastal rise:** `base = −0.08 + 0.48·tanh(d·1.25 / coastWidth)`; levels near `0.40` inland.
* **Ridged spines:** for `d > 60`, `ridge = 1 − 2·|fbm(x/750, z/750)|`, `ridgeTerm = ridge·0.22·smoothstep((d−60)/190)·mountainBias·reliefScale`.
* **Height tiers:** 2-bit painted layer smoothed by a 3×3 kernel, bilinear sampled. `tierOffset` = `−0.15·(1−t)` for `t≤1`, `+0.18·(t−1)` for `1<t≤2`, `0.18+0.22·(t−2)` above; `mountainBias` rises 0.3 → 1.7.
* **Soft saturation:** for `c > 0.55`, `c = 0.55 + 0.40·tanh((c−0.55)/0.40)`, summit cap 0.95.
* **Rivers:** `carve = 0.14·smoothstep(1 − d_r/W)` subtracted from continentalness (v3 also on the ocean side so the mouth crosses the waterline).

### 8.3. v3 shore field (`ShoreField`)
* **Domain:** only columns with signed distance `d ∈ [intertidalLimit, MAX_INLAND=34]`, where `intertidalLimit(x,z,seed) = clamp(−6 − 12·fbm(x/16, z/16, salt), −18, 0)`.
* **Patch mask:** weighted fBm (`1/380`, `1/130`, `1/55`) thresholded by `threshold = 0.72 − 0.95·(0.88·density)`, with a gap term (`smoothstep(−0.34, −0.21, gapNoise)`). `patch ≤ 0` → no beach.
* **Width:** `(3 + 23·noise) · patch · factor · (1 − 0.45·notch)` (factor 0.25–1.15, notch from edge noise); `d > width` → NONE; water-side cells are kept only when `patch ≥ 0.55`.
* **Beach weight:** `beachWeight = patch · (0.35 + 0.65·bayness)`; below `0.20` → NONE. Bayness is the 8-direction ring average of signed distance at radius 60, mapped through `smoothstep(−0.15, 0.25, (mean − d)/60)`.
* **Stony chance:** if `rocky`, `stonyChance = 0.26 + 0.35·stonyWeight + 0.30·smoothstep(0.22, 0.58, slope)`, where `stonyWeight = (1 − bay)·(0.45 + 0.55·smoothstep(0.22, 0.58, slope))·patch`; a 33-block selector noise chooses STONY when `bay < 0.78`; otherwise SAND.
* **Outcomes:** `0 = NONE`, `1 = SAND`, `2 = STONY`. SAND maps to `snowy_beach` if temperature `< 0.24`, else `beach`; STONY maps to `stony_shore`. Certain hot/rocky land families suppress the shore outcome. Terrain flattens beach pockets by `1 − 0.38·beachMask` and steepens rocky cells by `1 + 0.18·rockyMask`.
* **Fallback:** if all shore biomes are disabled or `density = 0`, v3 keeps the legacy ribbon (`stony_shore`/`snowy_beach`/`beach` by slope and temperature).

### 8.4. Ocean / bathymetry (v2/v3)
* **Soft floor:** `b = max(cBase, −0.80)`; total clamped to `[−1.0, 0.70]`.
* **v2 ramp:** `cBase = −0.08 − 0.92·(s/Wo)`.
* **v3 ramp:** `cBase = −0.08 − 0.54·smoothstep(s/(0.4·Wo)) − 0.18·smoothstep(s/Wo)`; reaches `−0.62` (deep threshold) at `0.4·Wo` and the floor by `Wo`, with zero derivative at the coast.
* **Seabed noise:** weight `w = smoothstep(s/(0.5·Wo))`; `fbmBed·0.18` plus ridged trench `−trench·0.12`. Positive seamounts are multiplied by `seamountFade`, which is 0 inside `[0.4·Wo, 1.0·Wo]` (guaranteed deep band for monuments) and fades in beyond `Wo`.
* **Satellite islets:** 800-block jittered cellular grid, probability `0.15 + 0.5·isletDensity`, radius 40–160; centers must lie beyond `1.0·Wo + 80` (v3) / 350 blocks (v2) from the main island; a bell profile rises to `−0.08` inside `0.55·r` and returns to seabed.

### 8.5. Climate & biome selection
* **Zones:** `c < −0.30` ocean, `−0.30 ≤ c < 0.02` shore, else land; underground (`y < 0`) cave biomes; river biomes when the river network or the erosion/weirdness valley rule fires.
* **Scale & gradients:** `climate_scale` 384–1536 (default 720). Gradient amplitude is `0.5·themeSpan·clamp01(strength)`; theme spans ALL/STANDARD 0.90, MYSTIC 0.44, NORDIC 0.32, DESERT/TROPICAL 0.28. Modes OFF, NORTH_SOUTH, EAST_WEST, RADIAL.
* **Blending:** climate families blend with value-noise jitter (`±0.04` temperature, `±0.032` humidity for v2/v3), preventing straight borders.
* **Slope:** v2/v3 compute `slope = hypot((cE−cW)/8, (cS−cN)/8)·64` from neighboring climate samples; v3 uses it for stony shores.
* **Biome selection:** family lists are selected by temperature/humidity/mountain class; enabled-user lists are intersected with the category; empty categories fall back safely. Dynamic registry supplies modded/datapack biomes.

### 8.6. Structures & spawn
* **18 structure options**, all filtered to land/biome/depth conditions by the pack's structure sets. Strongholds: fixed 3-start ring scaled by island size. Ocean monuments: guaranteed deep band. Ancient cities: deep-dark conditions independent of surface peaks.
* **Spawn selection:** largest positive SDF within 40 % of the island radius, written as `spawn` into the config. v3 places a local dome (`radius 80`, `top_y 78`) so the marker is dry. On load, `OmniIsleMod` initializes the respawn point: v1/v2 run an SDF spiral search, v3 reconciles after `SERVER_STARTED` using the marker surface or the deepest land on sampled rings (radius 16–512, stop at depth 48). This sets the world spawn; it does not teleport an existing player.
* **World border:** optional, centered on the island with a 500–4000-block margin.

### 8.7. Editor, share codes, PNG
* Tools: brush (1–24 px), fill, line (Bresenham), rectangle, ellipse (Shift square/circle, fill/outline), smooth. Symmetry: off, mirror-X, mirror-Z, 4-way, 6-way, 8-way (forward-orbit stamping, exact closure). Undo/redo covers land, height, spawn, resolution and every tool.
* 12 presets: Natural, Round, Long, Crescent, Archipelago, Atoll, Volcano, Fjords, Twin Islands, Inland Sea, Horseshoe Bay, Ridge Island.
* Share codes deflate JSON payload; CRC16 verified; 256 KiB cap. PNG import supports mask threshold/invert and height quantile mode; export writes mask, height, biome, hillshade and shore maps.

### 8.8. Dedicated server & commands
* First boot without a world: `preLaunch` converts `config/omniisle.json` into `world/datapacks/omniisle-generated.zip` before `WorldLoader` scans packs. `/omniisle import` can replace it later.
* `/omniisle info` reports version, resolution, island size, bounding box, spawn, border and active layers; `/omniisle verify` probes surface/biomes; `/omniisle debug` writes server-side PNG maps.

---

## 9. Verification & Evidence (0.7.0)
* **Golden v1:** 30 preset/relief/size configs × 10 000 continentalness points + 200×200 biomes + deep-dark probes — exact.
* **Golden v2:** 30 configs × 1 500 points × 4 metrics (`continentalness`, `computeSignedDistance(false)`, `signedDistance`, `erosionBias`) — bit-exact against 0.6.0 (`docs/evidence` / `src/test/resources/golden/v2`).
* **Parity:** `BiomePreview` vs `OmniBiomeSource` zone parity 100 %, family agreement > 99.9 % on sampled grids (see `docs/evidence/`).
* **Coast fidelity (v3):** worst mean 0.2955 px and max 1.5778 px at 4000; 0.2462 / 1.3579 px at 2500 (budgets 0.6 / 2.0).
* **Shore acceptance:** fractions, longest straight border, density sweep monotonicity — `docs/evidence/shore/`.
* **Relief:** 93–107 local maxima per seed, max height 163.2, longest plateau component 96 blocks, tier-3 boost +122.4.
* **Structures/spawn:** full 20-structure matrix only 15/45 configs (PARTIAL); stronghold-only real `/locate` 45/45 on land.
* **Real chunk-gen:** dedicated server, fresh world, 25×25 chunks, 3 reps per version; numbers in `docs/evidence/perf/chunkgen_25x25.csv` and the final Phase 9 report. v2/v3 exceed the +15 % budget on the measured offshore area; see the report for the inland comparison and known issues.
* **Editor:** 12 presets × 20 seeds pass; PNG export 5/5; layout at 480×270; texture lifecycle 50/50. Screenshots at GUI scales 1–4 remain **NOT DONE** (`UI_CHECKLIST.md`).
