# OmniIsle — All Biomes & Structures on One Island

**OmniIsle** is an open-source Minecraft mod for Fabric (Minecraft 26.3, Java 25) that transforms standard infinite Overworld generation into a standalone, fully customizable, isolated island surrounded by an endless ocean.

Every Overworld biome (all 56 vanilla biomes, including Pale Garden, Dappled Forest, and Sulfur Caves) and all 18 structures generate organically on a single island, perfectly balanced and ready for exploration, survival, and server events.

---

## Features

### In-Game Island Editor
* Embedded directly into the "Create New World" screen (World Type: **OmniIsle**).
* **Interactive Canvas:** Draw land and height layers in real-time with brush sizes from 1 to 24 pixels.
* **Canvas Resolutions:** Choose between 192×192 (default), 256×256, or 384×384 with bilinear resampling.
* **Drawing Tools:** Freehand brush, Flood Fill, Line, Rectangle, and Ellipse (hold Shift for square/circle; fill/outline toggle).
* **Symmetry Modes:** Off, Mirror-X, Mirror-Z, 4-Way, 6-Way, and 8-Way radial symmetry.
* **Zoom & Pan:** Smooth 1×–8× canvas zoom with scroll wheel; middle-click or Space-drag to pan; 16px grid overlay toggle.
* **12 Procedural Presets:** Natural, Round, Long, Crescent, Archipelago, Atoll, Volcano, Fjords, Twin Islands, Inland Sea, Horseshoe Bay, and Ridge Island.
* **PNG Import/Export:** Import custom heightmaps or masks with threshold, invert, and aspect-preserving letterbox. Export B/W mask, height layer, color biome map, and relief hillshade.
* **Share Codes:** One-click copy/paste of island designs via compact compressed share codes (`OMNI2-...`).

### Next-Gen Terrain Generation (v2)
* **High-Precision SDF:** Exact Felzenszwalb-Huttenlocher Euclidean Distance Transform on native grids with sub-pixel distance convention.
* **Organic Coastlines:** Catmull-Rom bicubic interpolation with neighborhood clamping and domain warping (45-block low-frequency noise).
* **Shoreline Micro-Roughness:** Smoothstep-attenuated fine detail centered on the shoreline (-0.08).
* **Inland Relief:** Non-conical relief featuring compressed coastal rise, procedural ridged mountain spines, and painted 4-tier heightmaps (Lowland, Normal, Plateau, Alpine Peaks) with smooth transitions.
* **Ocean Bathymetry:** Soft ocean floor at -0.80 with seamounts and oceanic trenches down to -1.0.
* **Guaranteed Deep Band:** Continuous deep ocean band ensuring Ocean Monuments always generate properly.
* **Satellite Islets:** Outer archipelago islets generated via cellular noise with configurable density.
* **Physically Carved Rivers & Lakes:** Deterministically derived at load using Priority-Flood depression filling, flow accumulation, and $C^1$ smoothstep carving.

### Climate & Biome System
* **Biome Scale Slider:** Configurable `climate_scale` (384 to 1536 blocks).
* **Latitudinal Temperature Gradients:** North-South, East-West, or Radial gradients with theme-aware amplitude compression.
* **Noise-Jittered Blending:** Organic transitions between climate families with zero straight lines.
* **Dramatic Shorelines:** Automatic cliff detection transforming steep slopes ($\ge 0.35$) into stony shore cliffs.
* **Dynamic Biome Discovery:** Automatically detects and integrates datapack and modded biomes (e.g. Terralith) from `WorldCreationContext`.

### Structures & Guaranteed Safe Spawn
* **Small-Island Scaling:** Concentric stronghold rings automatically scale with island size, guaranteeing strongholds generate on land even on 2500-block islands.
* **Deep Dark Ancient Cities:** Guaranteed subterranean ancient city placement decoupled from surface peaks.
* **Inland Spawn Selector:** Automatically selects the inland pixel with the highest positive distance to coast.
* **Gold Crosshair Marker:** Manual world spawn placement on the canvas with right-click auto-reset.
* **First-Load Relocation:** Automated outward spiral search relocating the player to solid land if spawned in water.
* **World Border:** Optional island-bounded world border with configurable margin (500 to 4000 blocks).

---

## World Compatibility

> [!IMPORTANT]
> **Old Worlds Keep Generating Identically (Invariant I1):**  
> Worlds created with OmniIsle 0.5.0 (`generation_version = 1`) load seamlessly in 0.6.0. Chunks generated in 0.6.0 match existing 0.5.0 chunks **bit-for-bit** with zero seams or terrain tears. New worlds default to **v2** generation.

---

## Dedicated Server Support

OmniIsle works out of the box on dedicated servers:

### 1. Auto-Generation via `config/omniisle.json`
Place an exported OmniIsle preset JSON file in `config/omniisle.json`. When the server boots for the first time without a world, OmniIsle automatically builds and registers `world/datapacks/omniisle-generated.zip`.

### 2. In-Game & Console Commands
* `/omniisle info` — Displays generation version, resolution, island size, bounding box, spawn, and active layers.
* `/omniisle verify` — Probes surface elevation and biome distribution across coordinates.
* `/omniisle debug <continentalness|height|biome|river|tier> [radius] [step]` — Exports real server-side PNG map images to `omniisle/debug/`.
* `/omniisle import <preset-name|OMNI2-...>` — Imports a named preset from `omniisle/presets/` or a share code directly into the server's world datapacks folder.

---

## Building from Source

Requirements: JDK 25 and Gradle.

```bash
git clone https://github.com/y9hack337/OmniIsle.git
cd OmniIsle
./gradlew build
```

The resulting mod JAR will be located in `build/libs/OmniIsle-26.3-Fabric-0.6.0+mc26.3.jar`.

---

## License

OmniIsle is licensed under the GPL-3.0 License.
