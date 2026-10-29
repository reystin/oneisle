# OneIsle — All Biomes & Structures on One Island

**OneIsle** is an independent continuation of [OmniIsle](https://github.com/y9hack337/OmniIsle) 0.5.0 by **Hack337**, finished and released as its own project for Fabric (Minecraft 26.3, Java 25). It turns standard infinite Overworld generation into a standalone, fully customizable, isolated island surrounded by an endless ocean.

Every Overworld biome (all 56 vanilla biomes, including Pale Garden, Dappled Forest, and Sulfur Caves) and 18 structure options can generate on a single island, balanced and ready for exploration, survival, and server events.

## Origin & credits

- Original idea and 0.5.0 code base: **Hack337** — [GitHub](https://github.com/y9hack337/OmniIsle), [Modrinth](https://modrinth.com/mod/omniisle).
- OneIsle is an independent continuation: all work after 0.5.0 was done by **reystin**, under the same **GPL-3.0-only** license.
- OneIsle is **not affiliated with, sponsored by, or endorsed by** the original author. The original repository and Modrinth page are not modified or maintained by this project.
- Full attribution: [CREDITS.md](https://github.com/reystin/oneisle/blob/main/CREDITS.md).

## Breaking change

This mod uses the `oneisle` namespace. Worlds created with OmniIsle 0.5.0–1.0.1 reference `omniisle:*` and **do not load** in OneIsle. There is no migration; create a new world. Legacy `OMNI2-` share codes and `config/omniisle.json` are not read.

Terrain math is unchanged: v1 reproduces OmniIsle 0.5.0 and v2 reproduces 0.6.0 bit-for-bit; v3 matches the OmniIsle 1.0.1 behavior. Only identifiers and packaging changed.

## Features

- **In-game island editor** embedded in Create New World: custom flat UI with six tabs, canvas drawing tools, symmetry, zoom/pan, 13 shape presets, PNG import/export and `ONE2-` share codes, density tiers from 1920×1080 down to 320×150 logical pixels, en_us/ru_ru.
- **v3 generation (default):** deterministic shore fields, deep-ocean ramp, monument band, islets, carved rivers, validated dry spawn, lifted highlands, universal climate sectors with continuous warm↔cold bands, rare-biome pockets, five biome-matched village variants at about 40 % of the former density, strict structure placement.
- **Frozen v1/v2 generation** for reference and golden tests.
- **Dedicated servers:** `config/oneisle.json` auto-generates `oneisle-generated.zip`; `/oneisle info|verify|debug|import` commands.

## Requirements

- Minecraft **26.3** (Fabric)
- Fabric Loader **0.19.3** or newer
- Fabric API **0.165.0+26.3** or newer
- Java **25**

The mod is required on both client and server.

## Usage

1. Install Fabric Loader and Fabric API.
2. Put the JAR in `mods/`.
3. Create a new world with world type **OneIsle**; the island editor opens from the Create World screen.
4. On a dedicated server, place an exported preset at `config/oneisle.json` before the first start, or use `/oneisle import`.

## Performance

25×25 chunk generation (625 chunks, ROUND 4000, seed 4242, seven interleaved rounds on
OneIsle 1.0.0): median **v1 14.284 s**, **v2 13.882 s**, **v3 13.374 s**, **v3 with
Cherry Grove weight 45 13.606 s**. Every modern variant stays inside the +15 % budget
versus v1. Raw data:
[`docs/evidence/perf/chunkgen_25x25_1.0.0.csv`](https://github.com/reystin/oneisle/blob/main/docs/evidence/perf/chunkgen_25x25_1.0.0.csv).

## Verification and limits

- 198 tests, 0 failures, 0 errors, 3 assumption-gated skips; golden v1/v2 hashes unchanged.
- In-game smoke: `oneisle 1.0.0+mc26.3` loads and the editor renders.
- Phase 9 structure verification remains **PARTIAL 15/45**; 25×25 chunk timing is measured
  for 1.0.0 (see above).
- Interactive UI feel (tooltips, toasts, dropdown, IME) was not re-exercised after the rename.
- MEDIUM/MINIMAL editor tiers have no screenshots; MINIMAL is unreachable in a real client.

## AI disclosure

The code, tests and documentation were developed with AI assistance and reviewed by the maintainer. Measured claims are backed by files under [`docs/evidence/`](https://github.com/reystin/oneisle/tree/main/docs/evidence).

## License

**GPL-3.0-only**, the same license as OmniIsle 0.5.0. Copyright notices from the original code base are preserved. Source: <https://github.com/reystin/oneisle>.
