# OmniIsle: Technical Specification & AI Agent Reference Manual
> **Target Audience:** Autonomous Coding Agents, LLM Pair Programmers, Modding Architects.  
> **Minecraft Version:** 26.3  
> **Mod Loader:** Fabric Loader (>= 0.19.5)  
> **Java Version:** Java 25 (LTS)  
> **Loom Version:** 1.17.19  
> **License:** MIT / OmniIsle Original License  

---

## 1. Executive Summary & Core Objective
**OmniIsle** is an open-source Minecraft mod that transforms standard infinite Overworld generation into a standalone, fully customizable, isolated island surrounded by an endless ocean. 

Unlike mods that force hardcoded static maps, OmniIsle provides:
1. An **in-game real-time GUI editor** embedded seamlessly into the vanilla "Create New World" screen with drawing tools, symmetry, layers, presets, and PNG tools.
2. An algorithmic bridge translating pixel-drawn masks into continuous **Signed Distance Fields (SDF)** for Minecraft's native 3D noise router (density functions).
3. A dual-generation architecture (**v1 legacy** bit-for-bit preserving 0.5.0 worlds, and **v2 modern** with domain warping, micro-roughness, mountain spines, bathymetry, satellite islets, and carved rivers).
4. A climate-driven custom **BiomeSource** (`omniisle:island_biomes`) supporting all 56 Overworld biomes and modded biomes via dynamic registry discovery.
5. Automatic generation and injection of a lightweight, self-contained worldgen datapack (`omniisle-generated.zip`) upon world creation.

---

## 2. Technical Stack & Build Environment
* **Platform:** Fabric Mod for Minecraft Overworld generation.
* **Build System:** Gradle 9.5+ with `fabric-loom` 1.17.19.
* **Compiler Requirements:** JDK 25 (`sourceCompatibility = JavaVersion.VERSION_25`, `targetCompatibility = JavaVersion.VERSION_25`).
* **Key Dependencies:**
  * `net.fabricmc:fabric-loader:0.19.5`
  * `net.minecraft:minecraft:26.3`
  * Mojang `DataFixerUpper` (`com.mojang.serialization.Codec`, `MapCodec`, `RecordCodecBuilder`).
  * `com.mojang.blaze3d.platform.NativeImage` for in-memory GUI texture rendering.

---

## 3. End-to-End Execution Pipeline

```
[CreateWorldScreen (Vanilla)]
            │
            ▼ (Mixin: WorldTypeEditorMixin -> DynamicBiomeRegistry.updateFromRegistry)
[OmniIsleEditorScreen] <──────> [IslandDraft (192/256/384 grid + Land + Height Tiers)]
            │                               │
            │ (Paints, symmetry, tools)     ▼
            │                         [BiomePreview] <---> [IslandTerrain (v1/v2)]
            ▼
[Click "Done" (Commit)]
            │
            ▼ (WorldPackBuilder.export())
[Pack: omniisle-generated.zip]
   ├── pack.mcmeta (Format 121)
   ├── data/omniisle/worldgen/density_function/overworld/continents.json (IslandMask SDF + v2 configs)
   ├── data/omniisle/worldgen/world_preset/omniisle.json (OmniBiomeSource)
   └── data/minecraft/worldgen/structure_set/*.json (Scaled ring / single structure placements)
            │
            ▼ (Mixin: CreateWorldBridge injects pack into PackRepository)
[World Generation / Server Startup]
   ├── IslandDensityFunction -> IslandTerrain (ModernTerrainV2 or LegacyTerrainV1)
   │     ├── Exact EDT on native grid + Catmull-Rom bicubic interpolation
   │     ├── Domain warp (mask lookup only) + shoreline micro-roughness (-0.08)
   │     ├── Mountain ridges (headroom + soft tanh) + blurred height tiers
   │     ├── Bathymetry (soft floor -0.80) + guaranteed deep band + satellite islets
   │     └── IslandRivers (Priority-flood depression filling + C1 smooth carving)
   ├── OmniBiomeSource (Shared IslandClimate + latitudinal gradient + jittered blending + dynamic biomes)
   └── Spawn / WorldBorder setup (Safe inland spawn search + centered world border)
```

