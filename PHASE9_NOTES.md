# Phase 9 working notes (orientation)

Working notes for the Phase 9 takeover. Not user documentation; final docs are rewritten in Phase 9.8.

## Build baseline (2026-10-24)

* JDK: Prism `java-runtime-epsilon` (Java 25.0.1), because no system JDK is on PATH.
* Command: `JAVA_HOME="$HOME/Library/Application Support/PrismLauncher/java/java-runtime-epsilon" ./gradlew build --no-daemon`
* Result: `BUILD SUCCESSFUL in 2m 20s`, 38 tests, 0 failures, 0 skipped.
* Repo was checked out with CRLF + macOS quarantine; normalized to LF, `gradlew` made executable, `.gitattributes` added (Phase 9.0 commit).

## Architecture map (verified by reading source)

| Concern | Entry point | Notes |
| --- | --- | --- |
| Editor state | `client/IslandDraft.java` | 192/256/384 grid, 12 presets, 6 symmetries, seeds, undo (32), PNG/share codes |
| Editor UI | `client/OmniIsleEditorScreen.java` | 4 tabs (Draw/Shape/Climate/Presets), tools, preview texture |
| Advanced UI | `client/AdvancedSettingsScreen.java` | generation version, climate scale, gradient, islets, rivers, structure density, border |
| Datapack export | `client/WorldPackBuilder.java` | mask/config + structure sets (`omniisle:fixed`, `concentric_rings`) |
| Server density | `worldgen/IslandDensityFunction.java` | wraps shared `IslandTerrain`; 2D XZ sampler |
| Server biomes | `worldgen/OmniBiomeSource.java` | v1 legacy + v2 branch; climate via `IslandClimate` |
| Shared terrain | `continent/ModernTerrainV2.java` | SDF, bicubic, coast rise, ocean, islets, rivers |
| Shared climate | `continent/IslandClimate.java` | quantized target point, theme gradient, families, shore subtype |
| Commands | `command/OmniCommands.java` | info/verify/debug PNG exporters |

## Verified contradictions with prior-agent claims

1. **"Seeded 90-config tests"** — `IslandDraft`'s default constructor seeds `shapeSeed`/`biomeSeed` with `new Random().nextLong()` (`IslandDraft.java:169`). Tests never set seeds, so every run uses random islands; CSVs are nondeterministic. A fresh `./gradlew build` rewrote `STRUCTURE_REPORT_*.csv` with different distances.
2. **"Stronghold 24–77 blocks from spawn"** — the harness does not run structure placement. For strongholds it computes `abs(ringRadius - hypot(spawn))` where `ringRadius = scaledDistance*64` (`StructurePlacementHarness.java:172-181`). The "distance from spawn" is an arithmetic residual, not a located stronghold.
3. **Structure harness uses legacy climate** — `evaluateModern` samples biomes with `BiomePreview.surface(continentalness,x,z,seed)`, the v1 overload, instead of the v2/v3 climate path. Structure placement in `WorldPackBuilder` does the same.
4. **"Structure density 0.5x/1x/2x"** — `structureDensity` is stored/copied/shared but never consumed by `WorldPackBuilder` or placement (`grep structureDensity` shows UI/state only).
5. **"Zoom/pan implemented"** — `zoom`/`panX`/`panZ` are updated by scroll and middle-drag (`OmniIsleEditorScreen.java:1126,1139`) but the preview blit ignores them (`:679-680`) and hit-testing maps mouse directly to full texture (`:1054-1056`). Feature is cosmetic state only. No grid-scale rendering or space-pan.
6. **"All tools + presets"** — 12 presets and 8 tools exist, but symmetry applies only to brush-ish operations (needs per-tool verification), and the prompt's six-tab layout is absent.
7. **"100% client/server parity"** — parity tests likely compare the same `IslandClimate.sample` function to itself rather than an independent reference; must be re-derived and measured in Phase 9.1.
8. **"Flat ocean floor ≤ −0.92"** — v2 has soft floor −0.80 with noise/seamounts (`ModernTerrainV2.java`), not −0.92; deep band is not the prompt's `[0.4Wo,1.0Wo]` shape. Audit with radial profiles in 9.3.
9. **"V1 untouched"** — plausible for terrain (`LegacyTerrainV1`), but `IslandDraft.copy()` drops `riverDensity`, `worldBorder`, `worldBorderMargin` (`IslandDraft.java:205-220`), silently changing exports after a copy. Any v3 additions must be added there.
10. **Empty shore palette semantics** — `OmniBiomeSource` treats an empty `enabled_shore_biomes` as "all shore biomes" (`OmniBiomeSource.java:105`), preventing "no beaches" customization needed by v3.

## Plan constraints

* v3 = new `generation_version: 3`; v1/v2 code paths must stay byte-identical (`GoldenV1Test` extended).
* Shore outcome must be computed once in shared side-neutral code and consumed by both `OmniBiomeSource` and `BiomePreview`.
* Evidence goes to `docs/evidence/phase9_*` (CSV/PNG/log), no unverifiable claims.
