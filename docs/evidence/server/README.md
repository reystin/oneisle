# Phase 9.7 — dedicated server `config/omniisle.json` flow

Criterion: a headless Fabric 26.3 server started with `config/omniisle.json` creates a
fresh world that already uses the generated OmniIsle datapack, with log evidence.

## Setup

- Draft: `NATURAL`, island size 4000, shape/biome seed 424242, generation version 3,
  river density 0.45, exported with `ShareCode.toJson` to `run/config/omniisle.json`
  (`config_omniisle.json` here, sha256
  `a24720a12744734513a0c2a15e834450f5fd3f5cc572344b5a6374be76176385`).
- Fresh universe `/tmp/omniisle-server-flow2`; no pre-existing world or pack.
- `./gradlew runServer --args="--universe /tmp/omniisle-server-flow2 --nogui"` from
  the project, with `level-name=locate`, `level-type=omniisle\:omniisle`,
  `view-distance=4`, RCON on 25575.
- The server was queried through RCON (`datapack list enabled`, `omniisle info`,
  `omniisle verify`) and stopped with `stop`.

## Timing fix

The first attempt wrote the pack from `ServerLifecycleEvents.SERVER_STARTING`. Fabric
loads the world's pack list before mod initializers run, so that run enabled only
`[omniisle (Fabric mod)]`, `/tmp/.../datapacks/omniisle-generated.zip` existed but was
not in the list, and `omniisle info` reported `Generation Version: 1 (legacy)`.

The export now runs in the `preLaunch` entrypoint `OmniIslePreLaunch`, which resolves
the world folder from `user.dir`, the `--universe` argument and `server.properties`
`level-name`, and writes `<universe>/<level-name>/datapacks/omniisle-generated.zip`
before `WorldLoader` scans packs. The `SERVER_STARTING` handler is kept as a fallback
for integrated servers. `server_startup.log` shows the corrected order:

```
[21:58:12] (OmniIslePreLaunch) Automatically generated island datapack at
           /tmp/omniisle-server-flow2/locate/datapacks/omniisle-generated.zip
           from config/omniisle.json
[21:58:19] (Minecraft) No existing world data, creating new world
[21:58:20] (Minecraft) Loaded 1866 advancements
[21:58:20] (Minecraft) Preparing level "locate"
[21:58:22] (Minecraft) Done (1.589s)!
[21:58:23] (OmniIsleMod) Applied world spawn at BlockPos{x=14, y=128, z=-1062}
           (previous BlockPos{x=0, y=104, z=48}, marker [14, -1062]), land=true
```

The spawn line is the v3 spawn reconciliation, so the generated world preset was
active during world creation, not only after a reload.

## RCON results (first boot)

`datapack list enabled` — 5 packs, including `[file/omniisle-generated.zip (world)]`.

`omniisle info`:

| field | value |
|---|---|
| Generation Version | 3 (modern) |
| Resolution | 192x192 |
| Island Size | 5440 blocks (mask spans 1.36× the nominal 4000 size, i.e. 720 blocks of margin per side) |
| Bounding Box | [-2720, -2720] to [2720, 2720] |
| World Spawn | 14, 128, -1062 |
| World Border | Disabled |
| Enabled Layers | Land (Mask), Rivers (45%) |

`omniisle verify` (v3 world, first boot): the spawn-area samples are
`plains@68`, `cherry_grove@77`, `plains@120`, `dappled_forest@138`,
`plains@112`, `river@67`, `river@94`, `meadow@112`, `cherry_grove@118`,
`deep_ocean@63` (1536/1536); the surface scan finds 19 distinct biomes including
`jagged_peaks`, `stony_peaks`, `pale_garden`, `mushroom_fields` and all four ocean
temperature bands. Full outputs are in `rcon_world_info.txt` and `rcon_verify.txt`.

Generated pack sha256:
`efd102afafb7ce1abdd4adf5637630c2b99d4b4c1172ad5aad06fc8b7e9cd4e6`
(17760 bytes; contents in `generated_pack_entries.txt`).

## Files

- `config_omniisle.json` — exact config used by the server.
- `server_startup.log` — startup excerpt (export, world creation, spawn).
- `rcon_world_info.txt` — `datapack list enabled` and `omniisle info`.
- `rcon_verify.txt` — `omniisle verify` biome samples.
- `generated_pack_entries.txt` — zip listing of the generated pack.