---

## 4. Codebase Architecture & Class Roles

### 4.1. Core Client / GUI Layer (`com.hack337.omniisle.client`)
* **`IslandDraft.java`:** The in-memory data model representing the island draft. Supports dynamic resolutions (192, 256, 384) with bilinear resampling, dual layers (boolean[] land, byte[] height 0-3), undo/redo immutable snapshots, 12 procedural presets, 6 symmetry modes, river density, world border, and largest-SDF auto-spawn selection.
* **`OmniIsleEditorScreen.java`:** The interactive editor GUI. Divided into 4 clean tabs (`DRAW`, `SHAPE`, `CLIMATE`, `PRESETS`), with zoom/pan (1x-8x), 16px grid toggle, tool selection, seed inputs with dice buttons, and an inspector tooltip displaying elevation, height tier, distance to coast, and islet/ocean status.
* **`AdvancedSettingsScreen.java`:** Secondary screen for fine-tuning generation version (v2/v1), climate scale (384-1536), latitudinal gradient mode and strength, satellite islet density, river density, structure density, and world border margin.
* **`ShareCode.java`:** Encodes and decodes designs using `OMNI2-<base64url(deflate(payload))>-<crc16>` with CRC16 verification and a strict 256 KiB decompressed payload cap.
* **`ImageImportExport.java`:** PNG import with grayscale threshold (0-255), invert, aspect-preserving letterbox, and heightmap quantile mode; exports B/W mask, 4-shade height layer, color biome map, and NW hillshade.
* **`BiomePreview.java`:** Fast client-side counterpart of `OmniBiomeSource` powered by shared `IslandClimate` and `IslandRivers` for 100% zone parity and > 99.9% family agreement.
* **`DynamicBiomeRegistry.java`:** Registry-driven biome discovery scanning `WorldCreationContext` at world creation, categorizing into LAND, SHORE, OCEAN, RIVER, and CAVE with structure associations and falling back to 56 Overworld biomes.
* **`WorldPackBuilder.java`:** Generates `omniisle-generated.zip` containing `pack.mcmeta`, `continents.json`, `omniisle.json`, and scaled structure sets.

### 4.2. Side-Neutral Continent / Terrain Layer (`com.hack337.omniisle.continent`)
* **`IslandTerrain.java`:** Side-neutral terrain interface exposing `continentalness(x, z)`, `erosionBias(x, z)`, `signedDistance(x, z)`, and `rivers()`.
* **`ModernTerrainV2.java`:** Modern (0.6.0+) terrain generator implementing exact Felzenszwalb-Huttenlocher EDT on native grid, flat `float[]` cache, Catmull-Rom bicubic interpolation with neighborhood clamping, mask-only domain warping, shoreline micro-roughness, ocean floor bathymetry, satellite islets, procedural mountain spines, and carved rivers.
* **`LegacyTerrainV1.java`:** Frozen 0.5.0 terrain implementation guaranteeing bit-for-bit backward compatibility for existing worlds.
* **`IslandRivers.java`:** Deterministic river and lake derivation via coarse height estimation, Priority-Flood depression filling, D8 flow routing, flow accumulation, and $C^1$ continuous smoothstep carving into continentalness and erosion.
* **`IslandMask.java`:** Holds raster data, EDT calculation, bilinear sampling, and Mojang Codec serialization with backward-compatible defaults.
* **`IslandClimate.java`:** Shared side-neutral climate classification implementing temperature gradients (N-S, E-W, Radial) with theme-aware compression, noise-jittered border blending, and slope-based stony shore cliff selection.
* **`IslandShapeConfig.java`:** Side-neutral configuration containing mask, generationVersion, isletDensity, riverDensity, worldBorder, heightLayer, spawn, and biomeSeed.

