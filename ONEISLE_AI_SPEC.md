# OneIsle: Technical Specification & AI Agent Reference Manual
> **Target Audience:** Autonomous Coding Agents, LLM Pair Programmers, Modding Architects.  
> **Mod version:** 1.0.0+mc26.3-fabric  
> **Minecraft Version:** 26.3  
> **Mod Loader:** Fabric Loader (`>= 0.19.3`)  
> **Fabric API:** `0.165.0+26.3`  
> **Java Version:** Java 25 (LTS)  
> **Loom Version:** 1.17.19  
> **License:** GPL-3.0-only  

> **Identity:** OneIsle 1.0.0 is an independent continuation of OmniIsle 0.5.0 by Hack337
> (GPL-3.0-only, not affiliated or endorsed). It includes the work prepared in the OmniIsle
> 0.5.0–1.0.1 line; version labels such as `0.8.x`, `1.0.0` and `1.0.1` below are historical
> OmniIsle version labels. All runtime identifiers use the `oneisle` namespace; legacy
> `omniisle:*` identifiers are not registered (deliberate breaking change, see I1/I7).

---

## 1. Executive Summary & Core Objective
**OneIsle** turns the infinite Overworld into one customizable island surrounded by an endless ocean.

1. An **in-game editor** embedded in the vanilla "Create New World" screen: drawing tools, symmetry, undoing, 13 presets (12 procedural + the fixed Mineshield 4 Map), 3 canvas resolutions, PNG import/export and share codes.
2. A bridge from the painted mask to Minecraft's noise router: the mask is converted to a continuous **signed distance field (SDF)** and sampled by the custom density function `oneisle:island_continents`.
3. **Three generation versions** behind one config field (v1/v2 frozen, v3 revised in place):
   * **v1** — legacy 0.5.0 sampler (`LegacyTerrainV1`), bit-for-bit compatible.
   * **v2** — modern SDF terrain (`ModernTerrainV2`): EDT + Catmull-Rom, domain warp, ridged mountains, height tiers, bathymetry, satellite islets, carved rivers. This is the exact 0.6.0 output, bit-for-bit frozen by `GoldenV2Test`.
   * **v3** — v2 plus a deterministic **shore field** (patchy sand/stony/rocky coasts), a smooth ocean depth ramp that reaches the monument band before `0.4·Wo`, islets moved beyond the monument band, river mouths carved across the waterline, universal climate provinces, and since Phase 13 a generated offset lift (`scale`/`lift` splines on continentalness) with a 300→316 vertical fade plus a real dry-spawn search. Since 1.0.1 (Phase 14) beaches require an actually open water body plus a low coastal shelf, villages use five biome-matched vanilla variants at −60.4 % density, and warm↔cold family selection follows continuous strength ramps with a province-independent mountain-snow exception. **Default for new worlds.**
4. A climate-driven custom **BiomeSource** (`oneisle:island_biomes`) using the shared side-neutral `IslandClimate`; every vanilla Overworld biome plus dynamically discovered datapack/modded biomes. Since 0.8.0 climate-driven land biomes also have optional **relative-frequency weights** (0–400 %, default 100) calibrated into per-biome biases; absent/all-100 is bit-identical to 0.7.0.
5. Automatic generation and injection of a self-contained worldgen datapack (`oneisle-generated.zip`) at world creation or dedicated-server first boot.

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
[OneIsleEditorScreen] <──────> [IslandDraft (192/256/384 grid, land + 2-bit height)]
            │                               │
            │ paints / symmetry / tools     ▼
            │                         [BiomePreview] <──> [IslandTerrain (v1/v2/v3)]
            ▼                               │
[Done]                                      │
            │                               │
            ▼ (WorldPackBuilder.export())
