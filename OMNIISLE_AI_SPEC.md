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
| **Mouse Button Left** | 0 | `InputConstants.MOUSE_BUTTON_LEFT` (GLFW button 0) |
| **Mouse Button Right**| 1 | `InputConstants.MOUSE_BUTTON_RIGHT` (GLFW button 1) |
| **Keyboard Event** | `keyPressed(int keyCode, int scanCode, int modifiers)` | `net.minecraft.client.input.KeyEvent` (implements `InputWithModifiers`) |
| **Pack Format** | Format 48 / 61 | Format `121` (`TargetPlatform.PACK_FORMAT`) |
| **New Biomes** | N/A | `minecraft:dappled_forest` (Family 3), `minecraft:sulfur_caves` (Cave) |
| **New Structures**| N/A | `minecraft:abandoned_camp` (Forest, Birch, Taiga, Jungle variants) |

> [!NOTE]
> In GLFW, mouse buttons are 0-indexed: `GLFW_MOUSE_BUTTON_1 = 0` (Left), `GLFW_MOUSE_BUTTON_2 = 1` (Right), `GLFW_MOUSE_BUTTON_3 = 2` (Middle). Always use Mojang's `InputConstants.MOUSE_BUTTON_LEFT` and `InputConstants.MOUSE_BUTTON_RIGHT` rather than hardcoded integer literals.

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

---

## 8. Advanced Technical Deep Dive & Next-Gen Implementation Guide

### 8.1. Eliminating SDF Discretization & Terracing on Slopes

#### The Root Cause
At an island size of $4000$ blocks across a $192 \times 192$ raster grid:
$$\text{Pixel Scale} = \frac{4000 \times 1.36}{192} \approx 28.33 \text{ blocks per pixel}$$
At an island size of $8000$ blocks:
$$\text{Pixel Scale} = \frac{8000 \times 1.36}{192} \approx 56.66 \text{ blocks per pixel}$$
When `coast_width` ranges between $110.0$ and $320.0$ blocks, the entire coastal gradient spans only $2$ to $7$ pixels. Bilinear interpolation across a coarse grid produces:
1. Octagonal contour lines (distance artifacts from discrete grids).
2. Quantization terraces (artificial horizontal plateaus on mountain slopes).

#### Three-Pronged Algorithmic Solution
```
[World Coordinates (X, Z)]
            │
            ▼
[1. Domain Warping Noise] ───> Shifts (X, Z) by ±30..70 blocks
            │
            ▼
[2. Exact EDT on 2x Super-sampled Grid] ───> Continuous sub-pixel distance
            │
            ▼
[3. Coastal fBM Anti-Terrace Perturbation] ───> Adds natural fine roughness
            │
            ▼
[Smooth Continentalness Router]
```

#### Mathematical Formulation & Java Implementation

##### 1. Continuous Domain Warping
Before projecting world coordinates into grid pixel space, perturb them using a dual-frequency simplex or value noise field:
```java
// Low-frequency organic warp: amplitude 45.0 blocks, frequency 1/520
double warpX = fbm(worldX / 520.0, worldZ / 520.0, seed ^ 0x9E3779B9L) * 45.0;
double warpZ = fbm((worldX + 31.7) / 520.0, (worldZ + 73.1) / 520.0, seed ^ 0x6A09E667L) * 45.0;
double warpedWorldX = worldX + warpX;
double warpedWorldZ = worldZ + warpZ;
```

##### 2. Exact Euclidean Distance Transform (Felzenszwalb-Huttenlocher)
`IslandMask` already implements the $O(N)$ parabolic lower-envelope algorithm. To achieve sub-pixel smoothness, upsample the mask by $2\times$ ($384 \times 384$) before running `edt1d`:
$$\mathcal{D}(p) = \min_{q} \left( (p_x - q_x)^2 + (p_y - q_y)^2 + I(q) \right)$$
Where $I(q) = 0$ if $q \in \text{Target}$, and $\infty$ otherwise.

##### 3. Near-Coast fBM Anti-Terrace Perturbation
In `sampleTerrainValue()`, modulate continentalness within the transition band $|c| < 0.22$:
```java
double baseContinentalness = worldDistance >= 0.0
    ? -0.08 + worldDistance * 0.70 / coastWidth
    : -0.08 + worldDistance * 0.92 / oceanTransitionWidth;

// Attenuate perturbation to zero outside the coastal band [-0.22, 0.22]
double transitionWeight = Math.max(0.0, 1.0 - Math.abs(baseContinentalness) / 0.22);
if (transitionWeight > 0.0) {
    double microRoughness = fbm(worldX / 72.0, worldZ / 72.0, seed ^ 0xDEADBEEFL) * 0.045;
    baseContinentalness += microRoughness * transitionWeight;
}
return Math.max(-1.0, Math.min(0.70, baseContinentalness));
```