### 4.3. Worldgen Layer (`com.hack337.omniisle.worldgen`)
* **`IslandDensityFunction.java`:** Mojang DensityFunction registered as `omniisle:island_continents`, compiled with 2D column caching.
* **`OmniBiomeSource.java`:** Mojang BiomeSource registered as `omniisle:island_biomes`, resolving surface, shore, ocean, river, and cave biomes using `IslandClimate` and `IslandRivers`.
* **`SpawnSavedData.java`:** World-saved data ensuring one-time world spawn initialization on first load.
* **`SingleStructurePlacement.java`:** Custom structure placement type guaranteeing 1-per-world single structure positioning.
* **`RiverLife.java`:** Injects fish mob spawns into river biomes.

### 4.4. Commands & Lifecycle (`com.hack337.omniisle.command`)
* **`OmniCommands.java`:** Registers `/omniisle info` (worldgen statistics), `/omniisle verify` (biome/surface probe), `/omniisle debug <mode> [radius] [step]` (PNG map exporter using real server-side sampler), and `/omniisle import <preset|code>` (dedicated server preset loader).

---

## 5. Minecraft 26.3 API Mappings & Conventions

| Concept | Legacy (<= 1.21.1) | Modern 26.3 Standard |
| :--- | :--- | :--- |
| **Identifier Parsing** | `new Identifier(namespace, path)` | `Identifier.fromNamespaceAndPath(namespace, path)` |
| **Default Namespace** | `new Identifier(path)` | `Identifier.withDefaultNamespace(path)` |
| **Density Function Type** | `Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, ...)` | Same (Must register `MapCodec<? extends DensityFunction>`) |
| **Density Function Cache** | `flat_cache`, `cache_2d` | `"type": "minecraft:cache"` (compiles as 2D column cache when $Y$ axis omitted) |
| **Mouse Button Left** | 0 | `InputConstants.MOUSE_BUTTON_LEFT` (GLFW button 0) |
| **Mouse Button Right**| 1 | `InputConstants.MOUSE_BUTTON_RIGHT` (GLFW button 1) |
| **Keyboard Event** | `keyPressed(int, int, int)` | `net.minecraft.client.input.KeyEvent` |
| **Pack Format** | Format 48 / 61 | Format `121` (`TargetPlatform.PACK_FORMAT`) |
| **New Biomes** | N/A | `minecraft:dappled_forest` (Family 3), `minecraft:sulfur_caves` (Cave) |
| **New Structures**| N/A | `minecraft:abandoned_camp` (Forest, Birch, Taiga, Jungle variants) |

---

## 6. Datapack Schema & Presets Reference

### Continents Density Function (`data/omniisle/worldgen/density_function/overworld/continents.json`):
```json
{
  "type": "minecraft:cache",
  "input": {
    "type": "omniisle:island_continents",
    "config": {
      "generation_version": 2,
      "islet_density": 0.25,
      "river_density": 0.35,
      "world_border": false,
      "world_border_margin": 1000,
      "height_layer": {
        "bits_per_pixel": 2,
        "data": "<base64>"
      },
      "spawn": {
        "x": 128,
        "z": -64
      },
      "entries": [],
      "mask": {
        "version": 1,
        "resolution": 192,
        "width": 192,
        "height": 192,
        "min_x": -2720.0,
        "max_x": 2720.0,
        "min_z": -2720.0,
        "max_z": 2720.0,
        "coast_width": 192.0,
        "ocean_transition_width": 400.0,
        "data": "<base64>"
      },
      "biome_seed": 123456789
    }
  }
}
```

---

## 7. Hard Invariants (I1 – I6)

