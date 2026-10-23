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
1. An **in-game real-time GUI editor** embedded seamlessly into the vanilla "Create New World" screen.
2. An algorithmic bridge that translates pixel-drawn masks into continuous **Signed Distance Fields (SDF)** for Minecraft's native 3D noise router (density functions).
3. A climate-driven custom **BiomeSource** (`omniisle:island_biomes`) supporting all 56 Overworld biomes and 18 structures, including the newest Minecraft 26.3 additions (**Dappled Forest**, **Sulfur Caves**, and **Abandoned Camp**).
4. Automatic generation and injection of a lightweight, self-contained worldgen datapack (`omniisle-generated.zip`) upon world creation.

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
            ▼ (Mixin: WorldTypeEditorMixin)
[OmniIsleEditorScreen] <──────> [IslandDraft (192x192 bitmask + parameters)]
            │                               │
            │ (User paints / configures)     ▼
            │                         [BiomePreview] (Fast Client Noise Sim)
            ▼
[Click "Done" (Commit)]
            │
            ▼ (WorldPackBuilder.export())
[Pack: omnisle-generated.zip]
   ├── pack.mcmeta (Format 121)
   ├── data/omniisle/worldgen/density_function/overworld/continents.json (IslandMask SDF)
   ├── data/omniisle/worldgen/world_preset/omniisle.json (OmniBiomeSource)
   └── data/omniisle/worldgen/structure_set/*.json (Targeted Structure Placements)
            │
            ▼ (Mixin: CreateWorldBridge injects pack into PackRepository)
[World Generation / Server Startup]
   ├── IslandDensityFunction -> LandmassSampler -> IslandMask (SDF continuous noise)
   └── OmniBiomeSource (Climatic multi-noise biome distribution)
```

---

## 4. Codebase Architecture & Class Roles

### 4.1. Core Client / GUI Layer (`com.hack337.omniisle.client`)

#### `IslandDraft.java`
* **Purpose:** The in-memory data model representing the island draft before it is compiled into a Minecraft datapack.
* **Resolution:** `RESOLUTION = 192` (representing a 192x192 raster grid).
* **World Span:** `islandSize * 1.36` (block span in the actual Minecraft world, default `4000`, range `2500` to `8000`).
* **Key Fields:**
  * `boolean[] land`: 1D boolean array representing `192 * 192` pixels (true = land, false = water).
  * `long shapeSeed`, `long biomeSeed`: Random seeds for procedural shape and climate noises.
  * `Preset preset`: Procedural shape generator (`NATURAL`, `ROUND`, `LONG`, `CRESCENT`, `ARCHIPELAGO`).
  * `Relief relief`: Coastline elevation gradient (`BALANCED` [coast width 192.0], `FLAT` [320.0], `MOUNTAINOUS` [110.0]).
  * `BiomeTheme theme`: Quick-selection biome presets (`ALL`, `STANDARD`, `TROPICAL`, `NORDIC`, `DESERT`, `MYSTIC`).
  * `EnumSet<BiomePreview.Type> enabledBiomes`: Set of user-toggled biomes.
  * `EnumSet<StructureOption> enabledStructures`: Set of user-toggled structures.
  * `ArrayDeque<boolean[]> undoStack`, `redoStack`: Circular undo/redo history buffer (max 32 steps).
* **Key Algorithms:**
  * `rebuildShape()`: Fractional Brownian Motion (fBM) noise combined with trigonometric distortion curves to synthesize natural coastlines and atolls.
  * `smooth(centerX, centerZ, radius)`: Cellular majority-voting smoothing kernel over 8-connected neighborhoods to eliminate harsh pixel stairs.
  * `toggleBiome(type)`: Removes restrictive legacy checks (`remainingInCategory == 1`), allowing any biome to be toggled freely.
  * `compatibleChoices(preferred)`: Resilient biome fallback selector that safely handles empty user categories without `IndexOutOfBoundsException`.
  * `packedMask()`: Encodes `boolean[] land` into a Base64 bit-packed byte string (`(192*192)/8 = 4608 bytes`) for transmission into JSON.

#### `OmniIsleEditorScreen.java`
* **Purpose:** The primary interactive full-screen GUI.
* **Layout Geometry:**
  * Left: 192x192 preview viewport scaled dynamically to screen dimensions.
  * Middle: Controls panel (Preset, Island Size Slider, Tool selector [Land/Eraser/Smooth], Brush Slider, Relief, Theme, Shape/Biomes/Clear action buttons).
  * Right: Biome & Structure checklist panel with real-time category filtering (All, Land, Shore, Ocean, Rivers, Caves) and `+ All` / `- None` batch controls.
* **Rendering & Textures:**
  * Generates an in-memory `DynamicTexture` uploaded directly to the GPU via `NativeImage`.
  * Upload throttling: Max 60 FPS update interval (`now - lastPreviewUpload >= 16ms`) to eliminate frame stutter during painting.
* **Interactive Tooltip (Inspector):**
  * Calculates inverse-projection from screen coordinates `(mouseX, mouseY)` to draft pixel `(px, pz)` and Minecraft world coordinate `(worldX, worldZ)`.
  * Evaluates local terrain continentalness and queries `selectBiome()` to render a live HUD tooltip showing hovered biome name, color swatch, coordinates, and Land/Water status.
* **Shortcuts & Input:**
  * `Ctrl+Z`: Undo.
  * `Ctrl+Y` or `Ctrl+Shift+Z`: Redo.
  * Right Click: Eraser shortcut regardless of selected active tool.
  * Mouse Wheel: Smooth list scrolling and slider adjustment.

#### `BiomeSelectionScreen.java`
* **Purpose:** Compact modal dialog used when GUI scale or low display resolution (`width < 640 || height < 320`) leaves insufficient horizontal room for the 3-column editor layout.
* **Features:** Full category filtering, batch toggle buttons, scrollbar, and live sync with `IslandDraft`.

#### `BiomePreview.java`
* **Purpose:** High-speed client-side climate evaluator. Mimics `OmniBiomeSource` logic without loading server world registries or chunk generation pipelines.
* **Biome Categories:** `LAND`, `SHORE`, `OCEAN`, `RIVER`, `CAVE`.
* **Biome Family Mapping:** 18 distinct land families (0 to 17), ensuring variants (e.g. Flower Forest, Dappled Forest, Birch Forest) cluster naturally into compatible climatic zones.

#### `WorldPackBuilder.java`
* **Purpose:** Datapack compiler. Builds `omniisle-generated.zip` in UTF-8 encoding.
* **Outputs:**
  1. `pack.mcmeta`: Formatted with target `pack_format = 121` (Minecraft 26.3 standard).
  2. `continents.json`: JSON payload containing the Base64 mask, bounding boxes (`min_x, max_x, min_z, max_z`), and `coast_width` determined by the selected `Relief`.
  3. `omniisle.json`: Customized world preset with `enabled_land_biomes`, `enabled_shore_biomes`, `enabled_ocean_biomes`, `enabled_river_biomes`, `enabled_cave_biomes`.
  4. `structure_set/*.json`: Procedural structure placement constraints mapped to suitable land coordinates.

---

### 4.2. World Generation & Math Layer (`com.hack337.omniisle.worldgen` & `continent`)

#### `IslandMask.java` (Signed Distance Field)
* **Mathematical Core:**
  Converts the discrete 192x192 binary raster mask into a smooth, continuous 2D Euclidean Distance Transform (Signed Distance Field).
* **Algorithm:**
  * Runs a two-pass 8-neighbor distance transform algorithm (`O(N)` linear time) initializing inside land with distance to closest water, and outside land with negative distance to closest coast.
  * Bilinearly interpolates distances across sub-pixel coordinates `(worldX, worldZ)`.
  * Computes final continentalness:
    $$\text{continentalness} = -0.08 + \frac{\text{worldDistance} \times 0.70}{\text{coastWidth}} \quad (\text{for land})$$
    $$\text{continentalness} = -0.08 + \frac{\text{worldDistance} \times 0.92}{\text{oceanTransitionWidth}} \quad (\text{for ocean})$$
  * Resulting value is clamped to `[-1.0, 0.70]`.

#### `IslandDensityFunction.java`
* Implements vanilla Minecraft `net.minecraft.world.level.levelgen.densityfunction.DensityFunction`.
* Registered under ID `omniisle:island_continents`.
* Replaces vanilla continentalness in the Overworld 3D noise router with the `IslandMask` continuous field.

#### `OmniBiomeSource.java`
* Extends vanilla `BiomeSource`, registered as `omniisle:island_biomes`.
* **Resolver Pipeline:**
  1. If `continentalness < -0.30`: Ocean biome (Warm, Lukewarm, Deep Ocean, Cold, Frozen based on temperature noise).
  2. If `continentalness < 0.02`: Shoreline / Beach shelf (Sandy Beach, Stony Shore, Snowy Beach based on elevation Y and temperature).
  3. If `isRiverValley(continentalness, erosion, weirdness)` and $Y \ge 0$: River / Frozen River.
  4. If $Y < 0$ and underground: Cave biome (Deep Dark, Sulfur Caves, Lush Caves, Dripstone Caves).
  5. Otherwise: Surface land biome mapped to nearest compatible climatic temperature/humidity cluster.
* **Resilience:**
  Constructor automatically falls back to baseline defaults if any `enabled_*` list passed from the datapack is empty.

---

## 5. Minecraft 26.3 Specific Technical Adaptations

| Component | Vanilla Pre-26.3 | Minecraft 26.3 Specification |
| :--- | :--- | :--- |
| **GUI Graphics** | `net.minecraft.client.gui.GuiGraphics` | `net.minecraft.client.gui.GuiGraphicsExtractor` |
| **Mouse Event** | Direct coordinate doubles `(double x, double y, int button)` | `net.minecraft.client.input.MouseButtonEvent` (access via `event.x()`, `event.y()`, `event.button()`) |
| **Mouse Button Left** | Hardcoded int `0` | GLFW Button `1` (`InputConstants.MOUSE_BUTTON_LEFT`) |
| **Keyboard Event** | `keyPressed(int keyCode, int scanCode, int modifiers)` | `net.minecraft.client.input.KeyEvent` (implements `InputWithModifiers`) |
| **Pack Format** | Format 48 / 61 | Format `121` (`TargetPlatform.PACK_FORMAT`) |
| **New Biomes** | N/A | `minecraft:dappled_forest` (Family 3), `minecraft:sulfur_caves` (Cave) |
| **New Structures**| N/A | `minecraft:abandoned_camp` (Forest, Birch, Taiga, Jungle variants) |

---

## 6. Datapack Schema & Presets Reference

### World Preset Injection (`omniisle:island_biomes`):
```json
{
  "type": "omniisle:island_biomes",
  "climate_scale": 720,
  "biome_seed": 123456789,
  "land_biomes": [
    "minecraft:snowy_plains", "minecraft:taiga", "minecraft:birch_forest",
    "minecraft:forest", "minecraft:plains", "minecraft:meadow",
    "minecraft:cherry_grove", "minecraft:dark_forest", "minecraft:swamp",
    "minecraft:mangrove_swamp", "minecraft:jungle", "minecraft:savanna",
    "minecraft:desert", "minecraft:badlands", "minecraft:stony_peaks",
    "minecraft:jagged_peaks", "minecraft:mushroom_fields", "minecraft:pale_garden"
  ],
  "enabled_land_biomes": [ ... ],
  "shore_biomes": [ "minecraft:beach", "minecraft:stony_shore", "minecraft:snowy_beach" ],
  "enabled_shore_biomes": [ ... ],
  "ocean_biomes": [ "minecraft:ocean", "minecraft:deep_ocean", ... ],
  "enabled_ocean_biomes": [ ... ],
  "river_biomes": [ "minecraft:river", "minecraft:frozen_river" ],
  "enabled_river_biomes": [ ... ],
  "cave_biomes": [
    "minecraft:lush_caves", "minecraft:dripstone_caves",
    "minecraft:deep_dark", "minecraft:sulfur_caves"
  ],
  "enabled_cave_biomes": [ ... ]
}
```

---

## 7. Guidelines & Invariants for Future AI Modifications
1. **Preserve Fallbacks:** Never assume `enabledBiomes` contains elements in any given category. Always maintain fallback paths in `IslandDraft.compatibleChoices` and `IslandDraft.enabledBiomes` to prevent runtime crashes during JSON compilation.
2. **Preserve NativeImage LifeCycle:** When creating or destroying textures on `DynamicTexture`, always release via `minecraft.getTextureManager().release(previewId)` inside `removed()` to prevent OpenGL texture handle leaks.
3. **Respect GLFW Button Identifiers:** In 26.3, do not use raw numeric literals for mouse buttons; always reference `InputConstants.MOUSE_BUTTON_LEFT` and `InputConstants.MOUSE_BUTTON_RIGHT`.
4. **Encoding Invariance:** All text resources, ZIP entries, and JSON payloads must strictly use `StandardCharsets.UTF_8` to guarantee cross-platform compatibility between Windows, Linux, and macOS.
5. **Preserve Mod Jar Targets:** Output builds must be mirrored to Prism Launcher instance mods (`C:\Game\prism\instances\Vulkan Optimization\minecraft\mods`) for immediate in-game test validation.