---

### 8.2. Breaking the Conical Relief: Ridged Mountains & Heightmaps

#### The "Cone Problem"
Because continentalness is strictly derived from distance-to-coast, any circular or elliptical island naturally produces a conical mountain in the center.

#### Architectural Solutions

##### Approach A: Procedural Ridged Mountain Spines
Inject a ridged multifractal noise term into inland continentalness and erosion:
```java
if (worldDistance > 60.0) {
    // Distance factor fades from 0 at 60 blocks from coast to 1 at 250 blocks inland
    double inlandFactor = Math.min(1.0, (worldDistance - 60.0) / 190.0);
    
    // Ridged noise: peaks at zero crossings, sharp crests
    double ridge = 1.0 - 2.0 * Math.abs(fbm(worldX / 750.0, worldZ / 750.0, seed ^ 0x4B3A9C11L));
    ridge = Math.max(-0.5, Math.min(1.0, ridge));
    
    // Modulate continentalness: creates valleys and mountain ridges instead of a monotonic cone
    baseContinentalness += ridge * 0.25 * inlandFactor;
}
```

##### Approach B: 4-Tier Heightmap Layer (Dual-Layer Editor)
* **Editor Integration:** Add a "Height Layer" toggle in `OmniIsleEditorScreen` allowing users to paint elevation tiers:
  * Tier 0: Lowland / Plains (Erosion $+0.50$, Continentalness $+0.12$)
  * Tier 1: Normal / Rolling Hills (Default SDF)
  * Tier 2: Elevated Plateaus (Erosion $-0.20$, Continentalness $+0.35$)
  * Tier 3: Alpine Peaks (Erosion $-0.65$, Continentalness $+0.55$)
* **Data Packaging:** Stored as `data/omniisle/worldgen/height_layer.bin` (packed 2 bits per pixel $\to 4608$ bytes) inside `omniisle-generated.zip`.

---

### 8.3. Bathymetry, Satellite Islets & Seamounts

#### Eliminating the Flat Ocean Floor
When `worldDistance < -oceanTransitionWidth`, continentalness currently locks hard at $-1.0$. In Minecraft vanilla worldgen:
* $-1.0$ produces a featureless flat bedrock plane.
* Ocean Monuments require Deep Ocean ($-0.65 \le c \le -0.45$).
* Shipwrecks and Ocean Ruins generate on slopes and trenches.

#### Procedural Ocean Floor Formulation
Replace the flat $-1.0$ clamp with organic seafloor topography:
```java
if (worldDistance < 0.0) {
    double oceanDist = -worldDistance;
    double baseOcean = -0.08 - Math.min(0.55, oceanDist * 0.92 / oceanTransitionWidth);
    
    // Low-frequency underwater terrain variation: trenches (-0.85) to seamounts (-0.35)
    double seamountNoise = fbm(worldX / 1100.0, worldZ / 1100.0, seed ^ 0x8837AC7FL) * 0.18;
    double trenchNoise = fbm(worldX / 450.0, worldZ / 450.0, seed ^ 0x110294FEL) * 0.12;
    
    double finalOcean = baseOcean + seamountNoise - trenchNoise;
    return Math.max(-0.85, Math.min(-0.25, finalOcean));
}
```

#### Satellite Islet Generator
Introduce an "Islets" (Островки) slider in `OmniIsleEditorScreen` ($0.0 \dots 1.0$):
* Uses thresholded Voronoi cellular noise:
$$\text{IsletPeak}(x, z) = \max\left(0, \frac{\text{cellular}(x / 800, z / 800) - 0.85}{0.15}\right) \times \text{isletStrength}$$
* Adds scattered small islands, rocky arches, and coral reefs in the outer ocean without cluttering the main mask.

---

### 8.4. Mask Versioning & Forward Compatibility Specification

#### Updated `continents.json` Schema
```json
{
  "type": "minecraft:cache",
  "input": {
    "type": "omniisle:island_continents",
    "config": {
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
        "data": "..."
      },
      "biome_seed": 123456789
    }
  }
}
```