1. **I1. Backward Compatibility:** Worlds created with OmniIsle 0.5.0 (`generation_version = 1`) must load in 0.6.0 and produce bit-for-bit identical terrain, biomes, and structures in newly generated chunks (verified by `GoldenV1Test`).
2. **I2. Spec §7 Invariants:** Fallbacks for empty biome categories; `DynamicTexture` released via `getTextureManager().release(previewId)` in `removed()`; use `InputConstants.MOUSE_BUTTON_LEFT/RIGHT`; UTF-8 encoding across all files; release JAR mirrored to Prism instance.
3. **I3. Server/Preview Parity:** The terrain sampler used by the server (`IslandDensityFunction`) and client editor preview/inspector (`BiomePreview`) is the *exact same code* in the side-neutral `com.hack337.omniisle.continent` package.
4. **I4. Determinism & Thread Safety:** Worldgen samplers are pure functions of `(x, z, seed, config)`: no shared `Random` across threads, no mutable static state, no per-call allocation in hot paths, double arithmetic without platform variance.
5. **I5. Graceful Failure:** Corrupt or short mask data, unknown future `generation_version`, or missing optional JSON fields log warnings and fall back gracefully without crashing.
6. **I6. Bilingual Localization:** Every UI string has translation keys in both `en_us.json` and `ru_ru.json`.

---

## 8. OmniIsle 0.6.0 Architecture & Modern Worldgen Pipeline (v2)

### 8.1. Eliminating SDF Terracing & Stepping
* **Exact Felzenszwalb-Huttenlocher EDT on Native Grid:** Computes parabolic lower envelope $O(N)$ Euclidean distance on native grid (192, 256, 384). Sub-pixel distance convention: inside land $= d_{\text{in}} - 0.5$ px, outside $= -(d_{\text{out}} - 0.5)$ px.
* **Catmull-Rom Bicubic Interpolation with Neighborhood Clamping:** Resamples the continuous SDF with Catmull-Rom bicubic interpolation. Values are strictly clamped to the $[min, max]$ of their $4 \times 4$ neighborhood to eliminate overshoot and prevent sign flipping.
* **Flat Float Array Cache:** Precomputed once per config during initialization, avoiding per-sample EDT overhead.
* **Domain Warping on Mask Lookup Only:** Shifts $(x, z)$ with low-frequency noise (frequency $1/520$, amplitude 45 blocks, plus second octave at $1/190$, amplitude 12 blocks) applied *only* to the mask/SDF coordinate projection.
* **Shoreline Micro-Roughness:** Centered at the actual shoreline value of **$-0.08$** (rather than 0.0): $t = \max(0, 1 - |c - (-0.08)| / 0.22)$, adding fine fBm noise with smoothstep falloff without displacing the painted coast.

### 8.2. Breaking the Conical Relief: Mountain Spines & 4-Tier Height Layer
* **Compressed Coastal Rise:** Base continentalness is formulated as $\text{base} = -0.08 + 0.48 \cdot \tanh(d \cdot 1.25 / \text{coastWidth})$, leveling off to $\approx 0.40$ inland and leaving dynamic range ($0.40 \dots 0.95$) for mountain relief.
* **Procedural Ridged Mountain Spines:** Adds low-frequency ridged noise $\text{ridge} = 1 - 2 \cdot |\text{fbm}(x/750, z/750)|$ inland ($d > 60$ blocks) modulated by relief scale and mountain bias.
* **Soft Saturation:** Replaces hard clamping with smooth hyperbolic saturation: for $c > 0.55$, $c = 0.55 + 0.40 \cdot \tanh((c - 0.55)/0.40)$, allowing summits up to $0.95$.
* **4-Tier Height Layer:** Painted 2-bit height layer (0: Lowland, 1: Normal, 2: Plateau/Hills, 3: Alpine Peaks). Stored as Base64 in `continents.json`. Treated as a continuous float field, smoothed with a 3x3 kernel and bilinear sampled to prevent vertical walls. Also modulates erosion bias.
* **Hillshade Preview Shading:** Northwest illumination (azimuth 315°, altitude 45°) blended 35% over biome colors.