[oneisle-generated.zip]
   ├── pack.mcmeta (min_format = max_format = 121)
   ├── data/oneisle/worldgen/density_function/overworld/continents.json
   │      (minecraft:cache -> oneisle:island_continents)
   ├── data/oneisle/worldgen/density_function/overworld/offset.json            (v3 only)
   │      (add(mul(static offset, scale(c)), lift(c)))
   ├── data/oneisle/worldgen/density_function/overworld/final_density.json      (v3 only)
   │      (final_density_base with the fade raised to 300->316)
   ├── data/oneisle/worldgen/density_function/overworld/preliminary_surface_level.json (v3 only)
   ├── data/minecraft/worldgen/structure/desert_pyramid.json, jungle_pyramid.json (v3 only)
   ├── data/oneisle/worldgen/world_preset/oneisle.json (omnibiome source + enabled lists)
   └── data/minecraft/worldgen/structure_set/*.json (single/ring/fixed placements; v3 villages = 5 variants)
            │
            ▼ (mixin: CreateWorldBridge injects the pack into the PackRepository)
[World generation / dedicated-server startup]
   ├── IslandDensityFunction (oneisle:island_continents)
   │     └── IslandTerrain (LegacyTerrainV1 | ModernTerrainV2)
   │           ├── Landside: EDT/Catmull-Rom SDF, domain warp, micro-roughness,
   │           │   mountain spines, height tiers, v3 shore carving, river carve
   │           └── Ocean side: soft floor -0.80, seabed fBm + trenches,
   │               v3 deep band, satellite islets
   ├── OneBiomeSource (oneisle:island_biomes)
   │     └── IslandClimate (family blend, latitudinal gradient, cave/river zones)
   │         └── v3: ShoreField outcome (NONE/SAND/STONY)
   ├── SpawnPlateauDensity (v3 final_density wrapper, only near the spawn marker)
   └── SpawnSavedData + world border setup (first-load relocation, centered border)
```

---

## 4. Codebase Architecture & Class Roles

### 4.1. Client / GUI (`com.reystin.oneisle.client`)
* **`IslandDraft.java`:** Draft data model. Resolutions 192/256/384 (bilinear resampling), `boolean[] land` + `byte[] height` (0–3), undo/redo snapshots, 12 seeded procedural presets plus the fixed `MINESHIELD_4_MAP` preset, 6 symmetry modes, climate/gradient/shore/river/islet/structure settings, **generation version default V3**, world border, largest-SDF spawn selection. New drafts default to theme ALL; per-biome land weights (0–400, step 5) and calibrated bias/achieved/target are draft state covered by undo/redo and share codes.
* **`OneIsleEditorScreen.java`:** Six-tab editor (`SHAPE`, `TERRAIN`, `CLIMATE`, `BIOMES`, `STRUCTURES`, `TOOLS`) presented by the flat `client/ui` widget system: top bar (brand, undo/redo, preset menu, share code, PNG tools, advanced, settings), an eight-tool column, a framed canvas with Land/Height/Biomes/Shore preview modes, 2×3 labelled tabs, scrolling controls, a FULL-only right list and a status bar with Done/Cancel. Density tiers FULL/MEDIUM/COMPACT/MINI/MINIMAL come from the pure `EditorLayout`; MINI is icon-only with tooltips/narration, MINIMAL shows the actual logical size plus Cancel/Done. Zoom/pan (1×–8×), 16-px grid, brush/line/rect/ellipse/fill/smooth, seed fields with dice and the inspector (biome, surface Y, coast distance, islet, height tier, shore outcome) are unchanged. List rows show a toggle, a −/value/+ weight stepper, a read-only actual share with ✓/!/✕ and a terrain/climate tooltip; recalibration is debounced 150 ms on a daemon executor and stale results are rejected by fingerprint.
* **`client/ui/*` (22 classes):** `OneTheme` tokens (8 WCAG pairs ≥ 4.5:1, 80 ms easing, 50 % disabled), `OneUi` primitives, `OneInteraction`, `OneMetrics`/`OneText` (cached real font widths, grapheme-safe ellipsis, no per-frame allocation), `OneButton`/`OneIconButton`, `OneToggle`, `OneSegmented`, `OneSlider`, `OneTabBar`, `OneSection`, `OneScrollArea`/`OneListView`/`OneBiomeList`, `OneTooltip` (400 ms), `OneToast`, `OneDropdown`, `OnePopover`, `OneTextField` (flat frame around a vanilla `EditBox`) and `OneIcons` (20 icons in a 128×48 atlas generated by `tools/generate_ui_icons.py`, text fallback). No vanilla `Button`/`CycleButton`/`AbstractSliderButton` remains in the editor.
* **`client/OneIsleClient`, `client/dev/UiScreenshotHarness`:** client entrypoint and a dev-only harness that is a no-op unless `-Doneisle.uiScreenshot=<dir>` is set; it captures the tab × window × GUI-scale × language matrix, writes `manifest.csv`/`render_cost.csv`, and is not active in normal play.
* **`AdvancedSettingsScreen.java`:** Generation version (v1/v2/v3), climate scale (384–1536), gradient mode/strength, islet/river/structure density, shore density/variety/rocky, world border margin, seed and share-code actions.
* **`ShareCode.java`:** `ONE2-<base64url(deflate(json))>-<crc16>` with CRC16 check and 256 KiB decompressed cap. Missing `river_density`/`shore`/`height_layer` keys keep legacy defaults.
* **`ImageImportExport.java`:** grayscale mask import (threshold/invert/letterbox), heightmap import (25/50/75 % quantiles → tiers 0–3), exports mask, height, biome color map, NW hillshade and shore map.
* **`BiomePreview.java`:** Client mirror of `OneBiomeSource` built on the same `IslandClimate`, `IslandRivers`, `ShoreField` and `IslandTerrain` classes. Preview textures upload `NativeImage` into a registered `DynamicTexture` and release it exactly once.
* **`DynamicBiomeRegistry.java`:** Scans `WorldCreationContext`; categorizes biomes into LAND/SHORE/OCEAN/RIVER/CAVE with structure tags; falls back to 56 vanilla biomes.
* **`WorldPackBuilder.java`:** Writes `oneisle-generated.zip` (schema in §6), including scaled structure sets — v3 villages are five family-scoped vanilla variants at 54/12 (§8.6) — and, for v3, the highland `offset.json` wrapper, fade-raised `final_density.json`/`preliminary_surface_level.json` and relaxed pyramid structures. For non-neutral weights it calibrates a copy of the draft and writes `biome_share`; structure sampling uses the weighted preview selection. `validatePreset(zip, registries)` decodes the generated `world_preset/oneisle.json` with `WorldPreset.DIRECT_CODEC` under the live `RegistryOps` context before the editor enables the pack; failures surface as `oneisle.editor.error.preset`.
* **`BiomeShareCalibrator.java`:** Fixed 64×64 mask-land calibration shared by the editor and export; see §8.9.
* **`PreviewViewport.java`, `PreviewTextureSlot.java`, `EditorLayout.java`, `PngToolsScreen.java`, `ShareCodeScreen.java`, `BiomeSelectionScreen.java`, `EditorDraftStore.java`, `ClientScreenNavigator.java`:** viewport math, texture lifecycle guard, pure density-tier layout (`screen/topBar/toolColumn/canvas/tabs/controls/listPanel/status` rectangles, allocation-free `placeTooltip`), PNG/share-code/biome dialogs, draft persistence, screen transitions. The satellite screens use the shared flat panels, buttons, toggles, sliders and text fields; their file/draft behavior is unchanged.

### 4.2. Side-neutral terrain (`com.reystin.oneisle.continent`)
* **`IslandTerrain.java`:** Interface: `continentalness(x,z)`, `signedDistance(x,z)`, `erosionBias(x,z)`, `rivers()`. Factory selects v1 vs v2/v3.
* **`ModernTerrainV2.java`:** v2 and v3 sampler. Exact Felzenszwalb–Huttenlocher EDT on the native grid, sub-pixel convention, Catmull-Rom bicubic with 4×4 clamp, mask-only domain warp, shoreline micro-roughness at `-0.08`, height-tier offsets, ridged mountains, soft tanh saturation, ocean bathymetry/islets, v3 ocean ramp and shore carving. Precomputes flat `float[]` SDF/height grids; sampling itself allocates nothing.
* **`LegacyTerrainV1.java`:** Frozen 0.5.0 formula (`LandmassSampler` + erosion), no rivers, no v2/v3 terms. Do not edit for behavior.
* **`IslandRivers.java`:** Deterministic river network from `(mask, height, seed, river_density)`: coarse height, Priority-Flood depression fill, D8 routing, flow accumulation, C¹ smoothstep carve into continentalness and erosion. Density 0 disables it.
* **`IslandMask.java`:** Raster + EDT + bilinear sampling + codec (`version`, `resolution`, bounds, `coast_width`, `ocean_transition_width`, base64 `data`); tolerant of short/corrupt data.
* **`IslandClimate.java`:** Shared classification: `OCEAN_START = -0.30`, `INLAND_START = 0.02`, `DEEP_OCEAN_THRESHOLD = -0.58`, `STEEP_SHORE_SLOPE = 0.35`; theme spans, gradient modes (OFF/NORTH_SOUTH/EAST_WEST/RADIAL), jittered family blending, v1/v2 `sample` and v3 `sampleV3` (shore outcome + signed distance). v3 `selectLandFamilyV3` selects warm/cold families from `diff = warmStrength − coldStrength` with `V3_CLIMATE_CORE_DIFF = 0.35` / `V3_CLIMATE_BAND_DIFF = 0.12`; the categorical province no longer switches families (1.0.1).
* **`ShoreField.java` / `ShoreSettings.java`:** v3 deterministic shore outcome: ring scan (bayness + minimum signed distance), patch noise, inland width, beach/stony weights. Every beach requires an open water body (`ring.minDistance ≤ −55`), a low shelf (`heightTier ≤ 1.10`, `continentalness ≤ 0.20`) and a bay (`bay ≥ 0.20`), so fjord banks never classify as beaches (1.0.1). Settings record `density` (0–1), `variety` (0–1), `style` (`"natural"`), `rocky`; `density = 0` disables.
* **`IslandNoise.java`:** Pure hashed value/simplex/fBm helpers, seed-salted; no shared `Random`.
* **`IslandShapeConfig.java`:** Codec for the density-function config (fields/defaults in §6.1), builds the terrain.
* **`ShapeLayerSettings.java`:** Converts optional `entries` (legacy shape layers) into sampler settings.
* **`BiomeShare.java`:** Side-neutral catalog of 38 land variants in 18 climate families, optional `biome_share` settings codec (`weights`, `bias`, `achieved`, `target`, `feasible`), sanitization (path-only ids, unknown-id logging, weight 0–400, bias ±8, non-finite drop), and the deterministic weighted selector with `R_max = 0.62`, `exp(bias)` factors, a per-thread scratch buffer and a coherent weighted ticket (420-block fBm + 1024-bin quantile LUT).

### 4.3. Worldgen (`com.reystin.oneisle.worldgen`)
* **`IslandDensityFunction.java`:** Registered as `oneisle:island_continents`. `domainAxes() = AXIS_X | AXIS_Z`; `Sampler.sampleVolume` evaluates **once per XZ column** and calls `buffer.setRange(baseIdx, sizeY, value)`; `sampleValue` ignores Y. Holds a volatile `activeInstance` so the biome source and commands can reach the same terrain.
* **`SpawnPlateauDensity.java`:** Registered as `oneisle:spawn_plateau` for backward compatibility only. It wraps `final_density_base` in a quadratic dome (`radius` 80, `top_y` 78 → rim `top_y − 16`); new v3 packs never emit it (Phase 13), but already-generated worlds still reference the codec.
* **`OneBiomeSource.java`:** Registered as `oneisle:island_biomes`. v1 uses `climateAt`; v2 samples neighboring continentalness for slope (5 climate samples per lookup); v3 additionally samples signed distance and `ShoreField`. Resolves surface/shore/ocean/river/cave biomes, honoring enabled lists with non-empty fallbacks. An optional nested `biome_share` enables the weighted selector (§8.9); all-100/absent keeps the legacy path. The per-column `ColumnCache` stores the y-independent weighted ticket alongside terrain/shore values, so weighted lookups across Y levels do not resample the ticket.
* **`SpawnSavedData.java`:** Persistent flag + first-load spawn initialization/relocation.
* **`SingleStructurePlacement.java` / `FixedMultiStructurePlacement.java`:** 1-per-world and exact ordered start-chunk placements, both extending `RandomSpreadStructurePlacement` so 26.3 `/locate` sees them.
* **`RelaxedPyramids.java`:** v3 desert/jungle pyramid structure types that reuse vanilla pieces/tags but skip the hard `lowestY < seaLevel` rejection on low islands.
* **`RiverLife.java`:** Adds fish spawns to river biomes.

### 4.4. Commands & lifecycle (`com.reystin.oneisle.command`)
* **`OneCommands.java`:** `/oneisle info` (includes non-neutral weights and achieved shares when `biome_share` is active), `/oneisle verify`, `/oneisle debug <mode> [radius] [step]` (server-side PNG exporter; `biome` uses the real weighted resolver), `/oneisle import <preset|code>`.
* **`OneIslePreLaunch.java`:** On dedicated-server first boot, exports `config/oneisle.json` to `world/datapacks/oneisle-generated.zip` before packs are scanned.
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
    "type": "oneisle:island_continents",
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

### 6.2. `oneisle.json` (world preset)
`generator.type = minecraft:noise`, `settings = oneisle:one_terrain`, `biome_source.type = oneisle:island_biomes`. Generated keys: `biome_seed`, `climate_scale`, `generation_version`, `gradient_mode`, `gradient_strength`, `biome_theme`, enabled lists for land/shore/ocean/river/cave, and `shore` for v3. Missing enabled lists fall back to the full category list; empty categories fall back to a valid vanilla biome.

When at least one climate-driven land weight differs from 100, `biome_source` also contains:

```json
"biome_share": {
  "weights":  { "minecraft:plains": 300, "minecraft:forest": 50 },
  "bias":     { "plains": 0.4213, "forest": -0.6031, "...": 0.0 },
  "achieved": { "plains": 13.87, "forest": 2.10 },
  "target":   { "plains": 14.21, "forest": 2.05 },
  "feasible": true
}
```

`weights` are UI values (omitted = 100, 0 = disabled and also absent from the enabled list). Worldgen reads `bias`; `achieved`/`target`/`feasible` are diagnostics. A missing key, an all-100 map, or a neutral configuration leaves the legacy selector active and must remain bit-identical to 0.7.0. Keys may be written with or without the `minecraft:` prefix; sanitization stores path-only ids.

### 6.3. v3 extras (revised in place, Phases 13–14)
* `overworld/offset.json`: `minecraft:add` of `minecraft:mul`(the static vanilla-derived offset, `scale` spline) and the `lift` spline. Both splines use `coordinate = oneisle:overworld/continents` with `derivative = 0.0`; knots and rationale in §8.13.
* `overworld/final_density.json`: the static `final_density_base.json` with the vertical fade raised from 240→256 to 300→316 (values untouched).
* `overworld/preliminary_surface_level.json`: same fade rewrite to 300→316; `upper_bound = 320` is unchanged.
* `oneisle:spawn_plateau` is **not emitted any more**. The codec stays registered (and `final_density_base.json` stays in the classpath) so already-generated v3 packs still load; new packs never reference it.
* `data/minecraft/worldgen/structure/desert_pyramid.json` and `jungle_pyramid.json`: `oneisle:relaxed_desert_pyramid` / `oneisle:relaxed_jungle_temple`.
* Structure sets: single placements (ocean monument, woodland mansion, ancient city, stronghold set) and scaled random-spread sets for the rest; stronghold ring distance is `clamp(8, 32, round(32 · islandSize / 8000))`.
* `structure_set/villages.json` (1.0.1): five weight-1 entries (`village_plains`, `village_desert`, `village_savanna`, `village_snowy`, `village_taiga`), salt 10387312, spacing 54 / separation 12 scaled by `min(1, size/4000)` and `1/√clamp(structureDensity, 0.5, 2)`. Each variant has a family-scoped `has_structure/<tag>.json` with `replace:false`; variants without enabled biomes are omitted, and a fully unsupported village set is exported as disabled (`oneisle:fixed`). v1/v2 keep the 0.6.0 single plains entry at 34/8.

---

## 7. Hard Invariants (I1–I7)

1. **I1. Frozen versions:** v1 reproduces 0.5.0 bit-for-bit (`GoldenV1Test`). v2 reproduces 0.6.0 bit-for-bit (`GoldenV2Test`, snapshot generated from commit `7a5174d`). Any behavior change requires a new `generation_version` or a new optional field whose absence reproduces the old output. The optional `biome_share` field follows this rule: absent, all-100 and neutral configurations are bit-identical to 0.7.0. Golden comparisons are exact (`doubleToLongBits`), not tolerance-based. **v3 is not frozen:** Phase 13 revised its generated spawn/density resources in place without a `generation_version = 4` (Decision 070); already-generated v3 packs remain loadable but are not repaired. **Identity break (OneIsle 1.0.0):** the rename of every registry ID from `omniisle:*` to `oneisle:*` makes worlds created with OmniIsle 0.5.0–1.0.1 unloadable in OneIsle; there is no migration. The golden comparisons are unaffected because only identifiers, not terrain math, changed.
2. **I2. Platform & UI conventions:** non-empty biome fallbacks; texture released once via `getTextureManager().release(...)`; `InputConstants` mouse constants; UTF-8 files; the dev screenshot harness stays client-only and off unless its system property is set.
3. **I3. Server/preview parity:** `IslandDensityFunction`, `OneBiomeSource`, `BiomePreview` and commands all call the same side-neutral `com.reystin.oneisle.continent` code. Zone parity must be 100 %; family agreement ≥ 97 % (measured 100.00 % with non-default weights in Phase 10).
4. **I4. Determinism & thread safety:** samplers are pure functions of `(x, z, seed, config)`. No shared `Random`, no mutable static per-world state, no per-sample allocation in hot paths, no platform-dependent arithmetic.
5. **I5. Graceful failure:** corrupt/short mask data, unknown future `generation_version`, or missing optional JSON keys log and fall back; never crash world load. Unknown future versions are clamped to the newest known behavior.
6. **I6. Bilingual UI:** every user-facing string exists in `en_us.json` and `ru_ru.json`.
7. **I7. Platform & identity freeze:** Minecraft 26.3, Fabric Loader 0.19.3, Fabric API 0.165.0+26.3, Java 25 and Loom 1.17.19 are fixed. Public registry IDs (`oneisle:island_continents`, `oneisle:island_biomes`, `oneisle:spawn_plateau`, `oneisle:one_terrain`) and the world-preset ID `oneisle:oneisle` are never renamed. The legacy `omniisle:*` IDs are deliberately not registered (OneIsle 1.0.0 breaking change).

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

### 8.3. v3 shore field (`ShoreField`, revised 1.0.1)
* **Eligibility gates (in `outcome`, shared by all call sites):** signed distance `d ∈ [intertidalLimit, MAX_INLAND=28]` with `intertidalLimit(x,z,seed) = clamp(−6 − 8·fbm(x/64, z/64, salt+1601), −18, 0)`; height tier `≤ 1.10`; continentalness `≤ 0.20`; ring bayness `≥ 0.20`; and an actually open water body — `ring.minDistance ≤ −OPEN_WATER_MIN (55)`.
* **Ring scan:** one 8-direction scan at `BAY_RADIUS = 96` returns `Ring(bay, minDistance)`: `bay = smoothstep(−0.22, 0.38, (mean − d)/96)` and `minDistance` = the most negative SDF among the eight samples. A narrow fjord keeps every sample near its banks, so `minDistance` stays shallow and the bank is rejected; an open coast sees deep water on at least one side. Cost is the same eight SDF calls the old bayness alone used.
* **Patch mask:** coarse-only fBm (`1/380` and `1/145`, weights 0.45/0.55) mapped to `n01`; `threshold = 0.72 − 0.95·(0.88·density)`; pocket gate at `smoothstep(pocketCut−0.12, pocketCut+0.12, fbm(/95))` with `pocketCut = −0.10 − 0.90·density`; gap term `1 − (1−density)·(1 − smoothstep(−0.34, −0.21, gapNoise))`. `patch ≤ 0` → NONE.
* **Width:** `inlandWidth = (4 + 18·noise) · (0.42 + 0.58·patch) · factor · (1 − 0.35·notch)`, where `noise` is `1/170…1/110` fBm (`variety` shortens the scale), `factor ∈ [0.50, 1.0]` from a 190-block coarse fBm and `notch = smoothstep(0.62, 0.92, edgeNoise)`. Base max is 22 blocks, floor `MIN_WIDTH = 8` after the beach scale; `d > width − 2` → NONE; water-side cells need `patch ≥ 0.55`.
* **Beach/stony:** `beachWeight = patch · (0.26 + 0.74·smoothstep(−0.05, 0.30, bay))`; below `0.20` → NONE; the width is scaled by `(0.80 + 0.20·smoothstep(0.22, 0.40, beach))`. If `rocky`, `stonyChance = min(0.75, (0.48·headland + 0.30·steep) · (0.55 + 0.45·patch))` with `headland = 1 − smoothstep(0.25, 0.55, bay)` and `steep = smoothstep(0.22, 0.58, slope)`; a 150-block selector noise picks STONY below that chance, otherwise SAND.
* **Outcomes:** `0 = NONE`, `1 = SAND`, `2 = STONY`. SAND maps to `snowy_beach` if temperature `< 0.24`, else `beach`; STONY maps to `stony_shore`. Certain hot/rocky land families suppress the shore outcome.
* **Terrain coupling:** `ModernTerrainV2` flattens the shelf only where `d < 52 && heightTier ≤ 1.05` (`relief ×= 1 − 0.38·beachMask`, rocky cells `×= 1 + 0.18·rockyMask`), so the beach carve cannot cut into hills.
* **Measured (1.0.1):** Phase 9 acceptance 30 islands / 61,440 points — sand 11.56 %, stony 3.25 %, none 85.19 %, 0 run failures; Phase 11 component scan — 17–48 components per preset, median arc 36–70 blocks, 10 tiny (≤3) components of 144 (zero-speck target **PARTIAL**, distribution in `docs/evidence/phase11/after/shore_runs.csv`). Phase 14 — 0 interior/shallow/high-tier/inland-continentality violations over 15,067 coastal columns, 198/198 NONE on a synthetic 20-block fjord.
* **Fallback:** if all shore biomes are disabled or `density = 0`, v3 keeps the legacy ribbon (`stony_shore`/`snowy_beach`/`beach` by slope and temperature).

### 8.4. Ocean / bathymetry (v2/v3)
* **Soft floor:** `b = max(cBase, −0.80)`; total clamped to `[−1.0, 0.70]`.
* **v2 ramp:** `cBase = −0.08 − 0.92·(s/Wo)`.
* **v3 ramp:** `cBase = −0.08 − 0.54·smoothstep(s/(0.4·Wo)) − 0.18·smoothstep(s/Wo)`; reaches `−0.62` (deep threshold) at `0.4·Wo` and the floor by `Wo`, with zero derivative at the coast.
* **Seabed noise:** weight `w = smoothstep(s/(0.5·Wo))`; `fbmBed·0.18` plus ridged trench `−trench·0.12`. Positive seamounts are multiplied by `seamountFade`, which is 0 inside `[0.4·Wo, 1.0·Wo]` (guaranteed deep band for monuments) and fades in beyond `Wo`.
* **Satellite islets:** 800-block jittered cellular grid, probability `0.15 + 0.5·isletDensity`, radius 40–160; centers must lie beyond `1.0·Wo + 80` (v3) / 350 blocks (v2) from the main island; a bell profile rises to `−0.08` inside `0.55·r` and returns to seabed.

### 8.5. Climate & biome selection
* **Zones:** `c < −0.30` ocean, `−0.30 ≤ c < 0.02` shore, else land; underground (`y < 0`) cave biomes; river biomes when the river network or the erosion/weirdness valley rule fires.
* **v3 surface zones (0.8.2):** the zone decision uses the SDF, not only `c`. For `signedDistance ≤ 0`, OCEAN requires `d < V3_OFFSHORE_OCEAN_DISTANCE (−42)` or `c < V3_SEA_LEVEL_CONTINENTALNESS (−0.18)`; otherwise the column is LAND, or stony shore on steep/high columns when shore biomes are enabled. The `c < −0.18` test is never applied at `d > 0`, so underground lookups stay land. The measured dry-shelf waterline is `c ≈ −0.15…−0.18` / `d ≈ −33…−40` on the reference pack (`docs/evidence/phase82/waterline_probe.txt`).
* **v3 family tree (1.0.1, detail in §8.12):** `selectLandFamilyV3` applies elevation cooling `effTemp = temp − max(0, c − 0.28)·0.62` (plus `+0.14·warmStrength − 0.24·coldStrength` when province bias applies), then offshore mushrooms, a province-independent high-mountain rule (`tier ≥ 1.5 && c > 0.50`), foothills (`tier ≥ 1.25 && c > 0.30 && mountains > 0.42`), and the warm/cold bands selected by `diff = warmStrength − coldStrength` (`CORE 0.35`, `BAND 0.12`). Offshore mushrooms (family 16) require a satellite islet or outer-bay pocket and `fbm(x/650,z/650,seed+811) > 0.36`; never inside the main island. `V3_GRADIENT_SCALE = 0.55`, `V3_TEMPERATURE_NOISE_SCALE = 1.15`.
* **Scale & gradients:** `climate_scale` 384–1536 (default 720). Gradient amplitude is `0.5·themeSpan·clamp01(strength)`; theme spans ALL/STANDARD 0.90, MYSTIC 0.44, NORDIC 0.32, DESERT/TROPICAL 0.28. Modes OFF, NORTH_SOUTH, EAST_WEST, RADIAL.
* **Blending:** climate families blend with value-noise jitter (`±0.04` temperature, `±0.032` humidity for v2/v3), preventing straight borders.
* **Slope:** v2/v3 compute `slope = hypot((cE−cW)/8, (cS−cN)/8)·64` from neighboring climate samples; v3 uses it for stony shores.
* **Biome selection:** family lists are selected by temperature/humidity/mountain class; enabled-user lists are intersected with the category; empty categories fall back safely. Dynamic registry supplies modded/datapack biomes. With non-neutral `biome_share` weights the climate-driven land pick is replaced by the weighted selector in §8.9; terrain-driven families keep their legacy relief selection.

### 8.6. Structures & spawn
* **18 structure options**, all filtered to land/biome/depth conditions by the pack's structure sets. Strongholds: fixed 3-start ring scaled by island size. Ocean monuments: guaranteed deep band. Ancient cities: deep-dark conditions independent of surface peaks; v3 disables the city when `deep_dark` is not in the enabled palette.
* **Villages (1.0.1, v3):** five vanilla variants — plains {2 birch, 3 forest, 4 plains, 5 meadow, 6 cherry} minus grove, desert {12,13}, savanna {11}, snowy {0} minus ice spikes, taiga {1} — in one `structure_set/villages.json`, weight 1 each, salt 10387312, spacing **54** / separation **12** (was 34/8). Family-scoped `has_structure/<tag>.json` files are emitted with `replace:false`; variants with no enabled biome are omitted, and if none survive the set is exported disabled (`oneisle:fixed`). `(34/54)² = 0.396`, i.e. −60.4 % villages versus 1.0.0 at the same island size and density setting. v1/v2 keep the 0.6.0 single `village_plains` entry at 34/8.
* **Strict v3 fixed structures (0.8.2):** each fixed set has a policy over families, min/max continentalness, max slope and zone (desert `{12}` 0.00–0.32 `<0.22`; jungle `{10}` ≤0.36 `<0.28`; swamp `{8,9}` ≤0.16 `<0.10`; mansion `{7}` ≤0.36; igloo `{0,1}` ≤0.30; outpost/portal any land ≤0.40; trail `{1}`; camp `{3}`; mineshaft any land; monument/ruins/shipwreck/wreck ocean bands; buried treasure shore). The candidate must satisfy both the requested family and the actual point family; scoring prefers `preferC`, low slope and nearby spawn — never highest continentalness. If no candidate exists, the set is exported with `enabled=false` and no tag point. Tags are narrow (accepted families' enabled biomes + actual point biome); broad tags remain for random-spread villages/trial chambers, strongholds and ocean/deep-dark gates.
* **Runtime guard:** `RelaxedPyramids.allowsSurface(biomePath, topY, relief, c, slope, Policy)` samples the real biome source, checks `validBiome` and the allowed path, reads base height at center and ±8 blocks, rejects above `maxY`/8-block relief/slope and re-checks active OneIsle continentalness when available. Rejection returns `Optional.empty()`; there is no fallback structure.
* **Spawn selection:** largest positive SDF within 40 % of the island radius, written as `spawn` into the config. Since Phase 13, v3 does **not** place a density dome; on `SERVER_STARTED` `OneIsleMod.findModernSpawn` probes the marker column (solid non-fluid ground, air feet/head, no fluid, two blocks of headroom) and, if it is wet, scans rings 16–512 blocks (step 16, 32 angles) with the cheap `IslandTerrain.signedDistance` and `WORLD_SURFACE_WG` heights, validating promising candidates with the real column before accepting the first dry one. If no dry column exists (tiny/all-wet island) it logs a warning and falls back to the marker surface. `SpawnSavedData` reconciliation and the v1/v2 SDF spiral search are unchanged. Spawn may therefore differ from the editor marker; the old 45/45 marker-match evidence is stale.
* **World border:** optional, centered on the island with a 500–4000-block margin.

### 8.7. Editor, share codes, PNG
* Tools: brush (1–24 px), fill, line (Bresenham), rectangle, ellipse (Shift square/circle, fill/outline), smooth. Symmetry: off, mirror-X, mirror-Z, 4-way, 6-way, 8-way (forward-orbit stamping, exact closure). Undo/redo covers land, height, spawn, resolution and every tool.
* 13 presets: Natural, Round, Long, Crescent, Archipelago, Atoll, Volcano, Fjords, Twin Islands, Inland Sea, Horseshoe Bay, Ridge Island, and the fixed Mineshield 4 Map (see §8.11).
* Share codes deflate JSON payload; CRC16 verified; 256 KiB cap. PNG import supports mask threshold/invert and height quantile mode; export writes mask, height, biome, hillshade and shore maps.
* **Flat editor UI (1.0.0, presentation only):** the editor renders through the custom `client/ui` widgets and `EditorLayout` density tiers — FULL (right biome/structure list, labelled tools, 2×3 tabs), MEDIUM (icon tools, list via `BiomeSelectionScreen`), COMPACT (icon tools, scrolling controls), MINI (16-px tools, six icon tabs, abbreviated status) and MINIMAL (actual-size notice with Cancel/Done only). Canvas preview modes Land/Height/Biomes/Shore are palettes produced inside the existing throttled refresh and never affect generation. Status is always colour + glyph; weights show the measured share with ✓/!/✕, and `—` only for disabled rows or no land samples. Keyboard: Tab/Shift+Tab fixed order, Enter/Space, arrows/Home/End, Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z, layered Esc; no new letter shortcuts. Tooltips (400 ms), toasts, preset dropdown and the Clear confirmation are custom; the Create World button injected by `WorldTypeEditorMixin` stays vanilla. 1.0.0 captures (200 PNGs, en/ru, window × GUI scale 1–4) were taken under the OmniIsle name and remain local-only artifacts in `docs/screens/1.0.0/after/` (not committed to this repository); MEDIUM is not produced by that matrix and MINIMAL is unreachable because Minecraft clamps the logical area to ≥ 320×240, so both are unit-test-only.

### 8.8. Dedicated server & commands
* First boot without a world: `preLaunch` converts `config/oneisle.json` into `world/datapacks/oneisle-generated.zip` before `WorldLoader` scans packs. `/oneisle import` can replace it later.
* `/oneisle info` reports version, resolution, island size, bounding box, spawn, border and active layers; when weights are active it also prints non-neutral weights and achieved shares. `/oneisle verify` probes surface/biomes; `/oneisle debug` writes server-side PNG maps (`biome` uses the real weighted resolver).

### 8.9. Biome frequency weights (0.8.0, coherent ticket in 0.8.1)
* **Catalog:** 38 land variants in 18 climate families (`BiomeShare.VARIANTS`); 31 are climate-driven, families 14/15/16 (`stony_peaks`, `windswept_hills`, `windswept_gravelly_hills`, `jagged_peaks`, `frozen_peaks`, `snowy_slopes`, `mushroom_fields`) are terrain-driven and never weighted. Shore/ocean/river/cave rows are checkbox-only.
* **Weights:** integer 0–400, default 100, step 5 (values clamp to the nearest step; 0 removes the candidate and unticked it in the draft). Other biomes absorb the difference because probabilities are normalized.
* **Distances:** `d_g = sqrt(((T − T_g)/0.60)² + ((H − H_g)/0.60)²)` from the 18 family climate centres; `d_pref` is the legacy preferred family's distance.
* **Probabilities:** preferred family prior 1, other families `exp(−max(0, d_g − d_pref)/0.22)`; variant probability `prior · exp(bias)`. Candidates with `d_g > R_max = 0.62` are dropped while any candidate is inside the cap; if all are outside, the cap is relaxed. The cumulative distribution is sampled by a **coherent, family-independent ticket**: a 3-octave fBm at 420-block scale salted by `biomeSeed`, flattened through a static 1024-bin empirical CDF built from 65,536 deterministic samples (`TICKET_LUT_SEED`). `ticketFromRaw` is a piecewise-linear lookup with no allocation; `OneBiomeSource.ColumnCache` memoizes the y-independent ticket per column. The legacy fbm ticket is used only by the exact legacy short-circuit. Per-thread `SelectorScratch` means no allocation per lookup. This replaced the 0.8.0 per-block SplitMix ticket, which made every weighted world noisy.
* **I1 short-circuit:** if all candidate factors are equal (all biases zero), `legacyEquivalent` reproduces the legacy nearest-family filter and patch picker exactly. Neutral weights skip the weighted arrays entirely.
* **Targets and calibration:** measured on a fixed 64×64 mask-land grid, excluding the v3 shore band (`signedDistance ≤ 34`), rivers and terrain-driven families. Baseline share `s_f` is measured with zero bias (the legacy distribution). Target `t_f = s_f·w_f/100 / Σ_g(s_g·w_g/100)`. Bias starts at `ln t_f − ln max(s_f, 0.5 %)`; each of ≤24 iterations adds `0.7·(ln t_f − ln max(actual_f, 0.5 %))`, subtracts the mean and clamps to ±8; it stops at max error ≤1 pp. Feasible iff max error ≤3 pp and every weighted biome is reachable; unreachable targets are reported, not forced. The same `BiomeShareCalibrator` runs in the editor and in `WorldPackBuilder.export`.

### 8.10. v3 distances, per-column cache and new families (0.8.2)
* **Distances:** `IslandTerrain.Distances(double signed, double main)`; the default implementation returns `(signed, signed)`. `ModernTerrainV2.distances(x,z)` computes the main-island SDF once (`computeSignedDistance(..., true)`), merges the islet distance once, and returns both; `signedDistance` delegates to `distances().signed()`. `WorldPackBuilder` uses `main` for structure gates; `BiomePreview.selectionV3`/`surfaceV3` and `OneBiomeSource` pass both distances to `sampleV3(..., signedDistance, mainIslandDistance, shoreAllowed)`.
* **Column cache:** the v3 `SampleResult` is y-independent (surface zone/family/temperature/humidity/mountains/subtype), so `OneBiomeSource.ColumnCache` computes it once per column and reuses it for every Y quart. The weighted ticket is cached alongside; the hot path is allocation-free.
* **New families:** `dappled_forest` is family 3 (mid-temperate forest transitions); `pale_garden` is family 17 (rare mid-temperate inland pockets, ~0.02 % of reference land). v3 warm shallow caves (`y ∈ [-32,0)`, `temperature > 0.68`) route to `sulfur_caves` before the deep-dark noise test; v2 is untouched.
* **Default size:** `IslandDraft.DEFAULT_ISLAND_SIZE = 3500` (was 4000). Share codes and saved presets retain their stored size.

### 8.11. Mineshield 4 Map preset (0.8.3)
* **Source:** `assets/oneisle/presets/mineshield_4_map.jpg` (the bundled 1024×764 reference map) is letterboxed to the draft resolution by `Mineshield4Map`, with a one-time decode and per-resolution cache (`boolean[] land`, `byte[] height`).
* **Mask:** water is classified by hue 150–265°, saturation > 0.28, brightness > 0.08 and blue dominance (`b > r + 8`, `b ≥ 0.92g`); letterbox margins are water. Border pixels are forced to ocean.
* **Relief:** cool white pixels (value > 0.85, saturation < 0.12, hue 160–260°) → tier 3; warm brown/orange (hue ≤ 60°, saturation ≥ 0.30) → tier 2; all other land → tier 1. Tier 0 is not used, so the fixed coastline cannot be lowered below sea level by the height layer.
* **Determinism:** `rebuildShape()` returns immediately for `MINESHIELD_4_MAP`; the shape seed, rotation and procedural fBm are ignored. The mask is identical at 192/256/384.
* **Reference stats (192):** 11,232 land pixels / 30.47 %, 0 border land, largest 4-connected component 10,228 (91.06 %), tiers 3/2/1 = 740 / 1,509 / 8,983. The preset controls the land mask and relief only; biomes remain climate-driven. Evidence: `docs/evidence/presets/mineshield_4_mask.png`, `mineshield_4_stats.csv`.

### 8.12. Universal climate provinces, terrain APIs, strict sectors (0.8.4)
* **`ClimateLayout`** (`continent` package): built lazily for v3 from the mask bounds and biome seed, cached on `ModernTerrainV2`. Normalized coordinates `nx, nz ∈ [-1, 1]`. Warm = top 16 % of land by `-nx + 0.20·(-nz) + 0.10·noise(x/900, z/900, seed+1301)`; cold = top 12 % of total land (warm excluded) by `nx + 0.35·(-nz) + 0.10·noise(/900, seed+1307)`; the rest is temperate. Binary masks give `Province`; two 5-tap `[1,4,6,4,1]/16` passes give continuous `strength` (core ≥ 0.65). Sampling is **continuous, not nearest-cell**: the smoothed strength rasters are interpolated bilinearly, a 160-block `boundaryNoise` (salt +1621) is added to the warm/cold edges, and membership is compared against quantile-calibrated thresholds (`warmThreshold` = smoothed top 16 % of land, `coldThreshold` = smoothed top 12 % of total land excluding warm), so province contours are organic curves instead of 18–20-block stairs. Pale pocket = top 3.0 % of temperate land by `noise(/420, seed+1601)`; ice pocket = top 0.6 % of cold land by `noise(/260, seed+1607)`; cherry support = top 36 % by `noise(/520, seed+1613)`. Water within 10 blocks of land inherits the nearest province; empty/all-water masks return `temperate()`. Allocation-free after construction.
* **Terrain APIs:** `IslandTerrain` defaults `heightTierAt` → 1.0, `surfaceHeight` → `continentalnessToElevation(continentalness)`, `climateLayout` → null. `ModernTerrainV2` returns the 7-tap-smoothed tier and relief height; v3 labels 4-connected mask components and builds a second EDT/SDF for the largest one, so `Distances.main` is the main-island SDF (v2 keeps `main = signed`). `ModernTerrainV2.physicalRidge(x,z,seed) = 1 − 2·|IslandNoise.fbm2(x/750, z/750, seed+801, 3, 2.0, 0.5)|` is shared with `IslandClimate`.
* **Family tree (`selectLandFamilyV3`, 1.0.1), fixed order:** (1) mushroom 16 only when `main < 0`, `c < 0.05`, and `fbm(/650, seed+811) > 0.36`; (2) `effTemp = clamp01(rawTemp − max(0, c−0.28)·0.62)`, nudged by `+0.14·warmStrength − 0.24·coldStrength` when province bias applies; `diff = warmStrength − coldStrength` (`V3_CLIMATE_CORE_DIFF = 0.35`, `V3_CLIMATE_BAND_DIFF = 0.12`); (3) **mountains, province-independent:** `tier ≥ 1.5 && c > 0.50` → 15 (`effTemp < 0.50`), 14 (`effTemp > 0.72`), otherwise 5 — snow peaks are allowed next to a desert; (4) foothills `tier ≥ 1.25 && c > 0.30 && mountains > 0.42`: warm core (`diff ≥ 0.35`) → 13/11 by humidity, else `effTemp < 0.35` → 1, else 5; (5) cold core `diff ≤ −0.35` → 0 in the ice pocket, else 0/1 by humidity (`ice_spikes` hint); (6) cold band `diff ≤ −0.12` → 5 (`hum ≥ 0.55`) or 2; (7) cherry 6 when `|diff| < 0.12`, `0.34 < c ≤ 0.48`, `hum > 0.58`, cherry mask > 0.45; (8) wetlands 8/9 only `c < 0.15 && slope < 0.12 && tier ≤ 1.0 && mountains < 0.35 && hum > 0.64` (mangrove only in the warm band with `hum > 0.72 && effTemp > 0.60`, swamp otherwise); (9) warm core `diff ≥ 0.35`: desert 12 at `c ≤ 0.32` with `hum < 0.42`, badlands 13 / savanna 11 by humidity (`c ≤ 0.40` for 13), jungle 10 when humid; (10) warm band `diff ≥ 0.12`: meadow 5 / plains 4 / savanna 11 by humidity; (11) pale 17 only when `paleStrength > 0.50 && hum + 0.65·paleStrength > 0.88 && effTemp ∈ [0.33, 0.82]`; (12) temperate meadow/birch/dappled/plains/dark-forest by effTemp and humidity. The categorical province no longer selects families: it drives ocean subtypes, shore outcomes, the ice-spike hint and strict structure sectors only. `SampleResult` returns the chosen family's `BiomeShare` centroid temperature/humidity as effective climate (raw climate stays available for ocean subtypes); `landHint` carries `ice_spikes` and is ignored when the family has a non-neutral explicit weight.
* **Oceans and shores:** `oceanSubType` receives the province: WARM → `warm`/`lukewarm_ocean`, COLD → `cold`/`frozen_ocean` (deep variants where appropriate), TEMPERATE → legacy. Rim transition bands avoid a hard categorical switch: WARM strength < 0.55 selects lukewarm water (warm only in the core), and COLD strength < 0.55 selects cold water (frozen only at strength ≥ 0.55 and temperature < 0.34). Shore outcomes inherit the province (`beach`, `snowy_beach`, `stony_shore`). Dry shelf unchanged: never OCEAN at `signedDistance ≤ 0` with `c ≥ -0.18`; suppressed beach → LAND.
* **Shore coherence (1.0.1):** `ShoreField` uses only coarse fields and the ring open-water gate. Measured 8-connected sand runs across five presets: 17–48 components, median arc 36–70 blocks, P75 58–124, in-range share 0.47–0.55; 10 components ≤ 3 blocks remain of 144 (zero-speck target **PARTIAL**, distribution in `docs/evidence/phase11/after/shore_runs.csv`). Phase 9 acceptance: sand 11.56 %, stony 3.25 %, none 85.19 % over 30 islands / 61,440 points with 0 run failures.
* **Strict structures:** `WorldPackBuilder.Sample` adds `family`, `province`, `topY` (= `surfaceHeight`), `slope`; `StructurePolicy` adds `province` and `maxSurfaceY`. v3 candidates must match family + province + c + slope + topY; score = preferred-c closeness + low slope + small spawn penalty, never highest c. No candidate → `enabled=false`, no point, no exception. Tags list only enabled accepted-family biome IDs plus the point biome. `RelaxedPyramids.allowsSurface` re-checks the center/±8 surface, biome, relief, c and slope at runtime.
* **Mineshield cleanup:** 3×3 median + component reassignment; one central-north tier-3 range (612 cells at 192) and the western tier-2 mesa band (3 components, all west of x=0.26); detached specks → tier 1. Tiers 1/2/3 = 9,189 / 1,431 / 612; land 11,232 unchanged.
* **Editor:** full-width resolution button cycles 192→256→384 and renders `Resolution: 192×192` / `Разрешение: 192×192` (two-argument key, `×`); `EditorLayout.Rect` and `placeTooltip` keep controls non-overlapping and the tooltip inside the preview; biome rows show the preview-measured share even with all-100 weights (`—` only for disabled rows or no land samples).

### 8.13. v3 highland offset lift and raised fade (Phase 13)
* **Why:** the editor preview (`continentalnessToElevation`) reaches 279 at c = 0.95, but the real surface follows vanilla's `offset.json` spline keyed mostly on erosion/ridges: linearised medians are Y ≈ 81 at both c = 0.45 and c = 0.95 (p90 Y ≈ 169, max Y ≈ 253 at c = 0.95). High continentalness alone cannot raise terrain. The 240→256 fade then clipped the rare high offsets.
* **Generated `offset.json`:** `offset = vanilla_offset · scale(c) + lift(c)`, evaluated per 4×8 cell; `Y ≈ (1.5 + offset)·128 − 64`. Spline knots on `oneisle:overworld/continents`, sentinel points at c = −1.10 and 1.10, all derivatives 0:

| c | −1.10 | 0.45 | 0.55 | 0.65 | 0.75 | 0.85 | 0.90 | 0.95 | 1.10 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| lift | 0.00 | 0.00 | 0.15 | 0.40 | 0.60 | 0.80 | 0.90 | 1.00 | 1.00 |
| scale | 1.00 | 1.00 | 0.85 | 0.65 | 0.45 | 0.30 | 0.22 | 0.20 | 0.20 |

* **Fade:** v3 `final_density.json` and `preliminary_surface_level.json` raise the `minecraft:gradient` fade from 240→256 to 300→316 by rewriting the static templates at export; every other value is copied byte-for-byte. `upper_bound` in the preliminary surface level already clamps at 320.
* **Unchanged on purpose:** the preview curve, the mask/SDF pipeline, structures' `topY` (`terrain.surfaceHeight`), v1/v2 resources, the static `offset.json`/`final_density.json`/`final_density_base.json`/`preliminary_surface_level.json`, and the `oneisle:spawn_plateau` codec.
* **Measured (v3, ROUND 4000, seed 4242, tier-3 paint at spawn):** min 139 / median 228 / p90 235 / max 289, 1088 of 1681 sampled columns ≥ Y 220 (`Phase13HighlandWorldgenTest`, `FINDINGS.md` §20.3).

---

## 9. Verification & Evidence (0.8.2–1.0.1, Phases 13–14)
* **Golden v1:** 30 preset/relief/size configs × 10 000 continentalness points + 200×200 biomes + deep-dark probes — exact.
* **Golden v2:** 30 configs × 1 500 points × 4 metrics (`continentalness`, `computeSignedDistance(false)`, `signedDistance`, `erosionBias`) — bit-exact against 0.6.0 (`docs/evidence` / `src/test/resources/golden/v2`).
* **Parity:** `BiomePreview` vs `OneBiomeSource` zone parity 100 %, family agreement > 99.9 % on sampled grids (see `docs/evidence/`).
* **Coast fidelity (v3):** worst mean 0.2955 px and max 1.5778 px at 4000; 0.2462 / 1.3579 px at 2500 (budgets 0.6 / 2.0).
* **Shore acceptance:** fractions, longest straight border, density sweep monotonicity — `docs/evidence/shore/`.
* **Relief:** 93–107 local maxima per seed, max height 163.2, longest plateau component 96 blocks, tier-3 boost +122.4.
* **v3 highlands and spawn dome (Phase 13):** `Phase13HighlandWorldgenTest` exports ROUND 4000 seed 4242 with a tier-3 paint, asserts no `spawn_plateau`/`top_y`, the offset add/mul wrapper, the 300→316 fades (and no 240→256 gradient) in generated `final_density.json`/`preliminary_surface_level.json`, decodes through real worldgen registries and samples `getBaseHeight`: min 139 / median 228 / p90 235 / max 289, 1088/1681 columns ≥ Y 220, max ≤ 316. A v2 export keeps no v3 density overrides. Test-harness pack order was corrected to `vanilla, mod, generated` (last = highest priority); `Phase10PresetDecodeTest` uses the same order. Interactive checks (dome gone at spawn, F3 heights, aquifers/caves, preview match) are **NOT DONE** and not claimed.
* **Structures/spawn:** full 20-structure matrix only 15/45 configs (PARTIAL); stronghold-only real `/locate` 45/45 on land.
* **Reference climate (0.8.2, v3):** measured waterline `c ≈ −0.15…−0.18` / `d ≈ −33…−40`; dry-shelf ocean 0 and dry-shelf land 1,844 on the reference map; core temperate 68.05 %, green 79.05 %, peak columns 5,015, warm/arid 607 (max c 0.2871, component 97.36 %), 0 swamp violations, 0 inland mushrooms / 202 offshore, ridge/mountain correlation 0.9808, preview/source parity 99.02 %. `Phase82ReferenceClimateTest` (7 tests) + `Phase82StructureTest` (3 tests) pass. Evidence: `docs/evidence/phase82/`.
* **Strict structures (0.8.2):** 0.8.1 placed the desert pyramid at `c=0.6148` in CHERRY_GROVE and emitted broad fallback tags; 0.8.2 disables absent families with no fallback and emits narrow tags. Live server: swamp hut at 65,-82 (`c=−0.0923`, swamp, y82–88); `/locate` found it at 1040,-1312, desert/jungle/igloo returned nothing. Reference and weighted servers reached `Done` (1.559 s / 1.417 s) with no crash reports.
* **Real chunk-gen:** dedicated server, fresh world, 25×25 chunks, 3 reps per version; numbers in `docs/evidence/perf/chunkgen_25x25.csv` and the final Phase 9 report. v2/v3 exceed the +15 % budget on the measured offshore area; see the report for the inland comparison and known issues.
* **Editor (1.0.0):** 13 presets × 20 seeds pass; PNG export 5/5; texture lifecycle 50/50. Full build **181 tests, 0 failures, 2 assumption-gated skips** (150 baseline + 31 UI/layout). Real-client captures (OmniIsle-era, local-only, not committed): **200 PNGs** in `docs/screens/1.0.0/after/` (6 tabs × 16 window/scale combinations × en_us/ru_ru plus 4 preview modes per language), 200-row `manifest.csv`, validated 200/200 with exact dimensions; before evidence `docs/screens/1.0.0/before/editor_0.8.4_old_ui.png` (local-only). Tiers reached: FULL, COMPACT, MINI; MEDIUM is not produced by the matrix and MINIMAL is unreachable (Minecraft clamps the logical area to ≥ 320×240), so both are unit-test-only. Render cost at 960×540 GUI scale 2: median 1.219–2.007 ms, p95 2.214–3.702 ms; 11/12 medians ≤ 2 ms (en_us Climate 2.007 ms) — target **PARTIAL**. See `UI_CHECKLIST.md` and `FINDINGS.md` §19.6. The 1.0.0 UI captures are not part of the OneIsle repository; 25×25 chunk-generation timing remains UNVERIFIED and the Phase 9 structure matrix remains PARTIAL 15/45.
* **Mineshield 4 Map (0.8.3):** `Mineshield4MapPresetTest` (5 tests) asserts the fixed mask contract (30.47 % land, 0 border, 91.06 % largest component), seed independence, tiers 3/2/1, and share-code round-trip. Evidence: `docs/evidence/presets/`.
* **Universal provinces (0.8.4, v3; composition refreshed 1.0.1):** `Phase11PresetClimateTest` on Mineshield 4 / Natural / Round / Archipelago / Crescent at 256² seed 2025: warm 14.51–17.52 %, cold 13.86–15.46 %, green 66.59–68.90 %, cherry 1.12–2.67 %, mushroom 0.12–1.02 % (0 on the main island), pale 0.93–1.42 %; all zero-counters (wetlands at `c > 0.18`, snow below tier 1.4 outside cold, main-island mushrooms, dry-shelf ocean) are 0 and `all_targets_met=true`. Province membership is sampled bilinearly from the smoothed strengths against quantile-calibrated thresholds with a 160-block boundary-noise contour; `Phase11ClimateLayoutTest` 11L.6 checks continuous WARM crossings (fractional strength, blended edge) and 11L.7 checks the lukewarm/warm/cold/frozen ocean transition bands, with `docs/evidence/phase11/after/ocean_edge_natural.png` as visual evidence. The test also covers disjoint/deterministic masks, empty-mask safety, pocket rarity and strength continuity; `Phase11BiomeProvinceTest` covers every family-tree gate; `Phase11StructureTest` covers province/maxSurfaceY policies, disabled sets, narrow tags and `RelaxedPyramids.allowsSurface`; `Phase11ShoreTest` records 25–90-block sand arcs (1.0.1 recalibration: 10 tiny components of 144; zero-speck **PARTIAL**); `Phase11EditorLayoutTest` covers the resolution label, 13 window sizes at GUI scales 1–4, non-overlapping controls and tooltip containment. `Phase11EvidenceTest` writes `docs/evidence/phase11/after/` (province map, five overviews and close-ups, ocean edge, composition CSVs, shore runs, structure sites, tier map, summary). Full build **150 tests, 0 failures, 2 assumption-gated skips**; v2 hash `-4990092939012319655` unchanged, v3 hash `-6312501814918578635` (0.8.3: `3958321154656118617`).
* **1.0.1 shore, villages, climate bands (Phase 14):** `Phase14ShoreInteriorTest` (3 tests) — 0 `beyondBand`/`shallow`/`highTier`/`inlandC` violations over 2,597 NATURAL / 5,198 FJORDS / 7,272 ARCHIPELAGO coastal columns, a synthetic 20-block channel returns 198/198 NONE with minBay 1.0, open coast 76/201 sand, NATURAL coast trace 302 beach points. `Phase14VillagePackTest` (4 tests) — five entries at spacing 54 / separation 12 / salt 10387312 with weight 1 and exact ids, family-scoped tags excluding grove/ice_spikes, empty variants omitted, desert-only export leaves one entry, all-unsupported export disabled, v2 keeps the single plains 34/8 placement, `(34/54)²` density ratio asserted. `Phase14ClimateTransitionTest` (3 tests) — 0 adjacent warm-core↔cold-core pairs and 0 unbanded transects (MINESHIELD 39 switches, NATURAL 100), intermediate bands {2,4,5} present, synthetic high-tier family 15 chosen in a WARM province. Recalibrated: `Phase9ShoreTest` (sand 11.56 %, stony 3.25 %, none 85.19 %, 0 run failures), `Phase11ShoreTest` (median 36–70, 10/144 tiny), `Phase11BiomeProvinceTest`, `Phase11ClimateLayoutTest`. Full build **193 tests, 0 failures, 2 assumption-gated skips**; `GoldenV1Test`/`GoldenV2Test` and both biome-source hashes (`-4990092939012319655`, `-6312501814918578635`) unchanged. Evidence: `docs/evidence/phase14/` (captured `PHASE14` stdout + test run log); interactive checks (in-game beaches, village inspection, warm→cold walk) are **NOT DONE** and not claimed. Existing v3 worlds are **not** repaired — a new world is required. Phase 9 remains **PARTIAL 15/45**; 25×25 chunk-generation timing remains **UNVERIFIED**.
* **Biome weights (0.8.0→0.8.1):** I1 hash `2951624326361821806` for absent and explicit all-100; 45/45 calibration configs feasible, max error 0.99 pp (`docs/evidence/phase10/calibration.csv`); monotonic cherry_grove 0/50/100/200/400 → 0.00/20.13/32.93/48.98/66.10 %; zero weight ≡ disabled; 8-thread determinism; 20,000 extreme samples, 9 beyond R_max all without a closer alternative; preview/real parity 9836/9836 = 100.00 %; codec/share/preset/undo/bad data pass. **Coherent ticket:** adjacency max step 0.0101, mean 0.00145 over 131,072 samples, quantile LUT monotone. **Cherry 45 regression:** target 17.32 %, held-out 17.82 % (0.50 pp); a representative 192×128 one-block window has 99.4 % neighbour agreement / 0.0 % isolated pixels vs 21.8 % / 43.9 % for the 0.8.0 white ticket (`docs/evidence/phase10/cherry45_before_after.png`). **Preset safety:** `Phase10PresetDecodeTest` decodes the exported zip through the real worldgen registries; a corrupt zip is rejected by preflight. Lookup benchmark result is recorded by test 10.9; real 25×25 chunk-generation timing with weights and GUI 2/4 screenshots remain **UNVERIFIED / NOT DONE**.