#### Codec Implementation in `IslandMask.java`
```java
public static final Codec<IslandMask> CODEC = RecordCodecBuilder.create(instance -> instance.group(
    Codec.INT.optionalFieldOf("version", 1).forGetter(m -> m.version),
    Codec.INT.optionalFieldOf("resolution", 192).forGetter(m -> m.resolution),
    Codec.intRange(2, 4096).fieldOf("width").forGetter(m -> m.width),
    Codec.intRange(2, 4096).fieldOf("height").forGetter(m -> m.height),
    Codec.DOUBLE.fieldOf("min_x").forGetter(m -> m.minX),
    Codec.DOUBLE.fieldOf("max_x").forGetter(m -> m.maxX),
    Codec.DOUBLE.fieldOf("min_z").forGetter(m -> m.minZ),
    Codec.DOUBLE.fieldOf("max_z").forGetter(m -> m.maxZ),
    Codec.DOUBLE.optionalFieldOf("coast_width", 192.0).forGetter(m -> m.coastWidth),
    Codec.DOUBLE.optionalFieldOf("ocean_transition_width", 400.0).forGetter(m -> m.oceanTransitionWidth),
    Codec.STRING.fieldOf("data").forGetter(m -> m.data)
).apply(instance, IslandMask::new));
```

---

### 8.5. Biome Scaling & Latitudinal Climate Gradient

#### 1. Biome Scale Slider (`climate_scale`)
* Exposed in `OmniIsleEditorScreen` as a slider from $384$ to $1536$ blocks (default $720$).
* Directly passed into `OmniBiomeSource` via `omniisle.json`:
```json
"climate_scale": 720
```

#### 2. Latitudinal Temperature Gradient
In Minecraft: $-Z$ is North (Arctic), $+Z$ is South (Equatorial).
Implement realistic global climate zonation across the island:
```java
public static double temperatureAt(double x, double z, double halfSpan, double scale, long seed, boolean gradient) {
    double warpX = fbm(x / scale, z / scale, seed + 101) * scale * 0.30;
    double warpZ = fbm(x / scale, z / scale, seed + 211) * scale * 0.30;
    double wz = z + warpZ;
    
    double baseTemp = 0.50;
    if (gradient) {
        // Linear latitudinal gradient: North (-halfSpan) is -0.45, South (+halfSpan) is +0.45
        double latitudinal = (wz / halfSpan) * 0.45;
        baseTemp += latitudinal;
    }
    
    double noise = fbm((x + warpX) / (scale * 1.35), wz / (scale * 1.35), seed + 307) * 0.18;
    return Math.max(0.0, Math.min(1.0, baseTemp + noise));
}
```
* **Result:** Setting Theme to "Nordic" naturally clusters glaciers, snowy peaks, and frozen oceans in the North, while "Tropical" clusters lush jungles and warm waters in the South.

---

### 8.6. Guaranteed Structure Placement & Editor Spawn Marker

#### Small Island (2500 Blocks) Structure Safety Matrix

| Structure | Critical Biome Requirement | Risk on 2500 Island | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Ancient City** | `deep_dark` AND (`stony_peaks` or `jagged_peaks`) | High (Mountain peaks may not exist at $Y < 0$) | Relax fallback: allow any land biome below $Y=0$ where deep dark noise triggers. |
| **Ocean Monument** | `deep_ocean` / `deep_cold_ocean` | Medium (Offshore shelf may be shallow) | Fallback bathymetry: guarantee deep ocean trench at $> 120\text{ blocks}$ offshore. |
| **Stronghold** | `plains` | Low | Automatically re-targets to any enabled land biome. |
| **Trial Chambers** | `plains` (inland, underground) | Low | Disallow deep dark overlap, allow any solid stone stratum. |
| **Abandoned Camp** | Forest / Taiga / Savanna variants | Zero | Selects matching camp variant for the specific biome sampled. |

#### Editor Spawn Crosshair Marker
In `OmniIsleEditorScreen.extractBackground`:
```java
// World spawn is at (0, 0). Calculate pixel location on preview canvas:
int spawnScreenX = previewX + previewSize / 2;
int spawnScreenY = previewY + previewSize / 2;

// Draw gold spawn crosshair
int gold = 0xFFFFD700;
context.horizontalLine(spawnScreenX - 4, spawnScreenX + 4, spawnScreenY, gold);
context.verticalLine(spawnScreenX, spawnScreenY - 4, spawnScreenY + 4, gold);
context.outline(spawnScreenX - 2, spawnScreenY - 2, 5, 5, 0xFFFFAA00);
```
Displays an unmistakable spawn compass marker right on the editor preview canvas!