### 8.3. Bathymetry, Guaranteed Deep Band & Satellite Islets
* **Soft Floor at $-0.80$:** Replaces flat $-1.0$ lock, leaving headroom for oceanic trenches (down to $-1.0$) and seamounts (up to $-0.50$).
* **Seabed Noise:** Continuous combination of fBm seabed and ridged trenches with weight $w = \text{smoothstep}(s / (0.5 \cdot W_o))$ that is strictly 0 at the coast.
* **Guaranteed Deep Band:** For offshore distance $s \in [0.4 W_o, 1.0 W_o]$, positive seabed noise is forbidden, guaranteeing a continuous deep ocean band for Ocean Monument generation.
* **Satellite Islets:** Jittered cellular noise with density slider ($0.0 \dots 1.0$). Islets generate $\ge 350$ blocks from the main island with bell-shaped smoothstep profiles, receiving beaches, local climate, and vanilla ocean structures.

### 8.4. Climate System & Latitudinal Gradients
* **Biome Scale Slider:** Configurable `climate_scale` ($384 \dots 1536$, default 720) controlling biome size.
* **Latitudinal Temperature Gradient:** Gradient options (Off, North-South, East-West, Radial-from-center). Gradient amplitude is theme-aware: narrow themes (Nordic, Desert, Tropical) compress the gradient amplitude to stay within thematic limits.
* **Noise-Jittered Blending:** Neighbouring climate cells are blended with value noise jitter to prevent straight-line biome borders.
* **Slope-Based Stony Shore:** Coastlines where local slope $\ge 0.35$ generate dramatic stony shore cliffs rather than sandy beaches.

### 8.5. Structure Placement & Inland Spawn Selector
* **Stronghold Ring Scaling:** Stronghold placement ring `distance` is dynamically scaled by island size ($d = \text{clamp}(8, 32, \text{round}(32 \cdot \text{islandSize} / 8000))$), ensuring strongholds generate reliably on land even on 2500-block islands.
* **Ancient City & Deep Dark:** Deep dark underground conditions are decoupled from mountain surface peaks, guaranteeing underground Ancient Cities where depth permits.
* **Largest-SDF Spawn Selector:** Automatically selects the inland pixel with the largest positive SDF within 40% of the island radius, guaranteeing spawn on solid land.
* **First-Load Relocation:** On first world load, a spiral land search relocates the player to the nearest safe column if in water.

### 8.6. Advanced Editor Features & Deflate Share Codes
* **Multi-Resolution Draft Architecture:** Selectable resolutions (192, 256, 384) with bilinear resampling of land and height layers upon canvas resize.
* **Share Codes:** Compact `OMNI2-<base64url(deflate(payload))>-<crc16>` format with CRC16 validation and 256 KiB decompressed payload cap.
* **Drawing Tools & Symmetry:** Fill (flood fill), Line (Bresenham), Rectangle, Ellipse (Shift for square/circle, fill/outline toggle), and 6 symmetry modes (Off, Mirror-X, Mirror-Z, 4-way, 6-way, 8-way).
* **Procedural Shape Presets:** 12 presets total (Natural, Round, Long, Crescent, Archipelago, Atoll, Volcano, Fjords, Twin Islands, Inland Sea, Horseshoe Bay, Ridge Island).
* **PNG Import/Export:** Import mask with grayscale threshold and invert; import heightmaps to land + height tiers; export mask, height layer, color biome map, and hillshade.

### 8.7. Physically Carved Rivers & Stretch Features
* **Deterministic River Network:** Derived at world load from `(mask, height layer, seed, riverDensity)` in $< 25$ ms. Uses Priority-Flood depression filling to eliminate pits, D8 flow routing, and flow accumulation.
* **$C^1$ Continuous River Carving:** River channels are carved using smoothstep falloff $\text{carve} = 0.14 \cdot \text{smoothstep}(1 - d_r / W)$, maintaining exact continuity across chunk boundaries without terrace artifacts.
* **Dynamic Biome Registry:** Discovers modded biomes from `WorldCreationContext` and assigns climate families dynamically.
* **World Border:** Centers vanilla `WorldBorder` on the island with a configurable margin ($500 \dots 4000$ blocks).
* **Dedicated Server Support:** Automatically builds `omniisle-generated.zip` from `config/omniisle.json` on dedicated server startup, with `/omniisle import` for live preset loading.
