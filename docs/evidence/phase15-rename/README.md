# Phase 15 — OneIsle rebrand: verification evidence

Date: 2026-10-29. All commands run from the repository root with
`JAVA_HOME="/Users/reystin/Library/Application Support/PrismLauncher/java/java-runtime-epsilon/jre.bundle/Contents/Home"`.

## Files

| file | contents |
|---|---|
| `build.log` | tail of `./gradlew clean test build` (197 tests, 0 failures, 0 errors, 2 assumption-gated skips) |
| `test_summary.txt` | parsed JUnit counts and the names of the two skipped assumption tests |
| `namespace_gates.txt` | `rg` gates: no lowercase `omniisle` and no `Omni` class prefix under `src/main`, no old IDs in `src/main/resources`, no lowercase `omniisle` in the extracted jar; the single allowed attribution sentence; 16 files / 648 `oneisle` matches |
| `jar_metadata.txt` | `fabric.mod.json`, mixin config, `assets/`/`data/` namespaces and license entries read back from the built jar |
| `sha256.txt` | SHA-256 of the jar and sources jar |

## In-game smoke

`./gradlew runClient --console=plain -Doneisle.uiScreenshot=/tmp/oneisle-smoke -Doneisle.uiScreenshot.smoke=1`

- Client log: `oneisle 1.0.0+mc26.3` loaded by Fabric; resource reload lists the `oneisle` mod
  resources; no `omniisle` mod or namespace appears.
- Harness wrote `harness_done.txt` = `ok` and four captures:
  1280×720 scale 1 (1280×720 logical), 1280×720 scale 4 (427×240), 640×480 scale 1 (640×480),
  640×480 scale 2 (320×240).
- The 1280×720 scale-1 capture was inspected: editor header reads “OneIsle”, tabs and tool column
  render normally. Copied to `docs/screens/oneisle-1.0.0-smoke/`.

The smoke covered the Shape tab only; other tabs, tooltips, toasts and keyboard feel were not
re-exercised after the rename (their code paths are unchanged).

## Namespace

Run-time identifiers are `oneisle:*` only. `omniisle:*` is deliberately unregistered, so worlds
created by OmniIsle 0.5.0–1.0.1 cannot load. The GPL attribution “Independent continuation of
OmniIsle 0.5.0 by Hack337” in the jar description is the only intentional occurrence of the old
name in runtime metadata.
